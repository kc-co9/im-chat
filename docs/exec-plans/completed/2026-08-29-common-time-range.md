# Common Time Range Implementation Plan

> **For agentic workers:** Execute in the current workspace without subagents, worktrees, commits, or compatibility code.

**Goal:** Introduce a shared `TimeRange` domain value object and use it as the Audit domain query time boundary.

**Architecture:** HTTP and CQRS models keep their explicit `occurredFrom` and `occurredTo` fields. `AuditAppTransformer` assembles those fields into `com.co.kc.imchat.common.domain.time.model.TimeRange`; the domain query condition and Repository consume the value object.

**Design source:** [Central management audit design](../../design-docs/2026-08-28-central-management-audit-design.md)

**Affected modules:** `im-common`, `im-management/im-audit/im-audit-server`, shared Coding/Harness documentation.

---

### Task 1: Shared time range value object

- [x] Add failing tests for inclusive bounds, duration, null bounds and reversed bounds.
- [x] Implement the immutable, serializable `TimeRange` value object.
- [x] Run focused `im-common` tests.

### Task 2: Audit domain query adoption

- [x] Add failing Transformer and Repository tests that use `TimeRange`.
- [x] Replace the two Audit domain condition timestamps with `TimeRange`.
- [x] Assemble the value object in MapStruct and consume it in the Repository.
- [x] Run focused Audit tests and the complete Audit module test suite.

### Task 3: Documentation and verification

- [x] Document shared time-range ownership in Coding Guide, Harness matrix, Common README and Audit design.
- [x] Run `./scripts/verify.sh quick`, `./scripts/verify.sh full` and `git diff --check`.
- [x] Move this plan to `completed` without committing.

**Compatibility and rollback:** No external HTTP or Java SDK contract changes. Rollback restores the two internal domain-condition fields and removes the shared value object.

**Completion criteria:** Time invariants have focused tests, Audit domain queries use `TimeRange`, all repository gates pass, and no unrelated changes are included.
