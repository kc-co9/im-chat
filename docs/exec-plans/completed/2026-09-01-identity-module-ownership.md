# Identity Module Ownership Implementation Plan

> **For agentic workers:** REQUIRED: Execute this plan in the current session without subagents, as explicitly requested by the user. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move the complete Snowflake capability from `im-common` to `im-identity` without making Redisson a transitive requirement for static consumers.

**Architecture:** `im-identity` becomes the single owner of the Snowflake API, algorithm, constants, static implementation and optional Redis integration. Account, Social, Message and IAM depend directly on that component; `im-common` retains only generic utilities and exceptions.

**Tech Stack:** Java 21, Maven, Spring Boot AutoConfiguration, Redisson, JUnit 5, AssertJ.

---

## 1. Objective And Non-goals

- Move all four `im-common/identity` production types into `im-identity`.
- Preserve Snowflake output and Redis lease behavior.
- Prevent Redisson Starter from becoming a transitive dependency of every identity
  consumer.
- Do not introduce compatibility aliases or split another Maven module.
- Do not switch Account, Social or Message from their existing static allocation in
  this task.

## 2. Design Reference

- [Identity module ownership design](../../design-docs/2026-09-01-identity-module-ownership-design.md)

## 3. Affected Modules

```text
im-common                       removes Snowflake ownership
im-plugin/im-identity           owns core, SPI, static and Redis implementations
im-service/im-account           consumes im-identity
im-service/im-social            consumes im-identity
im-service/im-message           consumes im-identity
im-management/im-iam            updates imports; already consumes im-identity
```

## 4. Ordered Tasks

### Task 1: Establish Snowflake behavior in im-identity

**Files:**

- Create: `im-plugin/im-identity/src/test/java/com/co/kc/imchat/plugin/identity/snowflake/SnowflakeIdTest.java`

- [x] Add deterministic tests for data-center/machine bit placement and increasing
  values using the future plugin package.
- [x] Run the focused test and confirm RED because the plugin-owned types do not exist.

### Task 2: Move the complete identity implementation

**Files:**

- Move: `im-common/src/main/java/com/co/kc/imchat/common/identity/constant/SnowflakeIdConstant.java`
- Move: `im-common/src/main/java/com/co/kc/imchat/common/identity/snowflake/ISnowflakeMachineId.java`
- Move: `im-common/src/main/java/com/co/kc/imchat/common/identity/snowflake/SnowflakeId.java`
- Move: `im-common/src/main/java/com/co/kc/imchat/common/identity/snowflake/impl/StaticSnowflakeMachineId.java`
- Modify: `im-plugin/im-identity/src/main/java/com/co/kc/imchat/plugin/identity/**`

- [x] Move the types to `com.co.kc.imchat.plugin.identity` packages without changing
  algorithm behavior.
- [x] Update Redis allocator, properties and auto-configuration imports.
- [x] Run `SnowflakeIdTest` and existing `im-identity` tests; confirm GREEN.
- [x] Confirm `im-common/src/main/java/com/co/kc/imchat/common/identity` no longer exists.

### Task 3: Make Redis integration optional

**Files:**

- Modify: `im-plugin/im-identity/pom.xml`
- Modify: `im-plugin/im-identity/src/main/java/com/co/kc/imchat/plugin/identity/ImIdentityAutoConfiguration.java`
- Modify: `im-plugin/im-identity/src/test/java/com/co/kc/imchat/plugin/identity/ImIdentityAutoConfigurationTest.java`

- [x] Mark `redisson-spring-boot-starter` optional.
- [x] Use name-based auto-configuration ordering and guard Redis configuration with a
  Redisson class condition plus the existing Bean condition.
- [x] Test that a custom static machine ID still creates `SnowflakeId` and Redis
  allocation still activates when Redisson is present.

### Task 4: Migrate all consumers

**Files:**

- Modify: `im-service/im-account/im-account-server/pom.xml`
- Modify: `im-service/im-social/im-social-server/pom.xml`
- Modify: `im-service/im-message/im-message-server/pom.xml`
- Modify: Java production and test imports returned by repository search for
  `com.co.kc.imchat.common.identity`

- [x] Add direct `im-identity` dependencies to Account, Social and Message server
  modules; IAM already declares it.
- [x] Replace old imports in production and test sources.
- [x] Run focused tests for all four consuming server modules.
- [x] Verify repository Java sources contain no old identity package reference.

### Task 5: Documentation, Harness assessment and verification

**Files:**

- Modify: `im-common/README.md`
- Modify: `im-plugin/im-identity/README.md`
- Modify: `docs/design-docs/2026-09-01-redis-snowflake-machine-id-design.md`
- Modify: `docs/references/HARNESS_GUIDE.md` only if Review establishes a new reliable
  repository-wide rule

- [x] Update module ownership documentation and mark the previous ownership decision
  as superseded.
- [x] Assess whether the migration creates a reusable enforceable Harness rule; do not
  add a one-off package exception.
- [x] Run `mvn -q -pl im-plugin/im-identity,im-service/im-account/im-account-server,im-service/im-social/im-social-server,im-service/im-message/im-message-server,im-management/im-iam/im-iam-server -am test`.
- [x] Run `./scripts/check-drift.sh`, `bash scripts/test-java-style-harness.sh` and
  `git diff --check`.
- [x] Run `./scripts/verify.sh affected`, `./scripts/verify.sh quick` and
  `./scripts/verify.sh full`.
- [x] Review the final diff, archive this plan after all checks pass, and do not commit.

## 5. Completion Criteria

- `im-common` has no identity package or Snowflake classes.
- `im-identity` owns all Snowflake types and its Redis dependency is optional.
- All direct consumers declare `im-identity` and compile without old-package aliases.
- Snowflake behavior and Redis lease behavior remain covered by deterministic tests.
- Harness and repository verification gates pass.

## 6. Execution Notes

- The initial plan mentioned invalid static machine ranges. That check was removed
  before implementation because the existing static implementation does not validate
  ranges and this ownership migration must not silently change its contract.
- Final Review found that an application-provided `SnowflakeId` did not stop the Redis
  allocator because only `ISnowflakeMachineId` was checked. A RED/GREEN
  auto-configuration test now proves both override forms back off without switching
  existing static consumers to Redis.
- Verification completed on 2026-09-01: focused consumer tests, architecture tests,
  drift and Java-style Harness tests, `affected`, `quick` and `full` verification all
  exited successfully. No commit was created.
