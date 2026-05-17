package com.sourav.balancewidget.data

import android.content.Context
import androidx.datastore.preferences.core.edit
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
    val direction: String = "UNKNOWN", // DEBIT / CREDIT / UNKNOWN
    val source: String = "SMS",
    val sender: String? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val rawText: String = ""
)

@Singleton
class BalanceRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val historyKey = stringPreferencesKey("history_json")
    private val maxHistory = 100

    val history: Flow<List<BalanceEntry>> = context.dataStore.data.map { prefs ->
        prefs[historyKey]?.let { runCatching { Json.decodeFromString<List<BalanceEntry>>(it) }.getOrNull() }
            ?: emptyList()
    }

    val latest: Flow<BalanceEntry?> = history.map { it.firstOrNull() }

    suspend fun add(parsed: BalanceParser.Parsed, timestampMillis: Long = System.currentTimeMillis()) {
        val entry = BalanceEntry(
            balance = parsed.balance,
            txnAmount = parsed.txnAmount,
            direction = parsed.direction.name,
            source = parsed.source,
            sender = parsed.sender,
            timestampMillis = timestampMillis,
            rawText = parsed.rawText
        )
        context.dataStore.edit { prefs ->
            val current = prefs[historyKey]
                ?.let { runCatching { Json.decodeFromString<List<BalanceEntry>>(it) }.getOrNull() }
                ?: emptyList()
            // dedupe: skip if last entry has same balance + same raw text within 60s
            val last = current.firstOrNull()
            if (last != null &&
                last.balance == entry.balance &&
                last.rawText == entry.rawText &&
                (entry.timestampMillis - last.timestampMillis) < 60_000
            ) return@edit
            val updated = (listOf(entry) + current).take(maxHistory)
            prefs[historyKey] = Json.encodeToString(updated)
        }
    }

    suspend fun clear() {
        context.dataStore.edit { it.remove(historyKey) }
    }

    suspend fun latestSnapshot(): BalanceEntry? = latest.first()
}
