# Learner 同步确认与后台恢复职责分离设计

> 状态：已批准。用户已批准 `confirmLearner` 只提交新确认、历史未完成操作由现有定时协调器恢复，不新增 HTTP 恢复接口，并批准 CLI 确认端点保存与传递修复。第二轮独立设计 Review Approved、findings none；首轮 Revise 保留。实施已纳入[已归档 T2 计划](../exec-plans/completed/2026-11-09-broker-membership-flow-readability.md)；具体执行器落位、LearnerConfirmationState 内联及 SDK Result 迁移已实施，完整验收与状态回填已取得独立 Review；计划已归档，历史验收指纹与残余风险见计划。文件名保留早期讨论名称，不表示存在对外显式恢复接口。

## 摘要

`confirmLearner` 对新操作执行同步取证、Begin、Commit 和终态完成；对历史操作仅校验请求身份并返回已有事实，不推进未完成操作。现有 `BrokerMembershipScheduler` 调用 `resume` 负责后台恢复，learner 恢复不再回调 `confirmLearner`。保留在途共享、4096 共享永久预算、唯一 worker、generation 与 Leader term 约束。

## 背景与已知事实

当前 `confirmLearner` 同时承担新操作取证、成功重放、PENDING 恢复和 RAFT_COMMITTED 收尾。当前 `resume` 对 learner 的 PENDING 与 RAFT_COMMITTED 均重新调用 `confirmLearner`。

`BrokerMembershipScheduler.reconcile` 已在 Leader 上选择 RAFT_COMMITTED 或超过 recoveryDelay 的 PENDING 操作并调用 `resume`；COMPLETE_LEARNER_SYNC 在其类型范围内。Broker 没有独立恢复 HTTP 接口。`OpsCliClient.retry` 读取原记录并重发原操作，不直接调用 `resume`。

CLI 已明确 APPLIED/DUPLICATE 只确认提交、不代表 operation 完成，要求使用 `operation show` 查询。`CompleteLearnerSyncResult` 没有操作阶段字段，现有查询负责展示阶段。因此历史未完成请求拟复用 DUPLICATE 表示「相同请求已登记」，不新增响应枚举或公共路径。

独立 Review 发现既有端到端缺口：OperationRequestFile 禁止非 add-learner 操作保存 membershipEndpoint，OpsCliClient 也只为 add-learner 发送该字段，OpsCliController 为确认请求构造空端点；但 Broker HTTP 转换要求确认端点非空，且摘要包含端点。因此合法 CLI 确认及 retry 当前无法抵达应用服务。这不是本设计引入的回归，但阻断本设计对完整客户端契约的验收；不得宣称仅改文案即可连通。用户已批准将原请求端点保存与传递纳入本次范围，具体契约见下节；不得用 retry 时读取当前绑定、刷新 epoch 或删除摘要校验绕过。

## 目标与非目标

- 目标：确认入口仅保留新操作主线；历史未完成操作由后台恢复推进。
- 目标：保留不同内容同 ID 拒绝、相同在途请求共享、历史成功返回已有事实。
- 非目标：不新增恢复 HTTP 接口、通用编排框架、线程或外部依赖。
- 非目标：不修改持久化格式、同步证据标准、认证授权、拓扑所有权、恢复延迟或永久预算上限。

## 方案概览

```text
confirmLearner
  → 输入转换 → 在途共享 → 执行准入 → worker 读取 committed operation
      ├─ 已有操作 → 校验原请求身份 → 返回历史事实，不推进
      └─ 新操作 → 校验 → 取证 → 复验 → Begin
          ├─ 共识侧发现已登记相同操作 → 返回 DUPLICATE，不接管恢复
          └─ 本次新登记 → Commit → 终态完成

BrokerMembershipScheduler → resume(operationId)
  → learner 专属恢复路径 → 在途共享与执行准入 → worker 重新读取事实
      ├─ PENDING → 沿用原操作 Begin 幂等重读 → Commit → 终态完成
      ├─ RAFT_COMMITTED → 只补终态
      └─ 终态 → 返回已有事实
```

## 详细设计

### 历史身份与响应

确认入口必须先验证 operation 类型、目标与摘要，沿用领域重复意图校验，不能仅凭 ID 返回成功。历史请求不再检查新操作的当前 epoch 和端点，但请求摘要必须仍代表原语义。

- SUCCEEDED：DUPLICATE，返回原 acceptedEpoch/targetEpoch，不重复证据、Begin、Commit。
- PENDING、RAFT_COMMITTED：DUPLICATE，表示请求已登记，不表示业务完成；message 明示后台恢复与按 operationId 查询，不能指示调用不存在的恢复 API。
- 其他终态：沿用现有领域拒绝或终态映射，不将失败报告为成功。
- 同 ID 不同内容或类型：沿用冲突/拒绝映射，不写入。

不依赖 message 机器分支；客户端仍按 DUPLICATE 为提交已确认，再通过查询确定 operation 阶段。原记录重发仍可用于确认登记，不再触发历史推进。Ops/CLI 文案及 README 不得把重发称为调用后台恢复。

### CLI 确认端点契约

用户批准修复既有链路缺口，范围限定 add-learner 与 complete-learner-sync 的端点表达，不顺带修改 promote/demote/remove 的端点契约。

- `OperationRequestFile.Request`：add-learner 与 complete-learner-sync 均要求非空白 membershipEndpoint，其他 action 继续禁止该字段。确认请求允许保留现有可选 reason，不把其纳入 Broker 命令摘要；add-learner 的 reason 限制不变。
- `OpsCli`：确认与加入均要求显式 `--membership-endpoint`。确认保留可选 `--reason`，解析器及帮助必须覆盖端点与原因同时存在，不能因复用加入分支静默删除原 reason 功能；若采用固定参数顺序，在帮助中明确该顺序并给出组合正反例。
- `OpsCliClient.submitRequest`：对这两类 action 原样发送记录中的 membershipEndpoint；确认请求继续发送可选 reason。retry 仍通过同一发送路径，绝不查询当前绑定替换端点。
- `OpsCliController`：为确认定义独立 `CompleteLearnerSyncRequest(operationId, expectedEpoch, membershipEndpoint, reason)`，验证 epoch 非空与端点非空白，传给既有应用命令。不得直接复用名为 AddLearnerRequest 的请求类型，也不向其他成员操作的通用请求添加本次无需的字段。
- 既有 Ops 应用、HttpBrokerManagementClient、Broker HTTP 转换及摘要保持原样传递；使用实际测试确认字段保真，不凭类型存在推断链路正确。
- 记录文件字段结构不新增版本，不为原先无端点的确认记录补默认值。此类旧记录拒绝读取/重试并给出缺失端点的明确错误，不自动补写或创建替代 ID。若调用方已有不确定提交，保留原记录并查询原 ID，不把读取失败解释成原操作未登记。说明这是既有无效确认记录，不宣称可无损恢复缺失的原始端点。
- 不新增恢复 HTTP 路径、权限或自动重试。查询/后台恢复语义按正文执行。

### 新操作与并发登记

保留 expectedEpoch、绑定端点、领域资格、quorum ReadIndex 与目标应用位置证据、等待后的 committed revision/epoch/endpoint 复验及成员准入。保留每个必要异步阶段的任期与 generation 检查。

读取时不存在操作不代表 Begin 时不存在。共识侧仍执行幂等/CAS；若 Begin 返回 DUPLICATE，即使携带 PENDING/RAFT_COMMITTED 操作，也仅返回登记事实，不继续 Commit 或补终态。只有本次新接受的操作沿正常链继续。若 Begin 返回不可用等非接受状态，不推断本次未登记，仍保守返回未知，由查询及后台恢复处理。

### 后台恢复的职责与组织

`resume` 的 learner 分支进入独立恢复实现，不回调确认入口。不增加通用协作类；若提取私有方法，该方法承载完整恢复用例，而非两行包装。其他成员类型保持现状。

恢复使用 committed operation 重建原身份，不接受外部重构的 epoch/端点。保留既有缺失端点的失败关闭行为。在 worker 再读最新操作，避免 scheduler 快照过期：PENDING 沿用登记操作执行幂等 Begin、Commit、终态；RAFT_COMMITTED 仅补终态；SUCCEEDED 返回 DUPLICATE；其他终态按原模型处理。恢复不重新取证，不创建新 ID 或新 operation。

恢复与确认共用 `learnerConfirmationAttempts`，按同一原请求身份协调：已有执行者时共享结果，内容冲突时拒绝。恢复中的确认调用共享现有结果；「确认不推进历史」指确认自身不启动恢复，不阻止共享后台已在执行的恢复结果。后台选择操作与登记在途之间的竞态，通过 worker 内重读及共识 CAS 收敛。

### 预算与生命周期

保持现有预留时机：只有在途表胜者预留一次 learner 预算，重复共享不消耗。确认历史状态读取与恢复尝试仍各自消耗一次既有永久预算，不新增免费旁路或回收额度；容量耗尽仍不可继续。本次不解决永久预算耗尽问题。

确认与恢复均使用绑定首次 generation 的执行器，失效回调只完成本轮结果，不读 Node 或 committed 重放事实。先精确移除再完成 Future；不持锁通知回调。fault/close 既有可能未完成边界不在本次扩大或修复。正常出口异常映射保守，不将未知提交当成失败回滚。

## 可靠性与安全

响应丢失后保留原 ID 查询，重发只确认登记；已经登记的未完成操作由现有 Leader 定时协调器自动恢复。未登记的请求重发仍按新操作正常取证。角色切换后由新 Leader 恢复，旧代际不得继续副作用或发布成功。不承诺 exactly-once 或预算耗尽后的自动完成。

保持现有管理链认证、scope、授权、审计和操作查询边界，不增加外部控制能力。PENDING 仍按 recoveryDelay 等待，RAFT_COMMITTED 沿用当前调度；共享结果不得让不同原请求身份观察为相同操作成功。

## 运维与交付

无新配置、外部接口或存储迁移。行为变化为原请求重发不再主动推进历史操作，需要明确发布时 Broker 行为变化；客户端继续支持原有状态集合和查询。修订 Broker、Ops/CLI 相关说明，必要时调整 CLI 提示文案，使查询与后台恢复的职责准确。客户端语义回归及 E2E 验证是实施前提，不仅依赖服务端测试。

## 风险与回滚

主要风险是重复状态误读、Begin 竞态意外接管恢复、两个入口并发提交、恢复无 worker 门禁和预算意外变化。回滚仅逆向此次精确差异，不覆盖用户修改；无持久格式迁移，回滚后原请求重发再次具备自动推进能力。

## 验证策略

- RED/GREEN：历史 PENDING/RAFT_COMMITTED 的确认返回 DUPLICATE、原 epoch、无取证/Begin/Commit/终态副作用；成功重放及冲突保持原义。
- 新操作：正常取证与提交、证据不足、epoch/端点/revision 改变、Begin 返回 DUPLICATE 不继续推进。
- 恢复：scheduler 实际触发 learner resume，PENDING/RAFT_COMMITTED 分别推进正确阶段；恢复不调用确认入口、不重复取证，worker 重读终态不重复提交。
- 并发与故障：确认/恢复共享、不同内容拒绝、并发 Begin、延迟共识回调、换届、同 term 重启、fault/close 与 4096 预算。
- HTTP/Ops/CLI：DUPLICATE 只确认提交；query 显示阶段；原记录 retry 保持同 ID/epoch/端点，历史未完成最终可由 scheduler 完成。
- CLI 端点 RED/GREEN：确认记录读写保真，缺失/空白端点拒绝，确认带可选 reason 的解析和发送，其他 action 仍拒绝端点；retry 重放原端点且无绑定查询。旧无端点确认记录失败关闭、原 ID 保留。
- Ops Controller 使用真实 HTTP 请求体验证确认端点与 reason 进入应用命令、缺失端点拒绝；客户端 E2E 必须走 CLI 请求记录/发送 → Ops → Broker 实际确认 → 原记录 retry → query → scheduler 收尾，不仅 mock 一个 DUPLICATE。
- 定向 Maven、窄架构、文案/漂移检查后执行 clean/full 与独立实现 Review；当前 clean 的既有计划状态阻断需如实处理，不因本设计消失。

## 在途不共享增量（用户批准，独立设计第二轮 Approved）

本节取代正文及 D2 中 learner 在途共享响应的决定，不改变其他成员操作或 Permit 的共享契约。确认与后台恢复共用原子在途互斥：同 ID 不同内容仍 REJECTED；同 ID 同内容立即返回 IN_PROGRESS，不等待执行者结果、不再次取证/提交/预留预算。首个执行者及 generation 失效仍用内部完成信号协调，故不因停止对外共享而删除完成桥梁。取消任意调用方派生 Future 不取消执行、不提前移除记录或返还预算；正常结束、异常映射及可执行的换代失效路径精确移除，旧回调不得删除新 attempt。fault/close 既有可能 pending 的边界保留，不新增必然完成保证。精确移除先于通知不变；完成后按 committed operation 判断历史身份与阶段。

IN_PROGRESS 仅证明本进程有相同请求在途，不证明 Begin 已提交，不视为 accepted 或业务终态。acceptedEpoch/targetEpoch 在 wire 中允许 null 或按现有 JSON 空值策略省略，绝不用 0 或请求 epoch 替代；将 CompleteLearnerSyncResult、Ops 的 BrokerOperationPayload、BrokerOperationDTO、OpsCliSubmissionResponse 的 acceptedEpoch 改为 Long，现有生成 Transformer 原样传递，其他状态 epoch 语义不变。Broker enum 新增该值，HTTP 以字符串透传，Ops 不改写，CLI 白名单显式接纳并返回非成功退出与查询/原记录重发提示，不转换为 UNKNOWN、不自动重试。取证前查询可能找不到 operation；保留原记录与 ID，稍后查询或原语义重发，不生成新 ID。旧 CLI 不识别新值会保守映射 UNKNOWN；旧 Ops primitive epoch 无法保证接受 null，Broker/Ops/CLI 应同版本成组部署，不承诺混合版本兼容。

确认先入时恢复立即 IN_PROGRESS，后续 scheduler 再扫描；恢复先入时确认也立即 IN_PROGRESS。后台恢复仍负责推进已有 committed 操作，不被重复确认升级。新增/调整双向竞争、并发唯一执行、不同内容拒绝、重复不额外消耗预算、失败/取消/换代清理测试；HTTP/客户端验证状态保真及提示。发布说明同步 Broker Architecture/Server README、Ops/CLI README 与 RELIABILITY。独立设计首轮 Revise（`/tmp/learner-no-share-design-review.md`），补 nullable epochs、取消及最终合同后第二轮 Approved（`/tmp/learner-no-share-design-review2.md`）；限定实现 Review Accept（`/tmp/learner-no-share-code-review.md`），不覆盖旧平铺或完整交付。纳入同一 T2，不创建第二个 active 任务，完整 E2E/full 缺口保留。

## 确认链可读性增量（用户批准试改，独立增量 Review 待完成）

当前决定取代下述接口切片：用户批准按独立复核 `/tmp/executor-concrete-adapter-design-check.txt` 的Revise意见修正后采用单个具体 `adapter.cluster.membership.BrokerMembershipExecutor`，删除application契约与Default名称，不增加转发层。保留唯一账本/锁/预算/优先级；必须同时修正业务输入输出（完整受信身份与领域确认事实、Server自有确认结果）、本地入队成功不得命名共识APPLIED，以及RaftAdapter额外线程跳转的进入/返回记账。先验证同步原生入口边界，再转换公开模型并迁包；旧接口Approved仅属于历史方案，不作为当前实现验收。

应用执行契约补充（历史，已被上条决定取代）：在application.cluster声明BrokerMembershipExecutor接口，既有实现改名infrastructure.cluster.DefaultBrokerMembershipExecutor；不增加抽象类、Coordinator或转发Adapter。AppService只依赖接口，learner阶段返回JDK Executor；ConfirmationRound由应用契约声明只读stage/waiting，内部可写completion仍由实现私有拥有，已完成拒绝使用契约的不可变完成句柄。following从RaftRuntime转换为Adapter自有不可变FollowingObservation，仍原子无锁读取、mutex内仅采样，保留generation比较；native调用继续沿用现有RaftConfigurationResult边界，不在此轮扩大协议转换。所有原预算、mutex、优先级、单worker、取消隔离和fault/close未知语义保持。ModuleBoundary原“执行器禁止依赖整个application”规则应改为“只可依赖精确执行契约及其嵌套类型，不得依赖AppService或其他应用流程”，以正反fixture证明不扩白名单；LayerBoundary不变。实施前独立Review，未取得不推进源码。

当前落位补充：用户批准将仅供确认／恢复使用的 LearnerConfirmationState 收为 BrokerMembershipAppService 私有嵌套类型，删除 model.execution 文件；Prepared/Finished 字段与阶段行为保持，下面的包名与Outcome说明属于原实施历史，Outcome已由后续决定删除。不新增抽象类、Coordinator或转发Adapter；执行器分层修复另按原子性清单核对，不以本次内联宣称完成。

用户批准不通过新增 private 步骤方法拆分确认流程，改在 `confirmLearner` 内平铺准备、取证、复验 Begin、Commit、终态与响应阶段，并直接返回只读派生链。`model.execution.LearnerConfirmationState` 仅承载不可变的 Prepared、Outcome、Finished 数据，不调用 Adapter、不拥有 Future 或执行器。恢复用例不改。

保留正常响应的 worker 发布门禁、异常映射与先精确移除再完成；正常发布之后通过 `applyToEither` 与既有 attempt 完成信号汇合，保证旧 generation 阶段被跳过时，首次调用的返回链仍可收到失效结果。汇合后不再调度业务或发布副作用。fault/close 既有 pending 边界不变。

首次调用返回链的只读视图。早期平铺试改曾保留重复请求固定共享视图与同一结果对象；该部分已被上节用户批准的不共享增量取代，重复请求改为立即 IN_PROGRESS。首个执行者内部失效桥梁仍保留。原设计 Review 不覆盖这些增量，尚待独立复核。

## Permit 确认输入校验可读性增量（用户批准，限定独立设计 Approved）

限定设计 Review Approved（`/tmp/permit-command-validation-design-review.md`），限定实现 Review Accept（`/tmp/permit-command-validation-review.md`）；仅覆盖本增量，实际 Bolt 异常组合及完整验收缺口保留。

AcceptMembershipPermitDrainedCommand 构造器用 AssertUtils.argNotBlank/argNotNull 逐字段校验必填；格式错误由原 AppService REJECTED 改为构造时参数异常。null 命令为内部调用错误，不再转业务拒绝。RPC Params 已有相同格式约束，正常 wire 输入行为不变；Handler 去掉 result 空值兜底，但认证主体由可信上下文提取后仍须通过 Command 构造校验。非法认证上下文生成空 BrokerId 时构造失败，不伪装 UNKNOWN 或进入共识；核对 Handler/Bolt 异常路径。

AppService 仅保留 caller/executor 与 authenticatedExecutor 的匹配、cluster/receiver 与本机匹配，以及后续 Permit/证据/CAS 检查；身份不匹配保持原 REJECTED 文案及状态。分段 guard 保留短路顺序，caller 身份不匹配时不访问 Adapter。删除 Stream 全字段扫描，不新增 helper。发送端展开本机/远端分支，命令先构造再调用；可信身份仍取 Permit。此处重排保留最后一次 localDrainedResult 复验到外部入口之间的原有安全范围，不为可读性删除复验。

## 请求执行协调对象增量（用户批准，独立增量 Review 待完成）

将 AppService 的两张进程内表及认领、释放、完成规则封装为 `model.execution.MembershipOperationAttempts` 和 `LearnerConfirmationAttempts`。前者管理摘要冲突、在途共享、4096 身份容量、历史身份继承和确定未提交拒绝的回收；后者管理确认与恢复共用的互斥、立即 IN_PROGRESS／冲突响应以及先精确移除再完成。认领结果以 Acquired／Existing 明确区分执行者和直接返回者，不暴露 Map 操作，不引入接口实现对或 Spring Service。

这些对象属于进程内执行模型，不是领域服务：它们依赖共识结果或 CQRS 类型，不进入 domain。AppService 保留 committed 状态读取、取证、Begin／Commit、身份保留时点和 worker 发布位置；Executor 保留永久预算、generation 和 Permit 账本。Future 完成不持协调对象锁；精确移除保留旧回调不能删除新尝试的约束。首次投递拒绝仍无条件精确撤销；正常完成仅按原确定拒绝条件回收，不改变未知结果保留语义。

用户已确认上述代码形态；这是同一 T2 的内部职责抽取，不改变协议与业务规则。独立设计／实现增量 Review 尚未取得，不借既有 Approved 宣称已覆盖本增量。

## 确认链单次分流增量（用户批准，独立增量 Review 待完成）

`confirmLearner` 在准备阶段之后仅分流一次 Finished／Prepared：历史和准备失败直接返回响应，新操作进入内层取证、Begin、Commit、终态链。各阶段直接传递 `BrokerClusterConsensusResult`，通过 evidence／registered／committed／completed 命名表示阶段，不再重复包装 Outcome 或透传 Finished。准备后任期失败仍通过原 Transformer 映射，保留原始快照 epoch 和 Leader 提示，不改为可能改变字段的快捷响应工厂。

保留每个取证／提交阶段的 `thenComposeAsync(..., stages)`、取证后的 revision／epoch／端点／任期及新成员准入复验、Begin DUPLICATE 不推进、提交与终态任期复验；最终异常映射、worker 发布及失效桥梁不变。历史请求不再经过无业务效果的中间透传任务，最终发布仍经过 worker 门禁。恢复链不改，Outcome 仍为其使用，不新增 private helper 或协作类。本增量不声称队列任务数量不变；其验收关注可观察响应、副作用和代际约束。

## learner 认领领域化增量（用户批准，限定设计 Approved／实现 Accept）

独立设计 Review Approved（`/tmp/learner-domain-design-review.txt`），限定实现 Review Accept（`/tmp/learner-domain-code-review.txt`），findings none。实现 Reviewer 读取当前源码及旧契约，未取得精确增量 diff 或独立运行测试；不代替完整 T2 验收。配置测试通过转调生产工厂验证共享占用，尚未直接加载完整生产配置证明 AppService 实际注入；静态核对生产 Bean 参数未发现接错，作为非阻断增强项保留。

本轮仅调整 learner。领域值对象 LearnerConfirmationIdentity 按 operationId、brokerId、expectedEpoch、membershipEndpoint 四字段保持原 Command.equals 冲突语义；领域实体 LearnerConfirmationAttempt 不覆盖 equals，实例代表一次占用，避免旧回调移除同内容的新尝试。领域结果 LearnerConfirmationClaim 采用 ACQUIRED／IN_PROGRESS／CONFLICT 状态与 executionAttempt 字段，构造校验确保仅 ACQUIRED 有尝试。领域服务 LearnerConfirmationService 使用 LearnerConfirmationAttemptRepository 原子 registerIfAbsent，登记返回本次或原占用者，由服务判定相同身份／内容冲突；release 委托 removeIfSame。

内存资源库在 infrastructure/domain/repository，唯一 ConcurrentMap 按 ClusterOperationId 保存领域尝试。registerIfAbsent 原子登记，返回胜出者；removeIfSame 只移除精确实例。资源库不依赖 Command、应用结果、Future、预算或调度，也不重复建表。领域服务与资源库通过配置构造注入 AppService，所有直接构造测试同步，不增加兼容构造器。

AppService 确认和恢复构造领域身份、按 claim.status 判断响应；成功后局部 CompletableFuture 与 minimalCompletionStage 提供首次执行者的失效桥梁，Future 不放领域或资源库。预算拒绝只释放，不完成未外泄信号；正常及角色失效在原 worker 位置先 release 再 complete。完成顺序、取消隔离、永久预算及 generation 门禁不变；fault/close 既有 pending 边界不扩大承诺。恢复入口在登记前构造完整身份，现有 resume 仅生成合法端点／epoch 命令；直接非法输入仍在登记前失败。

删除 model.execution 的旧 LearnerConfirmationAttempts／Attempt／Claim 与对应测试，替换为领域服务和真实内存资源库的测试；成员共享 Future 的模型本轮不改。替代方案为整体迁包（违反领域依赖）或领域表加 Future 表（增加重复状态和原子协调）；选择单表领域尝试加局部 Future。回滚按本增量恢复原协调对象及调用，不改 wire 或持久化格式。验证覆盖重复／冲突／释放／旧回调、并发唯一登记者、确认恢复跨入口、generation 与预算、配置注入及架构依赖。

## 成员尝试领域化增量（用户批准，限定设计 Approved／实现 Accept）

独立设计 Approved（`/tmp/member-domain-design-review.txt`），限定实现源码 Review Accept（`/tmp/member-domain-code-review.txt`），findings none。Reviewer 对照旧契约与当前源码，未取得精确增量 diff 或独立执行验证。非阻断增强项：锁内删除后／锁外通知前的专属交错测试，以及完整生产 AppService 注入运行证明；不冒称已覆盖。稳定说明的旧术语和本轮不改措辞已随后同步。

成员领域实体 MembershipOperationAttempt 保存 operation 与 identityRetained，提供继承保留事实与释放资格判断，不包含 Future 或 Adapter 结果。领域服务 MembershipOperationService 根据摘要、在途事实、身份数量判定 ACQUIRED／SHARED／CONFLICT／CAPACITY_EXCEEDED，固定身份容量 4096。是否在途继续由协调对象的 Future.isDone 提供，领域不另存 completed 标志，避免标志完成与 Future 通知之间出现新替换窗口。

MembershipOperationAttempts 通过配置注入，唯一 Map 的内部 Execution 绑定领域实体和完成源。认领在原锁内调用领域规则并登记；Claim 改为状态、executionAttempt 和 resultView，其中 resultView 是本次执行或已有响应的只读视图。SHARED 返回固定原视图，拒绝返回固定响应；AppService 只有 ACQUIRED 启动执行。完成方法按精确尝试查找内部 Execution，锁内决定回收，锁外通知；不存在或已替换的旧完成不影响新尝试，旧 Future 已完成则无需再次通知。首次投递拒绝仍无条件精确移除。共享在途时 Future 尚未完成，不会被替换；确定拒绝释放后旧完成信号由局部 Execution 引用继续通知。无第二张 Future 表。

AppService 保留 retainIdentity 的原触发位置；技术结果在协调对象转换为确定拒绝／已关联 operation 两个事实，再调用领域释放规则。纯行为保持重构，保留共享视图、取消隔离、满额、身份继承、未知结果和 generation。成员表不强行新增资源库；配置及全部直接构造同步，无兼容构造器。回滚恢复原协调模型与构造调用，不修改协议和持久化格式。

## 成员与 learner 领域服务合并（用户批准，限定设计 Approved／实现 Accept）

限定独立设计 Approved（`/tmp/merge-membership-service-design-review.txt`），实现 Review Accept（`/tmp/merge-membership-service-code-review.txt`），无必修 findings；只读源码复核，非精确增量 diff 或独立测试执行。完整生产 AppService 注入运行证明仍是非阻断增强项，部分直接构造 fixture 使用分立服务实例，不作为生产 singleton 证明；生产装配参数静态核对共用同一 Bean。

将 LearnerConfirmationService 的认领与释放规则直接合入 MembershipOperationService，分别命名 claimLearnerConfirmation／releaseLearnerConfirmation；不保留转发门面。领域服务注入 learner 资源库，成员 evaluate／start 规则保持不变。AppService 注入统一服务，成员 Future 协调对象与 learner 共用该领域服务实例，配置只创建一个服务 Bean；所有直接构造同步，不新增无参兼容构造。

只合并规则服务，不合并两类身份、状态、预算或存储。learner 内存资源库继续独立，成员现有单表 Execution 协调仍保留，成员资源库化尚未实施，不以本次合并冒称完成。删除旧领域服务，测试迁入统一服务测试；验证同一服务可以分别处理 learner 占用与成员准入而不互相影响，原共享 Future／容量／回收／generation 不变。无 wire、配置参数或持久化变更；回滚恢复原服务及注入。限定文件为统一／旧服务、AppService、配置、全部直接构造测试及说明，纳入原 T2。

## 成员资源库收尾（用户批准方向，限定设计 Approved／实现 Accept）

限定独立设计 Approved（`/tmp/member-repository-design-review.txt`），限定实现源码 Review Accept（`/tmp/member-repository-code-review.txt`），无必修 findings；未取得精确增量 diff 或 Reviewer 独立测试执行。Review 后补8线程唯一成员认领并共享结果测试并复验，尚未单独复核新增测试。删除后通知前的专属暂停交错与完整生产 AppService 注入运行证明仍为非阻断增强项。首次回归旧公开 Stage 身份断言失败，按本节契约改为在途状态；固定内部视图、业务回归及公开映射取消专测共同验证执行共享，不把 pending 单独当作共享证据。

删除 MembershipOperationAttempts 中间层，AppService 仅注入 MembershipOperationService。成员独立资源库保存 MembershipOperationAttempt，领域服务串行化认领及条件释放的组合操作；资源库只提供 find/count/save/removeIfSame，不判断冲突或容量。内存资源库单 Map，不与 learner 合表。

为避免第二张 Future 表或 Adapter 反向依赖，新增领域结果 MembershipOperationOutcome，精确保留状态、关联 operation、Leader 端点提示及原因。领域 Attempt 绑定该领域结果的固定 minimalCompletionStage 和完成源；这是 Broker 本地尝试的完成事实，不是 Raft 提交或 Permit 状态，不独立引入 completed 标志。JDK Future 不是框架依赖；领域不依赖 CQRS 或 Adapter。边界由 MapStruct 在 BrokerClusterConsensusResult 与领域 Outcome 之间映射，状态／字段不重解释。AppService 返回映射后的只读阶段，取消隔离；成员协调内部相同尝试仍共享固定领域结果视图，边界映射不承诺不同调用返回同一个 Stage 实例，但响应字段与单次执行保持共享。原 public executeMembershipOperation 返回类型不变。

服务锁内完成准入/继承/登记、按原条件精确释放，锁外完成领域 Attempt 的 Future；先移除后通知仍允许同步重入。已替换旧尝试仍可完成自身固定 Future，但不能删除新尝试；资源库 removeIfSame 比较实例。身份保留时点及 generation 工作线程位置不变。领域结果保存原始提示字符串，不验证或规范化为真实端点权威。首次投递拒绝仍无条件精确撤销。成员完整容量4096，未知保留、满额同ID重试、固定内部视图、取消隔离及旧回调回归必须保留。此前将 Future 一律排除领域的过严表述由此限定取代：允许仅承载自有领域结果的本地尝试完成源，不推广到 CQRS／Adapter Future。

## 成员扫描与重传规则收拢（用户批准，限定设计 Approved／实现第二轮 Accept）

设计 Approved（`/tmp/member-rules-design-review.txt`）；实现首轮 Revise/P2（`/tmp/member-rules-code-review.txt`）指出接收者资格只由应用stub测试覆盖，补真实BrokerCluster规则测试后第二轮Accept（`/tmp/member-rules-code-review2.txt`），保留首轮历史。Reviewer仅源码复核，非精确增量diff或独立测试执行。新增测试覆盖缺绑定/voter/learner-only/非目标及查询副本；专属多错误组合和采样后故障的完整矩阵不冒称新增覆盖。

BrokerCluster 新增未完成成员操作／同步中learner判断、按绑定端点查节点、校验接收者是否目标voter；BrokerClusterOperation 新增按stepId查找与待确认步骤选择，完整保留顺序／数量安全检查，不以Builder不变量为由删除。MembershipOperationService 根据 operation 与本机排空事实快照选择重传步骤：优先唯一当前未排空许可，无待确认才选最高序已排空且本机结果一致的步骤；任一已排空结果与已知本机事实矛盾均拒绝。输入只含领域值，不向领域传Executor或回调。

AppService 在worker读取operation，先校验结构，采集currentPermit对应本机结果到Map，再交服务选择与聚合校验接收者；保留选定后本机结果读取以及外部入口前最后复验。采集和校验是只读，本机事实可能失效，快照不是发送授权，最后实时复验不可删除。结构扫描与矛盾检查由交错改为先结构后本机事实判定；若同时存在多种非法情况，首先报告结构问题，不承诺原多错误之间的异常优先级。正常、单一错误和无副作用拒绝语义不变。AppService保留取证/共识/调度及原异常分类，不新增资源库。集群按端点查询返回节点副本，查询无副作用；名称不把Leader提示当权威。范围仅上述领域对象/服务、成员AppService、领域测试/成员回归与说明，纳入同一T2。

## 成员执行结果确认可读性（用户批准，限定设计 Approved／实现 Accept）

限定设计Approved（`/tmp/execution-confirm-design-review.txt`），源码Review Accept（`/tmp/execution-confirm-code-review.txt`），无必修findings；非精确增量diff/独立测试执行。cluster不匹配时不读localBroker的专属交互测试及全部拒绝路径零交互矩阵仍可增强，不冒称新增覆盖。

本轮将私有轮次入口命名 prepareExecutionConfirmation，worker流程命名 confirmMembershipExecution，并使用中文“确认某次成员变更执行已结束及其结果”说明，明确不是执行成员变更或主动停止旧调用。领域服务接收显式领域身份，检查受信执行者与caller/executor关系，以及cluster/receiver与本机身份关系；仍分两段应用调用，第一段拒绝不得访问本机Adapter。operation提供findCurrentMembershipPermit封装步骤查找。worker继续调用聚合既有confirmMembershipPermitDrained校验，避免新增只转发的方法或重复规则；该规则已在领域，不需要再套服务。

公开Stage/Future入口及wire协议本轮保持名称和签名，不将领域重命名扩大至Executor/SDK。AppService保留取证、worker调度、任期/证据复验和提交；注释先讲执行结果确认，再在证据步骤解释技术要求，异常/状态文案不改。验证身份拒绝短路、许可缺失、等待桥、并发共享及取消/超时行为。后续公开入口统一术语仍可单独调整，不以本轮宣称全协议重命名。

## 执行确认准入决定收拢（用户批准，限定设计Approved／实现Accept）

限定Review原件 `/tmp/confirmation-admission-design-review.txt`、`/tmp/confirmation-admission-code-review.txt`；源码Review非精确增量diff或独立测试执行。step存在无permit、非法Admission字段组合、完整身份拒绝零交互矩阵仍可增强，不冒称覆盖。

身份检查保留分段短路，不合并成一次需要提前读取Adapter的调用。新增领域 MembershipExecutionAdmission 以 ACCEPTED／REJECTED 和 permit／reason 表达执行记录准入；MembershipOperationService.admitExecutionConfirmation(snapshot,operationId,stepId) 定位执行记录并返回决定，不接Command或响应。只判定记录是否存在，完整permitId/incarnation/result校验仍在worker聚合及执行器，不提前改变拒绝顺序。

AppService prepareExecutionConfirmation 保留获取事实和分段身份检查，在领域决定后只拒绝或调用 submitExecutionConfirmation(permit,request)；后者是独立技术边界，统一确认轮次提交和仅覆盖执行器准入的UNKNOWN转换。两个公开视图仍共享一个准入入口，不复制校验，不新增Coordinator/资源库或公共协议。此提取以隔离执行器异常边界为理由，不宣称所有应用编排都必须成为单行方法。原短路、等待桥、worker/generation及异常文案不变。

## 执行确认值对象与只读检查（用户批准，限定设计Approved／实现Accept）

限定Review `/tmp/execution-value-design-review.txt`、`/tmp/execution-value-code-review.txt`，无必修findings；非精确增量diff或独立测试运行。AppService暂停后变更权威状态再经真实FSM应用的完整交错尚可增强，当前由聚合重验与FSM调用链支撑，不冒称真实链路已测。

新增领域MembershipExecutionConfirmation组合原七字段，Command由MapStruct转换，wire保持不变。BrokerCluster.checkMembershipExecutionConfirmation只读返回原APPLIED／DUPLICATE／REJECTED判定，不修改operation或revision；confirmMembershipExecution先调用同一只读规则，只有APPLIED才替换Permit/递增revision，重复或拒绝原样返回。AppService预检改只读，FSM应用重新校验并变更，不能信任预检结果。旧七参领域入口及全部调用迁移，不保留兼容包装。非法字段仍按原领域拒绝规则处理，不因值对象构造增加异常语义。验证预检无变更、应用一次递增、重复不递增、矛盾拒绝、预检后状态改变时应用重验以及映射字段保真。

## 全类可读性收尾（用户批准，限定实现Review Accept）

独立Review对照精确前快照 `/tmp/readability-all-before.java`，结果Accept（`/tmp/readability-all-review.txt`），findings none；未由Reviewer独立运行测试，不覆盖整个混合工作树或T2验收。

恢复链采用确认链的单次Finished/Prepared分流，内层直接传递registered/committed/completed结果，删除无人使用的Outcome。恢复Begin DUPLICATE仍可推进，RAFT_COMMITTED跳过Begin，不重新取证，终态/异常/发布及失效桥梁不变；历史提前结束不再消费纯透传阶段，最终仍经过worker门禁。整理公开入口在前、私有恢复在后的源码顺序及import；合并历史stored重复判断为if/else，保留发现历史及Begin前retainIdentity时点；完成发布先选outcome再统一映射。私有名称统一为executeMembershipStep/sendExecutionResultToLeader/sendRecordedExecutionResult/confirmExecution，不改Adapter/SDK/wire。旧反射测试仅同步方法名，不增加反射。范围AppService、LearnerConfirmationState、ExecutorTest及说明，同一T2，保留完整回归与独立审查缺口。

## 后续文档与执行

独立设计 Review Approved 后纳入现有唯一活跃计划 T2，补准确文件清单与 RED/GREEN 命令，不创建并行 active 任务。同步 Broker Architecture、Server README、RELIABILITY 及受影响的 Ops/CLI README、CLI 帮助。通用工程规则没有新增；若 Review 形成可复用规则，再同步拥有规范与 Harness。

## 附录 A：备选方案

1. 同入口自动恢复：调用方原 ID 重发即可继续，但确认入口需要处理全部历史阶段。
2. 提交与后台恢复分离（选择）：首次主线清晰，复用现有 scheduler，不扩公共协议；代价是重发不再主动推进，必须查询并等待后台恢复。
3. 新增对外显式恢复：人工控制更直接，但需增加 HTTP、Ops/CLI 与权限契约；用户选择不扩大此范围。
4. 拒绝所有历史且不恢复：未完成操作无收尾责任，不满足现有可靠性约束。

仅调整自有用例职责，不引入新的第三方能力主张；共识约束沿用已批准治理设计。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 新旧请求混合 | 确认不推进历史操作，由现有 scheduler/resume 恢复 | 分离新提交与恢复职责 |
| D2 | 在途重复 | 保留共享，两个入口共用协调 | 不并发执行同一身份，不重复预留预算 |
| D3 | 已登记请求响应 | 身份一致的成功与未完成操作返回 DUPLICATE，查询区分阶段 | DUPLICATE 是登记事实而非完成保证，复用既有客户端契约 |
| D4 | 恢复入口可达性 | 保留自动恢复，不新增 HTTP 恢复接口 | 用户明确批准，不扩公共权限与协议 |
| D5 | 资源及生命周期 | 保留现有共享永久预算和 worker/generation/term 约束 | 不借职责调整改变安全或容量模型 |
| D6 | 首轮 Review 发现确认端点链路不通 | 用户批准确认原记录要求显式端点，并经 CLI/Ops 原样传递；reason 可选保留 | 不删除摘要校验、不重试时刷新端点，不新增恢复接口；旧缺失端点记录失败关闭 |

## 附录 C：独立 Review

第一轮独立 Review：Revise。Reviewer 为新启动的独立 pi 会话，句柄 `term_6c4bc379-60bc-4517-8258-75de007f2b04`，结果原件 `/tmp/learner-auto-recovery-design-review.md`，受审设计 SHA-256 `6fd714aaebbf25546b59639b9b988bd6c96561c0965ac17166cd7dd33da57749`。一项 P2：CLI 原记录无法保存并传递 Broker 必需的确认端点，需明确前置阻断或纳入范围，不能把客户端链路当作已连通。核心职责分离、DUPLICATE 提交语义、预算及 generation 方向未发现其他必修项；另要求实施覆盖双向共享、Begin 与恢复 DUPLICATE 的不同策略及最新 phase。用户已批准将该缺口纳入；已补端点、reason、旧记录失败关闭及实际链路测试契约。

第二轮同一独立 Reviewer：Approved、findings none。原件 `/tmp/learner-auto-recovery-design-review-round2.md`，受审设计 SHA-256 `39b236608d65b0eae69e0e0811138d66aaa97fe7d3fbdd15396d6c92458bd37e`。首轮 P2 在设计层关闭，不表示代码缺口已修复。实施重点保留双向共享、Begin DUPLICATE 的不对称策略、worker 最新 phase、端点与 reason 同时发送及旧记录无网络重试。
