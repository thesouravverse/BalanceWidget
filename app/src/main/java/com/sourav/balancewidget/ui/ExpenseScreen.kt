@file:OptIn(ExperimentalMaterial3Api::class)

package com.sourav.balancewidget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sourav.balancewidget.data.Expense
import com.sourav.balancewidget.data.ExpenseRepository
import com.sourav.balancewidget.ui.theme.CredPalette
import java.text.SimpleDateFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

@Composable
fun ExpenseScreen(
    modifier: Modifier = Modifier,
    viewModel: ExpenseViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    var amountText by remember { mutableStateOf("") }
    var noteText by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf(ExpenseRepository.DEFAULT_CATEGORY) }
    var editingCategories by remember { mutableStateOf(false) }

    // Categories can be removed underneath us — never leave the picker pointing at
    // a label that no longer exists.
    LaunchedEffect(state.categories) {
        if (state.categories.isNotEmpty() &&
            state.categories.none { it.name == selectedCategory }
        ) {
            selectedCategory = state.categories.first().name
        }
    }

    val parsedAmount = amountText.replace(",", "").trim().toDoubleOrNull()
    val canAdd = parsedAmount != null && parsedAmount > 0.0

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item("month-header") {
            MonthHeader(
                month = state.month,
                isCurrentMonth = state.isCurrentMonth,
                onPrevious = viewModel::showPreviousMonth,
                onNext = viewModel::showNextMonth,
                onJumpToNow = viewModel::jumpToCurrentMonth
            )
        }

        item("month-total") {
            MonthTotalCard(
                total = state.monthTotal,
                entryCount = state.monthExpenses.size
            )
        }

        item("log-spend") {
            LogSpendCard(
                amountText = amountText,
                onAmountChange = { amountText = it },
                noteText = noteText,
                onNoteChange = { noteText = it },
                categories = state.categories.map { it.name },
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it },
                canAdd = canAdd,
                onAdd = {
                    parsedAmount?.let { amount ->
                        viewModel.addExpense(amount, selectedCategory, noteText)
                        amountText = ""
                        noteText = ""
                    }
                }
            )
        }

        item("by-category") {
            ByCategoryCard(
                totals = state.categoryTotals,
                monthTotal = state.monthTotal,
                editing = editingCategories,
                onToggleEditing = { editingCategories = !editingCategories },
                onRemoveCategory = viewModel::removeCategory,
                onAddCategory = viewModel::addCategory
            )
        }

        item("entries-header") {
            ExpenseSectionHeader(
                if (state.monthExpenses.isEmpty()) "No entries" else "Entries"
            )
        }

        if (state.monthExpenses.isEmpty()) {
            item("entries-empty") {
                EmptyEntriesCard()
            }
        } else {
            items(state.monthExpenses, key = { it.id }) { expense ->
                ExpenseRow(
                    expense = expense,
                    onDelete = { viewModel.deleteExpense(expense.id) }
                )
            }
        }

        item("footer-space") { Spacer(Modifier.height(4.dp)) }
    }
}

// -------------------- Sections --------------------

@Composable
private fun MonthHeader(
    month: YearMonth,
    isCurrentMonth: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onJumpToNow: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(
            onClick = onPrevious,
            colors = ButtonDefaults.textButtonColors(contentColor = CredPalette.TextSecondary)
        ) { Text("<", fontWeight = FontWeight.Bold) }

        Text(
            monthLabel(month).uppercase(),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = CredPalette.TextPrimary
        )

        TextButton(
            onClick = onNext,
            enabled = !isCurrentMonth,
            colors = ButtonDefaults.textButtonColors(
                contentColor = CredPalette.TextSecondary,
                disabledContentColor = CredPalette.TextMuted.copy(alpha = 0.4f)
            )
        ) { Text(">", fontWeight = FontWeight.Bold) }

        if (!isCurrentMonth) {
            TextButton(
                onClick = onJumpToNow,
                colors = ButtonDefaults.textButtonColors(contentColor = CredPalette.Gold)
            ) { Text("NOW", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
        }
    }
}

@Composable
private fun MonthTotalCard(total: Double, entryCount: Int) {
    val gradient = Brush.linearGradient(
        colors = listOf(CredPalette.HeroStart, CredPalette.HeroEnd)
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient, RoundedCornerShape(28.dp))
            .border(1.dp, CredPalette.Border, RoundedCornerShape(28.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                "SPENT THIS MONTH",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                rupees(total),
                color = Color.White,
                fontSize = 42.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-1).sp
            )
            Spacer(Modifier.height(6.dp))
            Text(
                if (entryCount == 1) "1 entry" else "$entryCount entries",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
private fun LogSpendCard(
    amountText: String,
    onAmountChange: (String) -> Unit,
    noteText: String,
    onNoteChange: (String) -> Unit,
    categories: List<String>,
    selectedCategory: String,
    onSelectCategory: (String) -> Unit,
    canAdd: Boolean,
    onAdd: () -> Unit
) {
    ExpenseCard {
        Text(
            "LOG A SPEND",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.5.sp,
            color = CredPalette.TextPrimary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = amountText,
                onValueChange = onAmountChange,
                label = { Text("Amount") },
                prefix = { Text("₹") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                colors = expenseFieldColors(),
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                value = noteText,
                onValueChange = { onNoteChange(it.take(40)) },
                label = { Text("Note") },
                singleLine = true,
                colors = expenseFieldColors(),
                modifier = Modifier.weight(1f)
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(categories, key = { it }) { name ->
                FilterChip(
                    selected = name == selectedCategory,
                    onClick = { onSelectCategory(name) },
                    label = { Text(name, fontWeight = FontWeight.Medium) },
                    shape = RoundedCornerShape(50),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = name == selectedCategory,
                        borderColor = CredPalette.Border,
                        selectedBorderColor = CredPalette.Gold,
                        borderWidth = 1.dp,
                        selectedBorderWidth = 1.dp
                    ),
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = CredPalette.SurfaceVariant,
                        labelColor = CredPalette.TextSecondary,
                        selectedContainerColor = CredPalette.Gold.copy(alpha = 0.12f),
                        selectedLabelColor = CredPalette.Gold
                    )
                )
            }
        }

        Button(
            onClick = onAdd,
            enabled = canAdd,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(50),
            colors = ButtonDefaults.buttonColors(
                containerColor = CredPalette.Gold,
                contentColor = Color.Black,
                disabledContainerColor = CredPalette.Border,
                disabledContentColor = CredPalette.TextMuted
            )
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "ADD TO ${selectedCategory.uppercase()}",
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
    }
}

@Composable
private fun ByCategoryCard(
    totals: List<CategoryTotal>,
    monthTotal: Double,
    editing: Boolean,
    onToggleEditing: () -> Unit,
    onRemoveCategory: (String) -> Unit,
    onAddCategory: (String) -> Unit
) {
    var newCategory by remember { mutableStateOf("") }

    ExpenseCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "BY CATEGORY",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.5.sp,
                color = CredPalette.TextPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onToggleEditing) {
                Icon(
                    Icons.Filled.Edit,
                    contentDescription = if (editing) "Done editing" else "Edit categories",
                    tint = if (editing) CredPalette.Gold else CredPalette.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (totals.isEmpty()) {
            Text(
                "No categories yet.",
                style = MaterialTheme.typography.bodySmall,
                color = CredPalette.TextMuted
            )
        }

        totals.forEach { entry ->
            CategoryRow(
                entry = entry,
                monthTotal = monthTotal,
                editing = editing,
                onRemove = { onRemoveCategory(entry.name) }
            )
        }

        if (editing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newCategory,
                    onValueChange = { newCategory = it.take(20) },
                    label = { Text("New category") },
                    singleLine = true,
                    colors = expenseFieldColors(),
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        onAddCategory(newCategory)
                        newCategory = ""
                    },
                    enabled = newCategory.isNotBlank(),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CredPalette.Gold,
                        contentColor = Color.Black,
                        disabledContainerColor = CredPalette.Border,
                        disabledContentColor = CredPalette.TextMuted
                    )
                ) { Text("ADD", fontWeight = FontWeight.Bold, letterSpacing = 1.sp) }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    entry: CategoryTotal,
    monthTotal: Double,
    editing: Boolean,
    onRemove: () -> Unit
) {
    val share = if (monthTotal > 0.0) (entry.total / monthTotal).toFloat().coerceIn(0f, 1f) else 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.name,
                style = MaterialTheme.typography.bodyMedium,
                color = CredPalette.TextSecondary,
                modifier = Modifier.weight(1f)
            )
            Text(
                rupees(entry.total),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = CredPalette.TextPrimary
            )
            if (editing) {
                IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Remove ${entry.name}",
                        tint = CredPalette.Danger,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        // Thin share-of-month bar.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(CredPalette.Border, RoundedCornerShape(50))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(share)
                    .height(3.dp)
                    .background(CredPalette.Gold, RoundedCornerShape(50))
            )
        }
    }
}

@Composable
private fun ExpenseRow(expense: Expense, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CredPalette.Border, RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        color = CredPalette.Surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    expense.category + if (expense.note.isNotBlank()) " · ${expense.note}" else "",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = CredPalette.TextPrimary
                )
                Text(
                    dayLabel(expense.timestampMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = CredPalette.TextMuted
                )
            }
            Text(
                rupees(expense.amount),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = CredPalette.TextPrimary
            )
            IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Delete entry",
                    tint = CredPalette.TextMuted,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyEntriesCard() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CredPalette.Border, RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        color = CredPalette.Surface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "Nothing logged for this month yet.",
                style = MaterialTheme.typography.bodySmall,
                color = CredPalette.TextMuted
            )
        }
    }
}

@Composable
private fun ExpenseSectionHeader(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = CredPalette.TextMuted,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.5.sp
    )
}

@Composable
private fun ExpenseCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

// -------------------- Helpers --------------------

@Composable
private fun expenseFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CredPalette.Gold,
    unfocusedBorderColor = CredPalette.Border,
    focusedLabelColor = CredPalette.Gold,
    unfocusedLabelColor = CredPalette.TextMuted,
    cursorColor = CredPalette.Gold,
    focusedTextColor = CredPalette.TextPrimary,
    unfocusedTextColor = CredPalette.TextPrimary
)

internal fun rupees(value: Double): String =
    "₹" + String.format(Locale.getDefault(), "%,.2f", value)

private fun monthLabel(month: YearMonth): String =
    month.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()))

private fun dayLabel(timestampMillis: Long): String =
    SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(timestampMillis))
