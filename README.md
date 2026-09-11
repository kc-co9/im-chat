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

## 领域上下文

根 README 只提供跨上下文导航；统一语言、业务不变量、协作边界和状态所有权由对应模块 README 维护。

| 限界上下文 | 主要业务范围 | 所属模块文档 |
|---|---|---|
| User | 用户身份、邮箱、密码和基础信息 | [im-account](im-service/im-account/README.md) |
| 在线 Session | Token 签发与刷新、登录版本和会话撤销 | [im-account](im-service/im-account/README.md) |
| Friend | 好友关系、备注、拉黑和展示信息 | [im-social](im-service/im-social/README.md) |
| Group | 群组、成员、群主和成员设置 | [im-social](im-service/im-social/README.md) |
| Chat | 私聊与群聊会话、会话列表和当前聊天视图 | [im-message](im-service/im-message/README.md) |
| Message | 消息、收件箱副本、已读、撤回和通知投递 | [im-message](im-service/im-message/README.md) |
| 管理 IAM | 管理员身份、应用授权、OAuth2/OIDC 和应用级 RBAC | [im-iam](im-management/im-iam/README.md) |
| 管理 Audit | 不可变业务/安全审计事实、查询和受限导出 | [im-audit](im-management/im-audit/README.md) |

模型边界不一定等于部署边界：例如 Friend 与 Group 由同一个 `im-social` 部署承载，Chat 与 Message 由同一个 `im-message` 部署承载，但各自保留独立的模型语言和规则。通用 DDD 分层、依赖方向和编码约束见[编码规范](docs/references/CODING_GUIDE.md)。

## 运行架构

```text
客户端
  |-- HTTP ------> im-http-gateway --认证--> im-account
  |                                  `-----> 账号/社交/消息 HTTP
  `-- WebSocket -> im-ws-gateway --认证--> im-account
                         `---------> im-broker -> im-message
                                          |
                                          `-> 目标 im-ws-gateway -> 客户端
```

- `im-http-gateway` 负责外部 HTTP 路由、认证和可信用户上下文重建。
- `im-ws-gateway` 负责 WebSocket 握手认证、本地连接和帧协议适配。
- `im-broker` 负责用户到 Gateway 的路由、精确投递和 Broker 间 Gossip 状态同步。
- `im-account` 是 Token 签发和在线 Session 认证的唯一权威。
- `im-social` 拥有好友、群组和成员关系。
- `im-message` 拥有聊天视图、消息事实、收件箱副本和通知状态。

### 依赖与数据所有权

```text
im-gateway/*-server -> Gateway/Broker SDK -> Service Facade
im-broker-server    -> Broker/Gateway SDK -> Message Facade
im-service/*-server -> own Facade + dependent Facade/SDK
Service Facade      -> im-common
im-common           -> no business/runtime module dependencies
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
发送方客户端
  -> WS Gateway -> Broker -> Message Service
                              |
                              | 数据库事务
                              v
                    MySQL 消息/收件箱事实
                              |
                              | 提交后领域事件
                              v
                    创建 Redis 回执任务
                              |
                              v
                    Broker 用户路由 -> 目标 WS Gateway -> 接收方客户端
                              ^                                  |
                              | 延迟重投                           | 通知 ACK
                              +----------------------------------+
                                                                 |
接收方客户端 -> WS Gateway -> Broker -> Message Service ---------+
                                              |
                                              v
                                   更新接收状态并
                                   删除 Redis 回执任务
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
| `im-test/im-architecture-test` | ArchUnit 依赖和包边界检查 |
| `im-test/im-e2e-test` | HTTP、WebSocket、Bolt 与 Broker 实时链路 E2E |
| `scripts` | Harness、影响分析和验证入口 |

每个模块的运行能力、局部配置和领域细节由其 README 说明。

### Broker 监控链路

```text
运维浏览器
        |
        | /api（只读）
        v
    im-monitor ---- Nacos 元数据 ----> Broker 管理地址
        |
        +---- 并发 HTTP ----> Broker A :12201
        +---- 并发 HTTP ----> Broker B :12201
        `---- 并发 HTTP ----> 不可用节点 -> UNREACHABLE
```

Broker 的业务 Bolt 端口仍为 `12200`；只读管理 HTTP 默认绑定 `127.0.0.1:12201`。Monitor 对单节点失败进行隔离，页面不会直接访问 Broker，也不提供全量用户路由或任何写操作。运行方式和接口见 [im-monitor README](im-management/im-monitor/README.md)。

### 业务管理链路

```text
运维浏览器 -> IAM SSO -> im-admin -> Account Admin Facade -> im-account
                 |-------> im-monitor -> Broker diagnostics
                 `-------> im-audit -> 不可变审计查询/导出

im-admin / im-iam / im-monitor
        `-> 固定来源 Kafka Topic 或已认证异步 HTTP -> im-audit
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
| 架构测试 | 模块依赖、分层边界、包职责和公共契约 | `im-test/im-architecture-test` |
| 实时 E2E | WebSocket、Bolt、Broker 路由与 Message Facade 边界 | `im-test/im-e2e-test` |
| 行为测试 | 认证、路由、投递、确认及领域行为 | 各模块测试与 `verify.sh behavior` |
| 人工 Review | 领域归属、命名、抽象尺度和难以机械判断的业务语义 | Code Review Guide |

规则新增遵循“先写规范和正反 fixture，再接入门禁”的顺序。机械检查只覆盖可以稳定识别的低误报场景；不能可靠解析的构造器语义、领域服务尺度和业务命名由 Review 判断，不通过放宽门禁来掩盖真实违规。

Harness 是持续演进的工程能力，不追求一次性把所有约定都变成自动检查。只有形成稳定需求、明确维护者和验证方式时才增加新规则或专题入口，避免出现无人维护的文档和高误报门禁。

开始工作时检查环境，日常修改运行快速验证，收尾检查可交接状态，提交或交付前运行完整验证：

```bash
./scripts/verify.sh readiness
./scripts/verify.sh quick
./scripts/verify.sh e2e
./scripts/verify.sh clean
./scripts/verify.sh full
```

`e2e` 启动真实 Broker HTTP/Bolt 与 Netty WebSocket runtime，验证实时链路的认证连接、路由、私聊发送、ACK、断开和 Gateway 重启恢复。Account 认证与 Message Facade 使用受控测试边界，因此它不替代包含 MySQL、Redis、Nacos 的全栈环境验证。完整输出保存在 `.harness/runtime/e2e.log`。

### 为什么保留这些 Harness 能力

- **自动交接（`verify.sh handoff`）**：把当前验证、失败入口、Git 改动和下一步恢复动作从聊天上下文沉淀为可复查工件；报告是派生视图，不替代 `PROGRESS.md` 或 execution plan。
- **标准启动（`verify.sh startup` / `init`）**：新 Server 如果没有进入启动门禁，很容易只在生产环境才暴露配置装配问题；startup smoke 在受控依赖下逐个验证应用入口，并拒绝零测试假通过。它不冒充 MySQL、Redis、Nacos、Kafka 全栈启动。
- **受控清理（`verify.sh cleanup`）**：长期运行的 Harness 会留下临时文件和 PID 记录；默认扫描、`--apply` 仅清理 `.harness/tmp` 与有 provenance 的陈旧 PID，避免“自动清理”误删开发者文件或业务数据。
- **AI Reviewer 质量评审（`verify.sh quality`）**：测试能证明行为和门禁，但不能可靠判断领域归属、范围纪律和可维护性；脚本准备证据，独立上下文 Reviewer 填写三项语义分数，response 缺失或与当前 scope 不匹配时明确返回 `review_required`。
- **模块 A/B/C/D 快照**：单一总分无法解释哪个边界变弱；快照按 `im-gateway`、`im-broker`、Account、Message、IAM 等质量单元展示机器分数、Reviewer 分数、硬性上限、证据和缺口，便于下一轮只复评受影响单元。

开发者执行质量评审时只需运行 `./scripts/verify.sh quality`，把生成的 `.harness/quality/review-request.json` 交给独立 AI Reviewer，再重复该命令生成中文快照。评分模型、硬性上限和 Reviewer JSON 契约见 [Quality Model](docs/references/QUALITY_MODEL.md)；AI-only 指令见 [Quality Review Prompt](docs/references/QUALITY_REVIEW_PROMPT.md)。

所有本机 Harness 证据统一保存在仓库根目录的 `.harness/`。该目录已加入 `.gitignore`，不会提交到 Git，也不会被 `mvn clean` 删除。新 clone 不包含这些本机结果：开发者或 Coding Agent 通过 `verify.sh init/full/handoff/quality` 重新建立证据；架构、领域、任务和工程规则仍由 Git 内的 Architecture、README、`PROGRESS.md`、execution plan 和 references 负责，因此 `.harness/` 不能保存唯一决策或约束。

具体子系统、验证模式、规则生命周期、例外和移除条件见 [Harness Guide](docs/references/HARNESS_GUIDE.md)，已发现的问题和改进记录见 [Harness Feedback](docs/feedback/HARNESS_FEEDBACK.md)。

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
- Maven 3.8.4+ 且低于 4.0
- Node.js 20.19+ 或 22.12+（构建 Management UI 时）
- npm、Ruby、Git、ripgrep

仓库通过 `.java-version` 推荐 JDK 21，通过 `.nvmrc` 推荐 Node.js 20.19.5；`readiness` 按 POM 与 Vite 支持范围接受兼容版本。
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

### 构建

```bash
mvn clean package
```

分层验证命令和维护规则见前文 Harness 入口。

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
| 当前活跃工作、阻塞项和下一步 | [PROGRESS.md](PROGRESS.md) |
| Gateway、Broker、Message 内部拓扑与一致性 | [Gateway Architecture](im-gateway/ARCHITECTURE.md)、[Broker Architecture](im-broker/ARCHITECTURE.md)、[Message Architecture](im-service/im-message/ARCHITECTURE.md) |
| Coding Agent 执行规则 | [AGENTS.md](AGENTS.md) |
| 设计选择与历史决策 | [Design Documents](docs/design-docs/index.md) |
| 已确认的业务行为 | [Product Specifications](docs/product-specs/index.md) |
| 跨模块实施计划 | [Execution Plan Policy](docs/PLANS.md) |
| 编码、测试、SQL、Git 和 Review 规范 | [Engineering References](docs/references/index.md) |
| 可靠性和安全保证 | [RELIABILITY.md](docs/RELIABILITY.md)、[SECURITY.md](docs/SECURITY.md) |
| Harness 规则维护和反馈 | [Harness Guide](docs/references/HARNESS_GUIDE.md)、[Harness Feedback](docs/feedback/HARNESS_FEEDBACK.md) |

## 许可证

本项目基于 GNU General Public License v3.0 only，详情见 [LICENSE](LICENSE)。
