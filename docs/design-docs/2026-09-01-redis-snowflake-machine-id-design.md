# Redis Snowflake Machine ID Design

> Ownership update: the later [Identity module ownership design](2026-09-01-identity-module-ownership-design.md)
> moves the Snowflake algorithm, machine-ID contract, constants and static
> implementation from `im-common` into `im-identity`. The Redis lease decisions below
> remain current.
>
> Configuration update: the later [Snowflake configured mode design](2026-09-01-snowflake-configured-mode-design.md)
> makes STATIC or REDIS selection explicit and required. The plugin no longer selects
> Redis merely because a `RedissonClient` exists.

## Context

`im-common` already owns the framework-neutral `SnowflakeId`,
`ISnowflakeMachineId` and static machine-ID implementation. IAM currently creates a
static Snowflake node through IAM-specific configuration. Static machine IDs require
manual coordination and do not scale safely when service instances change dynamically.

The existing `life-platform` Redis allocator is the behavioral reference, but it must
not be copied unchanged: Redisson is an infrastructure dependency, IPv4 is not a unique
JVM owner identity, and its millisecond expiry constant is compared with Redis time in
seconds.

## Decision

Add `im-plugin/im-identity` as the reusable Redis-backed identity integration.

- `im-common` remains framework-neutral and continues to own the Snowflake algorithm,
  machine-ID interface, constants and static implementation.
- `im-identity` owns `RedisSnowflakeMachineId`, typed plugin configuration and Spring
  Boot auto-configuration.
- IAM depends on `im-identity`, consumes the auto-configured `SnowflakeId`, and deletes
  `IamSnowflakeProperties` and its local Snowflake Bean factory method.
- The plugin uses `im.identity.snowflake.*`; it does not introduce service-specific
  configuration keys.

## Lease Model

Redis stores one hash per data center. A hash field identifies a machine slot within an
allocation namespace, while its JSON value contains a random per-process owner ID and
the last heartbeat from Redis server time.

Allocation, renewal and release use Lua so ownership checks and writes are atomic:

1. Allocation claims the first absent or expired slot in the Snowflake machine range.
2. Renewal succeeds only while the slot still belongs to the current process.
3. A lost lease invalidates the local machine ID before attempting reallocation.
4. Release deletes only a slot owned by the current process.
5. Redis failures fail closed for ID generation rather than continuing with an
   ownership state that cannot be proven.
6. A local monotonic deadline blocks ID generation after a JVM pause that outlives the
   lease, even when the heartbeat task has not run yet.

The owner ID is generated once per JVM with `GeneratorUtils.nextRandomId`; IP addresses
are not used as process identity. Lease and heartbeat values use `Duration`, and Lua
receives seconds to match Redis `TIME`.

## Configuration

`SnowflakeProperties` uses prefix `im.identity.snowflake` and contains:

- `data-center-id`: Snowflake data-center segment, from 0 through 31.
- `namespace`: allocation namespace; defaults to `spring.application.name`.
- `lease-duration`: ownership expiry, default 10 minutes.
- `heartbeat-interval`: renewal interval, default 30 seconds and strictly shorter than
  the lease duration.

Lease duration is at least one second because Redis lease timestamps use seconds.
Different namespaces may reuse the same machine slots; applications that require
cross-service ID uniqueness must share one namespace.

The plugin activates when a `RedissonClient` exists. It exposes missing
`ISnowflakeMachineId` and `SnowflakeId` Beans so a business application can explicitly
override the generic implementation when necessary.

## Testing And Operations

- Unit tests cover initial unavailable state, allocation, exhaustion, lost-lease
  invalidation/reallocation, and owner-checked release.
- Auto-configuration tests cover property validation, Bean replacement and lifecycle.
- IAM context tests prove it no longer declares or binds IAM-specific Snowflake
  configuration.
- Plugin README documents Redis availability as a startup/runtime dependency.

No compatibility alias is retained for `im.iam.snowflake.*`; the project is still under
development. This change does not switch unrelated services to Redis allocation.
