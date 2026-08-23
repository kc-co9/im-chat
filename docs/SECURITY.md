# Security

## Trust Boundaries

- External HTTP traffic enters through `im-http-gateway`.
- External WebSocket traffic enters through `im-ws-gateway` and must be authenticated before business frames are accepted.
- HTTP requests and WebSocket handshakes authenticate Access Tokens online through `im-account`; gateways must fail closed on timeout, saturation, malformed results, or provider failure.
- Gateways remove externally supplied user and session headers and rebuild them only from the account authentication result.
- Broker, gateway, and service RPC endpoints are internal interfaces and must not be exposed as public client APIs.
- Service facades and SDKs define transport contracts; sensitive domain or persistence models must not leak through them.

## Configuration And Secrets

- Production credentials, tokens, private keys, and environment-specific namespace IDs must not be committed.
- `im-account` is the only Token signer and Session authentication authority. When JWT is enabled, startup fails in every profile if the configured key is missing or shorter than 64 UTF-8 bytes.
- Repository configuration may contain a non-production local key so the application can start deterministically. Every deployed environment must replace it through Nacos and must not reuse it as a production secret.
- Authentication and authorization logs must not contain passwords, Access/Refresh Tokens, Refresh fingerprints/digests, complete authentication bodies, user IDs, or session versions.

## Session Revocation

- Access and Refresh Tokens are accepted only when their session version matches the current online account Session.
- Refresh Tokens rotate atomically; a reused or losing concurrent credential is rejected without invalidating the winning replacement.
- Sign-out and replacement commit Session state before publishing best-effort WebSocket close controls. Control delivery is not an authentication authority.

## Change Requirements

- New ingress endpoints require authentication and authorization decisions to be explicit.
- New serialization types crossing process boundaries require input validation and compatibility tests.
- New dependencies and plugin auto-configuration must be scoped to the modules that need them.
- Security-sensitive fixes require focused regression tests and an update to this document when the trust model changes.

Report vulnerabilities privately to the repository owner rather than opening a public issue containing exploit details.
