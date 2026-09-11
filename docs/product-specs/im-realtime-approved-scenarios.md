# IM 实时链路已批准场景

本文是实时链路可观察行为的权威规格，回答“系统必须表现成什么样”。实现和测试入口可以变化，但修改以下预期结果前必须经过人工确认。

[关键行为目录](FEATURES.md)负责记录每个场景的验证入口、覆盖状态和证据边界；本文不重复维护测试类名称或某次运行结果。

## 连接与身份

### RT-001：认证连接与身份

- 只有 Account 认证成功的 token 才能建立业务 WebSocket 连接。
- Gateway 使用认证结果中的 `userId` 和 Session version 建立连接上下文。
- 客户端 Query、Header 或 Frame Body 中自行提交的 `userId` 不能覆盖认证身份。
- 连接建立后，负责该用户的 Broker 保存 `userId -> gatewayId`；Broker 不保存 Gateway 本地 `connectionId`。

### RT-002：断开与 Gateway 重启恢复

- WebSocket 断开后，Gateway 清理本地 Channel，Broker 删除对应 Gateway 路由。
- Gateway 重启不会恢复旧 Channel；客户端重新连接后重新建立 Broker 路由。
- 单个 Gateway 重启不删除同一用户在其他 Gateway 上仍然活跃的路由。

### RT-005：多 Gateway 与快照清理

- 同一用户可以同时连接多个 Gateway。
- Gateway 快照中消失的用户路由只从该 Gateway 清理，其他 Gateway 上的同一用户路由不受影响。
- Gateway 快照不拥有或同步远端 `connectionId`。

### RT-006：Broker 归属迁移

- Broker 成员变化后，不再归属当前 Broker 的用户路由迁移到新归属节点。
- 远端确认迁移成功前不得删除本地路由。
- 迁移失败或并发成员变化由后续扫描与 Gossip 收敛，不得直接丢失路由。

## Gossip 收敛

### RT-007：注册状态与删除传播

- Broker 和 Gateway 注册状态通过 digest/delta 最终收敛。
- 远端 Connection 状态保留在 Gossip View，不直接写入当前 Broker 的归属路由表。
- `REMOVED` 状态必须传播，并在 TTL 内阻止旧状态复活；TTL 到期后允许压缩。

## 消息与通知

### RT-003：私聊上行发送

- 私聊发送 Frame 从 WebSocket Gateway 经 Bolt 进入 Broker，再委托 Message Facade 处理业务语义。
- Broker 使用连接认证用户覆盖 Frame Body 的发送人字段。
- 正常响应保留请求的 Command、Sequence 和 TraceId，使客户端可以关联请求结果。

### RT-004：通知 ACK

- 通知 ACK 从 WebSocket Gateway 经 Broker 到达 Message Facade。
- ACK 使用连接认证用户，客户端不能替其他用户确认通知。
- 需要确认的通知允许重复投递；客户端按消息标识幂等，ACK 成功后停止后续重投。

### RT-008：下行投递与连接隔离

- 下行 Frame 先定位用户归属 Broker，再定位用户所在 Gateway。
- Gateway 只写本机活跃连接。
- 单连接写入失败不阻断同一用户的其他连接，并在结果中保留成功与失败连接信息。

### MSG-001：持久化与在线投递边界

- Message MySQL 事实提交后才触发在线通知。
- Broker 或 Gateway 暂时不可用时，持久化消息事实不回滚。
- 客户端可以通过历史查询补齐未在线收到的消息。
- 当前没有覆盖 MySQL、Redis、Nacos 和远端 Dubbo 的全栈 E2E；不得用实时链路 E2E 冒充数据基础设施证据。
