# SPEC-009: First-run setup and settings

**Status:** Approved

**Specification review:** 2026-10-10. Reviewed against D-02/D-05/C-04/D-08, SPEC-001's confirmation gate, and approved reporting contracts. The recorded owner choices resolve the visible setup and recovery decisions. No blocking specification findings; implementation and verification remain required.

## User outcome

On first run, the user can confirm one base currency and choose a week start before recording an expense. The user may browse the app while setup is unfinished. Settings later show the confirmed currency and allow week start to change.

## Scope and approved decisions

- D-05 suggests the device locale's currency and requires explicit confirmation before the first expense. A confirmed base currency is fixed until a separately approved migration decision; changing the device locale does not change it.
- Monday is the initial week start. The user may select any of the seven weekdays. The selected week start is a local reporting preference; ADR-003 keeps reporting timezone derived from the device at query time (D-02).
- Cash remains the initial default payment method under C-04. Setup does not create, rename, or change that method.
- On 2026-10-10, the product owner approved local persistence of unfinished setup choices, resume after interruption, preserving entered choices with Retry after storage errors, explicit completion, and browsing before completion while expense saving remains blocked. Later settings can edit week start; currency remains fixed pending an approved migration.
- D-08 removes screen-reader proof from MVP scope. Visible labels/errors, large and bold text, contrast, and reduced motion remain required.

## User journeys and states

First launch offers a setup route with a locale currency suggestion and Monday week start. The currency is a suggestion, not a confirmed value. The user may choose a different supported ISO 4217 currency, choose any weekday, leave setup, and browse. Unfinished choices are saved locally and shown again on return or process restart. The app shows a visible route back to setup while it is unfinished. Expense entry may be opened for browsing or drafting, but Save cannot commit until currency is durably confirmed; the user is guided back to setup without losing an expense draft.

The user explicitly completes setup. Completion validates the selected currency and weekday, durably stores the chosen week start and confirmed currency, and reports success only after stored state is known. If a write fails, entered choices remain visible with an error and Retry. If interruption occurs between writes, reopening reconciles stored values and returns to any incomplete step without undoing a confirmed currency or applying another logical confirmation. A stored confirmed currency must never be replaced by a different code through setup or settings.

After completion, Settings shows the confirmed currency as fixed and explains that changing it needs a separate migration. Settings allows selecting another weekday; reports use the persisted selection when they next query weekly periods. A failed preference write leaves the previous stored week start effective and the attempted choice visible for Retry. Timezone is not a stored setup field. Payment defaults follow the existing category/payment rules.

## Validation and empty/recovery states

- A blank, malformed, or unsupported currency code cannot complete setup. Currency comparison and storage use a canonical three-letter ISO 4217 code, without conversion.
- Week start must be one of the seven weekdays. A missing or invalid value cannot be committed.
- If the device locale has no usable currency, show no preselected currency and require an explicit supported choice. Monday still appears as the initial week start.
- If local settings cannot be read, show a visible loading/error state and Retry; do not treat an unknown confirmation state as confirmed. Browsing remains available and expense saving remains blocked until the durable confirmation can be read.
- If local settings cannot be written, keep the attempted choices on screen and show Retry. Do not show completion or a changed reporting preference until persistence succeeds.
- Setup and Settings must expose a usable path and readable state when there are no expenses or reports yet.

## Privacy and accessibility

Keep setup/settings data on device and add no account, telemetry, network, or permission request. Do not log transaction amounts, merchants, notes, backup contents, or credentials. Use visible field labels, errors, and actions; retain focus and entered choices after validation/storage errors. Large/bold text, high contrast, and reduced motion must preserve all actions and error content. No action or state may depend solely on color or animation.

## Acceptance criteria

- **SPEC-009-AC-01 — first-run confirmation gate:** Given a fresh install, when setup first opens, then the device locale currency is shown as an unconfirmed suggestion when available, Monday is selected, and no expense can be committed. Given a valid currency and weekday, when the user explicitly completes setup and persistence succeeds, then that currency is durably confirmed and an otherwise valid expense can be committed exactly once. Browsing or drafting before completion does not silently confirm currency or discard a draft.
- **SPEC-009-AC-02 — week start:** Given a completed setup, when the user changes week start in Settings to any of the seven weekdays and the save succeeds, then a subsequent weekly report uses that stored weekday for its local-calendar start in the device timezone, including after restart. Monday applies before a saved change. A timezone change follows D-02 at query time.
- **SPEC-009-AC-03 — durable settings and fixed currency:** Given confirmed setup and a saved week start, when the process restarts or device locale changes, then Settings shows the same confirmed currency and week start. A different currency cannot be confirmed through Settings or a resumed setup, and no existing expense is rewritten or converted. Cash remains the initial payment default under C-04.
- **SPEC-009-AC-04 — validation and storage recovery:** Given blank, malformed, or unsupported currency input or an invalid weekday, when completion is attempted, then no confirmation occurs, the entered choices remain, and a visible field error explains correction. Given a read or write failure, when the user retries, then success appears only after durable state is known; failed writes preserve the attempted choice and do not change the effective persisted week start or permit an unconfirmed expense commit.
- **SPEC-009-AC-05 — interruption and browsing:** Given unfinished setup choices, when the user browses away or the process is interrupted and reopened, then the app offers a visible setup return path and restores the locally saved choices without a currency confirmation or duplicate mutation. If interrupted during completion, reopening reconciles persisted state and resumes the incomplete step; a confirmed currency is never replaced with a different one. Expense Save remains blocked while confirmation is absent or unreadable.
- **SPEC-009-AC-06 — usable presentation:** Given first-run Setup, unfinished browsing, Settings, validation failure, storage failure, or an empty expense history, when large/bold text, high contrast, or reduced motion is enabled, then visible labels, values, errors, Retry, completion, and setup return controls remain readable and operable without relying only on color or motion.

## Verification and mapping

Application and local-storage acceptance tests cover AC-01 through AC-05. Physical-device first-launch/reopen and display-setting checks cover AC-01, AC-05, and AC-06. Verification command: `./gradlew spotlessCheck lint testDebugUnitTest connectedDebugAndroidTest assembleDebug` on the configured host. The [setup/settings proof](../proofs/setup-settings.md) records the exact test methods, 2026-10-10 results, physical-device display observations, and review APK provenance.
