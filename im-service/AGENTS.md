# Service Module Guide

## Ownership

`im-service` contains business capabilities only: account, social, and message. Operational tooling, gateways, Broker coordination, and generic infrastructure do not belong here.

## Boundaries

- Facade modules contain RPC contracts, DTOs, and parameters only. They must not depend on server modules.
- Server `domain` packages do not depend on Spring, HTTP/RPC interfaces, persistence entities, or infrastructure packages.
- `application` coordinates use cases and depends on domain abstractions or remote adapters.
- `interfaces` adapts HTTP/RPC input and delegates to application services.
- `infrastructure` implements domain repositories and external adapters; domain code never imports it.
- Cross-service access uses Facade contracts through adapters. Do not import another service's server classes.
- MapStruct transformers should remain declarative where field mapping is straightforward.

## Verification

For service changes run the affected service tests and:

```bash
./scripts/verify.sh architecture
```
