# IM Admin DDD Structure Alignment Implementation Plan

> **For agentic workers:** Execute sequentially in the current worktree. The user explicitly requested no sub-agents, no worktree, and no commit. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align `im-admin` with the Account Server DDD directory skeleton and current CQRS, pagination, Transformer, and dependency conventions without changing runtime behavior.

**Architecture:** Preserve `im-admin` as an independent management bounded context while moving technical types into the same stable layers used by `im-account-server`. Keep Admin capability subpackages, use common pagination and application DTOs, and enforce the resulting dependency direction with ArchUnit.

**Tech Stack:** Java 21, Spring Boot 3.5, ArchUnit, MapStruct, Lombok, MyBatis-Plus, JUnit 5, AssertJ, Maven.

---

## Objective and non-goals

Implement [IM Admin DDD 结构对齐设计](../../design-docs/2026-08-26-im-admin-ddd-structure-alignment-design.md). Preserve all Admin HTTP contracts, persistence schemas, Redis semantics, RBAC, audit behavior, Account Facade ownership and UI behavior. Do not add a Facade, change runtime data, introduce compatibility APIs, use a worktree, spawn sub-agents, or commit.

## Affected modules and ownership

- `im-management/im-admin`: package moves, CQRS/application DTOs, pagination and dependency cleanup.
- `im-architecture`: real Management import and layer rules.
- `docs/references`: Coding/Harness matrix only when implementation establishes a missing reusable rule.
- Existing Account and Admin Facade contracts remain unchanged.

## Ordered implementation tasks

### Task 1: Make Architecture tests observe Management

- [x] Add a focused ArchUnit rule/test that imports `com.co.kc.imchat.management` and rejects Admin domain outer-layer dependencies, Admin application dependencies on local config/infrastructure/interfaces/lifecycle, and AppService-to-AppService dependencies.
- [x] Run the architecture rule with reactor dependencies and record the expected RED from the six `AdminAuthAppService` references to `AdminSecurityProperties` (`-pl im-architecture` alone initially exposed stale/no imported classes; `-am` provided the accurate baseline).
- [x] Keep the rule generic by package role; do not add class-name exceptions.

### Task 2: Align the package skeleton

- [x] Move Adapter, configuration properties, Bean configurations, lifecycle components and Redis Session Repository to their Account-aligned packages.
- [x] Update imports, tests and component scanning.
- [x] Run `mvn -q -pl im-management/im-admin -am -DskipTests compile` and the complete Admin module test suite.

### Task 3: Remove the Application-to-configuration dependency

- [x] Add a failing authentication policy test and use the Architecture RED as the direct dependency assertion.
- [x] Introduce a pure Java authentication policy value used by the application service and construct it from typed properties in Bean configuration.
- [x] Run focused authentication, properties and Bean tests to GREEN.

### Task 4: Converge pagination

- [x] Update tests to construct page Queries with `Paging` and expect `PagingResult` semantics.
- [x] Verify RED while the new HTTP paging parser was absent.
- [x] Replace `PageBounds`, `AdminAccountPage`, `AdminRolePage`, and `AdminAuditPage` with `Paging/PagingResult` across Repository, application, Transformer and HTTP layers.
- [x] Delete obsolete page types and run focused account/role/audit tests.

### Task 5: Converge CQRS boundary values and application results

- [x] Add structural contract tests proving Command/Query records do not expose Admin domain types or primitive components and AppService methods do not return domain types.
- [x] Verify the contract tests fail against the current CQRS records (seven leaked domain types and thirteen primitive components).
- [x] Replace domain-valued CQRS components with basic/wrapper/time values and introduce an application audit context where needed.
- [x] Add `transformer/application` MapStruct mappings; remove AppService DTO construction and stop returning `ManagedUser` to Controllers.
- [x] Run focused application, Transformer and MockMvc tests.

### Task 6: Complete Harness and documentation feedback

- [x] Update Coding/Harness documentation only for reusable rules not already recorded.
- [x] Ensure ArchUnit diagnostics name the offending layer and package.
- [x] Run `./scripts/verify.sh affected` and `./scripts/verify.sh quick`.

### Task 7: Final verification and plan completion

- [x] Run `mvn -q -pl im-management/im-admin,im-architecture -am test`.
- [x] Run `./scripts/verify.sh full` and `git diff --check`.
- [x] Review the final diff for behavior changes and unrelated cleanup.
- [x] Move this plan to `docs/exec-plans/completed` only after all gates pass; do not commit.

## Final verification evidence

- `mvn -q -pl im-management/im-admin,im-architecture -am test`: passed.
- `mvn -q -pl im-management/im-admin,im-architecture -am verify`: passed after exposing the Spring Boot module's original class directory to ArchUnit.
- `./scripts/verify.sh affected`: passed.
- `./scripts/verify.sh quick`: passed.
- `./scripts/verify.sh full`: passed.
- `git diff --check`: passed.

## Rollout, compatibility and rollback

This is a source/package refactor within one unreleased worktree. HTTP paths, JSON fields, database schema, Redis keys and external Facade contracts remain stable. Rollback is file-level reversal of the package/model refactor; no data migration is required.

## Completion criteria

- Admin has the same canonical DDD layer skeleton as Account Server.
- Management is actually covered by ArchUnit.
- Application has no dependency on local configuration/infrastructure/interface/lifecycle packages.
- Common pagination replaces local duplicate page types.
- CQRS input boundaries and AppService output types follow current Coding Guide.
- Focused, quick and full verification pass without weakening Harness rules.
