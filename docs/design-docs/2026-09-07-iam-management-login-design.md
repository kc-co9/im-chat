# IAM Management Login Design

## Context

IAM Server currently uses Spring Security's generated login page. Browser fragments are not sent to the server, so a protected SPA URL can become `/login#/permissions/...`; the generated form cannot preserve that route deliberately and the page does not match the IAM management UI.

## Decision

- The IAM Vue application owns the visual login page.
- `GET /login` forwards to the built `index.html`; the Vue entry renders `LoginView` when the browser pathname is `/login` and does not start the authenticated router.
- The form continues posting standard `username`, `password` and `_csrf` fields to Spring Security's `/login` processing endpoint.
- A dedicated success/failure handler preserves only `/` or `/#/...` continuation paths. Invalid, absolute and protocol-relative values fall back to `/`.
- When no query continuation is present but the browser carries a Hash route, the login page converts that fragment into an explicit continuation value. With neither value, the form posts to plain `/login` so Spring can restore an OAuth SavedRequest.
- A fresh authentication rebuilds the administrator's current IAM internal permission authorities. Existing HTTP Sessions are not mutated in place.

## Security and failure behavior

- CSRF remains enabled through `CookieCsrfTokenRepository`.
- Authentication failures return to `/login?error=true` and preserve only a validated continuation.
- The implementation does not introduce a JSON password endpoint, template engine or second authentication mechanism.

## Verification

- Unit tests cover continuation validation, success and failure redirects.
- Configuration tests cover login-page forwarding without a dedicated Controller.
- Vue tests cover CSRF form fields, error feedback and Hash continuation.
- The repository quick and full verification gates remain required.
