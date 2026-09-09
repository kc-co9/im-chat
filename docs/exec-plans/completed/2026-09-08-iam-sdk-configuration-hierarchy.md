# IAM SDK Configuration Hierarchy Execution Plan

## Objective and non-goals

Replace the flattened IAM SDK client configuration with one hierarchy that visibly owns the current application, its Web and Catalog OAuth clients, and its local Session.

This work does not change OAuth grants, client registrations, Token contents, Session behavior or production secret management.

## Design references

- `docs/design-docs/2026-09-08-iam-sdk-configuration-hierarchy-design.md`
- `docs/design-docs/2026-09-08-iam-catalog-client-and-openapi-convergence-design.md`

## Affected modules and ownership boundaries

- `im-iam-sdk` owns the typed hierarchy and consumes its nested settings.
- Admin, Audit and Monitor own their local values and migrate their YAML atomically.
- IAM Server domain configuration remains independent from the SDK hierarchy.

## Ordered implementation tasks

- [x] Add failing binding and validation tests for enabled/disabled integration, required Web/Application/Session values, absent/complete/partial Catalog credentials, unchanged defaults and rejection of old-only prefixes.
- [x] Replace the two old property records with nested `IamProperties`.
- [x] Update SDK auto-configuration and Catalog synchronization to consume the new hierarchy.
- [x] Migrate Admin, Audit and Monitor YAML and prove each application context/configuration test uses only the canonical hierarchy.
- [x] Update SDK/module documentation and Harness ownership guidance; record the focused SDK and application binding tests as the enforcing sensor.
- [x] Run focused, quick and full verification.

## Implementation findings

Review found that the new root needed the same HTTPS-outside-loopback invariant as IAM Server and that Audit's separate Introspection client bypassed the hierarchy's HTTP timeouts. The implementation now validates the issuer, applies the configured timeouts to Audit ingestion Introspection, protects nested secrets from generated record strings, and form-encodes OAuth client credentials at the touched Basic authentication boundaries. Focused red-green tests cover each finding.

## Test and verification strategy

```bash
mvn -q -pl im-management/im-iam/im-iam-sdk -am test
mvn -q -pl im-management/im-admin,im-management/im-audit/im-audit-server,im-management/im-monitor -am test
./scripts/verify.sh quick
./scripts/verify.sh full
```

## Rollout, compatibility and rollback

The change intentionally has no old-prefix compatibility layer in code. For a safe runtime rollout, first add the new Nacos keys without removing the old keys, then deploy and verify Admin, Audit and Monitor, and only then remove the old keys. During that overlap window rollback requires only restoring previous binaries; after old-key removal, rollback also requires restoring the old Nacos paths.

## Completion criteria

- One `IamProperties` root expresses provider, application, OAuth client and Session ownership.
- No production configuration or source references the removed property classes or old prefixes; the old prefix appears only in a negative activation regression test.
- Admin, Audit and Monitor bind usable local defaults through `im.iam`.
- Focused, quick and full verification pass.
