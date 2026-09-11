# Gateway Module Guide

## Ownership

`im-http-gateway` owns HTTP routing and edge authentication. `im-ws-gateway-sdk` owns public WebSocket Gateway contracts. `im-ws-gateway-server` owns Netty connections, protocol handling, registration lifecycle, and Broker communication.

Read `ARCHITECTURE.md` before changing authentication, connection ownership, HTTP/WS process boundaries, or Broker interaction. Use the relevant child README for configuration and concrete processing details.

## Boundaries

- Gateway SDK code must not depend on Gateway server packages.
- HTTP Gateway does not contain account, social, or message business logic.
- WebSocket connection IDs and channel details remain local to the Gateway; Broker routes users to Gateway IDs.
- Handlers delegate conversion to transformers and coordination to registries or services.
- Gateway registration and heartbeat are separate operations even when scheduled by one lifecycle component.
- Never trust client-supplied internal identity headers; sanitize and rebuild trusted context at the edge.

## Verification

For Gateway changes run the affected module tests and:

```bash
./scripts/verify.sh architecture
```
