# Planner research cache

Before external research, inspect `workflow/research/<topic>.md`. Each entry uses
the front matter in the approved cache template: normalized `topic`, exact
`version` or API scope, UTC `accessed` date, and one or more source URLs. Entries
are current for at most 30 calendar days. The validator rejects future/malformed
dates, missing sources, and topic or exact-version mismatches. A stale entry is
historical context only; verify current facts from primary sources and refresh it.

Example entry (`workflow/research/android-room.md`):

```markdown
---
topic: android-room
version: "2.8.0"
accessed: 2026-10-01
sources:
- https://developer.android.com/training/data-storage/room
---

# Findings

Record only findings that apply to this exact topic and version scope.
```

Use an ISO UTC calendar date for `accessed`, an exact version/API scope, and
one or more HTTPS source URLs. The list syntax shown is the format accepted by
`workflow/research/cache.py`. The cache does not replace approved
specifications, ADRs, or task evidence.
