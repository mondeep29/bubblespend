# Task packet contract

Each packet names a measurable outcome, inputs and criterion IDs, allowed files, exclusions, tests or proof, acceptance, exact verification commands, and evidence. Use ordered implementation slices inside a deliverable packet. Add a new queue task only when work has an independently deliverable outcome or a genuine external dependency; routine layer-by-layer implementation stays within its feature packet. A task cannot silently expand scope.

For packets requiring approved specs, the spec approval and test mapping must be updated before implementation. For proof tasks, record actual device observations; an unrun proof is not completion.

## Delivery order

The active queue and its dependencies define execution order. These groups explain the deliverables:

| Delivery | Packets | Result |
| --- | --- | --- |
| Expense foundation | BT-006, BT-007 | Exact money/calendar primitives, domain contracts, Room persistence and migrations |
| Expense management | BT-009 | Usable create/edit/duplicate/delete/undo and category/payment management |
| Reporting | BT-011 | Dashboard, history, analytics, explainable insights and matching transaction lists |
| Floating entry | BT-014 | Opt-in Android overlay after platform proof and specification approval |
| Backup | BT-016 | Validated local export and atomic restore |
| Release candidate | BT-017 | Cross-feature audits, recoverable release artifact and future sync boundary review |

Read [the builder coding standards](../coding-standards.md) before implementation. The absorbed BT-008, BT-013, and BT-018 packets retain their requirements and archive links; their successors own delivery and completion evidence. Specifications and platform proofs remain explicit readiness work. Product questions are resolved by the owner before the corresponding behavior is implemented.
