package com.sourav.balancewidget.data

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.sourav.balancewidget.parser.BalanceParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads past SMS from device inbox and applies bank transactions to the repo.
 * Only txns at-or-after calibration time AND matching the configured account suffix are applied.
 */
@Singleton
class SmsInboxScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: BalanceRepository
) {
    data class ScanResult(
        val totalScanned: Int,
        val bankMessages: Int,
        val matchingTxns: Int,
        val applied: Int
    )

    suspend fun scanInbox(maxMessages: Int = 1000): ScanResult {
        val cfg = repo.configSnapshot()
            ?: return ScanResult(0, 0, 0, 0)

        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $maxMessages"

        val candidates = mutableListOf<Triple<Long, String, BalanceParser.ParsedTxn>>()
        var totalScanned = 0
        var bankMessages = 0
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
                if (date < cfg.calibratedAt) continue
                val parsed = BalanceParser.parseTxn(body, source = "SMS", sender = sender) ?: continue
                val suffix = parsed.accountSuffix ?: continue
                if (!suffix.endsWith(cfg.accountSuffix) && !cfg.accountSuffix.endsWith(suffix)) continue
                matchingTxns++
                candidates += Triple(date, sender, parsed)
            }
        }

        // Reset history to opening seed, then apply oldest-first so balance walks forward
        repo.clearHistoryKeepConfig()
        candidates.sortBy { it.first }
        var applied = 0
        for ((date, _, p) in candidates) {
            if (repo.applyTxn(p, timestampMillis = date)) applied++
        }

        return ScanResult(
            totalScanned = totalScanned,
            bankMessages = bankMessages,
            matchingTxns = matchingTxns,
            applied = applied
        )
    }
}
