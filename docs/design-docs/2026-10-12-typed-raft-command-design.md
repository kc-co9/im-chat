# Raft 类型化命令接入设计

> 状态：用户已批准方案，独立设计 Review 第二轮 Accept。本文的 v1 持久格式决定已由 [Broker 自有时间字段契约设计](2026-10-14-broker-time-contract-design.md) 的 v2 契约取代；其余类型化接入与职责边界决定保留，以下正文为原决策记录。

## 摘要

将通用插件的字节提交接口改为 `RaftRuntime<C, R>`，注入 `RaftCommandCodec<C>` 和 `RaftStateHandler<C, R>`。业务调用方直接提交命令，Runtime 在提交时编码，在所有节点应用日志时解码。Broker 继续拥有命令协议和业务状态；通用插件不依赖 Broker。

## 背景（重构前事实）

当前 `BrokerRaftAdapter` 手动编码，状态机自行解码。Broker Codec 编码与解码重复维护九种命令映射。用户确认尚未上线，允许日志和快照版本均重置为 1，不保留旧格式兼容。用户指定 Broker 的 Raft 支持代码归 `support/raft`，并要求按职责分包。

## 目标与非目标

- 目标：类型安全提交，集中编解码接线，消除重复协议类型清单，按职责迁移 Broker Raft 支持类型。
- 非目标：不改变成员变更、ReadIndex、Leader 检查、路由选择、Lease 签发、Bolt 协议或快照业务字段；不移动 consensus/membership 模型；不引入万能 nullable 命令或默认 Codec。

## 重构前事实与约束

- `im-raft` 只依赖通用契约；Broker 是当前唯一生产接入方。插件 Counter 集成测试也是需要迁移的调用方。
- Runtime 保存和复制字节；泛型接口不能替代持久化编码。
- 当前日志版本为 5、快照版本为 6；新实现只接受版本 1。
- Broker `support` 的现有 ArchUnit 规则只允许 exception/security/context/util，现有文档也禁止完整技术能力进入 support。用户指定的 `support.raft` 必须作为明确的技术接入边界记录，并配套依赖检查，不引入按类名的例外。

## 方案概览

```text
BrokerRaftAdapter 具名提交入口
  -> private submitCommand(command)
  -> RaftRuntime<C, R>.submit(command)
  -> codec.encode(command)
  -> Raft 字节日志复制和提交
  -> codec.decode(payload)（所有副本与重启重放共用）
  -> handler.apply(command)
  -> BrokerCluster 领域行为
```

## 详细设计

### 通用插件契约

新增 `im-plugin/im-raft/src/main/java/com/co/kc/imchat/plugin/raft/spi/RaftCommandCodec.java`，提供 `byte[] encode(C command)` 和 `C decode(byte[] payload)`。

`RaftStateHandler<C, R>` 的 `apply` 接收 C；snapshot/restore 保留字节接口。`RaftRuntime<C, R>` 构造器接收 settings、codec、handler，不保留无调用方的旧构造器或字节 submit 重载。

非 Leader 提交仍先返回 NOT_LEADER，不执行编码。Leader 在 node.apply 前完成编码并复制字节，避免 Codec 复用缓冲区改变在途日志。所有节点统一从日志字节解码，不能让 Leader 直接使用原始对象绕过 Codec。

Codec 必须无状态或自行保证线程安全：并发 submit 的 encode 以及日志应用线程的 decode 可以同时发生；同一实例可在多个 Runtime 间共享。encode 返回非空、归本次调用独占的字节数组，不支持无同步共享 scratch buffer。Runtime 仍复制编码结果，避免提交后的外部修改影响在途日志。decode 输入是本条日志的独立字节副本，返回非空命令；确定性及不访问网络、墙上时间和随机源为业务 Codec 契约。

非 Leader 在编码前返回 NOT_LEADER。编码异常同步抛出，不追加日志、不调用 Handler、不将健康 Runtime 标记为状态机错误。decode 与 handler.apply 必须位于同一个 onApply 异常捕获边界内；解码异常不调用 Handler，解码/应用异常完成技术失败、不报告 APPLIED、不继续当前批次，并将节点标记为状态机错误。错误节点不再提供有效 quorum 读取证明（confirmLeadership 和 fetchQuorumReadIndex 均拒绝），防止把残缺业务状态作为可信读取。

Handler 部分修改内存后抛错不承诺自动回滚；运行实例应关闭，从有效快照及日志重建，不能跳过损坏或旧版本日志。快照恢复失败不安装半恢复业务状态；运行层维持加载失败出口。Codec 和 Handler 在创建运行实例之前校验非空。快照与身份文件所有权不变。

### Broker 类型与目录

- `support/raft/codec/BrokerClusterCommandCodec.java`：实现 RaftCommandCodec，提供无状态 INSTANCE；一份稳定协议名称到 Class 注册表驱动编码与解码。不能使用 Java 全限定类名作为日志类型名，也不能依据日志输入任意加载 Class。
- `support/raft/model/BrokerClusterSnapshot.java`：保持快照字段及构造校验，版本改为 1。
- `support/raft/statemachine/RaftBrokerClusterStateMachine.java`：实现类型化 Handler，移除字节 apply；保留领域重放、快照和 Lease 共用同步边界。
- `adapter/cluster/BrokerRaftAdapter.java`：仍拥有唯一 Runtime 和生命周期；注入 Broker Codec，直接 submit(command)。

旧 `adapter/cluster/raft` 生产类型删除；引用迁移覆盖生产、测试、配置、脚本及当前文档。测试按被测职责迁入对应包；Adapter 集成测试仍归 adapter/cluster。注册表完整性通过测试比对 sealed permits 清单，不增加生产反射扫描或动态发现。

## 可靠性与安全

保留请求幂等、超时可能已提交、ReadIndex 任期保护和异常失败关闭。Lease 资格与签发序列仍在状态机同一锁内处理，不能因泛型接线迁移而拆锁。稳定类型白名单、未知类型拒绝、未知版本拒绝与空载荷校验仍保留。

## 运维与交付

旧 v5 日志与 v6 快照不再可恢复。开发者须停止节点后使用新的或经确认清理的本地 Raft 数据目录；实现任务不得自动删除用户数据目录。无需混合版本滚动升级方案，因为用户确认尚未上线。版本重置是有意的不兼容改变，文档不得继续宣称持久化版本不变。

## 风险与回滚

- 风险：类型参数迁移遗漏、Leader 绕过解码、编码字节别名、旧格式误接受、Lease 锁边界改变。
- 回滚：恢复整个 SPI 与 Broker 接入变更，并恢复与旧格式匹配的数据目录；不能混用新旧日志。
- 延后：通用快照泛型、自动类型发现、多格式兼容与新包层级扩展均不实施。

## 验证策略

- 先添加类型化 Runtime 失败测试：Codec 使用可观察的规范化转换，证明 Leader、Follower、learner 均应用解码后的命令；保留三节点换届、learner 复制与移除。
- 快照尾日志场景：提交 A → snapshot → 提交 B → 关闭 → 重启，断言恢复 A+B，再提交 C；不只验证重启后新提交。
- 编码失败同步抛出且业务状态不变，随后合法提交成功；非 Leader 不调用 Codec。通过可控 latch 在 node.apply 后、日志解码前修改已返回编码数组，断言实际日志/副本应用仍为原命令。并发 Codec 测试采用独占数组，不宣称 Runtime 的 clone 能保护不符合契约的共享缓冲区竞争。
- 解码异常不调用 Handler；Handler 异常不报告 APPLIED、不继续后续命令，两个 quorum 读取入口均拒绝错误状态。Handler 部分内存修改后抛错只证明失败隔离，不声称回滚。
- Broker 测试：九种 sealed 命令映射完整、逐个固定协议名、无重复 Class 映射、v1 envelope；未知类型用合法 v1 envelope 并断言类型错误原因，旧 v5 日志拒绝。v1 快照往返、旧 v6/损坏快照拒绝、恢复失败不替换原聚合、恢复身份与 Lease 原子性。
- 聚焦：`mvn -q -pl im-plugin/im-raft -am -Dtest=RaftRuntimeTest -Dsurefire.failIfNoSpecifiedTests=false test`；Broker Codec/Snapshot/Lease/Adapter 聚焦测试。
- 模块：`mvn -q -pl im-broker/im-broker-server -am test`。
- 门禁：`./scripts/verify.sh architecture`、`quick`、`clean`、`full`；Runtime 日志传输边界改变后运行 `behavior`、`e2e`。

## 后续文档与执行

同步 im-raft README、Broker Architecture/README、CODING_GUIDE.md、MODULE_STRUCTURE_GUIDE.md 和 Harness 矩阵。这是 Broker 专属的有意例外：support.raft 只承载日志 Codec、快照载体和状态机回调适配，取代这些类型必须留在 adapter.cluster.raft 的旧约束，不推广为通用 support.<capability> 许可。

ArchUnit 只允许 support.raft.codec、support.raft.model、support.raft.statemachine 三个精确职责包（不允许任意后代包），其他 support 原规则不变。support.raft 禁止依赖 application、interfaces、infrastructure 配置/生命周期、具体网络客户端和 RaftRuntime；允许领域、命令、结果、转换器、通用工具及 Raft SPI。领域不得反向依赖 support.raft。使用正反 fixture 证明合法状态机允许、未批准 support 能力、RPC 和生命周期所有权越界仍失败；业务编排是否通过 Transformer 间接渗入保持 Review-only。

以下保留设计阶段的实施前置要求；独立设计 Review 已 Accept，当前增量已实施，A5 仍 active，最终验收状态见活跃计划。

通过独立设计 Review 后，纳入现有 Broker Adapter 职责重划计划 A5 增量，保持 WIP=1；同步计划 Goal、Sprint Contract 和不变约束，将日志/快照版本不变修订为有意重置 v1、字段及业务语义不变。仅移动直接测试 Codec/Snapshot/StateMachine 的测试；应用测试和 Adapter 测试保留所属边界。当前不创建实施清单或生产代码，避免越过仓库设计 Review 前置条件。

## 附录 A：备选方案与行业依据

1. 保持字节接口，只合并 Codec 映射：迁移成本低，但业务调用方仍手动编码，未解决接线分散。
2. 类型化 Runtime 加可注入 Codec（批准）：提供类型安全，保留业务协议所有权；代价是公共 SPI 及全部调用方同步迁移。
3. 在插件定义 Broker 万能命令：违反通用模块依赖边界，破坏具名命令约束，不采用。

项目运行依赖 SOFAJRaft 1.4.1；此次不改变底层 Task 字节载荷和 StateMachine 回调协议。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 用户批准推荐优化 | Runtime 注入 Codec，Handler 接收具名命令 | 通用层统一接线，Broker 仍拥有协议 |
| D2 | 尚未上线 | 日志与快照均重置为 v1 | 拒绝旧格式，需要新的开发数据目录 |
| D3 | 用户指定目录并要求分包 | support.raft 下按 codec/model/statemachine 分包 | 明确技术支持职责，需同步原架构约束 |
| D4 | 现有包保持 | consensus/membership 模型不迁移 | 排除无关模型整理 |

## 附录 C：独立 Review

第一轮独立只读 Reviewer 结论：Revise，要求补齐并发所有权、失败边界、support.raft 窄例外及可反驳错误实现的验收；已逐项修订。第二轮独立 Reviewer 结论：Accept（设计接受，不是实现验收）。实施关注 ReadIndex 入口与异步完成时错误状态检查、失败后不可可信的内存状态、精确包规则正反 fixture。只读 Review 未运行构建或测试。
