# Autonomous agent workflow

`tasks.json` is the ordered execution queue. Each task has a packet in `docs/agent-tasks/`. An agent may start a task only when every dependency is `done`, the packet's inputs exist, and applicable specifications are `Approved`. A proof or documentation task can start while the related spec is still Draft if its packet explicitly says so.

Start with [`CONTEXT.md`](CONTEXT.md) for the concise operational digest and
[`../docs/coding-standards.md`](../docs/coding-standards.md) for implementation
conventions when available. Full approved specifications and ADRs remain
authoritative.

## State machine

`pending → claimed → red → implementing → verifying → review → done`. Queue state and `workflow.phase` in task JSON evidence must match. Move tasks only with `python3 workflow/transition.py ACTION BT-NNN`; actions are `claim`, `red`, `implement`, `verify`, `review`, `rework`, and `done`, and each transition checks legal order. Documentation and proof tasks may move from `claimed` directly to `verifying`.

Rework requires `workflow.activeFailure` with an `owner`, `classification`, stable `key`, and `status: "open"`. Builder implementation failures route to `implementing`; Tester failures route to `red`; coordinator specification or environment blockers route to `blocked`. The transition records key occurrences. A second unresolved occurrence of the same key blocks automatic handoff; a different key has its own count. The failure stays unresolved through the start of verification and is cleared only after the passing verification gate admits the task to review. A later recurrence then starts a fresh repair count. The coordinator owns classification and keys, which must not copy sensitive data or raw logs.

Each production gate evaluation appends one JSONL record to `workflow/evidence/BT-NNN-gates.jsonl`, including blocked and evaluator-error outcomes. Records contain only validated task ID, fixed gate name, UTC start/finish, nonnegative monotonic elapsed milliseconds, and `pass`, `block`, or `error`. The pure `gate.check()` function does not log. A gate record describes only the evaluation; it does not assert that a subsequent state-file write committed. Successful transitions append ordered visits to `workflow.phaseTimings`; old active tasks begin with `startedAt: null` and `elapsedMs: null` for their unknown current-phase start. No historical times are inferred. Failed gates and failed persistence leave phase history unchanged.

## Run one task

1. Inspect queue with `python3 workflow/next_task.py` from this folder. The coordinator creates both `workflow/evidence/TASK-ID.md` and `TASK-ID.json` from the templates, then claims exactly one task with `python3 workflow/transition.py claim BT-NNN`.
2. For coding tasks, Planner classifies risk before work. Low-risk tasks use a concise plan and skip external research unless a genuinely current or unknown fact is needed. Elevated tasks retain source-linked research, recovery details, and independent security preflight. Destructive actions additionally require action-specific live approval. Planner checks `workflow/research/<topic>.md` first; entries require source URLs, exact topic/version scope, UTC access date, and are current for at most 30 days. The coordinator persists responses with `python3 workflow/record_plan.py BT-NNN < plan.md`, updating the deterministic plan path and JSON reference together. Run `python3 workflow/gate.py check-start BT-NNN`.
3. Move to `red` with `python3 workflow/transition.py red BT-NNN`; give approved criteria to Tester for the smallest genuine failing acceptance test and record the command and observed failure. Move to `implementing` with `python3 workflow/transition.py implement BT-NNN`; this runs `check-build` before Builder starts. Builder reads the persisted plan and red evidence before minimal implementation and refactor. Documentation/proof packets use `verify` from `claimed` under their stated test exception.
4. After Builder handoff, move to `verifying` with `python3 workflow/transition.py verify BT-NNN`; Tester runs all packet gates and records reproducible results. Move to `review` with `python3 workflow/transition.py review BT-NNN`; this runs `check-review`. A separate Reviewer then inspects correctness, security, accessibility, privacy, and test quality.
5. For a blocking failure, record its owner, classification, and stable key, then use `rework`; do not loop automatically. Resolve findings with Builder/Tester, update traceability, then move from `review` to `done` with `python3 workflow/transition.py done BT-NNN`. The transition rechecks the completion gate. The next agent reads both evidence files before taking a dependent task.

Never mark a task done because a command could not run. Record environment limitations and leave the gate open. Do not create a passing test that merely mirrors implementation details.

## Agent roles

- **Planner:** one persisted executable plan; no production implementation or automatic redesign loop.
- **Tester:** immutable failing acceptance tests, then independent verification; no production edits.
- **Builder:** smallest change that satisfies the approved plan and tests; no test ownership or criteria redesign.
- **Reviewer:** independent security preflight for elevated work and scoped final review; returns actionable blocking findings in one batch.
- **Coordinator:** queue, evidence, handoffs, and final gate result. May be the primary Codex agent.

Keep Tester and Builder separate for behavior tasks, and Reviewer independent of both. Independent final review remains mandatory. Model/reasoning settings must reflect keys supported by the local runner; do not guess unsupported configuration. Planner and Builder models are explicitly set in project contracts; local support for per-agent reasoning and Tester/Reviewer model keys is not established. Public API token prices are not Codex agent costs. Task N+1 may be planned during task N review when inputs are ready. Parallel execution requires disjoint file ownership and an explicit integration owner. Dependencies remain sequential: spec → tests → implementation; domain → repository contracts → Room; expense flow → dashboard → analytics; overlay proof → production overlay; backup schema → restore. Do not begin dependent work before dependencies are done.

## Quality gates

Once Gradle exists, the standard local gate is `./gradlew spotlessCheck lint testDebugUnitTest assembleDebug`; instrumented and release gates are defined in the project build/CI during M1. If a formatter other than Spotless is selected, update this document and every affected task packet. Main/release must also pass connected UI journeys, migration and backup suites, accessibility audit, dependency/secret scans, and release build.

No code task begins until the project foundation supplies reproducible commands. No task claims device behavior without an instrumented proof.
