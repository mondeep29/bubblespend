package com.bubblespend.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.bubblespend.app.R
import com.bubblespend.app.data.PreferenceCurrencyRepository
import com.bubblespend.app.data.RoomExpenseStore
import com.bubblespend.core.application.BaseCurrencyRepository
import com.bubblespend.core.application.Category
import com.bubblespend.core.application.CategoryRepository
import com.bubblespend.core.application.Expense
import com.bubblespend.core.application.ExpenseDraft
import com.bubblespend.core.application.ExpenseEntryController
import com.bubblespend.core.application.ExpenseEntrySubmission
import com.bubblespend.core.application.ExpenseEntryValidator
import com.bubblespend.core.application.ExpenseField
import com.bubblespend.core.application.ExpenseRepository
import com.bubblespend.core.application.ExpenseValidation
import com.bubblespend.core.application.NameValidation
import com.bubblespend.core.application.PaymentMethod
import com.bubblespend.core.application.PaymentMethodRepository
import com.bubblespend.core.application.SetupSettingsController
import com.bubblespend.core.application.SetupSettingsRepository
import com.bubblespend.core.application.StoreResult
import com.bubblespend.core.domain.CurrencyCode
import com.bubblespend.core.domain.UuidGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import java.util.UUID

data class ExpenseAppDependencies(
    val expenseRepository: ExpenseRepository,
    val categoryRepository: CategoryRepository,
    val paymentMethodRepository: PaymentMethodRepository,
    val baseCurrencyRepository: BaseCurrencyRepository,
    val clock: Clock,
    val uuidGenerator: UuidGenerator,
    val locale: Locale,
    val dispatcher: CoroutineDispatcher,
    val setupSettingsRepository: SetupSettingsRepository,
)

enum class AppPage { HOME, ENTRY, DETAIL, CATEGORIES, PAYMENT_METHODS, MANAGEMENT_FORM, SETUP, SETTINGS }

data class ExpenseAppState(
    val loading: Boolean = true,
    val startupError: Boolean = false,
    val page: AppPage = AppPage.HOME,
    val currency: CurrencyCode? = null,
    val currencyPersisted: Boolean = false,
    val suggestedCurrency: CurrencyCode? = null,
    val currencyChoice: String = "",
    val settingsKnown: Boolean = false,
    val setupCompleted: Boolean = false,
    val weekStart: DayOfWeek = DayOfWeek.MONDAY,
    val weekChoice: Int = DayOfWeek.MONDAY.value,
    val setupMessage: Int? = null,
    val currencyError: Boolean = false,
    val weekError: Boolean = false,
    val settingsSaving: Boolean = false,
    val settingsRetry: String? = null,
    val expenses: List<Expense> = emptyList(),
    val categories: List<Category> = emptyList(),
    val categoryLabels: Map<UUID, String> = emptyMap(),
    val methods: List<PaymentMethod> = emptyList(),
    val selectedExpense: Expense? = null,
    val draft: ExpenseDraft? = null,
    val occurredAtText: String = "",
    val occurredAtEdited: Boolean = false,
    val editingExpense: Expense? = null,
    val errors: Set<ExpenseField> = emptySet(),
    val message: String? = null,
    val retry: String? = null,
    val saving: Boolean = false,
    val discardPrompt: Boolean = false,
    val undoExpense: Expense? = null,
    val managementCategory: Boolean = true,
    val managementId: UUID? = null,
    val managementName: String = "",
    val managementError: String? = null,
)

class ExpenseAppViewModel(
    private val context: Context?,
    injected: ExpenseAppDependencies?,
) : ViewModel() {
    private val occurredAtFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    private fun formatOccurredAt(instant: Instant): String =
        LocalDateTime.ofInstant(instant, ZoneId.systemDefault()).format(occurredAtFormat)

    private fun parseOccurredAt(text: String): Instant? =
        try {
            LocalDateTime.parse(text, occurredAtFormat).atZone(ZoneId.systemDefault()).toInstant()
        } catch (_: DateTimeException) {
            null
        }

    private val initialSuggestion =
        runCatching {
            CurrencyCode.of(Currency.getInstance(injected?.locale ?: Locale.getDefault()).currencyCode)
        }.getOrNull()
    private val stateStore =
        MutableStateFlow(
            ExpenseAppState(suggestedCurrency = initialSuggestion, currencyChoice = initialSuggestion?.value.orEmpty()),
        )
    val state: StateFlow<ExpenseAppState> = stateStore
    private var dependencies: ExpenseAppDependencies? = injected
    private var ownedStore: RoomExpenseStore? = null
    private var entryController: ExpenseEntryController? = null
    private var saveToken: UUID? = null
    private var mutationToken: UUID? = null
    private var undoPermit = 0L
    private var settingsReturnPage = AppPage.HOME
    private var settingsLoaded = false
    private var settingsDirty = false
    private var observing = false
    private val commands = Mutex()

    init {
        start()
    }

    fun start() {
        work {
            stateStore.update { it.copy(loading = true, startupError = false) }
            val deps =
                dependencies ?: withContext(Dispatchers.IO) {
                    val appContext = requireNotNull(context).applicationContext
                    val clock = Clock.systemUTC()
                    val ids = UuidGenerator { UUID.randomUUID() }
                    val store =
                        ownedStore ?: when (val opened = RoomExpenseStore.open(appContext, "bubblespend.db", clock, ids)) {
                            is StoreResult.Success -> opened.value.also { ownedStore = it }
                            else -> return@withContext null
                        }
                    val seeded = store.seedDefaults()
                    if (seeded !is StoreResult.Success) {
                        null
                    } else {
                        val settingsRepository = PreferenceCurrencyRepository(appContext)
                        ExpenseAppDependencies(
                            store,
                            store,
                            store,
                            settingsRepository,
                            clock,
                            ids,
                            Locale.getDefault(),
                            Dispatchers.IO,
                            settingsRepository,
                        )
                    }
                }
            if (deps == null) {
                stateStore.update { it.copy(loading = false, startupError = true) }
                return@work
            }
            dependencies = deps
            entryController =
                ExpenseEntryController(
                    deps.expenseRepository,
                    deps.baseCurrencyRepository,
                    deps.clock,
                    deps.locale,
                    deps.uuidGenerator,
                )
            val settings = withContext(deps.dispatcher) { deps.setupSettingsRepository.loadSettings() }
            if (settings is StoreResult.Success) {
                val saved = settings.value
                stateStore.update {
                    it.copy(
                        settingsKnown = true,
                        setupCompleted = saved.completed,
                        currency = saved.confirmedCurrency,
                        currencyPersisted = saved.confirmedCurrency != null,
                        currencyChoice =
                            saved.confirmedCurrency?.value
                                ?: if (settingsDirty) it.currencyChoice else saved.draftCurrency ?: initialSuggestion?.value.orEmpty(),
                        weekStart = saved.weekStart,
                        weekChoice = if (settingsDirty) it.weekChoice else saved.draftWeekStart ?: saved.weekStart.value,
                        page = if (!settingsLoaded && !saved.completed) AppPage.SETUP else it.page,
                        setupMessage = null,
                        settingsRetry = null,
                    )
                }
                if (!saved.completed && saved.draftCurrency == null && !settingsDirty) {
                    val snapshot = state.value
                    val draftSaved =
                        withContext(deps.dispatcher) {
                            deps.setupSettingsRepository.saveSetupDraft(snapshot.currencyChoice, snapshot.weekChoice)
                        }
                    if (draftSaved !is StoreResult.Success) {
                        settingsDirty = true
                        stateStore.update {
                            it.copy(
                                setupMessage = R.string.settings_write_failed,
                                settingsRetry = "draft",
                            )
                        }
                    }
                }
                settingsLoaded = true
            } else {
                stateStore.update {
                    it.copy(
                        settingsKnown = false,
                        currencyPersisted = false,
                        setupMessage = R.string.settings_read_failed,
                        settingsRetry = "read",
                    )
                }
            }
            val initialExpenses =
                withContext(deps.dispatcher) { deps.expenseRepository.observeAllActiveExpenses().first() }
            val initialCategories =
                withContext(deps.dispatcher) { deps.categoryRepository.observeCategories().first() }
            val initialMethods =
                withContext(deps.dispatcher) { deps.paymentMethodRepository.observePaymentMethods().first() }
            val initialLabels = categoryLabelsFor(deps, initialExpenses)
            stateStore.update {
                it.copy(
                    loading = false,
                    expenses = initialExpenses,
                    categories = initialCategories,
                    methods = initialMethods,
                    categoryLabels = it.categoryLabels + initialLabels,
                )
            }
            observe(deps)
        }
    }

    private fun observe(deps: ExpenseAppDependencies) {
        if (observing) return
        observing = true
        viewModelScope.launch {
            deps.expenseRepository.observeAllActiveExpenses().collect { list ->
                val labels = categoryLabelsFor(deps, list)
                stateStore.update { it.copy(expenses = list, categoryLabels = it.categoryLabels + labels) }
            }
        }
        viewModelScope.launch {
            deps.categoryRepository.observeCategories().collect { list ->
                val labels = categoryLabelsFor(deps, state.value.expenses)
                stateStore.update { old ->
                    old.copy(
                        categories = list,
                        categoryLabels = old.categoryLabels + labels,
                        draft =
                            old.draft?.let { draft ->
                                if (draft.categoryId != null && list.none { it.id == draft.categoryId }) {
                                    draft.copy(categoryId = null)
                                } else {
                                    draft
                                }
                            },
                    )
                }
            }
        }
        viewModelScope.launch {
            deps.paymentMethodRepository.observePaymentMethods().collect { list ->
                stateStore.update { old ->
                    old.copy(
                        methods = list,
                        draft =
                            old.draft?.let { draft ->
                                if (draft.paymentMethodId != null && list.none { it.id == draft.paymentMethodId }) {
                                    draft.copy(paymentMethodId = null)
                                } else {
                                    draft
                                }
                            },
                    )
                }
            }
        }
    }

    fun openSetup() {
        settingsReturnPage = if (state.value.draft != null) AppPage.ENTRY else AppPage.HOME
        stateStore.update { it.copy(page = AppPage.SETUP) }
    }

    fun openSettings() {
        settingsReturnPage = if (state.value.draft != null) AppPage.ENTRY else AppPage.HOME
        stateStore.update { it.copy(page = AppPage.SETTINGS, weekChoice = if (settingsDirty) it.weekChoice else it.weekStart.value) }
    }

    fun leaveSettings() {
        stateStore.update { it.copy(page = settingsReturnPage) }
    }

    fun chooseCurrency(value: String) {
        if (state.value.currency != null || state.value.settingsSaving || !state.value.settingsKnown) return
        settingsDirty = true
        stateStore.update { it.copy(currencyChoice = value.uppercase(Locale.ROOT), currencyError = false) }
        persistSetupDraft()
    }

    fun chooseWeek(value: Int) {
        if (state.value.settingsSaving || !state.value.settingsKnown) return
        settingsDirty = true
        stateStore.update { it.copy(weekChoice = value, weekError = false, setupMessage = null) }
        if (!state.value.setupCompleted) persistSetupDraft()
    }

    private fun persistSetupDraft() {
        val snapshot = state.value
        work {
            val deps = dependencies ?: return@work
            val result =
                withContext(deps.dispatcher) {
                    deps.setupSettingsRepository.saveSetupDraft(snapshot.currencyChoice, snapshot.weekChoice)
                }
            if (result !is StoreResult.Success) {
                stateStore.update { it.copy(setupMessage = R.string.settings_write_failed, settingsRetry = "draft") }
            } else if (state.value.settingsRetry == "draft") {
                stateStore.update { it.copy(setupMessage = null, settingsRetry = null) }
            }
        }
    }

    fun completeSetup() = saveSettings(complete = true)

    fun saveWeekStart() = saveSettings(complete = false)

    private fun saveSettings(complete: Boolean) {
        if (state.value.settingsSaving || !state.value.settingsKnown) return
        stateStore.update { it.copy(settingsSaving = true, setupMessage = null, currencyError = false, weekError = false) }
        val snapshot = state.value
        work {
            try {
                val deps = dependencies ?: return@work
                val controller = SetupSettingsController(deps.setupSettingsRepository)
                val result =
                    withContext(deps.dispatcher) {
                        if (complete) {
                            controller.complete(snapshot.currencyChoice, snapshot.weekChoice)
                        } else {
                            controller.changeWeekStart(snapshot.weekChoice)
                        }
                    }
                when (result) {
                    is StoreResult.Success -> {
                        settingsDirty = false
                        val saved = result.value
                        stateStore.update {
                            it.copy(
                                settingsKnown = true,
                                setupCompleted = saved.completed,
                                currency = saved.confirmedCurrency,
                                currencyPersisted = saved.confirmedCurrency != null,
                                currencyChoice = saved.confirmedCurrency?.value.orEmpty(),
                                weekStart = saved.weekStart,
                                weekChoice = saved.weekStart.value,
                                settingsRetry = null,
                                setupMessage = if (complete) null else R.string.settings_saved,
                                page = if (complete) settingsReturnPage else it.page,
                            )
                        }
                    }
                    is StoreResult.ValidationFailure ->
                        stateStore.update {
                            it.copy(
                                currencyError = result.message == "currency",
                                weekError = result.message == "weekStart",
                                settingsRetry = null,
                            )
                        }
                    else ->
                        stateStore.update {
                            it.copy(
                                setupMessage = R.string.settings_write_failed,
                                settingsRetry = if (complete) "complete" else "week",
                            )
                        }
                }
            } finally {
                stateStore.update { it.copy(settingsSaving = false) }
            }
        }
    }

    fun retrySettings() {
        when (state.value.settingsRetry) {
            "draft" -> persistSetupDraft()
            "complete" -> completeSetup()
            "week" -> saveWeekStart()
            else -> start()
        }
    }

    fun openEntry(
        expense: Expense? = null,
        duplicate: Boolean = false,
    ) {
        val deps = dependencies
        val source = expense
        val occurredAt = source?.occurredAt ?: (deps?.clock ?: Clock.systemUTC()).instant()
        stateStore.update {
            it.copy(
                page = AppPage.ENTRY,
                selectedExpense = null,
                editingExpense = if (duplicate) null else source,
                draft =
                    if (source == null) {
                        ExpenseDraft(
                            "",
                            it.categories.firstOrNull()?.id,
                            it.methods.firstOrNull { method -> method.isDefault }?.id ?: it.methods.firstOrNull()?.id,
                            occurredAt,
                            "",
                            "",
                        )
                    } else {
                        ExpenseDraft(
                            source.amount.toDisplayString(),
                            source.categoryId,
                            source.paymentMethodId,
                            source.occurredAt,
                            source.merchant,
                            source.note,
                        )
                    },
                occurredAtText = formatOccurredAt(occurredAt),
                occurredAtEdited = false,
                errors = emptySet(),
                message = null,
                retry = null,
                discardPrompt = false,
            )
        }
        saveToken = null
    }

    fun updateDraft(change: (ExpenseDraft) -> ExpenseDraft) {
        stateStore.update { it.copy(draft = it.draft?.let(change), errors = emptySet(), message = null) }
        saveToken = null
    }

    fun updateOccurredAtText(text: String) {
        val parsed = parseOccurredAt(text)
        stateStore.update {
            it.copy(
                occurredAtText = text,
                occurredAtEdited = true,
                draft = if (parsed == null) it.draft else it.draft?.copy(occurredAt = parsed),
                errors = it.errors - ExpenseField.OCCURRED_AT,
                message = null,
            )
        }
        saveToken = null
    }

    fun save() =
        work {
            val deps = dependencies ?: return@work
            val snapshot = state.value
            val draft = snapshot.draft ?: return@work
            if (snapshot.saving) return@work
            val occurredAt = parseOccurredAt(snapshot.occurredAtText)
            if (occurredAt == null) {
                stateStore.update { it.copy(errors = it.errors + ExpenseField.OCCURRED_AT) }
                return@work
            }
            val validatedDraft = if (draft.occurredAt == occurredAt) draft else draft.copy(occurredAt = occurredAt)
            stateStore.update { it.copy(draft = validatedDraft, saving = true, retry = null, message = null) }
            try {
                if (!state.value.settingsKnown || !state.value.currencyPersisted) {
                    openSetup()
                    return@work
                }
                val result =
                    if (snapshot.editingExpense == null) {
                        entryController!!.submit(validatedDraft)
                    } else {
                        val confirmed = withContext(deps.dispatcher) { deps.baseCurrencyRepository.loadConfirmedCurrency() }
                        if (confirmed !is StoreResult.Success || confirmed.value == null) {
                            stateStore.update {
                                it.copy(
                                    settingsKnown = false,
                                    currencyPersisted = false,
                                    settingsRetry = "read",
                                    setupMessage = R.string.settings_read_failed,
                                )
                            }
                            openSetup()
                            return@work
                        }
                        val currency = confirmed.value ?: return@work
                        val validated = ExpenseEntryValidator.validate(validatedDraft, currency, deps.locale, deps.clock)
                        if (validated is ExpenseValidation.Invalid) {
                            ExpenseEntrySubmission.Invalid(validated.fields, draft)
                        } else {
                            val original = snapshot.editingExpense
                            val token = saveToken ?: deps.uuidGenerator.next().also { saveToken = it }
                            val edited =
                                deps.expenseRepository.editExpense(
                                    original.id,
                                    original.version,
                                    (validated as ExpenseValidation.Valid).expense,
                                    token,
                                )
                            when (edited) {
                                is StoreResult.Success -> ExpenseEntrySubmission.Saved(edited.value)
                                is StoreResult.Conflict -> ExpenseEntrySubmission.Conflict
                                is StoreResult.InactiveReference ->
                                    ExpenseEntrySubmission.Invalid(setOf(ExpenseField.CATEGORY, ExpenseField.PAYMENT_METHOD), draft)
                                else -> ExpenseEntrySubmission.RecoverableFailure(draft)
                            }
                        }
                    }
                when (result) {
                    is ExpenseEntrySubmission.Saved ->
                        stateStore.update {
                            it.copy(page = AppPage.HOME, draft = null, editingExpense = null, message = null)
                        }
                    is ExpenseEntrySubmission.Invalid -> stateStore.update { it.copy(errors = result.fields) }
                    is ExpenseEntrySubmission.CurrencyConfirmationRequired ->
                        stateStore.update {
                            it.copy(settingsKnown = false, currencyPersisted = false, page = AppPage.SETUP, settingsRetry = "read")
                        }
                    is ExpenseEntrySubmission.Conflict -> stateStore.update { it.copy(message = "Conflict", retry = null) }
                    is ExpenseEntrySubmission.RecoverableFailure ->
                        stateStore.update { it.copy(message = "Save failed", retry = "save") }
                }
            } finally {
                stateStore.update { it.copy(saving = false) }
            }
        }

    fun closeEntry() {
        val draft = state.value.draft
        if (draft != null &&
            (
                draft.amountText.isNotEmpty() ||
                    draft.merchant.isNotEmpty() ||
                    draft.note.isNotEmpty() ||
                    state.value.occurredAtEdited ||
                    state.value.editingExpense != null
            )
        ) {
            stateStore.update { it.copy(discardPrompt = true) }
        } else {
            discardEntry()
        }
    }

    fun keepEditing() {
        stateStore.update { it.copy(discardPrompt = false) }
    }

    fun discardEntry() {
        stateStore.update {
            it.copy(
                page = AppPage.HOME,
                draft = null,
                occurredAtText = "",
                occurredAtEdited = false,
                discardPrompt = false,
                errors = emptySet(),
            )
        }
    }

    fun selectExpense(expense: Expense) {
        stateStore.update { it.copy(page = AppPage.DETAIL, selectedExpense = expense, message = null) }
    }

    fun backHome() {
        undoPermit++
        stateStore.update {
            it.copy(page = if (it.draft != null) AppPage.ENTRY else AppPage.HOME, undoExpense = null, retry = null, message = null)
        }
    }

    fun closeManagementForm() {
        stateStore.update {
            it.copy(
                page = if (it.managementCategory) AppPage.CATEGORIES else AppPage.PAYMENT_METHODS,
                managementError = null,
                retry = null,
            )
        }
    }

    fun delete() =
        work {
            val deps = dependencies ?: return@work
            val original = state.value.selectedExpense ?: return@work
            val token = mutationToken ?: deps.uuidGenerator.next().also { mutationToken = it }
            val result = deps.expenseRepository.deleteExpense(original.id, original.version, token)
            val persisted = deps.expenseRepository.getExpense(original.id)
            if (result is StoreResult.Success || persisted?.deletedAt != null) {
                mutationToken = null
                undoPermit++
                stateStore.update {
                    it.copy(page = AppPage.HOME, selectedExpense = null, undoExpense = persisted, retry = null, message = null)
                }
            } else {
                stateStore.update {
                    it.copy(
                        message = if (result is StoreResult.Conflict) "Conflict" else "Delete failed",
                        retry = "delete",
                    )
                }
            }
        }

    fun expireUndo() {
        undoPermit++
        stateStore.update { it.copy(undoExpense = null) }
    }

    fun undo() =
        work {
            val deps = dependencies ?: return@work
            val deleted = state.value.undoExpense ?: return@work
            val permit = undoPermit
            val result = deps.expenseRepository.undoDelete(deleted.id, deleted.version, deps.uuidGenerator.next())
            val persisted = deps.expenseRepository.getExpense(deleted.id)
            if (permit == undoPermit && (result is StoreResult.Success || persisted?.deletedAt == null)) {
                expireUndo()
            } else {
                stateStore.update { it.copy(message = "Undo failed", retry = "undo") }
            }
        }

    fun manageCategories() {
        stateStore.update { it.copy(page = AppPage.CATEGORIES, managementCategory = true, message = null) }
    }

    fun manageMethods() {
        stateStore.update { it.copy(page = AppPage.PAYMENT_METHODS, managementCategory = false, message = null) }
    }

    fun openManagementForm(
        id: UUID? = null,
        category: Boolean = true,
    ) {
        stateStore.update {
            it.copy(
                page = AppPage.MANAGEMENT_FORM,
                managementCategory = category,
                managementId = id,
                managementName = "",
                managementError = null,
                retry = null,
            )
        }
    }

    fun updateManagementName(name: String) {
        stateStore.update { it.copy(managementName = name, managementError = null) }
    }

    fun saveManagement() =
        work {
            val deps = dependencies ?: return@work
            val current = state.value
            if (runCatching { NameValidation.validate(current.managementName) }.isFailure) {
                stateStore.update { it.copy(managementError = "Name must contain 1 to 40 characters") }
                return@work
            }
            val result =
                if (current.managementCategory) {
                    val old = current.categories.find { it.id == current.managementId }
                    if (old == null) {
                        deps.categoryRepository.createCategory(
                            current.managementName,
                            "other",
                            "neutral",
                            deps.uuidGenerator.next(),
                        )
                    } else {
                        deps.categoryRepository.renameCategory(
                            old.id,
                            old.version,
                            current.managementName,
                            deps.uuidGenerator.next(),
                        )
                    }
                } else {
                    val old = current.methods.find { it.id == current.managementId }
                    if (old == null) {
                        deps.paymentMethodRepository.createPaymentMethod(current.managementName, deps.uuidGenerator.next())
                    } else {
                        deps.paymentMethodRepository.renamePaymentMethod(
                            old.id,
                            old.version,
                            current.managementName,
                            deps.uuidGenerator.next(),
                        )
                    }
                }
            when (result) {
                is StoreResult.Success ->
                    stateStore.update {
                        it.copy(
                            page = if (current.managementCategory) AppPage.CATEGORIES else AppPage.PAYMENT_METHODS,
                            managementError = null,
                            retry = null,
                        )
                    }
                is StoreResult.Conflict -> stateStore.update { it.copy(managementError = "Conflict") }
                is StoreResult.ValidationFailure -> stateStore.update { it.copy(managementError = result.message) }
                else -> stateStore.update { it.copy(managementError = "Save failed", retry = "management") }
            }
        }

    fun archiveCategory(id: UUID) =
        work {
            val deps = dependencies ?: return@work
            val record = state.value.categories.find { it.id == id } ?: return@work
            val result = deps.categoryRepository.archiveCategory(id, record.version, deps.uuidGenerator.next())
            if (result !is StoreResult.Success) stateStore.update { it.copy(message = "Archive failed", retry = "archiveCategory") }
        }

    fun archiveMethod(id: UUID) =
        work {
            val deps = dependencies ?: return@work
            val record = state.value.methods.find { it.id == id } ?: return@work
            val result = deps.paymentMethodRepository.archivePaymentMethod(id, record.version, deps.uuidGenerator.next())
            if (result !is StoreResult.Success) stateStore.update { it.copy(message = "Archive failed", retry = "archiveMethod") }
        }

    fun setDefault(id: UUID) =
        work {
            val deps = dependencies ?: return@work
            val record = state.value.methods.find { it.id == id } ?: return@work
            val result = deps.paymentMethodRepository.setDefaultPaymentMethod(id, record.version, deps.uuidGenerator.next())
            if (result !is StoreResult.Success) stateStore.update { it.copy(message = "Default update failed", retry = "default") }
        }

    fun retry() {
        when (state.value.retry) {
            "save" -> save()
            "delete" -> delete()
            "undo" -> undo()
            "management" -> saveManagement()
            else -> start()
        }
    }

    private fun work(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                commands.withLock { block() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                stateStore.update {
                    it.copy(
                        loading = false,
                        startupError = it.loading,
                        message = "Storage unavailable",
                        retry = "startup",
                    )
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
    }

    private suspend fun categoryLabelsFor(
        deps: ExpenseAppDependencies,
        expenses: List<Expense>,
    ): Map<UUID, String> =
        expenses
            .mapNotNull { expense ->
                deps.categoryRepository.getCategory(expense.categoryId)?.let { expense.categoryId to it.name }
            }.toMap()

    companion object {
        fun factory(
            context: Context?,
            dependencies: ExpenseAppDependencies?,
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = ExpenseAppViewModel(context, dependencies) as T
        }
    }
}

private fun com.bubblespend.core.domain.Money.toDisplayString(): String {
    val scale = currency.fractionDigits
    return java.math.BigDecimal
        .valueOf(minorUnits, scale)
        .toPlainString()
}
