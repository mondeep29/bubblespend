# SPEC-006: Transaction history and search

**Status:** Draft

Users can inspect an expense, search text, filter by period/category/payment method, sort deterministically, and open a history view from dashboard or analytics with filters preserved. Soft-deleted expenses are hidden from normal history. Search and sorting remain responsive at 50,000 records; exact semantics and tie-breakers must be approved. Empty, loading, and storage-error states need accessible actions.

Acceptance criteria to finalize: `SPEC-006-AC-01` filters match totals; `AC-02` drill-down context preserved; `AC-03` stable sorting; `AC-04` search behavior; `AC-05` detail and empty/error states.

**Test mapping:** Pending. **Open questions:** search fields, case/diacritic behavior, default sort, filter persistence.
