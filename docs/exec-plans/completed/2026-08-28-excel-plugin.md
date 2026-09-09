# Excel Plugin Implementation Plan

> **For agentic workers:** REQUIRED: Use `superpowers:executing-plans` to implement this plan. The user has explicitly prohibited subagents and worktrees for this task. Track every checkbox and do not create Git commits without explicit user authorization.

**Goal:** Extract reusable Fesod-based streaming Excel export capability into `im-plugin/im-excel` and migrate Audit without moving business semantics into the plugin.

**Architecture:** `im-excel` owns Writer lifecycle, generic batch sessions and common converters. `im-audit-server` owns its row model, query orchestration, limits, authorization and audit records, and directly uses the generic template without an Audit-specific delegation wrapper.

**Tech Stack:** Java 21, Maven, Spring Boot auto-configuration, Apache Fesod Sheet, JUnit 5, AssertJ.

---

## 1. Objective and non-goals

### Objective

- Add `im-plugin/im-excel` to the reactor and root dependency management.
- Provide `ExcelTemplate` and generic `ExcelWriteSession<T>`.
- Move the generic enum-name converter into the plugin.
- Migrate Audit streaming export and remove Audit-specific Excel infrastructure wrappers.
- Document module usage and ownership.

### Non-goals

- No Excel import API until a real import use case exists.
- No Audit query, row, authorization, rate limit or auditing logic in the plugin.
- No compatibility wrapper and no Git commit.

## 2. Design reference

- [Excel plugin boundary design](../../design-docs/2026-08-28-excel-plugin-boundary-design.md)
- [Plugin module guide](../../../im-plugin/AGENTS.md)
- [Coding guide](../../references/CODING_GUIDE.md)
- [Unit test guide](../../references/UNIT_TEST_GUIDE.md)

## 3. Affected modules

```text
pom.xml
im-plugin/pom.xml
im-plugin/im-excel
im-plugin/README.md
im-management/im-audit/im-audit-server
```

## 4. Ordered implementation tasks

### Task 1: Add the generic Excel plugin contract

**Files:**

- Modify: `pom.xml`
- Modify: `im-plugin/pom.xml`
- Create: `im-plugin/im-excel/pom.xml`
- Create: `im-plugin/im-excel/README.md`
- Create: `im-plugin/im-excel/src/main/java/com/co/kc/imchat/plugin/excel/ImExcelAutoConfiguration.java`
- Create: `im-plugin/im-excel/src/main/java/com/co/kc/imchat/plugin/excel/core/ExcelTemplate.java`
- Create: `im-plugin/im-excel/src/main/java/com/co/kc/imchat/plugin/excel/core/ExcelWriteSession.java`
- Create: `im-plugin/im-excel/src/main/java/com/co/kc/imchat/plugin/excel/convert/EnumNameConverter.java`
- Create plugin tests under the matching test packages.

- [x] Write failing tests proving multi-batch output, empty-workbook headers, enum-name output, output-stream ownership and auto-configuration replacement.
- [x] Run the focused plugin tests and confirm RED because the generic API does not exist.
- [x] Add the module/dependency declarations and implement the smallest generic API that passes the tests.
- [x] Run `mvn -q -pl im-plugin/im-excel -am test` and confirm GREEN.

### Task 2: Migrate Audit to the plugin

**Files:**

- Modify: `im-management/im-audit/im-audit-server/pom.xml`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/application/AuditExportAppService.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/model/cqrs/dto/AuditExportRow.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/infrastructure/config/beans/AuditServiceBeans.java`
- Delete: Audit-specific Excel exporter/session/Fesod converter files under `support/export`.
- Modify matching Audit tests.

- [x] Write or update Audit tests first to require `ExcelTemplate` and `ExcelWriteSession<AuditExportRow>`.
- [x] Run the focused Audit tests and confirm RED before production migration.
- [x] Replace Audit-specific Excel wrappers with the plugin template and preserve pagination, limits and audit behavior.
- [x] Run `mvn -q -pl im-management/im-audit/im-audit-server -am test` and confirm GREEN.

### Task 3: Documentation and verification

**Files:**

- Modify: `im-plugin/README.md`
- Modify: `im-management/im-audit/im-audit-server/README.md` only if its dependency/behavior description becomes stale.

- [x] Add `im-excel` to the plugin catalog and document its contract, lifecycle and ownership boundary.
- [x] Run `git diff --check` and `./scripts/check-drift.sh`.
- [x] Run `./scripts/verify.sh quick`.
- [x] Run `./scripts/verify.sh full`.
- [x] After all checks pass, move this plan unchanged to `docs/exec-plans/completed` and update documentation indexes if required.

## 5. Compatibility and rollback

There is no public external protocol change. The migration removes internal Audit wrappers without retaining compatibility APIs because the
repository is still under development. Rollback consists of restoring the Audit-local exporter and removing `im-excel` from the reactor.

## 6. Completion criteria

- Generic plugin tests prove streaming lifecycle and conversion behavior.
- Audit tests prove unchanged export behavior through the plugin.
- No plugin source imports Audit or other runtime business packages.
- Audit no longer directly constructs Fesod Writer/Sheet objects.
- Quick and full verification pass.
- No Git commit is created.
