---
name: bubble-spend-spec-to-test
description: Turn approved BubbleSpend acceptance criteria into deterministic failing Android tests before implementation.
---

Read `AGENTS.md`, the task packet, approved spec, and relevant ADRs. For each stable acceptance ID, choose the narrowest test layer that proves visible behavior. Use injected time, IDs, locale, timezone, and in-memory repositories; run the same repository contracts against Room. Include error, empty, permission, accessibility, and interruption cases when specified. Run the test and record the genuine failure in task evidence before production edits. Update the spec's test mapping with exact test file and test name. Do not assert private implementation details when a behavior assertion is possible.
