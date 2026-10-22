# Design Documents

设计文档记录背景、备选方案、决策和后果，解释系统为什么采用当前形态；执行状态属于 `docs/exec-plans`。新设计遵循[技术设计文档规范](../references/DESIGN_DOC_GUIDE.md)并从[设计模板](TEMPLATE.md)开始。通过 `grill`、brainstorming 或其他访谈确认的选择必须写入“决策记录”，记录完整语义和影响，不能只保留 A/B/C 字母或依赖聊天历史。

命中[技术设计文档规范](../references/DESIGN_DOC_GUIDE.md)触发条件的工作，应先完成设计批准和独立 Review，再创建 execution plan 或实施代码；无需单独设计的工作可由已批准 Product spec 或 `docs/PLANS.md` 允许的其他事实源进入计划。active plan 必须链接一个实际存在的设计或产品规格；机械检查保证链接和结构存在，内容是否完整仍由 Review 判断。

## Current Designs

- [收尾流程精简实施设计](2026-10-22-closeout-process-simplification-implementation-design.md)：用户批准、独立设计 Review Approved，实施中；S1/S2 代码及聚焦验证完成，S3 政策同步与真实 full 验收未完成。当前合同为同冻结输入自测一次、reactor 一次、同轮专用报告校验及真实 HA 一次，不承诺实测耗时收益。

- [收尾流程收口与验收去重提案](2026-10-22-closeout-process-simplification-design.md)：用户已批准保留战术修复、延期阶段/归档平台；当前实施契约见上述精简实施设计，原审计与候选方案作为决策历史保留。

- [Routing HA 前检与有界清理](2026-10-21-routing-ha-bounded-runner-design.md)：已批准且战术修复保留；Engine 前检、命令边界、会话进程所有权和有界安全清理不随平台撤下。25 场景/900 秒不变，真实新流程验收尚未完成，不归因未知 Docker 内部故障。

- [Harness 归档差异审计](2026-10-21-harness-archive-audit-design.md)：已由精简实施设计取代／延期；不再提供 archive-audit、journal 或发布特权。历史设计与 Review 保留，当前仅采用受控的两时点状态内容 Review。

- [Harness 验证输入隔离与阶段恢复](2026-10-21-harness-verification-resume-design.md)：已由精简实施设计取代／延期；阶段链、恢复与实现输入指纹例外不属当前能力，完整 Git 输入冻结仍保留。历史方案与 Review 不删除，不晋级旧失败。

- [Bolt 连接层认证与统一 invoke 生产设计](2026-10-20-bolt-unified-invoke-design.md)：草案基线已审，reviewer `5151be08-386f-491` Accept-as-design-baseline；O1–O4 身份引导、证书路径截止、明文开发与部署/重连选择未决，禁止直接实施。供用户后续推进，不作为10-11旧计划的生产实现前置。

- [Bolt 连接层身份认证原型](2026-10-20-bolt-connection-authentication-spike-design.md)：用户批准、独立设计 reviewer `0461448d-b90c-462` Accept；仅测试源码验证握手/身份完成前业务发送屏障、地址+身份池隔离及重连并发，不切换生产调用链，不预设原型成功；纳入既有A5，生产方案待原型证据另行批准。

- [本机非线程所有权锁](2026-10-17-local-lock-design.md)：用户批准仅在 im-lock 实现 `LocalLock implements Lock`，非重入、跨线程释放、无 Registry/Template/Broker 接入；独立设计 reviewer `8bcc59eb` Accept，施工纳入既有 A5。

- 结构化健康采样与图文入门：见[共识成员边界设计后续决定](2026-10-16-consensus-membership-boundary-design.md)，用户批准 Context 使用 revision/topology/targets，删除字符串签名，并补 DDD/Raft/Gossip/异步观测说明；独立 reviewer `05870a5a` Approved/findings none，进入既有A5，其他Block保留。

- [共识成员模型边界与聚合一致性判断](2026-10-16-consensus-membership-boundary-design.md)：用户批准 RaftMembership 恢复 String 列表、Adapter 转 Broker 领域 ConsensusMembership、规则迁入 BrokerCluster.findConsistentVoters；独立 reviewer `e76a97c8` Approved/findings none，已实施容器分离；用户后续批准领域Set、同角色重复归一化/交叠构造拒绝和确定性观察签名，独立 reviewer `2939fb39` Approved，当前增量实施中。取代前一共享容器方向，MembershipEndpoint 仍在 common。既有 A5/WIP=1、Follower Block 保留。

- [共享成员端点值对象](2026-10-16-shared-membership-endpoint-design.md)：MembershipEndpoint 迁 common 的决定保留；typed RaftMembership 和直接列表传值已由[共识成员边界](2026-10-16-consensus-membership-boundary-design.md)取代，旧迁移证据保留在 A5，不冒充新模型验收。

- [Broker 自动故障观察与摘流接入](2026-10-16-broker-automatic-failure-observation-design.md)：用户已批准 voter 主动探测、Leader 拉取独立报告、具名自动摘流及全阶段恢复；采用重启/换届/拓扑变化重新等待完整冷却，不扩张 Raft 持久格式。独立设计 reviewer `c5f87b0c-0030-412` 首轮 Revise；已补入站唯一 URI SAN 来源保证、实际 membership 一致性闸门及恢复后冷却，第二轮 Accept后部分实施。用户规范纠正触发独立 reviewer `9de26e93` Revise八项，纠正设计第二轮Accept后仅修八项规范问题，结构/类型/映射和回归测试经短复审八项全部closed，恢复功能接入；配置/自动组合窄验证已执行，完整功能Review及HA/广门禁未完成。补齐原D22，不撤销自动摘流要求。最新结构补充已获用户批准、独立设计 reviewer `9f9c5cb3-857b-488` Accept、findings none：State/Topology 私有内嵌 AppService，删除 Voters Policy，将纯成员身份规则合并为现有领域服务的 `findObservationVoters`；外层显式读取实际 membership 并转换 typed List，保留重复检测与失败关闭。此次不改 JRaft/依赖，Follower 本机实际成员读取的能力 Block 保留。最新待审设计修正：将具体观察 Store 迁至精确 `support.health.BrokerHealthObservationTracker`（允许 Tracker 后缀），明确其观察状态是安全决策输入而非可选诊断，保留 domain 决策所有权；并单独登记 `beginCommandFrom` 转换合并的小型无行为维护。独立设计 Review 首轮 `59f3550d` Revise，仅有一项 minor：文件范围遗漏 `docs/references/CODING_GUIDE.md`；已补齐。第二轮 Accept、findings none；两项维护实施 reviewer `62f3d1db` Accept、findings none，阅读性 reviewer `6952162a` Accept。既有 A5/WIP=1、整体正式 Revise 和 Follower Block 保留，最终门禁失败归因见 A5。

- [Broker Transformer 与 Monitoring 归属](2026-10-15-broker-transformer-monitoring-boundaries-design.md)：用户已批准最终命名与精确 `support.monitoring` 技术观察例外；独立设计 reviewer `7cf18e69-3af8-449` Accept、findings none，进入实施。纳入既有 Adapter 计划 A5/WIP=1，不提交。

- [Broker 自有时间字段契约](2026-10-14-broker-time-contract-design.md)：用户已批准全部自有字段范围与无兼容决定；独立设计 Review 首轮 Revise；已补齐 Ops 文件/显式映射、runtime Duration、确定性重放反证与 UI string 迁移，专门 reviewer 第二轮 Accept、findings none，进入实施。Server/SDK 与 Gateway/Ops 直接消费者同步，内部 Instant xxxAt、数字 xxxAtMs/xxxMs、单调 nanos 保留，Raft v2 拒绝 v1；纳入既有 Adapter 计划 A5/WIP=1。

- [Raft 类型化命令接入](2026-10-12-typed-raft-command-design.md)：用户已批准，独立设计 Review 第二轮 Accept；原 v1 日志/快照决定已由 [Broker 时间契约](2026-10-14-broker-time-contract-design.md) 的 v2 决定取代；类型化接入与 Broker 支持代码按 codec/model/statemachine 归 support.raft 的职责决定保留；已纳入 Adapter 计划 A5 实施，最终门禁与交付状态见活跃计划。

- [BrokerClusterAdapter 职责重划](2026-10-11-broker-cluster-adapter-boundaries-design.md)：职责拆分与后续增量已实施，独立实现 Review 已执行；A5 仍 active，最终验收缺口见活跃计划。

- [路由容量 Driver](2026-10-10-routing-capacity-driver-design.md)：已批准；2026-10-10 用户确认容量定规格与 Broker 代码 Review 分轨。本地模拟计划已归档；真实生产容量不在本次交付范围。

- [Broker 路由恢复领域行为](2026-10-07-broker-route-recovery-domain-design.md)：用户已确认职责划分，T13 实施中；独立 Review 未执行。

- [Raft 通用运行能力抽取](2026-09-29-raft-plugin-design.md)：用户确认边界，插件及 Broker 接入已实施；独立 Review 和 Broker 全链路测试待完成。

- [Broker 权威集群状态与高可用路由切换](2026-09-19-broker-routing-consensus-design.md)：原设计通过四轮独立 Review；D17/D18 RouteLookup 和 Broker Bolt 责任链增量由 [Broker Review 收口计划](../exec-plans/completed/2026-09-20-broker-routing-consensus.md)完成代码/集成验收；本地 HA 模拟已归档。
- [IM 核心链路容量压测](2026-09-14-im-load-test-design.md)：设计已批准并通过三轮独立 Review 和用户委托主审；实施与真实压测已完成并按 completed execution plan 归档，记录宿主 k6、本机连接/消息容量探索、批次数据清理与测量安全边界。
- [可观测性与分片运行接入](2026-09-11-observability-sharding-runtime-design.md)：记录本轮 `$grill-me` 确认的数据库 `version` 字段、MyBatis-Plus 插件、ShardingSphere 配置、Prometheus、SkyWalking、端口治理和本地启动编排决策。
- [Harness 交接、启动与质量评审](2026-09-10-harness-handoff-startup-and-quality-design.md)：自动派生 session handoff、验证全部应用的受控启动路径、限界清理，并用独立 AI Reviewer 与机器证据生成模块 A/B/C/D 快照。
- [Harness 证据与运行闭环](2026-09-10-harness-evidence-and-runtime-loop-design.md)：让验证绑定当前 worktree，建立实时 E2E、行为目录、运行证据和 CI 生命周期。
- [执行工件模板融合](2026-09-10-execution-artifact-template-integration-design.md)：用唯一计划模板和 active plan 结构门禁吸收 DTPet 模板契约，不建立第二套 Harness 工作区。
- [Harness 运行门禁增强](2026-09-10-harness-operational-gates-design.md)：在现有验证入口中增加 readiness、clean、五子系统报告和 WHAT/WHY/FIX 诊断。
- [领域 README 与 Harness 优化](2026-09-09-domain-readme-and-harness-refinement-design.md)：由所属模块维护的领域语言、简明的根开发者入口、有边界的 Netty WebSocket 选型说明，以及面向生命周期的 Harness 改进。
- [Unified management console](2026-09-09-unified-management-console-design.md): shared quiet high-density Element Plus visual and interaction contract across independently deployed IAM, Admin, Audit and Monitor consoles, including local port convergence.
- [IAM console interaction](2026-09-08-iam-console-interaction-design.md): scan-first management shell, application detail context, OAuth Client list, drawer-based editing and recoverable operation feedback.
- [IAM Catalog client and OpenAPI convergence](2026-09-08-iam-catalog-client-and-openapi-convergence-design.md): dedicated permission-catalog machine credentials, Spring-side Claim type restoration and explicit MVC documentation paths.
- [Web OpenAPI and IAM Introspection](2026-09-08-web-openapi-and-iam-introspection-design.md): shared Springdoc ownership in `im-web`, authenticated IAM documentation and unambiguous OAuth Token reconstruction for default Introspection.
- [IAM management login](2026-09-07-iam-management-login-design.md): IAM-owned Vue login page, standard Spring Security form authentication, safe SPA continuation, saved OAuth request restoration, and CSRF handling.
- [IAM security hardening](2026-09-02-iam-security-hardening-design.md): resource-audience Introspection, consistent producer identity, atomic OAuth credential consumption, immediate SSO expiry, and login timing hardening.
- [Snowflake configured mode](2026-09-01-snowflake-configured-mode-design.md): explicit STATIC or REDIS selection in `im-identity`, mode-specific validation, and removal of business-owned Snowflake Bean factories.
- [Identity module ownership](2026-09-01-identity-module-ownership-design.md): complete Snowflake capability ownership in `im-identity` with optional Redis integration and direct runtime consumers.
- [Redis Snowflake machine ID](2026-09-01-redis-snowflake-machine-id-design.md): Redis-backed process leases for dynamic Snowflake machine IDs, generic plugin ownership, and IAM integration.
- [IAM and Monitor hardening](2026-08-30-iam-monitor-hardening-design.md): staged authorization correctness, IAM DDD convergence, bounded Monitor aggregation, and protocol/time boundary isolation.
- [Excel plugin boundary](2026-08-28-excel-plugin-boundary-design.md): generic Fesod streaming export lifecycle in `im-excel`, with business row models, querying, authorization and auditing retained by consumers.
- [Central management audit](2026-08-28-central-management-audit-design.md): independent Audit Server/SDK, Kafka or asynchronous HTTP ingestion, unified business and security audit storage, IAM-protected query, and bounded Excel export.
- [Web infrastructure boundary](2026-08-27-web-infrastructure-boundary-design.md): generic HTTP request metadata and `HttpResult` ownership in `im-web`, optional Session Servlet adaptation in `im-session`, and unified Admin/Monitor/IAM SDK response behavior.
- [Management IAM](2026-08-26-management-iam-design.md): centralized administrator identity, OAuth2/OIDC SSO, application-scoped RBAC, realtime introspection and bounded stale-cache fallback for management applications.
- [IM Admin DDD structure alignment](2026-08-26-im-admin-ddd-structure-alignment-design.md): canonical business-module layers, common pagination, CQRS boundary values and real Management Architecture coverage. Implementation completed.
- [Dynamic Harness feedback loop](2026-08-25-dynamic-harness-feedback-loop-design.md): mandatory feedback assessment, Review-only rule handling, and optional internal query-condition structure. Implementation completed.
- [IM Admin user management](2026-08-24-im-admin-user-management-design.md): independent administrator authentication, RBAC, audit, and Account-owned user administration. Implementation completed.
- [Documentation and Harness structure](2026-08-23-documentation-harness-structure-design.md): documentation ownership, stable engineering references, Harness enforcement boundaries, and current-fact cleanup. Implementation completed.
- [Review findings remediation](2026-08-23-review-findings-remediation-design.md): canonical Session locking, authentication lifetime alignment, refresh routing, chat unread behavior, Broker owner routing, and README context restoration. Implementation completed.
- [README and message notification documentation](2026-08-23-readme-notification-documentation-design.md): README ownership, end-to-end notification and ACK flow, receipt retry semantics, and current contract-name cleanup. Implementation completed.
- [Message chat view presence](2026-08-21-message-chat-view-presence-design.md): message-owned current chat view state and unread behavior. Implementation completed.
- [Centralized session authentication](2026-08-14-centralized-session-authentication-design.md): centralized online authentication, rotating refresh tokens, and single-device session replacement. Implementation completed.
- [Broker business monitoring](2026-08-13-broker-business-monitoring-design.md): separated Broker diagnostics, Monitor aggregation/UI, and future Admin ownership. Implementation completed.

## Historical Designs

- [Audit RPC ingestion](2026-08-30-audit-rpc-ingestion-design.md): withdrawn synchronous Dubbo audit transport; current Audit ingestion uses Kafka or authenticated asynchronous HTTP.
- [IM Admin declarative audit and security context](2026-08-26-im-admin-audit-aop-design.md): historical local audit-first design; audit ownership and failure semantics are superseded by the central management audit design.
- [Group member and chat boundary](2026-05-11-group-member-chat-boundary-design.md)
- [Open chat lifecycle](2026-05-12-open-chat-lifecycle-design.md)
- [IM module split](2026-05-20-im-module-split-design.md)
- [Architecture upgrade](2026-06-08-architecture-upgrade-design.md)

New documents use `YYYY-MM-DD-<topic>-design.md` and must link to their execution plan when implementation begins.
