# Unified Management Console Design

## Context

The IAM, Admin, Audit and Monitor applications are independently deployed management consoles, but their browser experiences currently use different shells, density, colors and interaction patterns. IAM owns custom Vue components while Admin, Audit and Monitor use different subsets of Element Plus. Similar actions therefore behave differently across applications, and new pages have no durable visual standard to follow.

The consoles must look and behave like one management product without becoming one cross-service SPA. Authentication, routes, APIs, permissions and deployment remain owned by each application.

## Decisions

### Visual direction

All four consoles use the approved quiet, high-density operations style:

- a `232px` dark navigation rail and a light neutral work area;
- restrained typography, compact filters and scan-first tables;
- low-saturation green for primary actions, blue for navigation/read actions, amber for warnings and red only for destructive actions;
- no marketing hero, decorative illustration, floating page sections or nested cards;
- stable desktop density with a collapsed mobile navigation, full-width mobile drawers and horizontally scrolling tables.

The baseline tokens are:

| Token | Value | Meaning |
|---|---:|---|
| `--im-console-bg` | `#f3f6fa` | workspace background |
| `--im-console-surface` | `#ffffff` | table, drawer and dialog surface |
| `--im-console-sidebar` | `#172334` | navigation background |
| `--im-console-sidebar-active` | `#2a425d` | active navigation item |
| `--im-console-primary` | `#247662` | primary command |
| `--im-console-link` | `#286493` | read/navigation action |
| `--im-console-warning` | `#a36019` | warning state |
| `--im-console-danger` | `#b33d3d` | destructive state |
| `--im-console-text` | `#1f2b3d` | primary text |
| `--im-console-muted` | `#718096` | secondary text |
| `--im-console-border` | `#dfe5ec` | structural border |

Font size does not scale with viewport width and letter spacing remains `0`. Repeated items and genuine tools may use cards with a radius no greater than `8px`; page sections remain unframed or full-width.

### Component strategy

All four applications standardize on Vue 3, Element Plus and Element Plus Icons. IAM migrates its custom drawer, confirmation, data-state, status and toast components to Element Plus equivalents.

UI source is not shared between deployable applications. Each application owns its shell, routes, components and styles. Consistency is governed by this design, the Management UI coding guide and Harness checks. This preserves the existing rule that Admin and Monitor do not depend on shared UI source or each other's implementation.

Each UI uses the same local structure where applicable:

```text
src/
├── components/
│   ├── ConsoleShell.vue
│   ├── PageHeader.vue
│   ├── DataState.vue
│   └── StatusTag.vue
├── styles/
│   ├── tokens.css
│   └── console.css
├── App.vue
└── views/
```

Small applications may omit a component when there is no second caller, but must preserve the behavior and tokens.

### Navigation

Each console shows only the capabilities it owns. A separate “Other consoles” section links to the other deployments using ordinary full-page navigation. It does not render unavailable remote routes as local menu items.

Console locations are owned by each independently built UI. The local defaults are declared in `src/config/consoleLinks.ts`, and deployments that use different domains override them through Vite build environment variables.

```text
VITE_IAM_CONSOLE_URL=http://localhost:18090
VITE_AUDIT_CONSOLE_URL=http://localhost:18091
VITE_MONITOR_CONSOLE_URL=http://localhost:18092
VITE_ADMIN_CONSOLE_URL=http://localhost:18093
```

The current console is omitted from the remote-console action list. Opening another console performs a normal top-level navigation so that the target application runs its own IAM BFF flow. Navigation is presentation configuration and does not require a backend Controller, response model or Nacos property.

### Interaction contract

Creation, editing and detail inspection use right-side `el-drawer` panels. Short confirmations and sensitive one-field prompts use `el-dialog` or `ElMessageBox`. Destructive operations identify the target and describe the consequence before confirmation.

Dirty drawers intercept close-button, backdrop and Escape attempts. The confirmation uses “Continue editing” and “Discard changes”; a browser-native `confirm` or `alert` is not used.

Tables expose one frequent safe action directly. Secondary actions use an explicit overflow menu. Familiar icon-only commands such as refresh, close and download use Element Plus Icons with accessible labels/tooltips.

Every page distinguishes:

- initial loading;
- successful data;
- empty result with a contextual permitted action;
- failed load with retry;
- stale successful data plus refresh failure;
- row- or drawer-scoped mutation pending;
- mutation success with message and authoritative reload;
- mutation failure that preserves user input.

Filtering runs on the server before paging. A UI must not present current-page-only filtering as an authoritative search. Optional text filters omit blank values.

### Application-specific scope

IAM remains the visual reference and retains its management-account, application, OAuth Client, role, permission and session workflows. Its current proven focus restoration, dirty-close protection and localized pending behavior must survive the Element Plus migration.

Admin uses the shared shell and interaction contract for ordinary-user paging, detail, profile editing, password reset, ban/unban and deletion. It remains an Account Admin Facade consumer and does not regain local persistence.

Audit uses the shared shell for business/security tabs, collapsible filters, paging, detail drawers and export feedback. Read-only result inspection remains primary.

Monitor applies the same shell and density to overview charts, Broker/Gateway/connection tables and diagnostics. It remains read-only; visual consistency does not authorize operational write commands.

## Port migration

The local management ports become:

| Application | Port |
|---|---:|
| IAM | `18090` |
| Audit | `18091` |
| Monitor | `18092` |
| Admin | `18093` |

The migration updates server ports, IAM issuer references, Web OAuth redirect and post-logout URIs, console navigation defaults, tests and current documentation. Existing IAM database rows for Admin, Audit and Monitor Web clients require an explicit data migration. Client IDs, secrets, grants, scopes and ownership do not change.

The migration must be applied while the four management applications are stopped. Rollback restores the prior port map and OAuth redirect values together; partial rollback is unsupported because issuer and registered redirect equality are protocol requirements.

## Harness

The Harness enforces stable structural rules:

- all four package manifests depend on compatible Vue, Element Plus and Element Plus Icons versions;
- every UI provides `lint`, `format:check`, `test:unit`, `typecheck` and `build` scripts;
- every UI declares the required console tokens and contains no `window.confirm` or `window.alert`;
- local port, issuer, redirect and console-location defaults match the approved map;
- Admin and Monitor do not import UI source from each other;
- focused tests prove drawer close protection, destructive confirmation, data states and multi-`RestClient` wiring.

Visual quality, action priority, copy clarity, responsive composition and whether a filter is truly authoritative remain Review-only until a reliable semantic checker exists. Desktop and mobile screenshots are acceptance evidence, not committed generated artifacts.

## Verification

Each migrated application runs its complete UI quality suite and focused server tests. Final acceptance runs desktop/mobile screenshot inspection, `./scripts/verify.sh quick` and `./scripts/verify.sh full`.

Implementation is tracked by `docs/exec-plans/active/2026-09-09-unified-management-console.md`.
