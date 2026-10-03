# Glossary

- **Expense:** Positive spending amount tied to an active category and payment method at a UTC instant.
- **Minor unit:** Currency's smallest accounting unit, such as a cent. Stored as a signed 64-bit integer with overflow checks.
- **Base currency:** The user's one selected ISO 4217 currency for MVP entries and reports.
- **Occurred at:** When spending happened, stored as an instant; displayed and grouped in the configured timezone.
- **Tombstone:** A soft-deleted record with `deletedAt`; omitted from normal views but retained for restore and future sync.
- **Active:** A category or method available for new expenses. Referenced records can be archived.
- **Period:** Local calendar interval translated to UTC as `[start, end)`.
- **Equivalent prior period:** Immediately preceding interval with matched calendar semantics defined in SPEC-003.
- **Overlay:** Android window displayed over another app after explicit special-access permission.
- **Insight:** Deterministic rule result with period, comparison basis, and underlying transactions.
- **Backup:** Versioned local export validated before transactional restore.
