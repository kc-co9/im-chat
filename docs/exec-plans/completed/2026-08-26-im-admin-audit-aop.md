# IM Admin Audit AOP Implementation Plan

> **For agentic workers:** Execute sequentially in the current worktree. The user explicitly requested no sub-agents and no commit. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move Account DTO conversion into MapStruct and replace explicit Admin audit execution with an annotation-driven AOP boundary.

**Architecture:** Keep both concerns local to the Admin bounded context. A domain Transformer protects the Account adapter boundary, while an Admin-specific aspect extracts a compile-time audited command contract and preserves the existing audit-first state machine.

**Tech Stack:** Java 21, Spring AOP, MapStruct, JUnit 5, AssertJ, Mockito, Maven.

---

## Objective and non-goals

Implement [IM Admin 审计 AOP 与 Adapter 转换设计](../../design-docs/2026-08-26-im-admin-audit-aop-design.md). Preserve HTTP contracts, persistence, audit records, error mapping, lock order and application results. Do not create a generic audit plugin, add compatibility APIs, spawn sub-agents, use a worktree, or commit.

## Affected modules

- `im-management/im-admin`: Transformer, audit annotation/aspect, Commands, application services, Bean wiring and tests.
- `docs/references`: update Harness feedback only if implementation confirms a reusable convention not already recorded.

## Ordered implementation tasks

### Task 1: Move Account DTO conversion to Transformer

- [x] Add a failing `ManagedUserDomainTransformerTest` for both Account Facade DTO shapes.
- [x] Add `ManagedUserDomainTransformer` with declarative MapStruct mappings.
- [x] Replace the two private Adapter conversion methods and run focused tests.

### Task 2: Define the audited command and annotation contract

- [x] Add a failing structure/aspect test for `AuditedCommand` extraction and missing-context diagnostics.
- [x] Add `AuditedCommand` and make every audited Command implement it.
- [x] Add `@AdminAudited`, including the login actor-resolution option.

### Task 3: Replace the Executor with an aspect

- [x] Convert `AdminAuditExecutorTest` into `AdminAuditAspectTest` and verify RED for pending-first, success, mapped failure, final-write isolation and login actor enrichment.
- [x] Implement `AdminAuditAspect` with an order immediately after the distributed-lock aspect.
- [x] Annotate audited application methods, remove Lambda wrappers and remove `AdminAuditExecutor` dependencies and Bean wiring.
- [x] Run focused audit and application service tests to GREEN.

### Task 4: Harness feedback and final verification

- [x] Review Coding/Harness ownership and record only reusable rules.
- [x] Run `mvn -q -pl im-management/im-admin -am test`.
- [x] Run `mvn -q -pl im-management/im-admin,im-architecture -am test`.
- [x] Run `./scripts/verify.sh affected`, `./scripts/verify.sh quick`, `./scripts/verify.sh full` and `git diff --check`.
- [x] Move this plan to `completed` only after all gates pass; do not commit.

## Rollout and rollback

This is an internal invocation refactor. Rollback restores explicit Executor calls and the Adapter private conversion methods; no data migration or compatibility layer is required.

## Completion criteria

- Adapter contains no manual `ManagedUser` construction.
- Audited application methods contain no explicit audit executor calls.
- Audit ordering, actor identity, success/failure records and error codes remain unchanged.
- Focused and full verification gates pass.
