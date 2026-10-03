# Operational context

- Read `docs/coding-standards.md` when present for implementation conventions.
  Approved specifications and ADRs remain authoritative; this digest is only a
  navigation aid.
- Preserve red before implementation: genuine failing test, smallest change,
  verification, traceability, and independent review. Never edit Tester-owned tests during
  implementation or bypass `workflow/transition.py` gates.
- Keep BubbleSpend native, local-first, and private. Never log amounts,
  merchants, notes, backup contents, or credentials. Domain/application code
  must not import Android, Compose, Room, DataStore, or chart types.
- Planner classifies risk before work. Low risk gets a concise plan and skips
  external research unless a current or unknown fact is needed. Elevated work
  requires sourced planning and independent security preflight. Destructive
  work also requires action-specific live approval and recovery.
- Planner checks the exact-scope, 30-day research cache first. Record URLs and
  access dates; stale or mismatched entries are not current evidence.
- Reviewer independently checks the diff and evidence against risk-relevant
  security, privacy, accessibility, correctness, and acceptance requirements.
- Task N+1 may be planned while N is in review when inputs are ready. Parallel
  execution requires disjoint file ownership and a named integration owner;
  dependent work still waits for its dependencies to be done.
- Current runner contracts explicitly select Planner `gpt-6-sol` and Builder
  `gpt-6-luna`. Local configuration does not establish per-agent reasoning or
  Tester/Reviewer model keys. Do not guess them. Public API prices are not
  Codex agent costs.
