# IAM Local Security Defaults Implementation Plan

> **For agentic workers:** Execute locally without subagents. Track every checkbox and do not create Git commits without explicit user authorization.

**Goal:** Let IAM and its management clients start with coherent localhost defaults while retaining explicit production override points.

**Architecture:** IAM keeps RSA/OIDC and loads a development PKCS12 from classpath. HTTP is accepted only for loopback issuers; non-loopback issuers remain HTTPS-only. Admin, Monitor and Audit receive valid localhost client defaults with clearly development-only credentials that Nacos can override.

**Tech Stack:** Spring Boot configuration properties, Spring Authorization Server, RSA/PKCS12, JUnit 5.

---

## Ordered tasks

- [x] Add failing tests for classpath PKCS12 loading and loopback HTTP issuer validation.
- [x] Add the development RSA PKCS12 and IAM Server localhost defaults.
- [x] Replace invalid management-client placeholders with coherent localhost defaults.
- [x] Update IAM security documentation and reusable configuration guidance.
- [x] Run focused IAM/Admin/Monitor/Audit tests, affected, quick, full and `git diff --check`.
- [x] Move this plan to completed after every gate passes.

## Completion criteria

- IAM loads its RSA signing key from the configured classpath PKCS12.
- `http://localhost` and `http://127.0.0.1` issuers are accepted; other HTTP issuers are rejected.
- Management applications bind valid local IAM client settings without Nacos.
- Production configuration can override every local value through Nacos.
