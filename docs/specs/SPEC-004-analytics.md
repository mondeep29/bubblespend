# SPEC-004: Analytics and deterministic insights

**Status:** Draft

Show selected-period total and absolute/percentage change, zero-filled trend buckets, category exact amounts and percentages, comparative category bars, and explainable insight cards. Week buckets are daily, year buckets monthly; month/custom bucket policy needs approval. Daily average includes zero-spend days. Percentages derive from exact values and display rounding never feeds a calculation. Axes start at zero or explicitly label a different baseline. Every visual has a list/text equivalent and transaction drill-down.

Initial insights: largest category increase/decrease above an approved significance threshold; highest-spend weekday after enough observations; largest expense; new category activity. Each result states period, comparison basis, and underlying transactions. Insufficient data yields a safe no-insight state.

Acceptance criteria to finalize: `SPEC-004-AC-01` exact totals and comparisons; `AC-02` zero-filled buckets; `AC-03` category reconciliation; `AC-04` deterministic eligible insights; `AC-05` insufficient-data behavior; `AC-06` accessible equivalents and drill-down.

**Test mapping:** Pending. **Open questions:** bucket thresholds, significance threshold, minimum observations.
