# Identity Module Ownership Design

> Configuration update: the later [Snowflake configured mode design](2026-09-01-snowflake-configured-mode-design.md)
> centralizes STATIC and REDIS implementation selection in `im-identity`. Statements
> below about static consumers constructing their implementation describe the
> ownership migration stage and no longer define current runtime assembly.

## Context

The Redis Snowflake allocator introduced `im-plugin/im-identity`, while the Snowflake
algorithm, machine-ID contract, constants and static implementation remained under
`im-common/identity`. That split makes consumers depend on `im-common` for the main API
and on `im-identity` only for one infrastructure implementation. It also leaves a
cohesive reusable component inside the repository's broad common module.

The identity package currently contains only four Snowflake-specific types. Account,
Social, Message and IAM use those types directly; no facade or SDK contract exposes
them.

## Decision

`im-identity` owns the complete Snowflake capability:

- `SnowflakeIdConstant` moves to `com.co.kc.imchat.plugin.identity.constant`.
- `ISnowflakeMachineId`, `SnowflakeId` and `StaticSnowflakeMachineId` move under
  `com.co.kc.imchat.plugin.identity.snowflake`.
- `im-common` no longer contains an `identity` package.
- Runtime modules that generate Snowflake IDs declare a direct `im-identity`
  dependency and import its types.
- No compatibility aliases remain in the old package because the project is still in
  development.

The module remains a single component rather than splitting into core and Redis
artifacts. To keep that decision lightweight, Redisson is an optional Maven dependency.
Consumers that use only the algorithm or static machine-ID implementation do not gain
Redisson transitively. Redis auto-configuration activates only when Redisson classes and
a `RedissonClient` Bean are both available.

## Dependency Boundary

`im-identity` may depend on `im-common` for stable generic exceptions and utilities.
`im-common` must not depend on `im-identity`. Business, management and runtime modules
may depend on `im-identity`; facade and SDK contracts do not gain the dependency unless
they expose identity types in their public API.

The resulting ownership is:

```text
im-common
  generic utilities and exceptions

im-plugin/im-identity
  Snowflake algorithm and machine-ID contract
  static machine-ID implementation
  optional Redis allocator and Boot auto-configuration

Account / Social / Message / IAM
  consume SnowflakeId from im-identity
```

## Compatibility And Runtime Behavior

This change only moves Java ownership and Maven dependencies. Snowflake bit allocation,
epoch, generated values, Redis keys, lease semantics and IAM configuration remain
unchanged.

Static consumers keep constructing `StaticSnowflakeMachineId`. IAM continues to obtain
the Redis-backed implementation through auto-configuration. Removing the old package is
intentional so stale imports fail during compilation.

## Verification

- Algorithm tests move to `im-identity` and prove stable bit composition and monotonic
  sequence behavior.
- Auto-configuration tests cover static override and Redis activation.
- Repository search proves no Java source references
  `com.co.kc.imchat.common.identity`.
- Architecture, Drift, affected, quick and full verification gates must pass.

This design supersedes only the ownership statement in
`2026-09-01-redis-snowflake-machine-id-design.md` that kept Snowflake core types in
`im-common`; its Redis lease decisions remain current.
