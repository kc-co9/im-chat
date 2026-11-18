# Ops 成员写请求的业务目标与接收节点解耦设计

> 状态：已批准。2026-11-02 用户明确批准解耦路由，独立设计 Reviewer `ace3b628dc0670655` Approved、必修 findings none。仅修复首次管理 HTTP 接收节点选择，不授权绕过鉴权、伪造发现身份或新增未加入 Broker 启动模式；实施属于现有联合计划 T11。

## 摘要

成员写请求的 `brokerId` 是业务目标，不要求目标已有运行中的管理 HTTP 服务。Ops 从可信发现结果中选择第一个受支持的真实 Broker 管理端点接收原命令，Broker 仍负责目标状态、epoch、成员配置及 operation 的共识校验。不得用目标身份为其他实例创建发现别名。

## 背景

`BrokerClusterOperationAdapter.operate` 当前按命令目标 brokerId 精确查找发现条目。真实 CLI 添加离线 DISCOVERED 节点的旅程因此在进入 Broker 前失败。`.harness/t11/cli-submit-query-retry-attempt2/` 实际记录同 operationId 的 UNKNOWN 与 Ops IllegalArgumentException；具体缺失查找的归因来自确定性 fixture 输入与源码，不是已捕获的异常消息或完整运行栈。

## 目标与非目标

- 目标：允许已有真实管理接收节点处理针对离线 DISCOVERED 目标的成员命令，保持原身份和完整业务参数。
- 非目标：不新增 Broker 未加入启动模式、发现代理、跨节点自动故障重试、Leader 选举缓存、第三项安全排除或新公开 API。
- 非目标：本切片不解决 Raft PeerId 到管理 HTTP origin 的映射，不将现有裸 Raft 地址变为可信地址；实际 Follower 提示契约仍需单独修复和真实验证，不能由本设计宣称已闭合 T11。

## 已知事实与约束

- Ops 拥有发现与管理 HTTP 出站适配；Broker 拥有权威成员状态与幂等操作。
- 正式发现适配拥有管理 HTTP origin 的解析与校验；`BrokerManagementEndpoint.isSupported` 仅表示已提供 baseUri，不证明节点存活。端点来源必须继续是正式 BrokerDiscovery，不从命令、CLI 或任意提示拼接地址。
- 当前 Broker 与 Raft typed 启动约束要求本机属于初始成员，因此不能仅为本旅程启动一个真实未加入目标并假定启动合同成立。
- 原单次可信 Leader 跟随只接受发现中精确匹配的 HTTP origin。底层 Raft 提示当前可能是 PeerId；该差异是后续风险，不是本轮已触发的运行根因。

## 方案概览与详细设计

`CLI 原命令 → Ops 权限／审计 → 可信发现首个受支持接收节点 → Broker 成员 API → 原领域目标`

1. 对每次显式写请求获取一次发现结果，保持发现顺序，跳过不受支持的管理端点，选第一个受支持端点。不按业务目标过滤，不新增排序或 Leader 缓存。
2. 没有受支持端点时，在任何出站写入前失败。发现失败同样传播失败，不自行推断未提交。
3. 使用原 command 和原 scope 调正式出站客户端；`brokerId`、operationId、expectedEpoch、membershipEndpoint、reason 均不变，不用接收节点身份覆盖业务目标。
4. 首次传输失败、异常、超时或业务拒绝后，不尝试第二个普通发现节点。结果不确定仍遵守既有 UNKNOWN 和显式原记录恢复规则。
5. 仅具名 NOT_LEADER 走原应用服务至多一次可信 Leader 跟随；不接受未发现地址、非 HTTP origin 或继续跳转，不放宽安全检查。原命令在跟随中不变。

## 可靠性与安全

鉴权、Introspection、scope／permission、Broker 服务凭证和集中审计保持正式实现。接收节点与目标解耦不授予额外业务权限。选择端点不创建副作用事实；Broker 共识仍拥有提交点。首次写无自动多节点重试，避免把网络失败解释成未执行。无新增后台任务、资源池或持久化状态。

## 运维与交付

无新配置、数据库迁移或 CLI 参数变化。部署仍需在可信发现中发布真实 Broker 管理端点。旧行为要求目标在线，新行为允许由其他真实集群管理节点接收；该变化限于生产 Ops 适配层，需模块回归及真实旅程验收。

## 风险与回滚

- 首个受支持节点可能不可达或不是 Leader；不承诺首次成功或高可用，保留失败和显式恢复语义。
- 发现顺序决定首接收者，不代表权威 Leader。Follower 提示映射问题保持公开，不用单 voter 成功证明多节点跳转正确。
- 回滚恢复目标匹配逻辑会重新引入离线目标不可提交问题；不能把恢复旧限制称为成功验收。

## 验证策略

- 既有 Ops adapter 公共接缝新增目标不在发现中的测试：发现首项不受支持、后项真实受支持，断言原 command／scope 发送到后项；修复前实际 RED，修复后 GREEN。
- 无受支持端点零写调用；首个接收节点写失败不得向第二节点发送；沿用具名 NOT_LEADER 可信单跳及未发现地址拒绝测试。
- 原真实 CLI add-learner／queryOperation／原文件 retry 旅程重跑，不更改严格 APPLIED／DUPLICATE 或 LEARNER_SYNCING 边界。
- 按窄层向模块、架构及最终 clean／full 推进；外部 JaCoCo 问题单独保留，不由路由修复关闭。

## 后续文档与执行

独立 Review 无 Blocker／Major 后，纳入联合计划 T11：`docs/exec-plans/completed/2026-10-30-official-jraft-and-ops-cli.md`。同步 Ops README 的当前接收节点语义及 Harness Guide／Matrix 的跨服务管理写接收节点与业务目标分离规则；精确测试作为自动传感器。根拓扑和数据所有权不变，不新增 Architecture 结构。

## 附录 A：备选方案

- 采用：任一可信受支持运行节点接收原目标命令。已有 Broker API 能处理不同目标，所需变更最小，无需目标先在线。
- 不采用：首次必须直达目标。需先设计未加入启动与管理发现发布，无法在当前启动合同下仅靠 fixture 实现，且不解决 Leader HTTP 映射。
- 拒绝：将 learner 发现身份别名映射到 voter 地址。身份与实例事实不符，不能作为真实旅程证据。

## 附录 B：决策记录

| ID | 来源／问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 2026-11-02 用户明确选择批准解耦路由 | 首次请求可由可信发现中的运行且受支持 Broker 接收，业务目标及原请求身份不变 | 离线目标不应承担首次管理接收节点职责，不新增假发现或启动模式 |
| D2 | 失败与重复副作用风险 | 首次接收节点失败不自动换普通节点；仅保留至多一次可信 Leader 跟随 | 保持 UNKNOWN 与原记录显式恢复，不引入额外写重试链 |

## 附录 C：独立 Review

2026-11-02 独立 Reviewer `ace3b628dc0670655`：Approved，必修 findings none。核对原 CLI 设计、当前 Adapter 与 AppService 后，确认首接收者选择与 Follower hint 映射可独立分片；首次选择的一次发现不取消 Leader 跟随时的可信发现重验，isSupported 不证明存活。无候选零写、普通失败不换节点、原命令不变及可信单跳必须取得实际验证。该结论不代表实现／运行验收，也不关闭 HTTP Leader hint 缺口。
