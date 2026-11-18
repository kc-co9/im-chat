# IM Chat 架构

## 文档职责

本文是仓库级当前架构事实源，说明运行拓扑、限界上下文关系、模块依赖方向和数据所有权。模块内部组件、处理流程和一致性模型由局部 Architecture 说明：

- [Gateway Architecture](im-gateway/ARCHITECTURE.md)
- [Broker Architecture](im-broker/ARCHITECTURE.md)
- [Message Architecture](im-service/im-message/ARCHITECTURE.md)

开发和配置入口见各模块 README；设计取舍见 [`docs/design-docs`](docs/design-docs/index.md)；实施状态见 [`docs/exec-plans`](docs/PLANS.md)。局部 Architecture 只为复杂运行边界创建，Facade、SDK、聚合 POM 和单一能力 Plugin 不机械复制本文内容。

## 运行拓扑

```mermaid
flowchart LR
    Client[客户端] -->|HTTP| HttpGateway[im-http-gateway]
    Client <-->|WebSocket| WsGateway[im-ws-gateway]
    HttpGateway -->|认证| Account[im-account]
    HttpGateway --> Services[业务服务]
    WsGateway -->|认证| Account
    WsGateway <--> Broker[im-broker]
    Broker --> Services

    Ops[运维浏览器] --> IAM[im-iam-server]
    IAM --> IamDb[(IAM MySQL)]
    Ops --> Ops[im-ops]
    Ops --> BrokerHttp[Broker 管理 HTTP]
    Ops --> Admin[im-admin]
    Admin --> AccountAdmin[Account Admin Facade]
    AccountAdmin --> Account
    Ops --> Audit[im-audit-server]
    Audit --> AuditDb[(Audit MySQL)]
    Admin -->|Kafka / 认证异步 HTTP| Audit
    IAM -->|Kafka / 认证异步 HTTP| Audit
    Ops -->|Kafka / 认证异步 HTTP| Audit
```

- `im-http-gateway` 是外部 HTTP 入口和路由边界。
- `im-ws-gateway` 拥有 WebSocket 连接、握手认证、协议适配和本机投递。
- `im-broker` 拥有用户到 Gateway 的路由、Gateway 协调、精确帧投递和 Broker Gossip 同步。
- `im-service` 拥有 Account、Social、Message 业务规则和持久化事实。
- `im-management` 中的 IAM、Admin、Ops、Audit 是独立部署的管理应用，不与普通用户认证或业务表合并。

Broker SDK 将 Nacos 候选地址与部署显式地址/完整 URI 配对取交集作为 bootstrap；首次可信调用后，周期性执行 `GET_CLUSTER_STATE`，原子安装已提交完整状态，并以固定 Broker namespace + committed brokerId 构造后续目标。Nacos metadata、Gossip 观察和首响应自报身份都不是首次信任源。Raft committed state 拥有权威拓扑，Gossip Registry 只拥有运行观察。

项目角色身份命名空间由 `im-common` 的 `PeerIdentityConstants` 固定：Broker 为 `spiffe://im-chat/broker/`、Gateway 为 `spiffe://im-chat/gateway/`、Message 为 `spiffe://im-chat/message/`、Account 为 `spiffe://im-chat/account/`、Ops 为 `spiffe://im-chat/im-ops/`，不提供部署覆盖。SDK 构造目标、bootstrap 校验与 Server 角色授权共同遵循该约定；节点 ID、完整身份、地址和 committed 拓扑仍按原流程管理。通用 Plugin 不依赖项目角色常量，`im.bolt.client.allowed-peer-uri-prefixes` 仍可配置，但不能改变项目角色规则。环境隔离与自定义前缀部署迁移见 [安全规范](docs/SECURITY.md#固定项目角色身份命名空间)。

所有 Bolt 业务调用统一使用不可变 `BoltTarget(address, expectedPeerIdentity)`，由 im-bolt 受控客户端固定模式/前缀/预算资源策略并完成连接认证和 typed 发送准入。目标选择归 SDK/Adapter，证书和连接生命周期归 Plugin，成员、角色、租约及业务授权仍归接收模块；不保留地址调用或认证失败回退。D11 同 JVM 可信代码和 D10 会话数组边界见 [im-bolt](im-plugin/im-bolt/README.md)，不把静态 API 检查当任意 raw 字节运行期隔离。

## 业务上下文与部署边界

| 限界上下文 | 部署模块 | 权威事实 |
|---|---|---|
| User、在线 Session | `im-account` | 用户、凭据、Token、Session version 和撤销状态 |
| Friend、Group | `im-social` | 好友关系、群组和成员关系 |
| Chat、Message | `im-message` | 用户视角会话、收件箱副本、消息状态和当前 Chat View |
| 管理身份、应用授权、OAuth 授权 | `im-iam-server` | 管理员、应用、Client、角色、权限和 OAuth 授权 |
| 管理 Audit | `im-audit-server` | 不可变 BUSINESS/SECURITY 审计事实 |

限界上下文是模型语言和规则的边界，不等于独立进程。一个服务可以承载多个内部上下文，但跨部署模块协作只通过 Facade、SDK 或明确的 HTTP 协议，不共享内部 Entity、Repository 或数据库表。

### Management 模块边界

| 模块 | 所有权 |
|---|---|
| `im-iam-server` | 管理员身份、SSO、应用、confidential／专用 public Client、应用级 RBAC 和 OAuth2/OIDC 授权 |
| `im-iam-sdk` | 管理应用复用的 OAuth2/OIDC BFF、管理员 Bearer、Token Session、Introspection 和权限目录同步；不依赖 IAM Server 实现 |
| `im-ops-cli` | 独立 Java 21 前台客户端，仅依赖 JDK／Jackson；经浏览器 Authorization Code + PKCE 登录 IAM，再调用 Ops 的固定 CLI HTTP 用例，不依赖 Server 或保存 Token |
| `im-admin` | 普通用户管理用例与 UI；通过 Account Admin Facade 修改 Account 事实，不直连 Account 数据库 |
| `im-ops` | Broker 发现、诊断聚合、受控集群 operation 与运行控制台；不拥有路由状态或业务事实 |
| `im-audit-sdk` | 不可变 Audit Event、上下文采集、声明式审计和传输适配 |
| `im-audit-server` | Audit 接收、幂等追加、查询、UI 和受限导出；是管理 Audit 唯一持久化所有者 |

Admin、IAM、Ops 通过按来源隔离的 Kafka 或认证异步 HTTP 向 Audit 提交事实。生产者依赖 Audit SDK，不依赖 Audit Server；管理应用通过 IAM SDK 接入身份能力，不复制 IAM 的管理员、角色或授权模型。

CLI 不是独立部署的管理服务，不适用服务端 Nacos 注册要求。它仅向显式配置的 IAM／Ops HTTPS origin 发送请求；固定 IPv4 loopback 回调只承接单次授权码。Ops 的 `/api/cli/**` 使用独立无状态 Bearer 链，每次通过真实 Introspection 校验管理员主体、精确 Client、audience、scope 与 authority；浏览器 BFF 保持独立链及 CSRF。Ops 审计绑定当前管理员与可信 Client，Broker 仍拥有 operation 与许可权威，CLI 的 owner-only 非敏感请求记录只用于显式原语义恢复，不成为授权依据。

## 模块依赖方向

```text
im-gateway/*-server -> Gateway/Broker SDK -> Service Facade
im-broker-server    -> Broker/Gateway SDK -> Message Facade
im-service/*-server -> own Facade + dependent Facade/SDK
im-management       -> IAM SDK / Audit SDK / Account Admin Facade / generic Plugin
Facade / SDK        -> im-common + stable framework contract
im-plugin           -> generic framework integration
im-common           -> no business/runtime module dependencies
im-test             -> test-scope access to runtime modules; no production code
```

必须保持以下边界：

1. Shared module 不依赖运行模块或业务模块。
2. SDK、Facade 不依赖其 Server 实现。
3. Plugin 不依赖 Broker、Gateway 或业务服务包。
4. Domain 不依赖 interfaces、infrastructure、configuration、lifecycle 或 Spring 类型。
5. Controller、RPC Handler 只做协议适配并委托 Application/Domain Service，不直接访问具体持久化实现。
6. Bolt、Dubbo、Nacos、Redis、HTTP 等运行协议在边界完成转换，不进入领域模型。
7. `im-web` 只拥有通用 HTTP 行为；Session-to-Servlet 适配属于 `im-session`，IAM 身份语义属于 IAM。

成员通信端点值 `MembershipEndpoint` 归 `im-common/domain/cluster/model`，只拥有不透明非空白字符串和值语义。Raft 插件以 String 列表 `RaftConfiguration` 表达完整 voters/learners 目标，不表示已提交事实；`leaderConfiguration()` 分别读取两种角色，只提供非原子的 Leader 诊断观察，不证明 FSM applied、quorum 或许可排空。BrokerRaftAdapter 负责技术边界转换，Broker 的领域聚合与 FSM 拥有已提交业务状态、成员绑定、一致性判断及维护步骤与许可；不建立 Plugin → Broker 或 Domain → Plugin 的依赖。

机械可判断的依赖规则由 `im-test/im-architecture-test` 与 `scripts/check-drift.sh` 执行；跨模块实时运行旅程由 `im-test/im-e2e-test` 验证。业务语义和合理例外由 Review 判断。

## 数据与运行状态所有权

- Account、Social、Message 分别拥有 `im_chat_account`、`im_chat_social`、`im_chat_message` MySQL Schema，不共享表或执行跨 Schema SQL。
- IAM 的 `im_chat_iam` 拥有管理身份、应用、Client、角色、权限和 OAuth 授权；只持久化 Token digest。
- Audit 的 `im_chat_audit` 是管理 Audit 唯一权威，按 `auditId` 幂等追加事实；生产者只依赖 Audit SDK。
- WebSocket Gateway 拥有本机 `connectionId`、Netty Channel、身份和 Session version；Broker 不保存远端 Channel 或本机连接 ID。
- Broker 只保存定位用户 Gateway 所需的路由状态；Broker/Gateway Registry 与 Connection Gossip view 经 Gossip 最终一致同步，业务 Connection Registry 只保存当前 owner 的路由。
- Account Session version 是撤销权威。Broker 路由 `userId + oldSessionVersion` 关闭控制，目标 Gateway 只关闭匹配旧版本的本机连接。
- Message 的 MySQL 保存聊天与收件箱事实；Redis `ImChatView` 只影响未读判断，Receipt 状态只用于通知确认和有界重投。
- Management 应用各自的 Redis 只保存加密 BFF Token Session 和有界 Introspection cache，不成为普通用户、权限或 Audit 事实源。

## 跨模块一致性边界

- MySQL 单服务事务只保证所属 Schema 内的写入；跨服务调用不形成分布式事务。
- Message 在事务提交后发起在线通知，推送失败不回滚已提交消息。
- Broker Registry/Gossip 是运行态最终一致模型，不替代业务持久化或强一致事务日志。
- Nacos 提供配置和服务发现，不拥有用户路由或业务状态。Broker 从已初始化的实际 Node 发布完整 `raft-peer-id`，Ops 将其与受支持管理 HTTP origin 严格配对；metadata 只是可信发现范围内的寻址信息，不成为 committed 成员、TLS 身份或权限权威。Follower 的事件快照只允许无副作用拒绝，Node 查询和执行授权仍归 Broker worker。
- Ops 聚合有界诊断，并把管理员意图转成带 IAM 权限、幂等键和预期 epoch 的 Broker operation；只有 Broker Leader 能通过 Raft 改变权威状态，节点查询失败不会自行改变 Broker 状态。
- Audit 通过 Kafka 或认证异步 HTTP 接收，依赖来源校验、重试和 `auditId` 幂等，而不是调用方本地审计表。

## 变更规则

- 跨模块拓扑、上下文部署、依赖方向或数据所有权变化时更新本文。
- 模块内部组件关系、一致性模型或关键处理链路变化时更新对应局部 Architecture。
- 运行配置、开发入口和面向使用者的模块行为更新模块 README。
- 难以逆转且存在真实取舍的决策进入 `docs/design-docs`；多步骤实施进入 `docs/exec-plans/active`。
- 可靠性或安全保证变化时同步更新 [`RELIABILITY.md`](docs/RELIABILITY.md) 或 [`SECURITY.md`](docs/SECURITY.md)。
