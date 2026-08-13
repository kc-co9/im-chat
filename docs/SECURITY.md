# Security

## Trust Boundaries

- External HTTP traffic enters through `im-http-gateway`.
- External WebSocket traffic enters through `im-ws-gateway` and must be authenticated before business frames are accepted.
- Broker, gateway, and service RPC endpoints are internal interfaces and must not be exposed as public client APIs.
- Service facades and SDKs define transport contracts; sensitive domain or persistence models must not leak through them.

## Configuration And Secrets

- Credentials, tokens, private keys, and environment-specific namespace IDs must not be committed.
- Local configuration contains safe defaults; Nacos may override runtime values.
- Authentication and authorization failures must not log credentials or message content unnecessarily.

## Change Requirements

- New ingress endpoints require authentication and authorization decisions to be explicit.
- New serialization types crossing process boundaries require input validation and compatibility tests.
- New dependencies and plugin auto-configuration must be scoped to the modules that need them.
- Security-sensitive fixes require focused regression tests and an update to this document when the trust model changes.

Report vulnerabilities privately to the repository owner rather than opening a public issue containing exploit details.
