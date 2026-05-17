package com.sourav.balancewidget.ui

import android.content.Context
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sourav.balancewidget.data.BalanceEntry
import com.sourav.balancewidget.data.BalanceRepository
import com.sourav.balancewidget.data.Config
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
    val latest: BalanceEntry? = null,
    val history: List<BalanceEntry> = emptyList(),
    val config: Config? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repo: BalanceRepository,
    private val smsScanner: SmsInboxScanner
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        repo.latest,
        repo.history,
        repo.config
    ) { latest, history, config ->
        HomeUiState(latest = latest, history = history, config = config)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _scanStatus = MutableStateFlow<String?>(null)
    val scanStatus: StateFlow<String?> = _scanStatus.asStateFlow()

    fun calibrate(startingBalance: Double, accountSuffix: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.calibrate(startingBalance, accountSuffix)
        BalanceWidget().updateAll(appContext)
        _scanStatus.value = "Calibrated. Now scan SMS or wait for the next bank message."
    }

    fun resetAll() = viewModelScope.launch(Dispatchers.IO) {
        repo.resetAll()
        BalanceWidget().updateAll(appContext)
        _scanStatus.value = null
    }

    /** Inject a fake bank SMS for parser testing. Requires calibration first. */
    fun addTestMessage(raw: String) = viewModelScope.launch(Dispatchers.IO) {
        val parsed = BalanceParser.parseTxn(raw, source = "TEST", sender = "TEST")
        if (parsed == null) {
            _scanStatus.value = "Parser couldn't extract a txn from that text."
            return@launch
        }
        val applied = repo.applyTxn(parsed)
        BalanceWidget().updateAll(appContext)
        _scanStatus.value = if (applied) "Test txn applied." else
            "Test ignored — calibrate first OR account suffix didn't match."
    }

    fun syncPastSms() = viewModelScope.launch(Dispatchers.IO) {
        val cfg = repo.configSnapshot()
        if (cfg == null) {
            _scanStatus.value = "Calibrate first (enter balance + account last-4)."
            return@launch
        }
        _scanStatus.value = "Scanning inbox…"
        try {
            val r = smsScanner.scanInbox()
            _scanStatus.value = "Scanned ${r.totalScanned} SMS · ${r.bankMessages} bank · " +
                "${r.matchingTxns} for *${cfg.accountSuffix} · applied ${r.applied}"
            BalanceWidget().updateAll(appContext)
        } catch (e: SecurityException) {
            _scanStatus.value = "Need SMS permission — tap 'Grant SMS access' first."
        } catch (e: Exception) {
            _scanStatus.value = "Scan failed: ${e.message}"
        }
    }
}
