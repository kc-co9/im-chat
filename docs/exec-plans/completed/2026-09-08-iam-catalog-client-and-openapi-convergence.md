# IAM Catalog Client And OpenAPI Convergence Execution Plan

## Objective and non-goals

Separate permission catalog machine credentials from browser OAuth clients, move Spring Claim type restoration to the Spring adapter boundary, and explicitly configure OpenAPI paths for all deployable `im-web` applications.

This work does not automatically create OAuth clients, mix browser and machine grants, publish API documentation anonymously, or change Token formats.

## Design references

- `docs/design-docs/2026-09-08-iam-catalog-client-and-openapi-convergence-design.md`
- `docs/design-docs/2026-09-08-web-openapi-and-iam-introspection-design.md`
- `docs/design-docs/2026-08-26-management-iam-design.md`

## Affected modules and ownership boundaries

- `im-iam-sdk` owns Catalog client configuration and permission synchronization.
- Admin, Monitor and Audit own their Catalog client values.
- `im-iam-server` Spring infrastructure owns conversion into Spring authorization objects.
- Deployable MVC applications own their Springdoc paths and security policy.

## Ordered implementation tasks

- [x] Add failing Catalog credential isolation tests.
- [x] Add failing Spring adapter Claim restoration coverage.
- [x] Add failing OpenAPI configuration and authenticated-path coverage.
- [x] Implement dedicated Catalog client properties and update application configuration.
- [x] Move standard time Claim restoration from DB mapping to the Spring transformer.
- [x] Configure OpenAPI paths for every deployable `im-web` application.
- [x] Update README, engineering guidance and Harness ownership.
- [x] Run focused, quick and full verification.

## Test and verification strategy

```bash
mvn -q -pl im-management/im-iam/im-iam-sdk,im-management/im-iam/im-iam-server -am test
mvn -q -pl im-architecture -am test
./scripts/verify.sh quick
./scripts/verify.sh full
```

## Rollout, compatibility and rollback

Before restarting Admin, Monitor or Audit, create the configured Catalog machine client in IAM. Existing browser clients and Sessions remain compatible. If a Catalog client is absent or invalid, the application still starts but readiness remains failed and synchronization is retried only by the existing registrar lifecycle.

## Completion criteria

- Permission catalog synchronization never uses browser BFF client credentials.
- Spring receives `Instant` standard time Claims without DB Transformer framework coupling.
- Every deployable `im-web` application has explicit Swagger UI and API description paths.
- Management API descriptions require an IAM application Session.
- Focused, quick and full verification pass.
