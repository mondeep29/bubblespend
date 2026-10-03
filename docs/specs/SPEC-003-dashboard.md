# SPEC-003: Dashboard

**Status:** Draft

Show week, month, year, and custom period totals, an equivalent previous-period comparison, category bubbles, and recent expenses. Period boundaries use the selected local timezone, configured week start, and `[start, end)`. Every visible amount reconciles with the same expense query used by history. Bubble size/color never replace exact text, TalkBack value, or drill-down. The user can inspect matching transactions from every category or total. Define custom period limits, previous-equivalent semantics, bubble area scaling, and filter persistence before approval.

Acceptance criteria to finalize: `SPEC-003-AC-01` correct period totals; `AC-02` equivalent prior period; `AC-03` bubble/list equivalence; `AC-04` drill-down retains filters; `AC-05` loading/empty/error states; `AC-06` timezone and DST boundaries.

**Test mapping:** Pending. **Open questions:** D-02, custom comparison and filter persistence.
