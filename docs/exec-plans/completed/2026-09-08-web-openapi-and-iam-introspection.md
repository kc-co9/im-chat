# Web OpenAPI And IAM Introspection Execution Plan

> Final boundary correction: standard OAuth time Claim restoration now occurs in the Spring authorization transformer rather than the database transformer. The current decision is documented in `docs/design-docs/2026-09-08-iam-catalog-client-and-openapi-convergence-design.md`.

## Objective and non-goals

Centralize Springdoc WebMVC support in `im-web`, expose authenticated IAM documentation at `/api/doc.html`, and stop the Audit BFF login loop caused by ambiguous OAuth Token reconstruction.

This work does not introduce Knife4j, publish IAM documentation anonymously, replace Spring's default Introspection Provider, or change OAuth Token formats.

## Design references

- `docs/design-docs/2026-09-08-web-openapi-and-iam-introspection-design.md`
- `docs/design-docs/2026-09-02-iam-security-hardening-design.md`
- `docs/references/CODING_GUIDE.md`

## Affected modules and ownership boundaries

- `im-web` owns the common Springdoc WebMVC dependency.
- `im-iam-server` owns its documentation path and authenticated access policy.
- `im-message-server` consumes Springdoc through `im-web` and retains its local path configuration.
- IAM OAuth infrastructure adapts persisted credentials to Spring's default Introspection contract.

## Ordered implementation tasks

- [x] Add failing IAM OpenAPI dependency and path coverage.
- [x] Add failing unknown Token-type reconstruction coverage.
- [x] Move the Springdoc dependency to `im-web` and configure IAM paths.
- [x] Restore only the digest-matched raw credential during unknown-type lookup.
- [x] Restore standard OAuth time Claim types after JSON persistence.
- [x] Update module and engineering documentation.
- [x] Run focused, quick and full verification.

## Test and verification strategy

```bash
mvn -q -pl im-management/im-iam/im-iam-server -am -Dtest=IamOpenApiConfigTest,OAuthAuthorizationServiceAdapterTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -pl im-plugin/im-web,im-service/im-message/im-message-server -am test
./scripts/verify.sh quick
./scripts/verify.sh full
```

## Rollout, compatibility and rollback

Restart IAM Server after deployment so Springdoc auto-configuration and the OAuth transformer fix are loaded. Existing OAuth clients and stored authorizations remain compatible. Rollback restores per-service Springdoc declarations and the previous transformer behavior, though doing so reintroduces the Introspection login loop.

## Completion criteria

- IAM serves `/api/doc.html` after administrator authentication.
- Message retains its configured Swagger UI through `im-web`.
- Unknown-type Introspection resolves the submitted Access Token as an Access Token.
- Audit no longer begins a new login immediately after a successful callback.
- Focused, quick and full verification pass.
