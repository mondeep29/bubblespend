# BT-021 Planner plan

**Planner:** `bt021_plan` (`gpt-6-sol`), read-only. Prepared 2026-09-29 UTC.

## Objective and acceptance

Implement BT-021-AC-01 through BT-021-AC-07 in `docs/agent-tasks/BT-021.md`: reduce repeated research/context loading, set risk-tiered planning/review expectations, add safe plan persistence, and preserve the security/review gates.

## Dependencies, risk, and boundaries

BT-020 is done. BT-006 is claimed but its Android/domain and task evidence files are disjoint from BT-021's workflow ownership. No product specification or ADR governs workflow policy; `AGENTS.md`, `docs/security-gates.md`, the BT-021 packet, and executable workflow checks are authoritative.

Risk is **elevated** because this task changes security and review policy. No destructive operation is planned. Add an explicit BT-021 elevated risk floor. Keep existing conditional independent preflight for elevated/destructive tasks, destructive action-specific approval, and universal independent final review. Never weaken red-before-green or role separation.

## Implementation plan

1. **Risk-tier contract and enforcement:** Planner classifies risk first. Low-risk tasks get a concise plan but retain structured owner, files, rollback, and acceptance fields; skip external research unless a genuinely current or unknown fact is needed and skip security preflight. Elevated work keeps the full research, source, recovery, and preflight flow. Destructive work retains all elevated gates and the live action-specific permission prompt. Add BT-021 to `gate.py`'s elevated floor. Preserve completed and active legacy evidence where fields are absent.
2. **Research cache:** Add `workflow/research/<topic>.md` format and a small standard-library validator in `workflow/research/cache.py`. Require topic, exact version/API scope, UTC access date, and source URLs. Treat an entry as current only when its date is valid, not in the future, age is 0–30 days inclusive, and topic/version match. Planner checks it first, researches only missing/stale/version-bumped facts, and updates entries without treating cached facts as new research.
3. **Compact context:** Add `workflow/CONTEXT.md` with concise operating rules and architecture/privacy invariants. Agent contracts read it first; task packets include relevant approved spec excerpts and acceptance IDs. Approved specs, ADRs, tests, and public interfaces remain the source of truth; the digest never replaces them.
4. **Scoped review and model settings:** Reviewer receives the owned diff, evidence, criteria, and risk-relevant checklist. Keep review independent. Planner remains explicit `gpt-6-sol`, Builder `gpt-6-luna`. Current repo runner config establishes no per-agent reasoning key and no explicit Tester/Reviewer models; confirm local runner support before setting those fields. Do not conflate public API per-token pricing with Codex agent cost.
5. **Plan recorder:** Add `workflow/record_plan.py BT-NNN` to read UTF-8 plan text from stdin, validate task ID/queue/evidence, size-limit and require nonempty input, write to the deterministic `workflow/evidence/BT-NNN-plan.md` path, and update only `planner.planPath`. Use temporary files and rollback old plan/evidence bytes on partial failure. Reject symlink/path escapes; report rollback errors honestly.
6. **Parallel work:** Document that task N+1 planning may overlap task N review when its inputs are ready. Parallel implementation requires disjoint owned files and a named integration owner; dependent tasks do not start before dependencies are done.

7. **Pending-task consolidation:** Atomically replace three pending handoffs with ordered internal slices while preserving every obligation. BT-007 absorbs BT-008 after its pure domain/contracts slice, then implements Room adapters, migrations, reopen/idempotency/rollback/corruption/upgrade tests, and the complete BT-008 verification suite. Redirect BT-009 and BT-016 from BT-008 to BT-007. BT-011 absorbs BT-013 after dashboard/history, requires BT-003 chart proof plus BT-010 and BT-012 approvals, and retains the complete analytics/insight/accessibility/test/verification scope under approved SPEC-003/004/006. BT-017 redirects BT-013 to BT-011, then absorbs BT-018 as a final ADR-005 sync-readiness review after repositories and migration history exist. Remove only the pending BT-008, BT-013, and BT-018 records from the executable tasks list; preserve their full records in a non-executable archive and preserve their packets/evidence with explicit absorbedInto links. Do not mark absorbed work done, rewrite completed/active history, or change blocked BT-003 or claimed BT-006. Update gate floors, required specs, traceability, and successor packets for the active IDs.
8. **Consolidation recovery:** Before the atomic queue/packet migration, record the active dependency map and byte-preserving recovery scope. On failure, restore workflow/tasks.json, the six affected packets, gate/tests, and BT-021 evidence only. No Android/product source, dependencies, CI, accounts, telemetry, networking, or private financial data are in scope; deleting anything beyond the three pending executable queue records requires a new preflight and any applicable live destructive approval.

## Test-first acceptance plan

Tester owns `workflow/tests/test_gate.py` and new `test_record_plan.py`, `test_research_cache.py`, and focused contract tests.

- Gate: BT-021 elevated floor; low-risk preflight omission while universal independent review remains; elevated independent preflight; destructive approval; legacy evidence behavior.
- Research cache: current at day 30, stale at day 31, future/invalid/missing dates, missing URLs, topic/version mismatch, exact version bump.
- Recorder: valid deterministic write changes only `planner.planPath` and the plan file; invalid ID, absent task/evidence, malformed or oversized input, symlink/path escape, and injected partial write all preserve prior bytes.
- Contracts: context digest, cache-first research instructions, low/elevated plan scope, reviewer categories, explicit model support limitations, and disjoint parallel ownership.

- Consolidation: start with tests that fail against the current queue and packets. Assert unique active IDs; no dangling dependsOn or active references to absorbed IDs; exact BT-009/BT-016/BT-011/BT-017 redirects; only pending records can be absorbed; BT-003 remains blocked and BT-006 claimed; every absorbed objective/test/acceptance/verification/spec/proof obligation appears in its successor; archived records and packets link to the absorbing task; BT-007 requires SPEC-001/002; BT-011 requires SPEC-003/004/006 and chart proof; and next_task.py remains correctly ordered.

Record a genuine red result before Builder implementation.

## Verification

```bash
python3 -m unittest discover -s workflow/tests -p 'test_*.py' -v
python3 -m py_compile workflow/gate.py workflow/transition.py workflow/record_plan.py workflow/research/cache.py
python3 -m json.tool workflow/tasks.json
python3 workflow/next_task.py
```

Parse every `.codex/agents/*.toml` with `tomllib`. Verify invalid recorder input preserves existing plan/evidence bytes. Run BT-021 phase gates at the matching transitions. No Android build is required.

## Model research (official sources, accessed 2026-09-29)

- OpenAI's [models catalog](https://developers.openai.com/api/docs/models) positions GPT-6 Luna for focused, high-volume work and the current public API catalog lists GPT-6.1 Sol for complex coding/agentic work at a cost/intelligence balance. This public API model ID does not establish that the local Codex agent runner accepts the same ID.
- The [GPT-6 Luna model page](https://developers.openai.com/api/docs/models/gpt-6-luna) describes it as efficient for focused, high-volume tasks.
- The [OpenAI API pricing page](https://developers.openai.com/api/docs/pricing) lists standard short-context rates per million tokens of $0.10 input/$0.50 output for GPT-6 Luna and $2 input/$10 output for the current Sol API listing. These are API prices, not Codex agent prices or workflow-run costs.
- Official pages provide a qualitative speed indicator but no numeric latency comparison, so this plan makes no speed claim.
- Local CLI help (codex-cli 0.159.0, inspected 2026-09-29) exposes `-m` and generic `-c`; local project agent TOML explicitly configures Planner and Builder `model` values but does not establish a per-agent reasoning setting or a Tester/Reviewer model schema. Confirm support before changing these fields.

## Recovery and open questions

Restore only BT-021-owned workflow contracts, scripts, cache/docs, tests, and BT-021 evidence. Preserve BT-019/020 behavior, BT-006 evidence, and Android files. Technical open question: exact local runner configuration keys for per-agent reasoning effort and Tester/Reviewer model selection; do not add guessed keys or silently migrate models.

## 2026-10-01 delivery guidance supplement

User requests fewer handoffs, feature delivery, and coding standards before Builder starts. Read-only project Planner Peirce confirms the standards fit AC-03 context guidance. Coordinator owns docs/coding-standards.md and docs/agent-tasks/README.md; Builder references that contract. Standards restate existing ADRs, formatter and privacy constraints, slice order, failure handling, and concrete APK handoff evidence. No product behavior or draft specification is approved by this supplement. The three previously reviewed absorptions remain the executable consolidation scope.

Local sources inspected 2026-10-01: AGENTS.md, docs/adr/ADR-001..005, docs/build-setup.md, docs/security-gates.md, current task packets and workflow tests. No external technical claim or dependency change is needed.

## Feature dependency extension (2026-10-01)

Planner: Peirce, gpt-6-sol, read-only. Independent supplemental preflight: Chandrasekhar, approved, no blocking findings.

Change pending BT-004 dependency to BT-002 and scope to SPEC-001/002 approval. Move complete SPEC-005 authoring, D-01/D-06 resolution, template completion, criteria/test mapping, independent approval and manual verification into BT-003's final documentation phase after required device/chart proof and before done. Preserve BT-003 blocked, BT-006 claimed, all existing device/chart obligations, BT-014 prerequisites and Approved SPEC-005 gate. Once actually approved, the BT-003 coordinator must record SPEC-005 in planner.approvedSpecs for completion validation. No draft approval or product decision is inferred.

Acceptance IDs: BT-021-DEL-01 (in-app spec path independent of overlay proof), BT-021-DEL-02 (complete overlay approval/proof transfer before done), BT-021-DEL-03 (unchanged overlay prerequisite/spec gates). Jason owns workflow/tests/test_feature_delivery.py; Builder owns BT-003/004 packet edits and the queue change after its existing consolidation write; coordinator owns evidence and integration. Snapshot extension files just before editing; restore their bytes on migration failure. Record genuine supplemental red before applying the dependency change. Verify full workflow suite, queue JSON, next-task ordering and independent final review.
