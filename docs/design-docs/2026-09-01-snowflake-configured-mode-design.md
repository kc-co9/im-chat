# Snowflake Configured Mode Design

Implementation: [Snowflake configured mode execution plan](../exec-plans/completed/2026-09-01-snowflake-configured-mode.md)

## Context

`im-identity` owns the Snowflake algorithm and its static and Redis-backed machine-ID
implementations. Account, Social and Message still create `SnowflakeId` in local Bean
configuration with hard-coded data-center and machine IDs, while IAM relies on the
plugin's Redis auto-configuration. This splits implementation selection between the
plugin and its consumers and hides runtime identity allocation from application
configuration.

`RedisSnowflakeMachineId` also remains in the `snowflake` root package while the static
implementation is already under `snowflake.impl`, even though both implement the same
machine-ID contract.

## Decision

`im-identity` selects and configures the machine-ID implementation. Runtime modules no
longer construct `SnowflakeId` or `ISnowflakeMachineId` implementations themselves.

`SnowflakeProperties` gains a required `mode` with two values:

- `STATIC`: requires `data-center-id` and `machine-id` and creates
  `StaticSnowflakeMachineId`.
- `REDIS`: requires `data-center-id`, uses the existing namespace and lease settings,
  and creates `RedisSnowflakeMachineId` only when a `RedissonClient` is available.

The property prefix remains `im.identity.snowflake`. A missing mode is invalid and
fails application startup. Redis mode also fails startup when Redisson integration is
unavailable; it never falls back to a static machine ID.

Both concrete implementations live under
`com.co.kc.imchat.plugin.identity.snowflake.impl`. The contract and algorithm entry
remain in the `snowflake` package:

```text
snowflake
|-- ISnowflakeMachineId
|-- SnowflakeId
`-- impl
    |-- StaticSnowflakeMachineId
    `-- RedisSnowflakeMachineId
```

## Configuration

Static allocation is explicit:

```yaml
im:
  identity:
    snowflake:
      mode: STATIC
      data-center-id: 1
      machine-id: 1
```

Redis allocation is explicit:

```yaml
im:
  identity:
    snowflake:
      mode: REDIS
      data-center-id: 0
      namespace: im-iam
      lease-duration: 10m
      heartbeat-interval: 30s
```

Account, Social and Message initially preserve their existing `(1, 1)` static values
in local YAML. IAM explicitly selects Redis and preserves its existing data-center and
lease behavior. Nacos may override these local values through the existing application
configuration import.

## Bean Override Boundary

Applications may still provide an `ISnowflakeMachineId` or `SnowflakeId` Bean when a
specialized implementation is genuinely required. Auto-configuration backs off in
that case, but `mode` remains required so the intended runtime strategy stays visible
in configuration. Business modules must not declare ordinary static or Redis
Snowflake Bean factories.

## Alternatives

Separate `static.enabled` and `redis.enabled` switches were rejected because they
permit both implementations or neither implementation to be selected. Keeping the
Bean factories in business modules and binding only their numeric values was rejected
because implementation selection would remain duplicated outside `im-identity`.

## Failure Semantics

- Missing `mode`, missing mode-specific values, or values outside the Snowflake bit
  ranges fail configuration binding or context startup.
- Redis mode without `RedissonClient` fails context startup with a configuration error.
- Static mode does not require Redisson and does not activate Redis lease lifecycle.
- Existing Redis lease-loss behavior remains unchanged.

## Verification

- Property tests cover required mode and mode-specific validation.
- Auto-configuration tests cover static creation, Redis creation, missing Redis
  infrastructure, and custom Bean backoff.
- Application context tests prove Account, Social, Message and IAM obtain
  `SnowflakeId` without local Bean factories.
- Architecture Harness prevents business modules from constructing machine-ID
  implementations directly.
- A repository search confirms concrete Redis and static implementations live only in
  `snowflake.impl` and production business sources contain no `new SnowflakeId` or
  `new StaticSnowflakeMachineId` calls.
