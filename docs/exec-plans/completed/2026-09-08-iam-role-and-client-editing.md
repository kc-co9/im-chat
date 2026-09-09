# IAM Role And Client Editing Execution Plan

## Objective and non-goals

Complete IAM authorization administration by replacing raw internal-role IDs with authoritative selections, adding application-member role assignment, allowing application-role permission editing and allowing OAuth Client scope/redirect editing with immediate authorization invalidation.

This does not merge IAM internal roles with application roles, change immutable OAuth Client identity/owner/audience/grant family, or expose secrets.

## Design references

- `docs/design-docs/2026-09-08-iam-console-interaction-design.md`
- `docs/design-docs/2026-08-26-management-iam-design.md`

## Ordered tasks

- [x] Add IAM internal-role catalog and current administrator internal-role assignment queries.
- [x] Add application-scoped administrator-role query and replace command; preserve other applications and revoke affected administrator sessions.
- [x] Include application-role permission IDs in the role read model and expose role editing in the UI.
- [x] Add OAuth Client configuration update domain behavior, command/HTTP endpoint and revoke-by-client session operation.
- [x] Update IAM API contracts and Management Accounts/Application Detail drawers with authoritative selections.
- [x] Add focused service, repository, controller, API and component tests.
- [x] Update README/Harness, run UI gates, IAM tests, quick and full verification, then archive this plan.

## Verification

```bash
mvn -q -pl im-management/im-iam/im-iam-server -am test
cd im-management/im-iam/im-iam-server/ui
npm run lint && npm run format:check && npm run test:unit && npm run typecheck && npm run build
cd /Users/kc/Code/private/im-chat
./scripts/verify.sh quick
./scripts/verify.sh full
```

## Compatibility

All new GET and assignment endpoints are additive. The existing role-update endpoint remains compatible while its list response gains permission IDs. Existing OAuth Client registrations remain valid. Updating a Client or role assignment intentionally invalidates active affected sessions and requires a fresh login/token.

## Completion criteria

- No role assignment UI requires raw IDs.
- Internal and application role assignment cannot affect each other's relations.
- Existing application roles can change name and permissions.
- OAuth Client scopes and browser redirect URI sets can be updated without changing immutable identity fields.
- Permission-affecting changes invalidate stale authorizations.
- All verification gates pass.
