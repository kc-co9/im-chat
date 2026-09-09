# Security

## Trust Boundaries

- External HTTP traffic enters through `im-http-gateway`.
- External WebSocket traffic enters through `im-ws-gateway` and must be authenticated before business frames are accepted.
- HTTP requests and WebSocket handshakes authenticate Access Tokens online through `im-account`; gateways must fail closed on timeout, saturation, malformed results, or provider failure.
- Gateways remove externally supplied user and session headers and rebuild them only from the account authentication result.
- Broker, gateway, and service RPC endpoints are internal interfaces and must not be exposed as public client APIs.
- IAM-protected RPC carries Access Tokens only in the `im-dubbo` reserved attachment. Provider authentication and Scope checks complete before the business adapter runs; the authenticated identity comes from Spring Security, never from RPC Params. Filters restore attachment and security contexts after every invocation and never log Token values.
- Broker management HTTP is an operations-only interface. It defaults to loopback and must be exposed only to the `im-monitor` operations network; browsers access Broker diagnostics only through `im-monitor`.
- `im-iam-server` is the only management credential and RBAC authority. Admin and Monitor accept only IAM-backed BFF Sessions and never ordinary Account Tokens.
- `im-audit-server` is the only management audit persistence authority. Browser APIs use IAM BFF Sessions; internal HTTP ingestion accepts only dedicated Client Credentials tokens with `audit:ingest`.
- Service facades and SDKs define transport contracts; sensitive domain or persistence models must not leak through them.

## Configuration And Secrets

- Production credentials, tokens, private keys, and environment-specific namespace IDs must not be committed.
- `im-account` is the only Token signer and Session authentication authority. When JWT is enabled, startup fails in every profile if the configured key is missing or shorter than 64 UTF-8 bytes.
- Repository configuration may contain a non-production local key so the application can start deterministically. Every deployed environment must replace it through Nacos and must not reuse it as a production secret.
- Authentication and authorization logs must not contain passwords, Access/Refresh Tokens, Refresh fingerprints/digests, complete authentication bodies, user IDs, or session versions.
- Management application Session identifiers use `HttpOnly + Secure + SameSite` cookies; commands additionally require a Session-bound CSRF cookie/header. Usable OAuth2 Tokens are AES-GCM encrypted before Redis persistence.
- IAM administrator brute-force protection is temporary Redis state with atomic failure counting and TTL. It is not an administrator business status; Redis failures fail closed, and successful authentication or administrator security mutations clear the temporary state.
- IAM Access Tokens are opaque and valid for 15 minutes; rotating Refresh Tokens are valid for 8 hours. Local development may use the repository's public classpath PKCS12 and loopback HTTP issuer; production must override it with an HTTPS issuer, externally mounted PKCS12 and deployment-owned secrets, and fails closed without valid key material.
- IAM OAuth clients distinguish their owner application from the single target application. Access Token metadata exposes the owner as `appKey` and the target as standard `aud`; resource services must reject an Introspection result whose audience does not contain their application key.
- Spring Authorization Server owns Authorization Code single use and Refresh Token rotation. Reused or unknown credentials return `invalid_grant`; IAM does not retain historical Refresh Token indexes or add authorization-family replay revocation.
- IAM SSO absolute expiry invalidates the session before its SecurityContext is loaded for the current request. Administrator authentication performs equivalent BCrypt verification work for known and unknown email addresses.
- Administrator bootstrap credentials are one-time deployment inputs. Remove the password from Nacos immediately after the first super administrator is created; never place it in SQL or repository configuration.
- Kafka credentials and production IAM client secrets are deployment-owned. Repository defaults are public local-development values and provide no production trust. Each producer may write only its source Topic.

## Administration Authorization

- Every management endpoint is protected by an application-owned Spring Security Authority and `@PreAuthorize`; hidden UI controls are only a usability aid. Administrator permissions are captured when an Access Token is issued or refreshed, while high-risk account changes revoke current authorizations.
- Only IAM manages administrator credentials, browser applications, machine clients and roles. Admin owns ordinary-user operations; Monitor owns read-only diagnostics; Audit owns all management audit facts.
- Built-in roles cannot be changed or deleted. Role and administrator mutations revoke affected administrator Sessions, and the final active super administrator cannot be disabled.
- Management audit annotations and explicit authentication/protocol publishers never serialize arguments, bodies, return values, credentials, cookies, Session/CSRF identifiers, Tokens, SQL or exception stacks. Audit delivery failure is isolated from the original business result.
- Audit transport payloads do not declare `sourceApp`. HTTP ingestion derives it from the authenticated machine client, while Kafka ingestion injects it from the source-specific binding; producer-controlled payloads cannot override this trusted fact.

## Session Revocation

- Access and Refresh Tokens are accepted only when their session version matches the current online account Session.
- Refresh Tokens rotate atomically; a reused or losing concurrent credential is rejected without invalidating the winning replacement.
- Sign-out and replacement commit Session state before publishing best-effort WebSocket close controls. Control delivery is not an authentication authority.

## Change Requirements

- New ingress endpoints require authentication and authorization decisions to be explicit.
- Broker diagnostics remain read-only, accept only single-user route queries, and must not expose all routes, message payloads, credentials, full stack traces, or write operations.
- New serialization types crossing process boundaries require input validation and compatibility tests.
- New dependencies and plugin auto-configuration must be scoped to the modules that need them.
- Security-sensitive fixes require focused regression tests and an update to this document when the trust model changes.

Report vulnerabilities privately to the repository owner rather than opening a public issue containing exploit details.
