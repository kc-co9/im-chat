# IAM Catalog Client And OpenAPI Convergence Design

## Context

Management applications currently reuse their browser OAuth Client when synchronizing permission catalogs. The registrar requests `client_credentials` with `iam.catalog.write`, while browser clients intentionally support only Authorization Code and Refresh Token grants. This produces a startup `BadRequest` and leaves application permissions unsynchronized.

OAuth Claims persisted as JSON also lose Java runtime types. Restoring `iat`, `exp` and `nbf` inside the database transformer couples persistence mapping to Spring Authorization Server's runtime contract. The type restoration belongs where the framework authorization object is rebuilt.

Springdoc is now provided transitively by `im-web`, but only IAM and Message declare explicit paths. Other deployable MVC applications therefore rely on implicit defaults, and IAM BFF security does not classify the standard API description path as authenticated.

## Decisions

### Dedicated permission catalog client

The IAM SDK uses a dedicated Catalog client distinct from the Web BFF client. Its original sibling configuration paths were superseded by `im.iam.application.clients.catalog` and `im.iam.application.clients.web`; the canonical hierarchy is defined in `2026-09-08-iam-sdk-configuration-hierarchy-design.md`. `IamPermissionCatalogRegistrar` uses only the Catalog credentials.

Admin, Monitor and Audit configure separate Catalog machine clients. Each client is created through the IAM management UI with its application as owner, `imIam` as audience, `CLIENT_CREDENTIALS` as the only grant and `iam.catalog.write` as its only Scope. IAM does not automatically create configured business clients.

### Spring Claim restoration boundary

The database transformer preserves the JSON representation without interpreting Spring-specific Claim types. `OAuthAuthorizationTransformer`, which rebuilds Spring's `OAuth2Authorization`, restores `iat` and `exp` from the typed credential period and converts persisted `nbf` values to `Instant`. `OAuthAuthorizationServiceAdapter` remains a thin protocol adapter.

### Explicit OpenAPI configuration

Every deployable Servlet MVC application that directly consumes `im-web` explicitly declares `springdoc.swagger-ui.path=/api/doc.html` and `springdoc.api-docs.path=/v3/api-docs`. SDK and Plugin libraries do not own application routes. Management BFF security classifies `/v3/api-docs/**` as authenticated, while each runtime service retains its own security policy and context path.

## Verification

- Catalog registrar tests prove it authenticates with the dedicated Catalog credentials.
- Adapter tests prove persisted standard time Claims become `Instant` only when entering Spring Authorization Server.
- Architecture tests enumerate deployable `im-web` applications and require both OpenAPI paths.
- IAM BFF policy tests prove the API description is protected rather than public.
