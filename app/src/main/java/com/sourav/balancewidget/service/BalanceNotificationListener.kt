package com.sourav.balancewidget.service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
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
class BalanceNotificationListener : NotificationListenerService() {

    @Inject lateinit var repo: BalanceRepository
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        val extras = sbn.notification.extras
        val title = extras.getCharSequence("android.title")?.toString().orEmpty()
        val text = extras.getCharSequence("android.text")?.toString().orEmpty()
        val bigText = extras.getCharSequence("android.bigText")?.toString().orEmpty()
        val combined = listOf(title, text, bigText).filter { it.isNotBlank() }.joinToString(" \n ")
        if (combined.isBlank()) return

        val parsed = BalanceParser.parseTxn(
            text = combined,
            source = "NOTIFICATION",
            sender = sbn.packageName
        ) ?: return

        scope.launch {
            if (repo.applyTxn(parsed)) {
                runCatching { BalanceWidget().updateAll(applicationContext) }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) { /* no-op */ }
}
