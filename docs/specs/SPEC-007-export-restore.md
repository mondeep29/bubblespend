# SPEC-007: Export and restore

**Status:** Draft

Export versioned local data with integrity metadata through user-selected Android documents. Before restore, validate schema version, integrity, money/currency invariants, references, duplicate IDs, and preview counts. Apply an approved replace or merge policy transactionally; a failure cannot partially overwrite live data. The app remains usable offline. Do not log backup contents. The exact file format and D-04 restore policy require approval before implementation.

Acceptance criteria to finalize: `SPEC-007-AC-01` export/restore round trip; `AC-02` reject corrupt/unsupported input before mutation; `AC-03` rollback on failure; `AC-04` preview and explicit confirmation; `AC-05` large-data performance and privacy.

**Test mapping:** Pending. **Open questions:** D-04, file format, encryption option, duplicate policy.
