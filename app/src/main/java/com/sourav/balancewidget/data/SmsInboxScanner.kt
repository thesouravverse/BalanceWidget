package com.sourav.balancewidget.data

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import com.sourav.balancewidget.parser.BalanceParser
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads past SMS from the device inbox, parses bank messages, and seeds the
 * repository with historical entries. Requires READ_SMS permission.
 *
 * Returns a summary of what was found.
 */
@Singleton
class SmsInboxScanner @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repo: BalanceRepository
) {
    data class ScanResult(
        val totalScanned: Int,
        val bankMessages: Int,
        val parsedWithBalance: Int,
        val latestBalance: Double? = null
    )

    suspend fun scanInbox(maxMessages: Int = 500): ScanResult {
        val uri: Uri = Telephony.Sms.Inbox.CONTENT_URI
        val projection = arrayOf(
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE
        )
        // Newest first so we cap at maxMessages
        val sortOrder = "${Telephony.Sms.DATE} DESC LIMIT $maxMessages"

        val parsedList = mutableListOf<Pair<Long, BalanceParser.Parsed>>()
        var totalScanned = 0
        var bankMessages = 0

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
                val parsed = BalanceParser.parse(body, source = "SMS", sender = sender) ?: continue
                parsedList += date to parsed
            }
        }

        // Insert oldest-first so the most recent ends up at the top of history.
        parsedList.sortBy { it.first }
        for ((date, p) in parsedList) {
            repo.add(p, timestampMillis = date)
        }

        return ScanResult(
            totalScanned = totalScanned,
            bankMessages = bankMessages,
            parsedWithBalance = parsedList.size,
            latestBalance = parsedList.maxByOrNull { it.first }?.second?.balance
        )
    }
}
