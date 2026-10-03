---
name: bubble-spend-android-overlay-proof
description: Validate Android overlay permission, lifecycle, input, and privacy behavior on devices before production implementation.
---

Read ADR-004 and SPEC-005. Use a disposable spike first. Record device model, Android API level, permission state, exact steps, observed result, and redacted logs in `docs/proofs/android-overlay.md`. Exercise grant, denial, revocation, disable, drag/snap, expanded entry, exactly-once save boundary, main-app launch, rotation, process death, device restart, and lock-screen privacy. Mark untested cases explicitly; do not infer device behavior from source. Update ADR-004 and product decisions from findings, then approve the spec before production work.
