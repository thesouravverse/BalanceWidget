package com.sourav.balancewidget.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import androidx.glance.appwidget.updateAll
import com.sourav.balancewidget.data.BalanceRepository
import com.sourav.balancewidget.parser.BalanceParser
import com.sourav.balancewidget.widget.BalanceWidget
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class BalanceSmsReceiver : BroadcastReceiver() {

    @Inject lateinit var repo: BalanceRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        // Group by sender to combine multipart SMS
        val bySender = messages.groupBy { it.originatingAddress.orEmpty() }
        for ((sender, parts) in bySender) {
            val body = parts.joinToString("") { it.messageBody.orEmpty() }
            val parsed = BalanceParser.parse(
                text = body,
                source = "SMS",
                sender = sender
            ) ?: continue
            scope.launch {
                repo.add(parsed)
                runCatching { BalanceWidget().updateAll(context) }
            }
        }
    }
}
