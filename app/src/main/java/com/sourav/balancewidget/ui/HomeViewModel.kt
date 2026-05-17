package com.sourav.balancewidget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sourav.balancewidget.data.BalanceEntry
import com.sourav.balancewidget.data.BalanceRepository
import com.sourav.balancewidget.parser.BalanceParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val latest: BalanceEntry? = null,
    val history: List<BalanceEntry> = emptyList()
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repo: BalanceRepository
) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = kotlinx.coroutines.flow.combine(
        repo.latest,
        repo.history
    ) { latest, history ->
        HomeUiState(latest = latest, history = history)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun clearHistory() = viewModelScope.launch(Dispatchers.IO) { repo.clear() }

    /** Inject a fake SMS-like string for testing the parser without waiting for a real bank message. */
    fun addTestMessage(raw: String) = viewModelScope.launch(Dispatchers.IO) {
        BalanceParser.parse(raw, source = "TEST", sender = "TEST")?.let { repo.add(it) }
    }
}
