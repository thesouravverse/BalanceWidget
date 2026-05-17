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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
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
    val scanStatus by vm.scanStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val smsPermLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { /* ignored */ }

    var startBalText by remember { mutableStateOf("") }
    var suffixText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
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
                    state.config?.let { "Account *${it.accountSuffix}" } ?: "Not calibrated",
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

        // Calibration
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Calibrate balance", fontWeight = FontWeight.SemiBold)
                Text(
                    "Enter today's balance + the last 4 digits of the bank account you want to track. " +
                        "From now on, every debit/credit SMS for THAT account adjusts the balance.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = startBalText,
                    onValueChange = { startBalText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Current balance (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = suffixText,
                    onValueChange = { suffixText = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Account last 4 digits (e.g. 9504)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    enabled = startBalText.toDoubleOrNull() != null && suffixText.length >= 4,
                    onClick = {
                        vm.calibrate(startBalText.toDouble(), suffixText)
                    }
                ) { Text(if (state.config == null) "Save calibration" else "Re-calibrate") }
                state.config?.let {
                    Text(
                        "Active: ₹%,.2f starting · account *%s · calibrated %s"
                            .format(it.startingBalance, it.accountSuffix, relativeTime(it.calibratedAt)),
                        style = MaterialTheme.typography.bodySmall
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
                Button(
                    onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }
                ) { Text(if (isNotifListenerEnabled(context)) "Notif access ✓ (review)" else "Open Notification Access") }

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
                    "Then long-press home → Widgets → drag 'Balance Widget'.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Sync past SMS
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Sync past SMS", fontWeight = FontWeight.SemiBold)
                Text(
                    "Scans existing SMS since calibration time, applies every debit/credit for the tracked account.",
                    style = MaterialTheme.typography.bodySmall
                )
                Button(onClick = { vm.syncPastSms() }) {
                    Text("Scan SMS inbox now")
                }
                scanStatus?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        // Tests
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Test the parser", fontWeight = FontWeight.SemiBold)
                Text(
                    "Inject sample HDFC SMS (uses your tracked account suffix).",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedButton(onClick = {
                    val suffix = state.config?.accountSuffix ?: "9504"
                    vm.addTestMessage(
                        "Sent Rs.500.00 From HDFC Bank A/C *$suffix To Test On ${
                            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
                        }"
                    )
                }) { Text("Inject debit ₹500") }
                OutlinedButton(onClick = {
                    val suffix = state.config?.accountSuffix ?: "9504"
                    vm.addTestMessage(
                        "Update! INR 1,200.00 deposited in HDFC Bank A/c XX$suffix on today."
                    )
                }) { Text("Inject credit ₹1,200") }
                TextButton(onClick = { vm.resetAll() }) { Text("Reset everything") }
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
                            "No transactions yet. Calibrate above, then scan SMS or wait for the next bank message.",
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
    val isCredit = e.direction == "CREDIT"
    val color = when {
        isDebit -> Color(0xFFD32F2F)
        isCredit -> Color(0xFF2E7D32)
        else -> MaterialTheme.colorScheme.onSurface
    }
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
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDebit || isCredit) {
                    Icon(
                        imageVector = if (isDebit) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = color
                    )
                    Spacer(Modifier.height(0.dp))
                }
                Text(
                    e.txnAmount?.let { (if (isDebit) "−" else "+") + " ₹" + formatNumber(it) }
                        ?: e.direction,
                    color = color,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(formatMoney(e.balance), fontWeight = FontWeight.Bold)
        }
        Text(
            relativeTime(e.timestampMillis) + " · " + e.source +
                (e.accountSuffix?.let { " · *$it" } ?: ""),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

internal fun formatMoney(v: Double): String {
    val fmt = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return fmt.format(v)
}

private fun formatNumber(v: Double): String =
    NumberFormat.getNumberInstance(Locale("en", "IN")).format(v)

internal fun relativeTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    return when {
        diff < 60_000 -> "just now"
        diff < 3_600_000 -> "${diff / 60_000} min ago"
        diff < 86_400_000 -> "${diff / 3_600_000} h ago"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000} d ago"
        else -> SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(timestamp))
    }
}

internal fun isNotifListenerEnabled(context: Context): Boolean {
    val flat = Settings.Secure.getString(
        context.contentResolver,
        "enabled_notification_listeners"
    ) ?: return false
    if (TextUtils.isEmpty(flat)) return false
    val pkg = context.packageName
    return flat.split(":").any { it.contains(pkg) }
}
