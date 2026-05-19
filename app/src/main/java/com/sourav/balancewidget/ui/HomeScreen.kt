@file:OptIn(ExperimentalMaterial3Api::class)

package com.sourav.balancewidget.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sourav.balancewidget.data.Account
import com.sourav.balancewidget.data.BalanceEntry
import com.sourav.balancewidget.ui.theme.CredPalette
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    vm: HomeViewModel = hiltViewModel()
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
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
    var advancedExpanded by remember { mutableStateOf(false) }

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
            text = {
                Text(
                    "Remove ${acct?.label ?: "this account"} and all of its history. " +
                        "This can't be undone."
                )
            }
        )
    }

    val selectedAccount = state.accounts.firstOrNull { it.id == selectedAccountId }
    val selectedHistory = remember(state.history, selectedAccountId) {
        state.history
            .filter { it.accountId == selectedAccountId }
            .sortedByDescending { it.timestampMillis }
    }
    val selectedLatest = selectedAccountId?.let { state.latestByAccount[it] }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ---- Accounts row ----
        if (state.accounts.isNotEmpty()) {
            AccountChipsRow(
                accounts = state.accounts,
                selectedId = selectedAccountId,
                onSelect = { selectedAccountId = it },
                onAdd = { editingAccount = null; showAccountDialog = true }
            )
        }

        // ---- Hero balance card ----
        HeroBalanceCard(
            account = selectedAccount,
            latest = selectedLatest,
            onEdit = {
                selectedAccount?.let {
                    editingAccount = it
                    showAccountDialog = true
                }
            },
            onDelete = { selectedAccount?.let { confirmDeleteId = it.id } },
            onAddFirst = { editingAccount = null; showAccountDialog = true }
        )

        // ---- Transactions ----
        SectionHeader(
            title = if (selectedAccount != null) "Recent activity" else "No account"
        )

        TransactionsCard(history = selectedHistory)

        // ---- Widget look (always visible — primary new feature) ----
        WidgetLookCard(
            opacity = state.widgetOpacity,
            onOpacityChange = vm::setWidgetOpacity
        )

        // ---- Advanced (collapsible) ----
        ExpandableSection(
            title = "Advanced",
            icon = Icons.Filled.Tune,
            expanded = advancedExpanded,
            onToggle = { advancedExpanded = !advancedExpanded }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SetupCard(
                    notifEnabled = isNotifListenerEnabled(context),
                    onOpenNotifSettings = {
                        context.startActivity(
                            Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    },
                    onGrantSms = {
                        val perms = mutableListOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                        )
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            perms += Manifest.permission.POST_NOTIFICATIONS
                        }
                        smsPermLauncher.launch(perms.toTypedArray())
                    }
                )
                OutlinedButton(
                    onClick = { vm.resetAll() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, CredPalette.Border),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = CredPalette.Danger
                    )
                ) {
                    Text("RESET EVERYTHING", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

// -------------------- Components --------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountChipsRow(
    accounts: List<Account>,
    selectedId: String?,
    onSelect: (String) -> Unit,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        accounts.forEach { acct ->
            FilterChip(
                selected = acct.id == selectedId,
                onClick = { onSelect(acct.id) },
                label = {
                    Text(
                        acct.label,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.2.sp
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.AccountBalanceWallet,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                },
                shape = RoundedCornerShape(50),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = acct.id == selectedId,
                    borderColor = CredPalette.Border,
                    selectedBorderColor = CredPalette.Gold,
                    borderWidth = 1.dp,
                    selectedBorderWidth = 1.dp
                ),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = CredPalette.Surface,
                    labelColor = CredPalette.TextSecondary,
                    iconColor = CredPalette.TextSecondary,
                    selectedContainerColor = CredPalette.Gold.copy(alpha = 0.12f),
                    selectedLabelColor = CredPalette.Gold,
                    selectedLeadingIconColor = CredPalette.Gold
                )
            )
        }
        AssistChip(
            onClick = onAdd,
            label = { Text("Add", fontWeight = FontWeight.Medium) },
            leadingIcon = {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(14.dp))
            },
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, CredPalette.Border),
            colors = AssistChipDefaults.assistChipColors(
                containerColor = CredPalette.Surface,
                labelColor = CredPalette.TextPrimary,
                leadingIconContentColor = CredPalette.TextPrimary
            )
        )
    }
}

@Composable
private fun HeroBalanceCard(
    account: Account?,
    latest: BalanceEntry?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onAddFirst: () -> Unit
) {
    val gradient = Brush.linearGradient(
        colors = listOf(CredPalette.HeroStart, CredPalette.HeroEnd)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(28.dp))
            .border(1.dp, CredPalette.Border, RoundedCornerShape(28.dp))
    ) {
        if (account == null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    tint = Color.White
                )
                Text(
                    "NO ACCOUNT YET",
                    color = Color.White,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp
                )
                Text(
                    "add your bank account to start tracking",
                    color = Color.White.copy(alpha = 0.75f),
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onAddFirst,
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = CredPalette.Gold,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("ADD ACCOUNT", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "AVAILABLE BALANCE",
                            color = Color.White.copy(alpha = 0.7f),
                            style = MaterialTheme.typography.labelSmall,
                            letterSpacing = 1.5.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            account.label,
                            color = Color.White,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row {
                        IconButton(onClick = onEdit) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = "Re-calibrate",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = onDelete) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete",
                                tint = Color.White
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    formatMoney(latest?.balance ?: account.startingBalance),
                    color = Color.White,
                    fontSize = 46.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = (-1).sp
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    latest?.let {
                        "updated " + relativeTime(it.timestampMillis).lowercase() + " · via " + it.source.lowercase()
                    } ?: "calibrated " + relativeTime(account.calibratedAt).lowercase(),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    trailing: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = CredPalette.TextMuted,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp
        )
        trailing?.invoke()
    }
}

@Composable
private fun TransactionsCard(history: List<BalanceEntry>) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CredPalette.Border, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = CredPalette.Surface
    ) {
        Column {
            if (history.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(28.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No transactions yet.\nCalibrate or wait for the next bank message.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                history.forEachIndexed { idx, entry ->
                    TxnRow(entry)
                    if (idx < history.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun WidgetLookCard(
    opacity: Float,
    onOpacityChange: (Float) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CredPalette.Border, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = CredPalette.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Tune,
                    contentDescription = null,
                    tint = CredPalette.Gold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "WIDGET LOOK",
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${(opacity * 100).toInt()}%",
                    fontWeight = FontWeight.Bold,
                    color = CredPalette.Gold,
                    style = MaterialTheme.typography.titleMedium
                )
            }
            Text(
                "background opacity — drag left to see wallpaper through the widget",
                style = MaterialTheme.typography.bodySmall,
                color = CredPalette.TextSecondary
            )
            Slider(
                value = opacity,
                onValueChange = onOpacityChange,
                valueRange = 0.1f..1.0f,
                colors = androidx.compose.material3.SliderDefaults.colors(
                    thumbColor = CredPalette.Gold,
                    activeTrackColor = CredPalette.Gold,
                    inactiveTrackColor = CredPalette.Border
                )
            )
        }
    }
}

@Composable
private fun ExpandableSection(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "expand")
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, CredPalette.Border, RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = CredPalette.Surface,
            onClick = onToggle
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = CredPalette.Gold,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    title.uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = if (expanded) "Collapse" else "Expand",
                    modifier = Modifier.rotate(rotation),
                    tint = CredPalette.TextSecondary
                )
            }
        }
        AnimatedVisibility(visible = expanded) {
            content()
        }
    }
}

@Composable
private fun SetupCard(
    notifEnabled: Boolean,
    onOpenNotifSettings: () -> Unit,
    onGrantSms: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CredPalette.Border, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = CredPalette.Surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Notifications,
                    contentDescription = null,
                    tint = CredPalette.Gold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "PERMISSIONS",
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    style = MaterialTheme.typography.labelMedium
                )
            }
            Button(
                onClick = onOpenNotifSettings,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = CredPalette.Gold,
                    contentColor = Color.Black
                )
            ) {
                Text(
                    if (notifEnabled) "NOTIFICATION ACCESS ✓" else "OPEN NOTIFICATION ACCESS",
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
            OutlinedButton(
                onClick = onGrantSms,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, CredPalette.Border),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = CredPalette.TextPrimary
                )
            ) {
                Icon(Icons.Filled.Sms, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("GRANT SMS ACCESS", fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            Text(
                "then long-press home → widgets → drag 'balance widget'",
                style = MaterialTheme.typography.bodySmall,
                color = CredPalette.TextMuted
            )
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
    var balanceText by remember {
        mutableStateOf(existing?.startingBalance?.let { "%.2f".format(it) } ?: "")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CredPalette.Surface,
        titleContentColor = CredPalette.TextPrimary,
        textContentColor = CredPalette.TextSecondary,
        title = {
            Text(
                if (existing == null) "ADD ACCOUNT" else "RE-CALIBRATE",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        },
        text = {
            val fieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CredPalette.Gold,
                unfocusedBorderColor = CredPalette.Border,
                focusedLabelColor = CredPalette.Gold,
                unfocusedLabelColor = CredPalette.TextMuted,
                cursorColor = CredPalette.Gold,
                focusedTextColor = CredPalette.TextPrimary,
                unfocusedTextColor = CredPalette.TextPrimary
            )
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it.take(40) },
                    label = { Text("Label (e.g. HDFC Savings)") },
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = suffix,
                    onValueChange = { suffix = it.filter { c -> c.isDigit() }.take(6) },
                    label = { Text("Account last 4 digits") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = balanceText,
                    onValueChange = {
                        balanceText = it.filter { c -> c.isDigit() || c == '.' }
                    },
                    label = { Text("Current balance (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = fieldColors,
                    modifier = Modifier.fillMaxWidth()
                )
                if (existing != null) {
                    Text(
                        "re-calibrating wipes this account's history and stamps a fresh start.",
                        style = MaterialTheme.typography.bodySmall,
                        color = CredPalette.TextMuted
                    )
                }
            }
        },
        confirmButton = {
            val balanceVal = balanceText.toDoubleOrNull()
            Button(
                enabled = balanceVal != null && suffix.length >= 4,
                onClick = { onSave(label.trim(), suffix, balanceVal!!) },
                shape = RoundedCornerShape(50),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = CredPalette.Gold,
                    contentColor = Color.Black,
                    disabledContainerColor = CredPalette.Border,
                    disabledContentColor = CredPalette.TextMuted
                )
            ) { Text("SAVE", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = androidx.compose.material3.ButtonDefaults.textButtonColors(
                    contentColor = CredPalette.TextSecondary
                )
            ) { Text("CANCEL", letterSpacing = 1.sp) }
        }
    )
}

@Composable
private fun TxnRow(e: BalanceEntry) {
    val isDebit = e.direction == "DEBIT"
    val isCredit = e.direction == "CREDIT"
    val color = when {
        isDebit -> CredPalette.Danger
        isCredit -> CredPalette.Success
        else -> CredPalette.TextSecondary
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(color.copy(alpha = 0.12f), RoundedCornerShape(50))
                .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(50)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isCredit) Icons.Filled.ArrowDownward else Icons.Filled.ArrowUpward,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                e.txnAmount?.let {
                    (if (isDebit) "−₹" else if (isCredit) "+₹" else "₹") + formatNumber(it)
                } ?: e.direction,
                color = color,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                relativeTime(e.timestampMillis).lowercase() + " · " + e.source.lowercase() +
                    (e.accountSuffix?.let { " · *$it" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = CredPalette.TextMuted
            )
        }
        Text(
            formatMoney(e.balance),
            fontWeight = FontWeight.Bold,
            color = CredPalette.TextPrimary,
            style = MaterialTheme.typography.titleSmall
        )
    }
}

// -------------------- Helpers --------------------

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
