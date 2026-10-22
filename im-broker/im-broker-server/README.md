# im-broker-server

Broker 运行服务，负责用户到 Gateway 的路由、精确帧投递、权威集群控制面和运行态观察同步，提供 Bolt RPC 与独立管理 HTTP 入口。本模块不保存聊天历史、不持有 WebSocket Channel 或 `connectionId` 集合，也不访问业务服务数据库。

本文用于构建、启动、配置和排障；内部拓扑、数据归属、状态转换及故障一致性以 [Broker Architecture](../ARCHITECTURE.md) 为准。

## 阅读入口

| 想了解 | 文档 |
|---|---|
| Broker 能力、SDK 调用与路由拒绝 | [Broker README](../README.md)、[SDK README](../im-broker-sdk/README.md) |
| 用例入口和状态所有者 | [应用用例与入口](../ARCHITECTURE.md#应用用例与入口)、[DDD 模型与边界](../ARCHITECTURE.md#ddd-模型与边界) |
| RouteBroker、连接快照和 tombstone | [RouteBroker 与连接路由](../ARCHITECTURE.md#routebroker-与连接路由) |
| 成员变更、排空和故障恢复 | [成员变化与路由恢复](../ARCHITECTURE.md#成员变化与路由恢复) |
| Raft、Lease 与 Gossip 的一致性边界 | [Raft 权威状态流程](../ARCHITECTURE.md#raft-权威状态流程)、[Gossip 一致性](../ARCHITECTURE.md#gossip-一致性) |
| 项目环境与本地编排 | [根 README](../../README.md#本地运行)、[本地运行编排](../../deploy/local/README.md) |

## 运行概览

Broker 把「消息业务」与「在线连接位置」分开：Message 保存消息业务事实，Gateway 持有 WebSocket Channel，Broker 只负责把用户请求送到 Message、把下行送到正确 Gateway。三 Broker 示例和基础术语见 [Broker 入口](../README.md#阅读路线)。

| 运行机制 | 解决的问题 | 不能替代的能力 |
|---|---|---|
| Raft | 多数派提交拓扑、epoch、成员和 operation | 不记录每次心跳、连接位置或逐个实时 Lease |
| Gossip | 用 digest/delta 与 tombstone 传播运行观察 | 不决定 RouteBroker，不授权副作用，不产生故障票 |
| Bolt / SDK | 跨进程调用 / 缓存完整 committed state 并选择 RouteBroker | 调用成功不等于客户端 ACK；RPC 超时不证明未执行 |
| 本机 Lease / fence | 在执行副作用时检查许可与隔离水位 | ACTIVE 状态本身不是执行许可 |

高频观察允许逐步收敛，流量归属却必须共同认可；否则不同 Broker 可能同时接管同一用户。健康报告用于提出故障操作，Raft 提交和旧 epoch 隔离才决定接管顺序。应用服务安排外部调用与恢复，领域对象保护规则，Adapter 接入 JRaft、Bolt 和 Gateway；详细状态所有者见 [Architecture](../ARCHITECTURE.md#ddd-模型与边界)。

## 构建与启动

以下命令均在仓库根目录执行。需要 JDK 21、Maven 3.8.4+ 且低于 4.0；使用本地编排还需 Docker 27+、Docker Compose 2.31+。完整环境要求和 UI 构建工具见[根 README](../../README.md#环境)。

当前根 POM 管理私有候选 `com.co.kc.thirdparty.sofa:jraft-core:1.4.1-imchat.1`，仅安装在本机隔离仓库 `/tmp/jraft-artifact-m2`，尚未发布到 Registry。下列通用构建/编排命令以该制品可解析为前提；其他机器不能直接用普通 `mvn` 完成构建，须先按[制品构建材料](../../third-party/jraft-applied-membership/README.md)准备源码构建并隔离安装，或等待获准 Registry 发布。本机候选验证命令需显式添加 `-Dmaven.repo.local=/tmp/jraft-artifact-m2`，这不表示生产发布、双构建或依赖输入锁已完成。

```bash
mvn clean package
./scripts/local_up.sh        # 只启动 infra，适合随后在 IDE 启动 Broker
./scripts/local_up.sh full   # 构建 JAR/镜像并启动全部应用
./scripts/local_down.sh      # 停止，保留持久化数据卷
```

构建产物由本模块 [POM](pom.xml) 的 Spring Boot 插件生成 `*-exec.jar`。上述启动命令来自仓库编排脚本；仅调试 Broker 时，保持 infra 运行，在 IDE 启动 `com.co.kc.imchat.broker.ImBrokerApplication`。

联调按 MySQL/Redis/Nacos → Account/Social/Message → Broker → WS Gateway → HTTP Gateway 顺序启动。Broker 不需要业务表，但上行处理需要 Message Facade 可用。应用导入 Nacos `im-broker.yml`（默认 `INFRA_GROUP`）；默认本地 Nacos 为 `127.0.0.1:8848`，部署地址、凭据及公共配置见 [im-nacos](../../im-plugin/im-nacos/README.md)。

Broker 成员通信端点使用 `im-common` 的 `MembershipEndpoint`。RaftMembership 是插件提供的字符串列表技术快照，BrokerRaftAdapter.getConsensusMembership 将其转换为领域 `ConsensusMembership`，voter/learner 使用不可变 Set，同角色重复合并，跨角色交叠拒绝；BrokerCluster.findConsistentVoters 对照自身角色、端点绑定及成员变更状态，一致时返回 voter BrokerId，否则返回空集合，自动观察失败关闭。报告最新事实、多数和稳定窗口仍由 BrokerFailureObservationService.evaluate 判断。部署配置和外部协议继续使用原字符串格式，不解析或规范化端点，不改变 v2 日志与快照字段。

当前 Runtime 读取本机已应用的 stable membership，支持 Leader、Follower 和 learner；joint、停止/错误、队列拒绝、超时和中断失败关闭，不回退初始配置或缓存。超时/中断保留 cause，中断恢复线程标志。同步成员读取不得在 FSM callback 或其同步 continuation 内执行；它不是 quorum 证明，也不与业务状态读取构成原子快照，详见[成员读取边界](../ARCHITECTURE.md#membership-与-brokerroutingstate)。

默认单节点使用本地稳定身份和独立 Raft 目录。多节点建群前必须统一初始成员配置，分配稳定节点身份、可达端点和独立持久卷；不要直接复制单节点默认值。SOFAJRaft 原生端口不接受业务 token，跨主机部署必须由 sidecar/service mesh 的 mTLS 与 workload identity 保护。

| 入口 | 本地默认值 | 用途 |
|---|---|---|
| Bolt | `18020` | 业务 RPC，也是 Nacos 实例主端口 |
| 管理 HTTP | `127.0.0.1:19020` | 诊断与受控管理，端口来自 `im.broker.management.port` |
| Actuator | `127.0.0.1:19020` | 健康和指标，端口独立来自 `management.server.port` |
| Raft | `127.0.0.1:18120` | JRaft 原生通信，不是业务 RPC |
| OpenAPI | `/api/doc.html`、`/v3/api-docs` | 管理 HTTP 上的 UI 与 API description |

管理 HTTP 与 Actuator 的端口配置互不自动联动，均不能与 Bolt 端口相同。Nacos metadata 中的 `management-port` 来自 `management.server.port`；若修改管理 API 端口，必须同步对齐 `im.broker.management.port` 与 `management.server.port`，避免 Ops 发现错误地址。

Nacos metadata 的 `management-host` 发布管理地址，监听地址与发布地址可以不同。Actuator 暴露 `health,info,prometheus,metrics`，健康详情默认不公开。

## 重要行为与术语

| 术语 | 使用时需要知道 |
|---|---|
| RouteBroker | 由 committed `BrokerClusterState + userId` 选择的 ACTIVE Broker；普通数据面直达，不广播兜底 |
| revision / routingEpoch | revision 表示权威内容版本，epoch 表示 ACTIVE 路由集合代次；建群算法和既有 brokerId 的路由端点不能在线修改 |
| membership / 流量状态 | voter/learner 是共识资格，READY/ACTIVE/DRAINING/INACTIVE 是流量资格，二者不等价 |
| Lease / fence | 本机副作用执行资格及旧 epoch 隔离；ACTIVE 不等于已安装有效 Lease，Lease 失效时拒绝执行，不靠 Gossip 恢复授权 |
| operation | 带幂等身份、阶段与恢复证据的管理操作；接受操作不等于排空或恢复已经完成 |
| Gateway routeRevision | 同一 Gateway 进程的注册、注销和快照共用修订号；完整快照收齐并校验后才应用 |
| Gossip observation | 最终一致运行观察，不是权威拓扑、路由所有权或强一致事务日志 |

上行是客户端经 Broker 到 Message，下行是 Message 经 RouteBroker 到 Gateway。下行与关闭控制支持多 Gateway 和部分失败；关闭控制将旧 `sessionVersion` 透传给 Gateway 筛选本机连接，Broker 不解释会话版本。

`ROUTE_MOVED`、`BROKER_STATE_STALE`、`ROUTE_MISMATCH`、`ROUTING_FENCED` 均为副作用前的明确拒绝。SDK 仅在这些结果后刷新状态，以原业务标识和 deadline 定向重试一次；timeout 或连接中断不自动换节点重放。详细约束见[帧与控制流](../ARCHITECTURE.md#帧与控制流)。

优雅 drain 要求 Gateway 确认、旧路由清理和重建证据完整，超时为 `FAILED`，显式接受残留风险为 `FORCED`；故障恢复不等待不可达旧节点 cleanup，证据缺口为 `DEGRADED`，不能解释为成功。

## 代码导航

路径相对于 `src/main/java/com/co/kc/imchat/broker/`。

| 路径 | 阅读目的 |
|---|---|
| `ImBrokerApplication.java` | 应用入口 |
| `interfaces/handler`、`interfaces/http/management` | Bolt 与管理 HTTP 协议入口 |
| `interfaces/lifecycle`、`interfaces/scheduler` | 注册、心跳、Lease 续签与恢复任务 |
| `application/{cluster,local,frame,gossip,diagnostic}` | 集群、路由、投递、观察和诊断用例 |
| `domain/{cluster,authority,connection}` | 权威拓扑、执行授权和连接路由规则 |
| `adapter/{cluster,gateway,gossip}` | 本机共识、对端调用、Gateway 传输和观察适配；`gossip/BrokerGossipAdapter` 维护运行态观察与合并 |
| `support/health/BrokerHealthObservationTracker` | 按结构化 Context（`revision`、`BrokerClusterTopology`，目标从拓扑派生）裁剪有界采样并维护 freshness；非权威但作为安全决策输入，不作多数/资格决策，不拥有网络或生命周期 |
| `support/gossip/GossipSyncTracker` | 有界、非权威、可选的诊断历史与累计摘要，无业务决策、网络或生命周期 |
| `infrastructure/config/properties`、`infrastructure/config` | 配置键、校验与装配 |
| `infrastructure/domain` | 内存存取与 fence 文件 |
| `support/monitoring` | BrokerMetrics、RoutingAuthorityMetrics；保留 Spring/Micrometer 的只读指标观察，不拥有网络、Runtime、执行器或业务写入 |
| `transformer/application`、`transformer/interfaces` | 应用输出与入口映射，分别保留两个、三个转换器 |
| `transformer/adapter`、`transformer/registry` | BrokerClusterStateTransformer 及 Raft/Drain/Fence/Lease 转换器；RegistryTransformer |
| `model`、`transformer`、`support` | 输入输出、边界转换及技术支持；详细依赖规则见 Architecture |

## 配置

以下默认值以 [application.yml](src/main/resources/application.yml) 和 [typed properties](src/main/java/com/co/kc/imchat/broker/infrastructure/config/properties) 为准，Nacos 可覆盖部署值。表格首列为完整前缀，第二列为其下配置键；毫秒整数键单位由 `-ms` 后缀明确，其他时长使用 Spring Duration 格式。

### 地址、观察与调用预算

| 前缀 | 键 | 本地默认值 | 用途/约束 |
|---|---|---|---|
| `im.broker.instance` | `host` / `port` | `127.0.0.1` / `${im.bolt.server.port}` | 运行观察地址，观察 ID 从地址生成；不同于 Raft 稳定 node-id |
| `im.bolt.server` | `port` | `18020` | 业务监听端口 |
| `im.broker.management` | `bind-host` / `host` / `port` | `127.0.0.1` / `127.0.0.1` / `19020` | 监听地址/发布地址/端口；端口与 Bolt 分离 |
| `management.server` | `port` | `19020` | Actuator 端口与 Nacos 发布端口；需与 `im.broker.management.port` 对齐 |
| `im.broker.management` | `history-capacity` | `100` | 最近诊断记录容量，`1..1000`，重启清空 |
| `im.broker.management` | `write-audience` / `write-token` | `im-broker-management` / 本地开发 token | token 由 `IM_BROKER_MANAGEMENT_WRITE_TOKEN` 覆盖，生产不得用默认值 |
| `im.broker.cluster` | `seed-addresses` / `gossip-fanout` | `[]` / `2` | Gossip 引导地址/每轮 peer 数，有效 fanout 至少 1 |
| `im.broker.cluster` | `gossip-timeout-ms` / `gossip-removed-ttl-ms` | `3000` / `300000` | 同步超时/删除观察保留期；TTL 应覆盖允许离线后重入的窗口 |
| `im.broker.registry` | `gateway-ttl` | `60s` | Gateway 活跃观察窗口；Broker/连接无独立活性 TTL |
| `im.broker.gateway-push.bolt` | `timeout-ms` | `3000` | 单次 Gateway 调用超时 |
| `im.broker.peer-call` | `timeout-ms` | `3000` | 控制面 peer 调用超时 |
| `im.broker.peer-call` | `max-concurrent-requests` / `max-pending-requests` | `8` / `128` | 正数，启动装配生效；队列拒绝不在调用线程执行，fence 按未确认等待 quarantine |

### Raft 身份与持久化

| 前缀 | 键 | 本地默认值 | 用途/约束 |
|---|---|---|---|
| `im.broker.raft` | `cluster-id` / `group-id` / `node-id` | `local-broker-cluster` / `broker-cluster-state` / `broker-local-1` | 稳定部署身份，已有目录不可改绑 |
| `im.broker.raft` | `endpoint` / `data-directory` | `127.0.0.1:18120` / `./data/broker-raft` | 原生 Raft 地址/独立持久目录；生产不能用临时 Pod IP 作为身份 |
| `im.broker.raft` | `initial-voters` / `initial-learners` | `[127.0.0.1:18120]` / `[]` | 初始成员集合，本机端点必须在其中；建群后以 committed 配置为准 |
| `im.broker.raft` | `bootstrap-brokers` | 本机 `VOTER/ACTIVE` | 每项包含 `broker-id`、`route-endpoint`、`membership-endpoint`、`membership-role`、`routing-state`；所有初始 voter 完全一致，与成员端点绑定一致 |
| `im.broker.raft` | `election-timeout-ms` / `snapshot-interval` / `command-timeout-ms` | `1000` / `300s` / `5000` | 选举/周期快照/提交等待；snapshot-interval 为 Duration，非负整秒，0s 关闭，其余超时为正数 |
| `im.broker.raft` | `minimum-voter-count` / `operation-history-capacity` | `1` / `1000` | voter 最小数为正奇数，三节点环境通常为 3；历史只淘汰终态，不驱逐进行中证据 |

上述 Broker 自有配置不提供旧键 alias。`snapshot-interval` 绑定为 Duration，仅在 Raft Adapter 调用第三方时转换为秒整数；其他已有 Duration 路由配置仍使用各自业务名称和单位。

本地身份与地址可分别通过 `IM_BROKER_CLUSTER_ID`、`IM_BROKER_NODE_ID`、`IM_BROKER_RAFT_ENDPOINT`、`IM_BROKER_RAFT_DATA_DIRECTORY`、`IM_BROKER_ROUTE_ENDPOINT`、`IM_BROKER_HOST` 覆盖；初始成员列表仍须在所有节点统一配置。

### 路由、Lease、快照与恢复

旧 epoch 的隔离范围是冻结的 committed ACTIVE Broker 集合。fence 提交关闭旧 epoch 的新 Lease grant，不会自动撤销远端 Broker 已安装的 Lease；仍需 fence ACK 或完整 quarantine。

目标 epoch 的 ACTIVE Broker 执行 Lease prepare/install。新 epoch 不表示节点新加入，ACTIVE 也不表示已安装有效 Lease。

下表所有键的前缀为 `im.broker.routing`。

| 键 | 默认值 | 用途/约束 |
|---|---|---|
| `algorithm` | `RENDEZVOUS_HASH` | 另可选 `MODULO_HASH`，建群后不可变 |
| `max-lease-duration` / `renewal-interval` | `4s` / `1s` | 最大 Lease/续签周期；续签须短于保守 grant 时长 |
| `max-clock-drift-allowance` / `lease-safety-margin` | `100ms` / `200ms` | grant 扣减的漂移与安全余量 |
| `max-scheduling-pause` / `network-safety-margin` | `2s` / `500ms` | quarantine 的额外暂停与网络余量 |
| `lease-pending-capacity` | `16` | 待安装申请容量；缓存满可淘汰旧申请，不作为授权依据 |
| `max-pending-snapshot-batches` / `max-pending-snapshot-bytes` | `1024` / `67108864`（64 MiB） | 未完成批次数量/总字节上限，超限返回 `RETRY_AFTER` |
| `snapshot-batch-timeout` | `10s` | 超时丢弃整批，不应用部分路由 |
| `route-tombstone-grace-period` | `2m` | 注销标记被快照覆盖后的保留期，阻止迟到注册复活 |
| `max-route-event-ttl` | `10s` | 切换时冻结的旧事件存活窗口 |
| `drain-operation-timeout` / `drain-reconcile-interval` / `drain-cleanup-rpc-timeout` | `2m` / `1s` / `3s` | 排空/恢复总超时、Leader 轮询、旧节点清理 RPC 超时 |
| `drain-cleanup-result-capacity` | `10000` | 本机清理重试检查点容量，不是持久化恢复证据 |
| `route-lookup-timeout` / `route-lookup-negative-cache` | `300ms` / `50ms` | 每次 peer IO 超时/短负缓存，零负缓存时长不写入 |
| `route-lookup-negative-cache-capacity` | `1024` | 未命中用户缓存容量；查询当前其他 ACTIVE/DRAINING 节点，不固定截断候选数 |

保守 grant 时长为 `maxLeaseDuration - maxClockDriftAllowance - leaseSafetyMargin`，默认 `3.7s`；必须为正，`renewalInterval` 必须更短。完整 quarantine 为 `maxLeaseDuration + maxClockDriftAllowance + maxSchedulingPause + networkSafetyMargin`，默认 `6.6s`，缺 ACK 不能因 RPC 超时而缩短。

容量和执行超时必须为正数；漂移、余量、tombstone grace 与负缓存时长允许为零但不能为负。派生 grant/quarantine 不提供另一套覆盖键，`hopCount=1` 为协议约束。内部机制见[Lease 签发与恢复](../ARCHITECTURE.md#lease-签发与恢复)、[查询、投递与本机缓存](../ARCHITECTURE.md#查询投递与本机缓存)。

### 自动故障观察配置

健康观察分成「各 voter 主动采样」与「Leader 收集报告并尝试处置」。这不是 Gossip，也不是每次 probe 失败就改拓扑。前缀为 `im.broker.failure-observation`，由 `BrokerFailureObservationProperties` 绑定，Nacos 可覆盖；没有关闭必需 Bean 的业务开关。

| 键 | 默认值 | 约束 |
|---|---|---|
| `probe-interval` / `report-interval` | `1s` / `1s` | 正数，短于 freshness |
| `rpc-timeout` | `500ms` | 单次认证调用的单调结果接受预算，不超过 freshness；不是阻塞连接获取的硬返回期限 |
| `stable-window` / `freshness` / `cooldown` | `30s` / `10s` / `60s` | 正数；启动、换届、拓扑或实际 membership 变化及一致性恢复后重新冷却 |
| `max-concurrent-requests` / `max-pending-requests` | `4` / `64` | 观察专用有界线程/队列；拒绝不在调用线程执行 |

#### 从采样到新故障操作

1. `BrokerFailureProbeScheduler` 在当前 voter 上探测其他 committed ACTIVE 目标。`BrokerPeerAdapter.probe` 通过 `invokeAuthenticated` 验证真实 TLS 对端与唯一 Broker URI SAN；Subject、首个 URI 或可信网络回退不能替代此身份。
2. `BrokerHealthObservationTracker` 保存本机最新样本、单调采样时间和连续失败起点。只有 `TRANSPORT_UNAVAILABLE` 形成 UNHEALTHY；本机预算耗尽、TLS/身份/协议错误和队列拒绝为 UNKNOWN。HEALTHY、UNKNOWN 或采样断档均打断旧失败窗口。
3. Leader 的 `reconcileFailures` 先恢复既有未终态 `FAIL_BROKER`；没有待恢复操作时，读取本机 `getLeaderTerm()` 的 Optional 任期并检查 revision、实际 membership 与 Broker 绑定。任期为空、非 Leader 或成员不一致时作废冷却并跳过新建。
4. `BrokerObservationState.advance(topology, leaderTerm, observedAt)` 是冷却的唯一资格入口。冷却满足且没有阻塞操作后，应用读取本机快照，并经 `fetchHealthReport` 并发拉取其他 voter 本轮报告；失败按空报告处理，不沿用旧票。
5. 报告汇合后才调用 `confirmLeadership`，取得与起始标记相同任期的 quorum 证明，再核对角色和完整 Context。领域服务先选每个 reporter 的最新事实，同一时刻冲突不计票；只统计新鲜且覆盖稳定窗口的 UNHEALTHY，每个独立 voter 最多一票。
6. 多数分母始终是完整当前 voter 集合，目标自报不计票；三 voter 需要另外两个独立报告。证据合格只允许尝试 Begin；聚合和共识接受后，故障推进另行取得 fencing quorum，再隔离旧 epoch、提交新拓扑和授权。最终 proof 与 Begin 之间仍有并发窗口，不宣称提交原子绑定该任期。

采样 Context 当前为 `revision + BrokerClusterTopology`，ACTIVE 目标从拓扑取得；改变 Context 会清空旧样本。冷却只比较拓扑、任期及一致性背景，不因每个 revision 变化都重置。两者都只存在于本机，重启重新累计，不进入 Raft 快照。结构比较与异步两次检查见 [Architecture](../ARCHITECTURE.md#异步健康观察的两次-context-检查)。

#### 报告字段与时间边界

请求用 `callerBrokerId`、`receiverBrokerId` 区分调用者与接收者，报告查询的 `brokerIds` 是被观察集合；报告外层 `observerBrokerId` 表示实际观察者，行内 `brokerId` 表示被观察者，probe 响应仍用 `brokerId`。旧 target/reporter 字段不作别名，需同版本成组部署；RPC operation `fetchBrokerHealthReport` 和 Raft v2 格式不变。

probe/report 不携带绝对请求期限，接收方允许晚到的只读查询。此处删除期限不影响 Lease、Gateway 快照、RouteLookup 或其他有副作用协议。当前普通调用仍走 `invoke`，健康观察走 `invokeAuthenticated`；[连接层统一 invoke 设计](../../docs/design-docs/2026-10-20-bolt-unified-invoke-design.md)属于后续方案，尚未接入当前生产调用链。

`getHealthReport` 提供本机快照，`fetchHealthReport` 拉取远端报告。Adapter 在执行器任务内构造请求及 nonce，RPC 前取一次 `requestAt = Instant.now()`，用报告年龄还原全部行的观察时刻；实际观察者取自已认证目标，而非信任 payload 身份。报告行的数量、重复、范围、状态和时长仍校验，但客户端不再比较响应上下文回显，也不做返回后的绝对期限检查。这依赖已认证 Broker 按请求生成报告，不表示 TLS 能验证业务内容，亦不再用回显发现服务端错上下文缺陷。

`observationAgeMs` 向上取整，`unreachableDurationMs` 向下取整，不跨节点比较 nanoTime，也不把传输耗时加到连续失败时长。本机快照在读取处采样墙钟与单调时间；汇合及最终重验后统一取 `evaluatedAt = Instant.now()` 判断所有目标。应用没有整轮耗时上限，较慢轮次只要报告仍新鲜且背景有效就可继续；单次认证调用预算仍保留。

正常墙钟下，RPC 后的等待计入报告年龄。当前接受独立墙钟采样造成年龄高估或低估、回拨延长 freshness、前跳提前失效的风险，不宣称消除了轮内墙钟跳变。样本连续时长、冷却和单次 RPC 预算仍使用单调时间。

#### 观察暂停与排查

默认明文开发环境不会形成独立故障票。生产应为每个稳定 brokerId 签发独立 URI SAN 并开启双向 TLS，不能用可信网络回退替代。自动操作未发起时，依次检查 TLS 身份、当前 voter 绑定、freshness、冷却、观察队列和未终态 operation。

同步 DNS 或共享连接等待可能长期占用观察线程；单次预算限制结果接受性，不保证方法按固定墙钟期限返回。专用执行器不占 fence/Lease 队列；在途调用实际结束前不释放闸门，不因超时无限重发。自动处置因此可能安全暂停，但不会降低多数或新鲜度要求。

应用使用两把 `MutexLock`，分别保护观察轮次与故障推进/恢复；两个恢复入口共享后者，`tryLock()` 不等待，锁在同步异常或异步实际完成后释放，允许跨线程完成。同一目标探测复用 `ConcurrentMap<BrokerId, MutexLock>` 中的锁；不同目标可并行，完成链在分类、Context 重验和记录后释放捕获的锁实例。Context 切换和调用结束均不删除锁条目，避免新旧锁并存；历史 BrokerId 保留到应用销毁，存储随历史目标数增长，高频身份更替前需另行设计安全回收。既有故障操作恢复不重新要求冷却或新报告，换 Leader 后按 committed 阶段继续。

### 受信身份与 TLS

下表所有键的前缀为 `im.broker.routing`。

| 键 | 本地默认值 | 部署要求 |
|---|---|---|
| `gateway-workload-identity-prefix` | `spiffe://im-chat/gateway/` | 与 Gateway 证书身份匹配 |
| `broker-workload-identity-prefix` | `spiffe://im-chat/broker/` | 与 peer 证书身份匹配 |
| `message-workload-identity-prefix` | `spiffe://im-chat/message/` | 下行调用身份 |
| `account-workload-identity-prefix` | `spiffe://im-chat/account/` | 关闭控制调用身份 |
| `ops-workload-identity-prefix` | `spiffe://im-chat/im-ops/` | Ops 服务身份 |
| `allow-trusted-network-gateway-identity` | 环境变量默认 `true` | `IM_BROKER_ALLOW_TRUSTED_NETWORK_GATEWAY_IDENTITY`，生产设 `false` |
| `allow-trusted-network-broker-identity` | 环境变量默认 `true` | `IM_BROKER_ALLOW_TRUSTED_NETWORK_BROKER_IDENTITY`，生产设 `false` |
| `allow-trusted-network-service-identity` | 环境变量默认 `true` | `IM_BROKER_ALLOW_TRUSTED_NETWORK_SERVICE_IDENTITY`，生产设 `false` |

三个可信网络回退的 typed properties 默认均为 `false`，本地 `application.yml` 才通过环境变量缺省启用。它们只用于受控明文开发网络，生产必须关闭并启用 Bolt mTLS。

Gateway 路由写入验证 TLS session 身份（优先 URI SAN，其次 Subject）、`im-broker` audience、`broker:route:write` scope、Gateway 租约和注册地址，不能只信任请求体 `gatewayId`。明文连接不能伪造 TLS 身份。

生产环境的 Bolt TLS 配置：

- Broker 服务端配置 `im.bolt.server.tls`，Gateway 客户端配置 `im.bolt.client.tls`；两端启用 `enabled`，服务端启用 `need-client-auth`。
- 设置 `key-store`、`key-store-password` 和 `key-store-type: PKCS12`，密码从部署 secret 注入。
- SOFA Bolt 的同一个 keystore 必须包含本机私钥证书链与验证对端的 CA。
- Broker 下行作为客户端、Gateway 作为服务端时，也要启用对应双向 TLS。

配置键见 [ImBoltProperties](../../im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/properties/ImBoltProperties.java)，RPC 使用方式见 [im-bolt README](../../im-plugin/im-bolt/README.md)。

## 管理接口与访问控制

所有路径均位于管理 HTTP 端口，普通成功和失败由 `im-web` 包装为 `HttpResult`。

| 接口 | 内容 |
|---|---|
| `GET /management/broker/overview` | 当前节点、注册统计、Gossip/迁移摘要 |
| `GET /management/brokers`、`GET /management/gateways` | 本机已知运行观察 |
| `GET /management/connections?userId=...` | 单用户 Gateway 路由，不允许空条件获取全量用户 |
| `GET /management/gossip`、`GET /management/gossip/records?limit=...` | 累计摘要/最近记录，`limit` 为 `1..100` |
| `GET /management/cluster-state` | 已提交 revision、epoch、算法和 Broker 状态 |
| `GET /management/cluster-state/operations/{operationId}` | 操作类型、目标、阶段、纪元、时间、原因和恢复数量；不暴露领域实体、摘要或成员端点 |

以下写接口均以 `/management/cluster-state/brokers/{brokerId}` 为前缀，方法为 `POST`。

| 后缀 | 用途 |
|---|---|
| `/activate`、`/deactivate` | 启用/停止 RouteBroker 分流 |
| `/drain` | 排空流量 |
| `/add-learner`、`/complete-learner-sync` | 加入 learner/确认已追平，不由确认接口发起日志同步 |
| `/promote`、`/demote`、`/remove` | 晋升 voter/降为 learner/移除非活跃成员 |
| `/force-deactivate` | 提供风险确认原因，发起故障摘流与路由恢复；仍执行旧 epoch 隔离和新 Lease 授权，不等待故障 Broker cleanup，不移除 Raft 成员；不用于强制结束失败排空 |

写请求必须提供以下 Header：

| Header | 要求 |
|---|---|
| `Authorization` | `Bearer <write-token>` |
| `X-Im-Audience` | 匹配配置的 `write-audience` |
| `X-Im-Scopes` | 包含接口所需 scope |
| `Idempotency-Key` | 同一操作重试使用同一幂等身份 |
| `If-Match-Epoch` | 请求所依据的路由 epoch |

路由写入需 `ops:broker:cluster-state:write`，成员写入需 `ops:broker:raft:write`，强制摘流需 `ops:broker:force`。请求体见 OpenAPI；每个 URL 是明确用例，没有动态 action 或 dry-run 模式。

非 Leader 返回 `NOT_LEADER` 与 `leaderEndpoint`，调用方据此重试。重试保留原幂等身份与请求语义，不创建另一项操作绕过恢复。

`/management/**`、Actuator 和 OpenAPI 不建立普通用户 `UserContext`。只读访问依赖监听地址、网络策略和调用方基础设施；写入口当前校验配置的服务 Bearer token、audience 和 scope，并非内置 OAuth2 token 验证服务。生产必须覆盖开发 token，并通过受控网络、mTLS 或 OAuth2 Client Credentials 受信接入边界进一步保护。

Broker 只信任 Ops 服务身份，实际管理员授权与审计由 Ops/IAM/Audit 负责，不把浏览器 Header 或网络位置当作管理员身份。

管理 HTTP 的绝对时间使用 `Long xxxAtMs`，包括 `createdAtMs/completedAtMs`、`startedAtMs`、`registeredAtMs/lastSeenAtMs` 和诊断执行时间；耗时及运行时长使用 `durationMs/uptimeMs`。`im-web` 将 Long 序列化为十进制字符串，可空时间保持 null，消费者不能按 ISO 时间字符串或秒数读取。连接诊断的 `registeredAtMs/lastSeenAtMs` 分别在边界由内部 `connectedAt/refreshedAt` 转换；诊断历史为有界内存，重启清空，记录失败不改变业务结果。

## 数据目录与灾难恢复

`im.broker.raft.data-directory` 保存 term、vote、log、snapshot、identity 和 `routing-fence.json`。每个生产 Broker 使用独立 PVC/磁盘，不可共享；正常重启复用相同 `cluster-id + node-id + endpoint + data-directory`。identity 不一致时启动失败，不得删除 marker 或挂载另一节点目录绕过校验。

日志命令和快照均使用 v2，只接受版本 2，明确拒绝 v1 及其他不支持的格式版本，不支持字段别名、旧格式兼容或混合格式重放。不兼容目录须先停止节点并保全原目录，再显式换用新目录；程序不会自动迁移或删除数据，任何数据删除须另行确认。有业务状态的目录必须保全恢复与审计证据，不能直接删除。回调失败与恢复边界见[Raft 回调与持久格式](../ARCHITECTURE.md#raft-回调与持久格式)。

备份优先采用存储卷一致性快照，先触发/确认 Raft snapshot；不要在线逐文件复制正在写入的 RocksDB、log 和 meta。恢复演练须验证 snapshot 加后续 log 重建相同 revision、epoch 与 operation。

少数节点丢失时从原节点独立持久卷恢复，或按 learner 重新加入，多数派仍是唯一提交来源。多数派丢失时控制面拒绝新提交，现有上行业务进程不因此退出；不得自动 `resetPeers`、删除目录或用单个幸存副本覆盖集群。

先恢复多数派。只有确认原多数派不可恢复后，才可在保全旧目录与审计证据的前提下人工灾难重建，使用新的 `cluster-id` 并重新验证业务路由状态。

## 故障排查

| 现象 | 优先检查 | 行为边界 |
|---|---|---|
| 启动拒绝/identity 不一致 | 稳定身份、Raft 端点、目录挂载、初始成员绑定 | 不删除 marker 绕过校验 |
| `NOT_LEADER` / `UNAVAILABLE` | Leader hint、多数派连通性、Raft 日志和 operation 阶段 | 超时不当作 accepted，不猜测回滚 |
| `ROUTING_FENCED` | committed epoch、Lease 续签、fence 水位、peer 队列与隔离等待 | 不手动缩短 quarantine 或借 Gossip 授权 |
| 下行未命中或失败 | 单用户路由、Gateway 活跃观察、快照 ACK、逐连接结果 | 单 Gateway 失败保留其他结果，不广播重放 |
| 快照 `RETRY_AFTER` | 未完成批次数/字节数、超时、重试退避 | 不应用部分路由 |
| Gossip peer 失败/观察不一致 | 多节点诊断、最近 Gossip 记录、tombstone TTL | 仅延迟收敛，不改变权威 epoch |
| Gateway 异常消失 | 心跳/TTL、注册表、清理日志和后续快照 | 清理失败由扫描或调用方重试 |
| Message Facade 超时 | 上行 Handler 结果与 Dubbo 日志 | 返回未处理，不在 Broker 落消息 |
| 管理写拒绝 | token、audience、scope、幂等键与 epoch | 不使用普通用户 token 或伪造管理员 Header |
| 诊断记录失败 | Tracker 日志 | 不改变注册、同步或核心操作结果 |

Gateway 下行查看 Frame write result、`im.message.broker.frame.write` 与 `im.broker.gateway.frame.write`。后者仅表示 Broker 到 Gateway RPC 次数、结果和 duration，不带用户/消息/连接/Gateway 身份标签，不表示客户端收到或 ACK。

运行观察不能当作强一致集群真值。跨节点问题同时比较各节点 Registry/Gossip 与 committed cluster-state，再按 [Raft 与 Gossip 的边界](../ARCHITECTURE.md#raft-与-gossip-的边界) 判断。

## 验证入口

以下命令在仓库根目录执行，用于复现模块测试和集成验证。

```bash
mvn -q -pl im-broker/im-broker-server -am test
./scripts/verify.sh architecture
./scripts/verify.sh behavior
./scripts/verify.sh e2e
```

本地 Follower 修复已通过访问器 16 项、Runtime 22 项、Broker 聚焦 87 项及原三项 `AutomaticFailureTlsHaTest`；Runtime reviewer `a682f997`、HA fixture reviewer `cf4028e1` 均 scoped Accept，HA 日志为 `/tmp/follower-ha-three-final.log`。该 HA 使用真实三 Broker TLS，Gateway ACK 为替代边界，不是真实 Gateway E2E；不能据此关闭私有发布、双构建/输入锁或最终全仓门禁缺口。完整证据与当前计划状态通过[计划索引](../../docs/exec-plans/active/README.md)定位，最终门禁以 Harness 当前工作树指纹记录为准。

真实本地多节点故障拓扑入口为 `./scripts/verify.sh routing-ha`，运行隔离的三 Broker/两 Gateway，详情见[本地编排的路由高可用拓扑](../../deploy/local/README.md#路由高可用拓扑)。普通 full Compose、realtime E2E 和本地 load test 的单 Broker 结果不等于生产多副本容量保证；证据范围与生产缺口见 [Broker 验证矩阵](../README.md#验证状态)。
