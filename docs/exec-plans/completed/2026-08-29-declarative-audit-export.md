# Declarative Audit Export Implementation Plan

> **Final deviation (2026-08-29):** The accepted export policy now relies on the
> mandatory maximum 31-day query range and streams 100 rows per page without a
> separate maximum-row rejection. `export` returns `void`; the audit event keeps
> annotated query attributes but no exported-row-count result. See the current
> central audit design and Audit Server README.

> **For agentic workers:** Execute in the current workspace without subagents, worktrees, commits, or compatibility code. Use TDD and keep each checkbox current.

**Goal:** Make Audit export use the common declarative audit boundary and reduce `AuditExportAppService` to export orchestration.

**Architecture:** Add one parameter/property annotation, `@AuditAttribute`, while the aspect retains outcome, error-code, context, result capture, and failure-isolation ownership. Annotated scalar parameters use their parameter name; annotated objects and successful object results contribute first-level fields, with `include = false` excluding individual fields. Export-row conversion remains in a focused application Transformer while pagination and workbook lifecycle remain in the application service.

**Tech stack:** Java 21, Spring AOP, MapStruct/project Transformer conventions, Apache Fesod Excel, JUnit 5, AssertJ, Mockito.

**Design source:** [Central management audit design](../../design-docs/2026-08-28-central-management-audit-design.md)

**Affected modules:** `im-management/im-audit/im-audit-sdk`, `im-management/im-audit/im-audit-server`, shared Coding/Harness documentation.

**Non-goals:** Changing HTTP contracts, export limits, workbook columns, transport behavior, or introducing a generic paged-export framework.

---

### Task 1: Declarative dynamic audit attributes

**Files:**
- Modify: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/annotation/Audited.java`
- Create: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/annotation/AuditAttribute.java`
- Modify: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/support/AuditedAspect.java`
- Modify: `im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/ImAuditSdkAutoConfiguration.java`
- Test: `im-management/im-audit/im-audit-sdk/src/test/java/com/co/kc/imchat/management/audit/sdk/support/AuditedAspectTest.java`

- [x] Add failing tests proving annotated scalar/object parameters and successful results become flat attributes, excluded fields are omitted, and failures retain input attributes.
- [x] Implement parameter selection, first-level field extraction, result extraction, exclusion, null omission, and empty-attributes fallback.
- [x] Run focused SDK aspect tests and the complete Audit SDK suite.

### Task 2: Audit export adoption and service simplification

**Files:**
- Create: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/transformer/application/AuditExportAppTransformer.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/application/AuditExportAppService.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/infrastructure/config/beans/AuditServiceBeans.java`
- Delete: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/support/export/AuditExportAuditor.java`
- Test: `im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/application/AuditExportAppServiceTest.java`
- Test: `im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/transformer/application/AuditExportAppTransformerTest.java`

- [x] Add failing tests for the declarative export boundary and export-row conversion, including formula-safe text and explicit time zone.
- [x] Annotate `export`, remove explicit audit `try/catch`, and keep only query conversion, bounded paging, workbook lifecycle, and result construction in the application service.
- [x] Move row conversion out of the application service without creating a generic writer abstraction.
- [x] Delete `AuditExportAuditor` and the temporary export-specific attributes Resolver, update bean wiring, and run focused tests.

### Task 3: Documentation and verification

**Files:**
- Modify: `docs/design-docs/2026-08-28-central-management-audit-design.md`
- Modify: `docs/references/CODING_GUIDE.md`
- Modify: `docs/references/HARNESS_GUIDE.md`
- Modify: `docs/exec-plans/active/README.md`
- Modify: `docs/exec-plans/completed/README.md`

- [x] Document that deterministic management use cases prefer `@Audited`, parameter-level `@AuditAttribute`, automatic successful-result capture, and explicit field exclusion instead of use-case-specific Resolvers or application-level audit `try/catch`.
- [x] Record the convention as Review-only with focused aspect and export tests; do not add a fragile annotation-name checker.
- [x] Run `./scripts/verify.sh quick`, `./scripts/verify.sh full`, and `git diff --check`.
- [x] Move this plan to `completed` without committing.

**Compatibility and rollback:** The new parameter/property annotation is opt-in and existing `@Audited` callers only gain safe successful-result extraction. Rollback restores the explicit export auditor and removes attribute extraction without changing stored audit or export contracts.

**Completion criteria:** Export success and failure are audited by the common aspect with first-level query attributes and successful row count, application export code contains no audit infrastructure control flow or row-formatting logic, all focused and repository gates pass, and no unrelated user changes are included.
