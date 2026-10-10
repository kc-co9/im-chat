# Management Module Guide

## Ownership

`im-management` aggregates independently deployed operational applications. `im-iam/im-iam-server` owns management identity and authorization, while `im-iam/im-iam-sdk` owns reusable management-application security integration. `im-audit/im-audit-server` owns centralized management audit persistence and query, while `im-audit/im-audit-sdk` owns the producer contract and transport integration. `im-ops` owns runtime diagnostics and controlled operational intents. `im-admin` owns ordinary-user administration and its UI.

## Boundaries

- Management applications must not depend on runtime server implementations such as `im-broker-server`.
- Every independently deployed management application depends directly on `im-nacos` for dynamic configuration and service liveness registration.
- `im-iam-sdk` must not depend on `im-iam-server`; Admin and Ops consume IAM only through SDK contracts and standard OAuth2/OIDC endpoints.
- `im-ops` discovers Broker management endpoints through Nacos metadata. Diagnostic reads may target any healthy endpoint; controlled writes use the Broker-owned operation API and follow its Leader hint at most once with the same operation ID.
- Browser code calls only the owning management application's `/api` endpoints and never calls Broker nodes directly.
- Ops read models and operation views are operational projections, not authoritative cluster aggregates.
- Do not share Controller, application service, authentication session, or UI source between `im-ops` and `im-admin`.
- Do not add direct Broker state mutation, all-user route snapshots, a second cluster-state store, or ordinary-user administration behavior to `im-ops`. Broker remains responsible for validating every cluster invariant and committing operations through Raft.
- `im-admin` must not reuse ordinary Account authentication or access Account persistence directly. Ordinary-user management crosses only `im-account-admin-facade`.
- Admin browser credentials stay in same-origin cookies; password, Session, CSRF, Token, and hash values must not enter responses, logs, or audit summaries.
- Admin and IAM publish completed audit facts through `im-audit-sdk`; they must not depend on Audit Server implementations or declare local audit persistence, query APIs, or pages.
- Management write operations require server-side permission checks. Audit submission failures are observable but must not replace the original business result or exception. UI visibility is never the authorization boundary.

## Verification

For ops changes run:

```bash
mvn -q -pl im-management/im-ops -am test
./scripts/verify.sh architecture
```

For Admin changes also run:

```bash
mvn -q -pl im-management/im-admin -am test
cd im-management/im-admin/ui && npm run test:unit && npm run typecheck && npm run build
```

For Audit changes also run:

```bash
mvn -q -pl im-management/im-audit/im-audit-sdk,im-management/im-audit/im-audit-server -am test
cd im-management/im-audit/im-audit-server/ui && npm run test:unit && npm run typecheck && npm run build
```
