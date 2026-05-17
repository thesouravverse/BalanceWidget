package com.sourav.balancewidget.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.sourav.balancewidget.MainActivity
import com.sourav.balancewidget.data.Account
import com.sourav.balancewidget.data.BalanceEntry
import com.sourav.balancewidget.data.BalanceRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class BalanceWidget : GlanceAppWidget() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface WidgetEntryPoint {
        fun balanceRepository(): BalanceRepository
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repo = EntryPointAccessors
            .fromApplication(context.applicationContext, WidgetEntryPoint::class.java)
            .balanceRepository()
        val accounts: List<Account> = repo.accounts.first()
        val latestByAccount: Map<String, BalanceEntry> = repo.latestByAccount.first()
        val opacity: Float = repo.widgetOpacity.first()

        provideContent {
            Content(accounts = accounts, latestByAccount = latestByAccount, opacity = opacity)
        }
    }

    @Composable
    private fun Content(
        accounts: List<Account>,
        latestByAccount: Map<String, BalanceEntry>,
        opacity: Float
    ) {
        val openApp = actionStartActivity<MainActivity>()
        val bg = Color(0xFF0A0A0A).copy(alpha = opacity.coerceIn(0.1f, 1.0f))

        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bg)
                .cornerRadius(20.dp)
                .clickable(openApp)
                .padding(12.dp)
        ) {
            if (accounts.isEmpty()) {
                EmptyState()
            } else if (accounts.size == 1) {
                val acct = accounts[0]
                SingleAccount(acct, latestByAccount[acct.id])
            } else {
                LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                    items(accounts) { acct ->
                        AccountRow(acct, latestByAccount[acct.id])
                    }
                }
            }
        }
    }

    @Composable
    private fun EmptyState() {
        Column(
            modifier = GlanceModifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Tap to set up",
                style = TextStyle(color = onSurface, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            )
            Text(
                text = "Add your first account",
                style = TextStyle(color = onSurfaceMuted, fontSize = 11.sp)
            )
        }
    }

    @Composable
    private fun SingleAccount(acct: Account, latest: BalanceEntry?) {
        Column(
            modifier = GlanceModifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = acct.label,
                style = TextStyle(color = onSurfaceMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = formatMoney(latest?.balance ?: acct.startingBalance),
                style = TextStyle(color = onSurface, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            )
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = latest?.let { timeAgo(it.timestampMillis) } ?: "no txns yet",
                style = TextStyle(color = onSurfaceMuted, fontSize = 11.sp)
            )
        }
    }

    @Composable
    private fun AccountRow(acct: Account, latest: BalanceEntry?) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = acct.label,
                    style = TextStyle(color = onSurface, fontSize = 13.sp, fontWeight = FontWeight.Medium),
                    maxLines = 1
                )
                Text(
                    text = latest?.let { timeAgo(it.timestampMillis) } ?: "calibrated",
                    style = TextStyle(color = onSurfaceMuted, fontSize = 10.sp),
                    maxLines = 1
                )
            }
            Spacer(GlanceModifier.width(8.dp))
            Text(
                text = formatMoney(latest?.balance ?: acct.startingBalance),
                style = TextStyle(color = onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                maxLines = 1
            )
        }
    }

    companion object {
        private val onSurface = ColorProvider(Color(0xFFE8EAED))
        private val onSurfaceMuted = ColorProvider(Color(0xB3E8EAED))
    }
}

private fun formatMoney(amount: Double): String {
    val nf = NumberFormat.getInstance(Locale("en", "IN"))
    nf.maximumFractionDigits = 2
    nf.minimumFractionDigits = 2
    return "₹" + nf.format(amount)
}

private fun timeAgo(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    val mins = TimeUnit.MILLISECONDS.toMinutes(diff)
    val hrs = TimeUnit.MILLISECONDS.toHours(diff)
    val days = TimeUnit.MILLISECONDS.toDays(diff)
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        hrs < 24 -> "${hrs}h ago"
        else -> "${days}d ago"
    }
}
