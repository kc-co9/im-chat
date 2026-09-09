# Audit Time Boundary Implementation Plan

> **For agentic workers:** Execute in the current workspace without subagents, worktrees, commits, or compatibility code, following the user's repository workflow.

**Goal:** Use epoch milliseconds at the Audit browser HTTP boundary, preserve `Instant` internally and in MySQL `TIMESTAMP(3)`, and display time in the user's zone as `yyyy-MM-dd HH:mm:ss`.

**Architecture:** Browser requests and responses carry epoch-millisecond `Long` values. Interface Transformers convert those values to and from application `Instant`; persistence maps `Instant` directly to MySQL `TIMESTAMP(3)`. Browser rendering uses its resolved IANA zone, while server-side Excel export receives that zone explicitly.

**Tech Stack:** Java 21, Spring MVC, MapStruct, MyBatis-Plus, MySQL, Vue 3, Element Plus, TypeScript, Vitest.

---

### Task 1: HTTP epoch-millisecond boundary

**Files:**

- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/model/io/AuditPageRequest.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/model/io/AuditExportRequest.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/model/io/AuditListResponse.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/model/io/AuditDetailResponse.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/transformer/interfaces/AuditHttpTransformer.java`
- Modify focused HTTP and Transformer tests.

- [x] Add failing tests proving request epoch milliseconds map to `Instant` and response `Instant` maps to epoch milliseconds.
- [x] Add required export `timeZone` propagation.
- [x] Implement the smallest MapStruct conversion methods and rerun focused tests.

### Task 2: Direct Instant persistence

**Files:**

- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/infrastructure/mybatis/entity/DbAuditEvent.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/transformer/domain/AuditDomainTransformer.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/infrastructure/domain/repository/MysqlAuditEventRepository.java`
- Modify: `sql/audit-ddl.sql`
- Modify focused persistence tests.

- [x] Add a failing test proving `occurredAt` is persisted directly as `Instant`.
- [x] Change the Entity and repository query to use `Instant` without UTC/`LocalDateTime` conversion helpers.
- [x] Change the database column to `TIMESTAMP(3)` and rerun tests.

### Task 3: User-zone display and export

**Files:**

- Create: `im-management/im-audit/im-audit-server/ui/src/support/dateTime.ts`
- Create: `im-management/im-audit/im-audit-server/ui/tests/DateTime.spec.ts`
- Modify: `im-management/im-audit/im-audit-server/ui/src/api/audit.ts`
- Modify: `im-management/im-audit/im-audit-server/ui/src/views/AuditsView.vue`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/model/cqrs/query/AuditExportQuery.java`
- Modify: `im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/application/AuditExportAppService.java`
- Modify focused UI and export tests.

- [x] Add failing UI tests for timestamp requests and `yyyy-MM-dd HH:mm:ss` rendering.
- [x] Add a failing export test for explicit IANA-zone formatting.
- [x] Implement browser-zone formatting and export-zone propagation.
- [x] Run UI and backend module tests.

### Task 4: Time conventions and verification

- [x] Document storage, domain, HTTP and display time responsibilities in `CODING_GUIDE.md`.
- [x] Add the time/time-zone rules and automation conditions to `HARNESS_GUIDE.md`.
- [x] Run `./scripts/verify.sh quick` and `./scripts/verify.sh full`.
- [x] Run `git diff --check` and move this plan to `completed`.
