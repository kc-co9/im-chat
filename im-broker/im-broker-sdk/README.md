# im-broker-sdk

`im-broker-sdk` 定义 Broker 的内部调用契约和默认客户端，不包含 Broker 服务端、Registry 或 Handler 实现。

## 接入顺序

调用方通过 SDK 取得用户的 RouteBroker，不自行轮询 Broker 或广播业务帧。以三 Broker A/B/C 为例：即使 A 是 Leader，只要用户被分配给 B，Gateway 的连接更新和 Message 的下行都直达 B。SDK 缓存的只是已提交集群投影，不是用户在线表；真正的连接仍在 Gateway。

1. 启用 `im.bolt.client.enabled=true`，配置服务发现，确保启动时至少发现一个 Broker bootstrap 地址。
2. 应用就绪后由 `BrokerRefresher` 获取并周期刷新完整 committed state；用户路由调用使用 typed params，交给 `BrokerClient` 选择 RouteBroker。
3. 业务请求提供稳定操作标识和原始期限。SDK 只在明确的副作用前路由拒绝后刷新状态并定向重试一次；timeout 不自动换节点重放，调用方按自己的幂等协议处理不确定结果。
4. 解释下行结果时区分 Gateway 接受写入与客户端 ACK。SDK 不负责 Message 重投、会话有效性裁决或 Broker 成员治理。

基础术语和完整消息路径见 [Broker 入口](../README.md#阅读路线)；服务启动与 TLS 配置见 [Server README](../im-broker-server/README.md)。当前普通 SDK 调用仍使用 `BoltInvoker.invoke`，Broker 健康观察由服务端 Adapter 使用 `invokeAuthenticated`；[连接层统一 invoke 设计](../../docs/design-docs/2026-10-20-bolt-unified-invoke-design.md)尚未接入当前生产调用链。

## 目录

```text
broker/sdk/
├── BrokerClient.java
├── BrokerSdkAutoConfiguration.java
├── cluster/        # 权威 Broker 集群状态契约
├── enums/          # Bolt service/operation、负载均衡和结果状态
├── lifecycle/      # BrokerClusterState 周期刷新
├── loadbalance/    # HASH、ROUND_ROBIN、RANDOM 地址选择
├── routing/        # RouteBroker 纯函数选择器
└── model/
    ├── dto/
    ├── params/
    └── result/
```

## 地址发现

`BrokerClient` 构造时通过 Spring `DiscoveryClient` 查询 `ServiceName.IM_BROKER`，把发现到的 `host:port` 列表作为首次调用的 bootstrap 地址。没有可用实例时直接启动失败，不使用写死的 seed address。

首次建立调用能力后，`BrokerRefresher` 在应用就绪时及之后每 30 秒调用 `GET_CLUSTER_STATE`，从任意 bootstrap/ACTIVE Broker 获取 Raft 已提交的完整 `BrokerClusterState`。SDK 只原子安装 revision 更高的完整状态；低 revision 被忽略，同 revision 异内容或算法漂移会 fail closed。刷新失败只记录警告并保留最近一次有效状态。

因此两套发现能力职责不同：

- Nacos 只负责首次获取 bootstrap Broker 地址。
- Raft committed `BrokerClusterState` 决定运行期 RouteBroker 集合、epoch、算法和端点。
- Broker registry/Gossip 只提供运行观察，不覆盖 committed state。

消费方必须启用 `im.bolt.client.enabled=true`，使 `im-bolt` 自动配置创建 `BoltInvoker`。

## Peer ReadIndex 查询

Broker 控制面 Peer 查询使用 `FETCH_QUORUM_READ_INDEX`（`broker.broker/fetchQuorumReadIndex`）。`QuorumReadIndexParams` 不携带业务集群标识，`QuorumReadIndexResult` 返回 `nodeId` 和 `quorumReadIndex`：目标节点已取得 quorum ReadIndex 并在本机应用到该位置，不表示最新应用索引。该调用受 `BrokerAdmissionType.BROKER_CONTROL` 准入保护，Broker 节点需使用匹配的协议版本。

## 客户端边界

- `BrokerClient` 封装 Bolt service/operation、超时、状态缓存、RouteBroker 选择和模型转换，调用方不接触 Bolt 实现。
- Gateway 注册、连接增量、完整路由分片/整轮 ACK、帧写入和会话关闭均使用 typed params。
- `ConnectionCloseParams` 携带用户和旧 `sessionVersion`，供 Broker 路由到 Gateway；SDK 不判断会话有效性。
- 用户路由请求只按 committed state 的 `RENDEZVOUS_HASH` 或 `MODULO_HASH` 直达 RouteBroker；`BrokerLoadBalance` 只用于 bootstrap 和无用户路由键的控制调用。
- SDK DTO 是跨进程契约，不作为业务服务领域模型使用；Adapter 在边界完成转换。
- Broker/Gateway 本地状态、Redis、Gossip entry 和 Handler 只存在于 server。
- 下行写入结果描述 Gateway 接受与失败的连接集合，不等同于 Message notification ACK。

- 每次用户路由请求携带 `routingEpoch`、稳定 `operationId` 和绝对 deadline。Message 下行使用 Receipt `receiptId`，Account 关闭连接使用 `userId + sessionVersion`；SDK 内部重试不会更换这些字段。
- `ROUTE_MOVED`、`BROKER_STATE_STALE`、`ROUTE_MISMATCH`、`ROUTING_FENCED` 是接收端确认尚未产生路由副作用的拒绝。SDK 刷新完整状态后最多定向重试一次。
- RPC timeout、连接中断或其他无法判断是否执行的异常不会自动换 Broker 重放。
- 同 epoch 的 `ROUTE_MISMATCH` 若与 SDK 重新计算结果仍不一致，会停止重试并报错，避免掩盖算法或 ACTIVE 集合漂移。

## 时间字段使用

数字绝对时刻使用 Unix epoch milliseconds：`RouteRequestContext` 与 `RouteLookupParams` 的请求期限为 `expireAtMs`；`GatewayRouteSnapshotParams` 和 `GatewayRouteSnapshotAckParams` 的更新期限为 `updateExpireAtMs`；`DrainRouteCleanupParams` 的冻结旧事件期限为 `oldEventExpireAtMs`。`AcceptGatewayRoutesResult.retryAfterMs` 是毫秒退避时长，不能作为绝对截止时间。达到截止时刻即过期；明确拒绝后的重试保留原 `expireAtMs` 和 `operationId`，不重新开启窗口。

`BrokerClient` 构造输入 `timeoutMs` 是数字毫秒配置边界，客户端内部保存 `Duration timeout`，仅在 Bolt 调用边界转换为毫秒。已有观察 DTO 的 `Instant xxxAt` 不改为数字，Lease 的 `validForNanos` 仍是单调时长。协议不提供旧字段 alias 或双读，Broker 与直接消费方必须同步部署。

## 权威集群状态契约

`BrokerClusterState` 必须作为一个完整快照原子安装，不能分别更新 epoch、算法或 Broker 列表：

```text
BrokerClusterState
  revision
  epoch
  algorithm
  brokers[]
    brokerId
    routeEndpoint
    membershipRole
    routingState
```

- `revision` 在任意状态内容变化时递增。
- `epoch` 只在 ACTIVE Broker 集合等 RouteBroker 选择输入变化时递增。
- `algorithm` 只能在建群时选择 `RENDEZVOUS_HASH` 或 `MODULO_HASH`，运行中不可修改。
- `brokerId` 是跨重启稳定身份；同一身份的 `routeEndpoint` 不可原地替换。换端点应使用新 `brokerId` 加入并排空旧节点。
- 节点列表按 `brokerId` 规范化排序；输入顺序不同不会产生不同状态或路由结果。
- 至少存在一个 ACTIVE Broker，且 `DISCOVERED`、`LEARNER_SYNCING` 节点只能是 `INACTIVE`。`REMOVED` 节点不能继续留在权威状态中。

## RouteBroker 选择

两种算法只使用 committed ACTIVE Broker，并以稳定 `brokerId` 作为哈希身份。ACTIVE 表示参与路由选择，不等于该 Broker 已安装有效 Lease；目标 epoch 的 ACTIVE Broker 仍须执行 Lease prepare/install，新 epoch 不表示节点新加入。

算法如下：

```text
RENDEZVOUS_HASH:
  input = int64-big-endian(userId) || int32-big-endian(utf8Length(brokerId)) || utf8(brokerId)
  score = firstUnsignedInt64(SHA-256(input))
  result = 最高 score；相同 score 选择 brokerId 字典序较小者

MODULO_HASH:
  hash = firstUnsignedInt64(SHA-256(int64-big-endian(userId)))
  result = sortedActiveBrokers[unsignedRemainder(hash, brokerCount)]
```

`RENDEZVOUS_HASH` 是新集群默认选择；增删节点时只有必要用户迁移。`MODULO_HASH` 作为显式建群选项保留，但成员数量变化会重映射大量用户。两者均不依赖 Java `String.hashCode()`，协议由 `RouteBrokerSelectorContractTest` 的 golden vectors 固定。

空 ACTIVE 集合、重复或空白身份、空白端点、缺失算法、负 revision/epoch 及非法 membership/routing 组合会抛出 `IllegalArgumentException`。调用方不能在收到这类错误后降级为随机 Broker 或广播业务帧。

## Gateway 路由快照契约

`GatewayRouteSnapshotParams` 表示一个 Gateway 水位中、属于单个 RouteBroker 的完整分片。它携带 `gatewayId`、`routingEpoch`、`routeRevision`、目标 `routeBrokerId`、`batchId`、chunk 序号/总数、完整分片 checksum、绝对更新期限 `updateExpireAtMs` 和当前 chunk 的用户列表。

接收结果为 `AcceptGatewayRoutesResult`：`CHUNK_ACCEPTED` 表示本片已暂存，`ROUTES_APPLIED` 表示整批路由已应用。结果状态按枚举名称序列化，Broker 与 Gateway 必须使用同版本契约。接收、原子应用和清理的服务端职责见[本机分片接收与清理](../ARCHITECTURE.md#本机分片接收与清理)。

- Gateway 注册/注销和快照共享单调 `routeRevision`；低 revision 不能覆盖高 revision。
- 快照只发送给目标 RouteBroker。当前为空但上一轮非空的分片仍发送一个空 chunk，用于清除旧路由。
- chunk 可乱序或重复到达；Broker 只有收齐整批并验证 checksum 后才原子应用，超时批次不产生部分结果。
- `ROUTE_MOVED` 触发一次完整状态刷新和整批重发；`RETRY_AFTER` 使用有界指数退避；身份失败或批次非法直接失败。
- checksum 对排序后的完整 userId 列表计算，不能逐 chunk 独立计算或依赖集合遍历顺序。
- 所有目标分片均应用后，Gateway 使用 `GatewayRouteSnapshotAckParams` 提交目标 epoch 的全局 `routeRevision + routeCount + contentChecksum`。客户端会有界尝试 committed Broker，直到当前 Leader 返回 `ACKNOWLEDGED` 或当前 epoch 没有恢复 operation 时返回 `NOT_REQUIRED`；follower 不代写 Raft。
- `DrainRouteCleanupParams` 只用于 Leader 调用 operation 指定的 DRAINING Broker。请求中的 Gateway 水位必须与 Raft committed ACK 完全一致；它不是普通连接注销协议。

Gateway 每次进程启动使用新的随机 `gatewayId`，避免复用旧进程的 revision 水位。该 ID 仍须与传输层 workload identity 和已注册端点绑定，不能只因请求体携带相同字符串就获得路由写权限。

```bash
mvn -q -pl im-broker/im-broker-sdk -am test
```

## Broker 独立健康观察协议

`PROBE_BROKER` 与 `FETCH_BROKER_HEALTH_REPORT` 是 Broker 内部只读观察操作，不经数据面 Lease，不用 ReadIndex 判断进程健康。它们为故障判断提供输入，不直接授权摘流。

| 字段 | 语义 |
|---|---|
| 请求 `clusterId` / `revision` / `epoch` / `nonce` | 本轮请求背景与关联值 |
| 请求 `callerBrokerId` / `receiverBrokerId` | 调用者 / 接收者，不能把接收者误当被观察目标 |
| 报告请求 `brokerIds` | 查询健康状态的完整目标集合 |
| 报告外层 `observerBrokerId` | 实际产生采样的观察者；来源以 TLS 对端身份为准，不把 payload 当凭据 |
| 报告行 `brokerId` | 被观察 Broker；probe 响应也继续使用 `brokerId` |
| 行 `status` | HEALTHY / UNHEALTHY / UNKNOWN |
| 行 `observationAgeMs` / `unreachableDurationMs` | 非负年龄向上取整 / 非负连续失败时长向下取整；非 UNHEALTHY 的失败时长为 0 |

旧 `targetBrokerId`、`targetBrokerIds`、`reporterBrokerId` 不作别名；probe/report 请求和响应不保留 `expireAtMs`，接收方允许晚到只读查询。此变更要求同版本成组部署，RPC operation `fetchBrokerHealthReport` 不变；其他路由、Lease 和 Gateway 协议的期限不随之删除。

响应保留非期限的上下文回显，但客户端不重新比较回显，信任已认证 Broker 按请求生成报告。身份必须来自真实 TLS 握手和命名空间内唯一 Broker URI SAN，不能用 Subject、可信网络回退或多个 Broker URI 替代。报告内容仍检查行数量、重复、目标范围、状态与时长；TLS 认证不保证业务内容正确。

只使用本轮成功取得的报告。调用失败、单次认证预算耗尽、年龄过期或 Context 变化均不沿用旧票；不支持的协议为 UNKNOWN，不是不可达证明。只有可归因的传输失败形成 UNHEALTHY，本机预算、认证、协议和队列错误不能计票。多数按完整当前 voter 集合计算，目标自报排除但分母不变。

不传输跨节点 nanoTime。服务端 Adapter 在实际任务 RPC 前采样本机墙钟，以年龄还原观察时刻；不增加报告的连续失败时长。样本累计和单次调用预算用单调时间，最终 freshness 评估仍接受墙钟跳变的误差风险。应用不设整轮耗时上限，单次预算也不保证同步 DNS 或共享连接等待的硬返回期限。冷却、最终同任期 quorum 与 fencing 的分离见 [Architecture](../ARCHITECTURE.md#自动处置的资格与授权顺序)。
