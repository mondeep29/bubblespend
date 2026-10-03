# ADR-005: Future synchronization boundary

**Status:** Approved

SQLite remains the working source of truth. Persist stable UUID, timestamps, version, tombstone, and isolated sync status now. Keep UI and domain contracts free of remote API types. Do not add networking, accounts, background sync, cloud credentials, conflict resolution, or a queue in the MVP.

A later approved synchronization specification must define initial upload, offline writes, conflict policy, clock skew, tombstone retention, retries, partial failures, privacy, account deletion, migration, and rollback. Review the boundary in M8; no production cloud dependency is allowed in that milestone.
