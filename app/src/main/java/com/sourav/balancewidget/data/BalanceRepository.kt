package com.sourav.balancewidget.data

import android.content.Context
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
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
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "balance_widget")

@Serializable
data class BalanceEntry(
    val balance: Double,
    val txnAmount: Double? = null,
    val direction: String = "UNKNOWN",
    val source: String = "SMS",
    val sender: String? = null,
    val accountSuffix: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val rawText: String = ""
)

@Serializable
data class Config(
    val startingBalance: Double,
    val accountSuffix: String, // last 4 digits the user is tracking, e.g. "9504"
    val calibratedAt: Long
)

@Singleton
class BalanceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val historyKey = stringPreferencesKey("history_json")
    private val startBalKey = doublePreferencesKey("config_start_balance")
    private val acctSuffixKey = stringPreferencesKey("config_account_suffix")
    private val calibratedAtKey = longPreferencesKey("config_calibrated_at")
    private val maxHistory = 200

    val history: Flow<List<BalanceEntry>> = context.dataStore.data.map { prefs ->
        prefs[historyKey]?.let { runCatching { Json.decodeFromString<List<BalanceEntry>>(it) }.getOrNull() }
            ?: emptyList()
    }

    val latest: Flow<BalanceEntry?> = history.map { it.firstOrNull() }

    val config: Flow<Config?> = context.dataStore.data.map { prefs ->
        val bal = prefs[startBalKey]
        val suffix = prefs[acctSuffixKey]
        val at = prefs[calibratedAtKey]
        if (bal != null && suffix != null && at != null) Config(bal, suffix, at) else null
    }

    suspend fun configSnapshot(): Config? = config.first()

    /** Set or replace calibration. Clears history and seeds a starting entry. */
    suspend fun calibrate(startingBalance: Double, accountSuffix: String) {
        val now = System.currentTimeMillis()
        val seed = BalanceEntry(
            balance = startingBalance,
            txnAmount = null,
            direction = "OPENING",
            source = "CALIBRATION",
            accountSuffix = accountSuffix,
            timestampMillis = now,
            rawText = "Calibrated to ₹%,.2f for account *%s".format(startingBalance, accountSuffix)
        )
        context.dataStore.edit { prefs ->
            prefs[startBalKey] = startingBalance
            prefs[acctSuffixKey] = accountSuffix
            prefs[calibratedAtKey] = now
            prefs[historyKey] = Json.encodeToString(listOf(seed))
        }
    }

    /**
     * Apply a parsed transaction if it matches the configured account suffix.
     * @param applyToBalance when true (real-time SMS), compute new running balance.
     *                      When false (historical scan), keep balance the same — entry is informational.
     * Returns true if applied.
     */
    suspend fun applyTxn(
        parsed: BalanceParser.ParsedTxn,
        timestampMillis: Long = System.currentTimeMillis(),
        applyToBalance: Boolean = true
    ): Boolean {
        val cfg = configSnapshot() ?: return false
        val parsedSuffix = parsed.accountSuffix ?: return false
        if (!parsedSuffix.endsWith(cfg.accountSuffix) && !cfg.accountSuffix.endsWith(parsedSuffix)) return false

        context.dataStore.edit { prefs ->
            val current = prefs[historyKey]
                ?.let { runCatching { Json.decodeFromString<List<BalanceEntry>>(it) }.getOrNull() }
                ?: emptyList()

            // dedupe: same raw within 60s
            val last = current.firstOrNull()
            if (last != null &&
                last.rawText == parsed.rawText &&
                (timestampMillis - last.timestampMillis) < 60_000
            ) return@edit

            val lastBalance = last?.balance ?: cfg.startingBalance
            val newBalance = if (applyToBalance) {
                when (parsed.direction) {
                    BalanceParser.Direction.DEBIT -> lastBalance - parsed.amount
                    BalanceParser.Direction.CREDIT -> lastBalance + parsed.amount
                    else -> lastBalance
                }
            } else lastBalance
            val finalBalance = if (applyToBalance) (parsed.balanceFromBank ?: newBalance) else lastBalance

            val entry = BalanceEntry(
                balance = finalBalance,
                txnAmount = parsed.amount,
                direction = parsed.direction.name,
                source = parsed.source,
                sender = parsed.sender,
                accountSuffix = parsedSuffix,
                timestampMillis = timestampMillis,
                rawText = parsed.rawText
            )
            // Insert keeping list sorted newest-first
            val updated = (listOf(entry) + current)
                .sortedByDescending { it.timestampMillis }
                .take(maxHistory)
            prefs[historyKey] = Json.encodeToString(updated)
        }
        return true
    }

    suspend fun clearHistoryKeepConfig() {
        val cfg = configSnapshot()
        context.dataStore.edit { prefs ->
            if (cfg != null) {
                val seed = BalanceEntry(
                    balance = cfg.startingBalance,
                    direction = "OPENING",
                    source = "CALIBRATION",
                    accountSuffix = cfg.accountSuffix,
                    timestampMillis = cfg.calibratedAt,
                    rawText = "Re-seeded from calibration"
                )
                prefs[historyKey] = Json.encodeToString(listOf(seed))
            } else {
                prefs.remove(historyKey)
            }
        }
    }

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }
}
