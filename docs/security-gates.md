# Security and review gates

Apply these gates **before** a risky task or action, not as a release-only checklist. The coordinator records the result in `workflow/evidence/BT-NNN.json`; the Planner classifies risk, and the independent Reviewer performs the security preflight for elevated or destructive work.

## Classify the proposed work

| Level | Examples | Required before work |
| --- | --- | --- |
| Low | Spec edits, pure UI copy, read-only research | Planner packet and file ownership |
| Elevated | Money/date logic, Room schema or migrations, backup parser, overlay/permission code, dependency or CI changes, data export, release configuration | Planner risk and recovery plan; independent Reviewer security preflight; approved spec where applicable |
| Destructive | Deleting user/project data, destructive migration, replacing a live database, resetting history, force push, publishing a release, changing signing/credentials, writing to external services | Elevated gates plus explicit user approval for the **specific action** through the active Codex permission prompt or equivalent authorized channel |

A task's floor is enforced by `workflow/gate.py`; the Planner raises risk when actual operations warrant it. `check-start` must pass before Tester/Builder/proof execution. `check-build` also requires a genuine failing acceptance test for behavior tasks. `check-review` requires Builder completion and Tester pass. `check-done` requires independent feature review. The coordinator updates the JSON evidence from the agents' handoffs; the read-only Reviewer does not edit it. For destructive work, the JSON records an approval reference and recovery snapshot, but does not itself authorize execution.

## Security preflight questions

- What data or files can be lost, overwritten, exposed, or sent outside the device/workspace?
- Does the change touch financial calculations, time boundaries, Room migration, backup validation, overlay permission, lock-screen content, signing, secrets, or network access?
- What exact targets will change, and what is the rollback or recovery path?
- Are inputs validated and outputs/logs free of transaction values, merchants, notes, backups, and credentials?
- Are permissions requested just in time and revocable? Does denial leave the main app usable?
- Which tests or device observations will prove the risk is controlled?

Record the reviewer's name, decision, specific findings, and scope. An `approved` preflight authorizes work on the reviewed scope only; scope changes require another preflight. A feature review happens **after** Builder and Tester handoffs, even when the preflight was approved.

## Destructive-action rule

Prepare a concrete diff, dry run, backup/recovery path, and exact target first. Then ask for user approval through the execution tool; do not infer approval from a file, silence, or a previous unrelated approval. If approval is unavailable, stop only that action and continue safe independent work. Never put secrets or approval tokens in evidence. Project execution rules prompt for common destructive command families, but shell wrappers and tools can bypass prefix matching. Codex sandbox/approval controls remain authoritative; this workflow is an additional guardrail, not a substitute for them.

## Handoff order

Planner → independent security preflight when required → Tester red tests → Builder implementation → Tester verification → independent Reviewer feature review → coordinator marks done. Documentation-only and disposable proof tasks use packet-specific test exceptions, but retain preflight and independent review where risk is elevated.
