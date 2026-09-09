# IM Admin Audit Domain Model Implementation Plan

> **For agentic workers:** Execute sequentially in the current worktree. The user explicitly requested no sub-agents, no worktree and no commit. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Correct Admin audit domain naming and make security, lifecycle, equality and persistence-size invariants unavoidable.

**Architecture:** Keep `AdminAudit` as the aggregate root and retain existing audit-first AOP flow. Remove the intermediate request carrier, replace the misleading permission value object with an authorization-source enum, and keep HTTP, domain and persistence enum boundaries explicit through MapStruct.

**Tech Stack:** Java 21, Spring AOP, MapStruct, MyBatis-Plus, JUnit 5, AssertJ, Mockito, Maven.

---

## Objective and non-goals

Implement the “审计领域模型收敛” section in [IM Admin 声明式审计与安全上下文设计](../../design-docs/2026-08-26-im-admin-audit-aop-design.md). Preserve audit ordering, error propagation and final-write isolation. Do not introduce a generic audit plugin, compatibility response fields, sub-agents, a worktree or a commit.

## Affected modules and ownership

- `im-management/im-admin`: audit domain model, AOP creation flow, persistence mapping, HTTP response enum and focused tests.
- `sql/admin-ddl.sql`: rename the new audit table column before the feature is released; no compatibility migration is required for the unreleased schema.
- `docs/references`: update only if implementation establishes a reusable rule not already covered by the DDD and external-enum rules.

## Ordered implementation tasks

### Task 1: Lock down audit value-object invariants

- [x] Add failing tests proving `AuditDescription` always redacts sensitive values, keeps one line and limits output to 512 characters even through its normal constructor.
- [x] Add failing tests for `AuditUserAgent` truncation and rejection of overlong actor name, target ID, client address and error code values.
- [x] Implement the minimal value-object normalization and length checks; make aggregate component value objects serializable.
- [x] Run `mvn -q -pl im-management/im-admin -am -Dtest=AuditDescriptionTest,AdminAuditValueObjectTest -Dsurefire.failIfNoSpecifiedTests=false test`.

### Task 2: Stabilize aggregate identity and lifecycle

- [x] Add failing tests proving equality and hash code remain stable after `succeed` or `fail`.
- [x] Add failing tests proving a terminal timestamp before `createdAt` is rejected.
- [x] Restrict Lombok equality to stable identity and add the terminal-time invariant.
- [x] Separate the auto-increment database primary key from `AuditId`, backfill `pkId` after insert, and keep equality based on the stable business identity.
- [x] Remove the unused no-op `Validator` implementation if no caller invokes it.
- [x] Run `mvn -q -pl im-management/im-admin -am -Dtest=AdminAuditTest -Dsurefire.failIfNoSpecifiedTests=false test`.

### Task 3: Remove misleading authorization metadata and the carrier object

- [x] Add a failing Transformer test proving audit responses no longer require authorization-source metadata.
- [x] Remove `AuditAuthorizationSource`, its HTTP enum, and the corresponding DTO, response, entity and DDL fields.
- [x] Remove `AuditRequest`; build the pending aggregate directly in `AdminAuditAspect` without changing audit ordering.
- [x] Update MapStruct mappings and repository reconstruction.
- [x] Run focused `AdminAuditAspectTest` and `AdminAuditHttpTransformerTest` tests.

### Task 4: Verify and close the plan

- [x] Run `mvn -q -pl im-management/im-admin -am test`.
- [x] Run `./scripts/verify.sh affected` and `./scripts/check-java-style.sh`.
- [x] Run `git diff --check`.
- [x] Review Harness feedback; update Coding/Harness only for reusable rules not already present.
- [x] Move this plan to `docs/exec-plans/completed` after all checks pass.

## Rollout, compatibility and rollback

The Admin/IAM functionality is still unreleased, so no compatibility response alias or database migration is added. Rollback restores the previous field and value-object names together with `sql/admin-ddl.sql`; partial rollback is not supported because domain, HTTP and persistence names must remain aligned.

## Completion criteria

- Sensitive audit summary values cannot bypass normalization.
- Aggregate equality remains stable across state transitions and terminal times cannot precede creation.
- Audit records expose an authorization source rather than a misleading permission code.
- `AuditRequest` and its unused wrapper method no longer exist.
- Focused tests and repository verification gates pass.
