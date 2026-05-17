package com.sourav.balancewidget.ui

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sourav.balancewidget.data.Account
import com.sourav.balancewidget.data.BalanceEntry
import com.sourav.balancewidget.data.BalanceRepository
import com.sourav.balancewidget.data.SmsInboxScanner
import com.sourav.balancewidget.parser.BalanceParser
import com.sourav.balancewidget.widget.BalanceWidget
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val accounts: List<Account> = emptyList(),
    val history: List<BalanceEntry> = emptyList(),
    val latestByAccount: Map<String, BalanceEntry> = emptyMap(),
    val widgetOpacity: Float = 1f
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repo: BalanceRepository,
    private val smsScanner: SmsInboxScanner
) : ViewModel() {

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repo.migrateLegacyIfNeeded()
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        repo.accounts,
        repo.history,
        repo.latestByAccount,
        repo.widgetOpacity
    ) { accounts, history, latest, opacity ->
        HomeUiState(
            accounts = accounts,
            history = history,
            latestByAccount = latest,
            widgetOpacity = opacity
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _scanStatus = MutableStateFlow<String?>(null)
    val scanStatus: StateFlow<String?> = _scanStatus.asStateFlow()

    fun addOrUpdateAccount(
        id: String?,
        label: String,
        suffix: String,
        startingBalance: Double
    ) = viewModelScope.launch(Dispatchers.IO) {
        repo.addOrUpdateAccount(id = id, label = label, suffix = suffix, startingBalance = startingBalance)
        BalanceWidget().updateAll(appContext)
        _scanStatus.value = "Calibrated. Future SMS for *$suffix will adjust this balance."
    }

    fun deleteAccount(id: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.deleteAccount(id)
        BalanceWidget().updateAll(appContext)
    }

    fun setWidgetOpacity(opacity: Float) = viewModelScope.launch(Dispatchers.IO) {
        repo.setWidgetOpacity(opacity)
        BalanceWidget().updateAll(appContext)
    }

    fun resetAll() = viewModelScope.launch(Dispatchers.IO) {
        repo.resetAll()
        BalanceWidget().updateAll(appContext)
        _scanStatus.value = null
    }

    fun addTestMessage(raw: String) = viewModelScope.launch(Dispatchers.IO) {
        val parsed = BalanceParser.parseTxn(raw, source = "TEST", sender = "TEST")
        if (parsed == null) {
            _scanStatus.value = "Parser couldn't extract a txn from that text."
            return@launch
        }
        val applied = repo.applyTxn(parsed)
        BalanceWidget().updateAll(appContext)
        _scanStatus.value = if (applied) "Test txn applied." else
            "Test ignored — add an account whose suffix matches first."
    }

    fun syncPastSms() = viewModelScope.launch(Dispatchers.IO) {
        val accounts = repo.accountsSnapshot()
        if (accounts.isEmpty()) {
            _scanStatus.value = "Add at least one account first."
            return@launch
        }
        _scanStatus.value = "Scanning inbox…"
        try {
            val r = smsScanner.scanInbox()
            val topStr = if (r.topSuffixes.isEmpty()) "none"
                else r.topSuffixes.joinToString(", ") { "*${it.first}(${it.second})" }
            val acctList = accounts.joinToString(", ") { "*${it.suffix}" }
            _scanStatus.value = buildString {
                append("Scanned ${r.totalScanned} SMS · ${r.bankMessages} bank · parsed ${r.parsedTxns}\n")
                append("Top suffixes found: $topStr\n")
                append("Tracking: $acctList\n")
                append("Matched: ${r.matchingTxns} · applied: ${r.appliedToBalance} · history-only: ${r.historyOnly}")
            }
            BalanceWidget().updateAll(appContext)
        } catch (e: SecurityException) {
            _scanStatus.value = "Need SMS permission — tap 'Grant SMS access' first."
        } catch (e: Exception) {
            _scanStatus.value = "Scan failed: ${e.message}"
        }
    }
}
