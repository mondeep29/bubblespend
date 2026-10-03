---
name: bubble-spend-review-gate
description: Independently review BubbleSpend changes for spec traceability, data correctness, Android boundaries, privacy, and accessibility.
---

Read the task packet, evidence, approved spec/ADRs, changed files, and tests. Check every acceptance ID has a meaningful passing test; confirm a real red result was recorded for behavior changes. Inspect money/date calculations, Room migrations, transaction boundaries, idempotency, overlay permission/lifecycle, accessible equivalents, and private-data logging where relevant. Run the packet's verification commands when possible. Record concrete findings and required fixes in evidence. Do not approve your own implementation as an independent review.

For elevated or destructive work, perform a scoped security preflight from the Planner's risk assessment **before** Tester, Builder, or device proof actions. The coordinator records that handoff and runs `workflow/gate.py check-start BT-NNN`. Review again after Builder and Tester finish; the coordinator runs `check-done` only after resolving blocking findings. A destructive action also needs a live, action-specific permission approval.
