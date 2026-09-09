# IAM SDK Configuration Hierarchy Design

## Context

The IAM SDK currently exposes browser integration settings under `im.iam.client` and permission catalog credentials under the sibling `im.iam.catalog` prefix. `app-key`, OAuth client credentials, local Session encryption and IAM provider settings are therefore flattened or placed under misleading ownership.

## Decision

The SDK uses one typed `IamProperties` root bound to `im.iam`:

```yaml
im:
  iam:
    enabled: true
    issuer: http://localhost:18092
    application:
      key: imAudit
      clients:
        web:
          client-id: im-audit-client
          client-secret: local-secret
          redirect-uri: http://localhost:18093/iam/callback
          post-logout-redirect-uri: http://localhost:18093/
        catalog:
          client-id: im-audit-catalog
          client-secret: local-catalog-secret
      session:
        encryption-key: local-base64-key
        secure-cookie: false
```

`issuer` identifies the remote IAM provider. `application.key` identifies the consuming management application. `application.clients.web` and `application.clients.catalog` are OAuth clients owned by that application. `application.session` owns the consuming application's local BFF Session settings. HTTP timeouts remain provider-communication settings under `im.iam.http`; Introspection cache settings remain under `im.iam.introspection`.

The root properties record contains nested immutable records. Components that need only Catalog credentials receive the nested Catalog client object rather than the entire root. The old `IamClientProperties`, `IamCatalogClientProperties`, `im.iam.client` and `im.iam.catalog` contracts are removed without compatibility aliases.

## Validation and defaults

The Web client, application key, issuer and Session encryption key remain mandatory when IAM SDK integration is enabled. Because the auto-configuration itself is conditional on `im.iam.enabled=true`, disabled applications do not bind or validate the remaining hierarchy.

The issuer must use HTTPS outside loopback development so Web and Catalog credentials cannot be sent to an accidental clear-text remote endpoint. The shared HTTP timeout group applies both to SDK calls and to Audit's protected ingestion Introspection client. OAuth Client credentials are form-encoded before constructing `client_secret_basic` authentication at the touched Token and Introspection boundaries.

Catalog activation keeps the existing availability behavior:

- without an `IamPermissionCatalog` bean, the Catalog client may be absent and no registrar is created;
- with an `IamPermissionCatalog` and complete Catalog credentials, the registrar is created and synchronizes after startup;
- with an `IamPermissionCatalog` but no Catalog credentials, the application starts, Catalog readiness remains failed and synchronization records a configuration failure;
- half-configured Catalog credentials fail property binding because client ID and Secret must be supplied together.

Every old setting has one canonical destination:

| Old path under `im.iam` | New path under `im.iam` | Default |
|---|---|---|
| `client.enabled` | `enabled` | none |
| `client.issuer` | `issuer` | none |
| `client.app-key` | `application.key` | none |
| `client.client-id` | `application.clients.web.client-id` | none |
| `client.client-secret` | `application.clients.web.client-secret` | none |
| `client.redirect-uri` | `application.clients.web.redirect-uri` | none |
| `client.post-logout-redirect-uri` | `application.clients.web.post-logout-redirect-uri` | none |
| `catalog.client-id` | `application.clients.catalog.client-id` | absent |
| `catalog.client-secret` | `application.clients.catalog.client-secret` | absent |
| `client.encryption-key` | `application.session.encryption-key` | none |
| `client.session-cookie-name` | `application.session.cookie-name` | `IM_IAM_SESSION` |
| `client.secure-cookie` | `application.session.secure-cookie` | `true` |
| `client.same-site` | `application.session.same-site` | `Lax` |
| `client.session-ttl` | `application.session.ttl` | `8h` |
| `client.connect-timeout` | `http.connect-timeout` | `1s` |
| `client.read-timeout` | `http.read-timeout` | `2s` |
| `client.stale-ttl` | `introspection.stale-ttl` | `5m`, maximum `5m` |

## Compatibility

Admin, Audit and Monitor source YAML migrates atomically with the SDK. Runtime rollout uses an overlap sequence: add new Nacos keys while retaining old keys, deploy and verify all consumers on the new SDK, then remove old keys. Rollback can therefore restore the previous binaries while the old keys still exist. Local checked-in values remain usable development defaults and production values continue to be overridden externally.
