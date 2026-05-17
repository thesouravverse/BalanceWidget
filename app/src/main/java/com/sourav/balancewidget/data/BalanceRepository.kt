package com.sourav.balancewidget.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sourav.balancewidget.parser.BalanceParser
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "balance_widget")

@Serializable
data class Account(
    val id: String,
    val label: String,
    val suffix: String,
    val startingBalance: Double,
    val calibratedAt: Long
)

@Serializable
data class BalanceEntry(
    val accountId: String,
    val balance: Double,
    val txnAmount: Double? = null,
    val direction: String = "UNKNOWN",
    val source: String = "SMS",
    val sender: String? = null,
    val accountSuffix: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val rawText: String = ""
)

@Singleton
class BalanceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val accountsKey = stringPreferencesKey("accounts_json")
    private val historyKey = stringPreferencesKey("history_json")
    private val widgetOpacityKey = floatPreferencesKey("widget_opacity")

    // legacy single-account keys for one-time migration
    private val legacyStartBalKey = doublePreferencesKey("config_start_balance")
    private val legacyAcctSuffixKey = stringPreferencesKey("config_account_suffix")
    private val legacyCalibratedAtKey = longPreferencesKey("config_calibrated_at")

    private val maxHistoryPerAccount = 200

    val accounts: Flow<List<Account>> = context.dataStore.data.map { prefs ->
        decodeAccounts(prefs)
    }

    val history: Flow<List<BalanceEntry>> = context.dataStore.data.map { prefs ->
        decodeHistory(prefs)
    }

    /** Convenience: latest entry per account (keyed by accountId). */
    val latestByAccount: Flow<Map<String, BalanceEntry>> = history.map { list ->
        list.groupBy { it.accountId }.mapValues { (_, entries) ->
            entries.maxByOrNull { it.timestampMillis }!!
        }
    }

    val widgetOpacity: Flow<Float> = context.dataStore.data.map { prefs ->
        prefs[widgetOpacityKey] ?: 1.0f
    }

    suspend fun accountsSnapshot(): List<Account> = accounts.first()
    suspend fun widgetOpacitySnapshot(): Float = widgetOpacity.first()

    /** Create new account or update existing (matched by id). Seeds an OPENING entry. */
    suspend fun addOrUpdateAccount(
        id: String? = null,
        label: String,
        suffix: String,
        startingBalance: Double
    ): Account {
        val now = System.currentTimeMillis()
        val finalId = id ?: UUID.randomUUID().toString()
        val account = Account(
            id = finalId,
            label = label.ifBlank { "Account *$suffix" },
            suffix = suffix,
            startingBalance = startingBalance,
            calibratedAt = now
        )
        context.dataStore.edit { prefs ->
            val current = decodeAccounts(prefs).toMutableList()
            val idx = current.indexOfFirst { it.id == finalId }
            if (idx >= 0) current[idx] = account else current.add(account)
            prefs[accountsKey] = Json.encodeToString(current)

            // reset this account's history with a fresh opening seed
            val others = decodeHistory(prefs).filter { it.accountId != finalId }
            val seed = BalanceEntry(
                accountId = finalId,
                balance = startingBalance,
                direction = "OPENING",
                source = "CALIBRATION",
                accountSuffix = suffix,
                timestampMillis = now,
                rawText = "Calibrated ${account.label} to ₹%,.2f".format(startingBalance)
            )
            prefs[historyKey] = Json.encodeToString(others + seed)
        }
        return account
    }

    suspend fun deleteAccount(id: String) {
        context.dataStore.edit { prefs ->
            val remaining = decodeAccounts(prefs).filterNot { it.id == id }
            prefs[accountsKey] = Json.encodeToString(remaining)
            val remainingHistory = decodeHistory(prefs).filterNot { it.accountId == id }
            prefs[historyKey] = Json.encodeToString(remainingHistory)
        }
    }

    suspend fun setWidgetOpacity(opacity: Float) {
        context.dataStore.edit { prefs ->
            prefs[widgetOpacityKey] = opacity.coerceIn(0.1f, 1.0f)
        }
    }

    /**
     * Apply a parsed transaction. Routes to the first account whose suffix matches.
     * @param applyToBalance true (real-time SMS) recomputes running balance. false (historical scan) preserves last balance.
     */
    suspend fun applyTxn(
        parsed: BalanceParser.ParsedTxn,
        timestampMillis: Long = System.currentTimeMillis(),
        applyToBalance: Boolean = true
    ): Boolean {
        val parsedSuffix = parsed.accountSuffix ?: return false
        val all = accountsSnapshot()
        val target = all.firstOrNull { acct ->
            parsedSuffix.endsWith(acct.suffix) || acct.suffix.endsWith(parsedSuffix)
        } ?: return false

        var applied = false
        context.dataStore.edit { prefs ->
            val allHistory = decodeHistory(prefs)
            val accountHistory = allHistory.filter { it.accountId == target.id }
                .sortedByDescending { it.timestampMillis }
            val last = accountHistory.firstOrNull()

            // dedupe identical raw within 60s for this account
            if (last != null &&
                last.rawText == parsed.rawText &&
                (timestampMillis - last.timestampMillis) < 60_000
            ) return@edit

            val lastBalance = last?.balance ?: target.startingBalance
            val newBalance = if (applyToBalance) {
                when (parsed.direction) {
                    BalanceParser.Direction.DEBIT -> lastBalance - parsed.amount
                    BalanceParser.Direction.CREDIT -> lastBalance + parsed.amount
                    else -> lastBalance
                }
            } else lastBalance
            val finalBalance = if (applyToBalance) (parsed.balanceFromBank ?: newBalance) else lastBalance

            val entry = BalanceEntry(
                accountId = target.id,
                balance = finalBalance,
                txnAmount = parsed.amount,
                direction = parsed.direction.name,
                source = parsed.source,
                sender = parsed.sender,
                accountSuffix = parsedSuffix,
                timestampMillis = timestampMillis,
                rawText = parsed.rawText
            )

            // Keep newest maxHistoryPerAccount per account; preserve other accounts untouched.
            val updatedAccountHistory = (listOf(entry) + accountHistory)
                .sortedByDescending { it.timestampMillis }
                .take(maxHistoryPerAccount)
            val others = allHistory.filter { it.accountId != target.id }
            prefs[historyKey] = Json.encodeToString(others + updatedAccountHistory)
            applied = true
        }
        return applied
    }

    /** For one-shot historical scans — wipes history for one account but keeps the account itself. */
    suspend fun clearAccountHistory(accountId: String) {
        context.dataStore.edit { prefs ->
            val accts = decodeAccounts(prefs)
            val acct = accts.firstOrNull { it.id == accountId } ?: return@edit
            val seed = BalanceEntry(
                accountId = acct.id,
                balance = acct.startingBalance,
                direction = "OPENING",
                source = "CALIBRATION",
                accountSuffix = acct.suffix,
                timestampMillis = acct.calibratedAt,
                rawText = "Re-seeded ${acct.label}"
            )
            val others = decodeHistory(prefs).filter { it.accountId != accountId }
            prefs[historyKey] = Json.encodeToString(others + seed)
        }
    }

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }

    // -------- helpers --------

    private fun decodeAccounts(prefs: Preferences): List<Account> =
        prefs[accountsKey]?.let {
            runCatching { Json.decodeFromString<List<Account>>(it) }.getOrNull()
        } ?: emptyList()

    private fun decodeHistory(prefs: Preferences): List<BalanceEntry> =
        prefs[historyKey]?.let {
            runCatching { Json.decodeFromString<List<BalanceEntry>>(it) }.getOrNull()
        } ?: emptyList()

    /**
     * Migrates v1 single-account config into v2 multi-account schema.
     * Runs only when accounts list is empty AND legacy keys are present.
     * Idempotent — safe to call repeatedly.
     */
    suspend fun migrateLegacyIfNeeded() {
        context.dataStore.edit { prefs ->
            if (prefs[accountsKey] != null) return@edit
            val legacyBal = prefs[legacyStartBalKey] ?: return@edit
            val legacySuffix = prefs[legacyAcctSuffixKey] ?: return@edit
            val legacyAt = prefs[legacyCalibratedAtKey] ?: return@edit
            val migrated = Account(
                id = UUID.randomUUID().toString(),
                label = "Account *$legacySuffix",
                suffix = legacySuffix,
                startingBalance = legacyBal,
                calibratedAt = legacyAt
            )
            prefs[accountsKey] = Json.encodeToString(listOf(migrated))
            val seed = BalanceEntry(
                accountId = migrated.id,
                balance = migrated.startingBalance,
                direction = "OPENING",
                source = "CALIBRATION",
                accountSuffix = migrated.suffix,
                timestampMillis = migrated.calibratedAt,
                rawText = "Migrated from v1 calibration"
            )
            // Drop any v1 history (different schema, no accountId); seed fresh
            prefs[historyKey] = Json.encodeToString(listOf(seed))
            prefs.remove(legacyStartBalKey)
            prefs.remove(legacyAcctSuffixKey)
            prefs.remove(legacyCalibratedAtKey)
        }
    }
}
