# Managed User Boundary Naming Plan

## Objective and non-goals

将普通用户聚合与后台可管理用户模型明确分离，并在 Account、Admin 两个限界上下文内统一使用 `ManagedUser` 业务语言。此次只调整内部命名和依赖，不改变 HTTP、RPC、Facade 契约或用户管理行为。

## Design references

- [Coding Guide](../../references/CODING_GUIDE.md)
- [IM Admin user management design](../../design-docs/2026-08-24-im-admin-user-management-design.md)

## Affected boundaries

- Account：`User` 与 `UserRepository` 继续负责用户聚合，不感知逻辑删除事实。
- Account 管理链路：独立使用 `ManagedUser`、`ManagedUserRepository`、`ManagedUserAppService`。
- Admin：本地管理用例应用服务同步使用 `ManagedUserAppService`，其领域模型仍由 Admin 上下文自行拥有。

## Implementation tasks

- [x] 先修改边界测试期望并确认缺少 `ManagedUser` 的 RED 编译失败。
- [x] 重命名 Account 管理模型、Repository、MySQL 实现、Transformer 和应用服务。
- [x] 重命名 Admin 用户管理应用服务并同步 Controller、配置和测试。
- [x] 清除生产代码中的旧名称和 Snapshot 语义残留，不保留兼容类型。
- [x] 完成模块与仓库验证并归档计划。

## Verification

```bash
mvn -q -pl im-service/im-account -am test
mvn -q -pl im-management/im-admin -am test
./scripts/verify.sh quick
./scripts/verify.sh full
git diff --check
```

验证结果：

- RED：边界测试改为依赖 `ManagedUser` 后，测试编译因该类型尚不存在而失败。
- 聚焦测试通过：Account、Admin 的管理应用服务、Repository、Transformer、RPC 与 Controller 测试通过。
- `mvn -q -pl im-service/im-account -am test` 通过。
- `mvn -q -pl im-management/im-admin -am test` 通过。
- `./scripts/verify.sh quick` 与 `./scripts/verify.sh full` 通过。
- 旧名称扫描、Drift 检查及 `git diff --check` 通过。

## Rollout and compatibility

本次只调整未发布的内部 Java 类型和 Bean 方法，不提供旧名称兼容层。公开 Facade、RPC 和 HTTP 契约保持不变，无运行时迁移或回滚步骤。

## Completion criteria

- `User` 聚合及其 Repository 不包含逻辑删除事实。
- 两个上下文各自拥有 `ManagedUser` 与 `ManagedUserAppService`。
- Account 管理查询通过独立 `ManagedUserRepository` 完成。
- 聚焦测试、模块测试、快速门禁和差异检查通过。
