# Management Web Public Boundary Execution Plan

## Objective and non-goals

统一 Admin、Audit、Monitor 的 IAM BFF 公共前端路径、受保护路径和 Session Filter 跳过规则，避免静态资源、登录及回调执行无意义的 Session Introspection，并将缺失静态资源表达为 NOT_FOUND 业务结果。

本次不调整 Actuator 暴露策略、CORS、IAM Server 自身的表单登录边界，也不设计新的前端图标。

## Design references

- `docs/design-docs/2026-08-27-web-infrastructure-boundary-design.md`
- `docs/design-docs/2026-09-02-iam-security-hardening-design.md`
- `docs/references/CODING_GUIDE.md`
- `docs/references/HARNESS_GUIDE.md`

## Affected modules and ownership boundaries

- `im-management/im-iam/im-iam-sdk` owns reusable BFF endpoint paths, Session Filter behavior and default browser security chain.
- `im-management/im-admin` and `im-management/im-audit/im-audit-server` own their custom browser `SecurityFilterChain` declarations and reuse the SDK boundary.
- `im-management/im-monitor` continues using the SDK default chain.
- `im-plugin/im-web` owns generic missing-static-resource HTTP semantics without IAM knowledge.

## Ordered implementation tasks

- [x] Add focused tests for public path classification and `IamSecurityFilter` skipping public requests.
- [x] Add focused test for missing static resources returning NOT_FOUND instead of SYS_ERROR.
- [x] Introduce one immutable IAM BFF path policy and apply it to the SDK default chain, Admin and Audit.
- [x] Make `IamSecurityFilter` skip only the approved public paths.
- [x] Map `NoResourceFoundException` to the project NOT_FOUND result without error-level logging.
- [x] Update Web/IAM documentation and Harness ownership.
- [x] Run focused tests, quick verification and full verification.

## Test and verification strategy

```bash
mvn -q -pl im-plugin/im-web,im-management/im-iam/im-iam-sdk -am \
  -Dtest=ErrorAdviceTest,IamBffRequestPolicyTest,IamSecurityFilterTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./scripts/verify.sh quick
./scripts/verify.sh full
```

## Rollout, compatibility and rollback

Existing IAM Session cookies remain compatible. Newly unknown HTTP paths change from public to denied in BFF applications; all current UI requests are covered explicitly. Rollback consists of restoring the prior request matcher lists and removing the Filter skip policy; no data migration is involved.

## Completion criteria

- Public UI shell, assets, favicon, login, callback and error paths do not require a BFF Session or trigger Introspection.
- `/iam/me`, logout endpoints and `/api/**` remain authenticated.
- Audit `/internal/**` remains isolated under machine-token security.
- Missing static resources do not emit a system-error stack trace.
- Focused, quick and full verification pass.
