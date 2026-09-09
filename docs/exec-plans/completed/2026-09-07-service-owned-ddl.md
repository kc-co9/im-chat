# Service-owned DDL Implementation Plan

> **For agentic workers:** Execute locally without subagents. Track every checkbox and do not create Git commits without explicit user authorization.

**Goal:** Move all current DDL from the repository root into its owning deployable Server module and separate Account, Social and Message into independent MySQL schemas.

**Architecture:** Each Server owns `sql/ddl.sql` beside its source tree. Local defaults use `im_chat_account`, `im_chat_social`, `im_chat_message`, `im_chat_iam` and `im_chat_audit`; business services reference other bounded contexts only through business IDs and service contracts, never cross-schema SQL.

**Tech Stack:** MySQL 8, MyBatis-Plus, Spring Boot typed datasource configuration, Ruby/Bash SQL Harness, JUnit 5.

---

## Objective and non-goals

- Move Account, Social, Message, IAM and Audit DDL to their owning Server modules.
- Add explicit local database creation and selection to every owned DDL.
- Change local datasource URLs to their owned schemas.
- Split schema tests so each module verifies only its own tables.
- Do not introduce Flyway/Liquibase, runtime SQL initialization or historical migration scripts.
- Do not add cross-schema foreign keys or SQL joins.

## Ordered tasks

- [x] Add failing SQL Harness fixtures proving module-root `sql/*.sql` is scanned and arbitrary SQL locations are rejected.
- [x] Update SQL checker discovery and `DROP TABLE IF EXISTS` policy for owned `sql/ddl.sql` files.
- [x] Split root business DDL into Account, Social and Message module DDL files with independent database names.
- [x] Move IAM and Audit DDL to their Server modules and add database creation/selection.
- [x] Update local datasource defaults for all five services.
- [x] Split and update schema tests and current README references.
- [x] Update SQL Guide, Harness matrix and architecture documentation, including mandatory template columns for every table.
- [x] Run SQL Harness, five focused module test suites, affected, quick, full and `git diff --check`.
- [x] Move this plan to completed after every gate passes.

## Rollout and rollback

- The project is not deployed, so current DDL is updated directly without compatibility migrations.
- Production database creation remains a deployment operation; applications do not execute these files automatically.
- DDL and datasource configuration must be released together.
- Rollback restores the shared `im_chat` datasource URLs and root DDL as one unit.

## Completion criteria

- Root `sql/` contains no service-owned DDL.
- Every current table appears exactly once under its owning Server module.
- No service queries another service's table.
- SQL Harness and all repository gates pass.
