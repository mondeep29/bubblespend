# Decisions requiring product owner approval

These are intentionally open. Agents may prototype and document evidence without treating a proposed choice as approved.

| ID | Decision | Proposed default | Blocks |
| --- | --- | --- | --- |
| D-01 | Should a shortcut reappear after a device restart? | Keep disabled until the user opens the app, pending overlay proof | Final overlay lifecycle contract |
| D-02 | Which timezone should reporting use after travel? | Device timezone at query time, with explicit setting later if needed | Date-range acceptance tests |
| D-03 | Approved 2026-10-02: deletion can be undone during one visible snackbar window. When the window closes or the app is interrupted, the undo action is no longer available and the tombstone persists. | Product owner approved proposed default | Resolved for SPEC-001 |
| D-04 | Restore policy for existing records | Preview then replace atomically; no merge in MVP | Backup specification |
| D-05 | Partially approved 2026-10-02: suggest the locale currency and require explicit confirmation before first expense entry; a later currency change requires a separate approved migration decision. Week start and other local setup defaults remain open. | Product owner approved proposed currency default | Currency entry resolved for SPEC-001; onboarding/settings remain open |
| D-06 | Overlay entry presentation and lock-screen behavior | Show only a neutral button when locked; block expense details | Overlay proof |
| D-07 | May user-initiated export use a remote-backed Android document provider? | Unresolved; product owner choice required | Backup/export specification and the offline/local-data boundary |

Record owner decisions here with date and update affected specifications/ADRs before changing behavior.

## BT-004 category and payment decisions

- **C-01 (approved 2026-10-02):** First use provides predefined categories Food, Transport, Housing, Utilities, Health, and Other, and predefined payment methods Cash and Card. Users may create additional expense categories and payment methods. The later owner clarification supersedes the earlier no-starter answer.
- **E-01 (approved 2026-10-02):** Closing an unsaved expense draft requires a discard confirmation. Merchant text is limited to 100 characters and note text to 500 characters after trimming.
- **C-02 (approved 2026-10-02):** When a selected category or payment method is archived, an unsaved expense draft requires a new active selection. Archiving the default payment method clears the default until the user chooses another; no automatic replacement occurs.
- **C-03 (approved 2026-10-02):** Category and payment method names contain 1–40 characters after trimming. Uniqueness among active records of the same type compares names case insensitively after repeated spaces are collapsed.
- **C-04 (approved 2026-10-02):** Cash is the initial default payment method. All predefined categories and methods may be renamed and archived under the same rules as user-created records.
- **C-05 (approved 2026-10-02):** Use the proposed category visual defaults in SPEC-002: Food/red, Transport/orange, Housing/yellow, Utilities/green, Health/blue, Other/purple, with their matching icon keys. New user-defined categories begin with the Other icon and neutral color; users can choose another supported pair.
