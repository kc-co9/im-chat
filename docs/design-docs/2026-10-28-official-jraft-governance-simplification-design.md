# Broker 治理职责简化与官方 JRaft 迁移设计

> 状态：已批准。2026-10-30 用户最终确认继续；独立设计复核 Accept，findings none。历史 Block 与修订记录保留。本次批准覆盖与[自有 CLI 安全设计](2026-10-30-ops-cli-authentication-design.md)及[治理迁移设计](2026-10-28-official-jraft-governance-simplification-design.md)组合后的最终方案；允许创建执行计划，不代表运行验收。实施计划：[治理迁移与自有 CLI](../exec-plans/completed/2026-10-30-official-jraft-and-ops-cli.md)。

## 摘要

当前 Broker 业务治理依赖私有 JRaft 补丁的 `getAppliedConfiguration`。观察资格、Peer 准入和成员维护恢复都把「本机已应用的完整 Raft 成员配置」当作业务判断前提。结果是通用 `im-raft` 承载了一项非官方能力，业务协议也要求了框架内部才有的证明。官方示例的用法是：业务定义状态，`Node.apply` 提交命令，`StateMachine.onApply` 修改状态，需要一致读时调用 `readIndex`；没有要求应用读取本机已应用成员配置。

核心方案分四点：

1. **观察资格由已提交业务状态决定。** 观察者集合取已提交 Broker 状态中的 VOTER 角色节点，不再逐项比对真实 Raft voter 列表。故障阈值是业务策略；共识多数由 JRaft 在每次业务状态提交时负责。
2. **显式成员维护改为提交持久化目标配置序列。** 登记操作时确定每一步的完整目标配置，由 Leader 调用官方 `changePeers(目标)`。晋升和降级必须拆成先移除旧角色、再加入新角色两步，不能依赖一次调用完成角色转换。官方 BUSY 和同目标 OK 只支持局部重试，不能证明操作最终完成，也不能阻止旧执行者覆盖后续配置。
3. **Leader 侧做漂移诊断。** Leader 用官方 `listPeers`/`listLearners` 比对实际配置与业务期望配置。不一致时告警，并拒绝新的成员维护；自动路由接管不受影响。
4. **`im-raft` 只保留官方能力的通用封装。** 删除 `membership()` 与单步增删接口，新增 `changeConfiguration` 和 `leaderConfiguration`；依赖切回官方 `jraft-core`，删除补丁与 recipe 的有效入口。

关键取舍：不再证明「观察者恰好是本机已应用配置中的 voter」。业务记录与 Raft 实际配置的差异改由 Leader 诊断显式暴露，而不是在每个节点上隐式取消观察资格。2026-10-29 用户不接受把旧执行者迟到调用作为首版残余风险，要求先补齐防护设计；单一调用路径和漂移诊断不能充当该防护。防护未通过审查前，目标配置迁移不得实施。

发布边界：尚未上线，不考虑旧版本兼容，不支持混跑；新格式自身的重放和恢复必须可靠。

## 背景

补丁的唯一直接调用点是 `im-plugin/im-raft` 的 `RaftRuntime.membership()`。Broker 侧有以下消费点：

| 位置 | 用途 |
|---|---|
| `BrokerClusterAppService.probeFailures` | 判断本机是否为观察者；探测完成后重验 Context |
| `BrokerClusterAppService.reconcileFailures` | 观察者集合、阈值分母；ReadIndex 后重验 Context |
| `BrokerClusterAppService.probeBroker` / `getHealthReport` | Follower 服务端校验调用方观察资格 |
| `BrokerPeerAdmissionInterceptor.validateObservation` | 入站观察请求的调用方与报告方资格 |
| `BrokerMembershipAppService.checkLearnerProgress` | 确认 learner 已进入共识配置 |
| `BrokerRaftAdapter.addLearner/promoteLearner/demoteVoter/removeMember` | 先读配置判断已完成步骤，再调用单步变更 |

前五处都经 `BrokerCluster.findConsistentVoters(ConsensusMembership)`，或直接读取 `ConsensusMembership`。观察 Context 通过 `BrokerCluster.topology(membership)` 把实际配置也纳入比较。

为什么现在处理：用户确认 D9，要求删除私有补丁，并把职责退回「业务负责业务治理，框架只提供通用共识能力」的边界。

## 目标与非目标

- 目标：
  - 删除私有补丁和 recipe 的有效入口，`jraft-core` 回到官方坐标 `com.alipay.sofa:jraft-core`。
  - 保留自动故障摘流和路由接管（D1）；失去多数派时停止需要共识授权的操作（D2）。
  - 保留 fencing/quarantine、Lease、epoch 与消息语义（D5、D6）。
  - `im-raft` 只暴露官方公开能力的通用封装，不含 Broker 语义（D9）。
- 非目标：
  - 不重写路由算法，不调整 Lease/quarantine 时长、消息幂等或 Gateway 协议；附录 C 的 G1 问题维持现状，不随本迁移变化。
  - 不新增通用执行平台；旧执行者防护只围绕成员维护协议展开（D11 已取代 D8 的暂停边界）。
  - 不新增在线成员管理 API，不自动删除 voter（D3）。
  - 不承诺 RTO 数值（D4）。

## 已知事实与约束

- 所属模块：`im-plugin/im-raft`（通用）、`im-broker/im-broker-server`（业务）、私有制品构建（recipe、POM 坐标、Harness 规则）、`im-test`（HA/E2E）。
- 官方 API 事实，来源为固定 commit `cb1a0c7ac16ff146ad8abb04b1bcb035270bb234` 的已核验归档（SHA-256 `6c43b6b0…4a05`，见附录 C）；行号对应该归档中的 `NodeImpl.java`：
  - `unsafeRegisterConfChange`（约 2495 行）：非 Leader 返回 EPERM（领导权转移中返回 EBUSY）；`confCtx.isBusy()` 时返回 EBUSY；`conf.getConf().equals(newConf)` 时立即 OK；否则启动变更。
  - `changePeers`（约 3166 行）只检查非空，即进入上述逻辑。`addPeer`/`removePeer`（约 3136/3151 行）有「已存在/不存在」前置断言，重试会抛异常，不适合幂等重试；`addLearners`/`removeLearners` 无此断言。
  - `Configuration.equals` 同时比较 learners（`LinkedHashSet`）与 peers（`ArrayList`，**顺序敏感**）；`isValid` 要求 peers 非空且与 learners 不相交。
  - `ConfigurationCtx.start`（约 365 行）计算 adding/removing；新 voter 先追赶日志，再经 joint 进入 stable，done 在 STABLE 后回调。
  - `listPeers`/`listLearners`（约 3084/3110 行）仅限 Leader，返回 Leader 的最新配置；变更进行中可能是尚未提交的新配置，两次调用也不构成原子快照。
  - `resetPeers` 不经共识，禁止用于本设计的任何路径。
  - `RaftOptions` 默认 `ReadOnlySafe`，本应用未覆盖。
- 当前业务门：`hasBlockingOperation` 使未终态操作阻止新操作登记；`findConsistentVoters` 在任何未终态成员变更期间返回空集合。
- 兼容约束：成员维护 operation 新增字段会改变 Raft 命令编码和快照格式。

## 方案概览

```text
          已提交 Broker 业务状态（经 Raft 提交；同一 revision 下各节点一致）
                               │
        ┌──────────────────────┼─────────────────────────┐
        ▼                      ▼                         ▼
   观察者集合              成员维护 operation          路由 / Lease / epoch
 （VOTER 角色且已绑定端点）  （含完整目标配置）             （不变）
        │                      │
 Follower 服务端、Leader     Leader: changePeers(目标)
 决策、入站准入共用            逐步受理；未知结果不承诺收敛
                               │
                     Leader 漂移诊断：listPeers / listLearners
```

不变量：

- **I1** 观察资格和故障阈值分母只取已提交业务状态在请求 revision 下的值；请求方与接收方以 revision 绑定同一视图。
- **I2** 自动接管的每个状态转换仍经 Raft 提交（expectedRevision 加同任期 ReadIndex 证明），多数派由 JRaft 负责；观察报告不替代任何一次提交。
- **I3** 成员维护的外部配置调用只由 Leader 发起；目标配置在登记时确定并随 operation 持久化，重试只能提交同一目标。
- **I4** 业务期望配置与 Raft 实际配置不一致时，不发起新的成员维护；自动路由接管不读取 Raft 实际配置。
- **I5** `im-raft` 不出现 Broker 语义，不暴露 JRaft 内部配置历史。

## 详细设计

### 1. `im-raft` 接口

删除：

- `membership()`，以及 `RaftMembership` 的「本机已应用」语义。
- `addLearner`、`removeLearner`、`addVoter`、`removeVoter`。

新增：

- `RaftConfiguration(List<String> voters, List<String> learners)`：框架无关公共模型，构造时防御复制、按输入字符串去重排序，拒绝 null/blank、空 voters 及字面角色交集。JRaft endpoint 解析、别名规范化去重及规范化后的角色交集由 Runtime/core 在 Node 启动或配置调用之前统一校验；初始和目标使用相同转换规则，不在 model/spi 中依赖 PeerId。模型构造成功不代表原生 endpoint 已有效。
- `CompletionStage<RaftConfigurationResult> changeConfiguration(RaftConfiguration target)`：仅限 Leader，直接同步进入 `Node.changePeers`，不在 Runtime 内排队，不作自动重试。返回只读的底层完成观察，不对底层 Future 使用 `orTimeout`，也不把调用方取消传播到原生工作。`RaftConfigurationResult` 记录 `APPLIED / BUSY / NOT_LEADER / UNAVAILABLE`、原始错误码、leaderEndpoint 与 message；它是单次请求结果，不声称配置全局稳定或许可排空。正常返回只证明同步原生入口已退出，底层完成还须单独观察。调用方的有界等待使用派生 Future，不能修改此完成观察。同步异常保留为未知，除非能证明发生在调用原生方法之前；未知结果不得排空。
- `RaftConfiguration leaderConfiguration()`：仅限 Leader，读取 `listPeers` 和 `listLearners`，非 Leader 时抛 `IllegalStateException`。注释写明：两次读取不是原子快照，变更进行中可能包含未提交配置，只能用于诊断和失败即关闭的前置检查。
- 初始配置按 `RaftConfiguration` 的同一规则排序构造，保证后续同目标调用能命中 `equals` 立即返回 OK（V3）。

保留：`submit`、`confirmLeadership`、`fetchQuorumReadIndex`、`snapshot`、`isLeader`、`localLeaderTerm`、`leaderEndpoint`。

`Task.expectedTerm` 不在本迁移范围，延后处理。

### 2. 观察资格

| 入口 | 现状 | 迁移后 |
|---|---|---|
| `BrokerCluster.findConsistentVoters(ConsensusMembership)` | 业务角色与实际配置逐项一致才返回 voter | 改为 `findObservers()`：返回已提交状态中 `membershipRole=VOTER` 且已绑定成员端点的 Broker；存在 `LEARNER_SYNCING` 或未终态成员变更时仍返回空（保持完整门禁，见 D10）；不再读实际配置 |
| `BrokerCluster.topology(membership)` / `BrokerClusterTopology.membership` | Context 含实际配置 | 删除 membership 字段，Context 为 revision 加已提交拓扑 |
| `probeFailures` | 实际 voter 才探测；探测前后比较 Context | 改用 `findObservers` 和新 Context |
| `reconcileFailures` | 实际 voter 作为分母；ReadIndex 后重验 | 同上；同任期 ReadIndex 证明保留 |
| `probeBroker` / `getHealthReport` | Follower 校验调用方是实际 voter | 改用 `findObservers` |
| `BrokerPeerAdmissionInterceptor.validateObservation` | 调用方与报告方是实际 voter | 改用 `findObservers`；入站前后 revision/epoch 一致检查保留 |
| `validateControl` | 不读实际配置 | 普通 CONTROL 不变；增加仅供精确许可持有者提交排空确认的专用认证分支（§3） |

阈值维持现有 `ObservationQuorum` 规则：超过观察者集合的一半，排除目标自报；分母取完整观察者集合，不按响应数缩小。

安全论证：

- 观察者集合由已提交日志决定，任意节点在相同 revision 下得出相同集合。请求携带 revision，接收方 revision 不同即拒绝，不会出现「双方对资格看法不同却都接受」。
- 观察报告只形成候选目标。之后的 Begin、fence 和新拓扑都经 Raft 提交，并受 expectedRevision 保护（现有机制）。
- 节点被移出业务 VOTER 角色后，revision 随之前进，旧资格失效。

与现状的差异：现状额外要求实际 Raft 配置与业务记录一致，迁移后不再要求。具体后果是：外部 CLI 修改了 Raft 配置而业务状态未变时，现状会让所有节点的观察资格失效，自动接管随之停止；迁移后接管继续，漂移由 Leader 诊断暴露（§4）。这是有意取舍，符合 D3/D9 的职责分离，也避免运维漂移关闭自动接管（D1）。

### 3. 显式成员维护

涉及操作：加入 learner（`START_LEARNER_SYNC`）、晋升、降级、移除（`REMOVE_BROKER`）。

- **登记**：聚合根据已提交状态计算完整目标配置 `targetConfiguration`，写入 `BrokerClusterOperation`；该字段进入命令编码和快照。期望配置的计算规则是：voters 为 VOTER 角色节点的端点，learners 为 LEARNER 与 `LEARNER_SYNCING` 节点的端点，再叠加本次操作的变化，最后规范化排序。
- **推进**：首次执行与 `resume` 走同一路径，由 Leader 调用 `changeConfiguration(target)`：

  | 返回 | 处理 |
  |---|---|
  | `APPLIED` | 记录当前调用结果；仍须不可逆封闭、排空并提交许可排空事实，之后才可推进下一步或 `RAFT_COMMITTED` |
  | `BUSY` / 超时 / `UNAVAILABLE` | 保持 `PENDING`；只能针对同一 operationId 和当前持久化步骤恢复。官方同目标 OK 不证明最终收敛；失去多数派、目标永久不可达或遗留执行都可能阻止完成。D11 的防护协议未通过审查前，不批准具体重试实现 |
  | `NOT_LEADER` | 只证明当前调用被拒绝，不转移许可；持有者封闭并确认全部原生工作结束后，才可经共识许可后续恢复 |

- **晋升/降级**：采用两个持久化完整目标，先移除旧角色，再加入新角色；禁止单次 `changePeers` 跨角色转换。官方 1.4.1 在配置执行开始前校验角色交集：完整跨角色目标在 joint entry 校验被拒绝，向已有 learner 调用 `addPeer` 则因新配置自身交集被拒绝；两者都不能用作进入配置阶段后 replicator 失效的证据。必须记录当前步骤，每步结果未知时不得跳到下一步，两步之间超时、换届和重启均从持久化步骤恢复协议协调，但恢复不等于重新授予原生执行权，仍须遵守许可排空前置条件。过期执行防护尚未完成设计（D11），本段只是协议约束，不是已批准的实现方案。
- **Adapter**：删除所有 `membership()` 前置读取。
- **learner 进度检查**：`checkLearnerProgress` 中的 `getConsensusMembership().learners().contains` 改为 `leaderConfiguration().learners().contains`；该方法本来就要求本机是 Leader。
- **失去多数派**：`changePeers` 无法完成，操作保持 `PENDING`，与现状一致（D2）。
- **互斥**：保留 `hasBlockingOperation`；不在 FSM monitor 内调用 JRaft。成员步骤另受下述不可自动转移的执行许可约束，不能只靠 operation 登记互斥。

#### 仅确认 learner 同步的操作

`COMPLETE_LEARNER_SYNC` 不修改原生配置，不创建目标步骤或执行许可。保留 `confirmLearner` 的独立证据流程：校验 operationId/digest、目标身份、绑定端点和 expectedEpoch，检查目标在 Leader 查询的 learners 中，取得同任期 quorum ReadIndex，通过已认证的目标查询确认 applied index 不小于该 ReadIndex。配置查询只是前置检查，不是已应用配置证明。新操作只在证据成立后，以读取时 revision 提交 Begin；状态机 CAS 拒绝取证期间的版本变化，不能把空步骤当作同步成功证据。Begin 登记表示可信应用流程已完成证据检查，不新增对外直接构造 Begin 的入口。

此分支保留原 revision 规则：Begin 将接受 revision 加一，Commit 核对原 acceptedRevision 及当前 `acceptedRevision.next()`，再推进 revision 并将 `LEARNER_SYNCING` 改为 READY；既有终态推进规则保持。它不进入原生配置操作的新 Commit 分支。已登记同 operationId/digest 的重试及快照恢复复用已提交 Begin，不重新创建许可或调用原生配置 API；尚未登记成功的请求须重新取证，不盲目续用旧证据。重复 Commit／终态推进幂等，不重复修改节点状态。D10 只在没有未终态成员操作且没有 `LEARNER_SYNCING` 时解除；确认失败或未发生时仍可长期暂停，Scheduler 不代替显式确认。验证覆盖加入成功 → 显式确认 → READY → 终态 → 解除门禁，以及证据不足、版本冲突、重复确认和 Begin 后重启恢复。

#### 执行许可候选：先封闭排空，再允许后续目标

2026-10-29 用户确认 D16 的可用性取舍；以下精确协议已取得后文记录的独立设计 Accept 与 2026-10-30 用户最终批准，尚未实施验证。它不试图让官方 API 校验业务 operationId，而是禁止后续步骤越过仍有执行能力的旧许可。

- **持久化身份**：每次登记均生成不可复用的 permitId，绑定 operationId、stepId、固定目标、executorBrokerId 和进程 incarnation；同一步同目标重试若重新登记许可，也必须使用新 permitId，防止迟到排空确认产生 ABA。incarnation 不能仅用 brokerId，进程重启必须产生新身份。许可由共识登记，新 Leader 不因换届改写持有者；所有确认必须匹配完整 permitId 及绑定事实。
- **本地准入**：持有者为许可维护 OPEN → CLOSING → DRAINED 的单向状态。准入与封闭在同一本地短临界区原子互斥；任何能在未来调用原生 API 的任务必须先登记 in-flight，不能先拿到 Runnable 再登记。离开临界区后才调用 JRaft，不持有该锁、FSM monitor 或领域锁等待 Node 写锁、closure、ReadIndex 或提交 Future。状态机应用只更新持久化事实，不等待本地排空。
- **单一在途调用**：同一许可最多一个原生成员调用在途；请求超时只结束请求等待，不删除 in-flight，不替换底层完成通知，不重新发起未知调用。`im-raft` 必须保留真实原生完成信号，不能把被 `orTimeout` 改写的 Future 当作排空证明；技术完成观察采用 §1 的 `CompletionStage<RaftConfigurationResult>`，接口契约正在独立 Review，尚未授权实现。
- **封闭条件**：发起封闭后永久拒绝旧请求、Scheduler、重试及 continuation 的新准入；不得重新开放该 step。必须确认所有已准入同步入口退出、所有尚能调用的任务解除执行能力、所有原生异步变更明确结束，且后续 callback 不会继续发起旧调用。相同配置查询、超时、失去 Leader 或进程不可达都不构成此证明。无法分类的原生结果一律保持未排空，不用猜测放行。
- **确认与推进**：持有者只有在本地不可逆 DRAINED 后，才能请求当前 Leader 提交排空事实。状态机校验完整许可身份及步骤；重复确认幂等，旧 step/incarnation 的确认不能释放新许可。排空只是安全前提，不是业务成功：目标明确成功且排空事实已提交，才能推进下一固定目标或 `RAFT_COMMITTED`；目标未成功但明确排空，只能经状态机单一共识转换重新登记同一步、同一固定目标的许可，或进入已有具名失败处理。结果未知或未排空时，不得推进、移交或成功结束；不能以调用方提交一个 drained 布尔值放行。结束操作或登记后续操作还须满足原有领域成功／失败条件，不能仅凭排空放行。
- **换届与自移除**：持有者失去 Leader 后不得新调用，但仍可封闭和收集原生完成信号，并将排空事实交给新 Leader 提交。新 Leader 不能替持有者推断排空；自降级或移除当前 Leader 同样遵循此规则。确认投递复用认证 CONTROL 边界，身份须与许可持有者及 incarnation 匹配，不对外开放通用修复能力；请求与命令契约见下节；身份例外仅用于当前绑定许可的结束确认，不用于发起成员变更。
- **失联与重启**：许可持有者失联、原生结果未知、封闭后尚未提交确认即崩溃，均可能无限期阻塞维护。重启不能复活旧许可；换届不自动续租或超时抢占。外部 fencing 必须证明旧执行域及原生工作已不再有执行能力，并处理未结束配置变更，不能用心跳失败或等待时长代替。本次不新增 fencing/强制解锁 API；未有批准恢复流程时保持 blocked。
- **与业务门禁组合**：正常情况下仍支持在线维护；异常许可阻塞导致 operation 未终态时，D10 同时暂停自动观察与接管。D16 明确接受该长期暂停可能性，不表示执行协议已经获批。

原生结果按四个维度分别记账，不能把单次 closure 直接映射为许可排空：

| 结果 | 同步调用能力 | 本次原生工作 | 目标成功 | 许可排空 |
|---|---|---|---|---|
| 同目标 OK 或提交完成 OK | 原生方法返回前仍记 in-flight；回调先到也不提前退出 | 只记录该请求的完成证据，不替其他调用背书 | 可记录该请求成功；仍须核对所属许可、步骤和结果来源 | 必须另行永久封闭并检查全部任务、入口及原生工作 |
| BUSY | 同步返回后才能结束本次入口记账 | 本次未启动新变更；使其 BUSY 的其他原生工作不因此结束 | 不成功 | 不能直接确认排空 |
| NOT_LEADER / EPERM | 同步返回后才能结束本次入口记账 | 可能入口拒绝，也可能已启动上下文因失权 reset；只收到状态码不足以证明原生工作全部结束 | 不证明成功或配置稳定 | 未能证明所有原生工作结束时保持 blocked |
| 请求超时、同步异常、丢失回调 | 不按包装 Future 完成删除记账 | 发生位置与原生受理情况无法证明时为未知 | 未知 | 不确认排空、不移交 |

排空逻辑必须同时检查永久封闭、同步入口全部退出、所有准入任务失去未来调用能力、原生工作明确结束，以及 callback 不再生成旧调用。不能把同目标 OK、BUSY、NOT_LEADER 或任一单次结果单独作为该合取条件。若固定官方 API 无法提供某一路径所需证明，该路径按 D16 永久阻塞，不能以配置查询相同推断结束。

证明目标：若旧调用仍可能发生，则许可未排空，后续目标不能合法开始；若后续目标已开始，则旧许可已有不可撤销排空事实，旧调用已无执行能力。该论证依赖准入、封闭及原生完成信号契约全部成立，不能仅凭持久化一个 owner 字段宣称满足 D11。

必需反例：暂停已准入线程后换届，验证后续目标被阻止；封闭与准入竞争；请求超时但原生调用仍活跃；成功 callback 与同步返回乱序；迟到 callback 不重开许可；持有者自移除后跨节点提交确认；确认前崩溃及 incarnation 重用拒绝；FSM 队列压力下仍可应用业务日志。最终断言真实成员配置未被旧目标回退，而不只是业务 Commit 拒绝。

#### 许可命令与确认接口（设计复核已通过）

以下名称固定职责和数据契约，具体文件由批准后的 execution plan 列出；不引入通用执行平台。

线程边界：同步原生成员入口不得运行在 FSM callback、状态机应用线程及其同步 continuation 上。Broker 配置层装配专用受控执行器，应用服务依赖注入的执行能力；固定一个工作线程、一个有界等待槽，拒绝时禁止 caller-runs、内联执行或 common pool 回退。Runtime 仍直接同步进入原生 API，不增加内部队列。任务获得未来调用能力前须在短许可互斥内计入准入任务，排队任务同样阻止排空，重复调度不得再次入队。执行器拒绝时解除该任务的未来执行能力，记录未进入原生入口，再按完整封闭／排空条件处理，不推断其他工作结束。关闭先封闭准入并撤销排队任务能力，已有工作仍计入在途；不得在 FSM 线程等待退出，也不得另起替代线程越过卡住的旧许可。确定性验证须构造登记 Future 在 FSM 线程完成、Node 写锁持有者向满 FSM 队列投递 LEADER_STOP 的时序，断言 FSM 线程不等待原生入口，并覆盖拒绝、排队取消和关闭竞争。

| 所有者／入口 | 契约与状态机约束 |
|---|---|
| `BrokerCluster`／`RegisterMembershipPermitCommand` | 请求携带 operationId、stepId、permitId、executorBrokerId、executorIncarnation、expectedRevision；目标只从已登记 operation 的固定步骤读取，不接受请求覆盖。聚合要求 operation 为当前非终态成员操作、步骤未完成、没有未排空许可；首次或同一步失败重试才允许新许可。相同 permitId 与相同事实重复登记返回原事实，身份冲突拒绝。stepId 与 permitId 均随新格式日志／快照保存。 |
| `BrokerMembershipAppService`／本机执行 | 首次请求、resume、Scheduler、continuation 都只通过同一个成员许可执行入口。只有许可已提交且绑定本进程 incarnation 才能创建本地执行槽；每个 permitId 至多创建一次，封闭槽不能重建。本进程内保留封闭记录，不按 TTL 或容量驱逐；容量准入见下文。进程重启产生新 incarnation，拒绝恢复旧执行能力。异步回调只能记录结果或请求提交确认，不能直接再次调用原生成员 API。 |
| `BrokerCluster`／`ConfirmMembershipPermitDrainedCommand` | 完整匹配 operationId、stepId、permitId、executorBrokerId、executorIncarnation，附固定目标摘要及 `TARGET_APPLIED / TARGET_NOT_APPLIED` 结果；聚合拒绝 UNKNOWN，且只处理当前许可。首次确认持久化排空和步骤结果；完全相同的重复确认幂等，矛盾结果拒绝。目标成功且排空才可成功推进，未成功且排空仅允许同一步同目标登记新 permitId。确认不会自动变更业务拓扑，也不能以通用 phase advance 绕过。 |
| `BrokerPeerAdapter`／`ConfirmMembershipPermitDrainedParams` | 新增专用 `CONFIRM_MEMBERSHIP_PERMIT_DRAINED` typed CONTROL 操作。请求含 clusterId、callerBrokerId、receiverBrokerId、上述完整许可身份、targetDigest 和结果。响应明确区分已提交、重复已提交、非 Leader、拒绝、未知；请求超时以同一许可及结果重复提交，不再次执行成员配置。当前 Leader 将已认证身份附为命令执行上下文，再提交共识确认，RPC handler 不直接修改状态。 |

确认入站要求 MTLS BROKER 身份，认证 URI 的 brokerId 必须等于 callerBrokerId 及许可 executorBrokerId；禁止 DEV/TRUSTED_NETWORK 作为远程排空证明。校验 clusterId、receiver、参数类型及 operation 能力；当前 Leader 在一致读后校验持久化许可并提交，最终以状态机许可 CAS 为准，不能只依赖 RPC 时的检查。incarnation 是绑定在许可中的进程标识，不是证书宣称的身份，也不提供同 JVM 恶意代码隔离。可信 Broker 进程必须只有本地不可逆 DRAINED 才能构造确认。

确认请求**不要求持有者此时仍为 voter、观察者或当前 Leader**：自降级／自移除后仍须能报告旧许可结束。此例外只针对持久化许可已绑定的精确 executor 身份，不扩大其他 CONTROL 权限；必须在 `BrokerPeerAdmissionInterceptor` 的专用分支校验，而不是先被通用 `isCommitted` 拒绝。请求所指许可不存在、已替换或 incarnation 不同均拒绝，不能借此加入集群或修改目标。许可仍占用 operation，成员操作未成功收口前不因原生配置移除而删除执行者的确认路由信息。确认可重投当前 Leader，旧 Leader 不转发通用成员调用。

同进程持有者走相同应用校验和共识命令，不伪造 MTLS 请求；认证责任分为远程传输身份校验与本机可信调用。领域命令只包含领域值，不依赖 Spring、RPC principal 或 Plugin 类型。命令编码使用显式白名单，未知类型／旧格式拒绝，不添加兼容分支。

版本与业务提交规则：Begin、首次许可登记、首次排空确认、成员业务 Commit 各自作为一次聚合状态转换，将当前 revision 加一；拒绝及完全相同的幂等重复不推进 revision。成功确认在同一次转换中记录步骤成功并选择下一固定步骤，不另发可绕过许可的步骤 advance。Register 的 expectedRevision 必须在状态机中与当前 revision 比较；幂等命中只返回既有事实，不重新授权执行。Confirm 不依赖发送时的旧 revision，而以当前完整许可身份、固定目标摘要及已有结果作 CAS，首次应用时推进当时 revision。新格式快照与重放必须保留这些版本事实。

最终成员业务 Commit 的 expectedRevision 改为提交时读取的当前 revision，不再要求 `acceptedRevision.next()`；acceptedRevision 仅保留原请求的接受事实，不作为许可协议完成后的当前版本。聚合必须同时验证 operationId 与原 commandDigest、当前占用操作、当前 revision、所有固定步骤成功、所有许可排空且无未决执行，以及既有 epoch/fence 与领域约束，然后原子修改业务拓扑并推进 revision。本分支仅用于修改原生配置的成员操作；`COMPLETE_LEARNER_SYNC` 与其他非配置操作不套用全部目标／许可要求。成员操作的通用 phase advance、失败结束及其他终态入口均不得绕过未排空许可；重复业务 Commit 返回既有结果，不再次改拓扑。

资源上界：全局至多一个未排空成员许可，本地每许可至多一个在途原生调用。本次不实现封闭记录回收，以免回收与迟到调度产生新的 ABA；本进程的执行槽与封闭记录总计最多 4096 条，作为实现常量，而非已测容量结论。登记新许可前必须在短本地互斥内预留容量，再发共识登记；并发请求共享此上限。预留记录在本进程内也不复用，提交未知不能释放；满额时在新许可登记前明确拒绝新维护，不驱逐记录，不影响已有许可的封闭与确认。所有执行入口仍须核对当前持久化许可及 incarnation，不能仅凭本地缺少槽创建执行能力。已提交许可只能消费对应预留槽，缺少槽时失败关闭，不隐式重建。新 Leader 若要登记自身许可同样先预留；不能接管旧的未排空许可。

重启只更换 incarnation 并释放本机内存，不是未排空许可的恢复手段；若持有未排空许可，重启后仍按 D16 保持 blocked。只有已无未排空许可时，后续新进程才可为新许可重新提供容量；不在本任务新增自动重启、清理或解锁平台。若未来引入记录回收，必须另行证明迟到任务不能在回收后重建槽，并补齐确定性反例，不能直接删除记录以绕过此上界。

接口反例补充：两个固定步骤分别登记／确认后，最终业务 Commit 在新 revision 上成功；旧 revision 登记被拒绝，重复命令不推进 revision；容量耗尽在登记前拒绝且不驱逐旧记录，提交未知不释放预留，旧调度不能因本地无槽重建；同许可重复调度不产生第二次原生调用；同一步新 permitId 不接受旧确认；封闭后重投旧许可不重建执行槽；自移除持有者可提交精确排空确认但不能调用其他 CONTROL；请求者伪造 incarnation／targetDigest、DEV 远程确认与通用 advance 绕过均被拒绝；确认提交超时后重投只重复提交事实，不执行原生目标。

阻断问题 R1：旧执行者迟到调用 `changePeers(旧目标)` 可能覆盖后续目标，完整旧目标还可能回退多次后续成员变化。附录 C 的调查和本轮 Review 均不支持用单一路径或漂移诊断阻止该行为。D11 已重新打开这一段防护设计，取代 D8 的暂停边界；防护未证明前，不能把此问题登记为已接受残余风险，也不能进入实施。

### 4. Leader 漂移诊断

- 复用 `BrokerMembershipScheduler` 现有扫描周期：Leader 在没有未终态成员操作时读取 `leaderConfiguration()`，与已提交业务状态的期望配置比较。
- 不一致时：输出告警与指标，内容包含期望配置、实际配置、revision 和 leaderTerm；拒绝登记新的成员维护操作并返回明确原因；不影响自动故障接管和路由。
- 新维护请求不得把「扫描未发现漂移」当作准入证明；启动、换届、读取失败和结果未知一律不能授权维护。请求应直接核对配置，并在登记前重验 leaderTerm/revision；该前置检查仍不能阻止登记后的迟到调用，最终执行防护由 D11 协议负责。详细时序须随 D11 一并审查。
- 不自动修复，即不自动把配置改回业务目标；修复方式见 O2。
- 读取失败（例如本机已不是 Leader）时诊断跳过本轮，新维护请求失败关闭。

### 5. 依赖与制品

- POM 改回官方坐标；dependency tree 只允许存在一个 `jraft-core`，即官方坐标。
- 删除补丁、recipe 和私有制品的有效入口。私有制品 capture/verify-lock 相关的 Harness 规则按规则生命周期移除并写明理由；保留必要的历史来源说明。
- 版本首选官方 1.4.1，与补丁基线是同一 commit。V1 负责确认 Maven Central 制品与固定 commit 对应，并支持 JDK 21。

## 可靠性与安全

- 多数派：所有业务状态转换和配置变更都经 JRaft 共识；失去多数派时不签发新授权（D2）。
- 隔离：fencing、quarantine 与 Lease 不变（D6）。
- 重试与幂等：成员维护按 operationId 加固定目标重试；自动接管沿用现有 operation 幂等。
- 超时：`changeConfiguration` 超时表示结果未知，不推断失败，也不推断回滚。
- 授权：保留 MTLS 身份校验；普通 CONTROL 权限不变，排空确认增加 §3 的精确许可持有者分支；观察准入从「实际 voter」改为「已提交观察者」，仍要求认证身份与请求一致。
- 审计：成员维护 operation 记录目标配置；漂移告警记录期望配置与实际配置。
- 资源：复用现有调度入口，不新增通用执行平台；本地执行槽采用 §3 的 4096 条保守上限，满额拒绝新维护，不驱逐安全记录。该上限未作容量测试，不能据此宣称已测内存预算。

## 运维与交付

- 发布事实：用户确认尚未上线，不考虑旧版本兼容（D18）。本次不支持混跑，不增加 v2 兼容解码、旧快照转换、迁移启用屏障、双格式写入或降级窗口。
- 持久化格式：目标序列、步骤与执行许可进入新的显式命令／快照格式；旧格式和未知格式明确拒绝，不默默补默认目标。新版本自身的日志重放、快照恢复、许可身份恢复和未知状态阻塞仍必须验证，这不是旧版本兼容。
- 本地历史数据：按可重建开发／测试数据处理；若需重建，必须先确认具体目录及其归属，不在本任务擅自删除或覆盖已有 Raft 数据。
- 回退：代码和依赖作为同一逻辑变更回退，不承诺用旧二进制读取新格式开发数据；不建设旧数据迁移或恢复平台。
- 可观测性：新增漂移、执行许可未排空、成员维护阻塞及 BUSY 的告警／指标，明确 D10/D16 可能连带暂停自动接管。
- 配置：复用 Scheduler 扫描间隔。`im-raft` 的 `RaftRuntime` 在每个本地 Node 启动前统一设置 `NodeOptions.setDisableCli(true)`，包括初始及重建实例，不提供线上覆盖开关；通用插件负责原生选项装配，Broker 不依赖实现类。原生 CLI 读写请求均不作为支持入口，不承诺只禁写而保留只读 CLI。诊断使用现有日志、指标和 Broker Leader 的 `leaderConfiguration()` 只读诊断；无 Leader 时读取失败关闭，不临时开启 CLI，不新增配置修复 RPC。受许可的 `Node.changePeers` 调用不走 CLI guard，仍受 §3 约束；外部管理程序不得取得进程内 Node 写能力，不承诺同 JVM 恶意代码隔离。
- CLI 验证：所有节点及重建实例的原生 CLI 写请求不能改变配置，原生 CLI 只读请求也被拒绝；Broker 受许可成员调用仍能工作，诊断不开放写能力。

### 自有运维 CLI（D19，独立补充设计）

2026-10-30 用户确认浏览器登录／public client／PKCE 与 Ops 窄 Bearer 入口方向，准确安全契约集中于[自有运维 CLI 与 IAM 接入设计](2026-10-30-ops-cli-authentication-design.md)，已取得独立设计复核 Accept 与用户最终批准；本节仅保留治理边界，不作为独立的认证规范。

用户选择自有 CLI，作为 Broker 受控管理接口的客户端，而不是原生 JRaft CLI 的直连替代品。原生 CLI 继续禁用；「禁止管理旁路」不等于「禁止运维命令」。CLI 不持有 Node，不直接调用 `changePeers/resetPeers`，不自行计算完整目标配置，不维护另一份权威成员状态。

调用链为：运维 CLI → Ops 管理边界的 IAM 身份认证、逐操作授权与集中审计 → Broker 现有 management HTTP → 对应应用用例／领域校验 → 受执行许可约束的 im-raft 调用。操作权限与执行许可分别检查：管理员权限不能跳过旧许可排空、revision、epoch/fence 或 quorum 条件，也不复用 Broker 节点 CONTROL 身份作为人的管理权限。成员写操作复用已有 operationId/digest、幂等与结果未知语义；CLI 超时只能查询／以同一 operationId 及固定请求语义重试操作，不创建新的并发目标或强制解锁。

首版范围候选限定为本次治理所需命令，不复制整个运维控制台：

| 命令职责 | 对应能力与边界 |
|---|---|
| 查询集群 | 复用 Ops `GET /api/cluster-state`，明确为带来源、revision/epoch 的运行投影，不声称线性一致读取 |
| 查询 operation | 通过 Ops 读取 Broker 已有 operation 查询；Ops 当前尚无对应公开 GET，需补窄查询，不把 Broker 内部对象暴露到终端 |
| 加入 learner、显式确认同步、晋升、降级、移除 | 复用 Ops 现有 `/api/brokers/{brokerId}/raft/{action}` 白名单和应用服务，不开放任意 action 或原生完整配置参数 |

每个写命令显式携带 operationId、expectedEpoch 及用例所需参数；CLI 不自动刷新 epoch 后重新提交写入。提交前展示目标与操作，交互确认或显式非交互确认参数只表示用户意图，不代替服务端授权。首次发送前保存不含凭据的重试身份与原请求语义；超时后报告结果未知，保留 operationId，不把未知当失败生成新操作。操作受理与操作完成分开展示，查询不能修复、释放或转移许可。Ops 继续只跟随一次可信 Leader 提示，CLI 不持有 Broker 地址或服务凭证，也不叠加 Broker 重定向链。日志与集中审计不含 Token、Cookie、证书私钥或完整请求 Body，沿用现有脱敏约束。

用户已明确同意按自有 CLI 方向继续推进设计；这不是对尚未固定的管理接口及认证协议的整体实现批准。已核实仓库存在 Broker management HTTP 及 `im-ops` 运维应用：CLI 应经 Ops 复用已有成员用例，Ops 再调用 Broker，不新增直接绕过 Ops/IAM/Audit 的终端写入口，不向终端分发 Ops 到 Broker 的服务凭证。当前 Ops 只接受 IAM BFF Session，不能把 CLI 的 Bearer Token 接入当作现成功能，也不能靠导出浏览器 Cookie 代替正式登录设计。具体首版命令清单、管理传输协议、认证授权机制、凭据保管、审计载荷、只读结果一致性及 CLI 模块归属尚未固定，需补充设计和独立 Review 后才能进入 execution plan。不据此默认新增管理平台、审批系统或全部原生 CLI 功能。手动转移 Leader、触发 snapshot、强制恢复不自动纳入本次范围；D12 不新增漂移修复 API、D16 不自动移交未排空许可的决定不变。验证需覆盖未授权拒绝、授权后仍受许可约束、同一 operation 的超时重试，以及没有直连原生写旁路。


## 风险与回滚

- 风险：
  - R1：旧执行者迟到调用，见 §3。
  - R2：观察资格与实际 voter 分离后，外部 CLI 造成的漂移不再自动停止接管，依赖漂移告警和运维处理。
  - R3：跨角色单次变更已被否决；分步协议仍需证明持续复制、真实计票和每步恢复（V2）。
  - R4：peers 顺序不一致会导致「同目标」未被识别，产生一条无害的空配置变更日志（V3）。
  - R5：HA 拓扑和 E2E 需要重新取证，现有补丁版证据不可复用。
- 回滚：见「运维与交付」。
- 延后事项：`Task.expectedTerm`、维护期间允许自动接管、漂移自动修复、RTO 测量。旧执行者防护是 D11 要求的批准前置条件，不再列为延期。

## 验证策略

以下是设计层面的待证项，准确命令在 execution plan 中固定。

- **V1 官方制品**：确认坐标、SHA、对应 commit 与 JDK 21 兼容；dependency tree 只有单一实现，没有 imchat 私有坐标残留。
- **V2a 单步拒绝契约**：真实官方 Node 分别验证完整目标跨角色 `changePeers` 与已有 learner 的 `addPeer` 被拒绝、配置与配置日志未变、后续业务复制正常。这是进入配置执行前的拒绝，不要求或声称进入阶段后的 quorum 故障。
- **V2 RaftRuntime 真实多节点测试**：learner→voter、voter→learner 分两步完成；晋升后继续写业务日志，且新 voter 的票必须成为形成 quorum 的必要条件；降级后 learner 持续复制；覆盖两步之间换届、超时和重启。另验证同目标重复调用、进行中第二次调用 `BUSY`、非 Leader `NOT_LEADER`、移除当前 Leader 后新 Leader 配置。必须以 D11 最终防护协议补充迟到目标确定性反例，未通过前不切依赖。
- **V2b 完成顺序与未知结果**：通用适配器以受控 Node 回调分别覆盖 closure 先于同步返回与同步返回先于 closure；断言底层完成观察只读、调用方取消或派生超时不改写底层结果、同步异常保持未知。真实网络实验不靠调度碰运气证明两种顺序，日志观察不是断言，也不是排空证明。
- **V3 顺序规范化**：初始配置与目标按同一规则排序后，重复调用不产生新的配置日志（观察 `lastLogIndex` 不变）。
- **V4 Broker 观察资格**：
  - `findObservers` 规则：角色、端点绑定、未终态门。
  - Context 不再包含 membership。
  - Follower 服务端、Leader 决策、入站准入三处使用同一观察者集合。
  - revision 不一致时拒绝；revision 前进后旧观察者被拒绝。
  - RED 先行：现有依赖 `ConsensusMembership` 的测试先改为新契约并确认失败。
- **V5 成员维护与恢复**：首次成功、分步目标、封闭排空、成功与失败区分、请求超时但底层仍存活、换届不抢占许可、重启不复活旧 incarnation、失去多数派及未知结果保持阻塞。验证新格式日志／快照恢复完整许可与步骤；旧格式、未知格式明确拒绝，不做旧格式兼容。门禁测试覆盖 operation 已终态但仍 `LEARNER_SYNCING` 的长期暂停及显式确认后的恢复。
- **V6 漂移诊断**：配置一致时无告警；外部修改配置后告警并拒绝新维护，同时自动故障接管仍能完成。
- **真实拓扑/HA**：三节点中单 Broker 崩溃后自动接管、Leader 崩溃、learner 加入追平并晋升、显式移除、快照安装、分区恢复；核对 voter 没有被自动删除。
- **完整门禁**：readiness → 聚焦测试 → affected（behavior、startup、E2E 均适用）→ clean → full；独立 quality Review。

## 后续文档与执行

需要同步的文档：

- `ARCHITECTURE.md`：Broker 与 Raft 的职责划分。
- `im-broker/ARCHITECTURE.md`：观察资格与成员维护。
- `im-plugin/im-raft` README：接口变化。
- `im-broker` README：观察者定义与漂移告警。
- `docs/RELIABILITY.md`：多数派与接管边界。
- `docs/SECURITY.md`：观察准入。
- `docs/references/HARNESS_GUIDE.md`、`docs/references/HARNESS_RULE_MATRIX.md`：移除私有制品规则。
- `deploy/local`：若引用私有制品，一并修改。
- `docs/design-docs/index.md`：更新本文状态；此前私有制品设计已彻底删除，历史事实仅保留在执行历史中。

执行条件：上一项 Bolt 计划已收口，当前基线工作区干净；本设计仍需关闭 Block findings、完成开放决策并取得用户批准与独立 Review，之后才创建 `docs/exec-plans/active/<date>-official-jraft-migration.md`。

建议的任务顺序是先改业务、最后切依赖。原因是 `changePeers` 和 `listPeers`/`listLearners` 在补丁版中同样存在，T1a 到 T4 可以在补丁依赖上完成，并保留与现状的差分回归能力：

1. T1a：`im-raft` 新增 `RaftConfiguration`、`changeConfiguration`、`leaderConfiguration` 和 `BUSY`（V2、V3）。
2. T2：观察资格迁移（V4）。
3. T3：成员维护改为目标配置（V5）。
4. T4：漂移诊断（V6）。
5. T1b：删除 `membership()` 与单步接口，依赖切回官方坐标（V1）。
6. T5：删除补丁、recipe 与 Harness 规则，同步文档。
7. T6：HA/E2E 与完整门禁。

## 开放问题

以下问题需要用户决定后，设计才能批准：

- **O1 已决定，见 D10。** 保留完整维护门禁：未终态成员操作或任一 `LEARNER_SYNCING` 都暂停自动观察与接管。多数派健康也可能长期暂停，Scheduler 不保证解除；不能把本项确认理解为批准其他迁移风险。
- **O2 已决定，见 D12。** 仅告警并拒绝新成员维护，不增加自动修复或在线修复 API；异常配置按另行明确的运维恢复流程处理。
- **O3 已决定，见 D15。** 先以官方 1.4.1 为迁移候选，不顺带升级；制品来源、源码对应与 JDK 21 兼容必须实测，失败保持阻塞，不自动换版。
- **O4 已决定，见 D11。** 不接受 R1 为首版残余风险；先补齐过期执行防护设计，证明不成立前保留补丁、不实施目标配置迁移。

## 设计 Review

2026-10-29 首轮独立 Reviewer `afb14c7962f5b6789` 结论 **Block**，基线 `359b5893522997edc382a70f214fc80bed4c4090`，只读审查，未运行构建。固定 upstream 归档 SHA-256 与本文引用一致。

| ID | 问题与证据 | 处理状态 |
|---|---|---|
| F1 | 单次跨角色 `changePeers` 可能复用原角色 replicator；`ReplicatorGroupImpl.addReplicator` 对已有端点直接成功，Learner 响应不计票，配置清理还可能停止该 replicator | 摘要、详细设计与 V2 已改为分步约束；具体执行防护仍由 D11 阻塞，未取得复审 Accept，不授权实施 |
| F2 | 原草案承诺旧数据升级却遗漏旧日志重放 | 用户确认未上线、不考虑兼容（D18），旧数据迁移与兼容已移出范围；撤回兼容承诺和启用屏障候选。保留新格式自身恢复与旧格式明确拒绝测试 |
| F3 | PENDING 可无限重试；操作终态后 `LEARNER_SYNCING` 仍会使观察集合为空，Scheduler 不自动确认同步 | 用户已按真实长期暂停语义确认 D10；正文恢复条件、告警和测试尚待补全 |
| F4 | 漂移扫描与新维护登记之间的未知状态和时序未定义 | 必须明确新维护请求的 fail-closed 核对，不把未检测视为一致 |

审查同时指出：完整旧目标迟到可能覆盖多个后续成员变化并影响多数派；漂移诊断不是执行 fencing。审查后用户确认 D10–D16：保留完整门禁，不接受 R1，保留在线维护并禁止外部并发配置写，漂移只告警拒绝维护，官方候选先用 1.4.1；接受不可自动移交执行许可可能长期阻塞的代价。具体防护和恢复协议仍待 Review。官方制品对应关系、JDK 21、真实角色切换、HA/E2E 均未验证。上述 finding 未关闭前，其他正文中的旧推荐不构成实施授权。

### 本轮执行许可局部复审

2026-10-29，同一独立 Reviewer 对「先封闭排空，再允许后续目标」候选最终给出 **局部 Accept，findings none**：上层结果处理不再绕过许可，安全排空与业务成功分开，单次 closure 不等于许可排空，无法证明的路径按 D16 保持 blocked。仅接受协议约束，不代表整体设计获批或 R1 运行验证通过。后续已显式补充每次重新登记使用新 permitId 防止 ABA。

技术完成信号、许可登记／确认命令与认证 CONTROL 请求候选已写入 §1、§3。2026-10-29 接口首轮 Review 为 Revise：指出新增许可转换与原 `acceptedRevision.next()` Commit 条件冲突、本地封闭记录缺少资源上界。修订为各次转换推进 revision、最终成员 Commit 校验当前版本及全部成功排空事实，以及登记前预留且不驱逐的 4096 条本地上限后，同一 Reviewer 对这两项给出局部 Accept、findings none；其余接口重点未发现新的否决反例。该结论不等于整体设计批准，失败登记亦消耗名额的保守限制保留。真实多节点与确定性反例均未运行。旧格式恢复候选曾取得的局部 Accept 随 D18 撤回，不复用为当前证据。

### 全文复审与修订

2026-10-29 独立全文 Reviewer `a13cd6eb2101dd1b7` 对设计指纹 `9812aa1eab9fee73e4316d490f5bf956e11051bb228d52aa2e766860a307f13a` 给出 **Block**：FSM 同步 continuation 可能进入原生调用形成锁环，`COMPLETE_LEARNER_SYNC` 的 Commit 分支缺失，CLI 禁用仍未定案。现已分别补充有界专用执行器与拒绝策略、同步确认独立 revision／恢复分支、每个 Node 启动禁用原生 CLI 的装配责任及验证要求。随后同一 Reviewer 对设计指纹 `17908397752e44e42fb5ef336c4a76c7cf77831eb77b8c870a0763bf0cbcb880` 复核为 **Accept，尚存必须修正 findings none**，结合上一轮完整审查确认三项设计缺口已关闭，可以进入用户最终书面审阅。实际证据为只读段落核对、`git diff --check` 无输出和 SHA-256 计算；未构建、下载或运行反例。此 Accept 仅针对设计，不是生产实现或依赖切换验收；原生完成信号、并发反例、角色切换、新格式恢复、CLI 拒绝及 HA/E2E 仍须在实施阶段证明。D10/D16 的长期阻塞风险保留；新不变量须落为确定性测试，不另建通用框架。此为当时审查检查点；2026-10-30 用户最终批准与 CLI 补充组合后的方案，实施入口见页首计划，尚未修改生产实现。

2026-10-29 用户在最终书面批准前选择自有 CLI（D19）。原设计 Accept 保留为历史，不能覆盖新增管理入口；当前重新打开这一部分设计，尚未确定首版接口及模块范围，未授权实施。

### D11 防护候选复核与恢复入口

独立 Reviewer 继续只读核对固定 upstream，结论仍为 **Block**。普通 single-flight、最后一次 revision/term 检查、取消 Future、单步增删接口均不能独立阻止旧执行者跨越后续操作；单步接口只能缩小覆盖范围，不能防止旧 add 撤销新 remove。

曾提出的「业务状态应用与原生同步受理共用 monitor」候选已否决，不得实施：

- 配置日志在 `FSMCallerImpl` 直接处理，不经过 Broker 的业务 `apply` monitor，不能泛称持锁必然阻止 Leader 初始化完成。
- 存在锁环：维护线程持 Broker monitor 等 Node 写锁；失权线程持 Node 写锁向已满 FSM 队列投递 `LEADER_STOP`；FSM 消费线程等待 Broker monitor。即使不持锁等待异步 closure，原生同步入口仍可能造成死锁。
- 增大队列、给调用方 Future 超时、扩大回调 monitor 均不消除该锁环。

证据来自归档 `NodeImpl.java:1311–1325,3166–3174,3382–3385`、`FSMCallerImpl.java:244–251,300–304,542–558`，以及当前 `RaftBrokerClusterStateMachine.apply/restore` 的同步边界。未运行反例测试，不把源码分析写成运行验收。

生命周期隔离候选继续复核后仍为 **Block**：旧任务即使永久绑定旧 Node，异步退役任务也可能尚未执行，旧实例已经再次当选；旧调用越过最后一次 retired 检查后仍能被受理。`onLeaderStop` 经 FSM 队列异步通知，不是失权时的原子退役钩子；在 FSM 回调等待 shutdown/join 也不能作为无阻塞方案。只有确认旧 Node 已永久停止且任务不能转向新实例，才能证明该实例不再借新任期执行。不得实施「失权后最终 shutdown 即安全」的方案。

补充 API 事实：固定官方 `Node` 无公开 `getCurrentTerm()`；实现类中的测试方法不能作为生产契约。`NodeOptions.setDisableCli(true)` 可关闭该节点 CLI 请求，已选定为 D14 的启动约束（见「运维与交付」），但不阻止 Broker 内部旧调用，也不保留原生 CLI 只读请求。

**当前恢复入口**：重新检查维护推进条件是否必须等待旧执行许可明确封闭，而不是让新 Leader 在旧调用结果未知时继续后续目标；该方向尚无批准协议，可能牺牲成员维护在失联时的自动恢复，不得静默引入。下一轮须给出原生受理及旧许可排空边界、跨任期/重启/超时证明和无死锁锁序；确定性反例须断言实际 Raft 配置未回退，而不只是业务 Commit 被拒绝。覆盖首次推进、每个角色切换步骤、resume、Scheduler、continuation、关闭和快照恢复。若无法满足 D11，保持私有补丁与现有实现，不通过缩减验收或默认接受风险解锁。

## 附录 A：备选方案

| 方案 | 结论 |
|---|---|
| 保留私有补丁（现状） | 行为不变，但持续承担补丁维护和制品成本，且通用框架承载业务所需的非官方证明；与 D9 冲突 |
| **观察资格取已提交业务状态，成员维护用目标配置（本文推荐）** | 只依赖官方公开接口；代价是 R1、R2 及升级兼容工作 |
| 观察者仍绑定实际 voter，改用 Leader 的 `listPeers` 读取 | 不成立：`listPeers` 仅限 Leader，Follower 服务端和入站准入无法调用 |
| 官方包加关闭自动摘流 | 违反 D1，否决 |
| 官方包加存储/快照观察扩展 | 需要处理日志截断、压缩和快照竞态，复杂度高且无等价证明，否决 |
| 更换共识组件 | 改动和验证面过大，没有必要性证据，否决 |

官方依据：SOFAJRaft 固定 commit `cb1a0c7ac16ff146ad8abb04b1bcb035270bb234` 中的 `jraft-example` Counter 示例（`CounterServiceImpl`、`CounterStateMachine`），以及上文列出的 `NodeImpl`、`Configuration` 源码。

## 附录 B：决策记录

本节保留用户确认的完整语义。原「关闭自动摘流后移除私有调用」推荐已被否决，正文已统一修订为保留自动接管的研究路线。具体协议、接口选择、测试契约和迁移顺序仍需核实并独立 Review。

### 决策记录

| ID | 来源/问题 | 用户确认的决策 | 理由与影响 |
|---|---|---|---|
| D1 | 自动恢复是否可以改成人工响应 | 自动接管是 IM 的必需能力；保留多数派时，系统必须自动推进故障 Broker 的安全隔离、流量切换和路由重建 | 人工运维不能成为正常故障恢复的前置条件；不承诺多数派存在就足以克服所有数据面故障 |
| D2 | 失去多数派时的可用性边界 | 不绕过共识强行授权接管；失去多数派时允许停止需要共识授权的操作 | 不能用业务成员表或缩小观察分母模拟 quorum，不承诺网络分区下始终可写 |
| D3 | 故障接管是否必须先删除 voter | 流量接管与共识成员维护分离；研究固定共识成员下的自动路由接管，不把删除故障 voter 作为接管前提 | 三 voter 中一个故障时，存活两个可以继续形成多数派；永久下线、替换和扩缩容另走受控维护流程 |
| D4 | 恢复速度和切换体验 | 尽快恢复实时收发，允许短暂收发失败和自动重试，不要求切换完全无感 | 尚未确定秒级 RTO，不填写虚构时限；优先衡量受影响用户恢复时间，另行记录整个操作收尾时间 |
| D5 | 消息可靠性与重试 | 已向发送方确认持久化成功的消息不能因 Broker 切换丢失；未确认消息允许结果未知，使用同一消息标识重试并幂等处理；实时推送失败后需通过补投或同步获取 | 保存成功不等于接收方已收到；避免重复处理和重复展示属于待端到端证明的目标，不宣称 exactly-once，也不假定现有补投能力已完整满足要求 |
| D6 | 速度与隔离安全冲突 | 新 Broker 执行前必须满足旧权限隔离条件，保留 fencing 或完整 quarantine，不以缩短超时替代证明 | 无法联系旧节点不证明旧节点停止工作；Lease、观察窗口及可并行步骤须验证，不能为追求低延迟跳过安全条件 |
| D7 | 私有补丁与官方依赖的优先级 | 以保留现有证明方式和自动接管为安全基线；推荐研究官方 JRaft 下重新设计资格证明的路线，证明成立后才切换依赖，否则保留补丁 | 优先级为消息可靠性与隔离安全、快速自动接管、依赖维护成本；保留补丁不代表当前实现已完成验收；不优先更换共识组件或大幅重构控制面 |

### 当前收敛决定

| ID | 来源/问题 | 用户确认的决策 | 理由与影响 |
|---|---|---|---|
| D8 | 研究偏离治理简化主线，是否继续扩展维护协议 | 暂停扩展 stale-executor 调查和官方迁移细节研究 | 保留调查作为历史证据，不把它升级为当前生产修复任务；不新增锁、执行平台或成员 API |
| D9 | 谁负责业务治理，谁负责框架能力 | 业务模块负责故障观察、自动摘流、路由接管和业务状态恢复；通用 `im-raft` 只提供官方公开的通用共识能力；删除私有 JRaft 补丁；Raft 成员调整独立受控，不自动删除 voter | 避免把 Broker 业务语义塞进通用框架，也避免业务层复制 JRaft 内部状态机；自动接管不依赖自动成员删除；整体设计已获最终批准，生产实施与运行验收尚未完成 |

| D10 | 2026-10-29：成员维护与 learner 同步期间是否继续暂停接管 | 保留完整既有门禁：存在未终态成员操作，或任一 Broker 仍为 `LEARNER_SYNCING` 时，暂停自动故障观察与接管 | 即使多数派健康也可能长期暂停；不保证 Scheduler 自动解除。必须恢复成员操作，并显式确认 learner 同步或经受控维护解除状态；需补充告警、解除条件和行为测试。本例外不批准 R1 或其他未决迁移方案 |
| D11 | 2026-10-29：是否接受旧执行者迟到完整目标覆盖后续成员配置 | 不接受为首版残余风险，先补齐防护设计 | 重新打开这一小段维护协议研究；要求证明过期目标不能覆盖后续变更，证明不成立前保留补丁、不实施目标配置迁移。取代 D8 在该问题上的暂停边界；不授权新增执行平台或生产代码 |

| D12 | 2026-10-29：配置漂移处理范围 | 仅告警并拒绝新成员维护，不新增自动修复或在线修复 API | 异常配置的恢复另走明确的运维流程；不以自动覆盖掩盖未知结果 |
| D13 | 2026-10-29：是否保留在线成员维护 | 保留 learner 加入、晋升、降级和移除的在线维护能力 | 继续验证共用状态应用／原生同步受理边界候选；锁序、换届和停顿风险未通过审查前不实施；不改为仅停机维护 |
| D14 | 2026-10-29：外部成员配置写入口 | 禁止外部 JRaft CLI 或其他管理程序并发修改成员配置，Broker 为唯一在线成员变更入口 | 外部工具限允许的日志／指标与 Broker 只读诊断；原生 CLI 读写均禁用，具体装配见「运维与交付」。异常修复另走受控停机流程，不靠文档约定代替入口禁用 |

| D15 | 2026-10-29：官方依赖版本候选 | 先以官方 1.4.1 为迁移候选，不顺带升级 | 制品来源、固定源码对应和 JDK 21 兼容需实测；失败保持阻塞，不自动换版 |

| D16 | 2026-10-29：以不可自动转移执行许可消除迟到覆盖的可用性代价 | 接受执行者失联或原生结果未知时不因换届／重启自动转移许可；只在明确封闭排空，或另行设计并批准外部 fencing 后恢复 | 成员维护可能无限期阻塞，并因 D10 同时长期暂停自动故障接管。继续完善协议及独立 Review，不视为实现授权；不接受把不可达或超时当作排空 |

| D17 | 2026-10-29：是否保留直接降级窗口 | 接受新格式首次写入即不可直接降级；该写入可能来自维护启用前的快照、业务日志或失败的启用请求 | 不新增兼容写入模式。升级前完成旧成员维护并检查数据；写新格式后仅走另行验证的数据恢复流程，禁止用恢复旧备份丢弃后续已确认数据。不代表具体恢复协议已经获批 |

| D18 | 2026-10-29：用户澄清尚未上线 | 不考虑旧版本兼容；撤回旧日志／快照兼容、共识启用屏障与降级窗口设计 | 取代 D17 及相关迁移候选；不新增兼容层或启用命令。新格式自身重放／恢复和格式拒绝仍须验证，本地旧数据不擅自删除 |

| D19 | 2026-10-29：安全许可下的运维入口 | 选择自有 CLI，经 Broker 受控管理接口执行操作，不直接开放原生 JRaft 管理旁路 | 原生 CLI 仍关闭，但保留受控运维能力的设计方向。操作权限不能替代执行许可；命令范围、认证授权及模块归属须补齐并独立复审。不代表整体设计最终批准或实现授权，不改变 D12/D16 |

## 附录 C：历史研究（已被正文取代）

以下为正文定稿前的研究、核查、实验与审阅记录，保留用于追溯。其中「推荐」「下一步」「保留补丁」等表述属于当时的检查点，不覆盖正文；旧迁移顺序、验证策略、备选方案和开放问题已由正文对应章节取代。

### 背景与已知事实

当前唯一直接生产补丁调用在 `im-plugin/im-raft` 的 RaftRuntime，Broker 经 BrokerRaftAdapter 消费实际 voters/learners 与 stable 状态，用于成员操作恢复、观察资格和 Peer 准入。私有实现通过 FSM 队列顺序点和配置历史取得已应用配置；它不是全局最新配置、ReadIndex 或与另一业务读取原子一致的证明。

已检查固定上游提交 `cb1a0c7ac16ff146ad8abb04b1bcb035270bb234`：listPeers/listLearners 为 Leader 查询且不提供完整 local-applied/joint 快照；getCurrentConf 也不等价；配置回调缺少完整 learner/joint/index 信息。尚未核查其他官方版本，不声称所有版本均不支持。直接替换依赖将违反当前契约。

### 目标与非目标

- 硬目标：保留多数派时自动推进安全接管，尽快恢复实时收发；允许短暂失败和自动重试，具体 RTO 待测。
- 当前目标：删除私有补丁，保持自动业务摘流和路由接管，把 Broker 治理语义留在业务模块。
- 框架边界：`im-raft` 只依赖 JRaft 官方公开契约和通用结果类型，不暴露 JRaft 内部配置历史或 Broker 专属判断。
- 业务边界：`im-broker` 负责健康策略、故障 operation、epoch、Lease、fencing、路由恢复和消息可靠性。
- 成员边界：Raft voter/learner 的显式调整由受控集群管理流程负责，不因业务故障自动删除 voter。
- 保留：MTLS 身份、固定角色命名空间、明确 bootstrap 信任、Raft 权威路由状态、RouteBroker 选择、epoch、Lease/fencing、排空和已有消息幂等/ACK 语义。
- 非目标：不在本方案同时重写路由算法、放宽认证、取消多数派要求，或承诺网络分区下无损自动接管。
- 本阶段不实现、不修改当前验收要求、不删除补丁、不换 POM、不提交代码。

### 职责与推荐基线

| 事项 | 所有者 | 推荐行为 |
|---|---|---|
| 选举、复制、joint、安全多数派 | JRaft | 目标使用官方公开契约，不在应用复制其内部状态机；证明完成前保留当前补丁 |
| 路由集合、排空、路由 epoch、所有权 | Broker 权威业务状态 | 与 voter/learner 角色分离，继续经 Raft 提交 |
| 健康观察 | Broker 自动接管的安全决策输入 | 保留独立探测、严格身份及时间/背景检查；观察不直接授权路由切换或成员删除 |
| Raft 成员增删 | 显式集群管理流程 | 初期由运维执行，串行、有审计、失败关闭；不自动触发 |
| 流量迁移/摘流 | Broker 路由协议 | 满足观察、共识和隔离条件时自动推进，保留完整 Lease/fencing/恢复证据 |

例如 B2 故障后，voters 仍为 B1/B2/B3，ACTIVE 可以经安全协议自动调整为 B1/B3。保留多数派不是所有数据面必然恢复的充分条件。两 voter 仍需两票，不通过删除故障 voter 增加剩余集群的容错能力。

### 历史研究：自动接管与接口替代条件

以下保留已完成核查和候选路线用于追溯。D9 已收敛当前主线；其中「下一步」「推荐研究」等描述属于历史检查点，不覆盖当前边界。

#### 日常路由与身份准入

日常数据面继续核对认证身份、已提交路由成员、目标 epoch 和有效 Lease。业务成员身份只能证明业务准入，不能宣称调用者是当前真实 Raft voter。PROBE/REPORT 继续作为自动治理输入，不能降为诊断后仍声称保留原自动接管能力。

现有实现按真实 membership 与业务角色/端点一致解析观察 voter，新设计未获证明前保留此规则。固定成员是减少并发变化的运维约束，不是运行期配置永远不漂移的证明；外部 CLI、启动配置、旧节点数据及未完成维护都必须进入信任和恢复边界。

#### 自动故障治理

保留自动摘流和自动流量接管，不自动删除 Raft voter。现有 reconcileFailures 本身不移除 voter，因此职责分离不是一项尚未具备的全新能力；真正的研究点是移除私有读取之后，如何维持同等资格和恢复证明。

正常路径为：独立观察满足条件 → Leader 同任期 quorum 确认及背景重验 → 幂等 Begin → 旧 epoch fence 或完整 quarantine → 提交新拓扑及 epoch → 目标 Lease 安装 → Gateway 路由快照及 ACK → 恢复证据与终态。保留已有未终态操作优先恢复，不因换届重新创建同一次故障操作。

观察不足或背景变化时不发起；失去多数派时不新签发需共识授权的接管操作，已有 Lease 按其原契约失效。不能缩小观察分母、伪造 quorum 或通过任意 fallback 绕过原边界。迟到报告、旧节点恢复、换届与重复请求均不能使旧权限复活。

旧节点恢复后可以按原 Raft 协议追赶日志，但不由此自动回到 ACTIVE；重新承担流量需要独立安全激活条件。具体激活策略仍待定。

#### 现有调用链核查

以下为本轮源码静态核查，不是替代协议已通过验证。

| 入口/文件 | 当前读取用途 | 替代前必须解决的问题 |
|---|---|---|
| im-plugin/im-raft 的 RaftRuntime.membership | 调用 getAppliedConfiguration，joint 时拒绝，返回 voters/learners | 不能用 Leader 列表伪装完整本机已应用配置 |
| BrokerRaftAdapter.getConsensusMembership | 转成领域 ConsensusMembership | 明确未来快照的来源、有效背景和失败语义 |
| BrokerRaftAdapter.addLearner/promoteLearner/demoteVoter/removeMember | 识别现状并从部分完成继续 | 成员维护不是换成官方 API 后自然幂等；必须处理两步操作和未知结果 |
| BrokerCluster.findConsistentVoters | 未完成成员操作时返回空；精确核对角色、端点及 voter/learner 集合 | 不能只把业务 VOTER 标签作为真实 voter 证明 |
| BrokerClusterAppService.probeFailures/reconcileFailures | Follower 本机采样资格、Leader 报告集合、Context 和冷却重验 | 观察资格来源与多数分母必须可证明；不能只修改 Leader 查询 |
| BrokerClusterAppService.probeBroker/getHealthReport | 入站调用者资格和本机采样背景 | 需要覆盖 Follower 服务端，不只覆盖 Leader 决策端 |
| BrokerPeerAdmissionInterceptor.validateObservation | 真实身份、Context 及当前 voter 准入 | 保留入站与应用层完整拒绝语义，不遗漏重复检查位置 |
| BrokerPeerAdmissionInterceptor.validateControl | 当前按 committed 成员及具体请求能力准入 | 不应无依据扩大或删除授权；此入口当前不读取私有 membership |

BrokerMembershipAppService 对 getConsensusMembership 的消费和各个管理状态迁移仍需逐分支核查。ReadIndex 可用于现有领导权/应用进度证明，但不会自动附带完整成员列表或其与业务快照原子绑定的证明。

#### 推荐研究路线与退出条件

1. 保留现有私有读取及自动接管，作为对照基线，先完成原任务验收。
2. 研究是否可将观察资格定义为独立、已提交的观察者授权，而由 JRaft 自己负责真实共识多数派。该思路会改变故障报告策略，不是把业务成员改名成 voter；报告集合、阈值、撤权、过期背景和成员维护交叉必须重新论证，未获批准前不实施。
3. 对仍需真实 voter/local-applied 的入口单独解决，不因观察者解耦就宣称成员恢复问题一并消失。
4. 若公开接口不能覆盖这些证明或维护会永久阻塞自动恢复，则研究不通过，继续保留补丁；不关闭自动接管以使迁移通过。

#### 观察授权解耦：候选协议与反例

本节为研究候选，不是用户批准的新协议。核心区分是：观察报告只允许提出故障处置意图；真正的共识多数由 JRaft 确认，路由独占性由 epoch、关闭签发、fence/quarantine 及目标 Lease 共同保护。不能据此推导「报告可以任意可信」或「误判没有代价」。

候选保持已认证 Broker 独立探测与 Leader 拉取模式，但将报告来源称为 observer，不称为 voter。观察授权集合、授权代次和成员绑定需进入明确的权威模型；具体是否新增持久化字段尚未决定。以下条件必须共同满足：

- 请求与报告绑定 cluster、观察授权代次及路由 Context，保留唯一认证身份、目标范围和独立采样；不能仅依靠未认证回显。
- 按完整授权集合计算固定阈值，不按已响应或可达节点缩小分母；候选保留超过一半阈值及排除目标自报。阈值是故障判断策略，不冒充 Raft quorum，最终策略仍需批准。
- 授权改变时作废旧样本并重新冷却；旧节点持有过期视图最多导致拒绝或延迟，不能绕过 Leader 最终背景重验、预期 revision 和聚合校验。观察授权变更还必须与运维维护恢复衔接。
- 报告仅形成候选目标；Begin 前仍取得同任期 quorum 证明，之后另行关闭旧 epoch 签发、完成隔离并提交新拓扑，不把报告多数替代任一步骤。
- 无法证明维护交叉、授权恢复或接管活性时保留原 voter 一致性读取。独立 observer 模型本身不能解决成员操作部分完成的恢复。

| 场景 | 候选路线必须满足的结果 |
|---|---|
| observer 报告多数，但 JRaft 失去多数派 | 不提交新的接管授权，报告不能模拟 quorum |
| 真实 quorum 存在，但 observer 报告不足 | 不新发起；这会降低自动恢复能力，必须纳入故障矩阵，不能只证明安全就接受 |
| 误判一个仍在工作的 ACTIVE Broker | 只有完成隔离才能切换；仍可能导致全局 epoch 变化及短暂中断，属于可用性代价 |
| 被隔离旧节点还能联系部分 Gateway | 旧授权不得与新 epoch 产生冲突副作用；不可达不能当停止证明 |
| 维护已 Begin，随后 Leader 故障 | 新 Leader 必须从权威事实恢复或明确阻塞；不能永久等待人工却声称自动恢复达标 |
| 观察集合扩大或缩小、旧票晚到 | 按新授权代次重新建立证据，不重解释旧票或缩小旧轮分母 |

#### 隔离路径源码核查与尚缺证明

静态核查确认以下机制存在，不代表本轮已验证完整安全性：

1. RaftBrokerClusterStateMachine.grantLease 与日志应用共享 synchronized 边界；BrokerCluster.checkLeaseGrant 拒绝不匹配 epoch、非路由成员及已关闭签发 epoch。
2. BrokerClusterAppService.advanceEpoch 在 quorum 确认后持久化 RoutingEpochFence，冻结旧 ACTIVE 集合，随后向全部旧持有者请求 fence；不是只隔离故障目标。
3. 全部有效 ACK 返回时可以继续；存在未确认者时，当前实现先汇合全部 RPC，再等待完整 quarantine，然后提交新拓扑和安装新 Lease。不能在本次文档修订中擅自改成从故障发现时计时。
4. LocalRoutingLeaseService.executeOrElse 在准入后登记在途副作用；fenceLocal 关闭新准入、等待已登记调用完成，并持久化水位后才返回停止确认。

必须补齐的证明包括：

- quorum proof 与后续 grant/Begin 之间换届、延迟回调和进程暂停的影响；confirmLeadership 的调用存在，不足以证明所有后续操作都原子绑定任期。
- 显式确认 ReadIndex 使用模式及固定官方版本行为，不能把 ReadOnlyLeaseBased 与每次真实多数心跳确认混同；本轮尚未完成该核查。
- Lease 过期只关闭新的执行准入，不自动取消已开始的副作用。quarantine 的调度暂停余量不自动等于所有在途调用的执行上界，必须审计 Message/Gateway 的下游期限、epoch 校验、重试及幂等，证明旧调用晚到不会产生冲突。当前不据此宣称发现已复现漏洞，也不据局部锁推导全链路安全。
- maxSchedulingPause 等安全上界是故障模型前提，不是 JVM 或网络天然保证。无界暂停、非对称分区和重启应有明确的支持或失败边界。

#### 下游晚到与维护恢复核查

本轮沿生产调用路径继续静态核查，得到以下局部事实；尚未完成完整调用链及确定性故障反证。

| 路径 | 已确认的保护 | 不能由此推出的保证 |
|---|---|---|
| RouteBrokerExecutionInterceptor → Handler | 入场检查 deadline、epoch、RouteBroker 与 Lease，executeOrElse 登记在途调用 | 入场通过后不会自动取消下游工作；不是下游提交时重新检查 epoch |
| GatewayDispatchAdapter → GatewayClient → FrameWriteHandler | 每次尝试生成 deliveryAttemptId，沿用稳定 deliveryId；Gateway 校验 Broker 身份，按 deliveryId + connectionId 去重 | GatewayFrameWriteParams 不含路由 epoch 或业务绝对期限；SDK timeout 不等于远端取消 |
| GatewayBrokerAuthorizer | 唯一 Broker URI SAN、committed Broker 身份检查 | 当前不按 ACTIVE/RouteBroker/路由 epoch 判断写帧资格，不能称为下游 fencing |
| FrameDeliveryDeduplicator | 有容量和保留窗口的进程内去重；容量不足拒绝，写失败释放占位 | 不是跨重启或无限期幂等，也不能拒绝不同 deliveryId 的旧授权写帧；已占位不等于客户端已收到 |
| FrameProcessHandler → MessageService | 上行按命令调用 Message Facade；下行按 routeContext.operationId 形成稳定 deliveryId | 当前 Handler 传递路径不提供 Message 提交时的路由授权证明，不能把 Broker 入场 Lease 当作数据库提交 fence |
| PrivateMessageAppService.sendMessage | messageToken 参与唯一性检查，双方用户锁内执行；双方收件箱和会话在同一事务中保存 | 未完整核查唯一键和重复响应语义，不宣称重试能返回首次结果；私聊片段也不能替代群聊和其他命令验证 |

需要区分两类晚到：

- 同一已提交消息的重复通知，可以依赖稳定 deliveryId、接收幂等和补同步减轻影响，但必须验证保留窗口、重连及重启边界。
- 旧权限下不同操作标识的新副作用，不会被上述去重自动消除；若要求下游拒绝，必须另行定义 epoch/授权证据传播与最终执行处检查，不能只在 Gateway 加一个未经证明的 epoch 数字。

Message 业务提交是否必须受路由 epoch fence 约束，还需按操作分类决定。消息持久化与路由所有权不是同一事实；不能以治理简化为由擅自将 Broker 变成消息事务权威。现阶段要求先给出操作级允许/禁止晚到语义，再决定是否需要协议扩展。

成员维护方面，代码并非完全依赖人工恢复：BrokerMembershipScheduler 在 Leader 上扫描 PENDING 和 RAFT_COMMITTED，调用 BrokerMembershipAppService.resume，按原 operationId 继续；默认扫描间隔为 5 秒，PENDING 还需满足 recoveryDelay。该配置不是自动接管 RTO。

但 findConsistentVoters 对任何未终态成员变更返回空，reconcileFailures 新发起路径据此跳过；既有 FAIL_BROKER 则优先进入恢复。resume 对 PENDING 会重新进入原成员用例，仍依赖 Adapter 私有读取来识别部分完成状态。由此可知：

- 「不自动删除 voter」不等于「无需自动恢复已发起的人工成员维护」。维护恢复仍须保留，否则可能阻塞后续新接管。
- 在移除私有读取前，必须证明 add/promote/demote/remove 的未知结果可以收敛，或明确限制可支持的维护形态；仅禁止并发没有解决中断后的活性。
- 不能在超时后直接清除 PENDING 或放开闸门；真实配置可能已部分改变。具体恢复状态、证据来源和操作重试边界仍是设计阻塞项。

#### 重试结果与最终执行语义补充核查

源码进一步明确了三项边界，均尚未做故障复现或独立 Review：

1. ImMessageService.ensurePrivateMessageUnique/ensureGroupMessageUnique 在发现相同 messageToken 时抛出 RepeatException「消息已存在」。FrameProcessHandler 将 BaseException 转成失败结果，而不是在该路径读取并返回首次发送结果。因此目前可确认的是重复检测，不能宣称同标识重试已具备「返回首次成功结果」的完整语义。客户端是否将该错误解释为成功、是否查询原消息仍待查，不能把重复错误等同消息丢失。
2. ConnectionRegistry.writeFrame 调用 channel.writeAndFlush；非 EventLoop 调用线程会 future.awaitUninterruptibly()，该方法处没有显式等待期限。写入 accepted 只表示当前 Channel 写入处理结果，不表示接收方业务 ACK；SDK 调用超时也不证明该 future 或远端 Handler 已取消。这使「在途副作用有统一有限执行上界」不能从局部代码成立；Netty 装配及下游拒绝边界还需检查。
3. ConnectionCloseHandler 按 operationId/userId/sessionVersion 去重，ConnectionRegistry.closeConnections 按 userId + sessionVersion 选连接，不按路由 epoch。旧会话版本关闭不会直接匹配不同新版本，但同版本新建连接能否被晚到请求关闭，需要结合 Session 生命周期语义与竞争测试判断。不能将关闭连接的隔离语义和写帧通知混为一谈。

ReadIndex 配置方面，RaftRuntime.getOptions 直接构造 NodeOptions，设置初始成员、FSM、目录、选举及快照参数，本地没有显式设置 ReadOnlyOption。因此当前模式取决于实际依赖版本默认值；本轮未取得并核验固定版本 RaftOptions 定义，不将常见默认值写成已证事实。正式迁移核查须追到实际构建输入，而非仅阅读应用调用点。

这些事实不授权本任务重写消息幂等或 Gateway 协议。后续设计需分别确定：

- 重复发送是明确返回重复错误并由客户端查询原消息，还是返回首次持久化结果；同 token 不同内容如何拒绝，身份与会话范围如何约束。
- 写帧、关闭连接、发送、已读和撤回分别允许哪些已准入操作在 epoch 切换后完成；哪些必须在最终执行侧拒绝。
- 选择最终执行侧拒绝时，权威 fence 如何传播、如何在旧视图及分区下仍有效，以及是否增加接管等待；仅传 epoch 数字不构成完整协议。

#### ReadIndex 本地制品及消息确认入口取证

本轮找到本机 Maven 仓库中的 com.alipay.sofa:jraft-core:1.4.1 二进制及 sources JAR。读取源码并用 javap 交叉核对，RaftOptions 构造默认 ReadOnlySafe，NodeOptions 默认创建 RaftOptions。证据只约束这两个本地文件，不自动证明其发布来源、固定 commit 对应关系或当前私有制品的实际构建输入：

| 本地文件（~/.m2/repository/com/alipay/sofa/jraft-core/1.4.1/） | SHA-256 |
|---|---|
| jraft-core-1.4.1-sources.jar | 0aa1a58047ae16befbb2ac545d48f41a3f2028d08f7de9f06cddf555160a1ef3 |
| jraft-core-1.4.1.jar | bb263a996f3ab831859c579242b6b19eb978d78aaa6d9093e094896e0af26d1b |

可复查命令：javap -classpath ~/.m2/repository/com/alipay/sofa/jraft-core/1.4.1/jraft-core-1.4.1.jar -c com.alipay.sofa.jraft.option.RaftOptions。所查构造字节码含 ReadOnlyOption.ReadOnlySafe 并写入 readOnlyOptions。固定 commit 的历史 /tmp 源码路径本轮未找到，且原构建 job 路径环境变量在本会话未设置；不据本地同版本 JAR 冒称固定提交已核验，G4 仍未通过。

消息结果方面，已确认服务端存在现有查询入口：

- PrivateMessageController：GET /private/queryMessageDetail，参数 chatId、messageToken；userId 来自认证 UserContext，应用检查会话归属后查询原消息。
- GroupMessageController：GET /group/queryMessageDetail，同样接收 chatId、messageToken，并从认证上下文获取 userId；群聊应用及存储查询完整授权仍待继续核查。
- 因此「重复发送错误 → 查询原消息确认」可以作为复用既有入口的候选，不必预设一定要改发送 API 返回首次结果。查询暂未找到不能直接解释为首次发送未提交：旧请求仍可能在途，需保留原 token 及明确结果未知状态。
- 仓库搜索找到服务端、测试及压测脚本的 token 使用，尚未定位到实际终端客户端实现；不声称客户端已有自动查询和同标识重试闭环。

候选优先级为先复用既有消息详情/历史查询，不因 JRaft 治理迁移顺手重写消息 API；若现有入口不能满足内容冲突、安全或补偿语义，再单独提出所属模块变更。

#### 本地 1.4.1 ReadIndex 与成员列表实现核查

继续读取上述同 SHA sources JAR 的 NodeImpl，定位到以下实现点；这些行号只对应该本地文件，不绑定固定上游 commit：

- getQuorum（约 1590 行）按 conf.getConf().getPeers().size()/2+1 计算；ReadOnlySafe 分支（约 1654 行）从 conf.getConf().getPeers() 选择心跳对象。
- readLeader（约 1611 行）在 quorum <= 1 时直接返回 lastCommittedIndex；多 voter 分支先要求 lastCommittedIndex 的 term 等于当前 term，再按读取模式执行。因此不能把所有成功 ReadIndex 一概描述为「本次收到了多个节点心跳」。
- listPeers（约 3084 行）和 listLearners（约 3110 行）均检查 Leader 状态，再返回 conf.getConf() 的相应列表；二者不构成一个原子的完整配置查询，也没有返回 local-applied 配置索引。

这些局部方法未直接暴露 oldConf、joint 阶段或完整本机已应用快照。不能仅从读取当前 peers 和一个阈值，推导 joint 期间已同时确认新旧两组多数；也不能据此认定 JRaft 的整体 joint 安全存在缺陷，仍须核查配置上下文、复制/提交路径和版本约束。

对候选设计的直接约束是：

1. 不将 ReadIndex + listPeers/listLearners 组合命名为「稳定且已应用的完整成员快照」。不同调用之间的配置变化仍需处理。
2. 固定成员的正常运行阶段与显式维护阶段必须分别论证。正常阶段的简化不能被默认为维护中的通用证明。
3. 若首版只支持稳定配置下自动接管，维护中发生故障的恢复支持范围必须显式取得用户批准；目前 D1 并未授权任意时长的维护故障人工等待，不能用运维约定静默削弱自动接管要求。
4. 不自动移除 voter 只去掉故障路径主动发起配置变更，不会消除已经发起的配置变更、旧配置数据或外部 CLI 引入的变化。

研究结论仍是保留现有补丁。官方替代路线下一步必须绑定固定源码，并形成维护状态机或证明稳定性可观测的公开契约；继续堆叠 list 查询及本机 Leader 标记不能补齐缺口。

#### 成员变更完成回调：可行性收敛

继续检查同 SHA 本地 sources JAR 的 NodeImpl.ConfigurationCtx 与 unsafeRegisterConfChange：

- ConfigurationCtx 存在 CATCHING_UP、JOINT、STABLE 阶段，nextStage 在 STABLE 分支 reset；onConfigurationChangeDone 会检查任期并推进阶段。说明正常成员操作的完成有内部阶段机制支撑，不能把官方完成回调贬为单纯「已收到请求」。完整回调触发点及 FSM 顺序仍需追踪，不能推导所有 Follower 均已应用。
- unsafeRegisterConfChange 先检查 Leader，再检查 confCtx.isBusy，忙时返回 EBUSY；之后若完整 Configuration 与目标相等则返回 OK，否则启动变更。局部代码呈现「内部串行受理、同目标重复可完成」的可能恢复路径，但尚未证明该路径在换届、joint 恢复及全部 learner/voter 转换下等价于原私有读取。
- addPeer/removePeer 入口在调用上述逻辑前还检查单个 peer 是否存在。因此重新发送单步 add/remove 可能先触发前置条件异常；不能把 changePeers 的同目标行为直接套到现有 Adapter 的单步方法上。

由此形成两个明确判断：

1. **现有 Adapter 原样改用官方列表不可接受。** 其部分完成恢复、Follower 资格和 stable 检查仍缺等价证据。
2. **以持久化目标配置驱动显式维护，值得做有界验证。** 候选为预先登记完整目标 voters/learners、稳定操作身份及代次，由当前 Leader 通过受支持公开配置变更接口推进，busy 时等待既有变化，未知时沿同一意图核对与收敛；只有当前维护完成证据满足条件后才释放相关闸门。该候选不是已批准实现，不保证 changePeers 一次覆盖所有所需 learner 操作。

候选必须先用固定源码证明：Configuration 相等包含哪些字段、同目标受理能证明到哪一阶段、换届后 confCtx 恢复如何工作、变更回调与业务操作提交如何绑定、旧请求晚到能否覆盖新目标。外部 CLI 必须进入同一独占维护边界；当前没有可靠的跨进程独占设计，不能以「运维不并发」代替协议。失败或无法取得这些证明时继续使用私有读取。

建议下一阶段只做成员恢复有界原型，不同时改 observer 和消息 API。原型退出场景应包含：相同目标重试、相同 operationId 不同目标拒绝、joint 中断/换届、移除当前 Leader、learner/voter 两步转换部分成功、回调丢失、旧维护请求迟到及恢复后重新允许自动故障接管。原型开始前仍需固定源码、具体协议与独立设计批准；本轮没有创建或运行原型。

#### 目标配置候选的进一步核查与准入边界

同 SHA 本地源码新增核查：

- NodeImpl.becomeLeader 调用 confCtx.flush(conf.getConf(), conf.getOldConf())。flush 对无 oldConf 的情况进入 STABLE，否则进入 JOINT，并写入配置日志；内部 busy 用于阻止首条配置提交前受理新配置变更。这为换届后继续内部配置阶段提供源码依据，不代表应用维护操作已自动完成。
- ConfigurationCtx.reset 清理上下文、推进内部 version，并异步分发 done；成功调用路径在 nextStage 的 STABLE 分支。应用不能用回调收到时间推算配置的全局应用时间，也不能把内部 version 当作应用 operationId。
- Configuration.equals 同时比较 peers 与 learners；peers 的具体容器语义需注意，不能假定它等同本项目的无序领域 Set。未来目标必须规范化顺序并验证真实 PeerId 等价规则，避免逻辑相同却触发冗余配置变更。
- changePeers 将传入 Configuration 交给 unsafeRegisterConfChange；resetLearners 则复制当前配置再设置 learners。接口名称不能单独说明完整目标的实际覆盖范围，learner/voter 重叠及晋升/降级有效性仍需验证。
- Node.resetPeers 文档明确不保证一致性和共识，面向多数派死亡后的人工恢复；禁止用它实现本设计的重试、去 busy 或快速接管。它与经共识的 changePeers 完全不同。

据此可将候选恢复顺序收敛为：登记唯一维护意图及规范化目标 → 当前 Leader 验证该意图仍有效 → 调用获支持的配置接口 → busy 时等待内部恢复、未知时保留同一意图 → 获得可核实完成证据后提交业务终态。每一步尚需明确线性化点及失败处理，不能直接作为实现伪代码。

关键未解反例是：旧 Leader 或旧工作线程在意图 A 检查通过后暂停，新 Leader 完成 A 并启动 B，旧线程恢复后重新提交 A。仅在调用前读一次业务 revision 不能原子绑定外部配置调用；若该线程此时重新成为 Leader，单纯 isLeader 检查也不能替代操作代次保护。必须证明旧工作可被可靠废弃，或官方调用拥有可用的期望任期/代次契约及与业务闸门的绑定方式；不能让超时旧请求变成对新目标的回退。

因此，本轮不批准直接用 changePeers 替换现有两步晋升/降级。源码支持「值得验证」而非「已经等价」。下一步应针对上述一个反例和实际目标转换做固定源码下的有界验证；在获得具体协议批准和独立 Review 前，不创建生产实现或原型工程。

#### 旧维护请求的任期保护：公开 API 核查结果

检查同 SHA 本地 Node 接口、Task 及 NodeImpl 后，发现普通业务日志与成员变更的保护能力不同：

| 能力 | 所查本地 1.4.1 接口与实现 | 设计含义 |
|---|---|---|
| 普通业务提交 | Task.expectedTerm 默认 -1，可显式指定；NodeImpl.executeApplyingTasks 会拒绝不匹配当前任期的任务 | 可用于约束业务日志受理任期，但还需要业务 revision/操作身份规则；本项目当前 submit 未设置该值 |
| 配置变更 | Node.changePeers(Configuration, Closure) 没有 expectedTerm/operationId 参数；所查实现 under writeLock 检查当前 Leader/busy | 能阻止非 Leader 或忙状态调用，但不能直接表达「只接受维护 A 在任期 T 的请求」 |
| 本机当前任期 | 所查 NodeImpl.getCurrentTerm 是包内 OnlyForTest，不是 Node 公开契约 | 不能通过转型、反射或复制内部锁规避官方公开 API 目标 |

因此，在业务日志中写入一个带 expectedTerm 的维护意图，不会自动把随后独立调用的 changePeers 绑定到同一任期；先提交 barrier 再调用仍有暂停和换届窗口。单纯增加一次 ReadIndex、revision 重读或原子变量检查，也不能消除检查与外部配置调用之间的窗口。

本轮结论是：**「完整目标配置 + 普通 changePeers 重试」尚不能闭合旧调用覆盖新目标的反例，不应进入实施。** 这不是不可能性证明；是否能通过受控执行生命周期、公开回调时序和禁止旧执行者重新获得权限解决，仍需完整协议，而不是再增加一个检查。

后续可行性研究须在以下路线中明确收敛：

- 保留私有读取及现有行为，先审计原成员维护是否同样存在检查到调用的窗口；私有读取本身也不是任期 CAS，不能把它当作该反例的自动修复。
- 核查其他官方候选版本是否提供可用的配置变更预期任期/配置代次契约，并核实实际语义；本轮不声称其他版本均缺少能力。
- 若采用执行者生命周期隔离，必须证明所有旧在途调用已被拒绝或不再可能执行，才允许新维护意图；进程重启、同进程重新当选及跨进程管理都要覆盖。取消 future、设置 cancelled 或客户端超时不算停止证明。

未证明前不得扩大成新协调平台、引入内部 API 补丁或将成员维护转为不受控外部 CLI。下一步需要固定版本能力核查或具体受控执行协议，而非生产代码调整。

#### 当前执行保护与制品来源复核

本轮复核当前应用机制，不能把「缺少公开 expectedTerm」直接认定为现有竞态已经复现：

- BrokerCluster.checkAdmission 在日志应用的聚合边界检查 expectedRevision 与 hasBlockingOperation，已具备跨 Leader 的已登记操作互斥；这不是仅靠 Scheduler 本机标志。
- BrokerMembershipScheduler.running 只串行该调度入口；BrokerMembershipAppService 和 RaftRuntime.changeMembership 所查部分没有覆盖所有人工/恢复入口及异步延续的统一执行屏障。Runtime 仅在调用前检查 isLeader，并为返回 future 设置 orTimeout；future 超时不证明底层已停止。
- 因而需要同时验证「业务意图不能并发登记」和「旧意图的执行者不能迟到调用」。前者已有保护，不能误写成全部没有；后者仍需用可控暂停反证或完整执行生命周期证明。

同时追踪同 SHA 本地源码 ConfigurationChangeDone：其携带 term，成功时调用 onConfigurationChangeDone(term)，配置日志由 unsafeApplyConfiguration 经 ballotBox.appendPendingTask(newConf, oldConf, closure) 登记。这说明库内部配置阶段完成有任期和新旧配置机制，不应把应用 API 缺少 expectedTerm 描述为 JRaft 内部没有任期保护。应用旧请求在新任期被重新受理，是另一层问题。

本地二进制 MANIFEST 和 META-INF/maven pom.properties 仅含 Java/构建工具信息及 com.alipay.sofa:jraft-core:1.4.1 坐标，未提供可用 Git commit 标识。Maven 版本号及本地 SHA 不能单独补齐固定源码来源链；现有局部源码结果不能升级为正式候选版本验收。

后续最窄验证目标固定为：在一个成员操作已登记且存在暂停执行者时，换届完成该操作并尝试登记下一操作，再释放旧执行者，观测是否能再次进入配置变更。必须同时保留正确实现的拒绝路径与故意删除执行保护的负例；本轮未创建测试或假定反例已发生。

#### 同操作重复推进：当前代码的具体待复现时序

本轮核对重复登记链路后，已将抽象反例收窄到现有方法，不需要先假设 changePeers 新实现或换届：

1. BrokerCluster.submitOperation 对已有操作先调用 checkRepeatedIntent；匹配时返回 DUPLICATE，PENDING 不被拒绝。
2. BrokerClusterConsensusResult.hasPendingOperation 同时接受 APPLIED 与 DUPLICATE，只要携带 PENDING 快照就返回 true。因此同一操作的两个推进者可以都进入外部成员步骤；登记幂等不等于执行 single-flight。
3. BrokerMembershipAppService.addLearner 在上述判断后调用 Adapter.addLearner。Adapter 先读取 membership 判断是否已存在，再单独调用 raftRuntime.addLearner，读和变更之间不是原子操作。
4. BrokerMembershipScheduler.running 只约束调度调用，不覆盖并发人工重试或其他已取得 PENDING 结果的延续；Runtime.orTimeout 也不会停止旧线程。

待确定性复现的最小场景：

| 顺序 | 执行者/状态 | 动作 |
|---|---|---|
| 1 | A 的推进者 X | 取得 PENDING，Adapter 读取目标 learner 尚不存在后，在实际 addLearner 前暂停 |
| 2 | A 的推进者 Y | 相同操作幂等登记后继续，添加 learner 并完成业务 A |
| 3 | 新维护 B | 在其余准入条件满足、目标保持 INACTIVE 时移除该 learner，完成业务 B |
| 4 | 旧推进者 X | 恢复执行先前已通过检查的 addLearner，若仍为 Leader 且配置不忙，可能把已移除目标加回真实配置 |
| 5 | X 的业务提交 | 即使后续业务提交被拒绝或判为重复，也不能据此认为外部配置副作用已撤销 |

这是静态构造的可执行候选反例，尚未运行，不记为已证实故障；需验证初始 learner 状态、B 的移除准入、真实回调时序及最终实际配置。应优先用已有测试 seam 设置两个可控闸门，先证明当前行为，再评估最小保护；不能以 Thread.sleep 或真实调度概率替代。

由此明确缩小后续问题：不能仅增加「新操作登记互斥」或「同 ID 请求返回 duplicate」；必须覆盖同操作并发推进、旧延续实际停止和后续操作开始之间的关系。单机 single-flight 可能覆盖本时序的一部分，但跨 Leader 的恢复与旧线程重新获权仍需独立证明，不据此预选修复方案。

#### 最小反例的准入及测试范围核查

本轮确认 BrokerNode.checkOperation(REMOVE_BROKER) 要求 INACTIVE，不要求 learner 已 READY；START_LEARNER_SYNC 完成后变为 LEARNER_SYNCING 且保持 INACTIVE。BrokerCluster 的最低 voter 数检查仅在目标为 VOTER 时扣减。因此在其余绑定、epoch、revision 及操作条件满足时，前述「添加完成后移除未就绪 learner」不会仅因 LEARNER_SYNCING 被实体规则拒绝。仍需通过真实聚合执行验证，不能以静态条件替代运行结果。

现有 BrokerClusterAppServiceChangeTest 提供 StatefulClusterAdapterFixture，并已有：

- pendingMembershipRejectsAnotherChangeBeforeExternalSideEffects：覆盖未完成 A 阻止不同 B 的登记。
- membershipRetryDoesNotReapplyEarlierRoleChanges：覆盖已完成 A 的顺序重试不重复调用 Adapter。

二者都不能替代「相同 A 在 PENDING 时有两个推进者，旧推进者跨过 B 后继续」的回归。AppService fixture 模拟 Adapter，可用于验证重复 PENDING 会进入外部步骤；不能单靠模拟 Adapter.addLearner 直接修改 Set 就声称验证了真实 Adapter 的读取到调用窗口或 JRaft 配置行为。

最小验证应分两层且按顺序执行：

1. 应用层：使用真实聚合与状态机登记/提交，两个可控推进者共享 A，冻结 X，完成 Y 和移除 B 后释放 X；断言操作阶段、业务节点是否存在以及外部调用次数。该层只证明应用编排是否继续过期动作。
2. Adapter/Runtime 边界：在真实 Adapter.membership 读取完成与 Runtime.addLearner 受理之间设置测试侧闸门，验证它是否重新校验、拒绝或执行旧动作；若用 mock Runtime，结论只到配置调用受理边界，不冒称真实 Raft 已提交。最终配置分歧需真实拓扑另证。

不新增生产 no-op、测试专用构造或任意 sleep。后续精确文件范围优先为既有 BrokerClusterAppServiceChangeTest 及按现有模式选择的 Adapter 测试，命令、依赖可用性、输入冻结和独立设计审查通过前不执行新增实验。本节为验证设计，不新增竞争 execution plan。

#### 最小基线的实际执行结果

为从静态推理转向运行证据，本轮实际执行：

- ./scripts/verify.sh readiness：exit0；Java 21、Maven 3.8.4、Node 26.10.0，保留 PATH world-writable 警告。
- mvn -o -q -pl im-broker/im-broker-server -am -Dtest=BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test：exit1，在 im-raft 依赖解析阶段停止，缺少 com.co.kc.thirdparty.sofa:jraft-core:1.4.1-imchat.1。尚未运行目标测试，不记作测试断言失败。

本机 ~/.m2 对应私有坐标目录仅见 jar/pom 的 .lastUpdated，无实际 JAR/POM。未使用官方 1.4.1 冒充私有接口、未跳过 im-raft、未复用旧 target 宣称通过，也未重复启动相同构建。StatefulClusterAdapterFixture 真实应用领域状态机，但模拟了外部成员方法，仍需按前述两层范围区分证据。

新增反例测试尚未编写；独立设计审查未取得。当前优先恢复原已批准私有制品的可验证输入和基线，再做最小反例；本研究不另开私有构建、不覆盖原任务证据或提前安装来源未核验制品。

#### 恢复到固定源码输入（当前事实）

重新读取 PROGRESS.md 和当前计划末尾后，确认其他收口工作已推进：原任务双轮 capture 已结束，现处于外部模型输入审核；这不等于 reviewed lock、verify-lock 或消费者采用已通过。此前「固定源码未找到」和 Docker 不可用只能作为历史，不能继续当作当前结论。本研究不重复启动 capture，不安装未批准制品，不覆盖原任务进度。

本轮成功定位既有源码归档：

- 路径：/Users/kc/.claude/jobs/bb58725d/tmp/jraft-rebuild/download-20261028/upstream-source.tar。
- 实测 SHA-256：6c43b6b09f480f1371b7ec42800fa22bc18ace419ef21ffbbc9c3a382e594a05，与当前 provenance.sourceArchiveSha256 精确匹配；来源映射仍受 provenance 中既有 HTTPS 固定提交及清单核验的证据边界限制，不新增签名主张。
- 直接只读 tar 内容，不解压执行：固定归档 Node.java 第 220 行为 changePeers(Configuration newPeers, Closure done)，该接口无 expectedTerm；RaftOptions.java 第 89 行默认 ReadOnlySafe。两项结论现已从本地来源不明 JAR 的观察，补齐到现有固定提交来源记录下的归档。

这是实际解除的一项研究输入缺口：后续可直接从固定归档核查具体实现，无需继续检索 master。

后续只读逐字节比对已完成：以下六文件在固定归档与此前分析的本地 sources JAR 中完全一致。该结果将这些文件内已检查的源码行为绑定到现有固定提交来源记录，不证明本地二进制来源，也不证明运行行为。

| 文件（com/alipay/sofa/jraft/ 下） | 固定归档文件 SHA-256 |
|---|---|
| Node.java | 339905b21a05979c56ef6ed0492ce3f24722e5464a5d2afe91cd722ce51866aa |
| core/NodeImpl.java | 900b37e09a90cf45528ae6e2fa662b4def72bac1936dc36d1963f8ad31ca0aac |
| conf/Configuration.java | 2a33271259eb50a3612fa39b7d9ab73402f471c6118d25d1f5cd19f8afe57f82 |
| entity/Task.java | a1f61b5725fbc2bf4bf6f6e17c0f698b76f27b62f1fa3cd0624644bbf0ed04e2 |
| option/NodeOptions.java | b1a0305929b8c7772b03c6c9bb218d2cc6040fb0c359a1ac17a87e5e07033e5e |
| option/RaftOptions.java | 5c5506f685ba85b2065df7f5e017e26523c64f1656664c6ce716f7a303a42ae5 |

因此，Leader-only 成员列表、配置 busy/同目标受理逻辑、内部换届阶段恢复、Task.expectedTerm 与配置变更签名差异等已查源码结论，不再仅依赖来源未绑定的缓存源码。未比对的 FSMCaller/ReadOnlyService 等文件和完整回调时序不包含在此次六文件一致性结论中。

当前可行性结论保持不变：官方公开接口不是现有私有读取的直接替代；目标配置维护与 observer 解耦仅是候选。下一步需要独立审阅最小反例验证范围，然后在私有制品可消费的前提下实际运行两层测试。不继续以重复静态分析或文档篇幅替代运行证明。

运行验证仍有独立阻塞：当前消费者私有坐标未安装，原任务输入审核和采用门槛尚未完成；最小基线的前次依赖失败仍有效。必须沿原任务通过审核和消费准备后再跑，不能把 capture 成功直接当作允许安装或消费者测试通过。

#### 消费者验证已通过，进入反例基线前

在 verify-lock 双轮成功后，按原计划将 build-1 的三项 payload 安装到隔离 `/tmp/jraft-artifact-m2`，未写默认 Maven 仓库、未发布。先前因隔离仓库缺少项目依赖的 offline 失败已保留；随后仅将本机已有依赖复制到该隔离仓库，未改 POM、版本或网络来源。

最终消费者命令：

```bash
mvn -o -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-raft -am test
```

实际 exit0。fresh 结果为 3 个测试类、51 tests、0 failures、0 errors、0 skipped：`RaftRuntimeTest` 24、`RaftMembershipTest` 2、`RaftNodeSettingsTest` 25。日志保存在 `/tmp/im-raft-consumer.log`。这证明当前私有 payload 可以被 `im-raft` 测试消费，不证明全仓消费者、单一实现检查、生产采用或完整验收。

下一步是按原计划做实际 dependency tree/单实现核验，然后运行已独立批准的两层 stale-executor 调查；调查仍不修改生产代码。消费者测试成功不等于官方 JRaft 替代方案成立。

Dependency tree 已在隔离仓库执行并通过，唯一 JRaft 依赖为 `com.co.kc.thirdparty.sofa:jraft-core:1.4.1-imchat.1`；未发现 `com.alipay.sofa:jraft-core` 传递项。effective POM 同样只解析到该私有 GAV。证据日志：`/tmp/im-raft-dependency-tree.log`，effective POM：`/tmp/im-raft-effective-pom.xml`。

第一层调查测试已按独立 Review 的获审范围加入 `BrokerClusterAppServiceChangeTest`，未改生产代码、共享 fixture、POM 或依赖。测试先经历编译错误和错误状态类型断言，随后最小修正后目标测试 exit0，fresh XML 为 1 test、0 failure/error/skip；完整该测试类随后 exit0。测试观察到同一 PENDING learner 操作的两个推进者都能到达外部 add 边界，之后移除完成，旧推进者仍能完成其延续；该结果仅是受控 fixture 的应用编排 characterization，不是 JRaft 真实配置回退证明。

第二层真实 Adapter + Runtime 构造 mock 调查已执行，新增测试仅位于获审的 `BrokerRaftAdapterTest`。测试使用 `MockedConstruction<RaftRuntime>`、真实 `BrokerRaftAdapter.addLearner/removeMember` 和有界 executor/latch：旧 add 调用进入 Runtime API 后暂停，随后 remove 返回 DUPLICATE，释放旧调用后 Runtime mock 仍完成 add。目标测试 exit0；随后两个获审测试类合并运行 exit0：`BrokerRaftAdapterTest` 14 tests、`BrokerClusterAppServiceChangeTest` 45 tests，均 0 failures、0 errors、0 skipped。

上述初次运行虽通过，但独立实现 Review 已否定其满足完整获审时序的结论，当前不能拼接两层结果证明「实际移除后旧添加重新执行」。第一层只延迟 first future 完成，未执行旧添加动作；第二层缺少 Y 的成功添加，remove 仅为空目标 DUPLICATE。有效证据限于重复外部入口、旧结果延续和空目标移除场景。真实 JRaft 回退仍未证明，当前没有生产修复。

独立 Reviewer `term_5435c371-5777-4509-83ab-f9afbd223a91` 实际返回 **Revise**，报告 `/tmp/imchat-stale-executor-review/implementation-review.md`。F1 要求第一层保存并在 B 完成后执行旧添加动作；F2 要求第二层实际执行 Y 添加和 B 移除，补精确拒绝对照；F3 要求补阶段、绑定、revision/epoch、旧返回状态及有界资源退出证据；F4 要求收缩文档结论。原 Review 的 Revise 保留。F1–F3 的测试修订已完成并执行，F4 收缩证据说明已同步；独立复审已实际返回局部 Accept，原 F1–F4 在本轮冻结调查范围内关闭；不由实施者自行标记。保留原通过 XML 作为较弱场景的历史证据，不记作调查完成或 Accept。

修订轮证据在 `/tmp/imchat-stale-executor-review/revision-1/`：第一层现保存旧添加 Runnable，完成真实聚合的 Y/A 和 B 后执行旧模拟动作，再完成原 future；核对旧返回 DUPLICATE、业务节点/绑定仍缺失、完整 committedState 不变、A/B 终态以及外部模型重新出现 endpoint。第二层现只暂停第一次 Runtime mock 添加，第二次真实 Adapter 添加更新原子不可变 membership，真实 Adapter 移除返回 APPLIED 且调用 removeLearner，再释放旧添加；同时核对已存在 learner/voter/空目标移除对照及精确变更次数。executor finally 释放闸门、shutdownNow 并有限等待退出，Adapter/构造 mock 随后关闭。

本轮 readiness exit0（PATH 权限警告保留）；两项聚焦调查 exit0，随后两个测试类 59 tests、0 failures/errors/skips、exit0。`focused-result.json` 和 `classes-result.json` 保存完整 argv、UTC、实际退出码及 before/after SHA，classes 的两份 XML 另存并记录 SHA。运行中两测试源码未变。当前仅可表述为受控外部模型与 Runtime mock 中观察到 Y 添加、B 实际移除及 X 迟到添加顺序，不提升为真实 Runtime/JRaft 接受、落盘、换届或生产回退证明。

#### 维护执行保护的协议评估（未批准生产实现）

在两层调查局部 Accept 后，独立 Reviewer 对 single-flight/串行执行器、意图重读和 leaderTerm 检查的组合完成有界只读评估，报告 `/tmp/imchat-stale-executor-review/protocol-feasibility.md`。结论 **Revise：不接受将该组合视为完整跨任期生产修复**。这不重新打开已接受的测试实现；它限制的是下一阶段修复主张。

具体反例为：P 在任期 T 最后一次检查 A 后暂停 → P 失权、Q 完成 A 和 B → P 在新任期重新当选且配置不忙 → 旧调用才进入 Node API。Node 内部为本次受理绑定新任期，不能识别调用来自旧 A。该跨任期时序尚未运行；本机队列和回调后验检查不能据源码排除它。即使增加成员 expectedTerm，也还需解决同任期 A/B 意图次序，不能把任期与业务代次混为一谈。

候选路线及边界：

| 路线 | 当前可作出的判断 | 主要退出条件 |
|---|---|---|
| 保留依赖并做全入口 single-flight/串行化 | 可研究同 Leader 本机重复推进缓解，不是完整 fencing | 覆盖人工/调度/恢复及两步链，原生工作结束前不因 timeout 解锁；单独验证且保留跨任期缺口 |
| 采用有受理期望任期/代次契约的官方接口 | 固定版本不具备已核实能力，其他版本未核查 | 真实公开契约与受理原子边界、同任期意图排序、回调和未知结果证明 |
| 执行生命周期 fencing | 尚无具体获批准协议，不推荐直接重启/重建 Runtime 作为修复 | 旧域确实不可执行后才复用权限；覆盖暂停线程、原生在途工作、重启及自动接管活性 |

后续研究优先准备**真实 Runtime 受理边界的单一确定性实验设计**：保留同 Leader 条件，只在测试侧暂停已读 membership 的旧调用，驱动实际 learner 添加/移除后释放，记录真实 JRaft 返回及最终配置；不得从 mock 的 APPLIED 推断结果。先核查可用 test seam，不新增生产 seam 或修改 patch；若无法在获支持边界构造闸门就停下重新设计。该实验尚未获独立批准或执行，也不是新的 active 任务。

在此之前不增加生产锁、不调整 Lease/quarantine、不取消自动接管、不切换官方依赖。原 applied-read 补丁仍用于保留现有事实读取能力，不冒称已经解决调用代次问题。当前 readiness 实测 exit0，PATH 权限警告仍保留。

#### 修订调查实现的独立 Accept

Reviewer `term_5435c371-5777-4509-83ab-f9afbd223a91` 已返回 `/tmp/imchat-stale-executor-review/revision-1/review.md`：**Accept，findings none，仅限两层受控调查实现**。逐项核对关闭 F1–F4，三份冻结源码/草案 SHA 与现场一致，两测试 diff 与冻结 diff 相同，实际命令/退出记录和 59 项 XML SHA 匹配。

可接受结论限定为：真实聚合 + 受控外部模型、真实 Adapter + Runtime mock 各自观察到迟到添加动作发生在实际模拟移除后；不是一条真实端到端共识执行链。真实 Runtime 领导权、busy/joint、换届/重启、最终配置落盘和用户影响仍未证明，无生产修复或迁移批准。

保留证据限制：focused 轮没有单独归档 XML，具体方法执行由完整类轮保存的 XML 证明；Reviewer 未重跑 Maven，不将 quiet 空日志当测试执行数证明。不把局部 Accept 扩大为 full、原 T3 完成、制品采用或设计整体批准。

#### 最小实验独立审查结果

本轮已通过 Orca 启动独立上下文 Reviewer，handle 为 term_5ed05f21-5980-4d5d-85fd-55e92a235a43；报告 /tmp/imchat-membership-experiment-review.md 已实际返回。结论 **Approved，仅限 test-only 两层调查设计**，无阻止该调查的必改 finding；不批准生产修复、依赖替换、整体迁移或当前计划验收。先前「没有可用独立 Reviewer」为历史限制，现已解除。

Reviewer 独立核实添加后的 LEARNER_SYNCING/INACTIVE 允许移除，重复 PENDING 能继续推进，以及现有新操作登记互斥真实存在。获审最小文件范围仅为既有 BrokerClusterAppServiceChangeTest.java 与 BrokerRaftAdapterTest.java 内的新增测试及 test-local helper；不改共享 fixture、生产 seam、Runtime/POM 或依赖。

必须保持的实验边界：

- 第一层保存第一次模拟外部动作，不能先修改集合再仅延迟 future，否则不构成晚到添加；通过真实聚合完成 Y/A 和 B 后才释放旧动作。
- 第二层使用真实 Adapter 及既有 MockedConstruction<RaftRuntime> seam，在首次 Runtime 方法入口、记录受理或修改模拟状态之前暂停。这里只证明旧调用到达 Runtime API 边界，不证明真实 Runtime 或 JRaft 已受理、提交或落盘。
- 断言包括 A/B 阶段、绑定、节点、revision/epoch、调用事件顺序及对照拒绝路径；所有并发等待有界，finally 释放并回收 executor，不裸并发访问共享 Set。
- 本轮调查为 characterization，不把安全期望 RED 当 passing，不新增生产修复；未来修复须另立安全不变量并 RED/GREEN。

执行仍须先恢复准确私有依赖并取得基线；Review 未授权安装制品或跳过原任务消费门槛。报告尚在临时证据路径，结论摘要在此保留。此处为实验设计批准的历史检查点；后续已新增和运行测试，但实现 Review 为 Revise，实际状态以上文为准。

#### 历史研究停止点与恢复输入

本轮两次有界上游检索未获得官方最新或其他固定版本 Node.java 的可核验方法签名及 revision。结果主要为历史 issue，不能证明当前版本具备或缺少配置变更 expectedTerm 能力；不把检索失败转成 API 不支持的结论。停止继续以相同关键词重复搜索，也不拿其他 Raft 实现的同名接口代替 SOFAJRaft。

因此本设计当前为「需求已确认、官方替代证明 blocked」，不是「可以实现后再补测试」。后续恢复至少需要以下一种事实输入：

- 可定位的固定官方 commit 源码或来源已核验的候选发布制品，以核查 Node 配置 API、内部配置恢复、完成回调及任期保护；现有本地 1.4.1 JAR 只支持该 SHA 下的局部结论。
- 具体的受控维护执行协议及独立设计 Review，明确旧执行者失去权限与新意图开始的顺序，覆盖同进程重新当选和跨进程旧调用；仅本机锁或 cancelled 标记不能满足条件。

源码核查成功也只解除研究输入缺口，不自动通过 G1–G4。当前批准范围不包括下载并执行新版本、创建原型工程或生产实现；需要新实验时先固定来源、文件范围、验证命令及退出条件。现有自动接管和补丁保持不变，当前 Bolt 验收与提交边界不受本研究影响。

#### 下一阶段的证明闸门

| 闸门 | 必须提供的证据 | 不满足时 |
|---|---|---|
| G1 下游晚到语义 | 按写帧、关闭连接、私聊/群聊发送、已读、撤回等操作分类；构造旧调用跨过 quarantine 后完成的确定性反例或安全证明 | 不宣称全链路 fencing 完整，不缩短安全窗口 |
| G2 observer 授权 | 精确模型、固定阈值、撤权、旧背景拒绝及 quorum 存在时的恢复活性矩阵 | 保留现有 voter 资格读取 |
| G3 成员维护恢复 | 逐操作处理外部部分成功、回调丢失、换届和重启，证明不会永久阻断自动接管 | 保留私有成员读取及现有恢复，不直接下线恢复 Scheduler |
| G4 官方能力 | 固定版本、ReadIndex 模式、公开 API 源码/契约、构建依赖和真实拓扑证明 | 不换依赖，不删补丁 |

G1–G4 为设计退出条件，不是执行计划或新增 active 任务。涉及 Message/Gateway 协议扩展时，必须先获得对应模块语义批准并独立 Review。

#### 恢复时延：当前默认值不等于 RTO

BrokerRoutingProperties 的 Java 默认值为 maxLeaseDuration=4 秒、maxClockDriftAllowance=100 毫秒、maxSchedulingPause=2 秒、networkSafetyMargin=500 毫秒，因此默认 quarantineDuration 为 6.6 秒；保守 leaseGrantDuration 为 3.7 秒。部署覆盖可能改变这些值，本轮未核查实际环境配置。

6.6 秒只是当前默认隔离等待项，不是完整恢复耗时：当前未确认分支还要先等待 fence RPC 汇合，并叠加故障观察、可能的选举、共识提交、Lease 安装及 Gateway 重建。全部 ACK 分支可以省去 quarantine 等待，但 ACK 仍需等待本机在途副作用排空。

后续先分段测量，再评估减少等待或并行化。优先检查观察调度、RPC 汇合、授权和 Gateway 重建的关键路径；Lease 时长及安全余量不得先缩小再补证明。

#### 显式成员调整

仅 Leader 发起官方公开成员变更；在操作前确认稳定可管理状态和多数派，禁止与路由操作并行。官方完成回调只解释为该接口文档保证的完成，不冒充所有节点本地应用完成。

成员维护与路由操作的互斥必须显式定义。不能简单规定「未知成员操作一直关闭全局闸门」，却仍宣称满足自动接管要求；维护过程中发生故障、换届或重启的自动恢复边界是设计阻塞项。

超时、中断或 Leader 切换后标记结果未知，不盲目重试非幂等步骤，不推断回滚。官方管理查询和审计可协助确认，但不冒充 local-applied 证明。若安全恢复仍需私有能力，则保留该能力，不能仅把维护移到应用外就宣称竞态消失。

所有管理动作必须有明确认证、授权、操作标识、原始期限和审计；未满足前不新增在线管理接口。

### 迁移顺序与边界

1. 用户提交当前已验收实现后，记录实际提交作为迁移基线，不提前写入虚构 revision。
2. 核查官方候选版本、发布来源、支持 JDK 和实际 API，补齐逐入口资格、维护互斥和未知结果恢复证明；定稿后取得用户及独立设计批准。
3. 在保留私有依赖期间实现获批的新证明链，保留自动接管，针对旧资格拒绝、故障恢复与维护交叉做差分回归；证明不成立则停止迁移。
4. 重构 im-raft 对外 Interface，只保留真正需要的官方能力；私有 applied membership 不留误导性兼容壳或伪实现。
5. 切换官方依赖，检查实际 dependency tree 和单一 JRaft 实现；源码无私有 API 调用后再删除 recipe 和补丁的有效入口，保留必要历史来源说明。
6. 完成新冻结输入的完整验收及独立 Accept；不可复用迁移前 full 为新依赖背书。

禁止跨过第 2、3 步直接换依赖。存量部署不支持未经评估的混合行为版本；回滚须同时恢复二进制、依赖和治理配置，涉及已发生的 Raft 成员变更必须由运维确认，不能靠回退 JAR 自动撤销。

### 验证策略

- 静态核对：RaftRuntime 补丁调用、BrokerRaftAdapter、Membership/Cluster AppService、PeerAdmission、BrokerCluster 资格检查逐入口映射到保留/禁用/替代，禁止只搜索清零就宣称等价。
- 拒绝路径：Follower 发起变更、未授权调用、旧观察授权/背景、成员操作中断、未知结果、并发路由调整、失去多数派均不能绕过授权；故障报告不等于 Raft 投票或旧权限隔离证明。
- 自动恢复：三 voter 一节点崩溃、Leader 崩溃、对称及非对称网络分区、旧节点恢复、接管中换届/重启、迟到报告、重复 Begin、维护中故障；恢复用户流量的同时核对真实 voter 未被自动删除。
- 消息与时延：覆盖确认前后故障、同标识重试、重复通知和离线补投/同步；分别采集用户恢复时间与操作收尾时间，未测前不承诺 RTO。
- 路由行为：用户路由、重试边界、Lease prepare/install、epoch 交接、旧节点恢复、排空与清理按原契约验证。
- 真实拓扑：Leader 切换、learner 追平、显式增删、快照安装、网络分区与恢复；不把 test-compile 或管理查询替代真实场景。
- 依赖边界：官方来源/版本确认、实际单实现和无 overlay 检查。
- 最终按 readiness、聚焦、完整 clean/full 和独立 quality/Review 收口；准确命令和文件范围在实施计划批准时固定，本文不创建竞争 active 任务。

### 备选方案

| 方案 | 优点 | 代价与决定 |
|---|---|---|
| 保留最小私有补丁 | 当前行为不变、短期变更最少 | 仍承担内部生命周期维护和制品成本；作为当前提交前收口路径，不等于长期最优 |
| 官方包 + 重新设计资格证明 | 可能保留自动接管并减少私有能力 | 推荐研究方向，不是已证明可行方案；观察授权、成员维护与未知结果恢复都需新证明 |
| 更换共识组件/大幅重构控制面 | 可重新选择能力边界 | 改动与验证面大，目前没有必须采用的证据，不优先 |
| 官方包 + 关闭自动摘流 | 减少相关调用 | 不满足用户必需能力，已否决，不作为迁移基线 |
| 官方包 + 存储/快照观察扩展 | 理论上可重建配置历史 | 新增日志截断/压缩/快照竞态等基础设施，尚无等价证明，不选作简单替代 |

### 当前收口与后续实施的分界

当前 Bolt 计划仍按现有实现验收，需恢复并验收私有 JRaft、补 Broker/E2E/HA 和 full/独立 Accept。本文不能消除当前依赖缺失或授权跳过验证。用户自行提交当前变更后，再评审和推进本方案；不自动开启新 active 计划。

### 开放问题与批准条件

- 自动接管不能关闭；具体故障假设、维护窗口可用性边界与 RTO 测量验收标准仍需确定。
- 若观察者资格不再绑定真实 voter，如何证明阈值、撤权、背景隔离和误判安全？该候选策略尚未批准。
- 官方版本选择及其准确契约尚待核实；不预先填写版本或假定兼容。
- 哪些管理操作保留在线、哪些必须外部运维，需要逐项确定。
- 未完成维护操作如何跨重启保持安全、确认未知结果并恢复自动接管，需固定持久化/审计职责和具体状态转换；不能以无限期等待人工替代自动恢复目标。

上述开放项影响功能，必须在设计定稿和独立 Review 前解决；本草案不是可直接实施的计划。

### 草案独立审阅

Reviewer `ad812e4285644eabe` 认为原提案可作为延后讨论的 DRAFT 保存，未发现必须当时修改的安全或阶段边界问题；不是实施批准或当前验收 Accept。该审阅不覆盖下述用户确认的方向修订。

#### 历史研究提出的待解决问题（当前不继续执行）

- 逐入口区分成员恢复、健康报告、Peer 准入、接管决策和 Lease 授权各自需要的证明；不能直接用业务角色替代真实 voter，或删除旧检查后宣称等价。
- 核查候选官方版本的准确契约，证明多数派支持、并发成员变更、Leader 切换、超时未知和重启恢复如何闭环；尚不声称官方公开接口足够。
- 固定成员维护与自动接管的互斥及恢复规则，避免未完成维护永久阻断故障恢复；具体持久化状态、操作期限和闸门仍待设计。
- 测量故障发现、旧权限隔离、新授权安装、Gateway 路由重建与用户恢复各阶段耗时，分别验证崩溃和网络分区；不能用配置超时推算 RTO 保证。
- 将持久化确认、同标识重试幂等、推送失败后补投或同步映射到所属模块及端到端测试；不因 Broker 治理设计自行改变 Message 数据所有权。
- 正文方向已同步；下一轮须补齐具体协议与接口核查，并据此细化迁移及验证策略，再进行独立设计 Review；此前不创建新 active 计划，不切换依赖、不修改生产行为。

#### 本轮研究证据与限制

- 已执行 ./scripts/verify.sh readiness：通过，保留 PATH 中 world-writable 目录警告。
- 已静态阅读上述生产入口及自动故障流程，确认 reconcileFailures 不调用成员删除，确认私有读取不仅被 Leader 使用。未运行行为测试，源码行号随混合工作区变化，不视为冻结实现验收。
- 官方项目说明列出选举、learner、成员管理和线性一致读能力：[SOFAJRaft 官方仓库](https://github.com/sofastack/sofa-jraft)。本轮网络检索只获得概览及混杂的非官方结果，未作为固定版本 API 等价性证据；固定提交接口限制仍以原核查记录为边界。
- 后续静态核查已延伸到 BrokerFailureObservationService、RaftBrokerClusterStateMachine.grantLease、RoutingLeaseGrantService、BrokerCluster.checkLeaseGrant、advanceEpoch、LocalRoutingLeaseService 和 BrokerRoutingProperties，补充候选 observer 协议、反例及默认隔离耗时分析；未运行这些路径的行为测试。
- 未执行协议等价证明、性能测试或独立设计 Review；当前没有可调用的独立 Reviewer 工具，本轮不记录虚构 Approved/Accept。
- 本轮又检查 RouteBrokerExecutionInterceptor、GatewayDispatchAdapter、GatewayClient、GatewayFrameWriteParams、FrameWriteHandler、GatewayBrokerAuthorizer、FrameDeliveryDeduplicator、FrameProcessHandler、PrivateMessageAppService 的私聊发送片段，以及 BrokerMembershipScheduler/resume。已知存在自动维护恢复，但其未知结果收敛仍依赖私有读取；Gateway 写帧未发现 epoch/业务绝对期限字段。未审计完整下游网络执行链、群聊及所有操作，不把静态核查当已复现缺陷。
- 追加读取 ImMessageService 的重复检测、ConnectionRegistry 写帧/关闭实现、ConnectionCloseHandler 和 RaftRuntime.getOptions：确认重复请求在所查服务路径抛 RepeatException，非 EventLoop 写帧等待处没有显式期限，ReadOnlyOption 未在应用中显式设置。实际依赖默认模式、客户端重复错误处理和 Netty 全局超时仍未核实。
- 最新追加：本地 1.4.1 sources JAR 默认 ReadOnlySafe 与二进制 javap 一致，SHA 见正文，但尚未绑定固定 commit 或私有构建输入；确认私聊/群聊服务端 token 详情查询入口，未定位实际客户端恢复实现。
- 继续读取同 SHA sources JAR 的 NodeImpl.getQuorum/readLeader/listPeers/listLearners，确认列表为 Leader-only、单 voter ReadIndex 快路径及多 voter 当前 peers 心跳逻辑；不推断 joint 整体安全缺陷，不将这些局部方法组合成已证明的稳定配置读取。
- 本轮进一步检查 NodeImpl.ConfigurationCtx.nextStage、onConfigurationChangeDone 及 unsafeRegisterConfChange，形成「持久化目标配置驱动维护恢复」待验证候选。未把 busy/同目标 OK 推导为完整稳定性证明，未运行原型或变更生产接口。
- 新增核查 NodeImpl.becomeLeader/ConfigurationCtx.flush/reset、Configuration.equals、changePeers/resetLearners 及 Node.resetPeers 文档；确认内部换届恢复依据，明确禁止 resetPeers 作为自动恢复捷径，并保留旧维护意图迟到覆盖新目标的关键反例。
- 任期保护核查：所查 Task 支持 expectedTerm，普通配置变更接口不携带对应参数；当前应用 submit 也未使用 expectedTerm。明确业务 barrier 不能自动保护后续配置调用，现有私有读取也不是原子任期 CAS；未据此声称已复现现有实现缺陷。
- 最新复核当前聚合已有跨 Leader 操作登记互斥，Scheduler 仅本机调度互斥；Runtime future 超时不构成执行停止。库内 ConfigurationChangeDone 自带 term 及新旧配置提交机制，与应用过期调用重受理问题分开。JAR 内元数据未提供固定 commit。
- 本轮核对 submitOperation/checkRepeatedIntent、hasPendingOperation、AppService.addLearner 与 Adapter 的检查后调用，构造不依赖换届的同 A 双推进、B 移除后旧 X 再添加时序；仅静态候选，未运行或登记为已复现问题。
- 最新恢复：已找到既有固定归档并实测 SHA 与 provenance 一致，直接核验固定归档 Node.changePeers 签名及 ReadOnlySafe 默认值；源码来源缺口已部分解除。已读取最新原计划，capture 完成但输入审核/verify-lock/消费者仍未通过，不重复构建或擅自安装。
- 当前推进限于设计研究和正文修订，不修改当前计划任务状态，不启动新的实施计划。

### 官方单步角色校验事实修订（2026-10-30）

用户批准按实测纠正验收前提，并继续实施。独立实验与规格复核表明：官方 1.4.1 的 NodeImpl.changePeers 在 confCtx.start 之前验证 ConfigurationEntry；完整跨角色目标的新旧角色并集重叠会被拒绝。NodeImpl.addPeer 保留原 learners 后增加 voter，新配置自身即无效。历史 F1 的 replicator 风险推导不构成这两个公开入口可到达的失效证明，保留历史记录但由本节纠正其适用性。

协议仍要求两个持久化完整目标，先移除旧角色再添加新角色；真实新 voter 必要计票、降级复制、步骤之间换届/超时/重启、Leader 移除、迟到执行与许可排空验收均不减少。本次仅将不可成立的 post-stage 单步反例改为 V2a 的提前拒绝契约，并将顺序覆盖明确放在 V2b 的受控适配器测试。等待独立修订复核；此前实验 Review Revise 不改写为 Accept。

### 公共配置模型的解析边界澄清（2026-10-30）

架构门禁揭示初版实现将 PeerId 放入 model，违反既有公共契约框架隔离规则。按既有所有权约束澄清：公共模型只做不可变、字面去重排序及通用校验；Runtime/core 拥有 JRaft 语义解析、canonical 别名合并及跨角色冲突拒绝，且必须在 Node 启动/变更入口前完成。无效原生配置的拒绝不撤销，只改变拒绝所在边界。此澄清不增加兼容层、不复制 PeerId parser、不放宽架构规则；修改后须独立复核。


### T4 线程与角色事件实现细化（2026-10-30，待独立设计复核）

本节是原批准安全协议的实现候选补充，不改写原批准规则，也不代表新增范围已获代码实施批准。T4 保持唯一 active，局部实现的 BLOCKED 历史保留；T3–T5 仍为不可中途交付的原子批次。独立设计复核完成并获得实施授权之前，只修改本节和计划 T4 的准确范围，不编辑生产或测试代码。

#### 角色事件所有权与延迟窗口

`RaftRuntime.StateAdapter` 拥有原生角色回调；Broker 的 `BrokerRegistrationLifecycle` 只拥有注册表及 Gossip 生命周期，不接管 Raft 角色事件。新增 framework-neutral `RaftRoleListener`，仅接收任期开始和角色停止通知，不引用 Broker、Spring、RPC 或 JRaft 类型。监听器作为必需构造依赖在 `groupService.start()` 之前安装；同步更新现有构造调用方，不保留无调用者的兼容构造器。Broker 配置层先创建本机 permit executor，再经 `BrokerRaftAdapter` 向 runtime 注入监听器，不等待 AppService 创建，不形成循环依赖。

`StateAdapter.onLeaderStart` 先发布新任期及角色 generation，再短回调；`onLeaderStop` 先发布任期 0 及新 generation，再短回调。角色观察使用一个不可变 `(term, generation)` 值的原子引用，避免两次独立原子写入形成混合快照；generation 每次 start/stop 都递增，包括同任期 transfer 超时后重新 start。监听器仅短锁记账及非阻塞唤醒，不能调用 Node、发布会同步执行应用 continuation 的完成通知，或等待 worker、Future、ReadIndex、RPC。

已核查本机官方 1.4.1 source JAR 的 `NodeImpl.stepDown`：调用者持有 Node writeLock，先投递 `LEADER_STOP`，随后更新 follower soft state；`FSMCallerImpl` 经 `taskQueue.publishEvent` 入队，消费后才调用状态机 `onLeaderStop`。该通知存在延迟，不能声称 Node 失任瞬间即撤销 Java 调用能力。事件送达前已经准入或暂停的任务仍计入在途能力；它们没有精确排空证明时，持久化许可阻止后续目标。JRaft 自身角色拒绝不自动等于许可排空。

Broker executor 在同一短 mutex 内关闭当前 generation 的新预留并将所有旧 OPEN 槽转为 CLOSING，包括只预留、已排队和已进入 native 的槽。未进入者撤销未来调用能力；已进入者保持 in-flight，直到同步入口退出及原生完成均可证明。DRAINED 事实、预留记录和未知记录永不删除。重新当选只允许新 generation 的新预留，不能重新打开旧槽；进程关闭才永久关闭 executor。原生入口前在 worker 上检查当前角色及预留 generation；该检查不是与 Node 状态切换原子的任期 CAS，检查后暂停仍按已准入能力处理。

#### 监听异常的永久 fail-closed 边界（首轮 Review Revise 修订）

本补充首轮独立设计 Review 为 Revise：角色监听异常后的 fail-closed 通路未定义。该结论作为历史保留，本次仅修订候选，等待原 Reviewer 窄复核，不将其改记 Accept。`FSMCallerImpl.doLeaderStart/doLeaderStop` 直接调用业务回调，不能依赖外层记录异常后自动重试。

本轮收口 Review 对既有锁外通知／有界收尾契约的修正：成员发送结果须在 mutex 内固定本轮 completion 与结果后，完全离开全部外层锁再 complete；同步 continuation 可访问 Node，不能在锁内通知。已准入成员流程的待执行 continuation 队列独立上限为4096，不与永久槽相加拒绝本轮最后合法预留后的收尾；新执行准入、永久预算、generation、worker、fault/close 安全契约不变。两项确定性回归修复前失败、实现后通过；独立复审及完整验收由当前计划记录，不追改首轮Block。

角色监听的正常实现先在 executor 的同一短 mutex 内完整完成不可逆记账，再在锁外尝试 pump 唤醒；短记账段不做 IO、Node 调用、Future.complete、用户回调、日志格式化或调度。调度的 `RejectedExecutionException`（含 shutdown 竞争）由唤醒方法内部处理：保留 pending／全部槽事实，清除未成功的入队标志，不向角色监听抛出，不回滚记账。shutdown 已永久关闭准入时不再唤醒，仍保留账本。

为避免监听失败后 runtime 与 executor 各自恢复，`RaftRoleListener` 同时提供框架无关的终身 fault latch：一个初始化时分配的、只允许 healthy→faulted 的原子状态，由 runtime 事件包装器与 executor 的预留／native 准入共同读取。它不是可执行的第二个异常回调。该 latch 随必需监听依赖在 Node 启动前安装；无 listener 专用的空实现。runtime 在调用 listener 时捕获 `RuntimeException`，先设置该 latch 并保存首个异常引用，再发布 term=0 的新 generation，最后返回 FSM；executor 自身监听实现也在抛出 RuntimeException 前设置同一 latch。这样即使全槽遍历只完成一部分，所有尚未修改的槽也因同一个永久 gate 失去新准入能力；已进入者仍记为在途，不能伪造排空。

fault 设置只做原子状态／引用写入，不获取 Node 锁、不调用 listener 的补救回调、不完成 Future、不等待 worker。所有 reserve、queued→entered、角色 start 处理和 pump 的 native 阶段在原短锁内检查该 gate；fault 后 start 只能更新观察信息，不得重新开放准入。runtime 的受限确认入口与其他读取证明出口也拒绝签发新证明。已开始的 native 不取消；其迟到 callback 仍只记录事实。账本、DRAINED 与 pending 全部保留，不由故障处理删槽、释放容量或重执行；永久 fault 的进程不承诺继续投递，未收到响应的确认调用方只获得超时／UNKNOWN，不从 fault callback 强行完成请求。

通过 runtime／executor 的只读 fault 状态及首个 RuntimeException 原因暴露故障，供既有诊断／调用结果读取；不能只 log 后继续准入，不能自动清 fault，进程重启也不复活旧许可。此异常边界仅覆盖可恢复执行环境中的 RuntimeException；不捕获 Throwable 并宣称能可靠处理 OutOfMemoryError、StackOverflowError、VM 崩溃等 JVM 致命错误。若进程无法继续执行原子 gate 写入，维持原失联／未知许可永久 blocked 的协议边界，不承诺内存级故障恢复。

#### 受限排空确认入口的精确非阻塞检查

已核查官方 1.4.1 `Node` 公共 API 存在 `boolean isLeader(boolean blocking)`；`NodeImpl.isLeader(false)` 仅比较 `volatile State state` 与 `STATE_LEADER`，不获取 Node readLock；无参 `isLeader()` 则等同 `isLeader(true)` 并获取 readLock。来源为本机 `/tmp/jraft-artifact-m2/com/alipay/sofa/jraft-core/1.4.1/jraft-core-1.4.1-sources.jar` 的 `Node.java` 与 `core/NodeImpl.java`，是静态 API 证据，不是当前私有依赖的官方运行验收。

共享 `confirmLeadership` 已被 lease、learner 等消费者使用；其现有角色检查和返回契约保持不变，不把下述 DRAINED 专属论证推广给这些消费者。本次选择新增 runtime 公共受限方法 `confirmMembershipPermitDrain(byte[] context)`，仅由 Broker adapter 同名入口调用，AppService 只有 typed drained 确认分阶段路径调用该入口；不替换共享方法，不迁移 lease／learner 调用方。方法产生的技术 ReadIndex 事实仅供该完整许可确认 CAS 使用，禁止用来授权 native 配置、lease 或 learner 业务推进。方法名表达用途，不让 plugin 引用 Broker 领域类型。共享角色事件 fault gate 只增加失败关闭，不放宽共享方法成功条件。

`confirmMembershipPermitDrain` 发起阶段仍在唯一 worker：捕获正任期角色快照 `requested=(term,generation)`，检查未发生状态机错误及 `node.isLeader(false)`，再检查角色快照仍等于 requested，之后调用 `node.readIndex`。ReadIndex callback 不调用阻塞版 `isLeader()`，而是依次读取角色快照 before、检查原生 status OK／未发生状态机错误／`node.isLeader(false)`、读取角色快照 after；只有 before 与 after 均精确等于 requested 才记录该任期 ReadIndex 事实，否则记录失败。角色快照对象不可复用，generation 防止已送达的 stop/start（含同任期重新 start）产生 ABA。callback 不发起第二次 ReadIndex 或提交。

安全依据分为三层，不能混为原子角色快照：ReadOnlySafe 的原生成功返回证明该请求的 quorum read；相同任期及 generation 的双采样拒绝已观察到的换届／角色中断；worker 消费事实时再次核对相同快照及当前角色，并在应用校验后提交完整身份的确认 CAS。FSM 事件尚未送达的窗口不能由 generation 消除，非阻塞角色检查也不承诺返回后 Leader 不变。窗口内可能提交的仅是不可逆 DRAINED 的既有完整事实，FSM CAS 再核对身份、摘要和结果；它不创建 native 执行能力，不推进未满足条件的目标。换届后的提交失败或未知保持原事实待重投，不能以该观察作为新 native 调用或业务成功授权。原批准的当前 Leader 同任期一致读验证保留，不删除验证、不称其为原子任期 fence。

#### 唯一 worker、分阶段 pump 与满队列

固定现有一 worker、一等待槽、AbortPolicy；不新增池、common pool、补线程、caller-runs 或阻塞入队。native callback、FSM Register continuation、ReadIndex callback 和提交完成 callback 只向永久槽写入事实／下一阶段 pending，再非阻塞请求 pump。所有可能进入 Node 的确认阶段，包括角色读取、Leader endpoint 查询、本机一致读发起、第二次 applied ReadIndex 发起和共识 submit，都由唯一 worker 执行；不在 worker 上 join/get 等待异步完成。接收远程 typed 确认也进入同一 worker 的分阶段入口，handler 只在 RPC 线程等待应用结果，不把 RPC caller 当作替代 Node worker。

阶段至少区分：预留／Register 结果、唯一 native 准入、同步退出及 native 结果、本机 DRAINED、确认读取、确认 CAS、确认响应。确认请求使用既有持久化许可的有界槽，纯事实投递不能创建 native 执行权；远程入口占用有界等待预算，预算满返回 UNKNOWN，不创建无界接收队列。4096 永久槽上限继续包含失败与未知预留，不复用、不驱逐、不以确认重投绕过容量限制。若无法为远程确认取得本机有界事实槽则拒绝本次调度，发送方仍保留自己的 DRAINED 事实。

至多一个 pump 正在等待队列，使用短 mutex 保护 pending 与唤醒状态；worker 每次退出前重查 pending，已接受的 native/pump 任务负责再次扫描。入队拒绝不能标记唤醒成功，也不能清除 pending；必须以 latch 覆盖拒绝与 worker 退出之间的 lost-wakeup 交错。待处理项存放于受上限约束的槽而不是另一条 Runnable 队列。完成应用 Future 也在 worker 边界发布，callback/FSM 不直接执行应用 thenCompose。旧 callback 永远不能创建新的 native 阶段。

队列满仅拒绝本次调度，不能删除 DRAINED、清空事实、重新打开槽或重复 native。显式重投严格复用 permitId、operationId、stepId、executorBrokerId、incarnation、targetDigest 和结果；接收方与 Leader 路由变化只改变投递 envelope，不改变该事实。UNKNOWN／请求超时保留原事实，本节不新增自动 timer、重试周期或无人触发也最终成功的承诺。worker 被未知原生工作永久占用时保持 blocked，不允许绕过。

#### 文件范围与验证落点

新增生产文件只有 `im-plugin/im-raft/src/main/java/com/co/kc/imchat/plugin/raft/spi/RaftRoleListener.java`。修改生产文件：`im-plugin/im-raft/src/main/java/com/co/kc/imchat/plugin/raft/core/RaftRuntime.java`；原 T4 的 Broker `application/cluster/BrokerMembershipPermitExecutor.java`、`BrokerMembershipAppService.java`、`adapter/cluster/BrokerRaftAdapter.java`、`BrokerPeerAdapter.java`、`infrastructure/config/BrokerClusterConfiguration.java`、`infrastructure/chain/interceptor/BrokerPeerAdmissionInterceptor.java`、`interfaces/handler/broker/MembershipPermitDrainedHandler.java` 及 SDK operation/params/response 原范围不减少。

必需构造调用方修改限定为现有 `im-plugin/im-raft/src/test/java/com/co/kc/imchat/plugin/raft/core/RaftRuntimeTest.java`、`RaftConfigurationIntegrationTest.java`，以及 Broker `adapter/cluster/BrokerRaftAdapter.java` 的直接 runtime 构造；`infrastructure/config/BrokerClusterConfiguration.java` 同步调整 Adapter／Executor 的启动前接线。已对 im-plugin/im-raft 与 im-broker 的 Java 源码检索 `new RaftRuntime`，当前直接构造仅这三个文件。若代码阶段搜索发现其他实际构造调用方，先报告精确路径，不以兼容构造器掩盖遗漏。测试继续修改原 T4 的 `BrokerMembershipPermitExecutorTest`、`BrokerMembershipPermitAdmissionTest`、`BrokerClusterConfigurationTest` 及 SDK admission 合同测试；不新增反向模块依赖。说明更新在 `im-plugin/im-raft/README.md`、Broker server/SDK 两份 README；不把本节写成已实现事实。

先用 `RaftRuntimeTest` 确定性验证监听器启动前安装、任期先发布、受限入口非阻塞角色 API、双采样拒绝跨 generation、共享 confirmLeadership 的 lease／learner 契约不变；用 permit 两个测试类覆盖失任封闭预留／排队／在途、重新当选不复活、callback/FSM 不进 Node、队列满不丢事实、lost wakeup、同事实显式重投及 native 调用最多一次。另以 listener 在全槽记账前／中途抛 RuntimeException 的 fixture 验证同一 fault latch 立即永久关闭 runtime／executor，新 start、reserve 和排队旧 native 均不恢复，已有在途／DRAINED／pending 不丢失且无 Future continuation 在 FSM 执行；以 shutdown 与 pump enqueue 拒绝竞争的 fixture 验证异常内部处理、无虚假唤醒成功、无账本回滚。上述测试分别放现有 RaftRuntimeTest 与 permit 两类，不新增生产故障注入接口。命令分别为：

```bash
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-raft -am -Dtest=RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipPermitExecutorTest,BrokerMembershipPermitAdmissionTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerBoltAccessContractTest,BrokerAdmissionContractValidatorTest,BrokerClusterConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

真实拓扑不回退证明放在 Broker server 的 `BrokerMembershipPermitExecutorTest`，使用真实 RaftRuntime、持久化状态机和许可执行器，在测试 supplier 边界暂停已准入旧线程，再真实换届；旧许可未确认时拒绝下一目标，精确排空 CAS 后执行下一真实配置，重放旧调度／callback 后核对当前 Leader 配置及后续业务日志均不回退。不得用手工追加 target history 或 mock Leader 替代此证明。原 im-raft `RaftConfigurationIntegrationTest` 的真实夹具仅作参考及构造迁移，不引入对 Broker 的反向依赖。当前私有坐标下的真实拓扑结果必须标注依赖事实，不冒称官方坐标验收；依赖切换仍不属于 T4。

上述协议边界与真实不回退反例属于 T4；首次／resume／Scheduler／continuation 全入口迁移、多步推进、最终业务 Commit 和原子批次全链验收属于 T5。完整原 RED、启动、行为、E2E 与最终 full 要求均保留，不因补充窄命令而减少。本节尚未独立设计复核，不记录 Accept，不解除 T4 原 BLOCKED。


#### T4 实现细化独立设计复核结果（2026-10-30）

controller 转达原独立设计 Reviewer 对上述修订补充的结论为 **Accept，findings none，仅设计**。首轮 Revise 与修订原因保留；本次不代表实现 Review、运行验收或 T4 完成，T4 原 BLOCKED 与原子批次不可交付边界不变。controller 已按用户既有授权允许按小批次继续代码实施；计划与 PROGRESS 由 controller 管理。

### T4 远程确认等待预算与共享等待桥补充（2026-10-31，已批准）

本节修复已批准 T4「远程入口占用有界等待预算」的实现缺口，不新增 native 权限或自动恢复机制。当前独立 QUALITY 仍为 **Block**，原件 `.harness/t4-reaccept/quality-review.json` 保留；下述配置和等待视图是 controller 的候选决策，不是用户新批准或既有运行事实。独立设计复核及准确计划落地前不修改生产、测试或 README。T4 保持唯一 active，T1–T3 passing、T5–T7 blocked。

#### 问题、目标与非目标

现有 Handler 对应用返回的未完成 stage 执行无限 `join()`。4096 永久事实槽只限制许可身份数量，不能限制同一身份占用多少 RPC 线程；发送方超时也不会释放接收端线程。即使增加 Semaphore 和有限 `get`，每次将同一未完成 MinimalStage 转为 CompletableFuture 仍会新增源上的 relay；请求超时不保证 relay 解绑，串行重投也可能累积历史对象。必须同时约束实际请求等待、同轮持久依赖和永久事实槽，不能用其中一个预算代替另外两个。

本补充只处理受限确认入口及其现有应用／Executor 返回边界，不修改 SDK 载荷、认证链、Node 阶段、worker 数量、4096 槽、不回收原则或 D16。UNKNOWN 不表示未提交；超时不取消、完成、删除共享事实，不重执行 native，不新增 retry、timer、线程池或通用等待框架。

#### 配置所有权候选

在现有 typed `BrokerProperties` 中增加固定嵌套 `MembershipConfirmation`，由 `im.broker.membership-confirmation.max-concurrent-waiters` 和 `im.broker.membership-confirmation.timeout-ms` 分别控制本 Handler 的同时等待数和单请求本地期限。候选本地默认分别为 **8** 和 **3000 ms**：这是本次待复核的保守配置选择，不是已测容量或 SLA，也不声称原设计已指定这些数字。Handler 构造时要求两值为正并快照，Nacos 可覆盖启动输入，不承诺在线 Semaphore 热缩容。

不复用 `peerCall.maxConcurrentRequests`：它拥有 outbound 控制面 RPC executor，接收端等待预算应独立调节。独立 timeout 同样避免 outbound 调用配置变化意外改变接收方等待契约；二者期限相同不保证网络端到端按时响应。替代方案是让 peerCall 同时控制两个独立资源，文件较少但混淆所有权，故不推荐。仍使用既有配置扫描，不新增 Bean、YAML 重复默认或兼容构造器。

#### 等待桥与生命周期候选

现有 Executor 的每个确认轮拥有一个私有等待桥；共享确认完成结果只接入一次，重复接收请求复用同轮桥，不在每次请求上执行 `MinimalStage.toCompletableFuture()` 或追加永久 continuation。对外只提供有限等待所需的只读能力，不暴露 cancel、complete、obtrude 或可下转型修改源的共享 CompletableFuture。准确候选为 Executor 内包可见具体 `ConfirmationRound`，持有私有原始 completion、固定 `CompletionStage<MembershipPermitDrainedResult>` 和固定 `Future<MembershipPermitDrainedResult>` 等待视图。现有 `confirm` 返回该轮对象；AppService 现有 `confirmMembershipPermitDrained(command)` 保留 CompletionStage 签名，新增有 Handler 实际调用方的 `requestMembershipPermitDrainConfirmation(command)` 返回 Future；二者委托同一个私有轮次入口，复用原全部校验及拒绝逻辑。立即拒绝通过已完成轮对象返回，不进入永久槽。私有具体 WaitingView 只实现 JDK Future，get 直接委托原始 completion、cancel 恒 false，不是可下转型为 CompletableFuture 的对象；不得增加完整 CompletableFuture 子类框架。Future.get 的临时等待节点由 JDK 超时／中断清理，不安装每请求 relay；需由测试补充固定依赖数量证据，不能只凭相同包装器身份认定有界。

桥与现有有界槽／当前确认轮共同生存；完成后的显式新轮使用新桥，旧轮结果不能串入新轮，不建按请求或历史轮次保留的表。原 CompletionStage 调用方仍保持 F1 已接受的同轮固定只读 view、派生取消隔离和显式新轮语义。立即拒绝／认证失败等无在途轮路径也必须闭合，不为拒绝创建永久槽或全局缓存。

Handler 先执行原 principal 校验，再非阻塞取得私有请求预算；满额立即返回 typed UNKNOWN，不调用应用、不建立接收队列。已准入请求只等待同轮桥，使用 `System.nanoTime()` 的单一 deadline；超时返回 UNKNOWN。保持旧 join 的不取消语义：捕获中断后在同一剩余期限内继续等待，finally 恢复 interrupt bit；不因每次中断重置期限。所有正常、同步异常、异步异常、取消和超时出口均 finally 释放实际请求预算；不把认证或执行异常统一吞成 UNKNOWN。异步异常保留 join 的 CompletionException／CancellationException 形状。

该预算只约束本 Handler 已准入的实际等待，不宣称隔离所有 Bolt 流量或消除上游 Processor 的既有队列。没有新 worker，也不把 RPC caller 当 Node worker；确认阶段仍只在原唯一 worker 执行。

#### 验证与交付边界

公共 admission／handleRequest 测试须证明：首请求确已进入应用且未完成时，超额请求立即 UNKNOWN 且不调用应用；准入请求期限退出后预算可再次使用；大量串行超时重投仍只保留同轮固定等待桥；共享事实与 native 均不取消／重复；解除 worker 屏障后原确认完成，显式新轮与旧轮结果隔离。补齐同步异常、异步 cause、取消和中断后的预算释放；配置默认、覆盖、非法值由绑定及构造测试验证。测试用受控等待结果与 latch，不用 sleep、进程挂死或墙钟耗时阈值充当 RED。

Q2 只修正现有 `queuedCommittedPermitCannotStartNativeAfterLeadershipLoss` fixture：必须先让真实公开 workflow 完成 Begin／Register，在精确持久化许可及 queued native 屏障成立后失权，再证明零 native；增加不失权时精确一次 native 的正向对照，排除入口拒绝或测试屏障吞任务。此修正不追认旧测试为历史 RED，也不为制造 RED 弱化生产 gate。

实施前须固定准确 API、完整文件范围及具名 RED／GREEN 命令并独立复核；之后按窄层、模块、architecture、冻结 clean／full 顺序重新取证。原模块与 architecture 通过仍只属于改前输入，原 full 不升级为 fresh。README 仅在实现后写当前事实。沿用已有有界资源与确定性测试规范，本补充尚未确立需新增 Harness 规则的通用惯例。

| ID | 来源／问题 | 候选决策 | 理由与影响 |
|---|---|---|---|
| T4-W1 | outbound 配置不拥有 inbound 等待 | 独立 typed MembershipConfirmation，候选默认 8／3000 ms | 配置可独立调节；默认不是容量承诺，待复核 |
| T4-W2 | timeout 不会清除历史 relay | 现有槽内每轮一次私有只读等待桥 | 同轮持久依赖不随历史重投次数增长，不建第二张历史表 |
| T4-W3 | 有限等待不能修改协议事实 | 非阻塞准入、单一 deadline、恢复中断标志、finally 释放请求预算 | 仅满额／期限返回 UNKNOWN，不取消共享工作或重置期限 |

2026-10-31 独立窄设计复核为 **Accept，仅设计，findings none**，原件 `.harness/t4-reaccept/quality-fix/design-review.json`。该结论不是用户新增批准，也不解除原 QUALITY Block 或授权实施。实施时 deadline 从取得请求预算起计算，应用入口返回后仅使用剩余期限；所有新旧轮 callback 捕获本轮，不重新读取 Slot 当前轮。期限不保证任意同步 Java 代码可被抢占或网络端到端 SLA。固定等待视图身份之外，必须补充未完成源依赖／安装次数的稳定性证据。配置默认、共享桥与 Q2 尚无实现或新运行证据；用户随后明确回复「可以」，确认上述新增独立配置、8／3000 ms 本地默认、共享等待桥及中断策略，并允许继续实施；本节据此转为已批准，进入既有活跃计划的 T4 修复批次。前文候选／待审文字保留为决策历史，不覆盖本条批准状态；实现、测试及原 QUALITY Block 尚未闭合。

### T5 learner 确认的有界 worker 入口补充（2026-10-30，pending 独立 Review）

本节仅为待审设计，不是实现事实。controller 已允许将 `BrokerMembershipPermitExecutor.java` 纳入 T5，并授权本次同步计划的准确范围；尚未授权按本节修改生产代码。PROGRESS 仍由 controller 管理。保留 T3–T5 原子批次、T4 已批准线程协议及 D10/D16，不增加 native 自动重试或可用性承诺。

#### 已核实的缺口与最小扩展

现有 executor 只有依赖 `BrokerMembershipPermit` 的 `confirmationExecutor`／`confirm` 阶段入口；`reserve` 创建 native 预留。`confirmLearner` 当前通过 `confirmLeadership`、对端 `fetchQuorumReadIndex`、Begin、Commit 和终态提交组成链，不能为获得线程入口伪造 permit，也不能让异步 callback 的内联 `thenCompose` 发起下一次 Node 调用。executor 继续拥有唯一 worker；不增加线程池、通用任务平台或新的生产文件。

候选精确 API 如下，均为 `application.cluster` 包可见，不向接口层暴露 `Executor`、`Runnable` 或可注入 action：

```java
// BrokerMembershipPermitExecutor：唯一生产调用方是 AppService.confirmLearner。
CompletionStage<CompleteLearnerSyncResult> confirmLearner(
        ConfirmLearnerCommand command, BrokerMembershipAppService service);

// BrokerMembershipAppService：仅由上述 typed 入口在 worker 上调用。
CompletionStage<CompleteLearnerSyncResult> confirmLearnerOnWorker(
        ConfirmLearnerCommand command, Executor stages);
```

AppService 的公开 `confirmLearner(command)` 只委托 typed 入口，不在调用线程读 Node。executor 内部将 action 固定为 `service.confirmLearnerOnWorker(command, stages)`，不接收外部 Supplier／Function；`stages` 只在这个内部链中使用。现有 native permit 入口不改为通用 workflow 入口。`resume` 对 COMPLETE_LEARNER_SYNC 的 PENDING 与 RAFT_COMMITTED 均须先分流到公开 `confirmLearner`，再处理其余类型既有的通用 phase 分支；不能保留位于 type switch 前的 RAFT_COMMITTED early return 作为该类型的终态提交旁路。Scheduler 仍经 `resume`，不新增绕行分支。配置层继续注入同一个 executor，不增加生产构造器或测试专用实现。

#### 身份、永久预算与重复请求

workflow 是独立的非 native 槽，不构造 `BrokerMembershipPermit`，不调用 `reserve`／Register，也不进入 native 槽映射。身份冻结为本进程 incarnation、operationId、完整 commandDigest、完整 `ConfirmLearnerCommand`、接纳时 role generation 和本机单调 attempt 序号。commandDigest 必须包含原有 brokerId、expectedEpoch、membershipEndpoint；不以摘要替代完整载荷比较。

在现有同一 mutex 下检查并分配；native／远程确认永久槽与 workflow 永久槽的**总数**至多 4096，不是各自 4096。一个 workflow 从接纳、排队、异步在途、pending、失败到终态始终计一槽；所有失败、未知和已完成 attempt 永不删除或驱逐。operationId 到最新 attempt 的索引只引用这些槽，键数量不超过槽总数；不建立无界历史、等待者列表或第二条 Runnable 队列。满额拒绝本次新 attempt，不影响已有事实或 native 权限。

同一 operationId 的完整同事实在途请求共享只读派生结果，不增加槽或重复阶段；派生取消不能取消账本链。相同 operationId 但不同摘要或不同载荷立即返回冲突，不覆盖原 attempt。成功／业务确定拒绝的终态可复用原结果；UNKNOWN／NOT_LEADER attempt 的显式重投可在旧 attempt 已发布终态且没有 pending 或在途阶段后分配新 attempt，仍计永久预算。新 attempt 必须重新进行既有领域幂等／恢复判断，不能把旧 ReadIndex 当新授权；不通过 timer、Scheduler 内部循环或 callback 自动重开 attempt。旧 attempt 不因重新当选或显式重投恢复执行权。

#### 单 worker 阶段、异常和迟到结果

沿用一 worker、一等待槽、AbortPolicy 及原分阶段 pump。每个 workflow 槽最多一个 next-stage pending 和一个未消费的异步完成事实；当前阶段只允许一个受跟踪的异步等待。重复投递不能覆盖已登记 pending，矛盾事实拒绝并保留原事实。callback 只记录完成值／异常并非阻塞请求 pump；事实存于对应永久槽，不能借回调创建其他 operation 或 native 调用。拒绝入队保留 pending、不误记唤醒成功，由既有 worker 退出重查处理 lost wakeup；无 common pool、caller-runs、fallback、阻塞入队、join/get 或额外 Runnable 集合。

实际 Node 的角色读取、Leader hint 查询、native 配置诊断、ReadIndex 发起、Begin、Commit 及终态 submit 全部在 worker 阶段执行。唯一窄例外由[可信提示设计附录 D／H7／H8](2026-11-02-ops-trusted-leader-hint-mapping-design.md)拥有：caller 可在既有 executor mutex 内原子无锁读取无 Node 的 following 事件值，仅用于纯拒绝。摘要冲突、在途共享、closed／roleFault／满额硬拒绝优先，随后仅 `roleStopped` 且观察有效、generation 一致时返回 `NOT_LEADER`；零新增 workflow、永久预算、队列、pump、许可或 native。缺观察及独立 FSM failure 不提供提示。锁序仍为 `rolePublication → listener → executor mutex`，getter 不反锁 publication；事件延迟不证明实时健康或 quorum。该例外不授权任何新执行，不改变 worker 首检及后续 native 门禁。使用受槽约束的 `thenComposeAsync`／`thenApplyAsync`，不能重新引入 callback 上的内联 Node 调用。最终应用结果也在 worker 阶段发布；新接入或已注册的调用链不得在 ReadIndex／FSM callback 完成结果。对于已完成结果后才附着的外部 continuation，CompletionStage 本身不保证线程：AppService 内后续 Node 阶段仍必须显式通过对应有界 worker 入口，不能依赖完成线程推导安全性；接口层只转换／返回结果，不取得 Node 能力。

请求方超时／取消只影响派生等待，不清除槽、pending 或在途事实，不重复 native 或自动重新执行 workflow。异步失败由 worker 转换为现有 `CompleteLearnerSyncResult` 状态；Begin／Commit 结果未知时保留领域 operation 的实际状态，显式恢复读取已提交事实。正常角色 stop 在短 mutex 中不可逆封闭旧 generation 的新业务阶段，但仍允许旧槽的结果消费与失败发布；晚到事实不得触发旧 generation 的 Begin／Commit。角色 start 只开放新 attempt，绝不重开旧槽。每个有副作用的阶段在 worker 上重验 role generation、当前 Leader 任期及相关 revision，不宣称与 Node 换届原子 CAS。

close 永久关闭新 workflow 与新 Node 阶段，不 interrupt 已进入工作，不等待回调；已经提交的事实不得擦除。shutdown 后不能调度的迟到 pending 仍保留，派生结果允许保持未完成，不用调用线程补发完成。fault latch 一旦触发同样永久拒绝新 attempt／Node 阶段，保留全部事实，不新建线程或通过新 generation 恢复；不能为了给出终态而在 fault callback 上运行 continuation。未知工作永久占用时允许 blocked，与原安全优先边界一致。

#### 不授予 native 权限与 learner 证据保真

typed workflow 不接收目标配置或 native action；槽类型不满足 `execute` 的精确本机 native 预留／已提交 permit／incarnation 校验，不能转换成 native 槽。`confirmLearnerOnWorker` 的固定链只允许 learner 身份／已提交端点检查、Leader 配置诊断、同任期 quorum ReadIndex、经既有认证对端取得的应用进度、领域 Begin／Commit／终态提交，不得调用 `changeMembershipConfiguration` 或旧 native delta API。能力边界是受控 Java 调用方与现有 permit 校验，不是恶意同 JVM 代码 sandbox；不会声称 `Executor` 在 Java 类型系统中能阻止任意恶意 Runnable。测试和独立 Review 必须核对 typed 入口的唯一调用方及上述固定链，不能用「所有代码可信」取消原生 permit 检查。

新请求先保留完整身份、端点、epoch、learner 配置及 catch-up 检查；quorum read 的任期和角色 generation 在对端进度返回后、Begin 前由 worker 重验，同时重验取证时的 revision 与端点绑定。证据不足或任期／revision 变化不登记新 Begin；不把较大应用位置单独当作同任期证明。历史 duplicate／PENDING 恢复保留原规则，不重新要求一份取代原 Begin 的证据。COMPLETE_LEARNER_SYNC 不是 native 维护，不生成步骤／permit；Commit 继续使用原 `acceptedRevision` 规则，不误用 native 最终 Commit 的当前 revision。成功仍须 READY 与操作终态均提交才解除 D10；未知或缺少显式确认仍暂停。

#### 文件、fixture 与验证落点

生产增量仅在 T5 原四文件及新增授权的 `application/cluster/BrokerMembershipPermitExecutor.java`；不自动扩大到 runtime、SDK、认证或 transformer。API 与有界槽的回归放 T5 已有新增 `BrokerMembershipWorkflowTest`，使用真实 executor、真实领域／FSM 提交边界及受控 Adapter/Peer 异步事实；不另加生产故障注入接口。既有 `BrokerMembershipSchedulerTest`／`BrokerMembershipChangeTest` 按原范围保留。需要修改共享 fixture 时，实施前另报精确文件及原因，本补充不预授权扩大 fixture 范围。

新增确定性 RED 必须覆盖共享 4096 总预算（native 与 workflow 混合）、在途同事实去重／矛盾事实拒绝／派生取消、满队列 pending 与 lost wakeup、ReadIndex callback 不进 Node、同任期 stop/start generation 失效、晚到结果不提交、显式未知恢复、close／fault 不 fallback，以及不同 revision 下 learner Begin／Commit 仍沿用 acceptedRevision。typed workflow 全程验证零 native permit、零 native 配置调用；首次／resume／Scheduler 原三具名 RED 仍保留，continuation 与完整链在原 T5 继续补齐。

窄验证命令（待设计独立 Accept 并获实施授权后运行）：

```bash
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipWorkflowTest,BrokerMembershipDriftTest,BrokerMembershipSchedulerTest,BrokerMembershipChangeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipPermitExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerClusterConfigurationTest -Dsurefire.failIfNoSpecifiedTests=false test
```

两条窄链通过后才按原 T5 执行 Broker 模块回归和 `affected`，不以本补充替代原验收。本次设计前实际证据为 `/tmp/im-chat-t5-uDxRui/`：readiness exit0；原指定聚焦命令 exit1，fresh XML 共 28 tests／4 failures／1 error／0 skipped。新增 `firstResumeAndSchedulerMustUseTheSamePersistedPermitEntry` 的 FIRST_REQUEST／RESUME／SCHEDULER 三项均为具名 assertion RED（permit 入口预期 1、实际 0）。另有既有 Scheduler 1 failure／1 error：前者为缺少已提交初始端点绑定的旧 fixture 准入断言失败，后者为旧 fixture 传入空 operation 导致 NPE；该归因来自源码检查，尚未通过修订 fixture 复验，不将四个 failure 一概称为新缺陷，也不抹除 error。尚无 GREEN、模块或 affected 证据。


#### T5 补充首轮设计 Review Block 与窄修订（2026-10-30，pending 原 Reviewer 复审）

controller 转达首轮独立设计 Review 为 **Block**：前版只覆盖 `resume` 的 type switch，遗漏其前置 RAFT_COMMITTED early return。源码已核实该 early return 直接调用 `completeMembershipOperation`，再进入 Adapter submit；因此仅修改 switch 不能保证 learner 终态恢复经过唯一 worker。保留本次 Block，不记录 Accept，不修改原 28 tests／4 failures／1 error 或三项具名 RED，也不将以下 fixture 计划写为已验证事实。

准确修订仅涉及原 T5 的 AppService 分流及既定 typed 入口，不增加通用执行接口：`resume` 读到 COMPLETE_LEARNER_SYNC 后，无论 PENDING 或 RAFT_COMMITTED，均先用已提交 operation 与已提交端点构造原完整确认命令，进入公开 `confirmLearner`；该分流必须位于通用 RAFT_COMMITTED early return 之前。调用线程仅读取 committed snapshot／构造命令，不查询 Node 角色、Leader hint 或 submit。其他 operation 类型仍按原 T5 范围处理，本补充不替其新增入口或扩大授权。

typed workflow 在 worker 上重新读取 operation 并核对完整命令身份、摘要、端点及当前阶段。若已为 RAFT_COMMITTED，读取已提交事实后**只推进成功终态**：不重新获取 learner 同步证据，不重新取 ReadIndex，不重新 Begin，不重做业务 Commit，不创建 native permit，也不调用配置变更。该分支不能先走新请求的 learner 资格判定或要求目标仍为 LEARNER_SYNCING；业务 Commit 已使节点 READY，但尚未终态的 operation 仍保留 D10 门禁。终态 submit 前仍需 worker 上核对 attempt 的 generation、当前角色及 fault／close；旧 attempt 不因同任期重新 start 而恢复资格。submit 未知则保留已提交事实和原恢复边界，不把 READY 单独视为维护完成。

在 `BrokerMembershipWorkflowTest` 计划新增以下具名确定性 fixture（设计通过后先 RED，再最小 GREEN），不增加共享 fixture 或生产测试接口：

- `raftCommittedLearnerResumeQueuesTerminalOnlyOnWorker`：通过真实领域／FSM 依次提交 learner 同步 Begin 与业务 Commit，确认 operation 实际为 RAFT_COMMITTED 且节点 READY；用 latch 暂停唯一 worker，分别参数化显式 `resume` 与 Scheduler 调用。记录调用线程，断言两种入口在 worker 释放前零 Node submit。释放 worker 后只提交一次终态，零新 Begin、零 ReadIndex、零业务 Commit、零 native permit／配置调用；终态提交前 D10 仍关闭，只有终态成功后才允许解除。
- `raftCommittedLearnerRecoveryCannotSubmitAfterSameTermRoleRestart`：同样先实际提交至 RAFT_COMMITTED，暂停 worker 并接纳恢复 attempt，然后送达同任期 stop/start（generation 改变）；释放后断言旧 attempt 零终态 submit、零 fallback、零新 Begin／native 调用，D10 保留。显式新 attempt 的后续恢复不混入本条旧 attempt 断言。
- `raftCommittedLearnerRecoveryCannotSubmitAfterFaultOrClose`：分别参数化 fault latch 与 close，在 worker 暂停且恢复已接纳后触发，随后释放 worker；旧 attempt 不 submit、不重新排入替代执行器、不在调用线程或 callback fallback。fault／close 下派生结果允许按既定边界保持未完成，测试不以等待 Future 完成代替安全断言；已提交 RAFT_COMMITTED／READY 事实和 D10 门禁保持不变。

上述 fixture 还需在终态 worker 阶段前设置第二个受控 latch，复验「已接纳／已消费事实但未 submit」窗口中的同任期 stop/start、fault、close，避免仅测第一次排队失效而漏掉迟到 continuation。所有线程断言使用具名 worker／调用线程及有界 latch，不以 sleep 或 mock 调用顺序替代持久化阶段前提。窄命令仍为本节原两条；本轮只修设计与对应计划，不构建、不改代码，等待原 Reviewer 窄复审及 controller 后续实施授权。

#### T5 补充窄复审结果与实施授权（2026-10-30）

controller 转达原独立设计 Reviewer 的窄复审结果为 **Accept，findings none，仅设计层面**；复审设计指纹为 `e38f3edf6fd623616a5d25f9e22f6f548812cdc87d5af8655fdf3b4a75be1ddd`，计划指纹为 `e14cc54a2fb8d211f0f15e4587ed6453b740db1f63c5b6770ebae775d51fd219`，与交付一致。首轮 Block 和 RAFT_COMMITTED early return 的修订原因保留，不改写为首轮通过。controller 依据用户既有推进授权恢复 T5 实施：先按批准补充完成 typed 非 native worker 与 RAFT_COMMITTED 恢复具名 fixture 的 RED→GREEN，再接通原 T5 native 全入口、漂移准入及最终 Commit。本记录仅解除本补充的设计实施等待，不代表实现 Review、运行验收、T5 passing 或 T3–T5 原子批次可交付；PROGRESS 仍由 controller 管理。


### T5 固定结果视图与既有幂等结果语义补充（2026-10-30，pending 独立窄设计 Review）

本节仅修订待审设计和具名测试计划，未授权生产实施。此前 typed 实现的局部 Accept、native 规格 Review 的 Revise、首轮应用迁移 45 tests／11 failures／5 errors 均保留，不将结果缓存造成的语义变化归为旧 fixture。T5 仍为唯一 active；不修改 PROGRESS，不运行 full、不切依赖、不提交。

#### 事实来源与不变量

`BrokerCluster.submitOperation` 首先读取已登记 operation，再调用 `BrokerClusterOperation.checkRepeatedIntent`：摘要、类型和目标匹配返回 DUPLICATE，不匹配返回关联原 operation 的 REJECTED。`CompleteLearnerSyncResult.failure` 与 `BrokerClusterAppTransformer.completeLearnerSyncResultFrom` 保留原 operation 的 acceptedEpoch 和 resultEpoch，不以当前集群 epoch 或重复请求 expectedEpoch 替代。成功终态重复不得返回首次 APPLIED；身份冲突不得改为 CONFLICT。D10、acceptedRevision、原 UNKNOWN／NOT_LEADER 的显式恢复规则不变。

#### 每永久槽三个固定只读 view

每个 typed learner 永久槽至多预留三个 Future 及其唯一只读 view：首次执行／同事实在途、稳定终态同事实重放、身份冲突。每个 view 仅在创建槽时分配一次；重复请求不追加 dependent stage、不创建 attempt、不占新 worker 队列槽。三个 view 同属一个永久槽，仍与 native、远端确认事实、其他 workflow 共享 4096 总预算；不回收槽、不增加线程或等待槽。

同事实在途仍返回首次 view，派生取消不改变账本。只有 worker 已确认存在已提交 operation、`checkRepeatedIntent` 明确匹配且该 operation 已处于稳定 SUCCEEDED 时，才能准备固定 DUPLICATE 重放结果。该结果保留原 acceptedEpoch／resultEpoch；后续业务操作或当前 epoch 改变不影响这些历史字段。不得用首次 APPLIED 简单改状态后猜测持久化成功，不缓存 RAFT_COMMITTED 或 PENDING 的阶段结果作为稳定终态。

确定业务拒绝、尚未持久化、UNKNOWN、UNAVAILABLE、NOT_LEADER 沿用原规则：未形成稳定成功终态时，不转为 DUPLICATE，也不借重放隐式发起恢复。原有允许的显式恢复仍创建有预算的独立 attempt；未知 native 工作仍保留原许可，不自动重试。确定拒绝的同事实重复复用首次确定拒绝 view，不伪造成功终态。

#### 身份冲突与并发边界

同 operationId、不同完整命令或摘要的请求，在途与终态均按原领域身份规则返回 REJECTED，不回传冲突请求的 brokerId、endpoint 或 expectedEpoch。响应载荷只有原 operationId、状态、原接受与目标 epoch、Leader hint 和通用原因；因此固定冲突结果可使用 `operationId is already bound to another request`，不包含请求特定内容。已存在 operation 时必须关联原 operation，保留其 acceptedEpoch／resultEpoch。

冲突到达时若当前 attempt 尚未取得已提交 operation，不能用调用线程读 Node，也不能将请求 epoch 当作已提交事实：仅在既有事实中设置幂等 `conflictRequested` 标志，多次冲突返回同一个固定冲突 view，不追加 Runnable 或 dependent stage。只要原 attempt 仍可能持久化，普通消费／阶段发布边界不得完成无关联 operation 的 REJECTED；该 view 保持等待原 attempt 的持久化结论。有已提交 operation 后，由原 worker 读取该 operation 并使用原 acceptedEpoch／resultEpoch 准备固定冲突结果。只有原 attempt 最终确定未持久化，才能在最终结果发布边界读取 committed snapshot，冻结未关联 operation 的 REJECTED（acceptedEpoch 为该快照 epoch、targetEpoch 为空）。必须区分「最终未持久化」与「尚未持久化」。stop／fault／close 若阻断此最终边界，则沿用既有未完成边界，不在 caller／callback fallback。新的显式 attempt 拥有自己的固定槽；此分支的身份排他不更改领域持久化合同。

worker 发布首次完成事实前，同步读取已提交 operation 并准备适用的终态重放与冲突结果。在同一 worker 的 mutex 临界区内，先固定结果／完成 replay 与 conflict Future，再完成首次 Future；不允许出现首次 done=true 而稳定重放结果尚未准备的窗口。纯结果映射不新增 Node 阶段，不绕过 generation、fault、close，也不在 shutdown 后通过调用线程或 callback 补发布。若角色失效／fault／close 阻止原阶段继续，沿用原关闭与未知边界，不以新重放 Future 永久等待一个本来已经发布的成功结果。已发布的稳定只读结果可继续读取，不重新授予执行能力。

#### 具名 fixture 与审查范围

生产候选仅 `BrokerMembershipPermitExecutor.java` 和 `BrokerMembershipAppService.java`；测试仅原授权的 `BrokerMembershipWorkflowTest.java`、`BrokerClusterAppServiceChangeTest.java`。不修改共享 fixture，不扩大真实拓扑文件。设计获独立 Accept 和 controller 实施授权后，先取得下列真实缺陷 RED，再最小修复：

- `terminalLearnerReplayPreservesDuplicateAndOriginalEpochWithoutNewAttempt`：真实 FSM 完成同步后推进当前 epoch；同事实多次重放为 DUPLICATE，原接受／目标 epoch 不变，零新 Begin／Commit／native／ReadIndex、总槽不增加。
- `learnerIdentityConflictReusesFixedRejectedViewAndOriginalEpoch`：参数化在途已持久化与稳定终态，变化 expectedEpoch／endpoint／目标产生 REJECTED，原 epoch 不变；所有矛盾请求复用固定 view，必须覆盖 Begin 前冲突→同一 attempt 成功持久化→再次冲突：两次冲突返回同一个固定 view，使用原 operation epochs，零新 attempt／stage；另测最终确定未持久化时才使用最终快照 epoch 与空 targetEpoch，不能在普通阶段消费时提前冻结。
- `terminalReplayPublicationIsAtomicWithFirstCompletion`：有界 latch 控制首次完成前后并发同事实／矛盾重投，验证三个固定 view、无新 stage／attempt 和无 done／replay 悬挂窗口；取消派生 Future 不取消执行事实。
- `nonTerminalLearnerOutcomesRetainExplicitRecoveryBudget`：确定拒绝不转 DUPLICATE，UNAVAILABLE／NOT_LEADER 仅显式恢复，新 attempt 仍计入共享 4096，fault／close 不恢复资格。

另外，六测试迁移已暴露 native 完成重试同样返回 APPLIED 的独立缺口：`advanceMembershipOnWorker` 对非 PENDING 阶段构建 applied。`successfulRemovalCanBeRetriedAfterEndpointBindingIsDeleted` 的既有 DUPLICATE 断言保留；本节不授权顺带修改 native 生产分支，需在整体 T5 修复范围中单独核实和复审。


#### T5 终态重放补充首轮设计 Revise 与窄修订（2026-10-30）

独立窄设计 Review 为 **Revise**：前版允许在普通阶段消费边界尚无 operation 时冻结固定冲突 view，同一 attempt 后续成功会永久丢失原 epochs。现仅修订本节与计划：Begin 前冲突仅置标志，仍可能持久化时不发布无关联拒绝；已持久化使用原 epochs，最终确定未持久化才在最终发布边界取快照。具名 fixture 补充同一 attempt 的 Begin 前／成功后冲突固定 view 身份与零新增依赖断言。保留本次 Revise，等待原 Reviewer 窄复审，未授权 learner 生产修改。


## T5 旧执行者 DRAINED 事实的定向显式重投补充（待独立 Review）

本节仅细化已批准的显式同事实恢复，不授权生产实施。2026-10-30 controller 要求先设计 Review；不增加 native 重试、管理面、SDK operation 或认证例外。T5 保持 active，D10／D16 不变。

### 实测缺口与原有边界

`BrokerRaftAdapterTest.selfReductionDrainsExactOldPermitOnNewLeaderWithoutLateConfigurationRollback` 两个真实三节点参数场景均完成自降级／自移除的首个完整目标并选出新 Leader。`/tmp/im-chat-t5-uDxRui/self-topology-diagnose-2.log`（exit1，两个等待超时）记录 native `APPLIED`、无异常、旧 executor 唯一 native slot 的 undrained 数为 0、新 Leader 中同 permit 仍为 UNKNOWN，旧业务 future 为 NOT_LEADER／`membership role generation changed`，旧 holder 的 Leader hint 为 null。此前 setup 失败日志保留，不算生产 RED；本次只证明明确的实现缺口，不以 15 秒无转发单独推断缺陷。

准确调用链是：native supplier 同步返回使 returned=true；APPLIED completion 写 TARGET_APPLIED 并排空；worker 发布 slot 结果；既有 confirmationExecutor 执行 reportDrainedPermit；该方法只采样一次 Leader hint，null 时返回 UNKNOWN；旧 membership workflow generation 已关闭，resume 又进入仅当前 Leader 可准入的 native workflow，不能重投旧事实。自移除的节点不再收新配置消息，不能假设 hint 最终恢复。这里本机已经 DRAINED，不属于 D16 的未知 native；持久化 UNKNOWN 仍不能单独充当证明。

### 最小 typed 入口与完整事实来源

候选应用签名为 `CompletionStage<MembershipPermitDrainedResult> retransmitDrainedMembershipPermit(ClusterOperationId operationId, BrokerId receiverBrokerId)`，仅放在 `BrokerMembershipAppService`；不接受 endpoint、permitId、digest、incarnation、结果或任意 target。入口只识别一次显式请求，不循环找 Leader。

executor 增加 package-private 的专用 typed 投递入口，接收 operationId、receiverBrokerId 与 owning service；在既有 native slot 上保存本次确认阶段和共享在途结果，不分配 native reservation／membership attempt，不增加池、队列或独立预算。caller 只做有界账本准入，不读 Node；worker 从该 operation 对应的原 native slot 读取不可变完整 permit 身份，要求 nativeReserved、executorBrokerId 为本机、executorIncarnation 为当前 executor incarnation、state=DRAINED 且 knownResult 为 TARGET_APPLIED 或 TARGET_NOT_APPLIED。UNKNOWN、旧 incarnation、缺 slot、远端事实槽均拒绝，不能根据持久化 UNKNOWN 猜测本机排空。slot 原身份与确定结果永不更新或重新开放。

worker 从本机已提交 operation 的 membershipSteps 中按原 slot 的 stepId 查找历史步骤（不只查 currentMembershipStep），核对 currentPermit 的 permitId、operationId、stepId、完整 target、executorBrokerId、incarnation 及 step.targetDigest；不从 caller 重建目标。operation 无此精确许可或已换许可即拒绝。确切已确认历史事实允许幂等重投，由现有确认 CAS 返回 DUPLICATE；不创建或重执行任何 native。首次投递与本入口共用既有每 slot confirmation 机制，同事实在途共享，矛盾／并发占槽请求失败关闭，不覆盖 pending。4096 共享永久预算及单 worker／单等待槽不变。

### 持久化步骤顺序与唯一许可选择

独立首轮 Review 为 **Revise**：operationId 不能唯一定位多步骤／多次失败重试的永久 native slot；并发、有界性和生命周期发布矩阵也未充分固定。保留该结论，本轮仅文档修订，待原 Reviewer 复审。

先核对既有领域合同：`BrokerClusterOperation.currentMembershipStep()` 取持久化 membershipSteps 中首个未成功步骤；`BrokerCluster.registerMembershipPermit` 只允许该步骤登记，并拒绝任何既有 undrained permit；`BrokerMembershipStep` 规定历史 permit 只能为 TARGET_NOT_APPLIED，currentPermit 是列表最后一个；`confirmMembershipPermitDrained` 仅确认每一步 currentPermit，精确已确认重复返回 DUPLICATE。因此不是任意历史 slot 都可投递，不能按 HashMap、slot 插入顺序或 permitId 字典序挑选。

worker 读取本机已提交 operation 后，严格按 membershipSteps 持久化列表序号选择：

1. 先校验步骤／许可身份一致性、成功前缀与 currentMembershipStep 顺序。扫描未持久化排空确认的 currentPermit；若超过一个、出现在非 currentMembershipStep、前序未成功却出现后序 permit、或本机事实与已持久化确定结果矛盾，拒绝而非择一。
2. 若存在唯一未确认 currentPermit，它是唯一候选，且必须具有当前本机 incarnation 的精确 nativeReserved DRAINED 确定结果 slot。若本机尚 UNKNOWN／缺 slot／身份不符，拒绝，禁止回退到历史 A 或跳到后续步骤。即使历史 A 已确认且其 slot 后插入，也必须选待确认 B。
3. 若没有未确认 permit，才允许历史幂等投递：仅考虑各步骤的 currentPermit（排除同一步已被后续 permit 替代的历史预留），要求持久化已 drained、本机精确 DRAINED slot 且结果完全一致，取其中持久化 step 序号最高者。最高候选的完整身份无法唯一消歧、存在矛盾或无符合候选则拒绝；不得因接收者不符合最高候选的 target 而回退较早步骤。多个已确认本机步骤时始终选最高序号，不按插入顺序。

所有选择发生在 worker，caller 不读取 Node。caller 的固定有界准入只合并同 operation 的显式请求；一个 operation 的多个 native slot 不能同时承载独立重投链。既有 slot 可保存一个 operation 级 admission 标记／固定请求字段，worker 消费时按上述规则解析唯一事实；只在已有槽中记账，不增加槽或持久化新事实。解析后选择的 permit／receiver 对在该次投递完成前固定，后续快照不能把在途请求迁移到另一个 permit；若发现绑定变化失败关闭。原自动首次确认与显式确认也必须共用该许可的单一发送占用，不允许分别建立两条发送链。

### 有界确认占用与发布协议

每个 operation 同时最多一个发送占用，对应唯一选中 permit 和 receiverBrokerId；最多一组当前 in-flight completion／只读 view／pending。首次自动确认一旦持有占用，显式同 receiver 请求共享其固定 in-flight view，不附加 thenCompose／dependent stage，不增加 send；不同 receiver 在占用中拒绝，不修改原 receiver／target／pending。若尚在 worker 选择事实阶段，固定 operation+receiver 请求仍共享唯一准入 view，不能先为每个 slot 或每次 caller 建 Future。receiver 只有在本次完成并释放占用后，才能由下一次显式请求重新指定；自动确认不会因显式请求再次启动。

完成后的显式投递允许下一轮创建一组当前 completion/view，但仅覆盖已无 pending／在途阶段的上一轮可变引用，不追加 attempt 集合、view 列表、回调链或结果历史；已返回给调用者的历史只读 view 不由 executor 累积保留。大量同轮重复请求零新增内部 stage；多轮显式请求的 executor 内部引用数保持常数，永久 slot 总数不增加。取消调用者派生 Future 不取消账本中的 completion、发送或 pending。

准入、worker 消费、send 前及最终结果发布均复验 fault／close。纯 DRAINED 事实不以旧 native generation 授权任何工作；失任可发送，但 fault／close 不可绕过。准入后消费前 fault／close：零发送、保留原 DRAINED；已经发送后发布前 fault／close：远端可能已提交，但本机不能发布未经最终复验的 COMMITTED／DUPLICATE 成功，不自动重发、不清除事实。既有 worker 可发布 UNKNOWN／关闭结果时只发布非成功；若 close/fault 阻止 worker 发布，保留未完成边界，不 caller／callback fallback。成功只由 worker 在最终复验后发布。不得把超时或取消当作释放原 native 权限的证明。

### 接收者、MTLS 与失任边界

receiverBrokerId 必须对应本机已提交 BrokerNode，具有已提交 membership endpoint 绑定，且该 endpoint 属于该 step 完整 target.voters；learner、非目标、缺绑定和任意地址全部拒绝。地址取该可信 BrokerNode.routeEndpoint，不把 Raft membership endpoint 当 Bolt 地址。投递前在 worker 复核完整事实和接收者绑定；不将接收者参数当作其当前 Leader 身份证明，接收方仍执行现有同任期一致读及完整事实 CAS。

现有 `BrokerPeerAdapter.confirmMembershipPermitDrained(receiver, request)` 已要求 MTLS observationsAllowed、本机 caller/executor 与 receiver 精确对应；`BoltTarget(receiver.routeEndpoint, BROKER_URI_PREFIX + receiver.id)` 将连接绑定至完整预期身份，`BoltInvoker` 不提供地址绕过。接收侧 `BrokerPeerAdmissionInterceptor.validatePermitConfirmation` 校验真实 MTLS、唯一 Broker URI、cluster/caller/executor/receiver 及持久化完整许可。它不要求旧执行者仍为当前 voter，因此无需恢复其成员资格。上述是静态能力核实，不是本次真实 MTLS 网络验收。

本机接收者也先经过相同 target voter／可信绑定／完整事实校验，再调用现有 trusted-local confirm 入口；不能因为本机而跳过应用校验。远端只调用既有 peer operation，一次请求仅投递一次。NOT_LEADER／UNKNOWN／超时原样返回，调用者再次显式选择接收者；不遍历候选、不 timer／Scheduler 轮询、不自动使用返回 hint 再投。失任允许投递既有 DRAINED，不恢复 native 或 membership workflow 的旧 generation。fault latch、close 保持既有失败关闭；worker 消费及最终发布复验 fault／close，拒绝时保留事实，不 caller/FSM fallback。

### 运维可达性与本次范围限制

当前 `BrokerMembershipController` 只有 add／sync／promote／demote／remove，没有定向旧 holder 的重投或 resume HTTP endpoint；Scheduler 只恢复当前 Leader。因而新增应用方法不等于现有 Ops／CLI 已能触达旧 holder。最小实现范围仅 AppService、PermitExecutor 及两个现有测试文件；不直接扩大 SDK／HTTP／Ops／CLI／认证。公开运维接入须后续在既有 Ops 授权与审计路径上另行明确请求路由到原 executor 及接收 BrokerId 的合同并 Review，不能向终端分发服务凭据、直接请求 Bolt 或新建管理面。该接入缺口在闭合前不得声称完整 CLI 恢复可用。

REMOVE_BROKER 的业务 Commit 才删除旧 holder 的 BrokerNode／membershipEndpoints；要完成该 Commit 必须先确认本 permit，因此缺口发生时原绑定仍在。被移出 Raft 的旧 holder 本机可能永久停留在 Commit 前快照；其 slot、operation 和目标中剩余 voter 绑定仍可核对，事实不是从新 Leader 反向伪造。接收者在后来也被移除或地址发生变化时失败关闭；本补充不承诺跨任意后续拓扑变化的可达性或无人工触发最终成功。

### 精确文件与具名验证

待本节独立 Accept 及 controller 实施授权后，生产仅修改 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipAppService.java`、同目录 `BrokerMembershipPermitExecutor.java`。不改 plugin、shared fixture、SDK、认证 interceptor 或 peer adapter。测试仅现有 `application/cluster/BrokerMembershipWorkflowTest.java` 和 `adapter/cluster/BrokerRaftAdapterTest.java`。

- `explicitDrainedRetransmissionUsesExactLocalFactWithoutNativeOrLeaderHint`：null hint、失任但未 fault／close、本机已 DRAINED；精确 receiver 一次投递，身份／target／incarnation／结果不变，native 计数不增；在途共享与拒绝不丢事实。
- `explicitDrainedRetransmissionRejectsUnknownForeignAndUntrustedReceiver`：UNKNOWN native、非本机 incarnation／远端事实槽、缺精确持久化 permit、learner／非目标／缺 binding、fault／close 全部拒绝，零 native／零发送；本机接收者同校验。
- `explicitDrainedRetransmissionSelectsPendingStepBeforeHistoricalSlots`：持久化历史 A 已确认、B 待确认，本机 A／B slot 正插与反插均选 B；B UNKNOWN 时拒绝不回退 A；多个待确认或非法跳步恢复快照拒绝。无待确认且多历史已确认时固定最高 step 序号的 currentPermit，排除同一步已替换旧 permit；无精确候选／矛盾身份拒绝。
- `explicitDrainedRetransmissionSharesAutomaticAndExplicitInflightWithoutGrowth`：用有界 latch 保持首次自动确认在途，同 receiver 显式请求及 5000 次重复均返回同一只读 view、send 总数仍为 1；不同 receiver 占用中拒绝且 pending／target 不变；取消派生不影响链。再完成 100 轮显式投递，检查每轮一次 send、永久槽不增、内部最多一组当前 completion/view/pending、无 history／continuation 累积，不以仅检查槽数替代引用有界性。
- `explicitDrainedRetransmissionRevalidatesLifecycleBeforeSendAndPublication`：参数化 close／fault；latch 分别卡在准入后消费前（零发送且事实保留）、send 后结果发布前（不发布未复验成功、send 不增、不丢 DRAINED）。失任而未 fault／close 对照允许纯事实发送，零新增 native；所有最终映射／发布仍在唯一 worker。
- `selfReductionDrainsExactOldPermitOnNewLeaderWithoutLateConfigurationRollback`：真实自降级／自移除，明确 native APPLIED 与本机 DRAINED，hint null 后显式指定新 Leader，实际确认读取／CAS 精确 drain，后续固定步骤与最终业务 Commit；迟到重复事实不增加 native、真实配置不回退，降级后 learner 复制及后续日志可写。peer 本地转发不冒称真实 MTLS。

先对新增具名恢复断言取得实际 RED，再最窄 GREEN；原 UNKNOWN／BUSY 阻塞断言不改为可恢复。命令按计划执行，最窄归因完成前不运行模块或 affected；本节设计不标任何实现 passing。


2026-10-30 定向 DRAINED 重投设计原 Reviewer 窄复审 **Accept，findings none，仅设计**；首轮 Revise 保留。controller 依据既有用户授权恢复批准四文件实施，先具名 RED→GREEN，再全部窄验证、模块成功后 affected。当前只批准内部应用接缝，不代表公开 Ops／CLI 恢复可用，也不代表真实 MTLS 网络认证证据；原公开可达性缺口保留。不得恢复 UNKNOWN native 或新增自动重试权限。


### T5 确认入口既有 CQRS 规范落实

2026-10-30 affected exit1，在 Maven 前停止，实际唯一 Java-style 命中为 `confirmMembershipPermitDrained(ConfirmMembershipPermitDrainedParams, String authenticatedBrokerId)`；先前将其归因为重投入口不准确，保留原日志与纠正。重投入口两个领域值对象未命中该 scalar checker，原批准签名不变，不新增重投 command。

确认公开应用入口改为 `confirmMembershipPermitDrained(AcceptMembershipPermitDrainedCommand command)`；仅落实既有 CODING_GUIDE 的单输入对象及边界字段规范，不改变许可、认证、CAS 或 native 行为。command 为 flat String 身份事实、自有封闭 Result 与独立 `authenticatedExecutorBrokerId`，不包含 SDK Params、security principal、endpoint、native target，不 implements consensus command。handler 将协议字段映射为 command，认证执行者仅取自已验证 principal；本机路径仅取自可信 slot 的原 executor。payload caller/executor 与独立认证身份、cluster/receiver 校验均保留，不允许 payload 补身份。executor 只将不可变 command 用于原在途事实 equals，不增加预算、链或重试。领域与原持久化 command/codec 不变。

缺失身份或结果在应用入口确定返回 REJECTED，不由 record 构造异常泄漏为连接异常；协议 Params 原有未知／缺失结果参数拒绝保持不变。封闭 Result 不提供 UNKNOWN。既有 MTLS 准入、旧 holder 精确例外、本地同应用校验／CAS 仍须原样验证。旧模块1516项通过仅为改前输入历史证据；改后须 drift、三类 focused、模块、affected 重新验证。原设计 Accept 不自动覆盖本次改后输入。


## T5 Runtime 部分启动失败的资源生命周期补充（待独立 Review）

### 已证事实与非目标

2026-10-30 CQRS 改后三类 focused 重跑 88 项全零；后续模块 exit1，fresh 报告 498 项、0 failures、1 error，Broker 尚未执行，affected 未启动，输入无漂移。失败为 `RaftRuntimeTest.minorityCannotSubmitPayloadOrConfirmLeadership` 在 `127.0.0.1:52137` RPC bind 时抛 `BindException: Address already in use`。失败后同 fork 的后续测试仍出现该节点 ElectionTimer，表明构造失败未清理已初始化 Node。原日志 `/tmp/im-chat-t5-uDxRui/cqrs-module.log` 与 XML 保留。此前另一配置测试端口 57336 bind 失败属于相同启动失败类别，不能推断相同占用来源。旧 holder 复制的 ConnectionClosed 根因尚未证明同源；原 88 项 1 error 及之后隔离、重跑成功分别保留。

端口分配的 close-before-bind 有 TOCTOU，失败时占用者未知。本补充只修部分启动资源所有权，不调整两个测试 fixture 的 reservation、不增加重试或超时、不切依赖、不改变配置执行和 D10/D16。保留端口直到紧邻 bind 也不能消除外部竞争，不以 fixture 改善替代生产清理。

### 上游核实与两条候选路径

已逐文件比较本地官方 `com.alipay.sofa:jraft-core:1.4.1` 与当前私有 pin 的 source jar：`RaftGroupService.java`、`NodeManager.java`、`RaftServiceFactory.java`、`rpc/RaftRpcServerFactory.java` 内容一致。此为上述 API／实现核实，不是官方构建或 T6 验收。

`RaftGroupService.start` 按 addAddress、createAndInitRaftNode、RPC init、started=true 排序。RPC bind 失败时 getRaftNode 可取已初始化 Node，但 shutdown 在 started=false 时直接返回。更早的 Node.init=false／抛异常发生在工厂内部，group 字段尚未得到局部 Node；工厂没有失败清理。默认 group 构造还隐式创建两个 RPC executor。其 shutdown 吞 RPC cleanup 异常，不能作为「全部资源已回收」的证明。

- 路径 A：保留 group，显式传入本实例 RPC 与两个 executor；RPC init 失败时取 getRaftNode 后精确 shutdown/join，单独清 RPC、executor 和 address。此路径对本次 bind 失败足够窄，但不能回收工厂未返回的 init=false／抛异常 Node。若另以自定义 group override 重写 start 保存 Node，则实际已复制生命周期，还须处理 group 私有 started 状态；不采用反射获取私有 Node、不增加只为测试的构造器。A 只有在独立核实存在不丢失局部 Node 的官方扩展点后才可选，当前不冒称满足全部失败路径。
- 路径 B（提交 Review 的推荐）：Runtime 显式拥有由 `RaftServiceFactory.createRaftNode` 返回的 Node，先保存引用再 init；显式创建与默认 group 相同的私有 RAFT／CLI executors，使用 `RaftRpcServerFactory.createRaftRpcServer(endpoint, raftExecutor, cliExecutor)` 安装原处理器；保留 getOptions 的配置、disableCli、线程池大小、StateAdapter、role listener 与 fault latch 语义。只替换不可见的 group 资源封装，不改变业务执行或共享 Node 行为。init=false 转为原同义启动失败，但不能与完整 init 后 RPC init 失败共用无条件 shutdown/join；具体保证按下述 Block 修订分层。

独立 Reviewer 必须比较 A/B 的实际可行性与配置等价；本节不是生产实施授权，不预先认定 B 已通过。

### 所有权、退出与异常合同

Runtime 不支持共享 RPC server。address 是 NodeManager singleton 中的裸集合，addAddress 无所有权 token。为防止同 endpoint 已有实例被误删，Runtime 在创建任何 Node 前，以 NodeManager 同步边界检查 serverExists，并维护本 Runtime 路径的 endpoint 独占认领；已存在即拒绝且零 removeAddress。认领、注册、释放都在同一同步协议中，本实例仅清理自己成功登记的 address，Node shutdown 只针对保存的本实例 Node，不调用 clear 或按名字关闭其他实例。测试覆盖同 endpoint 的现存健康 Runtime 仍可提交、不会因第二实例失败被删登记。第三方绕过该协议并直接修改 singleton 的并发行为无法由裸集合 API 原子辨认；不承诺支持此类混用，不能将检查再删除误称全进程通用所有权。若实现需要额外生产 registry／锁对象，先由 Reviewer 确认最小必要性，不扩展成通用框架。

仅完整 Node.init 成功后，启动异常才可进入本实例 RPC shutdown、Node shutdown/join、私有 executor shutdown/等待及 address 释放的完整退出路径；init 未成功的路径禁止套用此顺序，见下述 Block 修订。某一步失败不能跳过其他已证明安全的资源清理。Node join 未完成时不能释放 endpoint 认领、不能报告 CLOSED 或允许同 endpoint 重新准入。只有所有必需清理已确认才标完成，否则保留失败并明确资源可能残留，不宣称全部回收。原启动异常保持主异常，后续 cleanup 异常逐项 suppressed，不以 cleanup 覆盖主因；中断保留 cause、恢复线程标志，继续可执行的非阻塞清理，不以吞中断假称 join 完成。正常 close 的异常同样可观察，失败后保留可继续清理的状态，而非无条件置 closed。

close 使用 Runtime 内部专用生命周期状态串行化，不在 Node 回调线程持有的角色锁上等待 join；并发 close 只能有一个清理者，后续 close 等待／观察同一终态，完成后重复 close 无额外资源操作。构造失败对象不发布；成功对象 close 开始即封闭本实例准入，角色 snapshot 置为非 Leader并推进 generation，后续原生角色事件不能重新开启已关闭准入。listener 短记账和同一 fault latch 保留，首因不覆盖、不因重试构造或 close 清除。不能为关闭重建 listener、绕过 fault 或新增 callback→Node 调用。如何处理 cleanup 中断后的再次 close 须通过状态与测试证明，不引入无限自动后台清理。

### 确定性真实失败测试与证据边界

在既有 `RaftRuntimeTest` 中用实际 `ServerSocket` 占住唯一 loopback 端口，保持到失败构造返回；使用真实 Node／Bolt server，不注入 fake native。测试不仅断言 BindException cause，还核对该 group+PeerId 的 NodeManager Node/address 已移除。修复前应在仍存活的 Node/address 断言上 RED，预期 bind 异常本身不是 RED。fixture finally 必须仅捕获并关闭自己 group+PeerId 的失败 Node，释放测试 socket，避免 RED 污染后续测试；不能 clear 全局状态。

在失败返回后通过精确 group+endpoint 对应的 timer 对象／节点资源观测证明已 stopped/destroyed；若线程断言，只捕获实际 Node 持有的 timer/executor 所属线程对象，不扫描全局相似线程名。NodeManager absence 单独不等于线程退出；需结合 Node join、精确 timer 状态、角色事件不再增长及本实例目录 RocksDB 可重新打开。释放占用 socket 后，同 identity／目录的新 Runtime 能启动并提交，再正常 close；此为资源回收证据，不是自动启动重试。检查外部占用 socket 在失败清理后仍保持绑定，证明未清理他人资源。

另覆盖同 endpoint 已有健康实例、真实 Node.init=false／抛异常可到达边界、正常／重复／并发 close 与调用线程预置 interrupt 的可观察结果。init 失败优先使用真实非法配置／不可用目录触发并保留 cause，不用仅测试用生产构造器；若找不到可确定触发的真实分支，报告覆盖缺口并由 Review 决定，不冒称覆盖。suppressed cleanup 异常优先现有真实可测边界；无法可靠触发时标 Review-only，不凭空添加无 caller seam。精确 Node 资源观察可用局部测试反射，不增加生产诊断公开 API；字段跨官方／私有差异在 T6 单独验证。

### 精确文件范围与验证顺序

生产仅 `im-plugin/im-raft/src/main/java/com/co/kc/imchat/plugin/raft/core/RaftRuntime.java`；测试仅同模块 `src/test/java/com/co/kc/imchat/plugin/raft/core/RaftRuntimeTest.java`。稳定文档为 `im-plugin/im-raft/README.md`、`docs/references/HARNESS_GUIDE.md`；规则 matrix 另列最小一行 `docs/references/HARNESS_RULE_MATRIX.md`，记录部分启动必须回收本实例资源与确定性占用端口 fixture，异常聚合／singleton 所有权先标 Review-only 条件。不改 RaftConfigurationIntegrationTest、端口分配、依赖或 checker。本设计与 active plan 在设计 Review 阶段先更新，README/Harness/matrix 待实施同任务沉淀。

Review Accept 后仍须 controller 授权实施。先运行 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-raft -am '-Dtest=RaftRuntimeTest#occupiedRpcPortFailureReleasesOwnedNodeAndResources' -Dsurefire.failIfNoSpecifiedTests=false test` 记录真实资源残留 RED，再修复与同命令 GREEN；其后 Runtime 全类，再配置 integration 全类。前层失败停止，不直接模块重跑求绿。所有原失败／指纹保留，改后源码必须重新独立 Review；模块和 affected 另待实际窄证据后确认，不运行 full、不更新 PROGRESS、不提交。


### 首轮独立 Review Block 与收窄候选（未获接受）

首轮生命周期设计独立 Review 为 **Block**，原结论保留：保存 Node 引用并不能保证 partial-init 的 shutdown/join 安全。只读核实官方 1.4.1：Node.init 在启动 applyDisruptor、分配 FSMCaller 后调用 initLogStorage；LogManager.init 在 logStorage.init=false 时返回，diskQueue 尚未建立。其 shutdown 无条件调用 stopDiskThread，先创建 shutDownLatch，再通过 group executor 异步向 null diskQueue 发布；异步异常不能释放 latch，join 可永久等待。Node.shutdown 在此阶段还会尝试其他未完整初始化的组件，因此不能只去掉 join 后仍盲目调用 shutdown。当前不以反射拆生产组件补救，不修改依赖，不使用后台超时 cleanup 框架。

修订候选将两类启动失败严格区分：

1. **Node.init 已返回 true，之后 RPC bind 失败**：本批可确定修复的完整初始化退出路径。保存 initSucceeded 事实后才允许 Node.shutdown/join；所有权、异常聚合、interrupt、并发 close 与已有 endpoint 保护仍按本节合同。cleanup 任一步失败时不得报告全部回收，必须保留失败。真实占用端口 RED/GREEN 仍覆盖此路径。
2. **Node.init 返回 false 或抛异常，尚未确认完整初始化**：不调用 Node.shutdown 或 join，不尝试推断具体内部初始化阶段。只关闭确定为本 Runtime 独有且生命周期可安全关闭的 RPC server 与两个私有请求 executor；若 RPC 自身 shutdown 失败，附加 suppressed 并明确保留。永久保留本 endpoint 的认领与已登记 address，禁止同进程复用或再次构造，原异常返回。不能将构造失败的责任交给不可达 close。保存 Node 引用仅用于诊断所有权，不因此宣称已清理。该路径可能残留 applyDisruptor、timer 对象／调度器、storage、FSMCaller、计数与后续阶段资源；具体已创建哪些取决于失败位置，不能承诺无活动线程、无连接或磁盘锁已释放。

第二类仅为候选风险处置，不是已批准的可靠回收方案。Runtime 不擅自 System.exit、不终止用户进程；启动方收到异常必须中止服务启动并由其进程所有者终止／替换该进程，不能 catch 后继续提供服务或用新端口绕开 poisoned endpoint。通用插件无法强制宿主遵守，构造异常返回也不能证明进程可安全继续。当前范围内没有对任意 partial-init 提供安全完整退出的已证方法，原先「全部失败均回收」保证撤回。是否接受「完整 init 后清理、partial-init 进程不可继续复用」的收窄合同，需要 controller 与原 Reviewer 明确决策；在此之前保持 Block，不能标 passing 或先行实施。若产品要求任意 init 失败后进程仍安全可用，本范围方案不足，应另行评审上游生命周期修复，不能隐式扩大本批。

### storage-init 失败的隔离验证计划（尚未执行）

在 `RaftRuntimeTest` 的测试 JVM 中 fork 专用 Java 子进程运行具名 probe，父进程施加有界等待；仅对自己启动的 probe 超时进程做终止兜底，不是生产 cleanup timeout。probe 使用真实 Runtime／默认 storage：预先把 `dataDirectory/log` 建为普通文件，令 RocksDB 日志目录初始化失败。须先确认该路径实际到达 logStorage.init=false 而非更早抛异常；若观察为 throw，单独分类并报告，不能冒称 false 分支。不依赖文件权限（root 环境可能绕过），不 mock Node、不新增生产测试构造器。验证命令候选 `mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-raft -am '-Dtest=RaftRuntimeTest#storageInitFailureReturnsWithoutUnsafeJoinInIsolatedProcess' -Dsurefire.failIfNoSpecifiedTests=false test`，仅在独立 Review／实施授权后执行。

子进程输出结构化阶段：init failure cause、构造异常已返回、endpoint 再次准入被拒、外部占用资源未被关闭；只在测试侧精确检查该 Node 的已创建 applyDisruptor／timer／storage 状态和 NodeManager/address。这些观察用于列残留，不把 absence of callback 当线程退出证明。父进程验证在限时内收到「原异常返回」标记而不是卡在 cleanup；然后要求 probe 明确退出，父进程只回收自己创建的子进程。若 probe 原异常无法返回、safe RPC shutdown 也挂住，记录实际栈与资源状态，候选失败、继续 Block，不加生产超时或吞异常。任意未测到的 partial-init 分支、cleanup suppressed 边界与进程终止依赖均写入残余风险；不将 probe 的进程退出包装成 Runtime 已自行回收。

### 修订后的精确范围与决策边界

本次仅改本设计和 active plan。原 endpoint monitor 协议、A 工厂局部 Node 不可见的核实结论不重做。未来候选实现仍仅 Runtime、RuntimeTest、README、Harness 与 matrix 最小行；稳定规范必须明确区分完整初始化清理保证与 partial-init 不可复用风险。先等待原 Reviewer 对收窄候选的判断，不启动任何 RED、隔离、模块或 affected。原模块 498 项／1 error、focused 88 通过与旧 learner 连接根因未知均保持。


### RPC 阶段第二轮 Review Revise 与局部保证（待窄复审）

原 Reviewer 第二轮结论为 **Revise**：partial-init 不 shutdown/join 的收窄方向成立，该遗留是上游既存缺陷，不是本批新增；但 RPC shutdown-before-start 不安全，已自清理后的重复 shutdown 也不能误报 cleanup 失败。controller 接受本批仅修完整 Node.init 后 RPC 失败回收，partial-init 不作为本局部修复的新增验收条件。它仍可能残留资源；宿主启动失败后不能复用该进程，须由进程所有者替换。插件不主动 exit、不强制全进程 poison、不承诺消除上游残留，不表示最终 full 通过或运行故障已被接受。此前要求隔离子进程证明 partial-init 的计划保留为后续诊断候选，不阻塞本局部修复且本轮不执行；不能用未执行计划宣称已覆盖。

核实依据：官方与私有 pin 的 Node.init 对服务端仅检查 NodeManager.serverExists(endpoint)，不持有 RpcServer 引用；原 group 本来也在 Node.init 返回后才 RPC.init。因此可在成功登记 address 并完整 Node.init=true 后，才分配 RPC 对象与两个私有请求 executor；Node 自有 client／replicator 仍由 Node 管理，不改变其默认配置。NodeOptions.disableCli、默认线程池大小、RaftRpcServerFactory 的完整 processors 保持等价，绝不为 cleanup 主动启动 RPC。

Bolt 1.6.6 source 与本地 1.6.12 字节码均显示 AbstractRemotingServer.startup 在 super.startup 后执行 doInit/doStart；catch 中调用自身 shutdown，只有 shutdown 正常返回，才构造 `IllegalStateException("ERROR: Failed to start the Server!", originalCause)`。shutdown 先调用 super.shutdown，再 doStop；shutdown-before-start 或已关闭后的重入会在前者抛 LifeCycleException，不执行 doStop。RpcServer 在对象字段初始化时已分配私有 bossGroup，不能把「从未 init」等同于「无资源」，也不能只看 started=false 判断回收。

RPC 清理合同按阶段分开，取代此前不区分阶段的「关闭私有 RPC」表述：

- **未创建**：Node.init 未成功时根本不分配 RPC 对象／两个请求 executor；因此 partial-init 路径不尝试 RpcServer.shutdown，避免制造 bossGroup 残留。已有 Node 内部残留按上述宿主处置限制报告。
- **创建中／已创建未启动**：记录已取得的私有资源。构造／processor 注册异常可能使对象未完整返回；不能盲 shutdown-before-start、不能为可关闭而主动 init、不能反射拆 bossGroup。此类 native 工厂阶段若无法用公开 API 确认安全回收，清理完整 Node 与已取得的请求 executor，但保留原异常和 RPC 资源状态未知，禁止声称全回收。它不自动归入「已自清理」；作为上游公开生命周期限制明确保留。若 Review 要求覆盖此任意工厂异常的完整回收，当前最小范围不足，继续报告而不加框架。
- **启动尝试中**：明确已调用且首次调用 RPC.init，不能只用 isStarted 标志。占用端口测试应实际进入 Bolt doStart/bind。启动失败若正常经过 Bolt catch→shutdown→doStop→约定包装异常返回，可判为「native 同步清理调用已完成」；这不单独证明异步线程全部退出。可靠识别必须同时依赖本次首次 init 的阶段事实与已核实的特定 Bolt 包装路径／原 bind cause，不接受任意同消息异常或仅 started=false。若无法可靠识别具体适配器／调用路径，则状态为未知并保留风险，不吞异常。
- **启动失败已自清理**：不再次调用 shutdown，不把预期的 already-closed LifeCycleException 加为 cleanup 失败。原 bind cause 仍为主因，随后清完整初始化的 Node、私有请求 executor及自己登记的 address；真实 fixture 另以精确本实例 bossGroup／Node timer 与目录释放验证实际退出。不能将异步 doStop 返回夸大成全部线程已终止；测试若不成立，必须停下修订，不能凭 wrapper 掩盖。
- **启动成功**：正常 close 对本实例 RPC 只调用一次 shutdown，随后 Node shutdown/join、私有 executor 与登记释放。若 shutdown 抛错，保留该真实失败；后续 close 只能继续已证明可安全执行的剩余清理，不承诺再次 shutdown 能修复 native 已切换状态但未执行完 doStop 的缺陷。不能将任意 LifeCycleException 当已关闭成功，不能在失败后假称 CLOSED／资源全释放。

close 幂等指已确认完成的资源动作不重复；重入不是「native cleanup 必定恢复」的保证。cleanup 失败与无法确认阶段保持可见，原异常与 suppressed、interrupt、endpoint 所有权及角色／fault latch 规则不变。完整 init 后的真实 bind 失败是本批确定性 RED→GREEN 的目标；created-before-init 的工厂异常与上游 partial-init 资源残留是明确未消除边界，交原 Reviewer 核准，不自行当作全部资源安全。

本次仍仅两文档，未执行测试、隔离重跑或模块。实现须先获独立窄复审 Accept 与 controller 另行授权。原模块 498 项／1 error、focused 88 通过、旧 holder ConnectionClosed 未证同源保持不变。


### 完整 Node 初始化后 RPC 失败的局部实施状态（2026-10-30）

收窄设计经原 Reviewer scoped Accept 后获实施授权。Runtime 改为显式持有 Node、地址与私有请求 executor；完整 Node.init 成功后才创建 RPC。首次默认 Bolt bind 失败只在具体适配器、原包装异常栈位置与 BindException cause 同时匹配时认定同步自清理已返回，不重复 shutdown；未知工厂／启动状态保留 suppressed 与地址认领。正常 close 关闭准入、串行释放，保留 RPC cleanup 失败与 interrupt，已释放地址不因重复 close 删除后继 owner 的认领。partial-init 不安全 shutdown/join 的上游限制未消除，宿主仍须替换进程。当前实现未获独立源码 Accept，不代表 T5 完成。

证据目录 `/tmp/im-chat-t5-uDxRui`：`lifecycle-red` 为真实占端口 1 test／1 failure，NodeManager 残留精确自有完整初始化 Node；`lifecycle-green` 仅 production compile 失败（Executor 返回类型），fresh XML=0，不计 GREEN；核实 native 工厂实际构造 ThreadPoolExecutor 后保留 Executor 并按 ExecutorService 关闭，`lifecycle-green-2` 为 1 test／0 failure／0 error，包含 Node/address、timer、applyDisruptor、精确 bossGroup 终止和同目录端口重新提交。原日志、fresh XML、hash 与 drift=[] 均归档，后续修改不冒称沿用该输入验收。

`lifecycle-runtime` 全类为 47 tests／0 failures／1 error，drift=[]。`learnerReceivesCommittedLogsAndCanBeRemoved` 首次 follower.membership() 抛 `Applied configuration unavailable`，cause 为 `No applied configuration`，不是 bind 失败。该 fixture 原本在 Leader addLearner 成功后立即读 follower applied membership，未先证明 follower 已应用首条配置；现有 untilAsserted 不重试 IllegalStateException。日志显示 learner 初始 committedIndex=0、随后连接成功但仍 prevLogIndex=1/local lastLogIndex=0，fixture 失败清理后才出现 lastCommittedIndex=2 的迟到推进。现有证据支持缺少 learner applied-ready 等待前提，但不能证明生产推进始终正常或排除启动时序变化；不得标为已证旧 fixture 或 flake。Node.init→RPC.init 顺序与原 group 相同，仅 RPC 分配时间后移。membership 既定 fail-closed 合同不变，不缓存／回退初始配置，也不吞任意 IllegalStateException。

已停止后续验证，ConfigurationIntegration、module、affected 均未执行；未重跑求绿。下一步先独立源码窄 Review 与失败归因，若批准 fixture 修订，应采用真实 follower 应用提交的可观察前提，或只识别确切临时 unavailable 原因；不可放宽全部异常或删除最终成员断言。原模块 498 项／1 error、Broker 未执行、旧 learner 连接根因未知的历史事实保留。


### 生命周期源码 Block 修订与窄验证（2026-10-30）

原源码 Review **Block** 保留：close 未封闭 role publication、请求 executor 未等待 termination、join 中断后 finally 丢失 shutdown 异常。三项新增具名测试在原实现上真实 RED：`lifecycle-review-red` 为 3 tests／3 failures／0 errors。修订采用独立 role publication monitor、先请求全部 executor 停止再等待 termination、统一主异常与 suppressed；不持 publication monitor 等待 Node，不重做已接受 Bolt 识别与 endpoint 协议。

controller 批准调整 learner fixture 前提：原 submit(9) 和结果断言前移，bounded 等 learnerState=10 后再读 membership；原后续成员、ReadIndex、nonleader、remove、immutable 断言保留，无 sleep 或异常忽略。该修订不宣称排除生产回归。

fresh 证据均在 `/tmp/im-chat-t5-uDxRui`，每次 drift=[]：`lifecycle-review-green` 为包含真实 occupied-port 的 4 tests／0 failures／0 errors；`lifecycle-runtime-2` 为 50 tests／0 failures／1 error，membership interrupt fixture 的预置 interrupt 在资源已终止时被新 cleanup 错当实际等待中断。已据此窄修：仅仍未终止的请求 executor 才因跳过等待产生中断失败；保留原 interrupt 标志，不改变 membership 异常断言。随后 `lifecycle-runtime-3` 为 50 tests 全通过，包含全部新回归；`lifecycle-integration` 为 5 tests 全通过。先前 compile 失败、47 项 learner error、源码 Block 和 runtime-2 error 均保留，不冒称首次全绿。

- [x] 原 Runtime/Test 范围内三项真实 RED→GREEN 与 learner 前提修订；fresh XML/hash 归档。
- [x] Runtime 全类 50 项通过后 ConfigurationIntegration 全类 5 项通过；更新 README、Harness、matrix 最小合同。
- [ ] 将当前完整 task-only delta/hash 与历史证据交原 Reviewer 源码窄复审；未经 Accept 不标 T5 passing。

本批未执行 module、affected、full，未修改 PROGRESS、依赖、reservation 或其他生产文件，未提交或委派。partial-init／工厂返回前异常的残余风险不变，旧 holder 连接根因未知仍保留。


2026-10-30 原生命周期 Reviewer 对补丁 `a9c61044cb81c487f68a76ab5e55d85900a4e42db4d54d33a3195d2d648a6c46` 独立核对源码、hash 与 3 RED／4 GREEN／Runtime 50／ConfigurationIntegration 5 的 fresh 证据，局部源码规格复审结论为 **Accept，findings none**。该结论不覆盖 T5 整体、module、affected 或 full，历史 Block 与失败记录不变。独立局部代码质量 Review `ad80046503a314624` 已启动，当前待审；生产与测试输入冻结，不执行 module／affected。后续仅在质量 Accept 后按 controller 授权恢复 module→affected，若质量提出必修则先修复。PROGRESS 未修改，未提交。


2026-10-30 局部质量 Review 结论为 **Revise**，仅规范必修，安全与正确性 findings none：Runtime 新增的 `Executor` 与 `BindException` 无同名冲突，应使用 import 与 simple names。已仅作机械导入修正，保留 Executor／instanceof ExecutorService 及全部行为，未修改测试。精确源码补丁 `/tmp/im-chat-t5-uDxRui/lifecycle-quality-import-only.patch`，SHA256 `7cbd5c2f7d798b76647f96ba2148fdc60a50b6d467d3a83c35eddb4fe3d27535`；before／after hash 与原件已归档。`git diff --check` 通过；`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-raft -am -DskipTests compile` exit 0，drift=[]，fresh 测试 XML=0，不冒称测试重跑。待原质量 Reviewer 机械复核 Accept，未执行 module／affected，历史 Revise 保留；未修改 PROGRESS，未提交。


2026-10-30 原局部质量 Reviewer 对 import-only 修订逐字节核实机械等价，窄复核 **Accept，findings none**。生命周期局部规格与质量双 Accept 已取得，历史 Revise／Block 保留，T5 仍 active。controller 授权以当前累计输入先执行隔离 Maven Broker reactor module，成功且归档后再执行 affected；每段冻结全部 Git-visible 输入并归档 fresh XML／log／hash，段内不改 Git-visible 文件。该授权不包含 full、T6 依赖切换或完成 T5，后续仍需累计规格与独立累计质量 Review。


### 累计 module 与 affected 证据（2026-10-30，T5 仍 active）

局部生命周期规格与质量双 Accept 后执行累计门禁。首轮 `cumulative-module` 为 1524 tests／0 failures／1 error，全部 Git-visible 输入 drift=[]；唯一失败是 `BrokerMembershipPermitExecutorTest.runtimeAndExecutorShareFirstFaultAfterPartialRoleAccountingAndNeverReopen` 仍 mock 已不使用的 RaftGroupService 构造，FSM callback 未捕获而为 null。按 controller 预授权，仅将该既有 fixture 改为实际 Node／RPC 工厂入口 mock，原 observable／fault／首因／零 native 断言保留；精确补丁 `cumulative-fixture-task-only.patch` SHA256 `15d8a9238a0fa4d1fd3b6697d21ffc842321e1d6f6b670f22f3b43d220c1a3d9`。修后该测试全类 27 项通过，未修改生产行为，不将首轮失败隐藏为成功。

重新冻结后，`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am test` 成功，`cumulative-module-2` 为 1524 tests／0 failures／0 errors／0 skipped。归档后执行 `MAVEN_OPTS="-Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh affected`，成功，2507 tests／0 failures／0 errors／2 skipped；两项 skip 均为 LocalShardingRoutingIntegrationTest 的本地 MySQL opt-in 未设置 `im.test.local.mysql`，不冒称真实 MySQL 集成通过。affected 实际范围为 Broker SDK／Server、E2E、im-raft、Architecture Test；drift checker 通过，Harness status=passed 且 worktreeStable=true。MAVEN_OPTS 使用 Maven launcher 官方支持的 JVM 参数，生效仓库探针和完整命令已归档。

证据均在 `/tmp/im-chat-t5-uDxRui`：每段 start JSON 覆盖全部 2788 个 Git-visible 文件（包括干净 tracked 文件与当前 PROGRESS），module-2 与 affected 输入逐文件相同，各段 drift=[]。`cumulative-input-correspondence.json` 记录输入总 hash 与精确 manifest；各 `*-reports/manifest.json` 保留 fresh XML／log SHA 与实际命令，`cumulative-affected-harness.json` 为 Harness 原记录副本。该输入证据不借用历史 1516／88。本段结束后的设计／plan 状态追加不冒称原 frozen-input 验收。

当前仍待累计原规格复核和独立累计质量 Review，T5 不 passing。历史 CQRS compile／旧 learner 连接失败、模块 bind 失败、生命周期 Review Block／Revise、Runtime 两轮失败与本次旧 fixture error 均保留；旧 holder 连接根因仍未证明，partial-init／工厂返回前异常资源限制仍未消除。未执行 full／T6 依赖切换，未修改 PROGRESS，未提交或委派。


2026-10-30 独立累计质量 Review **Revise**：Critical 与行为缺陷 none，仅新增生产 FQCN 规范必修。逐项以最新 29 scope 的准确 before 检查新增生产代码，机械修正 Configuration 中 Micrometer 类型、AppService 中 Transformer／AtomicLong／Stream 及 Adapter 中 Collectors；无同名冲突，不清理历史全树，不改行为或测试。精确补丁 `/tmp/im-chat-t5-uDxRui/cumulative-quality-import-only.patch` SHA256 `46af2ba3b034e0b42f79380f152aa11fa2f9e071c2ee16235086373746789935`，before 原件与 hash manifest 同目录归档。归一化仅移除 import／空行并替换所列类型 token，其余字节（含缩进）逐字节相同。

`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -DskipTests compile` exit 0，全部 Git-visible drift=[]，fresh XML=0，不计测试。待原质量 Reviewer 机械复核 Accept；未重跑 module／affected／full，原 1524／2507 绑定此前输入，不称当前新输入 fresh 通过。PROGRESS 未改，未提交。

同规则重复出现，最窄后续自动化候选是扩展现有 Java style 检查的新增行检查：仅新增生产类型引用 token，排除 import／package、注释、字符串与真实同名冲突；配套 FQCN 新增失败、simple-name 新增通过、字符串与冲突通过、历史未改行不触发的正负 fixture。可靠 token／冲突识别和准确 diff baseline 未建立前维持 Review-only；本批未获 checker 扩展授权，不实现新框架、不放宽规则或添加类名豁免。
