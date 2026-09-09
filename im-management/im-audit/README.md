# IM Audit

`im-audit` 是集中管理审计的 Maven 聚合模块：

- `im-audit-sdk`：不可变事件契约、安全上下文采集、声明式审计和 Kafka/异步 HTTP 投递；
- `im-audit-server`：认证接收、幂等追加、查询、详情、独立管理 UI 和受限 Excel 导出。

生产者依赖 SDK，不依赖 Server 实现。Audit Server 是管理端 BUSINESS/SECURITY 审计的唯一
持久化所有者；Admin、IAM、Monitor 不声明本地审计 Repository、MyBatis 类型或表。

详细设计见
[`2026-08-28-central-management-audit-design.md`](../../docs/design-docs/2026-08-28-central-management-audit-design.md)。
