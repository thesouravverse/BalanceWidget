package com.sourav.balancewidget.data

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.sourav.balancewidget.parser.BalanceParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads past SMS from device inbox and seeds history for the configured account.
 * Past txns are stored for VIEWING only — they do NOT change the calibrated balance,
 * because the user calibrated to the *current* balance which already reflects them.
 */
@Singleton
class SmsInboxScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: BalanceRepository
) {
    data class ScanResult(
        val totalScanned: Int,
        val bankMessages: Int,
        val parsedTxns: Int,
        val matchingTxns: Int,
        val appliedToBalance: Int, // post-calibration, changed balance
        val historyOnly: Int,      // pre-calibration, info only
        val topSuffixes: List<Pair<String, Int>>
    )

    suspend fun scanInbox(maxMessages: Int = 1000): ScanResult {
        val cfg = repo.configSnapshot()
            ?: return ScanResult(0, 0, 0, 0, 0, 0, emptyList())

        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $maxMessages"

        val candidates = mutableListOf<Triple<Long, String, BalanceParser.ParsedTxn>>()
        val suffixCounts = mutableMapOf<String, Int>()
        var totalScanned = 0
        var bankMessages = 0
        var parsedTxns = 0
        var matchingTxns = 0

        context.contentResolver.query(uri, projection, null, null, sortOrder)?.use { cursor ->
            val addrIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
            val bodyIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
            val dateIdx = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
            while (cursor.moveToNext()) {
                totalScanned++
                val sender = cursor.getString(addrIdx) ?: continue
                val body = cursor.getString(bodyIdx) ?: continue
                val date = cursor.getLong(dateIdx)
                if (!BalanceParser.looksLikeBankMessage(body, sender)) continue
                bankMessages++
                val parsed = BalanceParser.parseTxn(body, source = "SMS", sender = sender) ?: continue
                parsedTxns++
                val suffix = parsed.accountSuffix ?: continue
                suffixCounts[suffix] = (suffixCounts[suffix] ?: 0) + 1
                if (!suffix.endsWith(cfg.accountSuffix) && !cfg.accountSuffix.endsWith(suffix)) continue
                matchingTxns++
                candidates += Triple(date, sender, parsed)
            }
        }

        // Apply oldest-first so history list stays chronological.
        // Post-calibration txns: deduct/credit from balance (the real monthly tracker).
        // Pre-calibration txns: stored as history-only (no balance change) — already
        // baked into the user-entered current balance.
        candidates.sortBy { it.first }
        var appliedToBalance = 0
        var historyOnly = 0
        for ((date, _, p) in candidates) {
            val applyToBalance = date >= cfg.calibratedAt
            val ok = repo.applyTxn(p, timestampMillis = date, applyToBalance = applyToBalance)
            if (ok) {
                if (applyToBalance) appliedToBalance++ else historyOnly++
            }
        }

        val topSuffixes = suffixCounts.entries
            .sortedByDescending { it.value }
            .take(5)
            .map { it.key to it.value }

        return ScanResult(
            totalScanned = totalScanned,
            bankMessages = bankMessages,
            parsedTxns = parsedTxns,
            matchingTxns = matchingTxns,
            appliedToBalance = appliedToBalance,
            historyOnly = historyOnly,
            topSuffixes = topSuffixes
        )
    }
}
