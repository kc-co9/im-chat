# 共识成员模型边界与聚合一致性判断

> 状态：已批准。用户已批准技术快照与领域成员配置分离，以及 `BrokerCluster.findConsistentVoters` 命名；独立 reviewer `e76a97c8` Approved/findings none，纳入既有 A5/WIP=1 实施，不提交。

## 后续决定：结构化采样上下文与入门文档（用户已批准，独立 Review Approved）

独立 reviewer `05870a5a` Approved/findings none，授权按既有 A5 实施；不构成实现/HA验收。用户批准保留顶层 support.health.BrokerHealthObservationContext，字段改为 ClusterRevision revision、BrokerClusterTopology topology、Set<BrokerId> targets，不再拼接字符串 generation。已有领域 BrokerClusterTopology 由 cluster.topology(membership)创建，构造及访问复制BrokerNode，Map/Set不可变；Context构造校验非空，targets防御复制。Context通过record结构相等比较，Map/Set按内容而非顺序；BrokerCluster已排序的节点List继续保持原语义，不改成Set。Topology不含revision，冷却仍比较Topology/term/leader/coherence；Tracker Context额外比较revision和targets，不统一两套重置条件。

删除仅为generation服务的Topology定制toString及旧文本断言，不新增签名/hash/UUID或兼容generation构造器。所有Context调用点显式传revision、topology、targets，不新增factory/helper，不减少任何实际membership/cluster读取，不移动异步finally释放。Application完成后重验与Tracker锁内重验保留：前者检查最新集群事实，后者阻止检查到写入之间的Tracker切换。结构化比较消除Map/Set文本顺序影响，不宣称提供集群读取与样本写入的全局原子性，也不新增本地递增代次/ABA保护。

验证包括Context相等/hash、targets不可变/null拒绝、仅revision/拓扑/目标变化清空样本、等价Map插入序与成员Set排列不清空，旧Context不能读写新样本、连续失败/UNKNOWN/采样断档/墙钟回拨、完成时最新事实与锁内检查、冷却revision独立及实体副本隔离。保留已知Follower Block和全局门禁缺口，不改Raft/Gossip协议或v2载荷。

文档按零基础读者路径补充Server README、Broker Architecture：先说明消息路由和三Broker故障问题，再解释DDD的实体/值对象/聚合/领域服务/应用服务/Adapter，用实际类串联；区分Raft提交权威决定、Gossip传播运行观察、健康探测构成证据而非直接授权摘流。配Mermaid流程/时序图及图后失败边界解释。README负责入门与导航，Architecture负责详细状态/职责/并发链，不复制两套运行手册。没有真实运行通过的HA不写成已验收，既有Security/Reliability限制不删。

## 后续决定：领域成员集合（用户已批准，独立设计 Review Approved）

本节取代下文领域 ConsensusMembership 的 List、重复拒绝及顺序敏感约定；RaftMembership 仍为原始 List<String> 技术快照。ConsensusMembership 改为 Set<MembershipEndpoint> voters/learners，Set.copyOf 防御复制且拒绝 null 引用/元素；允许空集合。Adapter 的 MapStruct 集合映射合并同角色重复端点，跨角色交叠在领域容器构造时用 AssertUtils.domainPropTrue 明确拒绝，不猜测角色。聚合只核对当前角色、绑定及完整成员集合，不再校验容器内部重复或交叠，也不创建 actualVoters/actualLearners 副本；保持用户要求的单一返回与生命周期谓词归属。

这属于明确行为变化：同角色重复从失败关闭变为归一化，交叠从聚合返回空集合变为构造异常。Adapter/应用已有异常释放与失败关闭路径必须回归，不能继续自动摘流，不新增吞异常、fallback 或平行包装。

当前 Context.generation 拼接 Topology.toString，不能依赖 Set 迭代顺序。在现有私有 Topology 中为字符串表示提供显式、按 endpoint.value 字典序排序的 voter/learner 表示，并保持其余组成字段不变；排序仅用于本机签名表示，不存入领域集合，不减少原成员读取/重验。不新增独立签名组件或为此重新抽取用户已要求展开的 App helper。相同成员不同输入顺序须得到相同上下文、保留采样与冷却；成员增减/角色变化必须失效旧采样和冷却。旧本机 generation 无持久兼容要求，不改 v2 command/snapshot。

验证先写 ConsensusMembership/Transformer 的集合归一化、交叠拒绝、不可变/null/空集合测试，以及 App 顺序变化不重置采样/冷却的用例，先取得类型/行为 RED；同步所有构造 caller 和旧重复/交叠断言，不把旧语义断言静默删除。现有聚合一致性/真实 Adapter/观察/准入测试、固定 v2 fixture 和 HA 编译继续覆盖。实现范围为现有容器、聚合、Transformer、App Topology 与其直接测试/caller；独立 reviewer `2939fb39` Approved/findings none，沿既有 A5/WIP=1 实施，不改 Common/Plugin 技术快照或 JRaft/Follower Block。测试还须覆盖初次读取与异步重验构造异常后不写无效观察、不摘流、probe/round闸门释放且后续调用可继续，以及准入不进入下游。

## 摘要与决定

本设计取代[共享成员端点设计](2026-10-16-shared-membership-endpoint-design.md)中 RaftMembership 使用 typed List、应用直接拆两个列表的决定。共享 MembershipEndpoint 保持 im-common 所有权，不再迁包；技术与领域容器不再合并。

- `im-raft/model/RaftMembership` 恢复 `List<String> voters/learners`，Runtime 保留 PeerId.toString 的字典序与完整 index/priority 表示，不依赖共享端点类型。
- 新增 Broker `domain/cluster/model/ConsensusMembership` record，字段为两个 `List<MembershipEndpoint>`；构造器 List.copyOf 防御复制，拒绝 null 列表/元素，允许空列表，保留顺序、重复和交叠，不在容器中判断成员资格。
- `BrokerRaftAdapter.getConsensusMembership()` 读取一次 Runtime.membership，经现有 BrokerClusterStateTransformer 的 MapStruct 映射转换为 ConsensusMembership。恢复必要的 String 集合映射与现有单项映射，不新建 Transformer 或 wrapper。
- 将 `BrokerFailureObservationService.findObservationVoters(cluster, voters, learners)` 的完整规则迁至 `BrokerCluster.findConsistentVoters(ConsensusMembership membership)`。聚合使用自身 operations、brokers、membershipEndpoints，校验过渡操作、角色、重复、交叠与完整绑定一致性，一致返回 Set<BrokerId>，否则返回空集合。输入通过正式构造器已非 null；不添加未调用的 null 兼容入口。领域服务保留 evaluate 等跨报告故障规则，删除旧方法及其纯迁移后无用依赖。
- 应用、准入、HA 和观察 Topology 使用领域 ConsensusMembership，直接调用聚合，不再依赖 RaftMembership，也不新增应用/领域服务转发。Adapter 原 getRaftMembership 公开入口无独立 caller 后删除。

## 边界与不变量

Plugin 只依赖 JDK/common 通用基础能力，Domain 不依赖 Plugin、Adapter 或 Spring。身份绑定与成员一致性属于 BrokerCluster，而非 Adapter。Adapter 负责获取和转换，不判断观察资格。

Adapter 内成员 add/promote/demote/remove 可直接读取 Runtime 的原始快照，用字符串 `.contains(endpoint.value())`；应用 learner 检查读取 getConsensusMembership 后使用 typed `.contains(endpoint)`。两种模型比较不能混用，必须有真实成员迁移/重复分支测试。

每次原快照读取仍在原位置读取，不为省转换缓存另一份实际成员事实，不减少上下文重验；Topology 等价性保持字符串表示和列表顺序敏感。重复项在转换后必须保留，由聚合失败关闭，不得提前 Set 化或 distinct。

## 范围、兼容与非目标

范围包括 Common 端点引用保持不动，RaftMembership/Runtime/测试恢复字符串，Broker 新领域容器、聚合、领域服务、Adapter/Transformer、应用/准入/配置、所有测试 fixture 和 HA caller；删除 PeerAdmission 仅为旧资格服务而引入且不再使用的依赖及配置参数。

不改 Settings、Runtime String add/remove API、SDK/HTTP、v2 command/snapshot 字段格式；不改先前用户要求展开的观察流程、不新建方法包装。原固定 v2 fixture 继续验证。JRaft Follower 实际配置能力 Block、其他 WIP 和历史门禁缺口不在范围；不升级依赖或弱化规则。

回滚须一起恢复两种容器、聚合规则归属及全部 caller，不能只恢复一个方法。没有新进程状态、授权、网络或资源。非 blank 端点校验在 Adapter 转换边界执行，非法实际端点失败，不提供猜测/默认成员。

## 验证策略

测试先行新增 ConsensusMembershipTest（防御复制/空/null/顺序重复），将原 BrokerFailureObservationVotersTest 原规则断言迁入 BrokerClusterConsistentVotersTest（不 Mock 聚合），先取得缺模型/方法的编译 RED。全套过渡、重复、交叠、未知/缺失端点、角色不符、DISCOVERED/LEARNER_SYNCING 及正确 BrokerId 结果保持；新转换测试证明重复和原字符串不丢失。

顺序聚焦 common/raft 测试、Broker 聚合/Transformer/Adapter/观察/准入测试、Broker reactor、HA test-compile、architecture/behavior；独立代码 Review 后 quick/clean/full。已知缓存容量失败、磁盘不足与工具超时分别归因；补充选择不能冒充完整 reactor。

## 文档与执行

批准后在既有 A5 登记准确文件和 checkbox；同步 Common/Raft/Broker Architecture/README、原两份设计取代关系、相关 Coding/Harness 中 findObservationVoters 所有权事实（不放宽 support.health 依赖），PROGRESS 和设计索引。历史验证不倒改。

## 备选方案与决策记录

| ID | 方案 | 决定与理由 |
|---|---|---|
| D1 | 技术与领域共用一个容器 | 被本轮取代；用户选择显式语义边界，允许 Adapter 内一次转换 |
| D2 | Raft 字符串快照 + Broker 领域 ConsensusMembership | 采用；隔离插件类型，聚合可直接接收完整领域输入 |
| D3 | findConsistentVoters 归 Adapter 或领域服务 | 不采用；规则依赖当前聚合自有状态，归 BrokerCluster；报告 evaluate 保留领域服务 |

## 独立 Review

独立 reviewer `e76a97c8` Approved/findings none；仅批准本设计，不关闭 Follower Block 或旧门禁缺口。Topology 的 record 名称改变会改变本机 generation 字符串，属于新运行时采样重置，不承诺旧 generation 字面兼容；各次读取、重验和 List 等价性必须保留。
