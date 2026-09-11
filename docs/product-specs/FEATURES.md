# 关键行为目录

本目录记录需要长期保持的用户或运维可观察行为，以及对应的稳定验证覆盖。它不记录当前开发任务；实施状态见 `PROGRESS.md` 和 active execution plan。当前 worktree 是否已经运行并通过所列入口，以 `.harness/report.json` 的 freshness 为准。

覆盖状态：

- `uncovered`：尚无稳定自动验证。
- `partial`：已有较低层或部分路径证据，仍缺目标层级覆盖。
- `covered`：存在与证据边界一致的稳定自动验证。
- `blocked`：目标验证受已记录的环境或外部条件阻塞。

| ID | Owner | 可观察行为 | 验证入口 | 覆盖状态 | 证据边界 |
|---|---|---|---|---|---|
| RT-001 | Gateway/Broker | [认证连接与身份](im-realtime-approved-scenarios.md#rt-001认证连接与身份) | `./scripts/verify.sh e2e` | `covered` | 真实 WebSocket、Bolt、Broker HTTP；Account 为受控认证边界 |
| RT-002 | Gateway/Broker | [断开与 Gateway 重启恢复](im-realtime-approved-scenarios.md#rt-002断开与-gateway-重启恢复) | `./scripts/verify.sh e2e` | `covered` | 真实 Gateway/Broker runtime；不覆盖跨 Broker Gossip 恢复 |
| RT-003 | Gateway/Broker/Message | [私聊上行发送](im-realtime-approved-scenarios.md#rt-003私聊上行发送) | `./scripts/verify.sh e2e` | `covered` | Message Facade 为受控边界，不证明 MySQL/Redis 持久化 |
| RT-004 | Gateway/Broker/Message | [通知 ACK](im-realtime-approved-scenarios.md#rt-004通知-ack) | `./scripts/verify.sh e2e` | `covered` | Message Facade 为受控边界，不证明 Redis Receipt 删除 |
| RT-005 | Broker | [多 Gateway 与快照清理](im-realtime-approved-scenarios.md#rt-005多-gateway-与快照清理) | `./scripts/verify.sh behavior` | `covered` | JVM 内 Broker registry 行为 |
| RT-006 | Broker | [Broker 归属迁移](im-realtime-approved-scenarios.md#rt-006broker-归属迁移) | `./scripts/verify.sh behavior` | `covered` | JVM 内迁移、竞争和失败语义 |
| RT-007 | Broker | [注册状态与删除传播](im-realtime-approved-scenarios.md#rt-007注册状态与删除传播) | `./scripts/verify.sh behavior` | `covered` | JVM 内 Gossip 合并与 TTL 行为 |
| RT-008 | Broker/Gateway | [下行投递与连接隔离](im-realtime-approved-scenarios.md#rt-008下行投递与连接隔离) | `./scripts/verify.sh behavior` | `covered` | JVM 内路由与写入结果；不启动真实 Gateway 网络端口 |
| MSG-001 | Message | [持久化与在线投递边界](im-realtime-approved-scenarios.md#msg-001持久化与在线投递边界) | `./scripts/verify.sh full` | `partial` | 应用与 Repository 测试覆盖事务和失败语义；尚无真实 MySQL/Redis 全栈 E2E |
