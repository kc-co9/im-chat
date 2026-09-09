# Plugin Auto-configuration Convergence Implementation Plan

> **For agentic workers:** REQUIRED: Execute locally without subagents, worktrees or commits because the user explicitly prohibited them for this worktree. Track every checkbox and preserve unrelated changes.

**Goal:** Merge the Web and Session plugin auto-configuration fragments into one documented entry class per plugin without changing conditional behavior.

**Architecture:** `ImWebAutoConfiguration` owns all generic Servlet MVC beans and keeps CORS opt-in through a method-level property condition. `ImSessionAutoConfiguration` owns JWT beans and optional Servlet identity adaptation, with web-only beans guarded by method-level web conditions. Each plugin exposes exactly one class in its Spring Boot auto-configuration imports.

**Tech Stack:** Java 21, Spring Boot 3.5 AutoConfiguration, Spring MVC, JUnit 5, AssertJ.

---

### Task 1: Converge `im-web` auto-configuration

**Files:**
- Modify: `im-plugin/im-web/src/test/java/com/co/kc/imchat/plugin/web/ImWebAutoConfigurationTest.java`
- Modify: `im-plugin/im-web/src/main/java/com/co/kc/imchat/plugin/web/ImWebAutoConfiguration.java`
- Delete: `im-plugin/im-web/src/main/java/com/co/kc/imchat/plugin/web/WebContextAutoConfiguration.java`
- Delete: `im-plugin/im-web/src/main/java/com/co/kc/imchat/plugin/web/WebCorsAutoConfiguration.java`
- Delete: `im-plugin/im-web/src/main/java/com/co/kc/imchat/plugin/web/WebJacksonAutoConfiguration.java`
- Delete: `im-plugin/im-web/src/main/java/com/co/kc/imchat/plugin/web/WebLoggingAutoConfiguration.java`
- Delete: `im-plugin/im-web/src/main/java/com/co/kc/imchat/plugin/web/WebResultAutoConfiguration.java`
- Modify: `im-plugin/im-web/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [x] Change the context-runner test to load only `ImWebAutoConfiguration` and verify RED.
- [x] Move all beans into `ImWebAutoConfiguration`, preserve back-off and opt-in CORS conditions, and add concise Javadoc.
- [x] Delete fragment classes, reduce imports to one entry and verify focused tests GREEN.

### Task 2: Converge `im-session` auto-configuration

**Files:**
- Modify: `im-plugin/im-session/src/test/java/com/co/kc/imchat/plugin/session/ImSessionAutoConfigurationTest.java`
- Modify: `im-plugin/im-session/src/main/java/com/co/kc/imchat/plugin/session/ImSessionAutoConfiguration.java`
- Delete: `im-plugin/im-session/src/main/java/com/co/kc/imchat/plugin/session/SessionWebAutoConfiguration.java`
- Modify: `im-plugin/im-session/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

- [x] Add a Servlet context-runner test that loads only `ImSessionAutoConfiguration` and verify RED.
- [x] Move web properties, interceptor and MVC registration into the main configuration with Servlet conditions and complete Javadoc.
- [x] Delete the fragment class, reduce imports to one entry and verify focused tests GREEN.

### Task 3: Documentation and verification

**Files:**
- Modify: `docs/design-docs/2026-08-27-web-infrastructure-boundary-design.md`
- Modify: `docs/exec-plans/completed/README.md`

- [x] Update the design to describe one auto-configuration entry per plugin and record this as a local organization decision rather than a Harness rule.
- [x] Run `./scripts/verify.sh quick`, `./scripts/verify.sh full` and `git diff --check`.
- [x] Move this plan to `docs/exec-plans/completed` after verification; do not commit.

## Outcome

- `im-web` and `im-session` each expose one Spring Boot auto-configuration import.
- Conditional nested configurations preserve opt-in CORS and Servlet-only Session adaptation without creating inactive configuration-property Beans.
- Focused tests passed after recording RED failures for both single-entry expectations.
- `./scripts/verify.sh quick`, `./scripts/verify.sh full` and `git diff --check` passed on 2026-08-27.
