# Web Infrastructure Boundary Implementation Plan

> **Final organization note (2026-08-27):** The initial implementation split Web and Session capabilities across
> multiple auto-configuration classes. The follow-up convergence retained the same conditional behavior but reduced
> each plugin to one public auto-configuration entry. See the current
> [design](../../design-docs/2026-08-27-web-infrastructure-boundary-design.md).

> **For agentic workers:** REQUIRED: Use `superpowers:executing-plans` to implement this plan. The user has explicitly prohibited subagents, worktrees and automatic commits for this task. Track every checkbox and preserve unrelated worktree changes.

**Goal:** Consolidate reusable HTTP request context and `HttpResult` behavior in `im-web`, move Session-to-Servlet adaptation into existing `im-session`, and remove duplicate Admin/Monitor response protocols without moving `im-iam-sdk`.

**Architecture:** `im-web` owns framework-neutral HTTP/MVC mechanisms and must not depend on Session or management modules. `im-session` optionally adapts trusted headers into `UserContext` in Servlet applications. Admin retains audit/permission/lock business semantics while consuming generic request metadata; Admin and Monitor use `HttpResult`, with IAM SDK emitting the same protocol for authentication and authorization failures.

**Tech Stack:** Java 21, Spring Boot 3.5, Spring MVC, Spring Security, Maven, JUnit 5, AssertJ, ArchUnit, shell Harness.

---

### Task 1: Add generic HTTP request context

**Files:**
- Create: `im-plugin/im-web/src/main/java/com/co/kc/imchat/plugin/web/context/HttpRequestContext.java`
- Create: `im-plugin/im-web/src/main/java/com/co/kc/imchat/plugin/web/context/HttpRequestContextHolder.java`
- Create: `im-plugin/im-web/src/main/java/com/co/kc/imchat/plugin/web/context/HttpRequestContextInterceptor.java`
- Test: `im-plugin/im-web/src/test/java/com/co/kc/imchat/plugin/web/context/HttpRequestContextTest.java`
- Modify: `im-plugin/im-web/pom.xml`

- [x] Write tests for request metadata normalization, User-Agent truncation, lifecycle cleanup and TTL propagation.
- [x] Run the focused tests and verify RED because the generic context types do not exist.
- [x] Implement the three context types and register them through Web auto-configuration.
- [x] Run focused `im-web` tests and verify GREEN.

### Task 2: Migrate Admin audit context

**Files:**
- Modify: `im-management/im-admin/src/main/java/com/co/kc/imchat/management/admin/support/audit/AdminAuditAspect.java`
- Modify: `im-management/im-admin/src/main/java/com/co/kc/imchat/management/admin/infrastructure/config/beans/AdminHttpConfig.java`
- Delete: `im-management/im-admin/src/main/java/com/co/kc/imchat/management/admin/support/context/*`
- Delete: `im-management/im-admin/src/main/java/com/co/kc/imchat/management/admin/support/web/AuditRequestContextInterceptor.java`
- Modify tests under matching Admin packages.

- [x] Change Admin tests first to require `HttpRequestContext` and verify RED.
- [x] Migrate the aspect/configuration and remove Admin-owned duplicate context classes.
- [x] Run focused Admin audit tests and verify GREEN.

### Task 3: Unify MVC and Security error responses

**Files:**
- Modify: `im-plugin/im-web/src/main/java/com/co/kc/imchat/plugin/web/advice/ErrorAdvice.java`
- Modify: `im-management/im-iam/im-iam-sdk/src/main/java/com/co/kc/imchat/management/iam/sdk/security/IamSecurityFilter.java`
- Add IAM SDK security response writer/handlers in `sdk/security` as required.
- Delete Admin/Monitor exception handlers and error response records.
- Modify: Admin/Monitor POM and UI HTTP clients.
- Test matching Web, IAM SDK, Admin and Monitor HTTP behavior.

- [x] Add failing tests for RPC, authorization-denied, IAM unauthenticated and Monitor exceptions returning `HttpResult` with stable integer codes.
- [x] Implement common MVC exception mappings and Spring Security `HttpResult` writers.
- [x] Remove `AdminErrorResponse`, `MonitorErrorResponse` and their duplicate handlers.
- [x] Update Admin/Monitor browser clients to unwrap successful `HttpResult.data` and react to business auth/permission codes despite HTTP 200.
- [x] Run focused Java and frontend tests.

### Task 4: Move Session Web adaptation out of `im-web`

**Files:**
- Move: `im-plugin/im-web/.../session/UserContextInterceptor.java` to `im-plugin/im-session/.../web/UserContextInterceptor.java`
- Create: `im-plugin/im-session/.../properties/SessionWebProperties.java`
- Create: `im-plugin/im-session/.../SessionWebAutoConfiguration.java`
- Move and update interceptor tests.
- Modify: `im-plugin/im-web/pom.xml`, `im-plugin/im-session/pom.xml`, auto-configuration imports and application YAML.

- [x] Move tests first and require configurable public paths; verify RED against the existing hard-coded implementation.
- [x] Add optional Servlet auto-configuration and typed public-path properties.
- [x] Configure Account public business endpoints in Account application YAML; leave other applications with no business-path defaults.
- [x] Remove the `im-web -> im-session` dependency and run focused Session/Web/application context tests.

### Task 5: Split Web auto-configuration and codify boundaries

**Files:**
- Refactor classes under `im-plugin/im-web/src/main/java/com/co/kc/imchat/plugin/web`.
- Modify: `im-architecture/src/test/java/com/co/kc/imchat/architecture/ModuleBoundaryTest.java`
- Modify: `docs/references/CODING_GUIDE.md`
- Modify: `docs/references/HARNESS_GUIDE.md`
- Modify Harness scripts/fixtures only for low-noise mechanically enforceable rules.

- [x] Add architecture and Maven dependency tests proving `im-web` cannot depend on Session/IAM/runtime modules; document business Controller path ownership as Review-only because reliable mechanical identification requires route semantics.
- [x] Split result/Jackson/logging/CORS/context configuration without creating new Maven modules.
- [x] Document request-context ownership, response protocol and Review-only business-semantics rules.
- [x] Run architecture, Harness, affected verification and `git diff --check`.

### Task 6: Final verification and plan closure

- [x] Run `./scripts/verify.sh full`.
- [x] Review the complete diff for unrelated changes and secrets.
- [x] Move this plan to `docs/exec-plans/completed` only after all checks pass.
- [x] Do not commit unless the user explicitly requests it.

## Outcome

- `im-web` now owns generic request metadata, `HttpResult`, MVC error mapping, logging, Jackson and opt-in CORS only.
- `im-session` owns optional Servlet Header adaptation with application-configured public paths and no dependency on `im-web`.
- Admin, Monitor and IAM SDK share one MVC/Security response protocol; both management UIs unwrap it consistently.
- ArchUnit and Maven dependency tests enforce the stable dependency direction; route ownership and business-semantic placement remain explicitly Review-only.
- `./scripts/verify.sh full` passed on 2026-08-27. The first sandboxed run could not bind Dubbo ports; the required unrestricted rerun completed successfully.
