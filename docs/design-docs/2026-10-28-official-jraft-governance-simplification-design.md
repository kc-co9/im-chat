# Broker 治理职责简化与官方 JRaft 迁移设计

> 状态：草案，待独立设计 Review。D1–D9 已由用户确认（见附录 B）。「摘要」至「开放问题」为当前设计正文；附录 C 是历史研究，只作追溯，不再作为当前推荐。设计获批前不创建 execution plan，不修改生产代码，不切换依赖，也不删除补丁。当前 Bolt 计划 T3 仍按私有补丁实现收口，本文不改变其验收要求。

## 摘要

当前 Broker 业务治理依赖私有 JRaft 补丁的 `getAppliedConfiguration`。观察资格、Peer 准入和成员维护恢复都把「本机已应用的完整 Raft 成员配置」当作业务判断前提。结果是通用 `im-raft` 承载了一项非官方能力，业务协议也要求了框架内部才有的证明。官方示例的用法是：业务定义状态，`Node.apply` 提交命令，`StateMachine.onApply` 修改状态，需要一致读时调用 `readIndex`；没有要求应用读取本机已应用成员配置。

核心方案分四点：

1. **观察资格由已提交业务状态决定。** 观察者集合取已提交 Broker 状态中的 VOTER 角色节点，不再逐项比对真实 Raft voter 列表。故障阈值是业务策略；共识多数由 JRaft 在每次业务状态提交时负责。
2. **显式成员维护改为提交完整目标配置。** 登记操作时确定完整目标配置并持久化，由 Leader 调用官方 `changePeers(目标)`。官方实现对进行中的变更返回 EBUSY，对与当前配置相同的目标立即返回 OK，因此按同一目标重试即可收敛，不需要先读本机已应用配置。
3. **Leader 侧做漂移诊断。** Leader 用官方 `listPeers`/`listLearners` 比对实际配置与业务期望配置。不一致时告警，并拒绝新的成员维护；自动路由接管不受影响。
4. **`im-raft` 只保留官方能力的通用封装。** 删除 `membership()` 与单步增删接口，新增 `changeConfiguration` 和 `leaderConfiguration`；依赖切回官方 `jraft-core`，删除补丁与 recipe 的有效入口。

关键取舍：不再证明「观察者恰好是本机已应用配置中的 voter」。业务记录与 Raft 实际配置的差异改由 Leader 诊断显式暴露，而不是在每个节点上隐式取消观察资格。旧执行者迟到调用的问题（附录 C 已调查）本方案不解决，补丁同样不解决；本方案以单一调用路径和漂移诊断限制其影响，列为残余风险。

发布边界：不支持新旧版本混跑；升级前要求没有未终态的成员维护操作。

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
  - 不解决旧执行者迟到调用（D8 已暂停）。
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
 决策、入站准入共用            按官方 BUSY / 同目标语义收敛
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

- `RaftConfiguration(List<String> voters, List<String> learners)`：构造时规范化排序，校验 voters 非空且与 learners 不相交。
- `CompletionStage<RaftResult<Void>> changeConfiguration(RaftConfiguration target)`：仅限 Leader，调用 `Node.changePeers`。Status 映射：OK → `APPLIED`；EPERM → `NOT_LEADER`；EBUSY → 新增 `BUSY`；其他 → `UNAVAILABLE`；`orTimeout` 到期表示结果未知。之所以新增 `BUSY`，是因为调用方需要区分「稍后按同一目标重试」与「失败」。
- `RaftConfiguration leaderConfiguration()`：仅限 Leader，读取 `listPeers` 和 `listLearners`，非 Leader 时抛 `IllegalStateException`。注释写明：两次读取不是原子快照，变更进行中可能包含未提交配置，只能用于诊断和失败即关闭的前置检查。
- 初始配置按 `RaftConfiguration` 的同一规则排序构造，保证后续同目标调用能命中 `equals` 立即返回 OK（V3）。

保留：`submit`、`confirmLeadership`、`fetchQuorumReadIndex`、`snapshot`、`isLeader`、`localLeaderTerm`、`leaderEndpoint`。

`Task.expectedTerm` 不在本迁移范围，延后处理。

### 2. 观察资格

| 入口 | 现状 | 迁移后 |
|---|---|---|
| `BrokerCluster.findConsistentVoters(ConsensusMembership)` | 业务角色与实际配置逐项一致才返回 voter | 改为 `findObservers()`：返回已提交状态中 `membershipRole=VOTER` 且已绑定成员端点的 Broker；存在 `LEARNER_SYNCING` 或未终态成员变更时仍返回空（保持现有门禁，见 O1）；不再读实际配置 |
| `BrokerCluster.topology(membership)` / `BrokerClusterTopology.membership` | Context 含实际配置 | 删除 membership 字段，Context 为 revision 加已提交拓扑 |
| `probeFailures` | 实际 voter 才探测；探测前后比较 Context | 改用 `findObservers` 和新 Context |
| `reconcileFailures` | 实际 voter 作为分母；ReadIndex 后重验 | 同上；同任期 ReadIndex 证明保留 |
| `probeBroker` / `getHealthReport` | Follower 校验调用方是实际 voter | 改用 `findObservers` |
| `BrokerPeerAdmissionInterceptor.validateObservation` | 调用方与报告方是实际 voter | 改用 `findObservers`；入站前后 revision/epoch 一致检查保留 |
| `validateControl` | 不读实际配置 | 不变 |

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
  | `APPLIED` | 提交 `RAFT_COMMITTED`，后续阶段不变 |
  | `BUSY` / 超时 / `UNAVAILABLE` | 保持 `PENDING`；`BrokerMembershipScheduler` 下一轮按同一 operationId、同一目标重试；没有进行中变更且目标与当前配置相同时，官方立即返回 OK，重试因此收敛 |
  | `NOT_LEADER` | 由新 Leader 的 Scheduler 执行 `resume` |

- **晋升/降级**：从两步变为一次 `changePeers`，voter 与 learner 角色同时变化，并删除「只完成第一步」的恢复分支。`ConfigurationCtx` 是否正确处理这种变化由 V2 验证；不成立时退回为两个持久化目标（target1、target2），每一步仍提交完整目标。
- **Adapter**：删除所有 `membership()` 前置读取。
- **learner 进度检查**：`checkLearnerProgress` 中的 `getConsensusMembership().learners().contains` 改为 `leaderConfiguration().learners().contains`；该方法本来就要求本机是 Leader。
- **失去多数派**：`changePeers` 无法完成，操作保持 `PENDING`，与现状一致（D2）。
- **互斥**：保留 `hasBlockingOperation`，不新增锁。

已知缺口：旧执行者迟到调用 `changePeers(旧目标)` 可能覆盖后续目标。附录 C 已构造并调查该时序；现状的单步调用同样存在此问题，补丁也不解决。本方案让所有外部配置调用只经 `BrokerMembershipAppService` 单一路径，并由 §4 的漂移诊断发现回退；该问题列为残余风险 R1，不在本迁移中解决（D8）。

### 4. Leader 漂移诊断

- 复用 `BrokerMembershipScheduler` 现有扫描周期：Leader 在没有未终态成员操作时读取 `leaderConfiguration()`，与已提交业务状态的期望配置比较。
- 不一致时：输出告警与指标，内容包含期望配置、实际配置、revision 和 leaderTerm；拒绝登记新的成员维护操作并返回明确原因；不影响自动故障接管和路由。
- 不自动修复，即不自动把配置改回业务目标；修复方式见 O2。
- 读取失败（例如本机已不是 Leader）时跳过本轮。

### 5. 依赖与制品

- POM 改回官方坐标；dependency tree 只允许存在一个 `jraft-core`，即官方坐标。
- 删除补丁、recipe 和私有制品的有效入口。私有制品 capture/verify-lock 相关的 Harness 规则按规则生命周期移除并写明理由；保留必要的历史来源说明。
- 版本首选官方 1.4.1，与补丁基线是同一 commit。V1 负责确认 Maven Central 制品与固定 commit 对应，并支持 JDK 21。

## 可靠性与安全

- 多数派：所有业务状态转换和配置变更都经 JRaft 共识；失去多数派时不签发新授权（D2）。
- 隔离：fencing、quarantine 与 Lease 不变（D6）。
- 重试与幂等：成员维护按 operationId 加固定目标重试；自动接管沿用现有 operation 幂等。
- 超时：`changeConfiguration` 超时表示结果未知，不推断失败，也不推断回滚。
- 授权：MTLS 身份与 `validateControl` 不变；观察准入从「实际 voter」改为「已提交观察者」，仍要求认证身份与请求一致。
- 审计：成员维护 operation 记录目标配置；漂移告警记录期望配置与实际配置。
- 资源：不新增线程或调度器。

## 运维与交付

- 升级前置条件：没有未终态成员维护操作；集群整批升级，不支持混跑。新命令编码含 `targetConfiguration`，旧版本无法解码。
- 快照兼容：旧快照中的 operation 没有 `targetConfiguration`。终态操作不需要该字段；若存在未终态成员操作，节点拒绝启动并提示先完成操作或回退版本（V5）。
- 回滚：二进制与依赖必须一起回滚。升级后已执行的 Raft 配置变更不会随回滚撤销，需由运维确认。若升级后已写入新格式快照，回退前需评估旧版本能否读取（V5）。
- 可观测性：新增漂移告警，以及成员维护 BUSY 重试次数指标。
- 配置：不新增配置项，复用 Scheduler 扫描间隔。

## 风险与回滚

- 风险：
  - R1：旧执行者迟到调用，见 §3。
  - R2：观察资格与实际 voter 分离后，外部 CLI 造成的漂移不再自动停止接管，依赖漂移告警和运维处理。
  - R3：若一次 `changePeers` 完成晋升/降级的行为与预期不符，需要退回两步目标（V2）。
  - R4：peers 顺序不一致会导致「同目标」未被识别，产生一条无害的空配置变更日志（V3）。
  - R5：HA 拓扑和 E2E 需要重新取证，现有补丁版证据不可复用。
- 回滚：见「运维与交付」。
- 延后事项：`Task.expectedTerm`、旧执行者 fencing、维护期间允许自动接管（O1）、漂移自动修复（O2）、RTO 测量。

## 验证策略

以下是设计层面的待证项，准确命令在 execution plan 中固定。

- **V1 官方制品**：确认坐标、SHA、对应 commit 与 JDK 21 兼容；dependency tree 只有单一实现，没有 imchat 私有坐标残留。
- **V2 RaftRuntime 真实多节点测试**：learner→voter、voter→learner 都能通过一次 `changePeers` 完成；同目标重复调用立即 `APPLIED`；变更进行中的第二次调用返回 `BUSY`；非 Leader 返回 `NOT_LEADER`；移除当前 Leader 后，新 Leader 的配置正确。
- **V3 顺序规范化**：初始配置与目标按同一规则排序后，重复调用不产生新的配置日志（观察 `lastLogIndex` 不变）。
- **V4 Broker 观察资格**：
  - `findObservers` 规则：角色、端点绑定、未终态门。
  - Context 不再包含 membership。
  - Follower 服务端、Leader 决策、入站准入三处使用同一观察者集合。
  - revision 不一致时拒绝；revision 前进后旧观察者被拒绝。
  - RED 先行：现有依赖 `ConsensusMembership` 的测试先改为新契约并确认失败。
- **V5 成员维护**：首次成功；超时后重试；BUSY 后重试；Leader 切换后 resume；失去多数派时保持 `PENDING`；旧快照含未终态成员操作时拒绝启动。
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
- `docs/design-docs/index.md`：更新本文状态；批准后把 `2026-10-16-jraft-applied-membership-patched-artifact-design.md` 标记为已取代。

执行条件：本设计获批，且当前 Bolt 计划收口、用户自行提交后，才创建 `docs/exec-plans/active/<date>-official-jraft-migration.md`。

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

- **O1 维护进行中是否允许自动接管？** 现状不允许（门禁返回空集合）。首版建议保持现状：维护操作由 Scheduler 自动收敛，不会永久阻塞；代价是维护卡在 BUSY 或超时期间，即使多数派健在，自动接管也会被推迟。需确认是否接受。
- **O2 漂移出现后怎么处理？** 推荐只告警并阻止新维护；另一种是增加显式的「按业务目标修复」运维操作。
- **O3 官方版本选哪个？** 推荐沿用 1.4.1，变化最小；另一种是升级到更新版本。
- **O4 是否接受 R1 作为首版的已知残余风险？**

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
| D9 | 谁负责业务治理，谁负责框架能力 | 业务模块负责故障观察、自动摘流、路由接管和业务状态恢复；通用 `im-raft` 只提供官方公开的通用共识能力；删除私有 JRaft 补丁；Raft 成员调整独立受控，不自动删除 voter | 避免把 Broker 业务语义塞进通用框架，也避免业务层复制 JRaft 内部状态机；自动接管不依赖自动成员删除；整体设计和生产实施仍待批准 |

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
