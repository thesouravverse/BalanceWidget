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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sourav.balancewidget.data.Account
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

    var selectedAccountId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(state.accounts) {
        if (selectedAccountId == null || state.accounts.none { it.id == selectedAccountId }) {
            selectedAccountId = state.accounts.firstOrNull()?.id
        }
    }

    var showAccountDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }
    var confirmDeleteId by remember { mutableStateOf<String?>(null) }

    if (showAccountDialog) {
        AccountEditorDialog(
            existing = editingAccount,
            onDismiss = { showAccountDialog = false; editingAccount = null },
            onSave = { label, suffix, balance ->
                vm.addOrUpdateAccount(editingAccount?.id, label, suffix, balance)
                showAccountDialog = false
                editingAccount = null
            }
        )
    }

    confirmDeleteId?.let { id ->
        val acct = state.accounts.firstOrNull { it.id == id }
        AlertDialog(
            onDismissRequest = { confirmDeleteId = null },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteAccount(id)
                    confirmDeleteId = null
                    if (selectedAccountId == id) selectedAccountId = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteId = null }) { Text("Cancel") }
            },
            title = { Text("Delete account?") },
            text = { Text("Remove ${acct?.label ?: "this account"} and all of its history. This can't be undone.") }
        )
    }

    val selectedAccount = state.accounts.firstOrNull { it.id == selectedAccountId }
    val selectedHistory = remember(state.history, selectedAccountId) {
        state.history.filter { it.accountId == selectedAccountId }
            .sortedByDescending { it.timestampMillis }
    }
    val selectedLatest = selectedAccountId?.let { state.latestByAccount[it] }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "Balance Widget",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        // Account selector
        if (state.accounts.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScrollable(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.accounts.forEach { acct ->
                    FilterChip(
                        selected = acct.id == selectedAccountId,
                        onClick = { selectedAccountId = acct.id },
                        label = { Text("${acct.label}") }
                    )
                }
                OutlinedButton(
                    onClick = { editingAccount = null; showAccountDialog = true },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", fontSize = 13.sp)
                }
            }
        }

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
                    selectedAccount?.label ?: "No account yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    selectedLatest?.let { formatMoney(it.balance) }
                        ?: selectedAccount?.let { formatMoney(it.startingBalance) }
                        ?: "—",
                    fontSize = 44.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                selectedLatest?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Updated " + relativeTime(it.timestampMillis) + " • via " + it.source,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                selectedAccount?.let {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            editingAccount = it
                            showAccountDialog = true
                        }) {
                            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Re-calibrate")
                        }
                        OutlinedButton(onClick = { confirmDeleteId = it.id }) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Delete")
                        }
                    }
                }
            }
        }

        if (state.accounts.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Add your first account", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Enter today's balance and the last 4 digits of the bank account to track. " +
                            "Repeat for each account.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = { editingAccount = null; showAccountDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Add account")
                    }
                }
            }
        }

        // Permission setup
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Setup", fontWeight = FontWeight.SemiBold)
                Button(onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }) {
                    Text(if (isNotifListenerEnabled(context)) "Notif access ✓ (review)" else "Open Notification Access")
                }
                OutlinedButton(onClick = {
                    val perms = mutableListOf(
                        Manifest.permission.RECEIVE_SMS,
                        Manifest.permission.READ_SMS
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        perms += Manifest.permission.POST_NOTIFICATIONS
                    }
                    smsPermLauncher.launch(perms.toTypedArray())
                }) { Text("Grant SMS access") }
                Text(
                    "Then long-press home → Widgets → drag 'Balance Widget'.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Widget opacity slider
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Widget look", fontWeight = FontWeight.SemiBold)
                Text(
                    "Background opacity — drag left to see more of your wallpaper through the widget.",
                    style = MaterialTheme.typography.bodySmall
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        value = state.widgetOpacity,
                        onValueChange = { vm.setWidgetOpacity(it) },
                        valueRange = 0.1f..1.0f,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text("${(state.widgetOpacity * 100).toInt()}%", fontWeight = FontWeight.Medium)
                }
                Text(
                    "Tip: changes apply next time the widget refreshes (or remove + re-add it).",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        // Sync past SMS
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Sync past SMS", fontWeight = FontWeight.SemiBold)
                Text(
                    "Scans the inbox once. Messages after calibration adjust the balance; older ones are kept as history-only.",
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
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Test the parser", fontWeight = FontWeight.SemiBold)
                Text(
                    "Injects sample HDFC SMS for the currently selected account.",
                    style = MaterialTheme.typography.bodySmall
                )
                val activeSuffix = selectedAccount?.suffix ?: "9504"
                OutlinedButton(onClick = {
                    vm.addTestMessage(
                        "Sent Rs.500.00 From HDFC Bank A/C *$activeSuffix To Test On ${
                            SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date())
                        }"
                    )
                }) { Text("Inject debit ₹500") }
                OutlinedButton(onClick = {
                    vm.addTestMessage(
                        "Update! INR 1,200.00 deposited in HDFC Bank A/c XX$activeSuffix on today."
                    )
                }) { Text("Inject credit ₹1,200") }
                TextButton(onClick = { vm.resetAll() }) { Text("Reset everything") }
            }
        }

        Text("Recent transactions", fontWeight = FontWeight.SemiBold)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (selectedHistory.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No transactions yet. Calibrate above, then scan SMS or wait for the next bank message.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            } else {
                selectedHistory.forEach { entry ->
                    TxnRow(entry)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun AccountEditorDialog(
    existing: Account?,
    onDismiss: () -> Unit,
    onSave: (label: String, suffix: String, balance: Double) -> Unit
) {
    var label by remember { mutableStateOf(existing?.label ?: "") }
    var suffix by remember { mutableStateOf(existing?.suffix ?: "") }
    var balanceText by remember { mutableStateOf(existing?.startingBalance?.let { "%.2f".format(it) } ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add account" else "Re-calibrate ${existing.label}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text("Label (e.g. HDFC Savings)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = suffix,
                    onValueChange = { suffix = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Account last 4 digits") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = { balanceText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("Current balance (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (existing != null) {
                    Text(
                        "Re-calibrating wipes this account's history and stamps a fresh start.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            val balanceVal = balanceText.toDoubleOrNull()
            TextButton(
                enabled = balanceVal != null && suffix.length >= 4,
                onClick = { onSave(label.trim(), suffix, balanceVal!!) }
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
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
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isDebit || isCredit) {
                    Icon(
                        imageVector = if (isDebit) Icons.Filled.ArrowUpward else Icons.Filled.ArrowDownward,
                        contentDescription = null,
                        tint = color
                    )
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

/** Tiny helper for the horizontally scrolling chip row. */
@Composable
private fun Modifier.horizontalScrollable(): Modifier {
    val state = rememberScrollState()
    return this.then(androidx.compose.foundation.horizontalScroll(state))
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
