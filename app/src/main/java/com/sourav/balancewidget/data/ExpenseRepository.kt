package com.sourav.balancewidget.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deliberately a SEPARATE DataStore file from BalanceRepository's "balance_widget".
 * BalanceRepository.resetAll() and clearAccountHistory() wipe that store, so sharing
 * it would mean re-calibrating an account silently deletes the user's expense log.
 */
private val Context.expenseDataStore by preferencesDataStore(name = "expenses")

@Serializable
data class Expense(
    val id: String,
    val amount: Double,
    val category: String,
    val note: String = "",
    val timestampMillis: Long = System.currentTimeMillis(),
    val source: String = "MANUAL",   // reserved: future AUTO import from parsed bank txns
    val txnKey: String? = null       // reserved: dedupe key when AUTO import lands
)

@Serializable
data class ExpenseCategory(
    val name: String,
    val budget: Double = 0.0         // reserved: budget gauges, later slice
)

@Singleton
class ExpenseRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val expensesKey = stringPreferencesKey("expenses_json")
    private val categoriesKey = stringPreferencesKey("categories_json")

    private val maxExpenses = 5000

    /** Newest first. */
    val expenses: Flow<List<Expense>> = context.expenseDataStore.data.map { prefs ->
        decodeExpenses(prefs).sortedByDescending { it.timestampMillis }
    }

    val categories: Flow<List<ExpenseCategory>> = context.expenseDataStore.data.map { prefs ->
        decodeCategories(prefs)
    }

    suspend fun addExpense(
        amount: Double,
        category: String,
        note: String = "",
        timestampMillis: Long = System.currentTimeMillis()
    ) {
        if (amount <= 0.0) return
        val label = category.trim().ifBlank { DEFAULT_CATEGORY }
        val expense = Expense(
            id = UUID.randomUUID().toString(),
            amount = amount,
            category = label,
            note = note.trim(),
            timestampMillis = timestampMillis
        )
        context.expenseDataStore.edit { prefs ->
            val updated = (listOf(expense) + decodeExpenses(prefs))
                .sortedByDescending { it.timestampMillis }
                .take(maxExpenses)
            prefs[expensesKey] = Json.encodeToString(updated)
        }
    }

    suspend fun deleteExpense(id: String) {
        context.expenseDataStore.edit { prefs ->
            val remaining = decodeExpenses(prefs).filterNot { it.id == id }
            prefs[expensesKey] = Json.encodeToString(remaining)
        }
    }

    suspend fun updateExpenseCategory(id: String, category: String) {
        val label = category.trim()
        if (label.isBlank()) return
        context.expenseDataStore.edit { prefs ->
            val updated = decodeExpenses(prefs).map { expense ->
                if (expense.id == id) expense.copy(category = label) else expense
            }
            prefs[expensesKey] = Json.encodeToString(updated)
        }
    }

    /** Case-insensitive dedupe; blanks ignored. */
    suspend fun addCategory(name: String) {
        val label = name.trim()
        if (label.isBlank()) return
        context.expenseDataStore.edit { prefs ->
            val current = decodeCategories(prefs)
            if (current.any { it.name.equals(label, ignoreCase = true) }) return@edit
            prefs[categoriesKey] = Json.encodeToString(current + ExpenseCategory(label))
        }
    }

    /** Removes the category only — expenses already tagged with it keep their label. */
    suspend fun removeCategory(name: String) {
        context.expenseDataStore.edit { prefs ->
            val remaining = decodeCategories(prefs)
                .filterNot { it.name.equals(name, ignoreCase = true) }
            prefs[categoriesKey] = Json.encodeToString(remaining)
        }
    }

    suspend fun setBudget(name: String, budget: Double) {
        context.expenseDataStore.edit { prefs ->
            val updated = decodeCategories(prefs).map { category ->
                if (category.name.equals(name, ignoreCase = true)) {
                    category.copy(budget = budget.coerceAtLeast(0.0))
                } else category
            }
            prefs[categoriesKey] = Json.encodeToString(updated)
        }
    }

    suspend fun clearAllExpenses() {
        context.expenseDataStore.edit { prefs ->
            prefs[expensesKey] = Json.encodeToString(emptyList<Expense>())
        }
    }

    // -------- helpers --------

    private fun decodeExpenses(prefs: Preferences): List<Expense> =
        prefs[expensesKey]?.let {
            runCatching { Json.decodeFromString<List<Expense>>(it) }.getOrNull()
        } ?: emptyList()

    private fun decodeCategories(prefs: Preferences): List<ExpenseCategory> =
        prefs[categoriesKey]?.let {
            runCatching { Json.decodeFromString<List<ExpenseCategory>>(it) }.getOrNull()
        } ?: DEFAULT_CATEGORIES

    companion object {
        const val DEFAULT_CATEGORY = "Other"

        val DEFAULT_CATEGORIES: List<ExpenseCategory> = listOf(
            "Food", "Transport", "Shopping", "Bills", "Fun", "Health", "Fees", "Other"
        ).map { ExpenseCategory(it) }
    }
}
