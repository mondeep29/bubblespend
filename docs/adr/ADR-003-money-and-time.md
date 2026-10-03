# ADR-003: Money and calendar semantics

**Status:** Approved for money storage, UTC instants, and `[start, end)` interval invariants; D-02 reporting timezone behavior remains open.

Store monetary amounts in signed 64-bit integer minor units with ISO 4217 code. Domain arithmetic checks overflow and currency equality; financial calculations never use binary floating point. Parse locale-specific input at the UI boundary and preserve exact minor units. Percentage display may use decimal arithmetic and rounding, never rounded display values as source data.

Store instants in UTC. Convert local calendar boundaries in the configured reporting timezone into UTC intervals `[start, end)`. Week start is user-configured. Equivalent comparison periods use matching calendar semantics, with exact rules in SPEC-003. Inject `Clock`, ID generator, locale, and timezone providers.

**Verification:** Tests at midnight, month/year boundaries, leap days, daylight saving transitions, negative/zero/overflow amounts, and locale parsing.
