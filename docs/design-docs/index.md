# Design Documents

Design documents capture context, alternatives, decisions, and consequences. They describe why the system is shaped a certain way; execution status belongs in `docs/exec-plans`.

## Current Designs

- [Unified management console](2026-09-09-unified-management-console-design.md): shared quiet high-density Element Plus visual and interaction contract across independently deployed IAM, Admin, Audit and Monitor consoles, including local port convergence.
- [IAM console interaction](2026-09-08-iam-console-interaction-design.md): scan-first management shell, application detail context, OAuth Client list, drawer-based editing and recoverable operation feedback.
- [IAM Catalog client and OpenAPI convergence](2026-09-08-iam-catalog-client-and-openapi-convergence-design.md): dedicated permission-catalog machine credentials, Spring-side Claim type restoration and explicit MVC documentation paths.
- [Web OpenAPI and IAM Introspection](2026-09-08-web-openapi-and-iam-introspection-design.md): shared Springdoc ownership in `im-web`, authenticated IAM documentation and unambiguous OAuth Token reconstruction for default Introspection.
- [IAM management login](2026-09-07-iam-management-login-design.md): IAM-owned Vue login page, standard Spring Security form authentication, safe SPA continuation, saved OAuth request restoration, and CSRF handling.
- [IAM security hardening](2026-09-02-iam-security-hardening-design.md): resource-audience Introspection, consistent producer identity, atomic OAuth credential consumption, immediate SSO expiry, and login timing hardening.
- [Snowflake configured mode](2026-09-01-snowflake-configured-mode-design.md): explicit STATIC or REDIS selection in `im-identity`, mode-specific validation, and removal of business-owned Snowflake Bean factories.
- [Identity module ownership](2026-09-01-identity-module-ownership-design.md): complete Snowflake capability ownership in `im-identity` with optional Redis integration and direct runtime consumers.
- [Redis Snowflake machine ID](2026-09-01-redis-snowflake-machine-id-design.md): Redis-backed process leases for dynamic Snowflake machine IDs, generic plugin ownership, and IAM integration.
- [IAM and Monitor hardening](2026-08-30-iam-monitor-hardening-design.md): staged authorization correctness, IAM DDD convergence, bounded Monitor aggregation, and protocol/time boundary isolation.
- [Excel plugin boundary](2026-08-28-excel-plugin-boundary-design.md): generic Fesod streaming export lifecycle in `im-excel`, with business row models, querying, authorization and auditing retained by consumers.
- [Central management audit](2026-08-28-central-management-audit-design.md): independent Audit Server/SDK, Kafka or asynchronous HTTP ingestion, unified business and security audit storage, IAM-protected query, and bounded Excel export.
- [Web infrastructure boundary](2026-08-27-web-infrastructure-boundary-design.md): generic HTTP request metadata and `HttpResult` ownership in `im-web`, optional Session Servlet adaptation in `im-session`, and unified Admin/Monitor/IAM SDK response behavior.
- [Management IAM](2026-08-26-management-iam-design.md): centralized administrator identity, OAuth2/OIDC SSO, application-scoped RBAC, realtime introspection and bounded stale-cache fallback for management applications.
- [IM Admin DDD structure alignment](2026-08-26-im-admin-ddd-structure-alignment-design.md): canonical business-module layers, common pagination, CQRS boundary values and real Management Architecture coverage. Implementation completed.
- [Dynamic Harness feedback loop](2026-08-25-dynamic-harness-feedback-loop-design.md): mandatory feedback assessment, Review-only rule handling, and optional internal query-condition structure. Implementation completed.
- [IM Admin user management](2026-08-24-im-admin-user-management-design.md): independent administrator authentication, RBAC, audit, and Account-owned user administration. Implementation completed.
- [Documentation and Harness structure](2026-08-23-documentation-harness-structure-design.md): documentation ownership, stable engineering references, Harness enforcement boundaries, and current-fact cleanup. Implementation completed.
- [Review findings remediation](2026-08-23-review-findings-remediation-design.md): canonical Session locking, authentication lifetime alignment, refresh routing, chat unread behavior, Broker owner routing, and README context restoration. Implementation completed.
- [README and message notification documentation](2026-08-23-readme-notification-documentation-design.md): README ownership, end-to-end notification and ACK flow, receipt retry semantics, and current contract-name cleanup. Implementation completed.
- [Message chat view presence](2026-08-21-message-chat-view-presence-design.md): message-owned current chat view state and unread behavior. Implementation completed.
- [Centralized session authentication](2026-08-14-centralized-session-authentication-design.md): centralized online authentication, rotating refresh tokens, and single-device session replacement. Implementation completed.
- [Broker business monitoring](2026-08-13-broker-business-monitoring-design.md): separated Broker diagnostics, Monitor aggregation/UI, and future Admin ownership. Implementation completed.

## Historical Designs

- [Audit RPC ingestion](2026-08-30-audit-rpc-ingestion-design.md): withdrawn synchronous Dubbo audit transport; current Audit ingestion uses Kafka or authenticated asynchronous HTTP.
- [IM Admin declarative audit and security context](2026-08-26-im-admin-audit-aop-design.md): historical local audit-first design; audit ownership and failure semantics are superseded by the central management audit design.
- [Group member and chat boundary](2026-05-11-group-member-chat-boundary-design.md)
- [Open chat lifecycle](2026-05-12-open-chat-lifecycle-design.md)
- [IM module split](2026-05-20-im-module-split-design.md)
- [Architecture upgrade](2026-06-08-architecture-upgrade-design.md)

New documents use `YYYY-MM-DD-<topic>-design.md` and must link to their execution plan when implementation begins.
