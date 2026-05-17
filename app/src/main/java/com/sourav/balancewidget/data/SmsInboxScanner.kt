package com.sourav.balancewidget.data

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.sourav.balancewidget.parser.BalanceParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans the SMS inbox once and routes each parsed transaction to whichever
 * configured account matches the suffix. Post-calibration txns adjust balance;
 * pre-calibration txns are stored as history-only.
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
        val appliedToBalance: Int,
        val historyOnly: Int,
        val topSuffixes: List<Pair<String, Int>>
    )

    suspend fun scanInbox(maxMessages: Int = 1000): ScanResult {
        val accounts = repo.accountsSnapshot()
        if (accounts.isEmpty()) {
            return ScanResult(0, 0, 0, 0, 0, 0, emptyList())
        }

        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $maxMessages"

        // Triple<date, parsed, matchedAccountCalibratedAt>
        data class Candidate(
            val date: Long,
            val parsed: BalanceParser.ParsedTxn,
            val accountCalibratedAt: Long
        )

        val candidates = mutableListOf<Candidate>()
        val suffixCounts = mutableMapOf<String, Int>()
        var totalScanned = 0
        var bankMessages = 0
        var parsedTxns = 0

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
                val matched = accounts.firstOrNull { acct ->
                    suffix.endsWith(acct.suffix) || acct.suffix.endsWith(suffix)
                } ?: continue
                candidates += Candidate(date, parsed, matched.calibratedAt)
            }
        }

        // oldest first so balance evolves chronologically
        candidates.sortBy { it.date }
        var appliedToBalance = 0
        var historyOnly = 0
        for (c in candidates) {
            val applyToBalance = c.date >= c.accountCalibratedAt
            val ok = repo.applyTxn(c.parsed, timestampMillis = c.date, applyToBalance = applyToBalance)
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
            matchingTxns = candidates.size,
            appliedToBalance = appliedToBalance,
            historyOnly = historyOnly,
            topSuffixes = topSuffixes
        )
    }
}
