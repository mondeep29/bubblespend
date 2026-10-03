# BubbleSpend agent contract

You are working on a **native Android application**. Read `README.md`, `docs/product/product-brief.md`, relevant `docs/specs/SPEC-*.md`, and relevant `docs/adr/ADR-*.md` before editing. Use the source-of-truth order: approved specifications, approved ADRs, executable tests, public interfaces, implementation, visual mockups.

## Operating rules

- Read `workflow/CONTEXT.md` first for the operational digest and
  `docs/coding-standards.md` for implementation conventions when present;
  approved specifications and ADRs remain authoritative.

- Take the first unblocked task from `workflow/tasks.json`. Read its packet in `docs/agent-tasks/` and claim it with `workflow/transition.py`, then claim file ownership in evidence before editing. Do not start dependent tasks early.
- A feature is ready only when its specification is approved, criteria have stable IDs, user-visible open questions are resolved, and the packet names verification commands.
- Follow spec → failing test → smallest implementation → refactor → verification → traceability → review. Record the red test result before implementation. Move tasks only through `python3 workflow/transition.py ACTION BT-NNN`; queue state and evidence phase must match. Documentation-only and build scaffolding tasks may use their packet's explicit exception.
- Before any coding task, use the project `planner` agent with `gpt-6-sol` for technical research and planning. Planner produces one executable plan, which the coordinator stores with research findings, source links/access dates, and open questions in `workflow/evidence/BT-NNN-plan.md` and references from JSON evidence. Planner must not enter an automatic redesign loop. Only after the required gates and genuine red test pass, use the project `builder` agent with `gpt-6-luna`; Builder follows the persisted plan and does not redesign criteria or edit Tester-owned tests.
- One agent writes a specification; another role reviews it before approval when another agent is available. An implementation agent cannot approve its own specification or independent review.
- Do not invent product behavior to close an open question. Record an assumption and ask the product owner when it changes visible behavior or data semantics. Continue independent work.
- Preserve existing passing behavior. Avoid unrelated refactors and dependency upgrades. Never silently disable tests or add unconditional retries.
- Never log transaction amounts, merchants, notes, backup contents, or credentials. Keep data on device; add no account, telemetry SDK, or network dependency to the MVP.
- Work within task ownership. Concurrent agents must have disjoint files or an integration owner.
- Task N+1 planning may overlap task N review when its inputs are ready. Parallel
  execution requires disjoint file ownership and a named integration owner;
  dependent work still waits for dependencies to be done.
- Generated sources must be reproducible from checked-in schemas/configuration.
- Use `skills/spec-to-test/SKILL.md` for acceptance-test work, `skills/android-overlay-proof/SKILL.md` for the platform proof, and `skills/review-gate/SKILL.md` for independent review.
- Delegate bounded work to the project-scoped `planner`, `tester`, `builder`, and `reviewer` roles in `.codex/agents/` when multi-agent tools are available. A coordinator owns queue/evidence updates and agent handoffs. Planner and Reviewer are read-only.
- Classify every task with `docs/security-gates.md`. Use the `claim`, `red`, `implement`, `verify`, `review`, `rework`, and `done` transitions as applicable; transitions enforce state-aligned `check-start`, `check-build`, `check-review`, and `check-done` gates. For elevated work, an independent security preflight must be recorded before Tester, Builder, or device proof work. Rework evidence must include a failure owner, classification, and stable fingerprint; the second identical unresolved failure blocks automatic handoff.
- Before an actual destructive operation, prepare an exact target, dry run, and recovery path, then obtain action-specific approval through the active permission prompt. A handoff file is never proof of user approval.

## Architecture guardrails

- Kotlin and Jetpack Compose only for the app UI. Android framework code is limited to platform and presentation adapters.
- Domain and application modules cannot import Android, Compose, Room, DataStore, or chart types.
- Room/SQLite is the device source of truth. Use UUIDs, UTC instants, integer minor units, tombstones, positive versions, and an isolated local sync status.
- Transactions and period summaries must share filtering semantics: local calendar timezone, inclusive start and exclusive end.
- Every chart and bubble has an accessible text/list equivalent and a transaction drill-down.
- Overlay permission is opt-in; denial must leave normal app use intact. Validate lifecycle behavior on a representative emulator and a real device before release.

## Completion evidence

Write `workflow/evidence/TASK-ID.md` and `TASK-ID.json` using the templates. Include changed files, specification criterion IDs, red/green commands and results, reviewer findings, assumptions, and known limits. Complete a task only through `workflow/transition.py done` after its gates pass. See `workflow/README.md` for exact state transitions.
