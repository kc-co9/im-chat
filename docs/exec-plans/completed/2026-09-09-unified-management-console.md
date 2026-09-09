# Unified Management Console Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents are explicitly allowed) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make IAM, Admin, Audit and Monitor use one quiet high-density Element Plus console style and interaction contract while preserving independent application ownership, and migrate local ports to IAM `18090`, Audit `18091`, Monitor `18092`, Admin `18093`.

**Architecture:** Each deployable application keeps local UI source, routes and APIs. Consistency comes from an approved design, identical local design tokens, Element Plus, focused behavior tests and Harness checks. Console switching uses typed local configuration and same-origin read endpoints; OAuth redirect data changes with the port migration.

**Tech Stack:** Vue 3, Element Plus, Element Plus Icons, TypeScript, Vitest, Spring Boot, typed `@ConfigurationProperties`, Maven, shell Harness checks.

---

## Objective and non-goals

Deliver a consistent management experience, friendlier feedback and stable future UI rules across all four consoles. This plan does not merge the applications into one SPA, introduce shared UI source, move API or permission ownership, add Monitor write operations, or redesign backend business domains.

## Design reference

- `docs/design-docs/2026-09-09-unified-management-console-design.md`

## Affected ownership boundaries

- `im-management/im-iam/im-iam-server`: IAM server port, management UI reference implementation and navigation configuration.
- `im-management/im-iam/im-iam-sdk`: issuer consumers and BFF continuation behavior; no server dependency.
- `im-management/im-admin`: Account-owned user administration client and local UI.
- `im-management/im-audit/im-audit-server`: centralized audit query/export and local UI.
- `im-management/im-monitor`: read-only Broker diagnostics and local UI.
- `im-architecture`, `scripts`, `docs/references`: stable dependency, port and UI consistency rules.

## Ordered tasks

### Task 1: Lock the local port and OAuth migration contract

**Files:**
- Modify: `im-management/im-iam/im-iam-server/src/main/resources/application.yml`
- Modify: `im-management/im-admin/src/main/resources/application.yml`
- Modify: `im-management/im-audit/im-audit-server/src/main/resources/application.yml`
- Modify: `im-management/im-monitor/src/main/resources/application.yml`
- Create: `im-management/im-iam/im-iam-server/sql/2026-09-09-management-console-ports.sql`
- Create: `im-management/im-iam/im-iam-server/sql/2026-09-09-management-console-ports-rollback.sql`
- Modify: IAM/Admin/Audit/Monitor configuration tests containing old `18090..18093` values
- Modify: current README and design documents that state runtime defaults

- [x] Add failing configuration tests for the new server ports, IAM issuer `http://localhost:18090`, and each Web Client callback.
- [x] Add a SQL Harness check proving the migration uses bounded conditional updates and does not alter secrets/grants/scopes.
- [x] Change YAML defaults and all current tests to the approved map.
- [x] Add forward and rollback SQL for existing OAuth redirect and post-logout URI JSON values.
- [x] Run the four configuration test classes and `./scripts/check-sql.sh`.

### Task 2: Add runtime console navigation configuration per application

**Files:**
- Create locally in each deployable management application: `infrastructure/config/properties/ManagementConsoleProperties.java`
- Create locally in each application: `interfaces/http/ConsoleNavigationController.java`
- Create locally in each application: `model/io/ConsoleNavigationResponse.java`
- Modify: four `application.yml` files
- Test: application-specific properties and Controller tests

- [x] Write failing property-binding tests for four absolute console URIs and local defaults.
- [x] Write failing Controller tests proving only non-sensitive URI/name data is returned and that the endpoint is available to an authenticated management session.
- [x] Implement the local typed properties and read endpoint without a shared Controller or UI module.
- [x] Reject relative, missing-scheme and non-HTTP(S) console URIs at binding time.
- [x] Run focused properties, Controller and security tests in all four applications.

### Task 3: Establish the Element Plus console baseline and Harness

**Files:**
- Modify: four UI `package.json` and lock files
- Create locally in each UI: `src/styles/tokens.css`, `src/styles/console.css`
- Modify: four `src/main.ts` and `src/App.vue`
- Create: `scripts/check-management-ui.sh`
- Create: `scripts/test-management-ui-harness.sh`
- Modify: `scripts/verify.sh`, `docs/references/CODING_GUIDE.md`, `docs/references/HARNESS_GUIDE.md`

- [x] Add failing Harness fixtures for missing Element Plus, missing quality scripts, token drift and browser-native confirmation APIs.
- [x] Add compatible Element Plus and Icons dependencies to IAM and missing lint/format tooling to Monitor.
- [x] Define the approved tokens and Element Plus CSS variables in every local UI.
- [x] Implement local `ConsoleShell`, navigation groups, remote-console links, responsive rail and account/logout area.
- [x] Wire the new Harness into quick/full verification without checking subjective visual details.
- [x] Run Harness fixture tests and each UI lint/format/typecheck suite.

### Task 4: Migrate IAM to the common Element Plus interaction system

**Files:**
- Modify: `im-management/im-iam/im-iam-server/ui/src/App.vue`
- Modify: `im-management/im-iam/im-iam-server/ui/src/views/*.vue`
- Replace or remove: `ui/src/components/AppDrawer.vue`, `ConfirmDialog.vue`, `DataState.vue`, `StatusBadge.vue`, `ToastRegion.vue`
- Modify: IAM UI tests

- [x] Write failing component tests for Element Plus drawer focus, dirty-close confirmation, authoritative reload and row-scoped pending behavior.
- [x] Replace custom shell, buttons, table, pagination, drawer, dialog, menu, status and toast markup with themed Element Plus components.
- [x] Preserve application-detail tabs, role selections, OAuth Client editing and all current permission-gated actions.
- [x] Verify mobile drawer width, table scrolling and absence of overlapping action controls with screenshots.
- [x] Run IAM UI lint, format, unit tests, typecheck and build.

### Task 5: Migrate Admin user administration

**Files:**
- Modify: `im-management/im-admin/ui/src/App.vue`
- Modify: `im-management/im-admin/ui/src/views/UsersView.vue`
- Add local UI components/styles and navigation API mapping
- Modify: Admin UI tests

- [x] Write failing tests for retained records after refresh failure, one primary row action, overflow actions and drawer input preservation.
- [x] Apply the common shell, compact server-side filters, data states and pagination.
- [x] Move user detail and profile editing to right drawers; use explicit confirmations for reset, ban/unban and delete consequences.
- [x] Preserve Account Admin Facade and server-side permission behavior.
- [x] Run Admin UI quality gates and Admin server tests.

### Task 6: Migrate Audit query and export

**Files:**
- Modify: `im-management/im-audit/im-audit-server/ui/src/App.vue`
- Modify: `ui/src/views/AuditsView.vue`
- Add local UI components/styles and navigation API mapping
- Modify: Audit UI tests

- [x] Write failing tests for collapsible filters, blank filter omission, stale-data refresh failure, detail drawer and export pending/error feedback.
- [x] Apply the common shell and compact business/security tab layout.
- [x] Keep detail read-only in a drawer and make export a clearly labelled command with scoped pending state.
- [x] Verify permission failure and re-login continuation retain the current hash route.
- [x] Run Audit UI quality gates and Audit server tests.

### Task 7: Migrate Monitor diagnostics without adding writes

**Files:**
- Modify: `im-management/im-monitor/ui/src/App.vue`
- Modify: `ui/src/views/*.vue`, `ui/src/components/*.vue`
- Add local UI styles/components and navigation API mapping
- Modify: Monitor UI tests and package tooling

- [x] Write failing tests for shared shell, overview failure isolation, table states, connection query and remote-console navigation.
- [x] Apply the common shell, typography, colors, table density and feedback behavior.
- [x] Keep charts full-width within the work area and diagnostics read-only.
- [x] Verify responsive chart sizing and table overflow on mobile and desktop.
- [x] Run Monitor UI quality gates and Monitor server tests.

### Task 8: Cross-console acceptance and documentation

**Files:**
- Modify: four module README files
- Modify: `docs/references/CODING_GUIDE.md`, `docs/references/HARNESS_GUIDE.md`
- Move this plan to `docs/exec-plans/completed` only after all checks pass

- [x] Start all four applications on the new ports and verify console switching plus IAM login callbacks.
- [x] Capture temporary desktop/mobile screenshots for all primary routes and inspect hierarchy, clipping, overlap and nonblank rendering.
- [x] Confirm no frontend bundle contains secrets or hard-coded production console addresses.
- [x] Run `./scripts/verify.sh quick` and `./scripts/verify.sh full`.
- [x] Update the four module READMEs with the approved port map, runtime navigation and UI interaction contract.
- [x] Record any approved deviations, mark all tasks complete and archive this plan.

## Test and verification strategy

Focused tests use TDD for each behavior. UI tests assert observable loading, empty, error, retry, pending, confirmation and navigation results rather than component internals. Server tests verify typed configuration, security and response contracts. Harness tests cover only stable mechanical rules.

```bash
mvn -q -pl im-management/im-iam/im-iam-server,im-management/im-admin,im-management/im-audit/im-audit-server,im-management/im-monitor -am test

for ui in \
  im-management/im-iam/im-iam-server/ui \
  im-management/im-admin/ui \
  im-management/im-audit/im-audit-server/ui \
  im-management/im-monitor/ui; do
  (cd "$ui" && npm run lint && npm run format:check && npm run test:unit && npm run typecheck && npm run build)
done

./scripts/test-management-ui-harness.sh
./scripts/verify.sh quick
./scripts/verify.sh full
```

## Rollout, compatibility and rollback

- Stop all four management applications before applying the port migration.
- Back up affected IAM OAuth Client rows, apply the forward SQL, then start IAM first followed by Audit, Monitor and Admin.
- Verify discovery/configuration overrides do not retain old ports before browser testing.
- Existing cookies are host-scoped and may remain, but OAuth state created before the switch must be discarded and login restarted.
- Rollback requires stopping all four applications, applying rollback SQL and restoring every old port/issuer/callback together.
- UI and server changes in each JAR roll back together; no mixed-version browser compatibility is promised during this coordinated local-port migration.

## Completion criteria

- Four consoles visibly follow the approved quiet high-density Element Plus style.
- Each application exposes only owned routes and uses configured remote-console links.
- Drawers, confirmations, data states, errors and pending feedback follow one interaction contract.
- IAM, Audit, Monitor and Admin run on `18090`, `18091`, `18092`, `18093` respectively with matching OAuth configuration and migrated database values.
- Monitor remains read-only and Admin remains persistence-free.
- Mechanical style rules are represented in Harness with positive and negative fixtures.
- Focused tests, screenshots and full repository verification pass.

## Final deviation summary

Runtime acceptance exposed three defects outside the initial visual migration surface and they were fixed before completion:

- Audit now enables `@ConfigurationPropertiesScan`, matching the other management applications so its local console navigation properties are registered at startup.
- Redis Snowflake allocation embeds the validated lease duration as a Lua number literal because Redisson's default Codec encodes `ARGV` strings; existing encoded lease fields and owner values remain compatible.
- IAM login ignores an inherited root `#/` fragment, allowing Spring Security to resume a saved OAuth Authorization Request while preserving explicit IAM deep-link continuations.

The Admin user list still requires a running Account service for business data. Its absence does not affect IAM login, BFF sessions, console navigation or the management console acceptance completed here.

### Post-completion refinement

The navigation implementation was simplified after review: each UI now owns `src/config/consoleLinks.ts` with Vite build-time environment overrides. The four backend navigation Controllers, response models, typed properties and YAML blocks were removed. Audit therefore no longer needs the `@ConfigurationPropertiesScan` annotation introduced only for that deleted configuration.

OAuth Client redirect URI JSON is now stored as raw `String` fields in the MyBatis Entity and converted at the domain Transformer seam. `IamPrincipalController` remains the IAM Server's local-session adapter, with its method named `principal()` while `GET /api/iam/me` remains unchanged.
