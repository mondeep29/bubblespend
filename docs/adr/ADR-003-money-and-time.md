# ADR-003: Money and calendar semantics

**Status:** Approved for money storage, UTC instants, `[start, end)` interval invariants, and D-02 reporting timezone behavior. The product owner approved device timezone at query time on 2026-10-07; reporting timezone is not stored on expenses.

Store monetary amounts in signed 64-bit integer minor units with ISO 4217 code. Domain arithmetic checks overflow and currency equality; financial calculations never use binary floating point. Parse locale-specific input at the UI boundary and preserve exact minor units. Percentage display may use decimal arithmetic and rounding, never rounded display values as source data.

Store instants in UTC. Convert local calendar boundaries in the device timezone at query time into UTC intervals `[start, end)`; a timezone change affects subsequent local-calendar queries. Week start is user-configured, with Monday as the initial default approved 2026-10-07. Equivalent comparison periods use matching calendar semantics, with exact rules in SPEC-003. Inject `Clock`, ID generator, locale, and timezone providers.

**Verification:** Tests at midnight, month/year boundaries, leap days, daylight saving transitions, negative/zero/overflow amounts, and locale parsing.
