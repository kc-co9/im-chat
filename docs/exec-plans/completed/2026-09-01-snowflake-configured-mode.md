# Snowflake Configured Mode Implementation Plan

> **For agentic workers:** REQUIRED: Execute this plan in the current session without subagents, as explicitly requested by the user. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configure STATIC or REDIS Snowflake allocation centrally in `im-identity` and remove business-owned hard-coded Snowflake Bean factories.

**Architecture:** `SnowflakeProperties` declares a required allocation mode and mode-specific values. `ImIdentityAutoConfiguration` creates the selected `ISnowflakeMachineId` and the shared `SnowflakeId`; business modules declare only YAML values and consume the Bean.

**Tech Stack:** Java 21, Maven, Spring Boot AutoConfiguration, Configuration Properties, Redisson, JUnit 5, AssertJ.

---

## 1. Objective And Non-goals

- Move `RedisSnowflakeMachineId` into `snowflake.impl`.
- Add required `STATIC` and `REDIS` configuration modes.
- Remove hard-coded Snowflake Bean factories from Account, Social and Message.
- Preserve existing static `(1, 1)` values and IAM Redis lease behavior through YAML.
- Do not change the Snowflake algorithm, Redis lease protocol, generated ID layout or
  service data ownership.
- Do not add compatibility aliases or create a Git commit.

## 2. Design Reference

- [Snowflake configured mode design](../../design-docs/2026-09-01-snowflake-configured-mode-design.md)

## 3. Affected Modules

```text
im-plugin/im-identity       owns mode selection and both implementations
im-service/im-account      removes local Snowflake Bean and selects STATIC
im-service/im-social       removes local Snowflake Bean and selects STATIC
im-service/im-message      removes local Snowflake Bean and selects STATIC
im-management/im-iam       explicitly selects REDIS
im-architecture            prevents runtime modules from constructing implementations
```

## 4. Ordered Tasks

### Task 1: Specify mode-specific configuration behavior

**Files:**

- Modify: `im-plugin/im-identity/src/test/java/com/co/kc/imchat/plugin/identity/properties/SnowflakePropertiesTest.java`
- Modify: `im-plugin/im-identity/src/test/java/com/co/kc/imchat/plugin/identity/ImIdentityAutoConfigurationTest.java`

- [x] Add failing property tests for required mode, STATIC machine ID validation and
  REDIS lease configuration.
- [x] Add failing context tests proving STATIC creates both Beans, REDIS requires a
  `RedissonClient`, and custom Beans retain backoff behavior.
- [x] Run `mvn -q -pl im-plugin/im-identity -am test` and confirm RED for the missing
  mode API and static auto-configuration.

### Task 2: Implement centralized selection and package convergence

**Files:**

- Create: `im-plugin/im-identity/src/main/java/com/co/kc/imchat/plugin/identity/properties/SnowflakeMode.java`
- Modify: `im-plugin/im-identity/src/main/java/com/co/kc/imchat/plugin/identity/properties/SnowflakeProperties.java`
- Modify: `im-plugin/im-identity/src/main/java/com/co/kc/imchat/plugin/identity/ImIdentityAutoConfiguration.java`
- Move: `im-plugin/im-identity/src/main/java/com/co/kc/imchat/plugin/identity/snowflake/RedisSnowflakeMachineId.java`
  to `im-plugin/im-identity/src/main/java/com/co/kc/imchat/plugin/identity/snowflake/impl/RedisSnowflakeMachineId.java`
- Move the corresponding Redis implementation test into the `snowflake.impl` package.

- [x] Add required `mode` and optional `machineId` properties with range validation.
- [x] Create the static implementation only for `STATIC` and the Redis implementation
  only for `REDIS`; never silently fall back.
- [x] Keep custom `ISnowflakeMachineId` and `SnowflakeId` Bean backoff behavior.
- [x] Update imports after moving the Redis implementation.
- [x] Run `mvn -q -pl im-plugin/im-identity -am test` and confirm GREEN.

### Task 3: Remove business Bean factories and migrate configuration

**Files:**

- Delete: `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/infrastructure/config/beans/BasicBeans.java`
- Delete: `im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/infrastructure/config/beans/BasicBeans.java`
- Delete: `im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/infrastructure/config/beans/BasicBeans.java`
- Modify: the corresponding Account, Social and Message `application.yml` files.
- Modify: `im-management/im-iam/im-iam-server/src/main/resources/application.yml`.

- [x] Configure Account, Social and Message with `mode: STATIC`,
  `data-center-id: 1` and `machine-id: 1`.
- [x] Configure IAM with `mode: REDIS` and preserve `data-center-id: 0`.
- [x] Delete the now-empty business `BasicBeans` configurations.
- [x] Run focused tests for Account, Social, Message and IAM server modules.

### Task 4: Harness, documentation and verification

**Files:**

- Modify: `im-architecture/src/test/java/com/co/kc/imchat/architecture/RuntimeDependencyPolicyTest.java`
- Modify: `docs/references/HARNESS_GUIDE.md`
- Modify: `im-plugin/im-identity/README.md`
- Modify: relevant Identity design documents if implementation establishes a deviation.

- [x] Add an architecture test preventing production runtime modules from directly
  constructing `SnowflakeId` or concrete machine-ID implementations.
- [x] Update the Harness matrix and plugin README with required mode semantics.
- [x] Confirm production imports reference the new Redis implementation package and
  no business module declares a Snowflake Bean factory.
- [x] Run `./scripts/check-drift.sh`, `bash scripts/test-java-style-harness.sh` and
  `git diff --check`.
- [x] Run `./scripts/verify.sh affected`, `./scripts/verify.sh quick` and
  `./scripts/verify.sh full`.
- [x] Review the final diff, archive this plan after all checks pass, and do not commit.

## 5. Execution Evidence

- 2026-09-01 RED: focused `im-identity` tests failed because the required mode API
  and centralized implementation selection did not yet exist.
- 2026-09-01 RED: the missing-Redisson-class context test started successfully before
  an explicit REDIS dependency failure was implemented.
- 2026-09-01 RED: the architecture fixture failed to compile before the centralized
  Snowflake construction rule was added.
- 2026-09-01 GREEN: `mvn -q -pl im-plugin/im-identity -am test` passed.
- 2026-09-01 GREEN: focused Account, Social, Message and IAM server tests passed.
- 2026-09-01 GREEN: Drift, Java style Harness, affected, quick and full verification
  gates passed; `git diff --check` reported no whitespace errors.

## 6. Rollout And Rollback

- Local YAML provides complete values; Nacos may override the same keys.
- A missing mode or missing STATIC machine ID fails startup instead of selecting an
  implicit implementation.
- REDIS mode without Redisson infrastructure fails startup and never falls back.
- Rollback restores the three local Bean factories and removes the mode selector; no
  stored data or Redis key migration is required.

## 7. Completion Criteria

- Both concrete machine-ID implementations are under `snowflake.impl`.
- `im-identity` exclusively selects and creates the configured implementation.
- Account, Social and Message contain no hard-coded Snowflake Bean factory.
- IAM explicitly selects Redis mode.
- Focused, architecture, Drift, affected, quick and full verification gates pass.
