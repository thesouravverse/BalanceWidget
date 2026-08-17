package com.sourav.balancewidget.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sourav.balancewidget.data.Expense
import com.sourav.balancewidget.data.ExpenseCategory
import com.sourav.balancewidget.data.ExpenseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject

data class CategoryTotal(
    val name: String,
    val total: Double
)

data class ExpenseUiState(
    val month: YearMonth = YearMonth.now(),
    val categories: List<ExpenseCategory> = emptyList(),
    val monthExpenses: List<Expense> = emptyList(),
    val categoryTotals: List<CategoryTotal> = emptyList(),
    val monthTotal: Double = 0.0
) {
    val isCurrentMonth: Boolean get() = month == YearMonth.now()
}

@HiltViewModel
class ExpenseViewModel @Inject constructor(
    private val repo: ExpenseRepository
) : ViewModel() {

    private val _month = MutableStateFlow(YearMonth.now())

    val uiState: StateFlow<ExpenseUiState> = combine(
        repo.expenses,
        repo.categories,
        _month
    ) { expenses, categories, month ->
        val monthExpenses = expenses.filter { yearMonthOf(it.timestampMillis) == month }
        val spentByLabel = monthExpenses.groupBy { it.category }
            .mapValues { (_, list) -> list.sumOf { it.amount } }

        // Every configured category, plus any label that only survives on an expense —
        // removing a category must never hide the spend already tagged with it.
        val labels = LinkedHashSet<String>()
        categories.forEach { labels += it.name }
        monthExpenses.forEach { labels += it.category }

        val totals = labels
            .map { CategoryTotal(name = it, total = spentByLabel[it] ?: 0.0) }
            .sortedByDescending { it.total }

        ExpenseUiState(
            month = month,
            categories = categories,
            monthExpenses = monthExpenses,
            categoryTotals = totals,
            monthTotal = monthExpenses.sumOf { it.amount }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpenseUiState())

    fun showPreviousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun showNextMonth() {
        val next = _month.value.plusMonths(1)
        if (next <= YearMonth.now()) _month.value = next
    }

    fun jumpToCurrentMonth() {
        _month.value = YearMonth.now()
    }

    fun addExpense(amount: Double, category: String, note: String) {
        if (amount <= 0.0) return
        val month = _month.value
        // Logging while browsing a past month should file the entry in that month,
        // not today — back-date it to noon on that month's last day.
        val timestamp = if (month == YearMonth.now()) {
            System.currentTimeMillis()
        } else {
            month.atEndOfMonth()
                .atTime(LocalTime.NOON)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }
        viewModelScope.launch(Dispatchers.IO) {
            repo.addExpense(
                amount = amount,
                category = category,
                note = note,
                timestampMillis = timestamp
            )
        }
    }

    fun deleteExpense(id: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.deleteExpense(id)
    }

    fun recategorize(id: String, category: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.updateExpenseCategory(id, category)
    }

    fun addCategory(name: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.addCategory(name)
    }

    fun removeCategory(name: String) = viewModelScope.launch(Dispatchers.IO) {
        repo.removeCategory(name)
    }

    private fun yearMonthOf(timestampMillis: Long): YearMonth =
        YearMonth.from(
            Instant.ofEpochMilli(timestampMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        )
}
