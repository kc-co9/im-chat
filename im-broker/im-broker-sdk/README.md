# im-broker-sdk

`im-broker-sdk` 定义 Broker 的内部调用契约和默认客户端，不包含 Broker 服务端、Registry 或 Handler 实现。

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

## Peer ReadIndex 查询

Broker 控制面 Peer 查询使用 `FETCH_QUORUM_READ_INDEX`（`broker.broker/fetchQuorumReadIndex`）。
`QuorumReadIndexParams` 不携带业务集群标识，`QuorumReadIndexResult` 返回 nodeId 和
quorumReadIndex：目标节点已取得 quorum ReadIndex 并在本机应用到该位置，不表示最新应用索引。
该调用仍受 `BrokerAdmissionType.BROKER_CONTROL` 准入保护；旧 getLearnerAppliedIndex/appliedIndex 名称不保留别名，
Broker 节点需协调使用匹配版本。

## 地址发现

`BrokerClient` 构造时通过 Spring `DiscoveryClient` 查询 `ServiceName.IM_BROKER`，把发现到的 `host:port` 列表作为首次调用的 bootstrap 地址。没有可用实例时直接启动失败，不使用写死的 seed address。

首次建立调用能力后，`BrokerRefresher` 在应用就绪时及之后每 30 秒调用 `GET_CLUSTER_STATE`，从任意
bootstrap/ACTIVE Broker 获取 Raft 已提交的完整 `BrokerClusterState`。SDK 只原子安装 revision 更高的
完整状态；低 revision 被忽略，同 revision 异内容或算法漂移会 fail closed。刷新失败只记录
警告并保留最近一次有效状态。

因此两套发现能力职责不同：

- Nacos 只负责首次获取 bootstrap Broker 地址。
- Raft committed `BrokerClusterState` 决定运行期 RouteBroker 集合、epoch、算法和端点。
- Broker registry/Gossip 只提供运行观察，不覆盖 committed state。

消费方必须启用 `im.bolt.client.enabled=true`，使 `im-bolt` 自动配置创建 `BoltInvoker`。

## 客户端边界

- `BrokerClient` 封装 Bolt service/operation、超时、状态缓存、RouteBroker 选择和模型转换，调用方不接触 Bolt 实现。
- Gateway 注册、连接增量、完整路由分片/整轮 ACK、帧写入和会话关闭均使用 typed params。
- `ConnectionCloseParams` 携带用户和旧 `sessionVersion`，供 Broker 路由到 Gateway；SDK 不判断会话有效性。
- 用户路由请求只按 committed state 的 `RENDEZVOUS_HASH` 或 `MODULO_HASH` 直达 RouteBroker；
  `BrokerLoadBalance` 只用于 bootstrap 和无用户路由键的控制调用。
- SDK DTO 是跨进程契约，不作为业务服务领域模型使用；Adapter 在边界完成转换。
- Broker/Gateway 本地状态、Redis、Gossip entry 和 Handler 只存在于 server。
- 下行写入结果描述 Gateway 接受与失败的连接集合，不等同于 Message notification ACK。

- 每次用户路由请求携带 `routingEpoch`、稳定 `operationId` 和绝对 deadline。Message 下行使用
  Receipt `receiptId`，Account 关闭连接使用 `userId + sessionVersion`；SDK 内部重试不会更换这些字段。
- `ROUTE_MOVED`、`BROKER_STATE_STALE`、`ROUTE_MISMATCH`、`ROUTING_FENCED` 是接收端确认尚未产生
  路由副作用的拒绝。SDK 刷新完整状态后最多定向重试一次。
- RPC timeout、连接中断或其他无法判断是否执行的异常不会自动换 Broker 重放。
- 同 epoch 的 `ROUTE_MISMATCH` 若与 SDK 重新计算结果仍不一致，会停止重试并报错，避免掩盖算法或
  ACTIVE 集合漂移。

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
- `brokerId` 是跨重启稳定身份；同一身份的 `routeEndpoint` 不可原地替换。换端点应使用新
  `brokerId` 加入并排空旧节点。
- 节点列表按 `brokerId` 规范化排序；输入顺序不同不会产生不同状态或路由结果。
- 至少存在一个 ACTIVE Broker，且 `DISCOVERED`、`LEARNER_SYNCING` 节点只能是 `INACTIVE`。
  `REMOVED` 节点不能继续留在权威状态中。

## RouteBroker 选择

两种算法只使用 ACTIVE Broker，并以稳定 `brokerId` 作为哈希身份：

```text
RENDEZVOUS_HASH:
  input = int64-big-endian(userId) || int32-big-endian(utf8Length(brokerId)) || utf8(brokerId)
  score = firstUnsignedInt64(SHA-256(input))
  result = 最高 score；相同 score 选择 brokerId 字典序较小者

MODULO_HASH:
  hash = firstUnsignedInt64(SHA-256(int64-big-endian(userId)))
  result = sortedActiveBrokers[unsignedRemainder(hash, brokerCount)]
```

`RENDEZVOUS_HASH` 是新集群默认选择；增删节点时只有必要用户迁移。`MODULO_HASH` 作为显式建群
选项保留，但成员数量变化会重映射大量用户。两者均不依赖 Java `String.hashCode()`，协议由
`RouteBrokerSelectorContractTest` 的 golden vectors 固定。

空 ACTIVE 集合、重复或空白身份、空白端点、缺失算法、负 revision/epoch 及非法 membership/routing
组合会抛出 `IllegalArgumentException`。调用方不能在收到这类错误后降级为随机 Broker 或广播业务帧。

## Gateway 路由快照契约

`GatewayRouteSnapshotParams` 表示一个 Gateway 水位中、属于单个 RouteBroker 的完整分片。它携带
`gatewayId`、`routingEpoch`、`routeRevision`、目标 `routeBrokerId`、`batchId`、chunk 序号/总数、
完整分片 checksum、绝对 deadline 和当前 chunk 的用户列表。

接收结果为 `AcceptGatewayRoutesResult`：`CHUNK_ACCEPTED` 表示本片已暂存，`ROUTES_APPLIED`
表示整批路由已应用。Broker 应用入口使用 `AcceptGatewayRoutesCommand`；结果状态按枚举名称
序列化，Broker 与 Gateway 使用同版本契约，不保留旧成功状态别名。

- Gateway 注册/注销和快照共享单调 `routeRevision`；低 revision 不能覆盖高 revision。
- 快照只发送给目标 RouteBroker。当前为空但上一轮非空的分片仍发送一个空 chunk，用于清除旧路由。
- chunk 可乱序或重复到达；Broker 只有收齐整批并验证 checksum 后才原子应用，超时批次不产生部分结果。
- `ROUTE_MOVED` 触发一次完整状态刷新和整批重发；`RETRY_AFTER` 使用有界指数退避；身份失败或批次非法
  直接失败。
- checksum 对排序后的完整 userId 列表计算，不能逐 chunk 独立计算或依赖集合遍历顺序。
- 所有目标分片均应用后，Gateway 使用 `GatewayRouteSnapshotAckParams` 提交目标 epoch 的全局
  `routeRevision + routeCount + contentChecksum`。客户端会有界尝试 committed Broker，直到当前 Leader
  返回 `ACKNOWLEDGED` 或当前 epoch 没有恢复 operation 时返回 `NOT_REQUIRED`；follower 不代写 Raft。
- `DrainRouteCleanupParams` 只用于 Leader 调用 operation 指定的 DRAINING Broker。请求中的 Gateway 水位
  必须与 Raft committed ACK 完全一致；它不是普通连接注销协议。

Gateway 每次进程启动使用新的随机 `gatewayId`，避免复用旧进程的 revision 水位。该 ID 仍须与传输层
workload identity 和已注册端点绑定，不能只因请求体携带相同字符串就获得路由写权限。

```bash
mvn -q -pl im-broker/im-broker-sdk -am test
```
