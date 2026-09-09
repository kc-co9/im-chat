# Audit HTTP And Persistence Mapping Implementation Plan

> **For agentic workers:** Execute in the current workspace without subagents or commits, as requested by the user. Track every step with TDD evidence.

**Goal:** Keep HTTP request conversion declarative in MapStruct and keep audit-attribute JSON encoding inside the MyBatis persistence boundary.

**Architecture:** HTTP controllers bind request objects and delegate Request-to-Query conversion to `AuditHttpTransformer`. `DbAuditEvent` exposes a persistence-native string map while a MyBatis TypeHandler owns JSON storage; `AuditDomainTransformer` only maps persistence values to domain values.

**Tech Stack:** Java 21, Spring MVC, MapStruct, MyBatis-Plus, Jackson/`JsonUtils`, JUnit 5, AssertJ.

---

## Objective And Non-goals

The change removes manual multi-argument CQRS construction from Audit HTTP controllers and removes JSON serialization from domain transformers. It does not change HTTP paths, query semantics, database columns, audit authorization, export behavior, or public SDK contracts.

## Design Reference

- `docs/design-docs/2026-08-28-central-management-audit-design.md`
- `docs/references/CODING_GUIDE.md`
- `docs/references/HARNESS_GUIDE.md`

## Affected Ownership Boundaries

- `interfaces/http` owns request binding and response delivery.
- `transformer/interfaces` owns HTTP Request/Response conversion.
- `transformer/domain` owns declarative Entity/domain mapping, not storage encoding.
- `infrastructure/mybatis` owns JSON column representation and TypeHandler behavior.

## Task 1: HTTP Request To Query Mapping

**Files:**

- Create `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/model/io/AuditPageRequest.java`.
- Create `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/model/io/AuditExportRequest.java`.
- Modify `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/transformer/interfaces/AuditHttpTransformer.java`.
- Modify `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/interfaces/http/AuditQueryController.java`.
- Modify `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/interfaces/http/AuditExportController.java`.
- Add focused transformer and controller tests.

- [x] Add failing tests proving Page/Export Requests map to CQRS queries, including interface-enum to domain-enum conversion.
- [x] Run the focused tests and confirm failure because the request types and mapping methods do not exist.
- [x] Add request objects, MapStruct mapping methods, and `@ModelAttribute` controller binding.
- [x] Run the focused tests and confirm success.

## Task 2: Audit Attributes Persistence Encoding

**Files:**

- Modify `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/infrastructure/mybatis/entity/DbAuditEvent.java`.
- Add a TypeHandler under `infrastructure/mybatis/typehandler` only if the existing MyBatis-Plus handler cannot preserve the required `Map<String, String>` contract.
- Modify `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/transformer/domain/AuditDomainTransformer.java`.
- Delete `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/transformer/domain/AuditAttributesJsonCodec.java`.
- Modify `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/infrastructure/config/beans/AuditRepositoryBeans.java`.
- Add focused persistence-mapping tests.

- [x] Add a failing test proving persisted attributes are represented as a map and round-trip through the MyBatis JSON boundary.
- [x] Run the focused test and confirm failure against the current string/Codec implementation.
- [x] Configure the narrowest reliable MyBatis JSON TypeHandler and make domain mapping declarative.
- [x] Remove the obsolete Codec and Bean.
- [x] Run focused and module tests and confirm success.

## Task 3: Conventions And Verification

- [x] Check whether the existing coding specification already owns Request-to-Query MapStruct mapping and persistence-format conversion; update it only where the reusable rule is absent.
- [x] Add the narrowest reliable Harness check or mark the convention Review-only with an automation condition.
- [x] Run `mvn -q -pl im-management/im-audit/im-audit-server -am test`.
- [x] Run `./scripts/verify.sh quick`.
- [x] Run `./scripts/verify.sh full`.
- [x] Run `git diff --check` and review only the affected files.

## Compatibility And Rollback

No compatibility layer is added because the repository is still under development. The database column remains JSON text and HTTP paths/parameter names remain unchanged. Rollback consists of restoring controller-local CQRS construction and the previous Codec-backed Entity mapping.

## Completion Criteria

- Controllers no longer convert interface enums or manually call long CQRS constructors.
- Audit JSON encoding is absent from `transformer/**`.
- Existing query, detail, ingestion, and export behavior remains covered.
- Module tests, quick verification, full verification, and diff checks pass.
- No commit is created.
