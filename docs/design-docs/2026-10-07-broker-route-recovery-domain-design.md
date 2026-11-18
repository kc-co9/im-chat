# Broker 路由恢复领域行为设计

> 状态：用户已确认职责划分，实施中；独立 AI Review 未执行。用户要求不使用子代理，T13 不因此收口。

## 摘要

recoverRoutes 保留异步提交编排，将恢复证据生成、降级资格、降级原因及终态选择移入现有聚合。
DrainProgress 汇总自己的检查点，BrokerClusterOperation 表达恢复生命周期，BrokerCluster 在日志应用时更新操作。
不新增领域服务或操作上下文，保留“保存证据 → 验证完成意图 → 完成”的中断恢复边界；正常证据提交
可以由聚合直接进入 VERIFYING，降级仍单独提交验证意图。

## 背景与目标

当前应用服务计算已确认数量、缺失 Gateway 和超时降级，并通过通用阶段命令指定 VERIFYING/SUCCEEDED/DEGRADED。
通用阶段转换不足以保证降级资格。本次使领域规则在实际应用日志时执行，应用层只作等待判断、提交和响应转换。
Raft 算法、排空 cleanup、公共 SDK、成员变更及终态响应枚举不在改动范围内。

## 详细设计

- DrainProgress 提供 summarizeRebuild 和 missingGatewayIds；返回领域证据与 GatewayId，已有证据不重算覆盖。
- operation 根据自身阶段、ACK、创建时间及明确传入的恢复等待时长判断等待，验证恢复时检查证据、期限并生成降级原因。
- BrokerCluster.verifyRouteRecovery 核对操作身份并委托实体，根据最新操作登记正常或降级 VERIFYING。
- BrokerCluster.completeRouteRecovery 按已持久化 error 选择终态，正常完成重验 readyToComplete，重复完成幂等返回。
- 新增 VerifyRouteRecoveryCommand、CompleteRouteRecoveryCommand；验证命令记录恢复等待时长和调用时间，所有节点使用日志数据计算期限。
- Adapter 接收领域操作、Duration 和 Instant，通过 Transformer 生成命令；应用层不再构造降级原因或选择两种完成提交。
- FAIL_BROKER 的 VERIFYING/SUCCEEDED/DEGRADED 不再允许由通用 Advance 命令进入；前置授权和重建阶段不变。

## 可靠性与安全

领域行为不读取 Properties、Clock 或本机墙上时间，日志内的策略时长与时间保证确定性重放。
VERIFYING 重试不重算降级意图，完成读取当前聚合事实；拒绝与异常不继续后续提交。
故障证据提交必须匹配日志应用时的检查点；汇总形成后只允许相同的重复 ACK，防止过期证据或迟到确认污染恢复事实。
范围内验证仍必须包含未超时拒绝降级、无证据拒绝、终态幂等、正常及降级重启恢复。

## 运维、风险与回滚

通用 Advance 的语义约束改变，命令编码格式从 v4 升为 v5，明确拒绝旧日志；快照字段不变，仍为 v6。
沿用同 revision 全组件发布约束，不增加字段别名或旧命令兼容方法。此变更不能直接用于需要读取旧日志的滚动升级。
回滚须恢复配套代码及可读取的同版本数据；不在本任务中删除已有日志或快照。

## 验证策略与后续文档

聚焦 Domain、Codec、StateMachine/Adapter 和恢复应用测试，验证命令 round-trip 与旧版本拒绝。
README/Architecture、Harness 矩阵及 PROGRESS 同步，实施步骤归现有
[T13 计划](../exec-plans/completed/2026-09-20-broker-routing-consensus.md)。先处理测试编译阻断再跑门禁。

## 备选方案与决策记录

| 方案 | 取舍 |
|---|---|
| 聚合行为与具名日志命令（采用） | 守住实际日志应用边界，应用编排变短；需更新命令版本和测试 |
| 仅移动应用层计算到一个领域服务 | 核心数据仍属同一聚合，服务成为转发；通用阶段命令仍可绕过资格 |
| 仅添加实体谓词，保留通用阶段提交 | 改动少，但预检查无法保护真正提交时的聚合状态 |

用户已确认：证据归进度对象、资格归 operation、状态变更归聚合、不新增领域服务。
后续排空 Review 同步采用此划分：operation 共用 ACK 等待及操作期限判断，needsRouteCleanup
表达类型/阶段/ACK/证据组合资格；advanceDrain 保留 RPC、超时失败策略与共识编排，完成响应由既有 Transformer 转换。
本次排空整理不增加日志命令或持久化字段，完成资格仍由 BrokerCluster.completeDrain 在日志应用时核验。
实现补充：超时策略与时间作为日志数据，不增加持久化字段；证据、验证意图与终态的先后关系保持不变。

## 独立 Review

未执行独立子代理审查，按用户不使用子代理的要求保留此缺口；不生成 Approved 质量结论。
