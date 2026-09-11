# 可靠性规范

## 可靠性模型

- 业务数据库是持久化 Account、Social、Conversation 和 Message 状态的事实源。
- Broker 路由与发现状态通过 Gossip 最终一致；临时分歧不得改变持久消息事实。
- WebSocket Gateway 拥有本机 Session。Gateway 故障后客户端可能需要重连并重建 Broker 路由。
- 需要客户端确认的通知使用持久化待确认任务和延迟重试；Handler 必须保持幂等。
- Account Session 状态是认证和撤销权威。Session 替换和登出只在 Account 事务提交后发布 best-effort WebSocket 关闭控制。
- Nacos 是配置覆盖层。Service 保留本地默认值，并显式报告远端配置缺失。
- Broker SDK Consumer 启动时至少需要发现一个 Broker。周期性 Broker 快照刷新只有在新结果非空时才替换本地地址；刷新失败保留最后可用快照，不停止 Consumer。
- `ImChatView` 是带过期时间的 Message 领域 Redis 状态，只参与未读判断。缺失或过期按“未查看”处理，不影响持久消息事实。
- Broker 诊断历史是有界内存观测，重启后有意丢失，不参与路由、Gossip、迁移或持久消息决策。
- IAM MySQL 是管理身份、浏览器应用、机器 Client、应用级角色、授权和元数据的权威。Audit MySQL 是不可变管理审计事实的唯一权威。普通用户管理仅在 Account Admin Facade 可达时提供，不得回退为直接访问 Account 数据库。

## 运行要求

- 启动失败必须指出缺失依赖或非法配置，不能静默降级关键路径。
- 定时注册、心跳、迁移和 Gossip 任务必须幂等，并隔离单个 Peer 故障。
- Timeout、重试上限、Tombstone/Removed Entry 保留时间和 Gossip fanout 归入类型化配置属性。
- 业务监控必须暴露连接、投递、重试和同步健康状态，但监控数据不得进入投递正确性路径。
- `im-monitor` 使用有界 Executor 和单节点 Timeout 并发查询 Broker management endpoint。部分失败返回 `UNREACHABLE`，健康节点数据仍可用；全部失败时不得伪造健康汇总。
- Session 关闭控制发布暴露有限基数的 `success`/`failure` Counter 与 Duration Timer，不包含用户、Session、Connection 或 Token Label。
- Account 状态变化只在数据库事务提交后安排 Session 踢出。Post-commit Callback 获取 Session 写锁、更新 Redis Session 状态，然后立即发布 best-effort 连接关闭控制；不能为了再注册一个 Post-commit Callback 单独开启数据库事务。
- 待确认通知由 Redis Receipt Task 是否存在表示。客户端 ACK 删除 Task、延迟标识和重试计数；不存在单独持久化的 `CONFIRMED` 状态。
- Message 通知在首次 Push 后最多重投三次。允许重复投递，因此客户端和 ACK Handler 必须幂等。
- Management Producer 提交已完成 Audit 事实，不改变原业务结果或异常。事务成功只在 Commit 后提交；Rollback 不发成功事实，失败时保留并重新抛出原异常。
- Kafka 至少投递一次，Audit 按 `auditId` 幂等写入。Source Consumer 持久化失败最多重试三次，再进入独立 DLQ。HTTP 只重试 Timeout 和 `5xx`，`4xx` 为最终结果。Kafka 与 HTTP 独立选择，不静默互相回退。
- Audit 导出要求不超过 31 天的有界时间范围，以每页 100 行流式写入 Workbook。
- IAM 健康时，Admin 和 Monitor 对每个受保护请求实时执行 IAM Introspection。只有连接失败、Timeout 和 IAM `5xx` 可以使用最后一次 `active=true` 结果，最多保留五分钟且不得超过 Access Token 过期时间；显式拒绝永不回退。
- IAM SSO 使用 8 小时 Idle Limit 和 24 小时 Absolute Limit。应用登出只移除对应 BFF Session；平台或全设备撤销使相关 IAM Authorization 失效。

## 已知可靠性债务

- 当前没有适合重试 Account Session 关闭控制的持久调度或 Outbox。Post-commit 发布失败只记录一次日志和计数，不进入无界内存重试队列。
- 在引入持久且有界的重试机制前，控制投递失败后已建立的旧 WebSocket 可能继续保持连接。已提交的 Session version 仍是权威，因此旧凭证在后续每次 Account 认证时都会失败。
- Message 通知重试由 Redis 支持且有界，但未与 MySQL 消息事务原子提交，也不是 Outbox。删除当前重试任务之后、安排下次延迟之前若进程故障，可能丢失剩余重投；持久 Message 与 Inbox 事实仍可用于恢复。
- Audit Producer 不使用本地 Outbox。业务 Commit 后、异步 Transport 接受前若进程故障，可能丢失事件；已被 Kafka 接受的事件因持久化幂等而可以安全重投。

## 验证

- Unit Test 覆盖状态转换和幂等。
- Integration Test 在禁用或确定性替换外部系统时覆盖 Adapter 和应用启动。
- `./scripts/verify.sh e2e` 启动真实 Broker HTTP/Bolt 和 Netty WebSocket runtime，验证认证路由、上行 Message 与 ACK、断开清理及 Gateway 重启恢复。Account 认证和 Message 持久化是受控边界，因此该结果不证明 MySQL、Redis、Nacos 或远端 Dubbo 可用。
- `./scripts/verify.sh full` 是仓库完成门禁。
- E2E 输出保存在 `.harness/runtime/e2e.log`；verification JSON 将结果绑定到当前 HEAD 和 worktree fingerprint。
- Broker JMX Metrics 暴露 `im.broker.instances`、`im.broker.connections` 和 `im.broker.gossip.entries`。
- WebSocket Gateway JMX Metrics 暴露 `im.gateway.connections` 和 `im.gateway.users`。

故障处理、状态权威、重试策略或运行保证变化时必须同步更新本文。
