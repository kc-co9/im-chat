# 共享成员端点值对象设计

> 状态：部分已取代。后续用户批准的[共识成员边界设计](2026-10-16-consensus-membership-boundary-design.md)取代本文 typed RaftMembership 与直接拆列表调用的方向：技术快照恢复 String 列表，Broker Adapter 转领域 ConsensusMembership，规则归 BrokerCluster.findConsistentVoters。MembershipEndpoint 归 common、不透明字符串/v2 格式及 List 不去重规则保留。以下批准与迁移描述为原增量事实，不作为新容器模型状态。

> 原状态：已批准。用户已批准将同一端点概念移至公共契约层，由 Raft 与 Broker 复用；独立 reviewer `fd740c07` Approved，无必须修正 finding，进入实施。实施纳入既有 [Adapter 计划 A5](../exec-plans/active/README.md)，不新增 active 任务、不提交 Git。

## 摘要

将 Broker 的 `MembershipEndpoint` 迁至 `im-common` 的 `com.co.kc.imchat.common.domain.cluster.model`，保持名称、不可变 String 值、Serializable、非 blank 校验和值相等语义。`RaftMembership` 的 voters/learners 改为 `List<MembershipEndpoint>`，Runtime 在 JRaft 边界把 `PeerId.toString()` 包装为共享值对象；Broker 直接使用列表，删除无独立调用方的 String 列表转换。

共享的是稳定成员通信端点值，不是 Broker 成员角色、路由端点或 Raft 共识机制。插件仍不依赖 Broker，Broker domain 仍不依赖 plugin。不给 Raft 新建另一套同形端点类型，不保留旧包别名、兼容构造器或测试专用工厂。

## 背景与约束

现有 Broker `MembershipEndpoint` 只有非 blank 校验、String 值和 Serializable，没有 Broker 专属解析规则。Broker 的成员绑定和观察资格规则消费该值；通用 `RaftMembership` 当前保存 String，因此应用、准入层反复调用 `toMembershipEndpoints`。Shared `im-common` 已保存跨模块领域值，并允许 Plugin 和 Domain 共同依赖。

本轮仅统一内存模型类型和所属位置。保持端点字符串原样，不 trim、不拆 host/port、不引入 DNS、地址可达性或 JRaft 格式校验；`host:port`、JRaft 带 index/priority 的表示都作为不透明非 blank 值。排序继续由 Runtime 按 `PeerId.toString()` 的原字典序执行。`RaftMembership` 构造器继续只做不可变防御复制，不排序、不去重，不将 List 改 Set；领域仍须识别重复、交叠及绑定不一致。

## 范围与非目标

- 范围：共享值对象迁移及其全部 import；RaftMembership/Runtime 读快照；Broker 成员 contains 比较、观察和准入直接传列表；所有调用方、fixture、测试和 HA 编译；当前 Architecture/README 与过时设计条款。
- 非目标：不扩张 `RaftNodeSettings`、Runtime add/remove 等现有 String 配置/操作契约；仅在这些真实字符串边界调用 `.value()`。不改 SDK/HTTP、Raft 命令和 snapshot 的字段、值及 v2 版本，不改变持久化格式或添加迁移代码。不重做 BrokerNode 端点字段，不处理 Follower 实际 membership 读取的既有能力 Block，不改 JRaft、依赖版本或其他 WIP。

## 详细设计

1. 唯一端点类型位于 `im-common/domain/cluster/model/MembershipEndpoint.java`，删除 Broker 原文件。文档说明其为共享成员端点值对象，不包含服务身份或成员资格。
2. `RaftRuntime.membership()` 保持分别遍历 voter、learner、转字符串和排序，再映射共享值对象；`RaftMembership` 防御复制输入 List，端点对象本身不可变，旧快照不受后续成员变化影响。列表引用及元素不得为 null，由 `List.copyOf` 保证；允许空列表。
3. Broker domain 的 Intent/operation/聚合 map、Transformer 的真实 String 边界映射及应用输入继续使用同一个共享值对象。纯 `List<String> -> List<MembershipEndpoint>` 方法无调用方时删除，直接传递 `membership.voters()` / `learners()`。
4. `contains(endpoint.value())` 不能照搬：成员比较改为 `contains(endpoint)`；真实 Runtime String API 调用保持 `.value()`。测试相应改为 typed List 和 typed 精确断言，不能只改 import 后让 contains 永远 false。fixture 可保持原 String 状态，再在返回 RaftMembership 时直接映射，不新增测试专用生产 helper。
5. 以迁移前包含成员端点的固定 v2 命令、含成员绑定和 operation 的快照载荷验证解码，核对版本、字段、原始字符串和无 Java 包名；不只依赖新编码/新解码往返。JSON 的真实模型值仍为 `{ "value": "..." }` 或现有 Transformer 展开的 String，必须以命令/快照往返测试核对；不把 RaftMembership 新结构解释为已发布 wire 协议。端点不在 JSON 中保存 Java 包名，源码包迁移不要求当前 JSON 持久化版本变化。Java 原生序列化流会包含类名，本次不承诺旧包的 ObjectInputStream 兼容；仓库已核对的 Raft 持久化使用 JSON，不新增旧类别名。若发现外部已发布的 RaftMembership 序列化路径，须先归因其兼容范围，不能将新 typed 列表直接视为 String wire 兼容。

## 可靠性、安全与交付

不新增状态、网络、线程、资源或授权路径；voter 多数分母、独立身份、稳定窗口和失效关闭规则不变。共享对象更早拒绝空白端点，不隐藏错误或引入 bootstrap fallback。Java API 需要同仓重新编译调用方，不提供混合源码兼容；已批准 v2 JSON 字段保持不变。回滚需一起恢复共享位置、RaftMembership 类型和所有调用方，不能仅恢复旧值对象。

环境当前 readiness 通过，但上一轮 quick 出现临时目录 No space left on device；未授权删除磁盘文件。按窄到广顺序验证，环境失败独立归因，不关闭已有 Follower Block 或历史交付缺口。

## 验证策略

- 测试先行：共享类型的非空/空白拒绝、值相等、String 原样保留和序列化；RaftMembership typed API 编译 RED，防御复制、不可变、顺序与重复保留、null 输入/元素拒绝测试。
- 聚焦：`mvn -q -pl im-common,im-plugin/im-raft -am -Dtest=MembershipEndpointTest,RaftMembershipTest,RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`。
- Broker 回归：`mvn -q -pl im-broker/im-broker-server -am test`；真实 Adapter 成员转换、观察 voter 重复/交叠拒绝、命令/快照往返及领域资格测试不得弱化。HA `mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile` 只证明编译。
- `./scripts/verify.sh architecture`、`behavior`，独立代码 Review 后顺序 `quick`、`clean`、`full`；环境/时限失败照实保留，不用旧报告冒充当前验证。

## 后续文档与执行

批准后在既有 A5 写精确文件清单及 RED/GREEN checkbox。同步根 Architecture 的共享类型所有权、Common README、Raft README、Broker Architecture/Server README，自动观察设计中关于转换的旧决定标记被本设计取代。若发现可复用约定，更新 Coding 与 Harness；不为类型名新增白名单。

## 附录 A：备选方案

- 共享一个值对象（用户选择）：消除同概念包装和重复转换，代价是形成小型稳定共享契约；仅保留通用不变量，成员资格仍归 Broker。
- 保留两侧独立模型并在 Adapter 转换：适合不同上下文存在不同端点规则，但目前没有此事实，保留转换只增加负担。
- Plugin 直接引用 Broker 值对象：违反已有模块依赖方向，不采用。另建 RaftEndpoint 不能消除同形转换，也不采用。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 用户确认先按共享类型方案 | MembershipEndpoint 迁 im-common/domain/cluster/model，RaftMembership 使用 typed List，Broker 直接复用 | 单一身份概念，无 Plugin → Broker 或 Domain → Plugin 依赖 |
| D2 | 端点格式与成员资格 | 仅保持非 blank、不透明 String，不规范化、不排序去重输入 List | 保留 JRaft 表示、排序和重复检测，避免隐藏成员异常 |
| D3 | 范围与兼容 | 不改 String 配置/操作边界、已发布协议、v2 日志/快照版本及 JRaft 能力 | 最小改动，不借共享迁移扩大重构或解除既有 Block |

## 附录 C：独立 Review

独立 reviewer `fd740c07` Approved，无必须修正 finding；采纳空列表表述及固定 v2 成员载荷兼容断言两项建议。用户已批准目标及共享方案，现进入既有 A5 实施；未据此宣称实现或验收完成。
