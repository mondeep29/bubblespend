# SPEC-008: Accessibility and localization readiness

**Status:** Draft

Critical entry, history, dashboard, analytics, overlay settings, and restore journeys work with TalkBack, large/bold text, high contrast, and reduced motion. Touch targets meet Android minimums; focus order is logical; error messages are announced. Charts and bubbles have equivalent text/list views and exact values. Copy is stored in Android string resources with locale-aware money/date formatting. No color-only or motion-only meaning.

Acceptance criteria to finalize: `SPEC-008-AC-01` entry with TalkBack; `AC-02` chart/list equivalence; `AC-03` large text no clipped critical actions; `AC-04` reduced motion; `AC-05` contrast and target sizes; `AC-06` localization-ready strings.

**Test mapping:** Pending. **Open questions:** supported locales for first release, audit devices and text scale.
