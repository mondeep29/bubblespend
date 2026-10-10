package com.bubblespend.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bubblespend.app.R
import com.bubblespend.app.ui.theme.Butter
import com.bubblespend.app.ui.theme.Coral
import com.bubblespend.app.ui.theme.Ink
import com.bubblespend.app.ui.theme.Lavender
import com.bubblespend.app.ui.theme.Mint
import com.bubblespend.app.ui.theme.MutedInk
import com.bubblespend.app.ui.theme.Peach
import com.bubblespend.app.ui.theme.Sky
import com.bubblespend.core.application.CategorySnapshot
import com.bubblespend.core.application.Expense
import com.bubblespend.core.application.ExpenseSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.math.BigInteger
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID

private val categoryColors = listOf(Peach, Lavender, Mint, Sky, Butter)

@Composable
internal fun ReferenceHome(
    state: ExpenseAppState,
    model: ExpenseAppViewModel,
) {
    var selectedCategory by rememberSaveable { mutableStateOf<String?>(null) }
    var showCategoryList by rememberSaveable { mutableStateOf(false) }
    var showBubbles by rememberSaveable { mutableStateOf(true) }
    val scroll = rememberScrollState()
    val currency = state.currency
    var summaryFailed by remember(state.expenses, currency) { mutableStateOf(false) }
    var snapshot by remember(state.expenses, currency) { mutableStateOf<ExpenseSnapshot?>(null) }
    LaunchedEffect(state.expenses, currency) {
        if (currency != null) {
            try {
                snapshot = withContext(Dispatchers.Default) { ExpenseSnapshot.from(state.expenses, currency) }
            } catch (_: IllegalArgumentException) {
                summaryFailed = true
            }
        }
    }
    val current = snapshot
    val categoryTints = current?.categories.orEmpty().mapIndexed { index, group -> group.categoryId to categoryColors[index % categoryColors.size] }.toMap()
    val records =
        state.expenses.filter {
            it.deletedAt == null && (selectedCategory == null || it.categoryId.toString() == selectedCategory)
        }
    val visibleGroups = if (showCategoryList) current?.categories.orEmpty() else current?.categories.orEmpty().take(4)
    val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(scroll).padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            BrandHeader(onAction = model::openSettings)
            if (!state.setupCompleted || !state.settingsKnown) {
                Surface(color = Butter.copy(alpha = 0.4f), shape = RoundedCornerShape(18.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.setup_unfinished), style = MaterialTheme.typography.bodyMedium)
                        if (!state.settingsKnown) {
                            state.setupMessage?.let { Text(stringResource(it)) }
                            Button(onClick = model::retrySettings) { Text(stringResource(R.string.retry)) }
                        }
                        OutlinedButton(onClick = model::openSetup) { Text(stringResource(R.string.return_to_setup)) }
                    }
                }
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .testTag("overview_total")
                    .clickable {
                        selectedCategory = null
                        showBubbles = false
                    }.padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(stringResource(R.string.total_spending), style = MaterialTheme.typography.titleMedium)
                Text(
                    if (current != null) formatAmount(current.totalMinorUnits, current.currency) else "—",
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                )
                Text(stringResource(R.string.money_story), color = MutedInk, style = MaterialTheme.typography.bodyMedium)
                Surface(color = Color.White, shape = CircleShape, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                    Text(
                        stringResource(R.string.all_time),
                        Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            if (summaryFailed) Text(stringResource(R.string.summary_unavailable), color = MaterialTheme.colorScheme.error)
            if (showBubbles && visibleGroups.isNotEmpty()) {
                visibleGroups.chunked(columns).forEach { groups ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        groups.forEach { group ->
                            CategoryBubble(
                                group,
                                categoryName(state, group.categoryId),
                                current!!,
                                categoryTints.getValue(group.categoryId),
                                selectedCategory == group.categoryId.toString(),
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    selectedCategory = group.categoryId.toString()
                                    showBubbles = false
                                },
                            )
                        }
                        if (groups.size < columns) Spacer(Modifier.weight(1f))
                    }
                }
            }
            if (current?.categories?.isNotEmpty() == true) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        if (selectedCategory ==
                            null
                        ) {
                            stringResource(R.string.recent_expenses)
                        } else {
                            categoryName(state, UUID.fromString(selectedCategory))
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(onClick = {
                        selectedCategory = null
                        showBubbles = !showBubbles
                        showCategoryList = true
                    }) {
                        Text(
                            stringResource(if (showBubbles) R.string.view_expenses else R.string.by_category),
                            color = Color(0xFFBB3E2F),
                        )
                    }
                }
            }
            if (records.isEmpty()) {
                EmptyExpenses(onAdd = { model.openEntry() })
            } else {
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = Color.White,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Column {
                        records.forEachIndexed { index, expense ->
                            ExpenseRow(expense, state, categoryTints[expense.categoryId] ?: Peach, onClick = { model.selectExpense(expense) })
                            if (index !=
                                records.lastIndex
                            ) {
                                HorizontalDivider(
                                    Modifier.padding(horizontal = 16.dp),
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                )
                            }
                        }
                    }
                }
            }
            state.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.retry != null) Button(onClick = model::retry) { Text(stringResource(R.string.retry)) }
            Spacer(Modifier.height(8.dp))
        }
        state.undoExpense?.let { deleted ->
            LaunchedEffect(deleted.id) {
                delay(5000)
                model.expireUndo()
            }
            Surface(color = Peach) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.expense_deleted))
                    TextButton(onClick = model::undo) { Text(stringResource(R.string.undo), color = Ink) }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            Button(
                onClick = { model.openEntry() },
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Ink),
            ) {
                BubbleIcon(BubbleGlyph.PLUS, size = 20.dp)
                Spacer(Modifier.width(10.dp))
                Text(stringResource(R.string.add_expense))
            }
        }
        HomeNavigation(model)
    }
}

@Composable
private fun CategoryBubble(
    group: CategorySnapshot,
    name: String,
    snapshot: ExpenseSnapshot,
    color: Color,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.heightIn(min = 126.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(64.dp),
        color = color,
        border = if (selected) BorderStroke(2.dp, Ink) else null,
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BubbleIcon(categoryGlyph(name), color = Ink.copy(alpha = 0.75f))
            Text(name, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Text(
                formatAmount(group.totalMinorUnits, snapshot.currency),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
            )
            Text(
                pluralStringResource(R.plurals.expense_count, group.expenses.size, group.expenses.size),
                style = MaterialTheme.typography.labelSmall,
                color = MutedInk,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ExpenseRow(
    expense: Expense,
    state: ExpenseAppState,
    color: Color,
    onClick: () -> Unit,
) {
    val name = categoryName(state, expense.categoryId)
    val method = state.methods.find { it.id == expense.paymentMethodId }?.name
    val date = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(expense.occurredAt)
    val largeText = LocalDensity.current.fontScale > 1.3f
    val amount = formatAmount(BigInteger.valueOf(expense.amount.minorUnits), expense.amount.currency)
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(color, RoundedCornerShape(13.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BubbleIcon(categoryGlyph(name), size = 22.dp)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(expense.merchant.ifEmpty { name }, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(listOfNotNull(date, method).joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MutedInk)
            if (largeText) Text(amount, style = MaterialTheme.typography.labelLarge)
        }
        if (!largeText) Text(amount, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun EmptyExpenses(onAdd: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = Color.White, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(Modifier.size(72.dp).background(Peach, CircleShape), contentAlignment = Alignment.Center) {
                BubbleIcon(BubbleGlyph.WALLET, size = 32.dp)
            }
            Text(stringResource(R.string.no_expenses), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.empty_expense_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedInk,
                textAlign = TextAlign.Center,
            )
            TextButton(onClick = onAdd) { Text(stringResource(R.string.record_first_expense), color = Color(0xFFBB3E2F)) }
        }
    }
}

@Composable
private fun HomeNavigation(model: ExpenseAppViewModel) {
    Surface(color = Color.White) {
        Column {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Top) {
                NavigationItem(BubbleGlyph.HOME, stringResource(R.string.home_title), Modifier.weight(1f), selected = true, onClick = {})
                NavigationItem(
                    BubbleGlyph.GRID,
                    stringResource(R.string.manage_categories),
                    Modifier.weight(1f),
                    onClick = model::manageCategories,
                )
                NavigationItem(
                    BubbleGlyph.CARD,
                    stringResource(R.string.manage_payment_methods),
                    Modifier.weight(1f),
                    onClick = model::manageMethods,
                )
                NavigationItem(
                    BubbleGlyph.SETTINGS,
                    stringResource(R.string.settings_title),
                    Modifier.weight(1f),
                    onClick = model::openSettings,
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    glyph: BubbleGlyph,
    label: String,
    modifier: Modifier,
    selected: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier
            .heightIn(min = 56.dp)
            .then(if (selected) Modifier.testTag("home_route").semantics { heading() } else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        BubbleIcon(glyph, color = if (selected) Color(0xFFBB3E2F) else MutedInk, size = 22.dp)
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) Color(0xFFBB3E2F) else MutedInk,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun categoryName(
    state: ExpenseAppState,
    id: UUID,
): String = state.categoryLabels[id] ?: state.categories.find { it.id == id }?.name ?: stringResource(R.string.archived_category)
