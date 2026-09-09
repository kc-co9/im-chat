# Redis Snowflake Machine ID Implementation Plan

> **For agentic workers:** REQUIRED: Execute this plan in the current session without subagents, as explicitly requested by the user. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a reusable Redis-backed Snowflake machine-ID allocator and make IAM consume it instead of IAM-specific static node configuration.

**Architecture:** Keep the Snowflake algorithm and SPI in framework-neutral `im-common`. Add `im-plugin/im-identity` for Redisson lease allocation and Boot auto-configuration, then remove the IAM-owned Snowflake properties and Bean factory.

**Tech Stack:** Java 21, Spring Boot AutoConfiguration, Redisson Lua scripting, JUnit 5, Mockito, AssertJ.

---

## 1. Objective And Non-goals

- Migrate and harden Redis machine-ID allocation from `life-platform`.
- Provide typed generic configuration and lifecycle-managed Beans.
- Replace IAM static machine-ID configuration.
- Do not switch Account, Message, Social, Broker or Gateway in this task.
- Do not add compatibility aliases for `im.iam.snowflake.*`.

## 2. Design Reference

- [Redis Snowflake machine ID design](../../design-docs/2026-09-01-redis-snowflake-machine-id-design.md)

## 3. Affected Modules And Ownership

```text
im-common                  Snowflake algorithm and machine-ID SPI
im-plugin/im-identity      Redis allocation, configuration and lifecycle
im-management/im-iam      consumes auto-configured SnowflakeId
```

`im-common` must not gain Redisson or Spring dependencies. `im-identity` must not depend
on IAM or another business/runtime module.

## 4. Ordered Implementation Tasks

### Task 1: Add the identity plugin skeleton and configuration

**Files:**

- Modify: `pom.xml`
- Modify: `im-plugin/pom.xml`
- Create: `im-plugin/im-identity/pom.xml`
- Create: `im-plugin/im-identity/src/main/java/com/co/kc/imchat/plugin/identity/ImIdentityAutoConfiguration.java`
- Create: `im-plugin/im-identity/src/main/java/com/co/kc/imchat/plugin/identity/properties/SnowflakeProperties.java`
- Create: `im-plugin/im-identity/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
- Test: `im-plugin/im-identity/src/test/java/com/co/kc/imchat/plugin/identity/SnowflakePropertiesTest.java`

- [x] Write failing binding/validation tests for data-center range, nonblank namespace,
  positive lease/heartbeat and heartbeat shorter than lease.
- [x] Add the module and managed dependency.
- [x] Implement immutable typed configuration with documented defaults.
- [x] Run the focused property tests and confirm GREEN.

### Task 2: Implement Redis lease allocation

**Files:**

- Create: `im-plugin/im-identity/src/main/java/com/co/kc/imchat/plugin/identity/snowflake/RedisSnowflakeMachineId.java`
- Test: `im-plugin/im-identity/src/test/java/com/co/kc/imchat/plugin/identity/snowflake/RedisSnowflakeMachineIdTest.java`

- [x] Write failing tests for unavailable-before-start, first-free allocation, exhausted
  slots, lost ownership, reallocation and owner-checked release.
- [x] Implement atomic Lua allocation, renewal and release using Redis server seconds.
- [x] Use a random JVM owner ID and a daemon heartbeat executor.
- [x] Fail closed after lease loss or Redis renewal failure.
- [x] Run the allocator tests and confirm GREEN.

### Task 3: Auto-configure and document the plugin

**Files:**

- Modify: `im-plugin/im-identity/src/main/java/com/co/kc/imchat/plugin/identity/ImIdentityAutoConfiguration.java`
- Create: `im-plugin/im-identity/src/test/java/com/co/kc/imchat/plugin/identity/ImIdentityAutoConfigurationTest.java`
- Create: `im-plugin/im-identity/README.md`
- Modify: `im-plugin/README.md`

- [x] Write failing context tests proving Redisson activation, Bean replacement and
  lifecycle start/close behavior.
- [x] Expose missing `ISnowflakeMachineId` and `SnowflakeId` Beans.
- [x] Register auto-configuration and document configuration/runtime behavior.
- [x] Run all `im-identity` tests and confirm GREEN.

### Task 4: Replace IAM static Snowflake configuration

**Files:**

- Modify: `im-management/im-iam/im-iam-server/pom.xml`
- Modify: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/config/beans/IamServiceBeans.java`
- Delete: `im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/infrastructure/config/properties/IamSnowflakeProperties.java`
- Replace: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/infrastructure/config/properties/IamSnowflakePropertiesTest.java`
- Modify: `im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/infrastructure/config/beans/IamServiceBeansTest.java`
- Modify: `im-management/im-iam/im-iam-server/README.md`

- [x] Write a failing IAM architecture/context test proving IAM no longer declares an
  IAM-specific Snowflake properties type or Snowflake Bean factory.
- [x] Add the plugin dependency and remove the IAM-specific implementation.
- [x] Update IAM runtime documentation to use `im.identity.snowflake.*`.
- [x] Run focused IAM configuration and context tests and confirm GREEN.

### Task 5: Harness assessment and verification

- [x] Assess the implementation against the dynamic Harness loop. Record a new rule
  only if Review establishes a reusable low-noise convention beyond existing plugin
  ownership and typed-configuration rules.
- [x] Run `mvn -q -pl im-plugin/im-identity,im-management/im-iam/im-iam-server -am test`.
- [x] Run `bash scripts/test-java-style-harness.sh` and `./scripts/check-drift.sh`.
- [x] Run `./scripts/verify.sh affected`, `./scripts/verify.sh quick` and
  `./scripts/verify.sh full`.
- [x] Run `git diff --check` and review the final diff without committing.

Harness assessment: the implementation follows existing plugin ownership,
business-override and typed-configuration rules. Lease expiry is component behavior and
is covered by deterministic tests and runtime documentation; it does not establish a
new reliable repository-wide static rule.

Verification checkpoint (2026-09-01): focused plugin/IAM tests, architecture tests,
Drift, Java Style Harness, `affected`, `quick`, `full` and `git diff --check` all passed.

## 5. Rollout And Compatibility

IAM now requires Redis to allocate a Snowflake machine ID before serving ID-generating
use cases. A process that cannot prove lease ownership fails ID generation. Rollback
restores the previous IAM properties and static allocator together; mixed configuration
keys are not supported.

## 6. Completion Criteria

- Redis allocation, renewal, expiry and release behavior have deterministic tests.
- IAM contains no `IamSnowflakeProperties` or local Snowflake Bean factory.
- Plugin, IAM, Harness, architecture and repository verification pass.
- Documentation describes the generic configuration and Redis dependency.
- No Git commit is created without explicit user authorization.
