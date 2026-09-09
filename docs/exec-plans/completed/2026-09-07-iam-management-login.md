# IAM Management Login Execution Plan

## Objective and non-goals

Replace the generated Spring Security login page with the IAM Vue login experience and make successful authentication return to the intended validated SPA route.

This plan does not change administrator credentials, OAuth client registration, BFF login in other management applications, or IAM authorization semantics.

## Design references

- `docs/design-docs/2026-09-07-iam-management-login-design.md`
- `docs/design-docs/2026-09-02-iam-security-hardening-design.md`
- `docs/references/CODING_GUIDE.md`

## Affected modules and ownership boundaries

- `im-iam-server` security owns form authentication and redirect validation.
- `im-iam-server/ui` owns login presentation and construction of the standard Spring form request.
- `im-datasource` owns MyBatis JSON TypeHandler configuration required by OAuth Claims persistence.

## Ordered implementation tasks

- [x] Add failing backend tests for login-page forwarding and safe success/failure redirects.
- [x] Add failing frontend tests for login form, CSRF, error state and Hash continuation.
- [x] Implement the server login page mapping and redirect handler.
- [x] Configure Spring Security to use the custom page and handlers.
- [x] Implement and style the Vue login page without starting the authenticated router.
- [x] Update IAM documentation and Harness guidance.
- [x] Bind MyBatis JSON TypeHandler to the application `ObjectMapper` and verify OAuth Token exchange.
- [x] Run focused, quick and full verification.

## Test and verification strategy

```bash
mvn -q -pl im-management/im-iam/im-iam-server -am test
cd im-management/im-iam/im-iam-server/ui
npm run lint && npm run format:check && npm run typecheck && npm run test:unit && npm run build
./scripts/verify.sh quick
./scripts/verify.sh full
```

## Rollout, compatibility and rollback

Existing credentials and Spring Sessions remain compatible. Administrators with stale permission snapshots must authenticate again. Rollback restores generated form login and removes the Vue login entry; no data migration is involved.

## Completion criteria

- `/login` displays the IAM-owned responsive page.
- Standard Spring Security authentication and CSRF remain in use.
- Successful login returns to a validated SPA route.
- Failed login displays a stable error without losing the route.
- Invalid external continuations cannot produce an open redirect.
- Focused, quick and full verification pass.
