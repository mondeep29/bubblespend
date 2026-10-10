# Decisions requiring product owner approval

Unresolved decisions remain open; rows marked approved record explicit product-owner choices.

| ID | Decision | Proposed default | Blocks |
| --- | --- | --- | --- |
| D-01 | Should a shortcut reappear after a device restart? | **Approved 2026-10-06:** Reappear automatically after restart. | Resolved for SPEC-005; verify feasibility and limits on physical device |
| D-02 | Which timezone should reporting use after travel? | **Approved 2026-10-07:** Use the device timezone at query time. Do not store a reporting timezone on expenses. A timezone change affects subsequent local-calendar queries. | Resolved for SPEC-003/006; verify date-range behavior on the named device/API |
| D-03 | Approved 2026-10-02: deletion can be undone during one visible snackbar window. When the window closes or the app is interrupted, the undo action is no longer available and the tombstone persists. | Product owner approved proposed default | Resolved for SPEC-001 |
| D-04 | Restore policy for existing records | Preview then replace atomically; no merge in MVP | Backup specification |
| D-05 | Approved 2026-10-02: suggest the locale currency and require explicit confirmation before first expense entry; a later currency change requires a separate approved migration decision. Approved 2026-10-07: Monday is the initial configurable week-start default. Approved 2026-10-10: offer all seven weekdays, retain the C-04 Cash default, save unfinished setup choices locally and resume after interruption, preserve choices with Retry on storage errors, require explicit setup completion, allow browsing before completion while expense saving remains blocked, permit later week-start edits, and keep the confirmed currency fixed pending migration approval. | Product owner approved these setup and settings choices | Resolved for SPEC-009; implementation and device verification remain |
| D-06 | Overlay entry presentation and lock-screen behavior | **Approved 2026-10-06:** Show only a neutral button when locked; block expense details. | Resolved for SPEC-005; verify behavior on physical device |
| D-07 | May user-initiated export use a remote-backed Android document provider? | Unresolved; product owner choice required | Backup/export specification and the offline/local-data boundary |
| D-08 | Is screen-reader support required for the MVP? | **Approved 2026-10-07:** No screen-reader requirement. Keep visible text/list equivalents, touch actions, large/bold text, contrast, and reduced-motion requirements. | Remove TalkBack criteria from the proof and synchronize product brief and accessibility specs |
| D-09 | How do dashboard category names tie-break equal totals? | **Approved 2026-10-07:** Reuse History normalization: trim and collapse whitespace, apply Unicode NFKD, remove combining marks, apply locale-independent Unicode case conversion, compare by Unicode code point, then use stable category ID. | Resolved for SPEC-003-AC-03 |

Record owner decisions here with date and update affected specifications/ADRs before changing behavior.

## Category and payment decisions

- **C-01 (approved 2026-10-02):** First use provides predefined categories Food, Transport, Housing, Utilities, Health, and Other, and predefined payment methods Cash and Card. Users may create additional expense categories and payment methods. The later owner clarification supersedes the earlier no-starter answer.
- **E-01 (approved 2026-10-02):** Closing an unsaved expense draft requires a discard confirmation. Merchant text is limited to 100 characters and note text to 500 characters after trimming.
- **C-02 (approved 2026-10-02):** When a selected category or payment method is archived, an unsaved expense draft requires a new active selection. Archiving the default payment method clears the default until the user chooses another; no automatic replacement occurs.
- **C-03 (approved 2026-10-02):** Category and payment method names contain 1–40 characters after trimming. Uniqueness among active records of the same type compares names case insensitively after repeated spaces are collapsed.
- **C-04 (approved 2026-10-02):** Cash is the initial default payment method. All predefined categories and methods may be renamed and archived under the same rules as user-created records.
- **C-05 (approved 2026-10-02):** Use the proposed category visual defaults in SPEC-002: Food/red, Transport/orange, Housing/yellow, Utilities/green, Health/blue, Other/purple, with their matching icon keys. New user-defined categories begin with the Other icon and neutral color; users can choose another supported pair.
