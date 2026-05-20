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

        // goAsync() keeps the BroadcastReceiver alive (~10s budget) so our DataStore
        // write + Glance updateAll actually complete before the process is reaped.
        val pending = goAsync()
        scope.launch {
            try {
                val bySender = messages.groupBy { it.originatingAddress.orEmpty() }
                var anyApplied = false
                for ((sender, parts) in bySender) {
                    val body = parts.joinToString("") { it.messageBody.orEmpty() }
                    val parsed = BalanceParser.parseTxn(body, source = "SMS", sender = sender) ?: continue
                    if (repo.applyTxn(parsed)) anyApplied = true
                }
                if (anyApplied) {
                    runCatching { BalanceWidget().updateAll(context.applicationContext) }
                }
            } finally {
                pending.finish()
            }
        }
    }
}
