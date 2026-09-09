# IAM Security Hardening Design

> 2026-09-06 更新：项目在上线前改为优先使用 Spring Authorization Server 默认 Provider。
> 管理员权限在 Access Token 签发/刷新时形成快照，Audience 由资源服务校验；历史 Refresh Token
> 表和授权族重放撤销不再实现。下文关于服务端实时权限、调用方 Audience 校验和 Refresh Token
> 历史追踪的内容保留为历史设计背景，不再代表当前实现。

## 1. Context

The IAM security infrastructure currently has four connected correctness gaps:

- Token Introspection accepts a Token only when the caller `clientId` equals the
  Token issuer client. This rejects resource-server Introspection, where a producer
  obtains a machine Token and Audit Server validates it with a separate client.
- IAM writes the producer application as `appKey`, while Audit HTTP security
  read `appId`, so an otherwise valid machine identity cannot be constructed.
- Authorization Code consumption and Refresh Token rotation use read-then-write
  persistence without a compare condition. Concurrent requests can consume the same
  credential more than once and overwrite each other's issued Token state.
- Expiring an IAM SSO `HttpSession` does not clear the authentication already loaded
  for the current request. Unknown administrator emails also skip password hashing,
  creating an observable login timing difference.

This design hardens those boundaries without replacing Spring Authorization Server,
changing the opaque Token strategy, or introducing compatibility behavior.

## 2. Goals And Non-goals

### Goals

- Authorize Introspection by the Token's target resource rather than by issuer-client
  equality.
- Preserve the producer application identity independently from the target resource.
- Enforce single-use Authorization Code and rotating Refresh Token semantics under
  concurrent requests.
- Make SSO absolute expiration effective for the current request.
- Reduce administrator email enumeration through equivalent password-verification
  work for known and unknown accounts.

### Non-goals

- Multiple audiences in one Access Token.
- Dynamic OAuth resource indicators supplied by callers.
- JWT Access Tokens or offline Token validation.
- A general database optimistic-lock version field.
- Compatibility aliases for the incorrect `appId` machine claim.

## 3. OAuth Identity And Audience

An OAuth client has two independent application relationships:

```text
owner application                       target application
        |                                       |
        | issues/uses the client                | receives the Token
        v                                       v
OAuthClient(appId, audienceAppId) -> Access Token(appKey, aud)
```

- `appId` identifies the application that owns the OAuth client.
- `audienceAppId` identifies the single protected application for which its Access
  Tokens are valid.
- `appKey` in Token claims identifies the owner/producer application and remains the
  trusted source identity returned by Introspection.
- Standard `aud` identifies the target application using its stable `appKey`; internal
  numeric IDs are not exposed as protocol identities.

Browser clients normally target their owning application. Audit producer clients are
owned by their producer application but target `imAudit`. The Audit Server's own
Introspection client is owned by `imAudit`.

OAuth client registration requires `audienceAppId`. The application service verifies
that both owner and target applications exist and are active. The database stores
`audience_app_id` as a business-ID reference and indexes it; it does not use the table
primary key as a cross-table business reference.

## 4. Introspection Authorization

管理应用的 BFF 入口使用显式请求边界：页面入口、静态资源、登录和回调保持公开，并在
`IamSecurityFilter` 中跳过 Session 恢复与 Introspection；`/iam/**` 的其他端点和 `/api/**`
要求有效应用 Session，未声明路径默认拒绝。运维端点不继承前端白名单。

For an active opaque Access Token, IAM performs these checks in order:

1. Resolve the authenticated Introspection caller's OAuth client and owning
   application from server-side repositories.
2. Read the Token's persisted `aud` claim.
3. Require the caller application's `appKey` to equal the Token audience.
4. Resolve the Token owner from persisted authorization metadata and return that
   owner's `appKey` as the source application.
5. For administrator Tokens, re-check administrator status and resolve current
   permissions for the audience application.

The handler never trusts caller-supplied `appKey`, `appId`, audience, or permissions.
A missing, malformed, inactive, or mismatched identity produces `active=false`.

Audit HTTP consumers read `appKey`; the old `appId` claim is removed rather
than supported as an alias. Management SDK validation continues requiring its local
application key to match the Introspection result.

## 5. Atomic Credential Consumption

IAM retains digest-only Token persistence. When `findByToken` restores an
Authorization Code or Refresh Token, it adds an internal, non-serialized persistence
attribute containing the matched credential type and its digest.

OAuth Token Claims remain structured JSON. MyBatis `JacksonTypeHandler` uses the application
`ObjectMapper`, including Java Time modules, so standard `Instant` claims such as `iat`, `nbf`
and `exp` are persisted without protocol-specific conversion in the Repository.

`save` distinguishes ordinary authorization persistence from credential consumption:

- Authorization Code exchange updates only when the stored code digest still matches,
  `authorization_code_used_at` is null, and the authorization is active.
- Refresh rotation updates only when the stored Refresh Token digest still matches and
  the authorization is active.
- After a successful rotation, the consumed digest is appended to
  `db_iam_refresh_token_history`. The table contains only SHA-256 digests and the
  authorization identifier, allowing replay of any older generation to revoke the
  current Token family without retaining usable Tokens.
- A conditional update count other than one raises OAuth `invalid_grant`; no Token
  response is returned to the losing request.
- Reuse of an Authorization Code revokes the authorization and Tokens already issued
  from that code. Reuse of a rotated Refresh Token revokes the current authorization
  so the surviving credential cannot continue an ambiguous Token family.

The compare condition belongs to the MyBatis persistence service because it is a
technical atomicity mechanism. The OAuth authorization adapter decides when the
condition is required. No generic Repository lock API or entity version field is
introduced.

## 6. SSO And Login Hardening

The SSO lifetime filter runs before Spring loads the session-backed SecurityContext.
When the absolute lifetime has elapsed, it invalidates the session and clears the
current SecurityContext before continuing. Authorization later in the chain therefore
treats the same request as unauthenticated.

Administrator authentication always performs one password-hash verification. For an
unknown email, the password service verifies against a fixed, valid BCrypt dummy hash
and returns authentication failure. The dummy hash is a non-secret work factor, is not
configurable, and never represents a real administrator credential. Existing-account
restriction counters remain keyed by administrator ID; broader IP or identifier-based
rate limiting is outside this change.

## 7. Testing

Focused tests must prove:

- a resource-server client can introspect a producer Token whose audience matches the
  resource application;
- an unrelated application's client receives `active=false`;
- machine Introspection exposes `appKey`, and Audit HTTP identity construction uses
  that exact field;
- two exchanges of the same Authorization Code allow exactly one conditional update;
- two rotations of the same Refresh Token allow exactly one conditional update;
- an expired SSO session clears the current SecurityContext before downstream code;
- known and unknown administrator emails both invoke password verification once;
- malformed or absent audience and application identity fail closed.

Existing digest-only persistence, PKCE, exact redirect URI, inactive client/application,
revocation, permission-resolution, Audit ingestion, IAM SDK, quick and full verification
tests remain required.

## 8. Rollout And Migration

This repository is still under development, so the change is a direct migration:

1. Add and populate `audience_app_id` for every OAuth client fixture/bootstrap record,
   and create the consumed Refresh Token digest-history table.
2. Issue only Tokens containing `appKey` and `aud` under the new contract.
3. Update the Audit HTTP consumer from `appId` to `appKey` in the same change.
4. Restart IAM and management applications; previously issued development Tokens are
   intentionally invalidated and must not receive compatibility handling.

Rollback restores the previous code and schema together. A partial rollback is not
supported because it would reintroduce claim and audience ambiguity.
