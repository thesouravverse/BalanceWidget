package com.sourav.balancewidget.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sourav.balancewidget.data.BalanceEntry
import com.sourav.balancewidget.data.BalanceRepository
import com.sourav.balancewidget.data.SmsInboxScanner
import com.sourav.balancewidget.parser.BalanceParser
import com.sourav.balancewidget.widget.BalanceWidget
import androidx.glance.appwidget.updateAll
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val latest: BalanceEntry? = null,
    val history: List<BalanceEntry> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val repo: BalanceRepository,
    private val smsScanner: SmsInboxScanner
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = kotlinx.coroutines.flow.combine(
        repo.latest,
        repo.history
    ) { latest, history ->
        HomeUiState(latest = latest, history = history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    private val _scanStatus = MutableStateFlow<String?>(null)
    val scanStatus: StateFlow<String?> = _scanStatus.asStateFlow()

    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { repo.clear() }

    /** Inject a fake SMS-like string for testing the parser without waiting for a real bank message. */
    fun addTestMessage(raw: String) = viewModelScope.launch(Dispatchers.IO) {
        BalanceParser.parse(raw, source = "TEST", sender = "TEST")?.let { repo.add(it) }
        BalanceWidget().updateAll(appContext)
    }

    /** Scan past SMS inbox and seed history. Requires READ_SMS perm. */
    fun syncPastSms() = viewModelScope.launch(Dispatchers.IO) {
        _scanStatus.value = "Scanning inbox…"
        try {
            val r = smsScanner.scanInbox()
            _scanStatus.value = "Scanned ${r.totalScanned} SMS · ${r.bankMessages} from banks · " +
                "${r.parsedWithBalance} with balance" +
                (r.latestBalance?.let { " · latest ₹%,.2f".format(it) } ?: "")
            BalanceWidget().updateAll(appContext)
        } catch (e: SecurityException) {
            _scanStatus.value = "Need SMS permission — tap 'Grant SMS access' first."
        } catch (e: Exception) {
            _scanStatus.value = "Scan failed: ${e.message}"
        }
    }
}

