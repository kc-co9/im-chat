# IM Management

`im-management` 是独立运维应用的 Maven 聚合模块，不包含共享运行进程。

| 模块 | 职责 | 当前状态 |
|---|---|---|
| `im-iam` | 统一管理端身份、OAuth2/OIDC、应用级 RBAC；聚合 `im-iam-server` 与 `im-iam-sdk` | 已实现基础闭环 |
| `im-monitor` | 发现 Broker 管理端点，聚合只读业务诊断状态并提供监控页面 | 本次实现 |
| `im-admin` | IAM 接入、普通用户管理和管理 UI | 已接入 IAM 与集中审计 |
| `im-audit` | 集中审计契约、接收、追加存储、查询、独立 UI 和 Excel 导出 | 已实现基础闭环 |

`im-iam-server` 是独立部署的统一认证与授权中心，`im-iam-sdk` 是 Admin、Monitor
接入该中心的公共契约和 Spring Security 集成。Admin 与 Monitor 仍分别构建和部署，
不共享业务 Controller、应用服务或 UI；Monitor 保持只读，Admin 的普通用户写操作通过
Account Admin Facade 执行，不直连 Account 数据库。

`im-audit-sdk` 供管理应用发布 BUSINESS/SECURITY 审计事实，`im-audit-server` 是唯一审计
存储与查询应用。生产者只能依赖 SDK，不能依赖 Audit Server 实现或重新声明本地审计表。

四个独立部署的管理应用都直接依赖 `im-nacos`：通过 Nacos 加载动态配置，并注册应用存活实例。
Dubbo Registry 复用同一 `im.nacos.namespace`，避免只依赖 RPC 插件的应用落入空 namespace。
