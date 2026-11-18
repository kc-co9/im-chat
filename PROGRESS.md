# 项目进度

- 当前活跃计划：`none`。[Broker 成员应用流程可读性计划](docs/exec-plans/completed/2026-11-09-broker-membership-flow-readability.md) 已归档，T1–T3 均为 `passing`。群聊风险由独立设计草案跟踪，未实施。

- Executor 可读性收尾已完成：import/命名/容量谓词/待处理阶段判定整理，未改锁和调度顺序。聚焦回归、完整Broker模块、完整架构模块均通过（证据见活跃计划），style/diff通过。下一步按窄到宽执行clean/full；T2仍active，不提交。

- 当前T2已采用单个具体adapter.cluster.membership.BrokerMembershipExecutor，删除application接口和Default实现；本地准入不再报告共识APPLIED，完整领域报告/确认结果隔离CQRS和SDK，原生观察保留错误码，RaftAdapter同步进入Runtime且删除第二线程池。原生入口RED/GREEN、架构/成员/映射及routing-ha编译通过，首轮实现Review Revise（仅accepted误映射）已修复并补拒绝映射/全状态回归；独立复审、clean/full待完成。历史接口方案及旧失败保留，不提交、不关闭计划。

- 执行接口切片已按用户批准实施：应用BrokerMembershipExecutor契约、Default基础设施实现、following Adapter自有观察、learner JDK Executor及只读确认句柄；原账本/锁/预算/优先级保持，配置和构造测试同步。独立设计Approved；LayerBoundary/ModuleBoundary及成员/配置聚焦通过，包含routing-ha编译。当前T2active，独立实现Review及新clean/full尚待，不提交。准确命令与范围见活跃计划。

- 当前架构修复第一片：用户批准的LearnerConfirmationState内联AppService已实施，原model.execution文件删除；成员回归通过，LayerBoundaryTest仅剩既有application→infrastructure Executor一项失败，不是完整通过。调用职责/原子性清单已写入原T2；不新增Coordinator、Adapter、抽象类或第二状态表，不迁Executor，第二片具体落位尚待明确与独立设计Review。无full/HA新证据、不提交，详见活跃计划。

- 当前full回归遗漏已修正：Ops Leader映射与审计两份fixture补齐必填端点，原完整字段／单跳／审计脱敏断言保留；聚焦及完整Ops模块通过、独立窄Review Accept。T2保持active，准备重新冻结clean/full；旧full 10项失败与网络失败均保留，不冒充当前通过，不提交。准确命令和原件见活跃计划。

- 用户要求继续收口：相同受控三键环境的 IAM UI npm ci 已成功，原 ECONNRESET 阻断的恢复前提得到窄复核；T2 恢复 active，重新冻结执行 clean/full，不复用旧失败的部分报告。未改网络配置、不提交，完整验收／quality／精确状态 Review 尚未完成，细节见活跃计划。

- 本轮收口停止于外部依赖：T2 改 blocked，完整 Broker 模块／真实治理 E2E 与最终独立代码 Review 已完成，最终 full 的 IAM UI npm ci 下载 whatwg-mimetype-4.0.0.tgz 报 read ECONNRESET，实际 Maven 退出1，尚未到HA。原启动 full.json 仍 incomplete，不手改机器证据；日志、关闭账本和恢复条件见活跃计划。原进程已全部退出、Loop #1已停止，不自动重复full、不归档、不提交。恢复下载后须重新冻结验收，代码级Accept不替代完整门禁。

- 当前用户要求继续收口原计划：T2 保持 active，恢复模块／真实治理 E2E／独立 Review／冻结 full 与 HA 验收。T1 非法 pending_acceptance 改为合法 blocked，仅修正表示，不改写历史证据；active 索引已同步。准确关闭账本与恢复步骤见活跃计划，未验收前不归档、不提交。

- 当前 Review 两项修复已实施，仍纳入原 T2：CLI 五动作显式端点记录/发送/retry 保真，旧缺端点记录拒绝不改写；权限同步由领域返回本轮撤回事实，不因历史停用记录重复撤销。RED/GREEN、真实 Ops→Broker 组合及 style/diff 证据见活跃计划。clean 仍被既有 T1 pending_acceptance 非法状态阻断，未继续 full；专属 MySQL 未启用，独立增量 Review 未取得。不提交、不关闭 T2。

- T2 BrokerCluster 注释：按用户批准补充关键方法Javadoc和观察资格/许可登记/Commit/阶段推进/排空收尾步骤块注释，说明校验顺序、重复处理与写入边界，不改逻辑。与`/tmp/broker-cluster-comments-before.java`对比Java token完全一致；readiness/style/diff check通过，计划同步。纯注释未重跑行为测试或独立Review，上一轮208/208不冒充本轮证据；clean仍既有pending_acceptance阻断（`/tmp/broker-cluster-comments-clean.log`），未继续full/HA，T2active，不提交。

- T2 BrokerCluster 可读性：按用户批准将Builder置底、公开行为集中、意图登记先于Commit、私有规则后置，展开成员阶段守卫和revision条件，观察者逐节点失败返回并复用已有成员谓词，整理import。不改签名/文案/Permit全历史扫描，不新增类或包装。六组208/208、readiness/style/diff check通过（`/tmp/broker-cluster-readability-tests.log`）；计划同步，无新通用规则，未取得独立增量Review。clean仍既有T1 pending_acceptance阻断（`/tmp/broker-cluster-readability-clean.log`），未继续full/HA，T2active，不提交。

- T2 全类可读性收尾：用户批准六项均已实施，AppService公开入口集中、import整理，恢复链一次分流后直接传递共识结果，删除无调用Outcome；历史分支合并且保留身份时点，发布统一选结果，四个私有名统一执行/发送/确认语义，旧反射测试同步名不新增反射。六组205/205、readiness/style/diff check通过（`/tmp/readability-all-tests.log`）；独立Review对照精确before快照Accept（`/tmp/readability-all-review.txt`），仅本增量非整体验收。设计/README/计划同步，clean结果见`/tmp/readability-all-clean.log`，未继续full/HA，T2active，不提交。

- T2 确认准备守卫整理：按用户批准展开上报者/cluster/receiver检查，保留逐段短路；删除matchesExecutionReceiver薄封装，SDK响应增加identityMismatch/rejected工厂，原状态/空Leader/文案不变。新增cluster错不读localBroker、receiver错不读snapshot且不提交执行器测试及响应工厂测试；五组聚焦回归（`/tmp/confirmation-guards-tests2.log`）、readiness/style/diff check通过，首轮测试注解漏限定已修。计划同步；clean本轮结果见`/tmp/confirmation-guards-clean.log`，未继续full/HA或取得新独立Review，T2active，不提交。

- T2 learner执行类型明确化：reserveLearnerExecution返回现有公开final内部LearnerExecution（私有构造、仍实现Executor），确认/恢复局部stages改execution，注释明确复用worker、绑定资格和失效收尾，不新增包装/finish机制或改变预算/调度。六组203/203、readiness/style/diff check通过（`/tmp/learner-execution-type-tests.log`），README/计划同步；本轮clean在drift阶段120秒超时，未取得最终结论（`/tmp/learner-execution-type-clean.log`）；此前pending_acceptance阻断仍为历史未解决项，未继续full/HA或独立增量Review，T2active，不提交。

- T2 应用确认入口命名统一：按用户批准改为confirmExecution／submitExecutionConfirmation／sendExecutionResult，更新应用注释、Handler、本机调用及Broker/HA fixture调用；Adapter/Executor/SDK/wire不改，无旧名包装。三组Workflow/Admission/RequestConfiguration测试及routing-ha testCompile、readiness/style/diff check通过（`/tmp/execution-api-rename-tests.log`）；README/计划同步。clean原pending_acceptance阻断保留（`/tmp/execution-api-rename-clean.log`），未继续full/HA、未新增独立Review结论，不提交。

- T2 成员投递入口已收为private：Workflow不再直接调用/spy submitMembershipOperation，改经resume公开入口及执行器submitApplicationStage协作边界，保留following拒绝/预算/共享优先/摘要冲突/worker重读覆盖，不加反射或兼容包装。Workflow/AppServiceChange/Scheduler135/135、readiness/style/diff check通过（`/tmp/private-submit-tests.log`），Java测试无私有入口引用。独立Review调用240秒超时（`/tmp/private-submit-review.txt`），未取得结论，不记Accept；clean既有pending_acceptance阻断保留（`/tmp/private-submit-clean.log`），未继续full/HA，T2active，不提交。

- T2 成员投递入口命名：executeMembershipOperation改submitMembershipOperation，从public收紧为包内可见；方法体不变，所有生产调用仍在AppService内。现有Workflow直接调用/spy隔离点仍依赖此入口，未强改private或引入反射，private目标尚未完成；后续迁移测试到公开用例后再收紧。Workflow/AppServiceChange/Scheduler三组135/135、readiness/style/diff check通过（`/tmp/submit-member-tests.log`），旧Java名称无残留。计划同步，clean原pending_acceptance阻断保留（`/tmp/submit-member-clean.log`），未继续full/HA或独立增量Review，不提交。

- T2 执行确认值对象/只读检查：七参数收为MembershipExecutionConfirmation，MapStruct生成Command转换；聚合check只读、confirm复用检查后首次更新，AppService预检不再改快照，FSM按最新状态重新校验。原七参入口及调用迁移，无兼容包装/wire变更。聚焦领域/应用/映射及ModuleBoundary命令通过（`/tmp/execution-value-tests.log`），readiness/style/diff check通过；设计Approved/实现Accept（`/tmp/execution-value-design-review.txt`、`/tmp/execution-value-code-review.txt`），非精确增量diff或独立执行。新增无副作用预检、相反结果后重验拒绝/重复revision及七字段保真测试；首轮漏test调用已补，不计行为RED。真实AppService→FSM交错待增强；设计/README/计划同步，clean既有pending_acceptance阻断保留（`/tmp/execution-value-clean.log`），未继续full/HA，T2active，不提交。

- T2 执行确认准入决定收拢：领域MembershipExecutionAdmission明确状态/permit/reason，统一服务定位执行记录；AppService准备入口保留身份短路并按决定拒绝或提交，submitExecutionConfirmation仅管理执行器准入异常边界，worker完整复验不变。六组聚焦回归/readiness/style/diff check通过（`/tmp/confirmation-admission-tests.log`），设计Approved/源码Review Accept（`/tmp/confirmation-admission-design-review.txt`、`/tmp/confirmation-admission-code-review.txt`），非精确增量diff或独立执行。无permit/非法字段/全部零交互矩阵为非阻断增强项；设计/README/计划同步，clean原pending_acceptance阻断保留（`/tmp/confirmation-admission-clean.log`），未继续full/HA，T2active，不提交。

- T2 执行结果确认可读性：私有入口改名prepareExecutionConfirmation/confirmMembershipExecution，用“确认成员变更执行已结束及其结果”说明；统一领域服务校验显式受信/本机身份，operation封装当前许可查找，已有聚合结果校验直接复用。公开API/wire及worker/证据/提交复验不变。九组220/220、readiness/style/diff check通过（`/tmp/execution-confirm-tests.log`），限定设计Approved／实现Accept（`/tmp/execution-confirm-design-review.txt`、`/tmp/execution-confirm-code-review.txt`）；拒绝路径完整零交互矩阵仍可增强。设计/README/计划同步，clean既有pending_acceptance阻断保留（`/tmp/execution-confirm-clean.log`），未继续full/HA，T2active，不提交。

- T2 等待句柄入口命名：按用户批准将 requestMembershipPermitDrainConfirmation 改为 submitMembershipPermitDrainedConfirmation，同步Handler和Admission/RequestConfiguration测试，不保留旧名包装，不改方法体或等待语义。readiness、两组聚焦测试（`/tmp/permit-confirm-rename-tests.log`）、style/diff check通过，Broker Java旧名无残留；历史原件名称不改写。clean既有pending_acceptance阻断保留（`/tmp/permit-confirm-rename-clean.log`），未继续full/HA，T2 active，不提交。

- T2 扫描/重传规则收拢：BrokerCluster拥有维护状态、端点副本查询和target-voter绑定资格，operation拥有步骤查询/完整结构扫描，统一服务根据本机领域事实Map选择Permit并拒绝矛盾。AppService保留采样/worker/请求组装、选定后及发送前实时复验。新增选择测试3项、真实聚合规则测试；最终业务/领域279/279、ModuleBoundary14/14、readiness/style/diff check通过（`/tmp/member-rules-final.log`）。首轮fixture缺minimumVoters、回归旧getter stub未命中新聚合方法已修正；独立设计Approved，实现首轮Revise/P2因领域资格缺专测，补齐后第二轮Accept（`/tmp/member-rules-code-review.txt`、`/tmp/member-rules-code-review2.txt`），首轮保留。设计/Architecture/README/计划同步，无新通用规则；clean原pending_acceptance阻断未解决前不继续full/HA（`/tmp/member-rules-clean.log`），T2 active，不提交。

- T2 成员资源库收尾已实施：删除 MembershipOperationAttempts／执行层 Claim，AppService 仅注入统一 MembershipOperationService；两张独立资源库，成员单表保存领域 Attempt 及自有 Outcome 完成源，MapStruct 边界保真映射，无第二张Future表或独立completed标志。领域服务锁内认领/容量/精确释放，锁外通知；公开Stage包装不保证实例身份，内部固定共享/取消隔离保持。首轮旧Stage身份断言失败，按获审契约调整并补映射取消/全状态测试；最终业务/配置246/246、ModuleBoundary14/14、routing-ha testCompile、readiness/style/diff check 通过（`/tmp/member-repository-final.log`），含新增8线程唯一认领。限定设计Approved／源码Review Accept（`/tmp/member-repository-design-review.txt`、`/tmp/member-repository-code-review.txt`），新增并发测试为Review后补、非精确增量diff或独立执行证明。配置完整图/专属删除后通知前交错仍待增强；Architecture/README/计划/设计同步。clean既有pending_acceptance阻断保留（`/tmp/member-repository-clean.log`），未继续full/HA，T2 active，不提交。

- T2 领域服务合并：按用户批准将 learner 认领/释放直接合入 MembershipOperationService，删除 LearnerConfirmationService，无转发门面；AppService 与成员协调由生产配置共用一个服务 Bean。仅合并服务，不合并存储；成员资源库化尚未完成。重命名领域服务测试，新增同 ID 两类占用双向不干扰回归；十组业务/配置234/234、ModuleBoundary14/14、routing-ha testCompile、readiness/style/diff check 通过（`/tmp/merge-membership-service-regression.log`）。限定设计 Approved／实现 Accept（`/tmp/merge-membership-service-design-review.txt`、`/tmp/merge-membership-service-code-review.txt`），非精确增量diff或独立测试。完整生产注入运行证明待增强，设计/Architecture/README/计划同步；clean 既有 pending_acceptance 阻断保留（`/tmp/merge-membership-service-clean.log`），未继续 full/HA，T2 active，不提交。

- T2 成员尝试领域化：MembershipOperationAttempt 移入领域保存身份/保留及回收资格，MembershipOperationService 拥有冲突/共享/固定4096容量和继承规则；协调对象单表 Execution 绑定领域尝试与 Future，不新增 completed 标志或第二张表。AppService 注入协调对象并按状态 Claim 判断，成员原子性/取消/generation 不变。八组协调回归及既有领域/业务/配置共233/233、ModuleBoundary 14/14、routing-ha testCompile、readiness/style/diff check 通过（`/tmp/member-domain-regression.log`）；设计 Approved、限定实现 Review Accept（`/tmp/member-domain-design-review.txt`、`/tmp/member-domain-code-review.txt`），非精确增量 diff/独立测试执行。专属删除后通知前交错和完整生产注入运行证明为非阻断增强项；设计/Architecture/README/计划同步。clean 既有 pending_acceptance 阻断未解决前不继续 full/HA，日志 `/tmp/member-domain-clean.log`，T2 active，不提交。

- T2 learner 认领领域化：按用户批准新增领域 Identity/Attempt/状态 Claim、LearnerConfirmationService 和领域资源库；InMemory 实现单表原子登记、精确实例释放。AppService 确认/恢复按状态响应，局部 Future 保留失效桥梁；配置注入共用服务/资源库，旧 model.execution 三类型及旧测试删除，成员共享模型未改。设计独立 Approved、限定源码 Review Accept（`/tmp/learner-domain-design-review.txt`、`/tmp/learner-domain-code-review.txt`）；九组业务/配置 225/225、ModuleBoundary 14/14、routing-ha testCompile、readiness/style/diff check 通过（`/tmp/learner-domain-regression2.log`）。首次漏同步一个全限定构造调用已修复；非生产缺陷 RED。配置专测尚非完整生产 AppService 注入运行证明，Review 记录非阻断增强项；clean 既有 pending_acceptance 阻断未解决前不继续 full/HA，证据 `/tmp/learner-domain-clean.log`，T2 active，不提交。

- T2 确认链单次分流：按用户批准，confirmLearner 准备后一次区分 Finished/Prepared；新操作链直接传递 evidence/registered/committed/completed 共识结果，删除重复 Outcome 包装和 Finished 透传。保留各副作用阶段 worker 调度、revision/epoch/端点/任期复验、Begin DUPLICATE 不推进、最终发布与失效桥梁；恢复链不改。六组 203/203、readiness/style/diff check 通过（`/tmp/learner-direct-tests.log`）。设计与活跃计划同步；独立增量 Review 未取得，clean 结果另见本轮日志 `/tmp/learner-direct-clean.log`，既有 T1 pending_acceptance 阻断未修复前不继续 full/HA，T2 active，不提交。

- T2 容量常量归位：按用户意见将 4096 常量移入 MembershipOperationAttempts，AppService 无参创建；小容量构造器仅包内测试使用。`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=MembershipOperationAttemptsTest,BrokerMembershipWorkflowTest,BrokerMembershipDriftTest' -Dsurefire.failIfNoSpecifiedTests=false`、readiness/style/diff check 通过（`/tmp/attempt-capacity-tests.log`）；既有 clean 阻断及独立 Review/full/HA 缺口不变，T2 active，不提交。

- T2 请求执行协调抽取：用户批准将两张表及认领、共享/互斥、容量、精确释放/完成规则封装为 model.execution.MembershipOperationAttempts 与 LearnerConfirmationAttempts，Acquired/Existing 区分执行者和直接响应。AppService 保留业务阶段、身份保留时点和 worker/generation 调度，不迁入 domain、不改永久预算。新增协调对象 8/8，八组业务共 211/211、窄架构 1/1、readiness/style/diff check 通过（`/tmp/attempt-coordination-final.log`）；初期 fixture/枚举/AssertJ 重载失败已修正，不计生产缺陷 RED。设计、Architecture/README 与活跃计划同步；clean 仍被既有 T1 pending_acceptance 阻断（`/tmp/attempt-coordination-clean.log`），未继续 full/HA，独立增量设计/实现 Review 未取得，T2 active，不提交。

- T2 MembershipOperationAttempt 迁移：按用户确认从 BrokerMembershipAppService 内部类移至 model/execution，改为语义访问器与 retainIdentity 动作，删除旧内部类；成员操作共享、容量、Future/身份保留语义不变。六组回归、style/diff check 通过，证据见活跃计划；此前 confirm 步骤注释已补，retransmitDrainedPermit 经核对保留为完整安全扫描，不删除。clean/full/HA 缺口保留，T2 active，不提交。

- T2 learner 结果工厂收敛：CompleteLearnerSyncResult 新增 notLeader/unavailable/自定义原因 rejected，替换 AppService 全部 learner workflow/recovery 裸结果构造；不把固定结果误放 Transformer 或 Builder。状态、epoch、文案测试与七组回归 211/211、style/diff check 通过，未重跑 clean/full/HA，T2 active，不提交。

- T2 组装/转换纠偏：Permit 登记命令与 SDK 确认 Params 使用手写 Builder（record canonical 校验/字段不变），删除两个多来源 Mapper；真正请求→命令转换仍用 MapStruct。Builder 复用隔离/字段/非法输入及领域/成员回归、style/diff check 通过，限定源码 Review Accept，证据见活跃计划。维护指标分段短路保留；扫描删除试改因 Review Revise 已恢复安全检查，既有领域 Builder alias 风险不在本次扩大修复。clean 仍被原 T1 pending_acceptance 阻断，未继续 full/HA，T2 active，不提交。

- T2 多参数转换：按用户澄清撤回新增 Lombok Builder，仅新增 MapStruct BrokerMembershipTransformer，接管 Permit 请求、受信身份显式传入的本机接收命令、共识确认和登记命令四类字段映射；canonical 校验、业务判断与生命周期位置保留，固定结果不强制转换。映射专测/聚焦回归/style/diff check 通过，限定源码 Review Accept，证据见活跃计划；clean 仍被原 T1 pending_acceptance 阻断，未继续 full/HA，T2 active，不提交。

- T2 Permit 确认校验整理：Command 用 AssertUtils 建立字段完整性，AppService 删除 Stream/null 兜底，仅分段校验受信身份及本机匹配；本机/远端分支展开，Handler 删除 result 空值兜底。格式错误由构造异常表达，身份不匹配仍 REJECTED；构造 RED/GREEN、聚焦回归/style/diff check 通过，限定设计/实现 Review Approved/Accept，真实 Bolt 异常组合覆盖缺口见活跃计划。clean 仍被原 T1 pending_acceptance 阻断，未继续 full/HA，T2 active，不提交。

- T2 命名收尾：按用户确认将私有 resumeLearnerConfirmation 及调用点更名 resumeLearner，不改方法体。readiness、编译、diff check 通过，证据见活跃计划；未重跑行为/full/HA，既有 clean 阻断保留，T2 active，不提交。

- T2 恢复链平铺：resume 删除不可达旧用例 switch、修正注释；为保留非成员输入旧错误分类保留末尾端点检查。resumeLearnerConfirmation 复用现有阶段载体，直接返回准备/Begin/Commit/终态/映射/发布链，汇合失效信号，保留恢复 Begin DUPLICATE 可推进、不重新取证和 worker 门禁。聚焦回归/style/diff check 通过，限定源码 Review Accept，覆盖边界见活跃计划；clean 仍被原 T1 pending_acceptance 阻断，未继续 full/HA，T2 active，不提交。

- T2 确认链两处冗余收敛：准备阶段直接 supplyAsync，终态守卫通过后直接调用 Adapter，移除同结果重复检查；其他短路、任期/revision/端点及发布门禁不改。聚焦回归/style/diff check 通过，证据见活跃计划；clean 仍被原 T1 pending_acceptance 阻断，未继续 full/HA、本增量未独立 Review，T2 active，不提交。

- T2 固定在途结果工厂：CompleteLearnerSyncResult 新增 inFlightConflict/inProgress，确认与恢复共用，固定状态/空值/文案不变；未新增 private 方法。工厂字段断言先 RED 后 GREEN，聚焦回归/style/diff check 通过，证据见活跃计划；clean 仍被原 T1 pending_acceptance 阻断，未继续 full/HA、未取得本增量独立 Review，T2 active，不提交。

- T2 learner 调度闭包已提取为 BrokerMembershipExecutor 的 private 内部 LearnerExecution，实现标准 Executor；pending 使用 mutex 保护的 boolean，保留固定 generation、单 pending、永久预算及锁外执行。六组聚焦回归/style/diff check 通过，限定源码 Review Accept，证据边界见活跃计划；clean 仍被原 T1 pending_acceptance 阻断，未继续 full/HA，T2 active，不提交。

- T2 两表职责核对：未证明可删除的冗余账本；保留成员身份/在途共享表和 learner 在途互斥表，不擅自统一共享契约。submissionStarted 更名 identityRetained，容量常量明确为 MEMBERSHIP_ATTEMPT_CAPACITY，修正完成后身份保留与执行器账本区别的注释/README。聚焦回归及 style/diff check 通过，证据见活跃计划；clean 仍被原 T1 pending_acceptance 阻断，未继续 full/HA或取得新独立 Review，T2 active，不提交。

- T2 预算拒绝出口收敛：确认与恢复在 reserve 返回 null 时精确移除后直接返回 completedStage，删除无等待者的 attempt 完成/返回中转；保留在途身份与换代桥梁。聚焦回归、Java style/diff check 通过，独立窄 Review Accept，证据见活跃计划；clean 仍被原 T1 pending_acceptance 阻断，未继续 full/HA，T2 active，不提交。

- T2 在途不共享已实现供 Review：同身份重复立即 IN_PROGRESS（epochs 为空），不同内容 REJECTED；确认/恢复只共用互斥，不共享响应，内部 generation 完成桥梁保留。Broker/Ops nullable acceptedEpoch、CLI 状态识别/非成功提示和同版本部署说明已同步。独立设计第二轮 Approved，限定实现 Review Accept；RED/GREEN、聚焦回归与具体证据见活跃计划。clean 仍被原 T1 pending_acceptance 阻断，未继续 full/HA；真实 E2E 缺口保留，T2 active，不提交。下条平铺阶段的共享契约已被本次批准取代，不代表当前行为。

- T2 确认链平铺试改：按用户批准不新增 private 方法，在 confirmLearner 内依次展示准备、取证、复验 Begin、Commit、终态和响应；新增 model.execution.LearnerConfirmationState 只承载不可变阶段事实。正常结果保留 worker 发布门禁，与 attempt 失效信号汇合后直接返回只读链；执行器及恢复实现不变。首次与重复请求 Stage 身份不再相同，重复请求仍共用固定视图，结果对象/副作用共享且取消隔离。首次六组 3 failures/1 error 为旧对象身份断言，更新契约断言后最终 203/203 通过（`/tmp/learner-flat-final2.log`）；readiness、Java style、diff check 通过。clean 仍因既有 T1 pending_acceptance 失败（`/tmp/learner-flat-clean.log`），未继续 full/HA；独立增量 Review 待完成，T2 active，不提交。设计增量与计划已同步。

- T2 共享/文档补齐：新增确认先入与恢复先入的双向同结果测试，确认先占表时不升级恢复、下一轮 resume 才推进；补旧无端点记录 retry 抛错、无任何网络请求且不改记录。六组 Broker 203/203、三组 CLI 135/135 通过（`/tmp/learner-sharing-docs-final.log`），Java style、diff check 通过。Broker Architecture/Server README、Ops/CLI README、RELIABILITY 与 CLI 提示已同步登记/终态区别及后台恢复边界。真实 E2E 尚未修改或验证：当前 write fixture 仅为 learner 分配地址而未启动目标 runtime，实际取证需扩正式 topology，不能 mock 进度冒充真实链路。独立实现 Review 仅覆盖前一增量，本轮新增测试/文档待复核；T2 active，未运行 full/HA、不提交。

- T2 Broker 分流首版：confirmLearner 对历史已登记请求只校验身份并返回 DUPLICATE，不取证或推进；新 Begin 返回 DUPLICATE 不接管恢复。resume 改走独立 learner 恢复用例，共用在途表和预算，在 worker 重读阶段后恢复。新增历史 PENDING/RAFT_COMMITTED 只读确认先 RED（`/tmp/learner-history-split-red.log`），实现后通过；六组首次回归三项旧重发恢复断言按新契约调整。最终六组 201/201 通过（`/tmp/learner-history-split-final2.log`）。一次重跑退出 1 且日志为空，后续同范围完整通过，不计为通过证据。独立限定 Review 首轮 Block（`/tmp/learner-history-split-code-review.md`）经完整调用链复核撤回为误报，第二轮 Accept/findings none（`/tmp/learner-history-split-code-review-round2.md`）；保留首轮原件。另补延迟 Begin 返回 DUPLICATE/SUCCEEDED 不重复提交的回归，未改生产逻辑即通过；该新增 fixture 首次 when 调用触发旧 stub 的线程断言，改 doAnswer 后通过。模块稳定说明、CLI 旧记录无网络 retry 专测和真实 E2E 尚待补齐，T2 active，不运行 full/HA、不提交，既有 clean 阻断保留。

- T2 CLI/Ops 端点链路增量：CLI 确认解析必填端点并保留可选 reason，记录发送与 retry 同时原样传递两者；旧缺端点记录拒绝且原文件不变，缺端点/错序命令退出失败。CLI 先 RED（3 failures，`/tmp/learner-cli-send-red.log`），最终全模块 173/173 通过（`/tmp/learner-cli-send-final.log`）。Ops 确认使用独立 CompleteLearnerSyncRequest，原样传递端点/reason；HTTP 测试先 RED 1 failure（`/tmp/learner-ops-endpoint-red.log`），最终 48/48 通过（`/tmp/learner-ops-endpoint-final.log`），含缺失/空白端点及负 epoch 拒绝。readiness、Java style、diff check 通过，CLI README 同步。Broker 分流、后台恢复、真实 E2E 及独立实现 Review 尚未完成，未运行 full/HA、不提交，T2 active，既有 clean 阻断保留。

- T2 后台恢复设计已获第二轮独立 Approved、findings none（`/tmp/learner-auto-recovery-design-review-round2.md`），用户批准纳入确认端点原样保存/传递；首轮 Revise 保留。已纳入原 T2，开始 CLI 子步骤：确认记录要求非空端点并保留 reason；OperationRequestFileTest 先 RED（3 failures/1 error，`/tmp/learner-endpoint-record-red.log`），最小实现后 34/34 GREEN（`/tmp/learner-endpoint-record-green2.log`）。首次 GREEN 仅旧拒绝文案断言未同步，已更新准确文案后通过。CLI 解析/发送/retry、Ops 请求、Broker 分流及 E2E 尚未实施，不把局部通过当作整体验收；readiness、diff check 通过，T2 active，不提交。

- T2 后台恢复设计：用户已批准保留自动 scheduler/resume、不新增恢复 HTTP 接口。独立 pi 设计 Review 首轮 Revise（`/tmp/learner-auto-recovery-design-review.md`）：CLI 原记录仅允许 add-learner 保存端点，确认经 Ops 传空端点而 Broker 要求非空，既有链路无法连通；须确认是否纳入本次端点契约修复范围。设计与索引已保留 Revise，未实施生产变更；readiness 通过，T2 active，既有验收缺口不变。

- T2 恢复可达性核对：`BrokerMembershipScheduler` 已自动调用 `resume`，但 Broker 无独立恢复 HTTP 接口，`OpsCliClient.retry` 仅重发原请求。已补充设计事实；「客户端显式恢复」并非现有能力。待用户选择保留自动协调恢复并查询，或新增对外恢复接口；不默认扩公共协议，不改生产代码。readiness 通过，独立设计 Review 尚未执行。

- T2 后续方向：用户批准确认提交与显式恢复分离，已写入 `docs/design-docs/2026-11-12-learner-confirmation-explicit-recovery-design.md` 并登记索引。核实现有 `resume` 对 learner 仍回调 `confirmLearner`，不能直接删除恢复分支；响应映射、恢复可达性、跨入口并发和预算规则尚待核对与独立设计 Review，草案不作为实施批准。readiness 通过，本轮仅新增设计事实，不改行为或新增实施任务，T2 仍 active；既有 clean 阻断保留。

- T2 输入转换前置：按用户批准将 `confirmLearner` 的 BrokerId、ClusterOperationId、MembershipEndpoint、RoutingEpoch 和 CommandDigest 构造移到方法开头、在途登记与预算预留之前，worker 直接使用领域值。非法领域值改为入口同步抛出，不再映射 UNAVAILABLE；新增负 epoch 回归验证无在途记录或 Begin/Commit，随后合法同 ID 可成功。五组定向测试通过（`/tmp/learner-conversion-tests.log`），readiness、Java style、diff check 通过；未执行修复前 RED，未取得独立 Review，未运行 full/HA，既有 clean 阻断保留，T2 active，不提交。

- T2 learner 异步入口简化：按用户批准将 `confirmLearner` 的外层 `supplyAsync` 加手动展平改为 `completedFuture(null).thenComposeAsync`，内部逻辑、受控执行器、异常映射及发布阶段不变。五组定向测试 193/193 通过（`/tmp/learner-compose-tests.log`），readiness、Java style、diff check 通过。未取得独立 Review，未运行 full/HA；既有 clean 状态阻断保留，T2 仍 active，不提交。

- T2 命名微调：按用户意见将 `confirmLearner` 的局部变量 `workflowTerm` 改为 `expectedTerm`，同步全部引用，不改逻辑或结果文案。`mvn -q -pl im-broker/im-broker-server -am -DskipTests compile` 与 diff check 通过（`/tmp/learner-term-rename-compile.log`）；未重跑测试或 full/HA，T2 保持 active。

- T2 按用户确认展开三个 learner 收尾 helper：删除 `learnerConfirmationUnavailable`、`publishInvalidLearnerAttempt`、`publishLearnerConfirmation`，在调用处直接构造结果、精确移除并完成；不改状态、文案、异步边界或发布顺序。五组定向测试 193/193 通过（`/tmp/learner-helper-inline-tests.log`），readiness、Java style、diff check 通过，旧方法无残留。未取得独立 Review，本轮不运行 full/HA、不提交；此前 clean 的既有计划状态阻断保留，T2 仍 active。

- T2 learner 入口展开：按用户确认，将 `executeLearnerConfirmation` 原样内联到 `confirmLearner` 的 worker lambda，删除单调用私有方法，不改判断、异步阶段、异常映射和发布顺序。机械等价脚本通过；Workflow/Executor/Admission/Drift/AppServiceChange 五组 193/193 通过（`/tmp/confirm-learner-inline-tests.log`），readiness、Java style、diff check 通过。`./scripts/verify.sh clean` 失败：既有 T1 状态 `pending_acceptance` 不在 checker 支持集合，日志 `/tmp/confirm-learner-inline-clean.log`；未改写既有任务状态或放宽规则。未取得独立 Review；T2 保持 active，本轮未运行 full/HA、不提交，不宣称整体验收。

- T2 learner 在途表按用户意见改为 ConcurrentHashMap：putIfAbsent 选唯一执行者，删除登记与完成发布的显式锁；仅胜者预留预算，拒绝时精确移除并完成共享结果。补八调用并发共享回归，旧等待应用锁测试调整为持有 Map monitor 不阻塞发布。五组定向测试 193/193 通过（`/tmp/learner-chm-final.log`），readiness、Java style、diff check 通过；独立限定复核无必修 findings。首轮旧锁测试超时已按新契约调整，T2 保持 active，未运行 full/HA、不提交。

- 监控迁移最终补验：初始 Gauge 断言补充后的配置测试 13/13 通过（`/tmp/membership-metrics-config-final.log`），readiness、Java style、diff check 通过；该结果补齐下条待记录项。T2 仍 active，未运行 full/HA、未提交。

- T2 监控迁移供 Review：两个配置诊断字段和 Gauge 注册移入 `support.monitoring.MembershipDiagnosticMetrics`，应用服务删除指标 getter，只报告采样结果；实时配置、任期/revision 准入检查不变。七组 Broker 测试 208/208 通过（`/tmp/membership-metrics-tests.log`），窄架构 1/1 与 routing-ha 测试编译通过（`/tmp/membership-metrics-boundary.log`）；独立限定复核无必修 findings。首次 testCompile 的两处遗漏已修复，HA fixture 构造同步；初始指标断言补充后的配置复验另记录。T2 保持 active，不运行 full/HA、不提交。

- T2 冗余收敛：删除 learner 在途 record.equals 后的重复 digest 比较；成员旧 Permit 恢复与新 Permit 登记统一结果尾部，不新增抽象，保留注册后的任期/Permit 身份、最新步骤和异步边界检查。Workflow/Executor/AppServiceChange/Admission 四组 184/184 通过（`/tmp/membership-dedup-tests.log`），readiness、Java style、diff check 通过，独立限定复核等价无 findings。T2 保持 active，不运行 full/HA、不提交。

- 当前 T2 按用户批准收敛为 learner 在途表：完成时精确移除，后续按 committed operation 判断幂等与恢复；不再缓存完成后的拒绝、重放或冲突视图。LearnerConfirmationAttempt 移至 model.execution，仅保留 command、一个完成源及固定只读视图；删除 retryable 与 LearnerReplayFacts。不同内容在途请求立即拒绝，新尝试仍消耗共享永久预算。成功历史直接 DUPLICATE，不重复 Begin/Commit/证据。五组业务 192/192、窄架构 1/1 通过（`/tmp/learner-inflight-final.log`），readiness、Java style、diff check 通过，独立限定复核无 findings。首次测试发现成功重放重复 Begin 已修复；T2 active，不运行 full/HA、不提交。

- 当前 T2 Future 增量已完成供 Review：删除 StageSlot 和 claimPublication，标准 Executor 闭包绑定首次 generation；永久计数和待执行队列替代历史槽位对象，保留共享 4096 预算及单 pending。confirmLearner 使用 Future 链编排，重放事实后再投递发布任务以复验 generation。五组业务测试 192/192、窄架构 1/1 通过（`/tmp/learner-future-final.log`）；readiness、Java style、diff check 通过，独立限定复核无必须修复 finding。fault/close 仍保留既有可能未完成边界，内部被跳过的 Future 不作为对外结果。T2 仍 active，不运行 full/HA、不提交。

- 当前 T2 增量已调整供 Review：learner 的身份、共享、重试与重放归应用层强类型 LearnerConfirmationAttempt，执行器仅保留技术阶段槽、共享永久预算与 worker/generation 约束，删除 submitWorkflow/unchecked API。完成发布在应用锁内认领当前代际，释放全部锁后完成 Future；认领后角色切换不撤销已选结果。五组业务测试 191/191、窄架构 1/1 通过（`/tmp/learner-ownership-final2.log`）；readiness、Java style 与 diff check 通过，独立限定复核无剩余必修 finding。此前一次增量构建因残留 class 的 NoClassDefFoundError 无法发现测试，clean 后通过。T2 仍 active，不运行 full/HA、不提交，不宣称整体验收。

- 两项 P2 已处理供 Review：本地状态改名 `MembershipOperationAttempt`，提交前确定拒绝可精确释放，提交未知/历史身份继续保留；诊断先复验 term/revision 再发布 drift。补满额拒绝恢复和失效 mismatch 回归，四组定向测试、Java style、diff check 通过（`/tmp/membership-attempt-final.log`），独立限定 Review 无新增 findings；入口步骤和字段注释已补。未执行修复前 RED 或 full/HA，不提交，T2 仍 active。

- 可读性小幅整理完成：两处 learner 任期守卫复用现有方法，终态推进移除多余 else，重放事实 record 收为 private；未改变 ReadIndex 证据校验或异步边界。Workflow/AppServiceChange 定向测试、readiness、Java style 与 diff check 通过，独立限定 Review 无 findings；日志 `/tmp/membership-small-cleanup-tests.log`。T2 仍 active，不运行 full/HA、不提交。

- 命名收尾：按用户确认去除成员处理、步骤推进、排空重传/确认和配置诊断五个私有方法的 `OnWorker` 后缀，同步调用点与 Javadoc；诊断私有方法采用 `inspectMembershipConfiguration` 避免与公开入口重名。编译、旧名搜索、diff check 通过；仅重命名，不改变线程调度和校验边界。T2 仍 active，本轮不运行 full/HA。

- 当前增量已完成供 Review：成员操作及完成发布绑定首次准入 generation，修复 Begin 延迟返回跨同 term 重启窗口；入口改名 `executeMembershipOperation`，收紧内部可见性，期望配置归入 `BrokerCluster.expectedMembership()`，不留兼容包装。新增回归修复前超时失败；最终成员/领域五组 227/227、窄架构 1/1、readiness、Java style 与 diff check 通过，日志 `/tmp/membership-operation-final.log`。独立源码 Review 未发现未处理 finding；本轮不运行 full/HA、不提交，T2 仍 active，不宣称整体验收。

- 入口可读性增量：展开 `executeMembershipPermit` 的冲突/共享/容量守卫，并将单调用的 `runMembershipOperation` 内联到 worker 任务；不改调度、异常映射或重试契约。内联后 Workflow 75/75、Java style 与差异检查通过；不运行 full/HA，T2 状态不变。

- 命名调整：按用户确认将技术组件改名为 `BrokerMembershipExecutor`，同步构造器、Bean 方法、测试与当前规范引用；不修改行为，历史验收名称保留。当前任务仍为 T2，不运行 full/HA。

- Review 增量：已整理 `BrokerMembershipAppService` 的方法与内部类型顺序；仅调整源码组织，不改方法名、可见性或方法体。readiness、等价检查、差异空白检查与 Java style 通过；遵照用户要求未重跑 full/HA，计划仍为 T1 active。

- 最新恢复：full 的 TOKEN_EXCHANGE 失败已定位为 CLI 严格模型未接纳标准可选 `scope`；显式字段修复后 CLI 测试 60/60、治理 E2E 8/8 及增量独立 Review Accept。重新 clean 通过；full 的 Maven/Harness 阶段完成，最终 routing-ha 因 Docker Hub 拉取 `eclipse-temurin:21.0.12_8-jre-jammy` 超时失败，直接 `docker pull` 仍因 Docker Hub 连接超时，未取得最终验收。

- 历史进度：[Broker 成员应用流程可读性](docs/exec-plans/completed/2026-11-09-broker-membership-flow-readability.md)，当时 T2 `active`：用户批准先调整职责和包位置，执行器已移到 `infrastructure.cluster`，learner 业务映射与完成事实由 AppService 提供，技术账本保留共享预算和 worker/generation 约束。迁包后成员回归 178/178、窄架构测试 1/1、Java style 与 diff check 通过；未运行 full/HA。T1 保留 `pending_acceptance`，T3 尚未开始，不以聚焦通过表示整体完成。[官方 JRaft 治理迁移与自有运维 CLI](docs/exec-plans/completed/2026-10-30-official-jraft-and-ops-cli.md) 已归档，T1–T12 `passing`。
- T11/B3 的正式模型、受控清理、完整重建、manifest 校验、launcher 修复和正式窄 Docker build 均已取得限定 Accept；历史冻结 `full` 与真实 HA 也已通过。旧冻结文档的精确差异未审定，保留历史缺口；当前以新完整快照、新 full／HA 和独立完整 Review 建立新验收时点，不追认旧状态迁移。
- 最小代理接入已完成：`full` 仅 HA 子阶段固定既有 `HTTPS_PROXY=http://127.0.0.1:7890`，主模型/Maven 保留三键。launcher 隔离回归、独立 Review、正式窄 build、冻结 full 和真实 HA 原件分别见活跃计划与 `.harness/t11/launcher-stdin-20261106/`、`.harness/t11/launcher-build-20261106/`、`.harness/verifications/full.json`、`.harness/verifications/routing-ha.json`。
- B4、新冻结 full／真实 HA、完整 T11 Review 及精确状态 Review 均已通过，原件见 `.harness/t11/b5-final-20261106/`。T12 正式 quality 已在完整 evidence allowlist 上取得 `Accept`；早期 README-only Revise 与旧取证缺口作为历史保留，当前合同回归已通过。
- 机器证据由 `.harness/report.json` 与 `.harness/t11/` 原件拥有；不提交、不 push、不修改用户 Docker 配置，不自动清理用户数据、旧证据、镜像或缓存。
- 空间清理与第三方退役材料整理已记录；保留法律文件、provenance 和原始验收证据。T11 与 T12 均已关闭，正式 quality 与状态迁移 Review 为 `Accept`。
- 精简前的入口原文见[历史快照](docs/exec-plans/history/2026-11-06-active-index-progress-before-trim.md)；当前恢复步骤由活跃计划拥有。
