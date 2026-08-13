# IM Realtime Approved Scenarios

这些场景定义实时链路必须保持的可观察行为，是行为 Harness 的稳定输入。实现可以变化，但修改预期结果前必须经过人工确认。

## 用户连接路由

1. Gateway 为用户上报连接后，负责该用户的 Broker 保存 `userId -> gatewayId`。
2. 同一用户可以同时存在于多个 Gateway，Broker 不保存 Gateway 本地 `connectionId`。
3. Gateway 快照中消失的用户路由会被清理，其他 Gateway 上的同一用户路由不受影响。
4. Broker 成员变化后，不再归属当前 Broker 的路由迁移到新归属节点；远端确认前不得删除本地路由。

## Gossip 收敛

1. Broker 和 Gateway 注册状态通过 digest/delta 最终收敛。
2. 远端 Connection 状态保留在 Gossip 视图，不直接写入当前 Broker 的归属路由表。
3. REMOVED 状态必须传播并在 TTL 内阻止旧状态复活，TTL 到期后允许压缩。

## 实时投递

1. 下行帧先定位用户归属 Broker，再定位用户所在 Gateway。
2. Gateway 仅写入本机活跃连接；单连接失败不应阻断同一用户的其他连接。
3. Broker 或 Gateway 暂时不可用时，消息持久化事实不被回滚；客户端通过历史查询补齐。
4. 需要确认的通知允许重复投递，客户端按消息标识幂等，ACK 后停止重试。

## Executable Evidence

已批准的行为证据使用 JUnit `realtime-behavior` 标签，`./scripts/verify.sh behavior` 按标签发现并执行。新增稳定场景时，在对应测试上标记该标签，无需修改验证脚本。

- `BrokerConnectionServiceTest`: Broker 选择、迁移成功和迁移竞争。
- `BrokerStateStoreTest`: Gossip 合并、删除传播和 removed TTL。
- `InMemoryConnectionRegistryTest`: 多 Gateway 路由与快照清理。
- `FrameProcessHandlerTest`: 上下行帧路由与失败语义。
- `GatewayMetricsTest` and gateway registry tests: 本地连接所有权和活跃视图。
