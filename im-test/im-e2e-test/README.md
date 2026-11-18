# im-e2e-test

`im-e2e-test` 验证实时链路与 Ops 出站管理协议的跨模块网络行为。实时黄金旅程从真实 WebSocket 客户端进入，穿过 Netty Gateway、Bolt 和 Broker，并通过 Broker management HTTP 观察路由结果。Ops 管理契约使用正式出站客户端连接真实 Broker HTTP 服务。

Account 认证和 Message Facade 实现作为受控测试边界替换，因此本模块不证明 Account 数据库、Message MySQL/Redis、Nacos 或 Dubbo 注册中心可用。全栈环境 E2E 需要单独的环境与数据初始化方案。

## 黄金旅程

- 认证连接在 Broker 中建立正确的用户路由；
- 上行私聊发送和 ACK 到达 Message Facade 边界；
- WebSocket 断开后路由移除；
- Gateway 重启并重新连接后路由重建。

## 验证

```bash
./scripts/verify.sh e2e
```

## Ops → Broker HTTP 管理契约

`OpsBrokerManagementContractE2ETest` 使用 `OpsHttpBeans` 的正式 RestClient 配置和 `HttpBrokerManagementClient`，连接 `RANDOM_PORT` 启动的真实 Broker Controller、应用服务、领域模型和单 voter JRaft。测试不启动 Ops 入站运行时，不替代现有 Ops HTTP、权限或审计接缝测试，也不引入 Ops → Broker Server 生产依赖。

覆盖范围：

- `cluster-state` 读取真实 committed 拓扑；未知 operation 保持客户端失败，不伪造已登记操作。
- 五个成员写入口（`add-learner`、`complete-learner-sync`、`promote`、`demote`、`remove`）发送不匹配的 epoch，断言真实 `CONFLICT`、拓扑不变且没有登记 operation。fixture 初始 epoch 为 0，使用 `current + 1` 验证不匹配拒绝，不宣称已经验证历史过期 epoch。
- 将预置 `DISCOVERED` 节点实际加入离线 learner，查询 Raft 已提交的 `START_LEARNER_SYNC/SUCCEEDED` operation、毫秒时间和 epoch，重复提交同一请求得到 `DUPLICATE`，查询事实和拓扑保持不变。该成功只表示加入 learner，不表示离线节点已复制日志、完成同步或晋升。
- 使用两端一致的随机服务凭证，并对照错误凭证与正确凭证下的同一请求，证明真实 HTTP 服务凭证拒绝。凭证不输出到测试日志。Broker 当前通用异常响应隐藏具体原因：未知查询为参数错误，错误凭证为系统繁忙；测试不将其冒称为专用 HTTP 401/403。

Message Facade 使用 Mockito 替身；Redis/JetCache 使用本地 mock 缓存并排除 Redis 自动配置；Nacos、Dubbo 关闭。模块同时承载 IAM 旅程，聚合测试 classpath 包含 IAM/Audit SDK 与 IAM Server。此契约 fixture 显式关闭 IAM/Audit，并排除 datasource/MyBatis 与 sharding 自动配置，保持原替换边界；本测试不验证 IAM、Audit、共享数据库或外部中间件。Bolt 使用显式 synthetic-data DEV 模式，Raft/Bolt 使用动态端口，Raft 日志写入唯一 `im-chat-t9-ops-contract-*` 临时目录。Spring 上下文关闭网络和 Raft 资源；日志目录保留为任务证据，不复用于下一次运行。没有启动 learner 进程，因此连接离线 learner 的 JRaft 日志不表示测试失败；本测试不提供完整 learner 生命周期、重启恢复或 mTLS HA 证明。

该契约 fixture 为聚合类路径新增的 identity 自动配置显式提供 Snowflake `STATIC` 参数。经用户限定授权，仅此 Broker 测试上下文追加排除 `SecurityAutoConfiguration` 与 `ManagementWebSecurityAutoConfiguration`，隔离管理依赖引入的默认安全链；原管理写凭证拦截器、Session 规则及全部断言保留，不修改生产或扩展到 realtime fixture。补齐 identity 后首次读取仍 HTTP 401 的失败原件保留在 `.harness/t11/legacy-ops-contract-identity-attempt1/`；限定排除后的原类回归通过，证据 `.harness/t11/legacy-ops-contract-security-attempt1/`。该结果不替代完整 E2E 模块或冻结验收。

聚焦命令：

```bash
mvn -pl im-test/im-e2e-test -am -Dtest=OpsBrokerManagementContractE2ETest -Dsurefire.failIfNoSpecifiedTests=false test
```

## IAM CLI 治理旅程的聚合类路径边界

`OpsCliGovernanceE2ETest` 的 owned Broker 测试上下文在原 datasource/MyBatis 排除之外，仅额外排除 `org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration` 和 `org.springframework.boot.actuate.autoconfigure.security.servlet.ManagementWebSecurityAutoConfiguration`。本模块为真实 IAM／Ops 旅程引入的 Spring Security 与 Actuator 会在未声明安全链的 Broker 测试上下文创建默认认证链，导致正式 Ops 出站只读请求收到 HTTP 401。仅排除前者会使后者创建 `managementSecurityFilterChain` 时缺少 `HttpSecurity`；两项排除共同隔离聚合测试类路径额外引入的默认链，不修改 Broker 生产配置。

两项排除均不应用于 IAM 或 Ops 上下文，也不排除其他安全自动配置，不新增放行全部请求的安全链。Broker 原有管理写凭证拦截器与 Session Web 路径规则保持不变；IAM 登录／Introspection 和 Ops Bearer／BFF 安全链继续使用正式实现。两项排除后的聚焦旅程已实际验证启动、真实登录／换码、集群读取，以及事务停用 public client 后同一 CLI 下一次请求返回 HTTP 401；当轮输入稳定且 1 项测试无失败、错误或跳过，证据位于 `.harness/t11/iam-first-journey-attempt15/`。该结果不证明此上下文的管理写鉴权负例、成员操作或完整模块验收；外部 JSqlParser 的 JaCoCo 方法过大插桩错误仍待单独修复。

`invalidBrokerServiceCredentialIsRejectedWithoutMutationInIsolatedContext` 使用独立的同类真实 topology，验证两项限定排除后 Broker 原管理写凭证拦截器仍拒绝错误服务凭证。测试对现有 VOTER 发送 `add-learner`，错／对凭证复用完全相同的 command；错误凭证得到正式客户端错误，正确凭证到达领域校验并得到要求 `DISCOVERED` 的 `REJECTED`。每次请求后立即通过公开 HTTP 读取比较完整拓扑，并查询 operation 确认未登记，不使用内部 Raft 查询替代 HTTP 接缝。此用例不登录 CLI，不证明 CLI 写权限、成功提交、learner 生命周期或额外默认安全链；Broker 当前错误包装不是专用 HTTP 401／403。该用例是已有保护的新增覆盖，不制造行为 RED。首次运行因请求端点不符合目标已有绑定而在正确凭证对照阶段失败；改用目标实际绑定端点后，原断言不变，聚焦验证通过并取得独立窄范围 Accept。随后本类两个用例组合回归通过，证据分别位于 `.harness/t11/broker-write-credential-attempt2/`、`broker-write-credential-review.json` 与 `governance-two-slice-regression-attempt1/`。组合运行仍出现上述外部插桩错误，不代表覆盖采集完整或完整模块验收。

`authenticatedCliAddsLearnerQueriesCompletionAndRetriesOriginalRecord` 单独启用 write-journey fixture：只在该独立上下文追加一个 `DISCOVERED/INACTIVE` bootstrap 节点，不改变默认单 Broker 拓扑。IAM MySQL 中持久化写权限及角色关联，PUBLIC Client 允许的 scope、真实授权请求 scope 与 Ops 允许 scope 同时包含读写权限；不伪造 Token 或绕过权限检查。

早期验收版本通过同一真实登录的 CLI 执行 `submit`、`queryOperation` 和显式 `retry`，验证 `APPLIED`、`START_LEARNER_SYNC/SUCCEEDED` 及同身份 `DUPLICATE`；当前版本的第二管理员接续见下文 B4 增量。加入后的节点仍为 `LEARNER_SYNCING/INACTIVE`，不表示 learner 已完成复制、同步或晋升。操作记录由正式客户端在 home 下本次独享的 `0700` 目录中创建为 `0600` 文件；生产目录信任检查保留，既有 home 权限不修改。重试前后校验记录原字节、业务字段、完成 operation 与完整拓扑不变；清理仅删除本次创建的精确记录和目录。该切片在首次接收节点与业务目标解耦后，第三次聚焦运行已通过，原件 `.harness/t11/cli-submit-query-retry-attempt3/`；前两次提交失败保留，不追认为生产行为 RED。路由与该旅程已取得独立窄 Accept，记录 `.harness/t11/receiver-routing-final-review.json`。该单 voter 旅程不证明 Follower 的 Raft PeerId→管理 HTTP origin 映射正确；外部 JaCoCo 插桩问题及完整模块／冻结验收仍由活跃计划跟踪。

### Learner 确认的后台恢复

`authenticatedCliRetriesCommittedLearnerBeforeSchedulerCompletesIt` 使用真实 CLI、Ops HTTP、Broker Raft 和 learner。测试夹具仅在首次提交和重试期间暂停成员定时扫描，并在 Raft 已提交拓扑后拒绝终态提交。CLI 查询看到 `RAFT_COMMITTED`；用原记录重试返回 `DUPLICATE`，记录字节和操作事实均不变。恢复扫描后，正式 scheduler 提交终态，CLI 查询同一操作达到 `SUCCEEDED`。该场景不验证进程崩溃重启，也不替代进程内 `PENDING` 恢复测试。

### 三 voter 的真实 Follower 单跳与拒绝

B4 增量测试保留真实 IAM/MySQL/Redis、PKCE 和 Introspection：独立只申请读 scope 的管理员仍持有写 authority，提交及接续写均拒绝、Broker 收件为零且 operation 未登记；同一管理员重新申请完整 scope 后可显式接续原记录。成功加入 learner 的旅程由第二位持久化管理员独立登录后接续，核对原记录字节和 operation 不变，并有界等待 AuditTransport 中两位实际管理员、可信 Client、operationId、epoch 与动作。这里的审计证明限 SDK 投递事件，不证明 Audit Server 持久化。新增 focused 套件及 affected 同轮报告均为 8 项通过，独立 B4 限定 Accept 见 `.harness/t11/b4-gap-fix-20261106/b4-final-review.json`；不沿用旧 XML，不替代最终 full／真实容器 HA。

`authenticatedCliFollowsRealFollowerToThreeVoterLeader` 使用真实三 voter、IAM 登录及正式 CLI／Ops／Broker HTTP：首次接收者在收件时为 Follower，实际返回 `NOT_LEADER`，完整 hint 与实际 Leader 的官方首次 registration 捕获值一致；第二次收件者为 Leader，最终 `APPLIED`。恰两次网络写保留同一 operationId、expectedEpoch、payload、scope 和业务目标。

`authenticatedCliRejectsFollowerHintWhenLeaderMetadataIsMissing` 与 `authenticatedCliRejectsFollowerHintWhenLeaderPairingConflicts` 只在发现边界分别删除 Leader metadata、追加同完整 PeerId 的冲突管理 origin，不伪造实际 Node 身份或返回 hint。两者实际收到首次 Follower `NOT_LEADER`，CLI 保守 `UNKNOWN`，恰一次网络写、零 follow。发现替身只转交官方 registration 捕获值及上述明确负向变异；官方 NacosRegistration／ServiceRegistry 路径真实执行，NamingService 受控，不证明真实 Nacos 网络注册。

请求 filter 保留真实计数，默认跳过 async dispatch，避免双计；ResponseBodyAdvice 只观察返回对象并原样返回，不包装、消费或提交响应，不记录凭据／原始响应。当前响应关联仅适用于每 Broker 一次 POST 的本旅程，不是通用重试观察器。资源仍限本任务独享 MySQL／Redis 与已授权 Broker 上下文，不访问共享库、其他容器或已有卷。

成功旅程通过后顺序补齐两拒绝片，最终本类 6 项全零、输入稳定，证据 `.harness/t11/leader-hint-governance-class-regression-attempt1/`；独立窄 Accept 为 `.harness/t11/leader-hint-three-voter-review.json`。此前响应 wrapper、advice 顺序及 CLI 状态预期的 fixture 失败保留，不伪称生产 RED；此结果不关闭整体 T11、完整模块、覆盖或冻结验收。

## Bolt 开发目标与真实边界

黄金旅程也验证真实 Broker → Gateway 下行、session close 清理和重连。fixture 在固定实际 DEV 模式下，用真实预绑定端口生成 D12 部署描述配对，再启动正式 Broker/Gateway context；不伪造 TLS principal，不覆盖原注册/租约事实。所有调用仍是 typed 目标，配对仅作 pool/routing descriptor，开发不发有效观察票、不自动摘流。该旅程不证明 mTLS 安全 HA；后者由 routing-ha 独立拥有。

`RealtimeMessagingE2ETest` 的 Broker 与独立 Gateway 显式提供 Snowflake `STATIC` 参数并使用不同 machineId。经用户单独限定授权，仅该类 `@SpringBootTest` Broker servlet 上下文排除 `SecurityAutoConfiguration` 与 `ManagementWebSecurityAutoConfiguration`，隔离聚合管理依赖带入的默认安全链；独立 Gateway 为非 servlet 运行时，不追加安全排除。生产配置、Session 规则、Broker 管理写鉴权和原旅程断言不变。补齐 identity 后首次 Broker `/management/connections` 读取 HTTP 401 的失败保留在 `.harness/t11/realtime-identity-attempt1/`；限定排除后原旅程聚焦通过，原件 `.harness/t11/realtime-security-attempt1/`。该 fixture 恢复已取得独立窄 Accept，原件 `.harness/t11/realtime-fixture-review.json`。两旧 fixture 恢复后的无 selector 模块回归 exit0、输入稳定，E2E 自身 13 项全零，原件 `.harness/t11/e2e-module-regression-attempt2/`；上游条件跳过不计真实数据库验收，外部 JSqlParser JaCoCo 方法过大错误仍在，不代表覆盖完整或冻结验收。

消费方 startup 只隔离外部设施，不替换正式受控客户端/SDK 生命周期；模块运行与完整验收以实际同指纹报告为准，不能以 stub、静态配对检查或 create/inspect 当业务已启动。
