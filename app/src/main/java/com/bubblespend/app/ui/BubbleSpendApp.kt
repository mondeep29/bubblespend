package com.bubblespend.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.bubblespend.app.R
import com.bubblespend.app.ui.theme.Coral
import com.bubblespend.app.ui.theme.Ink
import com.bubblespend.core.application.ExpenseField
import java.time.DayOfWeek
import java.util.Currency
import java.util.Locale

@Composable
fun BubbleSpendApp(dependencies: ExpenseAppDependencies? = null) {
    val context = LocalContext.current
    val model: ExpenseAppViewModel = viewModel(factory = ExpenseAppViewModel.factory(context, dependencies))
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, model) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_STOP) model.expireUndo()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    BackHandler(enabled = state.page == AppPage.SETUP || state.page == AppPage.SETTINGS) { model.leaveSettings() }
    Surface(Modifier.fillMaxSize().testTag("home_surface"), color = MaterialTheme.colorScheme.background) {
        if (state.loading) {
            Text(stringResource(R.string.loading), Modifier.padding(24.dp))
        } else if (state.startupError) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.storage_unavailable))
                ActionButton(onClick = model::start) { Text(stringResource(R.string.retry)) }
            }
        } else {
            when {
                state.page == AppPage.SETUP || state.page == AppPage.SETTINGS -> SetupAndSettings(state, model)
                state.page == AppPage.HOME -> Home(state, model)
                state.page == AppPage.ENTRY -> Entry(state, model)
                state.page == AppPage.DETAIL -> Detail(state, model)
                state.page == AppPage.CATEGORIES -> CategoryManagement(state, model)
                state.page == AppPage.PAYMENT_METHODS -> PaymentManagement(state, model)
                state.page == AppPage.MANAGEMENT_FORM -> ManagementForm(state, model)
            }
        }
    }
}

@Composable private fun SetupAndSettings(
    state: ExpenseAppState,
    model: ExpenseAppViewModel,
) {
    val setup = !state.setupCompleted
    val keyboard = LocalSoftwareKeyboardController.current
    val currencyRequester = remember { FocusRequester() }
    LaunchedEffect(state.currencyError) {
        if (state.currencyError) currencyRequester.requestFocus()
    }
    Column(
        Modifier.fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandHeader(onAction = model::leaveSettings, close = true)
        Text(
            stringResource(if (setup) R.string.setup_title else R.string.settings_title),
            Modifier.semantics { heading() }.testTag("settings_route"),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (state.currency != null) {
            Text(stringResource(R.string.fixed_currency, state.currency.value))
            Text(stringResource(R.string.currency_migration_body))
        } else {
            Text(stringResource(R.string.confirm_currency_body))
            OutlinedTextField(
                state.currencyChoice,
                model::chooseCurrency,
                label = { Text(stringResource(R.string.currency_code)) },
                modifier = Modifier.fillMaxWidth().focusRequester(currencyRequester),
                enabled = state.settingsKnown && !state.settingsSaving,
                isError = state.currencyError,
                supportingText =
                    if (state.currencyError) {
                        { Text(stringResource(R.string.select_valid_currency)) }
                    } else {
                        null
                    },
                singleLine = true,
            )
            state.suggestedCurrency?.let { Text(stringResource(R.string.suggested_currency, it.value)) }
        }
        WeekStartChoice(state, model)
        if (state.weekError) Text(stringResource(R.string.select_valid_week_start))
        state.setupMessage?.let { Text(stringResource(it)) }
        if (state.settingsRetry != null) {
            ActionButton(onClick = model::retrySettings, enabled = !state.settingsSaving) { Text(stringResource(R.string.retry)) }
        }
        ActionButton(
            onClick = {
                keyboard?.hide()
                if (setup) model.completeSetup() else model.saveWeekStart()
            },
            enabled = state.settingsKnown && !state.settingsSaving,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                stringResource(
                    if (state.settingsSaving) {
                        R.string.saving
                    } else if (setup) {
                        R.string.complete_setup
                    } else {
                        R.string.save_week_start
                    },
                ),
            )
        }
        ActionOutlinedButton(onClick = model::leaveSettings, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (setup) R.string.browse_app else R.string.close))
        }
    }
}

@Composable private fun WeekStartChoice(
    state: ExpenseAppState,
    model: ExpenseAppViewModel,
) {
    var expanded by remember { mutableStateOf(false) }
    val requester = remember { FocusRequester() }
    LaunchedEffect(state.weekError) { if (state.weekError) requester.requestFocus() }
    Text(stringResource(R.string.week_start))
    ActionOutlinedButton(
        onClick = { expanded = !expanded },
        enabled = state.settingsKnown && !state.settingsSaving,
        modifier = Modifier.fillMaxWidth().focusRequester(requester).testTag("week_start_choice"),
    ) {
        Text(
            if (state.weekChoice in
                1..7
            ) {
                weekdayLabel(DayOfWeek.of(state.weekChoice))
            } else {
                stringResource(R.string.select_valid_week_start)
            },
        )
    }
    if (expanded) {
        DayOfWeek.entries.forEach { day ->
            ActionOutlinedButton(
                onClick = {
                    model.chooseWeek(day.value)
                    expanded = false
                },
                enabled = !state.settingsSaving,
                modifier = Modifier.fillMaxWidth().testTag("week_day_" + day.value),
            ) { Text(weekdayLabel(day)) }
        }
    }
}

@Composable private fun weekdayLabel(day: DayOfWeek): String =
    stringResource(
        when (day) {
            DayOfWeek.MONDAY -> R.string.weekday_monday
            DayOfWeek.TUESDAY -> R.string.weekday_tuesday
            DayOfWeek.WEDNESDAY -> R.string.weekday_wednesday
            DayOfWeek.THURSDAY -> R.string.weekday_thursday
            DayOfWeek.FRIDAY -> R.string.weekday_friday
            DayOfWeek.SATURDAY -> R.string.weekday_saturday
            DayOfWeek.SUNDAY -> R.string.weekday_sunday
        },
    )

@Composable private fun Home(
    state: ExpenseAppState,
    model: ExpenseAppViewModel,
) {
    ReferenceHome(state, model)
}

@Composable private fun Entry(
    state: ExpenseAppState,
    model: ExpenseAppViewModel,
) {
    val draft = state.draft ?: return
    var categoryMenu by remember { mutableStateOf(false) }
    var methodMenu by remember { mutableStateOf(false) }
    val category = state.categories.find { it.id == draft.categoryId }
    val method = state.methods.find { it.id == draft.paymentMethodId }
    val firstError =
        listOf(
            ExpenseField.AMOUNT,
            ExpenseField.CATEGORY,
            ExpenseField.PAYMENT_METHOD,
            ExpenseField.OCCURRED_AT,
            ExpenseField.MERCHANT,
            ExpenseField.NOTE,
        ).firstOrNull { it in state.errors }
    val requesters = remember { ExpenseField.entries.associateWith { FocusRequester() } }
    LaunchedEffect(state.errors) {
        firstError?.let { field -> runCatching { requesters.getValue(field).requestFocus() } }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandHeader(onAction = model::closeEntry, close = true)
        Text(
            if (state.editingExpense == null) stringResource(R.string.add_expense) else stringResource(R.string.edit_expense),
            style = MaterialTheme.typography.titleLarge,
        )
        OutlinedTextField(
            draft.amountText,
            { model.updateDraft { old -> old.copy(amountText = it) } },
            supportingText = { Text(stringResource(R.string.amount)) },
            modifier =
                fieldModifier(ExpenseField.AMOUNT in state.errors, firstError == ExpenseField.AMOUNT)
                    .fillMaxWidth()
                    .focusRequester(requesters.getValue(ExpenseField.AMOUNT)),
            isError = ExpenseField.AMOUNT in state.errors,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            textStyle = MaterialTheme.typography.displaySmall,
            placeholder = { Text("0", style = MaterialTheme.typography.displaySmall) },
            prefix = {
                Text(
                    state.currency
                        ?.let {
                            Currency.getInstance(it.value).getSymbol(Locale.getDefault())
                        }.orEmpty(),
                    style = MaterialTheme.typography.displaySmall,
                )
            },
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Coral,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                ),
        )
        if (ExpenseField.AMOUNT in state.errors) Text(stringResource(R.string.amount_error))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f)) {
                ActionOutlinedButton(
                    onClick = { categoryMenu = true },
                    modifier =
                        fieldModifier(ExpenseField.CATEGORY in state.errors, firstError == ExpenseField.CATEGORY)
                            .focusRequester(requesters.getValue(ExpenseField.CATEGORY)),
                ) { Text(stringResource(R.string.category)) }
                category?.let { Text(it.name) }
                if (categoryMenu) {
                    state.categories.forEach { item ->
                        ActionOutlinedButton(
                            onClick = {
                                model.updateDraft { it.copy(categoryId = item.id) }
                                categoryMenu = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(item.name) }
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                ActionOutlinedButton(
                    onClick = { methodMenu = true },
                    modifier =
                        fieldModifier(
                            ExpenseField.PAYMENT_METHOD in state.errors,
                            firstError == ExpenseField.PAYMENT_METHOD,
                        ).focusRequester(requesters.getValue(ExpenseField.PAYMENT_METHOD)),
                ) { Text(stringResource(R.string.payment_method)) }
                method?.let { Text(it.name) }
                if (methodMenu) {
                    state.methods.forEach { item ->
                        ActionOutlinedButton(
                            onClick = {
                                model.updateDraft { it.copy(paymentMethodId = item.id) }
                                methodMenu = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(item.name) }
                    }
                }
            }
        }
        if (state.categories.isEmpty() || category == null) {
            ActionOutlinedButton(onClick = model::manageCategories, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_categories)) }
        }
        if (state.methods.isEmpty() || method == null) {
            ActionOutlinedButton(onClick = model::manageMethods, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.manage_payment_methods)) }
        }
        OutlinedTextField(
            state.occurredAtText,
            model::updateOccurredAtText,
            label = { Text(stringResource(R.string.occurred_at)) },
            modifier =
                fieldModifier(
                    ExpenseField.OCCURRED_AT in state.errors,
                    firstError == ExpenseField.OCCURRED_AT,
                ).fillMaxWidth().focusRequester(requesters.getValue(ExpenseField.OCCURRED_AT)),
            isError = ExpenseField.OCCURRED_AT in state.errors,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        if (ExpenseField.OCCURRED_AT in state.errors) Text(stringResource(R.string.occurred_at_error))
        OutlinedTextField(
            draft.merchant,
            { model.updateDraft { old -> old.copy(merchant = it) } },
            label = { Text(stringResource(R.string.merchant)) },
            modifier =
                fieldModifier(ExpenseField.MERCHANT in state.errors, firstError == ExpenseField.MERCHANT)
                    .fillMaxWidth()
                    .focusRequester(requesters.getValue(ExpenseField.MERCHANT)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        OutlinedTextField(
            draft.note,
            { model.updateDraft { old -> old.copy(note = it) } },
            label = { Text(stringResource(R.string.note)) },
            modifier =
                fieldModifier(ExpenseField.NOTE in state.errors, firstError == ExpenseField.NOTE)
                    .fillMaxWidth()
                    .focusRequester(requesters.getValue(ExpenseField.NOTE)),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { model.save() }),
        )
        state.message?.let { Text(it) }
        if (!state.settingsKnown || !state.currencyPersisted) {
            Text(stringResource(R.string.confirm_currency_before_save))
            ActionOutlinedButton(onClick = model::openSetup) { Text(stringResource(R.string.return_to_setup)) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ActionButton(
                onClick = model::save,
                modifier = Modifier.weight(1f),
                enabled =
                    !state.saving &&
                        state.categories.isNotEmpty() &&
                        state.methods.isNotEmpty() &&
                        state.settingsKnown &&
                        state.currencyPersisted,
            ) {
                Text(if (state.saving) stringResource(R.string.saving) else stringResource(R.string.save))
            }
            ActionOutlinedButton(onClick = model::closeEntry) { Text(stringResource(R.string.close)) }
        }
        RetryButton(state, model)
        if (state.discardPrompt) {
            AlertDialog(
                onDismissRequest = model::keepEditing,
                title = { Text(stringResource(R.string.discard_title)) },
                confirmButton = { ActionButton(onClick = model::discardEntry) { Text(stringResource(R.string.discard)) } },
                dismissButton = { ActionOutlinedButton(onClick = model::keepEditing) { Text(stringResource(R.string.keep_editing)) } },
            )
        }
    }
}

private fun fieldModifier(
    hasError: Boolean,
    announce: Boolean = hasError,
): Modifier =
    if (announce && hasError) {
        Modifier.semantics { error("Invalid field") }
    } else {
        Modifier
    }

@Composable private fun Detail(
    state: ExpenseAppState,
    model: ExpenseAppViewModel,
) {
    val expense = state.selectedExpense ?: return
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BrandHeader(onAction = model::backHome, close = true)
        Text(stringResource(R.string.expense_heading), style = MaterialTheme.typography.headlineMedium)
        Text(
            formatAmount(java.math.BigInteger.valueOf(expense.amount.minorUnits), expense.amount.currency),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(expense.merchant)
        Text(
            state.categoryLabels[expense.categoryId] ?: state.categories
                .find { it.id == expense.categoryId }
                ?.name
                .orEmpty(),
        )
        ActionButton(onClick = { model.openEntry(expense) }) { Text(stringResource(R.string.edit)) }
        ActionButton(onClick = { model.openEntry(expense, duplicate = true) }) { Text(stringResource(R.string.duplicate)) }
        ActionButton(onClick = model::delete) { Text(stringResource(R.string.delete)) }
        ActionOutlinedButton(onClick = model::backHome) { Text(stringResource(R.string.close)) }
        state.message?.let { Text(it) }
        RetryButton(state, model)
    }
}

@Composable private fun CategoryManagement(
    state: ExpenseAppState,
    model: ExpenseAppViewModel,
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandHeader(onAction = model::backHome, close = true)
        Text(stringResource(R.string.manage_categories), style = MaterialTheme.typography.headlineMedium)
        ActionButton(onClick = { model.openManagementForm(category = true) }) { Text(stringResource(R.string.create_category)) }
        if (state.categories.isEmpty()) Text(stringResource(R.string.no_active_categories))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            state.categories.forEach { category ->
                key(category.id) {
                    var expanded by remember { mutableStateOf(false) }
                    Column {
                        Text(category.name, Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded })
                        if (expanded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionButton(onClick = { model.openManagementForm(category.id, category = true) }) {
                                    Text(stringResource(R.string.rename))
                                }
                                ActionOutlinedButton(
                                    onClick = { model.archiveCategory(category.id) },
                                ) { Text(stringResource(R.string.archive)) }
                            }
                        }
                    }
                }
            }
        }
        ActionOutlinedButton(onClick = model::backHome) { Text(stringResource(R.string.close)) }
        state.message?.let { Text(it) }
        RetryButton(state, model)
    }
}

@Composable private fun PaymentManagement(
    state: ExpenseAppState,
    model: ExpenseAppViewModel,
) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        BrandHeader(onAction = model::backHome, close = true)
        Text(stringResource(R.string.manage_payment_methods), style = MaterialTheme.typography.headlineMedium)
        ActionButton(onClick = { model.openManagementForm(category = false) }) { Text(stringResource(R.string.create_payment_method)) }
        if (state.methods.isEmpty()) Text(stringResource(R.string.no_active_payment_methods))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            state.methods.forEach { method ->
                key(method.id) {
                    var expanded by remember { mutableStateOf(false) }
                    Column {
                        Text(method.name, Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { expanded = !expanded })
                        if (method.isDefault) Text(stringResource(R.string.default_label))
                        if (expanded) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionButton(onClick = { model.openManagementForm(method.id, category = false) }) {
                                    Text(stringResource(R.string.rename))
                                }
                                ActionOutlinedButton(onClick = { model.archiveMethod(method.id) }) {
                                    Text(stringResource(R.string.archive))
                                }
                                if (!method.isDefault) {
                                    ActionOutlinedButton(
                                        onClick = { model.setDefault(method.id) },
                                    ) { Text(stringResource(R.string.set_default)) }
                                }
                            }
                        }
                    }
                }
            }
        }
        ActionOutlinedButton(onClick = model::backHome) { Text(stringResource(R.string.close)) }
        state.message?.let { Text(it) }
        RetryButton(state, model)
    }
}

@Composable private fun ManagementForm(
    state: ExpenseAppState,
    model: ExpenseAppViewModel,
) {
    val nameRequester = remember { FocusRequester() }
    LaunchedEffect(state.managementError) {
        if (state.managementError != null) runCatching { nameRequester.requestFocus() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            if (state.managementId == null) stringResource(R.string.create) else stringResource(R.string.rename),
            style = MaterialTheme.typography.headlineMedium,
        )
        if (state.managementId != null) {
            val original =
                if (state.managementCategory) {
                    state.categories.find { it.id == state.managementId }?.name
                } else {
                    state.methods.find { it.id == state.managementId }?.name
                }
            original?.let { Text(it) }
        }
        OutlinedTextField(
            state.managementName,
            model::updateManagementName,
            label = { Text(stringResource(R.string.name)) },
            modifier = fieldModifier(state.managementError != null).focusRequester(nameRequester),
            isError = state.managementError != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { model.saveManagement() }),
        )
        state.managementError?.let { Text(it) }
        ActionButton(onClick = model::saveManagement) { Text(stringResource(R.string.save)) }
        ActionOutlinedButton(onClick = model::closeManagementForm) { Text(stringResource(R.string.close)) }
        RetryButton(state, model)
    }
}

@Composable private fun RetryButton(
    state: ExpenseAppState,
    model: ExpenseAppViewModel,
) {
    if (state.retry != null) ActionButton(onClick = model::retry) { Text(stringResource(R.string.retry)) }
}

@Composable private fun ActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier.heightIn(
                min = 48.dp,
            ),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Ink),
        content = {
            content()
        },
    )
}

@Composable private fun ActionOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier.heightIn(
                min = 48.dp,
            ),
        shape = RoundedCornerShape(16.dp),
        content = {
            content()
        },
    )
}
