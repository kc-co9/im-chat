# ImChat

`ImChat` 是基于 Java 21、Spring Boot 3 和 Maven 多模块构建的即时通讯服务。系统覆盖账号认证、好友与群组关系、聊天会话、私聊与群聊消息、WebSocket 在线连接和跨节点精确投递。

项目采用 DDD 组织业务代码，并将 HTTP 入口、WebSocket 连接、实时路由和业务服务拆分为独立运行职责。当前拓扑、模块边界和数据所有权以 [ARCHITECTURE.md](ARCHITECTURE.md) 为准。

## 核心能力

- 账号注册、登录、Token 刷新、登出和单端会话替换。
- 好友添加、删除、拉黑、备注和关系查询。
- 群组创建、成员管理、群主转让、群昵称和通知设置。
- 私聊与群聊会话的打开、退出、隐藏和列表查询。
- 私聊与群聊消息的发送、接收、已读、撤回和历史查询。
- WebSocket 认证、在线路由、跨 Gateway 投递和客户端回执重试。

## 领域建模

本项目使用 DDD（Domain-Driven Design，领域驱动设计）组织业务代码。DDD 的目的不是增加目录或对象数量，而是围绕统一语言建立模型，让用户、好友、群组、聊天和消息等业务规则在代码中有明确归属，并与 HTTP、RPC、数据库、缓存等技术细节分离。

业务服务内部按限界上下文组织聚合、实体、值对象、领域服务、领域事件和仓储接口。应用层负责用例编排，领域层负责业务规则，接口层和基础设施层分别负责协议适配与技术实现。

### 领域对象概念

| 概念 | 在项目中的职责 | 使用边界 |
|---|---|---|
| 聚合根 | 维护一组对象的一致性边界，对外提供具有业务语义的状态变更方法 | 外部对象通过聚合根修改聚合，不绕过聚合直接改变内部状态 |
| 实体 | 具有稳定业务身份和生命周期，属性变化后仍表示同一业务对象 | 是否相同由身份判断，而不是比较全部字段 |
| 值对象 | 表达业务含义、约束和组合关系，例如 `UserId`、`SessionVersion`、`ImChatId` | 通常不可变，按值比较；业务标识和领域属性优先使用值对象而不是裸基础类型 |
| 领域服务 | 承载无法自然归属于单个聚合或值对象的领域规则、策略或能力抽象 | 不用于包装简单构造、应用编排或对另一个对象的纯委托 |
| 领域事件 | 表达领域内已经发生的业务事实 | 事件由领域产生，应用层或监听器负责通知、投递等后续动作 |
| 仓储 | 提供按聚合语义查询和保存的抽象 | 接口位于领域层，MySQL、Redis 等实现位于基础设施层 |

应用服务、CQRS 命令和 DTO 不属于领域对象。应用服务负责认证、事务、锁、跨服务调用和结果转换，不应把聚合内部的业务判断拆散到用例编排中。

### 子域与限界上下文

| 子域 | 子域类型 | 限界上下文 | 主要职责 |
|---|---|---|---|
| 消息子域 | 核心域 | 消息上下文 | 私聊与群聊消息、收件箱副本、已读、撤回和通知投递 |
| 聊天子域 | 核心域 | 聊天上下文 | 私聊与群聊会话、会话列表、未读状态和当前聊天视图 |
| 群组子域 | 核心域 | 群组上下文 | 群组、群成员、群主、群昵称和通知设置 |
| 好友子域 | 支撑子域 | 好友上下文 | 好友关系、备注、拉黑和好友展示信息 |
| 用户子域 | 通用子域 | 用户上下文 | 用户身份、邮箱、密码和用户基础信息 |
| 在线会话子域 | 支撑子域 | Session 上下文 | Token 签发、刷新轮换、登录版本和会话撤销 |

限界上下文描述模型语言和规则的边界，不要求每个上下文单独部署。好友和群组当前由 `im-social` 承载，聊天和消息由 `im-message` 承载，用户和在线 Session 由 `im-account` 承载；跨服务协作只通过 Facade 或 SDK 契约进行。

### 统一语言

下面列出当前代码中的主要领域术语，帮助读者从业务概念定位到模型。它是阅读入口，不替代领域代码、已批准的[业务规格](docs/product-specs/index.md)和[设计文档](docs/design-docs/index.md)。

#### 用户与在线 Session

| 业务术语 | DDD 对象类型 | 建模名称 |
|---|---|---|
| 用户 | 聚合根 | `User` |
| 用户 ID | 值对象 | `UserId` |
| 用户邮箱 | 值对象 | `UserEmail` |
| 原始密码 | 值对象 | `UserRawPassword` |
| 加密密码 | 值对象 | `UserPassword` |
| 在线 Session | 聚合根 | `Session` |
| Session 版本 | 值对象 | `SessionVersion` |
| Access Token | 值对象 | `AccessToken` |
| Refresh Token | 值对象 | `RefreshToken` |
| Refresh Token 指纹 | 值对象 | `RefreshFingerprint` |
| Session 领域能力 | 领域服务 | `SessionService`、`SessionTokenCodec` |

#### 好友与群组

| 业务术语 | DDD 对象类型 | 建模名称 |
|---|---|---|
| 好友关系 | 聚合根 | `Friend` |
| 好友关系 ID | 值对象 | `FriendId` |
| 好友关系边 | 值对象 | `FriendEdge` |
| 好友备注 | 值对象 | `FriendAlias` |
| 好友状态 | 值对象 | `FriendStatus` |
| 群组 | 聚合根 | `Group` |
| 群成员 | 实体 | `GroupMember` |
| 群名称 | 值对象 | `GroupName` |
| 成员数量 | 值对象 | `MemberCount` |
| 群通知设置 | 值对象 | `GroupNotification` |
| 好友与群组规则 | 领域服务 | `FriendService`、`GroupService` |

#### 聊天与消息

| 业务术语 | DDD 对象类型 | 建模名称 |
|---|---|---|
| 聊天会话 | 聚合根基类 | `ImChat` |
| 私聊会话 | 聚合根 | `ImPrivateChat` |
| 群聊会话 | 聚合根 | `ImGroupChat` |
| 聊天 ID | 值对象 | `ImChatId` |
| 当前聊天视图 | 值对象 | `ImChatView` |
| 消息 | 聚合根 | `ImMessage` |
| 私聊收件箱消息 | 聚合根 | `ImPrivateInboxMessage` |
| 群聊收件箱消息 | 聚合根 | `ImGroupInboxMessage` |
| 消息 ID | 值对象 | `ImMessageId` |
| 消息 Token | 值对象 | `ImMessageToken` |
| 消息内容 | 值对象 | `ImMessageContent` |
| 聊天与消息规则 | 领域服务 | `ImChatService`、`ImMessageService` |

### 领域事件

领域事件描述已经发生的业务事实，例如 `FriendAddedEvent`、`GroupCreatedEvent`、`ImPrivateMessageSentEvent` 和 `ImGroupMessageRevokedEvent`。聚合产生事件后，应用层或事件监听器可以执行通知和实时投递；这些后续技术动作不反向进入领域模型。

新增或修改领域代码时遵循[编码规范](docs/references/CODING_GUIDE.md)：业务标识使用值对象表达，领域对象不依赖接口协议或基础设施类型，应用服务的输入输出使用明确的 CQRS 对象。

### DDD 分层与依赖方向

业务服务采用依赖倒置组织代码，领域模型不感知 HTTP、RPC、Spring 或具体存储：

```text
interfaces -> application -> domain
                    |          ^
                    v          |
                 adapter   infrastructure
```

- `interfaces` 将 HTTP、RPC 等外部协议转换为应用层命令或查询，不承载业务规则。
- `application` 使用 CQRS 入参和出参编排用例、事务、锁及跨边界协作，不替代聚合表达业务状态变化。
- `domain` 保存聚合、实体、值对象、领域服务、领域事件和仓储抽象，只依赖稳定的领域或公共契约。
- `adapter` 适配外部服务能力，边界方法优先使用本上下文的领域对象，不向领域层泄漏外部 DTO。
- `infrastructure` 实现仓储、加密、缓存和消息等技术契约；转换集中在 transformer，不让持久化对象进入领域行为。

简单对象创建留在聚合或应用层；只有需要跨对象规则、策略或外部能力抽象时才引入领域服务。完整边界和代码示例见[编码规范](docs/references/CODING_GUIDE.md)。

## 运行架构

```text
client
  |-- HTTP ------> im-http-gateway --auth--> im-account
  |                                  `-----> account/social/message HTTP
  `-- WebSocket -> im-ws-gateway --auth--> im-account
                         `---------> im-broker -> im-message
                                          |
                                          `-> target im-ws-gateway -> client
```

- `im-http-gateway` 负责外部 HTTP 路由、认证和可信用户上下文重建。
- `im-ws-gateway` 负责 WebSocket 握手认证、本地连接和帧协议适配。
- `im-broker` 负责用户到 Gateway 的路由、精确投递和 Broker 间 Gossip 状态同步。
- `im-account` 是 Token 签发和在线 Session 认证的唯一权威。
- `im-social` 拥有好友、群组和成员关系。
- `im-message` 拥有聊天视图、消息事实、收件箱副本和通知状态。

### 依赖与数据所有权

```text
gateway server -> gateway/broker SDK -> service facade
broker server  -> broker/gateway SDK -> message facade
service server -> own facade + dependent facade/SDK
service facade -> im-common
im-common      -> no business or runtime module
```

| 状态 | 权威所有者 | 说明 |
|---|---|---|
| Token 与 Session version | `im-account` | Gateway 只校验和透传认证结果 |
| WebSocket 本地连接 | `im-ws-gateway` | 不向业务服务暴露连接实现细节 |
| 用户到 Gateway 的在线路由 | `im-broker` | 通过 Broker 协调和 Gossip 维护 |
| 好友、群组和成员关系 | `im-social` | 其他服务通过 Facade 查询 |
| 聊天、消息和收件箱事实 | `im-message` | MySQL 为持久化事实来源 |
| 当前聊天视图与通知重试 | `im-message` | Redis 保存可恢复的短期状态 |

### 认证链路

HTTP 请求和 WebSocket 握手都通过 Account Facade 校验 Access Token 和当前 Session version。Access Token 默认有效期为 2 小时，Refresh Token 默认有效期为 30 天并在刷新时轮换。登出或再次登录会更新账号 Session；Broker 只负责把关闭控制路由到目标 Gateway，不持有 Token 或 Gateway 本地连接 ID。

认证设计见[集中式会话认证](docs/design-docs/2026-08-14-centralized-session-authentication-design.md)，安全保证见 [SECURITY.md](docs/SECURITY.md)。

### 消息与通知链路

```text
sender client
  -> WS Gateway -> Broker -> Message Service
                              |
                              | transaction
                              v
                    MySQL message/inbox facts
                              |
                              | domain event after commit
                              v
                    create Redis receipt task
                              |
                              v
                    Broker user route -> target WS Gateway -> receiver client
                              ^                                  |
                              | delayed redelivery               | notification ACK
                              +----------------------------------+
                                                                 |
receiver client -> WS Gateway -> Broker -> Message Service ------+
                                              |
                                              v
                                   update receive state and
                                   remove Redis receipt task
```

消息服务在事务内完成权限和幂等校验、写入消息及收件箱副本、更新聊天状态并发布领域事件。通知方法通过 `@AfterTransactionCommit` 在事务成功后执行，因此在线推送失败不会回滚已经提交的消息事实。

通知由 Message 服务转换为带 `eventId`、`receiptId` 的 PUSH 帧，经 Broker 的用户路由写入目标 WS Gateway。本机 Gateway 只向自己持有的连接投递；接收方离线或局部连接写入失败不影响 MySQL 中的消息，用户重新进入会话时仍可查询消息副本。

需要客户端确认的通知会在首次投递前把 `ReceiptTask` 写入 Redis。ACK 经 WS Gateway、Broker 回到 Message Facade；发送通知的 ACK 还会更新对应收件箱消息的接收状态，随后删除回执任务和重投次数。当前实现允许首次通知后最多重投 3 次，可能产生重复通知，客户端必须按 `eventId`、`receiptId` 或消息标识幂等处理。

Redis 回执任务提供有界重投，但不是数据库 Outbox，也不承诺覆盖进程崩溃的每个时间窗口。完整实现和可靠性边界见 [im-message-server README](im-service/im-message/im-message-server/README.md) 与 [RELIABILITY.md](docs/RELIABILITY.md)。

已批准的实时行为和边界见[实时场景规格](docs/product-specs/im-realtime-approved-scenarios.md)。

### 聊天视图状态

打开私聊或群聊时，Message 服务记录用户当前查看的 `ImChatView`；切换会话会覆盖旧状态，退出或隐藏当前会话会清理状态。投递新消息时，Message 服务据此决定当前会话是否累加未读数。该状态属于消息上下文，不存放在 Account Session、Gateway 或 Broker。

设计见[聊天视图状态](docs/design-docs/2026-08-21-message-chat-view-presence-design.md)。

## 模块地图

| 模块 | 职责 |
|---|---|
| `im-common` | 稳定共享契约、公共值对象、异常和框架无关工具 |
| `im-plugin` | Bolt、Dubbo、Nacos、Gossip、缓存、锁、数据源、Session、Metrics 等通用集成 |
| `im-gateway/im-http-gateway` | 外部 HTTP 路由、认证和内部请求头治理 |
| `im-gateway/im-ws-gateway` | WebSocket SDK、连接服务和协议入口 |
| `im-broker` | 实时路由 SDK、Broker 服务和 Gossip 协调 |
| `im-service/im-account` | 用户、凭证和在线会话 |
| `im-service/im-social` | 好友、群组和成员关系 |
| `im-service/im-message` | 聊天、消息、收件箱和通知 |
| `im-management/im-monitor` | Broker 只读业务诊断聚合和独立监控页面 |
| `im-management/im-iam` | 统一管理端身份、OAuth2/OIDC、机器客户端、应用级 RBAC 与接入 SDK |
| `im-management/im-admin` | IAM 接入、普通用户管理和管理 UI |
| `im-management/im-audit` | 集中业务/安全审计 SDK、接收服务、查询 UI 和受限 Excel 导出 |
| `im-architecture` | ArchUnit 依赖和包边界检查 |
| `scripts` | Harness、影响分析和验证入口 |

每个模块的运行能力和局部配置由其 README 说明。Coding Agent 修改模块前还需要读取最近的 `AGENTS.md`。

### Broker 监控链路

```text
operations browser
        |
        | /api (read only)
        v
    im-monitor ---- Nacos metadata ----> Broker management addresses
        |
        +---- concurrent HTTP ----> Broker A :12201
        +---- concurrent HTTP ----> Broker B :12201
        `---- concurrent HTTP ----> unavailable node -> UNREACHABLE
```

Broker 的业务 Bolt 端口仍为 `12200`；只读管理 HTTP 默认绑定 `127.0.0.1:12201`。Monitor 对单节点失败进行隔离，页面不会直接访问 Broker，也不提供全量用户路由或任何写操作。运行方式和接口见 [im-monitor README](im-management/im-monitor/README.md)。

### 业务管理链路

```text
operations browser -> IAM SSO -> im-admin -> Account Admin Facade -> im-account
                         |-------> im-monitor -> Broker diagnostics
                         `-------> im-audit -> immutable audit query/export

im-admin / im-iam / im-monitor
        `-> source Kafka Topic or authenticated async HTTP -> im-audit
```

IAM 使用与普通用户完全独立的管理账号、OAuth2/OIDC BFF Session、机器客户端和应用级 RBAC。Admin、Monitor、Audit 均实时查询 IAM 权限，不读取 Account 表也不复用普通用户 Token；普通用户管理命令仍由 Account 执行。Admin 和 IAM 通过 Audit SDK 发布审计事实，不再持有本地审计表。运行方式见 [IAM README](im-management/im-iam/im-iam-server/README.md)、[Admin README](im-management/im-admin/README.md) 与 [Audit README](im-management/im-audit/im-audit-server/README.md)。

## Harness 与质量门禁

项目引入 Harness Engineering，不是为了增加一套独立于开发流程的文档和检查脚本，而是让仓库本身具备足够的可理解性、约束能力和反馈能力。开发者和 Coding Agent 都应该能够从仓库中回答三个问题：

1. 系统当前如何组织，业务事实和运行状态分别由谁负责。
2. 修改需要遵守哪些架构、领域和编码边界。
3. 修改完成后，如何用可重复的证据证明结果可信。

代码中的良好模式和不良模式都会被后续开发继续复制。仅依赖个人记忆、聊天记录或人工 Review，会让相同问题反复出现，也会使文档和代码逐渐偏离。因此，Harness 把稳定的工程经验沉淀到仓库，并提供尽可能短且确定的反馈闭环。

### Harness 设计原则

1. **仓库内生**：架构、设计、计划、可靠性和安全约束与代码一起版本化，不依赖个人记忆或会话上下文。
2. **渐进披露**：根 README 解释项目全貌和设计背景，`ARCHITECTURE.md` 与专题文档展开细节，`AGENTS.md` 提供 Coding Agent 的执行规则，避免全部内容堆积在单个文件中。
3. **约束可执行**：能够稳定机械判断的问题进入 ArchUnit、测试、Maven 或漂移脚本；需要业务语义判断的内容保留在设计规范和 Review 中。
4. **反馈分层**：开发过程中使用快速、局部反馈，交付前执行完整验证，运行后通过指标和日志继续观察真实行为。
5. **持续沉淀**：重复出现的缺陷和 Review 意见逐步转化为规范、正反 fixture、测试或自动门禁，减少重复依赖人工提醒。

Harness 关注的不只是提示词质量。更重要的是为开发者和工具提供准确上下文、稳定边界和短反馈周期，同时控制仓库随着长期修改产生的熵增。

```text
开发与运行反馈
    -> 设计或编码规范
    -> 单元测试、行为测试或漂移检查
    -> 架构与 CI 约束
    -> 更短、更确定的反馈闭环
```

### 质量门禁分工

Harness 将高确定性规则自动化，同时把需要业务判断的 DDD 语义留给 Review，避免为了“全自动”引入大量误报。

| 层级 | 负责内容 | 主要入口 |
|---|---|---|
| 静态脚本 | SQL 安全、Java 明确坏味道、文档和计划漂移 | `scripts/check-*.sh` |
| 架构测试 | 模块依赖、分层边界、包职责和公共契约 | `im-architecture` |
| 行为测试 | 认证、路由、投递、确认及领域行为 | 各模块测试与 `verify.sh behavior` |
| 人工 Review | 领域归属、命名、抽象尺度和难以机械判断的业务语义 | Code Review Guide |

规则新增遵循“先写规范和正反 fixture，再接入门禁”的顺序。机械检查只覆盖可以稳定识别的低误报场景；不能可靠解析的构造器语义、领域服务尺度和业务命名由 Review 判断，不通过放宽门禁来掩盖真实违规。

Harness 是持续演进的工程能力，不追求一次性把所有约定都变成自动检查。只有形成稳定需求、明确维护者和验证方式时才增加新规则或专题入口，避免出现无人维护的文档和高误报门禁。规则生命周期、例外、反馈和移除条件见 [Harness Guide](docs/references/HARNESS_GUIDE.md) 与 [Harness Feedback](docs/feedback/HARNESS_FEEDBACK.md)。

## 外部入口

HTTP 请求统一经过 `im-http-gateway`，主要路径如下：

| 路径 | 服务 |
|---|---|
| `/account/**` | 账号、登录、刷新和登出 |
| `/social/**` | 好友、群组和成员关系 |
| `/message/**` | 聊天和消息查询 |

WebSocket 入口为 `/ws`，使用统一 JSON 帧而不是 STOMP destination。具体命令和通知以 Gateway、Broker SDK 及实时场景规格为准。

## 本地运行

### 环境

- JDK 21
- Maven 3.9+
- MySQL 8+
- Redis 6+
- Nacos 2+

DDL 与数据所有权一起放在各 Server 模块根目录：

- Account：[`im-service/im-account/im-account-server/sql/ddl.sql`](im-service/im-account/im-account-server/sql/ddl.sql)
- Social：[`im-service/im-social/im-social-server/sql/ddl.sql`](im-service/im-social/im-social-server/sql/ddl.sql)
- Message：[`im-service/im-message/im-message-server/sql/ddl.sql`](im-service/im-message/im-message-server/sql/ddl.sql)
- IAM：[`im-management/im-iam/im-iam-server/sql/ddl.sql`](im-management/im-iam/im-iam-server/sql/ddl.sql)
- Audit：[`im-management/im-audit/im-audit-server/sql/ddl.sql`](im-management/im-audit/im-audit-server/sql/ddl.sql)

五个服务分别使用 `im_chat_account`、`im_chat_social`、`im_chat_message`、`im_chat_iam` 和
`im_chat_audit` Schema。SQL 编写和 Mapper 约束见 [SQL Guide](docs/references/SQL_GUIDE.md)。

### Nacos

应用通过 `im-nacos` 导入公共配置和各自的应用配置。默认本地地址为 `127.0.0.1:8848`；Namespace、Group 和服务地址可按环境覆盖。账号服务启用 JWT 时必须配置满足长度要求的 `im.session.jwt.secret`，部署环境应通过 Nacos 覆盖仓库中的本地值。

各运行应用使用独立 Data ID，例如：

- `im-http-gateway.yml`
- `im-ws-gateway.yml`
- `im-broker.yml`
- `im-account.yml`
- `im-social.yml`
- `im-message.yml`

### 构建与验证

```bash
mvn clean package
```

日常修改后运行快速验证：

```bash
./scripts/verify.sh quick
```

提交或交付前运行完整验证：

```bash
./scripts/verify.sh full
```

需要时可以运行：

```bash
./scripts/verify.sh affected
./scripts/verify.sh behavior
./scripts/verify.sh architecture
./scripts/verify.sh report
```

`report` 输出 `target/harness/report.json`，汇总最近验证状态、测试结果、计划、技术债务和 Harness 反馈。验证模式和维护规则见 [Harness Guide](docs/references/HARNESS_GUIDE.md)。

### 启动顺序

本地联调建议按依赖顺序启动：

1. MySQL、Redis、Nacos。
2. `im-account`、`im-social`、`im-message`。
3. `im-broker`。
4. `im-ws-gateway`。
5. `im-http-gateway`。

实际配置和端口以各模块 README 与 `application.yml` 为准。

## 文档导航

| 想了解 | 文档 |
|---|---|
| 当前运行拓扑、边界和数据所有权 | [ARCHITECTURE.md](ARCHITECTURE.md) |
| Coding Agent 执行规则 | [AGENTS.md](AGENTS.md) |
| 设计选择与历史决策 | [Design Documents](docs/design-docs/index.md) |
| 已确认的业务行为 | [Product Specifications](docs/product-specs/index.md) |
| 跨模块实施计划 | [Execution Plan Policy](docs/PLANS.md) |
| 编码、测试、SQL、Git 和 Review 规范 | [Engineering References](docs/references/index.md) |
| 可靠性和安全保证 | [RELIABILITY.md](docs/RELIABILITY.md)、[SECURITY.md](docs/SECURITY.md) |
| Harness 规则维护和反馈 | [Harness Guide](docs/references/HARNESS_GUIDE.md)、[Harness Feedback](docs/feedback/HARNESS_FEEDBACK.md) |

## 许可证

本项目基于 GNU General Public License v3.0 only，详情见 [LICENSE](LICENSE)。
