# IM Audit

`im-audit` 是集中管理审计的 Maven 聚合模块，也是管理端 BUSINESS/SECURITY 审计事实的唯一权威。
Admin、IAM、Monitor 只生产已完成事实，不拥有本地审计表、查询 API 或审计页面。

## 领域位置

Audit 只有一个审计事实上下文：接收可信来源标识与不可变事件载荷，按全局业务标识幂等追加，并向
获授权的审计人员提供有界查询、详情和导出。传输方式和 UI 部署不产生新的审计聚合。

## 统一语言

| 业务术语 | 类型 | 建模名称 |
|---|---|---|
| 审计事实 | 聚合根 | Server `AuditEvent` |
| 审计事实标识 | 值对象 | `AuditId` |
| 可信来源应用 | 值对象 | `SourceApp` |
| 类型、动作、操作者、目标、结果 | 值对象 | `AuditType`、`AuditAction`、`AuditActor`、`AuditTarget`、`AuditOutcome` |
| 失败码、说明、客户端上下文、链路、扩展属性 | 值对象 | `AuditErrorCode`、`AuditDescription`、`AuditClientContext`、`TraceId`、`AuditAttributes` |
| 查询条件 | 值对象 | `AuditQueryCondition` |
| 跨进程不可变审计契约 | SDK 契约 | SDK `AuditEvent`、`AuditSubmission` |

SDK 与 Server 都使用 `AuditEvent` 这个名称：前者是跨进程不可变传输契约，后者是由可信来源和事件
载荷转换得到的领域聚合，二者在模块边界转换，不能直接共享 Server 实现类型。

## 关键不变量

- `AuditId` 由 SDK 一次生成，HTTP 重试和 Kafka 重投沿用同一个值；Server 的 `AuditEventRepository.append` 以它为幂等键，重复提交按成功处理，不创建第二条事实。
- 审计事实描述已经完成的 BUSINESS/SECURITY 行为，只允许追加和读取；已持久化事实不更新、不删除，也不提供业务修改命令。
- 必填身份、类型、动作、操作者、目标、结果、说明、属性和发生时间必须完整；失败结果必须有稳定错误码，成功结果不能携带错误码。
- 密码、Secret、Cookie、Session/CSRF、Token、授权码、签名材料、SQL 和调用栈不得进入审计说明或属性。
- 生产者业务成功只在事务提交后投递；审计收集或投递失败可观测，但不能替换原业务结果或异常。

## 协作与状态所有权

- Audit Server 独占 MySQL Schema `im_chat_audit` 和 `db_audit_event`，并通过唯一 `audit_id` 保存追加事实；Admin、IAM、Monitor 不直接访问该表。
- `sourceApp` 不接受生产者载荷覆盖。HTTP 接收边界从已认证的 IAM 机器身份 `appKey` 注入来源；Kafka 接收边界从来源专属 Binding 注入固定来源。
- 查询、详情和 Excel 导出属于 Audit Server 的只读应用边界。它们按 `AuditQueryCondition` 读取，导出必须有明确时间范围并分批执行；生产者 SDK 不提供查询能力。
- `im-audit-sdk` 拥有 `AuditClient`、`AuditTemplate`、`AuditContextCollector`、`@Audited`、事件工厂和 Kafka/异步 HTTP Transport，负责安全收集上下文、构造不可变契约并隔离投递故障。
- `im-audit-server` 拥有 HTTP/Kafka 接收适配、可信来源合并、`AuditEventRepository`、MySQL 持久化、查询、详情、受限导出和独立管理 UI。

## 子模块与验证

- [`im-audit-sdk`](im-audit-sdk/README.md)：生产者契约、上下文采集、声明式审计和传输边界。
- [`im-audit-server`](im-audit-server/README.md)：可信接收、幂等追加、查询权限、导出、UI 与运行配置。
- 详细设计见 [`2026-08-28-central-management-audit-design.md`](../../docs/design-docs/2026-08-28-central-management-audit-design.md)。

```bash
mvn -q -pl im-management/im-audit/im-audit-sdk,im-management/im-audit/im-audit-server -am test
./scripts/verify.sh architecture
```
