package com.sourav.balancewidget.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sourav.balancewidget.data.BalanceEntry
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val notifPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { /* result ignored */ }

    val smsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* result ignored */ }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header
        Text(
            "Balance Widget",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Big balance card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Available balance",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    state.latest?.let { formatMoney(it.balance) } ?: "—",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                state.latest?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Updated " + relativeTime(it.timestampMillis) + " • via " + it.source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        // Permission setup
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Setup", fontWeight = FontWeight.SemiBold)
                Text(
                    "1. Grant Notification Access so we can read bank notifications.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                ) { Text(if (isNotifListenerEnabled(context)) "Notif access ✓ (review)" else "Open Notification Access") }

                Text(
                    "2. (Optional) Grant SMS permission as a fallback for banks that only send raw SMS.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(
                    onClick = {
                        val perms = mutableListOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            perms += Manifest.permission.POST_NOTIFICATIONS
                        }
                        smsPermLauncher.launch(perms.toTypedArray())
                    }
                ) { Text("Grant SMS access") }

                Text(
                    "3. Long-press your home screen → Widgets → search 'Balance Widget' → drag it to home.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Debug / test row
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Test the parser", fontWeight = FontWeight.SemiBold)
                Text(
                    "Tap to inject a fake HDFC SMS so the widget updates without waiting for a real txn.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = {
                    vm.addTestMessage(
                        "Sent Rs.500.00 From HDFC Bank A/C *1234 To Test On ${
                            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
                        }. Avl Bal:Rs.12,345.67"
                    )
                }) { Text("Inject debit (500) → bal 12,345.67") }
                OutlinedButton(onClick = {
                    vm.addTestMessage(
                        "Update! INR 1,200.00 deposited in HDFC Bank A/c XX1234 . Avl bal INR 14,545.67"
                    )
                }) { Text("Inject credit (1,200) → bal 14,545.67") }
                TextButton(onClick = { vm.clearHistory() }) { Text("Clear history") }
            }
        }

        Text("Recent transactions", fontWeight = FontWeight.SemiBold)
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.history) { entry ->
                TxnRow(entry)
                HorizontalDivider()
            }
            if (state.history.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No transactions yet. Inject a test message above or wait for a bank message.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TxnRow(e: BalanceEntry) {
    val isDebit = e.direction == "DEBIT"
    val color = if (isDebit) Color(0xFFD32F2F) else Color(0xFF2E7D32)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            androidx.compose.foundation.layout.Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (e.txnAmount != null && e.direction != "UNKNOWN") {
                    Icon(
                        imageVector = if (isDebit) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                        contentDescription = null,
                        tint = color
                    )
                    Spacer(Modifier.fillMaxWidth(0.02f))
                    Text(
                        (if (isDebit) "-" else "+") + formatMoney(e.txnAmount),
                        color = color,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Text("Balance update", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("Bal " + formatMoney(e.balance), fontWeight = FontWeight.Medium)
        }
        Text(
            text = (e.sender ?: "—") + " • " + relativeTime(e.timestampMillis),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun formatMoney(amount: Double): String {
    val nf = NumberFormat.getInstance(Locale("en", "IN"))
    nf.maximumFractionDigits = 2
    nf.minimumFractionDigits = 2
    return "₹" + nf.format(amount)
}

private fun relativeTime(ts: Long): String {
    val diff = System.currentTimeMillis() - ts
    val mins = diff / 60_000
    val hrs = diff / 3_600_000
    val days = diff / 86_400_000
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "${mins}m ago"
        hrs < 24 -> "${hrs}h ago"
        else -> "${days}d ago"
    }
}

private fun isNotifListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    if (TextUtils.isEmpty(flat)) return false
    val pkg = context.packageName
    return flat.split(":").any { it.startsWith(pkg) }
}
