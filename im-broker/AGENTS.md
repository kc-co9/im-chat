# Broker Module Guide

## Ownership

`im-broker-sdk` owns transport-neutral Broker client contracts. `im-broker-server` owns routing, registries, lifecycle coordination, event publication, Gossip projection, and frame processing.

## Boundaries

- SDK code must not depend on server packages or Spring runtime components.
- `domain.registry` interfaces describe storage operations only; business decisions and conditional orchestration belong in domain services.
- `domain` must not depend on `interfaces`, `lifecycle`, `config`, `support`, or concrete plugin implementations.
- RPC handlers parse input and delegate. Do not embed registry migration, routing, or synchronization algorithms in handlers.
- Registry implementations remain separated by entity: Broker, Gateway, and user-to-Gateway connection routes.
- Broker events carry domain values, not handler request objects.
- Gossip state is eventually consistent and must preserve removal operations until their configured TTL expires.

## Verification

For Broker changes run:

```bash
mvn -q -pl im-broker/im-broker-server -am test
./scripts/verify.sh architecture
```
