# ADR-002: Local database and repositories

**Status:** Approved

Use Room over SQLite as the authoritative local store. Use DataStore for non-financial preferences. Define `ExpenseRepository`, `CategoryRepository`, `PaymentMethodRepository`, `SettingsRepository`, and `BackupRepository` at the domain/application boundary before implementations. Run identical contract tests against in-memory fakes and Room repositories. Use Flow for observable queries and explicit transactions for compound mutations.

Expense records include UUID, UTC `createdAt`/`updatedAt`, nullable UTC `deletedAt`, positive integer `version`, and locally meaningful `syncStatus` isolated from UI. Index active records by occurred time and category as profiling indicates. Seed default categories and methods idempotently. No destructive migration fallback for user data. Include fixtures and tests for each schema upgrade.

**Rationale:** Room supplies Android-native SQLite access while preserving a stable repository boundary for later synchronization.

**Verification:** Contracts, reopen persistence, migration fixtures, corruption/recovery, and rollback tests.
