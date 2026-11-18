# Broker 成员应用流程可读性实施计划

## Sprint Contract

- 目标：应用服务直接表达成员和 learner 流程，执行器不接收整个 AppService，不为可读性增加协作类。
- 范围：`im-broker/im-broker-server` 内成员应用服务、许可执行器、相关行为测试与模块说明。
- 非目标：不修改协议、持久化格式、Permit 身份、4096 永久预算、角色门禁、UNKNOWN 恢复策略、线程数量或调度优先级；不提交 Git。
- 验收标准：消除 Executor 对具体 AppService 的引用；保留既有重试、共享 Future、generation 与排空行为；聚焦回归、clean/full 和独立 Review 均通过后才关闭。
- 风险：此前会话修改了成员 operation 调度，聚焦通过不代表保留全部预算、generation、完成发布约束；必须补充检查，不能只按类名或方法名认定完成。
- 环境：JDK 21、Maven、Docker；readiness 已运行通过。不更改用户数据或环境配置。

## 事实源

- 设计或产品规格：[已批准治理设计](../../design-docs/2026-10-28-official-jraft-governance-simplification-design.md)：Permit、永久预算、唯一 worker、确认等待与 learner 阶段约束。
- [根 Architecture](../../../ARCHITECTURE.md)、[Broker Architecture](../../../im-broker/ARCHITECTURE.md)。
- [Broker Server README](../../../im-broker/im-broker-server/README.md)、[编码规范](../../references/CODING_GUIDE.md)、[测试规范](../../references/UNIT_TEST_GUIDE.md)。
- 本轮为上述契约下的内部重构，不授权改变执行保证；若必须改变约束，停止并另行设计 Review。

## 验证分层

| 层级 | 命令 | 当前结果 |
|---|---|---|
| 环境 | `./scripts/verify.sh readiness` | 通过 |
| 新任务 API | `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipPermitExecutorTest -Dsurefire.failIfNoSpecifiedTests=false test` | RED 为旧签名不接受任务输入；GREEN 通过，38 个场景 |
| 成员回归 | `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipPermitExecutorTest,BrokerMembershipWorkflowTest,BrokerMembershipPermitAdmissionTest,BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过；执行器 41、Workflow 75、Admission 16，变更服务回归通过 |
| 模块测试 | `mvn -pl im-broker/im-broker-server -am test` | 最终 guard 后于 2026-11-09 14:16:22 `BUILD SUCCESS`，耗时 5 分 15 秒 |
| 最终门禁 | `./scripts/verify.sh clean`、`./scripts/verify.sh full` | clean 通过；E2E 8/8 与独立 Review Accept；full Maven/Harness 通过，routing-ha Docker Hub 镜像拉取超时 |
| 独立 Review | T1/T1 增量 Review | Accept；full 仍受 routing-ha 外部镜像网络阻断 |

## 任务状态

| ID | 目标 | 状态 | 证据与备注 |
|---|---|---|---|
| T1 | 诊断与重传改为任务输入 | passing | 最终冻结 full 与真实 HA 通过；独立 quality Accept |
| T2 | learner 状态与流程回收 | passing | 真实 CLI 后台恢复、Broker 重试身份修复及同轮冻结验收通过 |
| T3 | 既有成员迁移复核及整体验收 | passing | 成员契约复核、窄修复回归与最终冻结验收完成 |

## 详细任务

### T1 诊断与重传任务输入

准确文件范围（均相对 `im-broker/im-broker-server/`）：

- `src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipPermitExecutor.java`
- `src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipAppService.java`
- `src/test/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipPermitExecutorTest.java`
- `src/test/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipWorkflowTest.java`：模块回归暴露旧成员缓存问题后，补充显式重试断言。
- `src/test/java/com/co/kc/imchat/broker/adapter/cluster/BrokerRaftAdapterTest.java`：恢复模块回归暴露自缩减后写入断言失败；仅补充结果诊断，未改断言条件。
- `README.md`

full 阻断诊断的增量范围：`im-management/im-ops-cli/src/main/java/com/co/kc/imchat/management/ops/cli/model/io/IamTokenResponse.java`、`im-management/im-ops-cli/src/test/java/com/co/kc/imchat/management/ops/cli/http/OpsCliClientTest.java`、`im-management/im-ops-cli/README.md`、`im-test/im-e2e-test/src/test/java/com/co/kc/imchat/e2e/support/OpsCliGovernanceTopology.java`、`im-test/im-e2e-test/src/test/java/com/co/kc/imchat/e2e/OpsCliGovernanceE2ETest.java`。只显式接纳 OAuth 标准可选 `scope`，不放宽全局 JSON 校验；E2E 只保留 HTTP 状态观察，不记录凭据或响应体。客户端禁用测试保留真实 401 断言，改为匹配 CLI 已有清除登录态行为。

- [x] 单个真实登录 E2E 复现 `TOKEN_EXCHANGE`；脱敏观察证明 HTTP 200、标准 scope、无 refresh token、合法 TTL/Bearer。
- [x] 新增 `OpsCliClientTest#standardScopeFieldIsAcceptedInTokenResponse`，旧模型报 `Invalid IAM token response`；增加显式 scope 后 OpsCliClientTest 全组通过。证据 `/tmp/token-scope-red.log`、`/tmp/token-scope-green.log`。
- [x] 运行 `mvn -q -pl im-test/im-e2e-test -am -Dtest=OpsCliGovernanceE2ETest -Dsurefire.failIfNoSpecifiedTests=false test`，最终 8/8 通过；OpsCliClientTest 60/60 通过。增量独立 Review Accept；随后重新 clean/full。

步骤：

- [x] 在 ExecutorTest 添加诊断 Runnable 合并/角色失效、重传 Supplier 在途共享/一次调用的行为测试。
- [x] 运行“新任务 API”命令，旧签名因不能接受 Runnable/Supplier 而编译失败，证据 `/tmp/membership-task-input-red.log`。
- [x] 将诊断参数与保存字段改为 Runnable，将重传参数改为 Supplier；AppService 捕获固定 operation/receiver 后提交任务。
- [x] 运行新任务 API 与成员回归命令，不修改原有生命周期、预算与重传校验。
- [x] 更新 Server README 的内部协作说明；不引入新规范或类名例外。
- [x] 处理模块测试暴露的既有成员缓存错误：只共享在途结果，已完成操作重新读取 committed 状态；成功重试保持 DUPLICATE，UNAVAILABLE 允许显式重试。现有两个 DUPLICATE 回归先失败，修复后通过。
- [x] 复核恢复模块曾报告的 `selfReductionDrainsExactOldPermitOnNewLeaderWithoutLateConfigurationRollback(false)`；最终 guard 后完整 Broker reactor 于 2026-11-09 14:16:22 通过，未复现该失败。
- [x] 复核同步 native 异常：原先 pending 符合批准的 UNKNOWN 阻塞契约；撤回新增完成路径，保留未排空许可。正常及永久 fault 后均不发布完成的回归通过。
- [x] 修复本地 native confirmation 在缺失 reservation 时创建事实槽：新增严格接缝，远程 fact confirmation 原契约保持不变；新增回归通过。
- [x] 完成 Broker 模块回归、clean、CLI 60/60、治理 E2E 8/8 及独立 Review。
- [x] 按用户 Review 要求整理 `BrokerMembershipAppService.java`：常量前置，对外用例集中在前，内部执行流程随后，record 与状态内部类置底；保留方法名、可见性和方法体。readiness、声明/方法体等价检查、全文件行内容守恒检查、`git diff --check -- im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipAppService.java` 及 `./scripts/check-java-style.sh` 通过。按用户要求不重跑 full/HA；此前测试是排序前证据，不代表本次新验收。
- [x] 最终 `./scripts/verify.sh full` 与真实 HA 已通过，见恢复状态。
- 历史未勾选记录：完成最终 `./scripts/verify.sh full`；当前 Maven/Harness 阶段通过，routing-ha 因 Docker Hub 无法拉取 `eclipse-temurin:21.0.12_8-jre-jammy` 阻断。

### T2 learner 状态与流程回收

本轮用户批准单个具体成员执行Adapter方案，取代application接口切片；独立复核Revise修正清单 `/tmp/executor-concrete-adapter-design-check.txt` 为实施依据。
- [x] 原生调度片：准确范围BrokerRaftAdapter.java及BrokerRaftAdapterTest.java，移除额外membershipExecutor线程池，调用线程同步进入Runtime，保留真实只读完成观察/异常未知；补线程/同步返回回归先RED，再跑Adapter及成员六组。
- [x] 边界片：原DefaultBrokerMembershipExecutor.java/application接口、AppService、BrokerMembershipTransformer.java及对应测试，新增领域确认报告/结果与技术准入结果，保留所有认证envelope字段的去重；移至adapter.cluster.membership具体BrokerMembershipExecutor，删除同形接口/Default名称。配置与直接构造Broker/routing-ha测试同步，ModuleBoundary恢复禁止应用依赖规则，不放宽LayerBoundary。
- [x] `BrokerMembershipExecutor` 可读性收尾：整理import、全限定函数类型、容量命名、executionUnavailable谓词、capacity/identity局部判定、readyPermit/learnerStage命名，并将worker待处理判断收为有明确职责的`hasPendingWork`/`findReadyPermit`/`isReadyPermit`。不改变锁边界、阶段优先级、队列和完成顺序。`/tmp/executor-close-tests.log`聚焦通过，`/tmp/executor-close-broker-module.log`完整Broker模块通过，`/tmp/executor-close-architecture.log`完整架构模块通过，style/diff通过。
- [x] SDK 结果类型职责迁移：`MembershipPermitDrainedResponse` 重命名为 `MembershipPermitDrainedResult`，从 `sdk.model.response` 迁至 `sdk.model.result`，同步 Server、routing-ha、SDK 测试、README、设计和计划引用；wire 字段/status/协议不变。`/tmp/membership-result-rename-tests.log` 相关测试通过，style/diff通过。
- 历史未勾选记录：文档Architecture/Server README/设计/Harness Guide及矩阵同步；独立实现Review、clean/full按窄到宽。

本轮证据：同步入口线程回归先RED `/tmp/concrete-executor-native-red.log`，最小实现后GREEN `/tmp/concrete-executor-native-green.log`；最终架构44项/成员及映射聚焦通过，routing-ha编译通过 `/tmp/concrete-executor-final-focused.log`。新模型MembershipExecutionReport保留11字段，MembershipConfirmationResult为领域结果；MembershipConfigurationResult和MembershipExecutionAdmissionResult为技术观察。首轮源码Review `/tmp/concrete-executor-review.txt` 为Revise：ACCEPTED映射APPLIED虽当前只在拒绝分支调用，仍语义错误；已改为THROW_EXCEPTION，补三态准入及五态确认往返回归，聚焦通过 `/tmp/concrete-executor-review-green.log`，style/diff通过。迁移初期编译/fixture SDK与domain类型断言错误及错误码自动映射遗漏均修复，原日志保留，不计为生产RED。全Peer→真实Handler→真实AppService组合、纯envelope差异专属确认回归未新增，不冒称运行全覆盖；新full/HA未执行。修复后独立窄复审Accept/findings none（`/tmp/concrete-executor-review2.txt`），原Revise保留；clean通过（`/tmp/concrete-executor-clean.log`），仅局部验收，不关闭T2。不新增Coordinator或转发Adapter，不拆单调用流程方法，不改预算/generation/UNKNOWN与取消隔离。

历史实现方案已被后续具体Adapter方案取代：当前 `adapter.cluster.membership.BrokerMembershipExecutor` 为唯一实现，不保留 application 执行接口或 `Default` 实现；`BrokerRaftAdapter` 返回 FollowingObservation，配置及所有直接构造同步。ModuleBoundary禁止执行器依赖 application/CQRS/SDK/Raft core/model，LayerBoundary保持不变。原 `/tmp/executor-contract-design-review.txt` 只保留为被取代方案的历史记录。

- [x] 实现切片及首轮Workflow/Executor/Configuration通过（`/tmp/executor-contract-compile.log`）。`mvn -q -pl im-test/im-architecture-test,im-test/im-routing-ha -am test '-Dtest=LayerBoundaryTest,ModuleBoundaryTest,BrokerMembershipExecutorTest,BrokerMembershipWorkflowTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest,BrokerClusterConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false`退出0（`/tmp/executor-contract-regression.log`），包含routing-ha testCompile；旧full两项架构失败不被改写。
- 历史未勾选记录：同步当前规范/README/Architecture及实现Review；clean/full未重新执行，不标passing、不提交。接口抽取不拆mutex/账本/预算/队列，不修改LayerBoundary；native模型沿用既有Plugin契约，不新增业务规则。

本轮架构恢复采用用户批准的分片：第一片仅收回私有流程状态，不改执行器、不新增Coordinator/Adapter/抽象类；第二片先核对职责和原子性，再决定具体迁移，不将标准Executor字段替换冒充全部修复。

- [x] 第一片准确范围：`im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipAppService.java` 内联private sealed LearnerConfirmationState及原Prepared/Finished，删除 `model/execution/LearnerConfirmationState.java`，字段和两个流程调用不改。
- [x] `mvn -q -pl im-test/im-architecture-test -am test '-Dtest=LayerBoundaryTest' -Dsurefire.failIfNoSpecifiedTests=false`：30项仅剩1项应用依赖infrastructure失败，model分组违规消失（`/tmp/learner-state-layer-redgreen.log`）；旧full两项失败为本片前置RED。`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipExecutorTest,BrokerMembershipWorkflowTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false`通过（`/tmp/learner-state-inline-regression.log`），不冒充完整架构通过。
- [x] 第二片调用职责核对：纯技术部分是worker/队列/pump/关闭及锁外通知；现有submitApplicationStage还混合following拒绝/永久槽准入/generation，不能原样称纯调度。reserveLearnerExecution把永久预算、generation与单pending捆绑；reserve/execute/localDrainedResult/nativeConfirmationExecutor属于本机Permit执行能力，和role撤销/原生进入共用mutex；confirm及retransmitDrained同时持有共享完成源和成员身份。领域已提交规则/请求占用仍由现有聚合及MembershipOperationService拥有，不重复建立attempt表。
- 历史未勾选记录：第二片安全落位待明确：标准Executor只能替代调度，不可强转或复制全部方法造接口。不得拆开“检查资格→登记排队能力→原生进入”的原子性，不得让旧generation continuation执行、回调直接调用Node或锁内通知。暂不新增MembershipExecutionState/Coordinator/Adapter，不迁包、不改变4096永久预算/单worker/优先级；具体方案经用户确认及独立设计Review后再实施。

本轮 Review 缺陷修复增量（用户要求修复，不扩展其他重构）：CLI 五个成员写操作均显式保存并原样传递 membershipEndpoint；旧缺端点记录拒绝且不修改文件。权限目录仅本轮 ACTIVE → INACTIVE 时撤销 audience 授权，不因历史停用记录重复撤销。

- [x] RED：修改 CLI `OpsCliClientTest` 五动作保存/发送/retry 矩阵及 Ops `OpsCliControllerTest` 端点保真断言；IAM `ApplicationPermissionAppServiceTest` 增加连续撤回同步仅撤销一次回归。命令：`mvn -q -pl im-management/im-ops-cli,im-management/im-ops,im-management/im-iam/im-iam-server -am test '-Dtest=OpsCliClientTest,OpsCliControllerTest,ApplicationPermissionAppServiceTest' -Dsurefire.failIfNoSpecifiedTests=false`。
- [x] 最小实现范围：CLI `OpsCli.java`、`operation/OperationRequestFile.java`、`http/OpsCliClient.java`；Ops `interfaces/http/OpsCliController.java`；IAM `domain/authorization/service/ApplicationPermissionService.java` 及 `application/ApplicationPermissionAppService.java`。同步对应 CLI 三组测试、Ops Controller 测试、IAM 应用测试及真实 `OpsBrokerManagementContractE2ETest`。
- [x] GREEN：上述聚焦命令加 `OpsCliTest,OperationRequestFileTest`；真实组合：`mvn -q -pl im-test/im-e2e-test -am test '-Dtest=OpsBrokerManagementContractE2ETest' -Dsurefire.failIfNoSpecifiedTests=false`。稳定文档范围：CLI/Ops/IAM Server README、CLI 认证设计、Harness Guide 的既有协议/幂等规则；不新增 checker 或放宽规则。
- 历史未勾选记录：最终验收未完成：`./scripts/verify.sh clean` 因既有 T1 `pending_acceptance` 非法状态退出 1（`/tmp/im-fix-clean.log`），未继续 full，不改写旧状态、不弱化规则。独立增量 Review 未取得，真实 MySQL 测试因未启用专属环境跳过；不关闭 T2、不提交。

本轮证据：IAM 回归修复前因重复 revoke 的 `TooManyActualInvocations` 失败（`/tmp/im-fix-red.log`）；CLI 五动作回归修复前 promote/demote/remove 因端点被拒绝构造而失败（`/tmp/im-fix-cli-red.log`）。Ops Controller 保真断言为实现后补充，无独立 RED。最终聚焦 `mvn -q -pl im-management/im-ops-cli,im-management/im-ops,im-management/im-iam/im-iam-server -am test '-Dtest=OpsCliTest,OperationRequestFileTest,OpsCliClientTest,OpsCliControllerTest,ApplicationPermissionAppServiceTest,ApplicationPermissionDeleteTest,IamAuthorizationInvalidationTransactionTest' -Dsurefire.failIfNoSpecifiedTests=false` 通过，206 执行／7 MySQL 跳过（`/tmp/im-fix-final-tests.log`）。真实组合命令通过12/12（`/tmp/im-fix-e2e.log`），新增三动作经真实 Broker 返回 CONFLICT 而非缺端点错误；只替换发现，Controller直接调用不证明完整 CLI OAuth 成功迁移。style／diff check通过。首轮GREEN旧fixture缺端点六项失败已同步，原日志保留 `/tmp/im-fix-green1.log`；不是生产修复RED。Harness Guide/矩阵记录既有边界规则的窄自动回归，不新增框架。

- [x] 用户批准补充 BrokerCluster 方法与步骤注释；准确范围 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/domain/cluster/model/BrokerCluster.java`、本计划及 `PROGRESS.md`。补成员许可、只读执行确认、Commit、阶段推进、fence、排空进度与完成行为的 Javadoc，以及观察资格、许可登记、拓扑提交、阶段推进和排空收尾的关键步骤块注释；不为简单 getter 添加说明。与 `/tmp/broker-cluster-comments-before.java` 比较，去除注释和空白后的 Java token 序列完全一致；readiness、Java style（`/tmp/broker-cluster-comments-style.log`）及 diff check 通过。纯注释增量，未重跑行为测试或取得独立 Review；此前208/208属于上一实现增量，无新通用规范。
- 历史未勾选记录：本轮 clean 仍因既有 T1 `pending_acceptance` 阻断（`/tmp/broker-cluster-comments-clean.log`），未继续 full/HA、不关闭 T2、不提交。

- [x] 用户批准 BrokerCluster 内部可读性整理；准确范围 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/domain/cluster/model/BrokerCluster.java`、本计划及 `PROGRESS.md`。Builder 置底，意图登记先于 Commit，公开行为集中、私有规则随后；展开成员阶段守卫和 revision 两类提交条件，findObservers 改为逐节点失败返回，复用既有未完成成员谓词并整理 import。不新增类型或包装，不改 Permit 全历史扫描、公开签名、状态与拒绝文案；未建立新通用工程规则。
- [x] `./scripts/verify.sh readiness`、`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerClusterTest,BrokerClusterConsistentVotersTest,BrokerMembershipPermitTest,BrokerTopologyIsolationTest,BrokerMembershipWorkflowTest,BrokerClusterAppServiceChangeTest' -Dsurefire.failIfNoSpecifiedTests=false` 六组208/208（`/tmp/broker-cluster-readability-tests.log`）、`./scripts/check-java-style.sh` 与 `git diff --check` 通过。纯重构沿用既有行为断言，未执行缺陷 RED，未取得独立增量 Review。
- 历史未勾选记录：`./scripts/verify.sh clean` 仍被既有 T1 `pending_acceptance` 阻断（`/tmp/broker-cluster-readability-clean.log`），未继续 full/HA，不关闭 T2、不提交。

- [x] 用户批准全类Review六项：准确范围BrokerMembershipAppService（公开方法集中、import/说明、恢复单次分流、stored合并、完成局部outcome、四私有名）、LearnerConfirmationState删除Outcome、BrokerMembershipExecutorTest旧反射字符串同步，设计/Server README/本计划/PROGRESS。`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false` 六组205/205（`/tmp/readability-all-tests.log`），readiness/style/diff check通过。独立Review对照精确AppService前快照 `/tmp/readability-all-before.java` 为Accept（`/tmp/readability-all-review.txt`），仅本增量、非独立测试执行或T2整体批准；无新通用规范。
- 历史未勾选记录：clean结果见`/tmp/readability-all-clean.log`，旧验收缺口保留，不继续full/HA、不关闭T2。


- [x] 用户批准确认准备守卫整理；准确范围BrokerMembershipAppService、MembershipOperationService（删除matchesExecutionReceiver）、MembershipOperationServiceTest、BrokerMembershipPermitAdmissionTest，SDK MembershipPermitDrainedResult及新增同名Test，本计划/PROGRESS。展开上报者/cluster/receiver三段，固定拒绝用工厂，保留短路/状态/文案。`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=MembershipPermitDrainedResultTest,MembershipOperationServiceTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipWorkflowTest,BrokerObservationRequestConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false`通过（`/tmp/confirmation-guards-tests2.log`），readiness/style/diff check通过；首次测试注解未限定编译失败已修，不计行为RED。新增cluster/receiver拒绝零后续交互和工厂字段测试，未取得本增量独立Review，无新通用规范。
- 历史未勾选记录：clean结果见`/tmp/confirmation-guards-clean.log`，未继续full/HA，T2保持active。


- [x] 用户批准仅明确learner执行类型：准确范围BrokerMembershipExecutor（现有LearnerExecution公开final/私有构造、reserve返回具体类型、说明）、BrokerMembershipAppService（confirm/resume局部execution及注释）、Server README、本计划/PROGRESS。不新增包装/finish接口、预算或生命周期状态；调度与失效回调不变。`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipExecutorTest,BrokerMembershipWorkflowTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false` 六组203/203（`/tmp/learner-execution-type-tests.log`），readiness/style/diff check通过。纯类型与命名整理，未执行缺陷RED或新增独立Review；本轮clean在drift阶段120秒超时未取得结论（`/tmp/learner-execution-type-clean.log`），此前pending_acceptance阻断为历史未解决项，不继续full/HA，不关闭T2。


- [x] 用户批准应用公开入口统一为confirmExecution／submitExecutionConfirmation／sendExecutionResult；准确范围BrokerMembershipAppService、MembershipPermitDrainedHandler、BrokerMembershipWorkflowTest、BrokerMembershipPermitAdmissionTest、BrokerObservationRequestConfigurationTest、BrokerRaftAdapterTest，以及routing-ha AutomaticFailureTlsHaTest、Server README、本计划/PROGRESS。只重命名及注释，方法体/Adapter/Executor/SDK/wire不变，不留包装。`mvn -q -pl im-broker/im-broker-server,im-test/im-routing-ha -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipPermitAdmissionTest,BrokerObservationRequestConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false` 通过（`/tmp/execution-api-rename-tests.log`），含routing-ha testCompile；readiness/style/diff check通过，Java应用旧名调用无残留。未运行真实HA或新增独立Review，clean仍原pending_acceptance阻断（`/tmp/execution-api-rename-clean.log`），不继续full、不关闭T2。


- [x] 用户批准继续收紧submitMembershipOperation为private，准确范围BrokerMembershipAppService及BrokerMembershipWorkflowTest、本计划/PROGRESS。测试从resume公开入口覆盖following准入/摘要冲突/worker重读，从demoteVoter/resume/scheduler验证执行器submitApplicationStage边界；不反射私有方法、不加兼容包装。Workflow87/87（`/tmp/private-submit-workflow.log`）；原spy私有入口替换为执行器真实协作边界mock，方法体不变。
- [x] `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false` 三组135/135（`/tmp/private-submit-tests.log`）、readiness/style/diff check通过，测试无submitMembershipOperation引用，无新增通用规范。
- 历史未勾选记录：独立Review调用240秒超时（`/tmp/private-submit-review.txt`），未取得结论；clean原T1 pending_acceptance阻断（`/tmp/private-submit-clean.log`），不继续full/HA、不关闭T2。


- [x] 成员投递入口更名：BrokerMembershipAppService及BrokerMembershipWorkflowTest中executeMembershipOperation→submitMembershipOperation，方法体不变，从public收紧包内可见。`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false` 三组135/135（`/tmp/submit-member-tests.log`），readiness/style/diff check通过。当前Workflow仍直接调用及spy该隔离点，未新增反射/兼容包装；private收紧尚未完成，需迁移公开用例测试后再处理。无新通用规则，未独立增量Review；clean原阻断保留（`/tmp/submit-member-clean.log`），不继续full/HA，不关闭T2。


- [x] 用户批准执行确认值对象/只读检查，设计Review `/tmp/execution-value-design-review.txt`。准确范围新增领域MembershipExecutionConfirmation；BrokerCluster拆check/confirm共享规则，AppService只读检查，FSM重新校验并应用，BrokerMembershipTransformer新增生成映射；同步BrokerMembershipPermitTest／BrokerClusterTest／BrokerTopologyIsolationTest／BrokerMembershipPermitAdmissionTest／BrokerMembershipExecutorTest／BrokerMembershipTransformerTest，设计/README/本计划/PROGRESS。不保留七参旧领域入口，wire不变。
- [x] `mvn -q -pl im-test/im-architecture-test -am test '-Dtest=BrokerMembershipPermitTest,BrokerClusterTest,BrokerTopologyIsolationTest,BrokerMembershipTransformerTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipExecutorTest,BrokerMembershipWorkflowTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest,ModuleBoundaryTest' -Dsurefire.failIfNoSpecifiedTests=false` 退出0（`/tmp/execution-value-tests.log`），readiness/style/diff check通过；源码Review Accept（`/tmp/execution-value-code-review.txt`），非精确增量diff或独立测试。首轮testCompile漏newLeader调用已修（`/tmp/execution-value-unit.log`），非行为RED。新增只读无变更/预检后状态变化应用重验/重复revision/映射字段测试；真实AppService→FSM交错待增强，无新增通用规范。
- 历史未勾选记录：clean仍既有T1 pending_acceptance阻断（`/tmp/execution-value-clean.log`），未继续full/HA，不关闭T2。


- [x] 执行确认准入决定收拢：用户批准，设计Approved（`/tmp/confirmation-admission-design-review.txt`），源码Review Accept（`/tmp/confirmation-admission-code-review.txt`），非精确增量diff或独立测试执行。准确范围新增领域 `MembershipExecutionAdmission.java`，修改 MembershipOperationService／BrokerMembershipAppService／MembershipDrainSelectionTest、设计/README/本计划/PROGRESS。领域服务定位当前执行记录并返回状态/permit/reason；AppService保留分段身份短路，提交与仅执行器异常映射归submitExecutionConfirmation，worker完整验证不变，不新增转发服务。
- [x] `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=MembershipDrainSelectionTest,MembershipOperationServiceTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerObservationRequestConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false` 通过（`/tmp/confirmation-admission-tests.log`），readiness/style/diff check通过。新增operation/step缺失及存在当前记录测试，未执行缺陷RED。step存在无permit、非法Admission组合及完整身份零交互矩阵为非阻断增强项，无新增通用规则或Harness例外。
- 历史未勾选记录：clean既有T1 pending_acceptance阻断保留（`/tmp/confirmation-admission-clean.log`），不继续full/HA，不关闭T2。


执行结果确认可读性：用户批准，限定设计Review `/tmp/execution-confirm-design-review.txt`。准确范围 Broker Server `application/cluster/BrokerMembershipAppService.java`（私有prepareExecutionConfirmation/confirmMembershipExecution、注释、调用）、`domain/cluster/service/MembershipOperationService.java`（显式领域身份谓词）、`domain/cluster/model/BrokerClusterOperation.java`（currentPermit查询）、测试 MembershipOperationServiceTest／MembershipDrainSelectionTest，设计/本计划/PROGRESS及Server README。公开API/wire不改，聚合已有结果校验直接复用，不套转发服务。

- [x] 将身份关系归领域、operation定位当前执行许可；保留分段短路及worker/证据/提交复验位置。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=MembershipOperationServiceTest,MembershipDrainSelectionTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerObservationRequestConfigurationTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false` 九组220/220（`/tmp/execution-confirm-tests.log`），readiness/style/diff check通过。限定实现Review Accept（`/tmp/execution-confirm-code-review.txt`），非精确增量diff或独立测试；全部拒绝路径零交互矩阵仍可增强。纯职责整理无缺陷RED，稳定README/设计/PROGRESS同步，无新增通用规范或Harness例外。
- 历史未勾选记录：clean仍既有T1 pending_acceptance阻断（`/tmp/execution-confirm-clean.log`），未继续full/HA，不关闭T2。


- [x] 用户批准仅将 Future 等待句柄入口更名 submitMembershipPermitDrainedConfirmation，准确范围 Broker Server `application/cluster/BrokerMembershipAppService.java`、`interfaces/handler/broker/MembershipPermitDrainedHandler.java`、`src/test/java/.../application/cluster/BrokerMembershipPermitAdmissionTest.java`、`infrastructure/config/BrokerObservationRequestConfigurationTest.java`，本计划与PROGRESS。方法体/固定等待桥/取消语义不变，不加兼容包装。`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipPermitAdmissionTest,BrokerObservationRequestConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false`、readiness/style/diff check通过（`/tmp/permit-confirm-rename-tests.log`），Java旧名搜索无残留；历史文档保留原API名。clean原阻断保留（`/tmp/permit-confirm-rename-clean.log`），不继续full/HA，不关闭T2。


扫描与重传规则收拢：用户批准，限定设计Review见 `/tmp/member-rules-design-review.txt`。准确范围 Broker Server `domain/cluster/model/BrokerCluster.java`（状态/端点/接收资格）、`BrokerClusterOperation.java`（步骤查找/结构扫描）、`domain/cluster/service/MembershipOperationService.java`（本机事实选择），`application/cluster/BrokerMembershipAppService.java`（调用与事实采样），新增 `src/test/java/com/co/kc/imchat/broker/domain/cluster/service/MembershipDrainSelectionTest.java`；设计、Broker Architecture／Server README、本计划与PROGRESS。

- [x] 收回聚合查询与完整结构检查，领域服务只接领域事实Map，AppService保留worker/请求组装/选定后及发送前复验。
- [x] 窄命令 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=MembershipDrainSelectionTest' -Dsurefire.failIfNoSpecifiedTests=false` 3/3（`/tmp/member-rules-unit2.log`），覆盖pending优先不回退、最高已确认、非选中矛盾、缺证明、Builder alias结构反例。首次fixture缺minimumVoterCount已补，非生产RED。
- [x] 运行 `mvn -q -pl im-test/im-architecture-test -am test '-Dtest=MembershipDrainSelectionTest,BrokerClusterTest,BrokerMembershipPermitTest,MembershipOperationExecutionTest,MembershipOperationServiceTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest,ModuleBoundaryTest' -Dsurefire.failIfNoSpecifiedTests=false` 最终业务/领域279/279、ModuleBoundary14/14（`/tmp/member-rules-final.log`），readiness/style/diff check通过。首轮回归missing-binding旧getter stub失效（`/tmp/member-rules-regression.log`），改为新predicate应用分支fixture，并补真实BrokerCluster规则测试。
- [x] 独立实现首轮Revise/P2保留（`/tmp/member-rules-code-review.txt`），因接收资格缺真实规则测试；补缺绑定/voter/learner-only/非目标及副本测试后第二轮Accept（`/tmp/member-rules-code-review2.txt`），非精确增量diff或独立运行。稳定说明/设计/PROGRESS同步；无新通用规则，不改Harness例外。准确新增文件范围增加 `src/test/java/com/co/kc/imchat/broker/domain/cluster/model/BrokerClusterTest.java`、修改 `application/cluster/BrokerMembershipWorkflowTest.java`。
- 历史未勾选记录：clean仍原T1 pending_acceptance阻断（`/tmp/member-rules-clean.log`），未继续full/HA，不关闭T2。专属多错误组合及采样后故障完整矩阵未新增，不冒称全部组合覆盖。


成员资源库收尾：用户批准方向，限定设计 Approved（`/tmp/member-repository-design-review.txt`）。准确范围新增 Broker Server `domain/cluster/model/MembershipOperation{Outcome,Claim}.java`、`domain/cluster/repository/MembershipOperationAttemptRepository.java`、`infrastructure/domain/repository/InMemoryMembershipOperationAttemptRepository.java`，修改领域 Attempt／Service、AppService、BrokerClusterConfiguration、BrokerMembershipTransformer；删除执行层 Attempts／Claim 和旧协调测试；新增 `domain/cluster/service/MembershipOperationExecutionTest.java`，修改 MembershipOperationServiceTest、TransformerTest、ConfigurationTest 及全部直接构造 Workflow／Executor／PermitAdmission／Drift／AppServiceChange／Scheduler／RaftAdapter、routing-ha AutomaticFailureTlsHaTest；设计、Broker Architecture／Server README、本计划和 PROGRESS。

- [x] AppService 只依赖统一服务管理尝试，两张独立内存资源库；领域 Attempt 的固定 Future 只承载自有 Outcome，边界 MapStruct 映射，不导入 Adapter/CQRS，不引入第二张结果表。
- [x] 窄 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=MembershipOperationExecutionTest,MembershipOperationServiceTest' -Dsurefire.failIfNoSpecifiedTests=false` 15/15（`/tmp/member-repository-unit.log`），原协调9场景迁入领域执行测试，不改为弱断言。
- [x] 运行 `mvn -q -pl im-test/im-architecture-test,im-test/im-routing-ha -am test '-Dtest=MembershipOperationExecutionTest,MembershipOperationServiceTest,InMemoryLearnerConfirmationAttemptRepositoryTest,BrokerMembershipTransformerTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest,BrokerClusterConfigurationTest,ModuleBoundaryTest' -Dsurefire.failIfNoSpecifiedTests=false`，最终业务/配置246/246、ModuleBoundary14/14、routing-ha testCompile 通过（`/tmp/member-repository-final.log`）；readiness/style/diff check通过。首轮旧公开Stage身份断言失败（`/tmp/member-repository-regression.log`），按获审契约改为在途状态；内部固定视图/共享执行回归及新增映射取消隔离/全状态保真测试补足，不把pending单独当共享证明。补8线程唯一认领与同结果回归。
- [x] 限定源码Review Accept（`/tmp/member-repository-code-review.txt`），findings none，非精确增量diff/独立测试执行；新增并发测试为Review后补、已复验未单独复核。完整生产注入图与删除后通知前专属交错为非阻断增强项。稳定文档同步，无新增通用工程规则，不改Harness例外。
- 历史未勾选记录：clean仍因既有T1 pending_acceptance失败（`/tmp/member-repository-clean.log`），未继续full/HA，不关闭T2。公共映射Stage/结果包装不保证对象身份，内部固定领域共享视图保持；原full/HA缺口不变。


领域服务合并：用户批准，限定设计 Approved（`/tmp/merge-membership-service-design-review.txt`）。准确范围统一 `domain/cluster/service/MembershipOperationService.java`、删除旧 LearnerConfirmationService，AppService 与 BrokerClusterConfiguration；所有直接构造测试 Workflow／Executor／PermitAdmission／Drift／AppServiceChange／Scheduler／RaftAdapter、routing-ha AutomaticFailureTlsHaTest，重命名旧服务测试为 MembershipOperationServiceTest，协调对象测试和配置测试；设计、Broker Architecture／Server README、本计划和 PROGRESS。只合并服务，两套存储与契约保持独立，成员资源库化继续未完成。

- [x] 直接合入 learner claim/release 并改为明确动作名，生产单一服务 Bean 注入 AppService 与成员协调对象，无转发门面或兼容构造。
- [x] 三组窄测 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=MembershipOperationServiceTest,MembershipOperationAttemptsTest,BrokerClusterConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false` 通过（`/tmp/merge-membership-service-unit.log`），新增同 ID 两类占用互不影响回归。
- [x] 运行 `mvn -q -pl im-test/im-architecture-test,im-test/im-routing-ha -am test '-Dtest=MembershipOperationAttemptsTest,MembershipOperationServiceTest,InMemoryLearnerConfirmationAttemptRepositoryTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest,BrokerClusterConfigurationTest,ModuleBoundaryTest' -Dsurefire.failIfNoSpecifiedTests=false` 退出0，业务/配置234/234、ModuleBoundary14/14、routing-ha testCompile 通过（`/tmp/merge-membership-service-regression.log`）；readiness/style/diff check 通过。Java 源码检索无旧服务残留。
- [x] 限定独立实现 Review Accept（`/tmp/merge-membership-service-code-review.txt`），无必修 findings；非精确增量 diff 或独立测试。生产配置静态证明共用服务 Bean，完整 AppService 图运行证明仍待增强；部分直接构造fixture分立服务实例不作singleton证据。稳定说明同步，无新增通用规范，不改 Harness 规则。
- 历史未勾选记录：clean 仍受既有 T1 pending_acceptance 阻断（`/tmp/merge-membership-service-clean.log`），未继续 full/HA，T2保持 active；成员资源库化不在本次完成范围。


成员尝试领域化：用户批准，独立设计 Approved（`/tmp/member-domain-design-review.txt`）。准确范围 Broker Server 新增 `domain/cluster/model/MembershipOperationAttempt.java`、`domain/cluster/service/MembershipOperationService.java`，删除旧 `model/execution/MembershipOperationAttempt.java`，修改同目录 Attempts／Claim、`application/cluster/BrokerMembershipAppService.java`、`infrastructure/config/BrokerClusterConfiguration.java`；同步全部直接构造测试 Workflow／Executor／PermitAdmission／Drift／AppServiceChange／Scheduler／RaftAdapter 和 routing-ha AutomaticFailureTlsHaTest，`model/execution/MembershipOperationAttemptsTest.java`；设计、Broker Architecture／Server README、本计划与 PROGRESS。

- [x] 领域尝试保存身份／保留／回收资格，领域服务判定冲突／共享／4096 容量及保留继承；协调单表内部 Execution 绑定领域实体与 Future，状态 Claim 代替 instanceof，配置注入，无兼容构造或第二张表。
- [x] `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=MembershipOperationAttemptsTest' -Dsurefire.failIfNoSpecifiedTests=false` 8/8（`/tmp/member-domain-unit.log`），覆盖满额／同 ID 重试、未知、关联操作、锁外通知／先释放、旧回调、取消隔离与保留继承。未执行缺陷 RED，纯职责迁移。
- [x] 运行 `mvn -q -pl im-test/im-architecture-test,im-test/im-routing-ha -am test '-Dtest=MembershipOperationAttemptsTest,LearnerConfirmationServiceTest,InMemoryLearnerConfirmationAttemptRepositoryTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest,BrokerClusterConfigurationTest,ModuleBoundaryTest' -Dsurefire.failIfNoSpecifiedTests=false` 退出0，协调/领域/业务/配置 233/233、ModuleBoundary 14/14、routing-ha testCompile 通过（`/tmp/member-domain-regression.log`）；readiness/style/diff check 通过。容量边界改用真实4096，不保留测试专用生产容量构造器。
- [x] 限定实现源码 Review Accept（`/tmp/member-domain-code-review.txt`），无必修 findings；非精确增量 diff 或独立运行。专属删除后/通知前交错与完整生产注入运行证明仍为非阻断增强项，文档旧措辞已同步，无新通用规范，不改 Harness 规则。
- 历史未勾选记录：clean 仍被既有 T1 pending_acceptance 阻断（`/tmp/member-domain-clean.log`），未继续 full/HA，不关闭 T2。


用户批准 learner 认领领域化，独立限定设计 Review Approved（`/tmp/learner-domain-design-review.txt`）。准确范围：Broker Server 新增 `domain/cluster/model/LearnerConfirmation{Identity,Attempt,Claim}.java`、`domain/cluster/service/LearnerConfirmationService.java`、`domain/cluster/repository/LearnerConfirmationAttemptRepository.java`、`infrastructure/domain/repository/InMemoryLearnerConfirmationAttemptRepository.java`；删除 `model/execution/LearnerConfirmation{Attempt,Attempts,Claim}.java` 及旧 `LearnerConfirmationAttemptsTest.java`；修改 `application/cluster/BrokerMembershipAppService.java`、`infrastructure/config/BrokerClusterConfiguration.java`；所有直接构造测试 `BrokerMembershipWorkflowTest`、`BrokerMembershipExecutorTest`、`BrokerMembershipPermitAdmissionTest`、`BrokerMembershipDriftTest`、`BrokerClusterAppServiceChangeTest`、`BrokerMembershipSchedulerTest`、`BrokerRaftAdapterTest`，配置测试及 `im-test/im-routing-ha/.../AutomaticFailureTlsHaTest.java`；设计、Broker Architecture、Server README、本计划和 PROGRESS。成员共享模型本轮不改。

- [x] 领域身份／实体／状态认领结果与领域服务、单表内存资源库落地；AppService 局部 Future 保留原失效桥梁，配置注入服务，同步所有直接构造，不加兼容构造器。
- [x] 新增 `domain/cluster/service/LearnerConfirmationServiceTest.java`、`infrastructure/domain/repository/InMemoryLearnerConfirmationAttemptRepositoryTest.java`，窄命令 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=LearnerConfirmationServiceTest,InMemoryLearnerConfirmationAttemptRepositoryTest' -Dsurefire.failIfNoSpecifiedTests=false` 8/8（`/tmp/learner-domain-unit.log`）。首次回归 testCompile 漏一个全限定构造调用已修正（`/tmp/learner-domain-regression1.log`），不是行为 RED。
- [x] 运行 `mvn -q -pl im-test/im-architecture-test,im-test/im-routing-ha -am test '-Dtest=LearnerConfirmationServiceTest,InMemoryLearnerConfirmationAttemptRepositoryTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest,BrokerClusterConfigurationTest,ModuleBoundaryTest' -Dsurefire.failIfNoSpecifiedTests=false` 退出 0；九组业务/配置 225/225、ModuleBoundary 14/14、routing-ha testCompile 通过（`/tmp/learner-domain-regression2.log`）。readiness、Java style/diff check 通过；领域及资源库新增 8/8，配置组新增共享占用专测。未修改既有业务断言。
- [x] 限定实现独立 Review Accept（`/tmp/learner-domain-code-review.txt`），未取得精确增量 diff 或独立重跑测试；配置专测转调生产 factory，非完整生产 AppService 注入证明，保留非阻断增强项。设计/Architecture/README/PROGRESS 同步，无新通用工程约定，不改 Harness 规则。
- 历史未勾选记录：clean 仍因既有 T1 pending_acceptance 失败（`/tmp/learner-domain-clean.log`），未继续 full/HA；既有验收缺口不变，不关闭 T2。


用户批准确认链单次分流增量，准确范围 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipAppService.java` 的 confirmLearner、必要的 `src/test/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipWorkflowTest.java`，确认与恢复设计、本计划与 PROGRESS。恢复链、协调对象和执行器不改。

- [x] 准备后一次 Finished／Prepared 分流，新操作内层链直接传递共识结果，删除重复 Outcome 包装；保留复验、DUPLICATE 分流和最终发布门禁。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false`，六组 203/203（`/tmp/learner-direct-tests.log`），readiness、Java style/diff check 通过。复用历史只读、Begin DUPLICATE、证据失效、预算、代际与发布回归；纯职责重构未执行缺陷 RED，未新增测试或修改断言。
- 历史未勾选记录：独立增量 Review/full/HA 未取得；clean 仍因既有 T1 pending_acceptance 失败（`/tmp/learner-direct-clean.log`），不继续 full、不关闭 T2。无新增通用工程规则，不改 Harness 规则或例外。


- [x] 用户追加容量归位：`MembershipOperationAttempts.java` 内持有 4096 常量及无参生产构造器，容量参数构造器改包内可见；`BrokerMembershipAppService.java` 删除常量并无参创建，本计划与 PROGRESS 同步。运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=MembershipOperationAttemptsTest,BrokerMembershipWorkflowTest,BrokerMembershipDriftTest' -Dsurefire.failIfNoSpecifiedTests=false` 通过（`/tmp/attempt-capacity-tests.log`），readiness/style/diff check 通过。既有 clean 阻断保留，不继续 full/HA，不宣称独立 Review。

用户批准请求执行协调对象抽取。准确范围：`im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/model/execution/{MembershipOperationAttempts,MembershipOperationClaim,LearnerConfirmationAttempts,LearnerConfirmationClaim}.java`、`application/cluster/BrokerMembershipAppService.java`；测试 `src/test/java/com/co/kc/imchat/broker/model/execution/{MembershipOperationAttemptsTest,LearnerConfirmationAttemptsTest}.java` 和 `application/cluster/BrokerMembershipWorkflowTest.java`；Broker Architecture、Server README、确认与恢复设计、本计划和 PROGRESS。不迁入 domain，不改变协议、预算或发布门禁。

- [x] 将两表与认领／回收／精确完成规则封装；AppService 保留业务与调度主线。
- [x] 新增两组协调对象测试，覆盖共享／冲突／容量、身份继承、未知结果保留、释放先于通知及旧回调不删新尝试。窄命令 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=MembershipOperationAttemptsTest,LearnerConfirmationAttemptsTest' -Dsurefire.failIfNoSpecifiedTests=false` 最终 6/6（`/tmp/attempt-coordination-unit4.log`）；首次 fixture 缺 drain progress、一次枚举名错误、MinimalStage 被 AssertJ Future 重载检查抛 UnsupportedOperationException 已修正，均非生产缺陷 RED。
- [x] 运行八组聚焦回归、窄架构、Java style、diff check、clean。最终命令 `mvn -q -pl im-test/im-architecture-test -am test '-Dtest=MembershipOperationAttemptsTest,LearnerConfirmationAttemptsTest,BrokerMembershipExecutorTest,BrokerMembershipWorkflowTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest,ModuleBoundaryTest#brokerClusterExecutionMustNotDependOnApplicationWorkflows' -Dsurefire.failIfNoSpecifiedTests=false` 退出 0，八组业务 211/211、窄架构 1/1（`/tmp/attempt-coordination-final.log`）；新增协调对象 8/8，覆盖首次投递拒绝和已关联 operation 不回收。readiness、style/diff check 通过。Workflow 移除两处 Map 反射断言，改以完成后重放及非法输入后同 ID 合法执行证明释放/无残留。
- 历史未勾选记录：独立增量设计／实现 Review 与 full/HA 尚未完成；clean 仍因既有 T1 pending_acceptance 失败（`/tmp/attempt-coordination-clean.log`），未继续 full，不关闭 T2。未形成新的通用工程约定，不修改 Harness 规则或例外。


用户确认补做遗漏的 `MembershipOperationAttempt` 迁移：从 AppService 内部类移至 `model/execution`，使用 operation()/identityRetained()/retainIdentity()/completed()/result()/complete() 访问器，不改变成员操作共享、容量、身份保留或 Future 语义。此前 review 提出的 confirm 步骤注释已补；retransmitDrainedPermit 经核对仍承载完整安全扫描，不删除。

- [x] 移动并替换全部调用，删除旧内部类；运行六组成员回归、Java style、diff check，未改变行为。

用户批准共识结果固定 DUPLICATE 构造收敛：`BrokerClusterConsensusResult.duplicate(operation, message)` 替换 learner 确认中的两处裸构造；状态、关联 operation、Leader 空值和文案不变。Raft/Transformer 边界仍保留直接构造，因为它们是跨边界字段映射，不是业务固定结果工厂。

- [x] 新增 duplicate 工厂并替换两个 learner 生产调用；运行成员/结果聚焦回归、style/diff check，记录 clean/full 缺口。

用户批准 CompleteLearnerSyncResult 固定失败结果工厂整理：新增 `notLeader`、`unavailable`、带自定义原因的恢复 `rejected`，替换 AppService 中全部 learner workflow/recovery 的裸构造；保留已有无自定义文案 `rejected` 工厂，不改 Transformer 映射及状态语义。

- [x] 新增工厂及字段/状态/epoch/message 测试，替换全部 learner 生产裸构造；无对象转换，不新增 Transformer/Builder。
- [x] 运行七组成员/结果回归、Java style、diff check；七组 211/211（`/tmp/learner-result-factories2.log`），未重跑 clean/full/HA；T2 active，不提交。
用户批准纠正现场组装与转换场景：准确范围 `RegisterMembershipPermitCommand.java`、SDK `ConfirmMembershipPermitDrainedParams.java`、`BrokerMembershipTransformer.java`、`BrokerMembershipTransformerTest.java`、`BrokerMembershipAppService.java`、新增 `RegisterMembershipPermitCommandTest.java` 与 SDK `ConfirmMembershipPermitDrainedParamsTest.java`，本计划及 PROGRESS。两个边界 record 使用手写 Builder，build 调用 canonical 校验；不是领域 Builder，不改变 record/JSON，不使用 Lombok。

- [x] 两个多参数 Mapper 改为调用处具名 Builder，删除无调用映射及其测试；保留对象转换与 UNKNOWN 枚举拒绝。
- [x] Builder 测试字段保真/非法输入/复用隔离；运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=RegisterMembershipPermitCommandTest,ConfirmMembershipPermitDrainedParamsTest,BrokerMembershipTransformerTest,BrokerMembershipPermitTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false`、style/diff check、clean、限定 Review。原 clean/full/E2E 缺口及扫描 Review Revise 保留，扫描安全检查已恢复。

Builder 增量：上述命令退出 0（`/tmp/permit-builders-tests.log`），包含真实存在的领域 BrokerMembershipPermitTest，style/diff check 通过。限定源码 Review Accept（`/tmp/permit-builders-review.md`），核对当前生成映射/Builder/扫描检查，不代替完整 diff 或独立执行。clean 仍原 T1 pending_acceptance 失败（`/tmp/permit-builders-clean.log`），不继续 full/HA；未执行新增测试修复前 RED，不冒称已复现缺陷。领域 Builder alias 仍为已识别风险，扫描删除试改已撤回；维护指标短路调整已回归。未形成新通用约定，不提交。


用户批准维护指标与排空扫描可读性核对。范围 `BrokerMembershipAppService.java`、必要的 `BrokerClusterOperationTest.java`、本计划与 PROGRESS。指标改为分段短路。operation 构造器/Builder 已保证首个未成功步骤之后无 Permit，Step 保证历史前置 Permit 全为已排空未应用；因此至多当前步骤的最后 Permit 未排空，应用层顺序/计数检查可能冗余，须限定 Review 与领域测试确认后才能删除；本地执行器事实比较仍归应用层，不让 Domain 依赖 Executor。

- 历史未勾选记录：指标分段短路已实施；排空扫描删除顺序/计数检查的试改经限定 Review Revise（`/tmp/membership-scan-review.md`），因 operation Builder 构建后仍可修改同一实例，无法无条件证明不变量不可绕过，已精确恢复原扫描检查，不改变该安全契约。未扩大修复 Builder alias；保留该既有风险，不把试改测试通过当作批准。
- 历史未勾选记录：运行准确现有领域组 BrokerMembershipPermitTest 与六组成员回归、style/diff check、clean；旧计划误写的 BrokerClusterOperationTest 并不存在，前一命令仅执行六组（`/tmp/membership-scan-tests.log`），不宣称领域测试已覆盖。恢复原检查后的验证待执行；clean 原阻断保留，T2 不关闭。

用户批准多参数构造可读性整理，保持协议与业务判断不变。范围：新增 `transformer/adapter/BrokerMembershipTransformer.java` 及对应测试、`BrokerMembershipAppService.java`、`CompleteLearnerSyncResult.java`、`RegisterMembershipPermitCommand.java`、必要的现有结果测试、本计划与 PROGRESS。最终按用户澄清仅做 MapStruct 转换，不新增 Builder；试加的 Lombok Builder 与调用已撤回，结果类型与登记命令恢复原 record。保留 canonical 校验，不机械改简单值对象或已使用手写 Builder 的领域 intent。

- [x] Permit 请求/接收命令/共识确认命令转换移入 Transformer，认证身份显式参数传入，不在映射中推断或选择；测试字段及枚举保真。
- [x] 按后续用户决定取代 Builder 步骤：Permit 登记命令使用 Transformer；learner 固定结果维持原构造/工厂，不把失败判断伪装为映射。撤回新增 Lombok Builder，保持校验、epoch/null/文案与执行时机。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipTransformerTest,CompleteLearnerSyncResultTest,AcceptMembershipPermitDrainedCommandTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false`、Java style/diff check；clean 另记录，既有 full/E2E 与 clean 阻断保留，T2 不关闭。

转换增量：Mapper 直接调用 record canonical constructor；字段、受信来源显式传入、两个结果枚举与 UNKNOWN 不生成证据、登记校验均有专测。首次新 fixture 用 List 代替 Set 导致测试编译失败，修正后上述命令退出 0（`/tmp/membership-transformer-tests2.log`），style/diff check 通过。九组共 226/226，独立限定源码 Review Accept（`/tmp/membership-transformer-review.md`），核对 generated mapper，但未取得精确 before/after diff，不冒称完整验收。clean 仍原 T1 pending_acceptance 失败（`/tmp/membership-transformer-clean.log`），不继续 full/HA。本轮未执行修复前 RED，不宣称缺陷复现；无新通用约定，不提交。


用户批准 Permit 输入校验与发送分支整理，设计限定 Approved（`/tmp/permit-command-validation-design-review.md`）。准确范围：`model/cqrs/command/AcceptMembershipPermitDrainedCommand.java`、`application/cluster/BrokerMembershipAppService.java`、`interfaces/handler/broker/MembershipPermitDrainedHandler.java`、新增 `model/cqrs/command/AcceptMembershipPermitDrainedCommandTest.java`、既有 `BrokerMembershipPermitAdmissionTest.java`，必要 Handler 测试及设计、本计划、PROGRESS。

- [x] 逐字段构造校验测试先 RED；将格式校验移入 Command，用 AssertUtils；应用服务只分段校验身份关系，保持短路/拒绝语义，删除 Stream 与 null 兜底。
- [x] 展开本机/远端三元分支，命令先构造再调用；Handler 直接转换已验证 result；不删除生命周期复验。
- [x] 更新格式错误由构造异常表达的 Admission 测试，运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=AcceptMembershipPermitDrainedCommandTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false`、Java style/diff check、clean，独立限定 Review；既有 clean 阻断与 full/E2E 缺口保留，不关闭 T2。

Permit 校验增量证据：构造边界测试先 RED（`/tmp/permit-command-validation-red.log`），七组 215/215 GREEN（`/tmp/permit-command-validation-green.log`），Java style/diff check 通过；限定源码 Review Accept（`/tmp/permit-command-validation-review.md`）。未新增实际 Bolt 非法认证上下文/waiter 复用测试，异常出口由设计及实现 Review 静态核对，不宣称该运行时组合已测。clean 仍原 T1 pending_acceptance 失败（`/tmp/permit-command-validation-clean.log`），不继续 full/HA，不提交。遵循已有构造校验规范，无新通用约定。


- [x] 用户批准将 `BrokerMembershipAppService.java` 私有方法及调用点 `resumeLearnerConfirmation` 更名 `resumeLearner`，不保留兼容包装、不改方法体。运行 `mvn -q -pl im-broker/im-broker-server -am -DskipTests compile` 与 diff check 通过（`/tmp/resume-learner-rename-compile.log`），readiness 通过；仅命名不重跑行为测试/full/HA，旧 clean 阻断保留，T2 active，不提交。

用户批准恢复可读性增量，范围 `BrokerMembershipAppService.java`、必要的 `BrokerMembershipWorkflowTest.java`、本计划与 PROGRESS。复用 LearnerConfirmationState，不改变恢复 Begin DUPLICATE 可继续、worker 重读、预算/门禁/发布规则。

- [x] 删除 resume 不可达 switch 分支并修正注释；保留非成员输入既有错误分类与端点检查顺序。
- [x] 平铺 resumeLearnerConfirmation 准备、Begin、Commit、终态、映射/发布，直接返回只读链并汇合失效信号，不新增 private 方法。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipExecutorTest,BrokerMembershipWorkflowTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false`、Java style/diff check、clean；独立限定 Review，不把聚焦证据视为 full/HA，T2 不关闭。

恢复平铺证据：六组 203/203（`/tmp/learner-resume-flat-tests.log`），Java style/diff check 通过；clean 仍原 T1 pending_acceptance 失败（`/tmp/learner-resume-flat-clean.log`），不继续 full/HA。独立限定源码 Review Accept（`/tmp/learner-resume-flat-review.md`），核对当前源码与提供的原流程，不声称取得精确完整 diff 或独立重跑。复用既有恢复/Begin DUPLICATE/换代测试；worker 重读 operation 消失/终态、恢复返回视图取消的专属组合覆盖仍可增强，尚非完整验收。为保留非成员 PENDING 输入原错误分类，末尾端点存在检查保留，仅删除不可达 switch；未新增类型或 private 方法，无新通用工程约定，不提交。


用户批准确认链两处确定冗余收敛，范围仅 `BrokerMembershipAppService.java`、本计划及 PROGRESS，不改变其他 guard、异步边界或响应。

- [x] 将确认准备阶段 `completedFuture(null).thenApplyAsync` 换成 `supplyAsync`；终态显式守卫通过后直接调用 Adapter，跳过 helper 的同结果重复检查（helper 其他调用者保留）。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipExecutorTest,BrokerMembershipWorkflowTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false`、Java style、diff check、clean。纯等价简化，不要求缺陷 RED；原 clean 阻断保留，不继续 full/HA，不关闭 T2。

确认链收敛证据：六组 203/203（`/tmp/learner-chain-trim-tests.log`），Java style/diff check 通过；clean 仍原 T1 pending_acceptance 失败（`/tmp/learner-chain-trim-clean.log`）。本增量尚未独立 Review，未继续 full/HA，不提交；未形成新工程约定。


用户批准固定在途结果工厂增量，准确范围：`model/cqrs/result/CompleteLearnerSyncResult.java`、对应 `CompleteLearnerSyncResultTest.java`、`application/cluster/BrokerMembershipAppService.java`、本计划与 PROGRESS。不改变业务语义，仅统一两处重复构造。

- [x] 新增 inFlightConflict/inProgress 工厂及完整字段断言，确认与恢复改用工厂，不新增 private 方法。
- [x] 先运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=CompleteLearnerSyncResultTest' -Dsurefire.failIfNoSpecifiedTests=false` 记录未实现工厂的 RED，再运行该测试与六组成员回归、Java style、diff check、clean；原 clean 阻断保留，不继续 full/HA。

结果工厂证据：RED 为两个工厂尚不存在的编译失败（`/tmp/learner-result-factories-red.log`）；GREEN 命令增加 `CompleteLearnerSyncResultTest` 与六组，210/210（`/tmp/learner-result-factories-green.log`），Java style/diff check 通过；clean 仍原 T1 pending_acceptance 失败（`/tmp/learner-result-factories-clean.log`）。未取得本增量独立 Review，不继续 full/HA、不提交。遵循既有结果工厂规范，无新通用约定。


用户批准 learner 调度闭包具名化：范围仅 `infrastructure/cluster/BrokerMembershipExecutor.java`、本计划与 PROGRESS。private 内部 LearnerExecution 实现 Executor，保存 generation/invalidated/pending，不保存业务结果；不改准入、预算和生命周期。

- [x] 提取 LearnerExecution，pending 改为 mutex 保护的 boolean，消费前清除，业务及 invalidated 锁外调用。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipExecutorTest,BrokerMembershipWorkflowTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false`、Java style、diff check、clean。复用现有单 pending 与同 term 换代回归，不改变行为，不要求缺陷 RED；原 clean 阻断保留，不继续 full/HA。

调度类增量：六组 Maven 退出 0（`/tmp/learner-execution-class-tests.log`），Java style/diff check 通过；独立窄 Review Accept（`/tmp/learner-execution-class-review.md`），对照提供的旧闭包与当前源码检查，不代表独立重跑或完整 diff Review。clean 仍为原 T1 pending_acceptance 失败（`/tmp/learner-execution-class-clean.log`），未继续 full/HA，无新规范沉淀，不提交。


成员/learner 账本核对增量：保留两表及成员共享行为。执行器 Permit 账本在 Begin 前不存在，且不保存 operation 摘要；applicationStages 是排队阶段而非应用身份容量，不能替代成员表。范围 `BrokerMembershipAppService.java`、Server README、本计划与 PROGRESS。

- [x] 将 submissionStarted 更名 identityRetained，容量常量明确为 MEMBERSHIP_ATTEMPT_CAPACITY；注释准确区分历史身份保留、在途结果及技术账本，不改变条件/锁/调度。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false`、Java style、diff check 和 clean；既有 clean 阻断保留，不继续 full/HA。

账本核对结果：未证明可删除的冗余账本，本轮仅命名/注释调整，成员共享和身份保留契约不变。六组 203/203（`/tmp/membership-ledger-naming-tests.log`），Java style/diff check 通过；clean 仍为原 T1 pending_acceptance 失败（`/tmp/membership-ledger-naming-clean.log`）。未执行新独立 Review 或 full/HA，不宣称整体验收；未建立新通用规范或放宽规则。


用户批准预算拒绝出口收敛，范围仅 `BrokerMembershipAppService.java` 的确认/恢复两个 `stages == null` 分支、本计划与 PROGRESS；不新增 private 方法或改变状态/文案/删除顺序。

- [x] 两个预留失败出口精确移除后直接返回 completedStage，删除无等待者的 completion 中转；内部换代完成桥梁保留。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false`、Java style、diff check、clean；既有 clean 阻断未解决前不继续 full，不关闭 T2。

预算出口增量：六组回归退出 0（`/tmp/learner-reservation-direct-tests.log`），Java style/diff check 通过；独立窄 Review Accept（`/tmp/learner-reservation-direct-review.md`），确认 reserve 返回 null 不持有回调，重复请求也不等待 attempt，故未完成的局部 attempt 不外泄。clean 仍因原 T1 pending_acceptance 失败（`/tmp/learner-reservation-direct-clean.log`），未继续 full/HA，无新行为、不要求 RED 缺陷复现，不提交。


用户批准在途不共享；本次为 Sprint 非协议变更目标的明确例外：新增 IN_PROGRESS，nullable acceptedEpoch，Broker/Ops/CLI 同版本部署。设计第二轮 Approved（`/tmp/learner-no-share-design-review2.md`），首轮 Revise 保留（`/tmp/learner-no-share-design-review.md`）。取代早期共享结果验收要求，不改变其他成员/Permit 共享。

- [x] RED/GREEN：修改 `BrokerMembershipWorkflowTest.java` 与 CLI `OpsCliTest.java`/`http/OpsCliClientTest.java`，验证双向竞争立即 IN_PROGRESS、唯一执行与取消隔离、CLI 明确状态与非成功提示。命令：`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest' -Dsurefire.failIfNoSpecifiedTests=false`、`mvn -q -pl im-management/im-ops-cli -am test`。
- [x] 实现：Broker `BrokerMembershipAppService.java`、`adapter/cluster/consensus/BrokerClusterConsensusResult.java`、`model/cqrs/result/CompleteLearnerSyncResult.java`、`model/execution/LearnerConfirmationAttempt.java`；Ops `infrastructure/client/model/BrokerOperationPayload.java`、`model/cqrs/dto/BrokerOperationDTO.java`、`model/io/OpsCliSubmissionResponse.java`；CLI `http/OpsCliClient.java`、`OpsCli.java`。不删除执行者内部失效桥梁。
- [x] 契约与文档：Broker `transformer/interfaces/BrokerManagementHttpTransformerTest.java`、Ops `infrastructure/client/HttpBrokerManagementClientTest.java` 和 `interfaces/http/OpsCliControllerTest.java` 验证 nullable epochs/IN_PROGRESS；同步 Broker Architecture/Server README、Ops/CLI README、RELIABILITY、设计、本计划与 PROGRESS。运行六组 Broker 回归、`mvn -q -pl im-management/im-ops -am test '-Dtest=HttpBrokerManagementClientTest,OpsCliControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`、CLI 全模块与 Java style/diff check。独立实现限定 Review Accept（`/tmp/learner-no-share-code-review.md`）；既有 full/E2E/clean 缺口保留，不关闭 T2。

不共享证据：Workflow 先 RED 2 failures/5 errors（`/tmp/learner-no-share-red.log`），最小实现后六组 Broker 203/203（`/tmp/learner-no-share-green3.log`）；新增 enum 需要补全 `BrokerClusterAppService.java` 的 ACK switch 和 MembershipAppService 的 Permit 响应 switch，均保守映射非成功，不使该状态 accepted。契约回归 Broker Transformer 9、Ops 58、CLI 全模块 174 均通过（`/tmp/learner-no-share-contracts3.log`、`/tmp/learner-no-share-cli-full.log`；空日志结合命令实际退出 0 与 Surefire 报告，不仅凭日志判断）。中间两次编译失败为 switch 漏项，一次 Ops 测试编译漏 verify import、一次新增测试 URI 多写 raft，均修正后复验，不改生产寻址。readiness、Java style、diff check 通过；clean 再次因原 T1 pending_acceptance 失败（`/tmp/learner-no-share-clean.log`），不继续 full/HA。契约测试仅分边界 fixture，不声称真实 E2E 已完成。无新增通用工程规则，原有并发/取消/nullable 协议回归承载约束。

用户批准试改平铺确认链：不新增 private 步骤方法、不改恢复用例。范围：`BrokerMembershipAppService.java`、新增 `model/execution/LearnerConfirmationState.java`、`BrokerMembershipWorkflowTest.java`、确认与恢复设计及本计划、PROGRESS。正常结果保留 worker 发布门禁，与失效完成信号汇合后直接返回只读派生链；在途桥梁不删除。

- [x] 平铺准备、取证、复验 Begin、Commit、终态和响应阶段；不可变状态只传递事实，不执行 Adapter 或拥有 Future。
- [x] 验证确认链的正常/失效完成、取消隔离与共享；运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false`、`./scripts/check-java-style.sh`、`git diff --check`。本轮不运行 full/HA，T2 不关闭。

平铺试改证据：上述六组 203/203 通过（`/tmp/learner-flat-final2.log`），Java style、diff check 通过。首次 3 failures/1 error 均来自旧 Stage 对象身份断言（MinimalStage 被 AssertJ 检查触发 UnsupportedOperationException）；改为保留重复请求固定共享视图、首个/共享结果对象相同、双向共享结果和取消隔离断言。正常发布仍在原 worker 门禁内，随后与失效信号汇合；未改执行器或恢复实现。`./scripts/verify.sh clean` 再次失败，仍为既有 T1 `pending_acceptance` 不在状态集合（`/tmp/learner-flat-clean.log`），不放宽 checker；按窄至宽顺序不继续 full。独立增量 Review 待完成，T2 active，不提交。

后续行为调整按[确认与后台恢复设计](../../design-docs/2026-11-12-learner-confirmation-explicit-recovery-design.md)执行：用户批准、独立设计第二轮 Approved；首轮 Revise 原件保留。以下为同一 T2 的新增步骤，未完成前不关闭任务。

- 历史未勾选记录：CLI 端点契约 RED/GREEN：修改 `im-management/im-ops-cli/src/test/java/com/co/kc/imchat/management/ops/cli/operation/OperationRequestFileTest.java`、`im-management/im-ops-cli/src/test/java/com/co/kc/imchat/management/ops/cli/http/OpsCliClientTest.java`、`im-management/im-ops-cli/src/test/java/com/co/kc/imchat/management/ops/cli/OpsCliTest.java`（已核对为现有准确位置），覆盖确认端点/reason 记录、解析、发送、retry 保真和旧缺端点记录拒绝；最小修改对应生产类。命令 `mvn -q -pl im-management/im-ops-cli -am test`，先记录 RED 再 GREEN。
CLI 首个子步骤：OperationRequestFile 及其测试已修改，确认记录端点/reason roundtrip 与 null/空白端点拒绝已验证。RED 为 3 failures/1 error（`/tmp/learner-endpoint-record-red.log`）；首次 GREEN 仅旧拒绝文案断言失败，更新准确文案后 `mvn -q -pl im-management/im-ops-cli -am test '-Dtest=OperationRequestFileTest' -Dsurefire.failIfNoSpecifiedTests=false` 34/34 通过（`/tmp/learner-endpoint-record-green2.log`）。后续解析/发送/retry 已补：先 RED 3 failures（`/tmp/learner-cli-send-red.log`），最终 `mvn -q -pl im-management/im-ops-cli -am test` 173/173 通过（`/tmp/learner-cli-send-final.log`），含端点/reason 同时提交、原记录 retry 无额外查询、旧缺端点记录不改写及缺参数/错序拒绝。CLI README 已同步。后续已补旧缺端点 retry 无网络请求且记录不变的专门回归，三组 CLI 135/135 通过（`/tmp/learner-sharing-docs-final.log`）；本次未重跑 CLI 其他测试。

- [x] Ops HTTP RED/GREEN：修改 `im-management/im-ops/src/main/java/com/co/kc/imchat/management/ops/interfaces/http/OpsCliController.java` 与 `im-management/im-ops/src/test/java/com/co/kc/imchat/management/ops/interfaces/http/OpsCliControllerTest.java`，独立确认 request 及字段传递/缺失拒绝。命令 `mvn -q -pl im-management/im-ops -am test '-Dtest=OpsCliControllerTest' -Dsurefire.failIfNoSpecifiedTests=false`。先运行单例 RED 1 failure（`/tmp/learner-ops-endpoint-red.log`），最终 48/48 GREEN（`/tmp/learner-ops-endpoint-final.log`），覆盖端点/reason 传递及缺失/空白端点、负 epoch 拒绝；Java style、diff check 通过。
- 历史未勾选记录：Broker 行为 RED/GREEN：修改 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipAppService.java`、`im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipWorkflowTest.java`、`im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/application/cluster/BrokerClusterAppServiceChangeTest.java`、`im-broker/im-broker-server/src/test/java/com/co/kc/imchat/broker/interfaces/scheduler/BrokerMembershipSchedulerTest.java`。覆盖确认历史只读、恢复不取证、Begin DUPLICATE 不接管、双向共享与阶段重读；保留预算及 generation。命令 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false`。
Broker 分流增量：已实现确认历史只报告登记、Begin DUPLICATE 不接管及独立恢复，新增历史双阶段回归先 RED（`/tmp/learner-history-split-red.log`），最终上述六组 201/201 通过（`/tmp/learner-history-split-final2.log`）。首轮回归三项旧重发恢复测试改为显式 resume；一次后续命令退出 1 且日志为空，重跑通过不覆盖该事实。独立限定 Review 首轮 Block 被 Reviewer 复核撤回：私有 completeMembershipOperation 已严格过滤非 RAFT_COMMITTED，因此原 finding 为误报；第二轮 Accept/findings none（`/tmp/learner-history-split-code-review-round2.md`），首轮原件保留。补延迟 Begin 返回 SUCCEEDED 无重复提交测试，生产代码未改即通过；fixture 首次 stub 线程断言失败已修正为 doAnswer。后续补确认先入/恢复先入的双向同结果专测，确认先占表不升级为恢复、下一轮 resume 推进；六组 Broker 203/203 通过（`/tmp/learner-sharing-docs-final.log`）。当时更完整阶段竞态及最终 Review 尚待补齐；最终验收见恢复状态，未新增的专项覆盖不计作通过。

- 历史未勾选记录：端到端验证：修改 `im-test/im-e2e-test/src/test/java/com/co/kc/imchat/e2e/OpsCliGovernanceE2ETest.java` 与实际需要的同目录 `support/OpsCliGovernanceTopology.java` fixture，真实 CLI 记录/发送 → Ops → Broker、原记录 retry/query 及 scheduler 收尾。命令 `mvn -q -pl im-test/im-e2e-test -am test '-Dtest=OpsCliGovernanceE2ETest' -Dsurefire.failIfNoSpecifiedTests=false`；不得以 mock 响应替代真实链路。
稳定说明已同步登记/终态区别、后台恢复、端点必填和旧记录失败关闭；CLI 提示不再把原记录重发称为恢复。当前真实 write fixture 仅为 learner 分配地址而未启动目标 runtime，需扩 `support/OpsCliGovernanceTopology.java` 才能实际取证；不得 mock 进度冒充真实 E2E。本轮未改该 fixture。

- 历史未勾选记录：同步 `im-broker/ARCHITECTURE.md`、`im-broker/im-broker-server/README.md`、`im-management/im-ops/README.md`、`im-management/im-ops-cli/README.md`、`docs/RELIABILITY.md`、本计划和 PROGRESS；取得独立实现 Review 并完成最终 clean/full。保留既有状态 checker 阻断，先归因处理，不放宽规则。

- [x] 按用户批准在 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipAppService.java` 前置 `confirmLearner` 五项领域转换至登记和预算预留之前，保留 command 用于在途身份比较和边界结果。在 `src/test/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipWorkflowTest.java` 新增负 epoch 同步失败、无残留登记/共识提交及后续合法同 ID 成功回归，同步本计划与 PROGRESS。运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest' -Dsurefire.failIfNoSpecifiedTests=false` 退出 0（`/tmp/learner-conversion-tests.log`），readiness、Java style、diff check 通过。输入非法从异步 UNAVAILABLE 改为同步领域异常；未执行修复前 RED，未取得独立 Review，未运行 full/HA，既有 clean 状态阻断保留，T2 active，不提交。

- [x] 按用户批准，仅在 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipAppService.java` 的 `confirmLearner` 将外层 `supplyAsync` 加 `thenCompose(result -> result)` 改为 `completedFuture(null).thenComposeAsync`；保留内部方法体、执行器及后续异常映射和发布阶段，同步本计划与 PROGRESS。运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest' -Dsurefire.failIfNoSpecifiedTests=false`：193/193 通过，无失败或跳过（`/tmp/learner-compose-tests.log`）；readiness、`./scripts/check-java-style.sh`、`git diff --check` 通过。未取得独立 Review，未运行 full/HA；既有 clean 状态阻断保留，T2 仍 active，不提交。

- [x] 按用户命名意见，仅在 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipAppService.java` 的 `confirmLearner` 将 `workflowTerm` 改为 `expectedTerm`，同步本计划与 PROGRESS；不改逻辑或结果文案。`mvn -q -pl im-broker/im-broker-server -am -DskipTests compile` 与 `git diff --check` 通过（`/tmp/learner-term-rename-compile.log`）；未重跑测试或 full/HA，T2 保持 active。

用户继续批准展开三个 learner 收尾 helper，范围仅 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipAppService.java`、本计划与 `PROGRESS.md`。

- [x] 将 `learnerConfirmationUnavailable`、`publishInvalidLearnerAttempt`、`publishLearnerConfirmation` 展开到调用处并删除声明；保持状态、文案、worker 边界及精确移除后完成的顺序。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest' -Dsurefire.failIfNoSpecifiedTests=false`，193/193 通过，无失败或跳过（`/tmp/learner-helper-inline-tests.log`）；readiness、`./scripts/check-java-style.sh`、`git diff --check` 通过，旧方法搜索无残留。未取得独立 Review；本轮不运行 full/HA，不提交，T2 保持 active。上一轮 clean 的既有计划状态阻断仍未处理，不宣称完整验收。

用户批准将 `executeLearnerConfirmation` 原样内联到 `confirmLearner` 的 worker lambda，仅修改 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipAppService.java`，同步本计划与 `PROGRESS.md`；不删除判断、不改变异步阶段、异常映射或发布顺序。

- [x] 内联方法体并删除单调用私有方法；使用 `/tmp/confirm-inline-before.java` 对照，脚本证明除缩进、调用展开和删除声明外无其他源码变化。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest' -Dsurefire.failIfNoSpecifiedTests=false`：退出 0，193/193 通过，无失败或跳过，日志 `/tmp/confirm-learner-inline-tests.log`。readiness、`./scripts/check-java-style.sh`、`git diff --check` 通过。`./scripts/verify.sh clean` 因既有 T1 状态 `pending_acceptance` 不在 checker 支持集合而失败（`/tmp/confirm-learner-inline-clean.log`），未改写既有状态或放宽规则。仅机械等价检查，未取得独立 Review；T2 保持 active，本轮不运行 full/HA、不提交，不宣称整体验收。

用户批准使用 ConcurrentHashMap 简化 learner 在途表：修改 `BrokerMembershipAppService.java` 和 `BrokerMembershipWorkflowTest.java`，同步 Broker Architecture、本计划与 PROGRESS。使用 putIfAbsent 选定唯一执行者，预留失败精确移除并完成共享结果，不调整成员操作表。

- [x] 替换 learner 显式锁，保留身份比较和精确删除，补并发同请求共享回归。
- [x] 运行 Workflow/Executor/Admission/Drift/AppServiceChange 定向测试、Java style 与 diff check；不运行 full/HA。

CHM 增量证据：`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest,BrokerClusterAppServiceChangeTest' -Dsurefire.failIfNoSpecifiedTests=false` 退出 0，193/193 通过（`/tmp/learner-chm-final.log`）。首轮旧等待应用锁测试超时，改为验证持有 Map monitor 不阻塞发布；新增八调用共享同一结果、证据/Begin/Commit 各一次回归。readiness、Java style、diff check 通过，独立限定复核无必修 findings；T2 仍 active，不运行 full/HA、不提交。

用户批准移出诊断监控状态，范围为 `BrokerMembershipAppService`、新增 `support/monitoring/MembershipDiagnosticMetrics.java`、`BrokerClusterConfiguration`、全部直接构造的 Broker 测试和 `im-test/im-routing-ha/.../AutomaticFailureTlsHaTest.java`，以及 Broker Architecture、Server README、Harness、PROGRESS。保留实时准入和任期/revision 检查，不调整业务行为，不运行 full/HA。

- [x] 迁移两个采样字段与 Gauge 注册，删除应用服务指标 getter，同步构造调用；指标不作为准入凭据。
- [x] 定向运行七组 Broker 测试、窄架构测试和 routing-ha 测试编译，执行 Java style 与 diff check，记录独立复核证据。

最终补验：`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerClusterConfigurationTest' -Dsurefire.failIfNoSpecifiedTests=false` 退出 0，初始值及状态转换断言所在配置组 13/13 通过（`/tmp/membership-metrics-config-final.log`）；readiness、Java style、diff check 通过。下段配置复验待记录项由此补齐，T2 不关闭。

监控迁移证据：`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipDriftTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerClusterAppServiceChangeTest,BrokerClusterConfigurationTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false` 退出 0，208/208 通过，日志 `/tmp/membership-metrics-tests.log`。`mvn -q -pl im-test/im-architecture-test,im-test/im-routing-ha -am test '-Dtest=ModuleBoundaryTest#brokerClusterExecutionMustNotDependOnApplicationWorkflows' -Dsurefire.failIfNoSpecifiedTests=false` 退出 0，窄架构 1/1、routing-ha 测试编译通过，未运行 HA，日志 `/tmp/membership-metrics-boundary.log`。独立限定复核无必修 finding；首次两处 testCompile 遗漏已修复，不添加兼容构造。补充初始 Gauge 断言后的配置复验待记录，T2 仍 active。

- [x] 冗余收敛仅修改 `BrokerMembershipAppService.java`：删除在途 record.equals 后的 digest 比较，两条成员 Permit 路径共用原完成尾部；不移动注册后的任期/身份检查，不新增 helper。运行 `mvn -q -pl im-broker/im-broker-server -am test '-Dtest=BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerClusterAppServiceChangeTest,BrokerMembershipPermitAdmissionTest' -Dsurefire.failIfNoSpecifiedTests=false` 退出 0，共 184/184；日志 `/tmp/membership-dedup-tests.log`。readiness、Java style、diff check 通过，独立限定 Review 无 findings；不替代 full/HA，T2 仍 active。

本轮用户批准行为简化，取代此前完成后固定缓存合同：只保留在途共享，完成精确移除；不同内容在途请求立即拒绝。新请求重新准入，未提交拒绝可重新检查，已提交成功依赖领域身份判断返回 DUPLICATE 和原 epoch，不重新提交 Begin 或取证；每次新尝试仍消耗永久预算。LearnerConfirmationAttempt 移至 model.execution，只有命令、一个完成源及只读视图；删除 replay/conflict 完成源、retryable 和 LearnerReplayFacts，不放应用包或 domain。

- [x] 修改 AppService、model/execution/LearnerConfirmationAttempt、Workflow 测试及 Broker Architecture、Server README、Harness、PROGRESS；删除 application/cluster 下旧状态类。
- [x] 定向验证：`mvn -q -pl im-test/im-architecture-test -am test '-Dtest=ModuleBoundaryTest#brokerClusterExecutionMustNotDependOnApplicationWorkflows,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerClusterAppServiceChangeTest,BrokerMembershipPermitAdmissionTest,BrokerMembershipDriftTest' -Dsurefire.failIfNoSpecifiedTests=false` 退出 0，五组业务 192/192、窄架构 1/1；日志 `/tmp/learner-inflight-final.log`。首次红测暴露成功重放重复 Begin（期望 1，实际 5），增加 SUCCEEDED 领域幂等分支后通过；独立限定复核无 findings。readiness、Java style、diff check 通过。T2 保持 active，未运行 full/HA。

用户追加批准 Future 链调整：在原 T2 文件范围内删除 StageSlot，准入返回标准 Executor；永久计数替代历史槽位列表，保留共享预算、单 pending 和代际约束。应用层使用 supplyAsync/thenCompose/handleAsync/thenAcceptAsync 编排，读取重放事实后另投递发布任务。发布消费时的生命周期检查作为线性化点，Future 完成不持应用锁或执行器锁；失效回调负责对外结果，不依赖跳过的内部 Future 自动完成。

- [x] 删除 StageSlot 及重复检查 API，调整应用层 Future 链、技术调度测试与职责文档。
- [x] 运行 Workflow/Executor/Admission/Drift/AppServiceChange 与窄架构测试，记录本次独立 Review 和最终证据；不运行 full/HA。

Future 增量证据：`mvn -q -pl im-test/im-architecture-test -am test '-Dtest=ModuleBoundaryTest#brokerClusterExecutionMustNotDependOnApplicationWorkflows,BrokerMembershipDriftTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerClusterAppServiceChangeTest' -Dsurefire.failIfNoSpecifiedTests=false` 退出 0，Workflow 79、Executor 44、Admission 16、Drift 8、AppServiceChange 45，共 192/192，窄架构 1/1。日志 `/tmp/learner-future-final.log`；readiness、Java style、diff check 通过。新增单 pending 拒绝后恢复测试，既有代际、稳定重放与永久预算回归通过；独立源码复核无必须修复 finding。未运行修复前 RED，full/HA 仍未运行，T2 不关闭。

本次用户批准收回 learner 流程所有权：准确范围为 `BrokerMembershipAppService.java`、新增同包 `LearnerConfirmationAttempt.java`、`infrastructure/cluster/BrokerMembershipExecutor.java`、`BrokerMembershipExecutorTest.java`、`BrokerMembershipWorkflowTest.java`、Server README、Broker Architecture、Harness、PROGRESS 与本计划。业务策略不再以泛型回调参数交给执行器；不改变证据、提交、共享重放及永久预算合同，不运行 full/HA。

- [x] 应用层持有强类型尝试、请求冲突、重试与重放策略；执行器只提供有界执行槽和受控发布，删除 submitWorkflow/unchecked API。
- [x] 同步技术槽测试，运行 Drift/Workflow/Executor/Admission/AppServiceChange 与窄架构回归，独立 Review 后记录实际结果。

本次增量证据：`mvn -q -pl im-test/im-architecture-test -am test '-Dtest=ModuleBoundaryTest#brokerClusterExecutionMustNotDependOnApplicationWorkflows,BrokerMembershipDriftTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipPermitAdmissionTest,BrokerClusterAppServiceChangeTest' -Dsurefire.failIfNoSpecifiedTests=false` 退出 0，Workflow 79、Executor 43、Admission 16、Drift 8、AppServiceChange 45，共 191/191，窄架构 1/1；日志 `/tmp/learner-ownership-final2.log`。首次扩展运行因残留 class 的 NoClassDefFoundError 无法发现测试，clean 后同范围通过，不以失败运行作为通过证据。新增回归验证持应用锁期间换代不得发布成功；最终发布按 map -> executor 锁顺序认领当前代际，锁外完成 Future，认领后角色变化不撤销已选结果。独立复核无剩余必须修复 finding；Reviewer 已撤回中间锁内发布版本的具体两线程死锁交错判断，不将其记录为已证实缺陷。readiness、Java style、diff check 通过。未执行修复前 RED，不运行 full/HA，T2 保持 active。

- [x] P2 增量：`BrokerMembershipAppService.java` 的本地状态改名为 MembershipOperationAttempt；明确记录是否已开始提交，精确释放提交前确定拒绝，保留未知与历史操作；诊断先复验 term/revision 再发布比较结果，补充关键注释。
- [x] 在 `BrokerMembershipDriftTest.java` 补充 4096 次漂移拒绝后恢复及 mismatch+term/revision 失效反例，运行 Drift/Workflow/Executor/AppServiceChange 定向测试退出 0（`/tmp/membership-attempt-final.log`）；Java style 与 diff check 通过。独立限定 Review 无新增 findings；入口补四步注释，README/Harness 同步回收与失效采样边界。不运行 full/HA。本轮未取得修复前 RED，不以新测试通过冒称已执行缺陷复现；预检 UNAVAILABLE、已开始 Begin 的尝试仍保守保留。

- [x] 小幅整理 `BrokerMembershipAppService.java`：两处 learner 任期守卫复用现有判断，终态推进去掉多余 else，`LearnerReplayFacts` 收为 private record；保留 ReadIndex 证据任期检查及全部异步边界。
- [x] 小幅整理验证：`mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipWorkflowTest,BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test` 退出 0（`/tmp/membership-small-cleanup-tests.log`），readiness、Java style 与 diff check 通过；独立限定 Review 无 findings，三项修改等价。不运行 full/HA。

- [x] 按用户确认将五个私有方法统一为 `processMembershipOperation`、`advanceMembershipOperation`、`retransmitDrainedPermit`、`confirmMembershipPermitDrained`、`inspectMembershipConfiguration`，同步调用点与 Javadoc；诊断私有方法避免与现有公开入口重名。仅调整 `BrokerMembershipAppService.java`，不留兼容包装、不改方法体逻辑。
- [x] 命名收尾验证：`mvn -q -pl im-broker/im-broker-server -am -DskipTests compile` 退出 0（`/tmp/membership-private-rename-compile.log`）、五个旧名残留搜索无匹配、`git diff --check` 通过；首次编译发现诊断入口重名，私有方法改为 `inspectMembershipConfiguration` 后通过。不运行 full/HA。

本轮增量准确范围：`BrokerMembershipAppService.java`、`infrastructure/cluster/BrokerMembershipExecutor.java`、`domain/cluster/model/BrokerCluster.java`、对应 `BrokerMembershipWorkflowTest`、`BrokerMembershipExecutorTest`、`BrokerClusterTest`，以及当前 README/Architecture、PROGRESS 与本计划。不保留旧方法兼容包装。

- [x] 新增同 term 重启且 Begin 延迟返回回归，修复前超时失败，日志 `/tmp/membership-generation-red.log`。
- [x] 将 operation 后续阶段绑定准入 generation；在 worker 上拒绝旧代际推进；完成发布同样回到该阶段执行器，fault/close 无法调度时保持 pending。
- [x] 重命名 operation/term/progress 方法，收紧内部可见性，移除未用参数，合并重复说明；期望成员计算归入 BrokerCluster。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipExecutorTest,BrokerMembershipWorkflowTest,BrokerMembershipPermitAdmissionTest,BrokerClusterAppServiceChangeTest,BrokerClusterTest -Dsurefire.failIfNoSpecifiedTests=false test`、Java style 与 diff check；独立 Review 后记录证据。不运行 full/HA。

本轮证据：首次五组 223 场景中 1 个错误为旧反射调用仍传已删除参数，已同步测试而非恢复兼容方法。随后五组退出 0；新增排队失效测试后，最终运行 `mvn -q -pl im-test/im-architecture-test -am '-Dtest=ModuleBoundaryTest#brokerClusterExecutionMustNotDependOnApplicationWorkflows,BrokerMembershipExecutorTest,BrokerMembershipWorkflowTest,BrokerMembershipPermitAdmissionTest,BrokerClusterAppServiceChangeTest,BrokerClusterTest' -Dsurefire.failIfNoSpecifiedTests=false test` 退出 0，执行器 43、Workflow 78、Admission 16、AppServiceChange 45、BrokerCluster 45，共 227/227，窄架构 1/1。日志 `/tmp/membership-operation-final.log`；readiness、Java style、diff check 通过。独立 Reviewer 初次指出拒绝路径 callback 发布风险，改为受控异步完成并补 FAULT/CLOSE 回归；复核未发现新增必须修复问题。源码 Review 不替代 full/HA，T2 继续 active。

learner 私有流程命名调整为 `executeLearnerConfirmation` 与 `resolveLearnerReplayFacts`：前者表达完整确认执行，后者表达读取已提交状态并生成重放/冲突事实；调度上下文已由调用方保证，不把 `OnWorker` 作为业务方法名；不保留旧名兼容包装。

- [x] 按用户 Review 调整 `BrokerMembershipAppService#executeMembershipPermit`：展开身份冲突、在途共享与容量守卫，明确 existingState/newState；不提取包装方法，保持锁边界、已完成重试和拒绝后的精确状态删除。
- [x] 运行 `mvn -q -pl im-broker/im-broker-server -am -Dtest=BrokerMembershipWorkflowTest -Dsurefire.failIfNoSpecifiedTests=false test` 验证守卫展开：75/75 通过，日志 `/tmp/membership-entry-readability-test.log`。
- [x] 按用户后续要求将唯一调用的 `runMembershipOperation` 内联到入口提交的 worker 任务，保留阶段拒绝异常、同步/异步失败映射与精确状态清理。
- [x] 使用相同命令复验内联后代码：退出 0，Workflow 75/75 通过，日志 `/tmp/membership-entry-inlined-test.log`；Java style 与 `git diff --check` 通过。不运行 full/HA。

- [x] 2026-11-10 按用户确认重命名为 `infrastructure.cluster.BrokerMembershipExecutor`，同步构造器、Bean 方法、Java 引用和 `BrokerMembershipExecutorTest`；当前 README/Harness 引用更新，历史命令与验收名称保留。
- [x] 重命名后 `mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile` 退出 0（日志 `/tmp/membership-rename-compile.log`）；readiness、`./scripts/check-java-style.sh`、`git diff --check` 通过。Java 源码无旧类名引用；未运行行为测试或 full/HA。

本轮用户批准调整顺序：不等待镜像网络恢复，先执行 T2，重型 full/HA 留待代码 Review 后。T1 不宣称完整验收。执行器移动到 `src/main/java/com/co/kc/imchat/broker/infrastructure/cluster/BrokerMembershipPermitExecutor.java`；同步所有生产和测试引用、配置装配、Server README 与 Broker Architecture。执行状态和共享预算保留在技术账本，learner 业务判断和结果映射由 AppService 提供，不新增业务 callback interface。

- [x] 消除执行器对具体 AppService、ConfirmLearnerCommand 和 CompleteLearnerSyncResult 的依赖，迁移至 infrastructure.cluster。
- [x] 新增 `BrokerMembershipPermitExecutorTest#workflowSharesInflightViewAndFreezesReplayOnWorker`，验证在途共享、冲突、worker 发布与稳定重放。
- [x] 同步 `im-test/im-routing-ha/src/test/java/com/co/kc/imchat/routingha/AutomaticFailureTlsHaTest.java` 的旧包引用，运行 `mvn -q -pl im-test/im-routing-ha -am -DskipTests test-compile` 退出 0；仅编译，不启动 Docker 或 HA。日志 `/tmp/membership-ha-compile.log`。
- [x] 运行 `mvn -q -pl im-test/im-architecture-test -am -Dtest=ModuleBoundaryTest#brokerClusterExecutionMustNotDependOnApplicationWorkflows,BrokerMembershipPermitExecutorTest,BrokerMembershipWorkflowTest,BrokerMembershipPermitAdmissionTest,BrokerClusterAppServiceChangeTest -Dsurefire.failIfNoSpecifiedTests=false test`：退出 0，成员回归 178/178、窄架构 1/1，无跳过；`./scripts/check-java-style.sh` 与 `git diff --check` 通过。日志 `/tmp/membership-migration-final.log`。首次新增测试因 Future 展示掩盖身份断言失败，改用布尔身份断言后单例与整组均通过；没有改动生产安全门以适配测试。

文件范围：T1 三个 Java 文件及 `src/test/java/com/co/kc/imchat/broker/application/cluster/BrokerMembershipWorkflowTest.java`、`im-broker/ARCHITECTURE.md`。

- [x] 核对 learner 首次、重复、冲突、UNKNOWN/NOT_LEADER、同 term 重启、fault/close、4096 预算与恢复，证据见恢复状态中的成员回归与定向复核。
- 历史未勾选记录：对照现有 learner 测试列出首次、重复、冲突、UNKNOWN/NOT_LEADER、同 term 重启、fault/close、4096 永久预算和显式恢复约束。
- 历史未勾选记录：在保留同一 worker、共享账本锁和预算的前提下迁移状态；Executor 不引用 AppService，不新增业务 callback interface。
- 历史未勾选记录：先跑每个受影响的失败测试，再跑成员回归；失败必须定位根因，不重复套用已失败迁移。
- 历史未勾选记录：同步 README/Architecture 的实际状态。

### T3 成员迁移复核与验收

文件范围：T1/T2 范围和 `src/test/java/com/co/kc/imchat/broker/adapter/cluster/BrokerRaftAdapterTest.java`；必要修复以任务内清单记录。

- [x] 核对 UNKNOWN 不缓存结果、共享预算、generation 与同步 native 入口，证据见恢复状态中的 T3 定向复核和成员回归。
- 历史未勾选记录：复核当前成员 Map 是否错误永久缓存 UNKNOWN、是否丢失统一预算和 generation 失效；检查 Adapter 新队列是否改变 native 进入/返回语义。
- [x] 修复回归证明的 Broker 重试身份丢失，原有安全守卫保留；RED/GREEN 与独立窄复核见恢复状态。
- 历史未勾选记录：修复经回归证明的问题，保留原安全守卫。
- [x] 模块测试、clean/full、真实 HA 与正式 quality 已取得对应证据，见恢复状态。
- 历史未勾选记录：运行模块测试、clean/full、quality；取得真实结果与独立 Review 才关闭计划。

## 恢复状态

- T1–T3 已完成实现验收；归档时同步修正设计入口链接，保留原验收指纹、时间和历史归档复核记录。

- 最终验收：`./scripts/verify.sh clean`、`./scripts/verify.sh full` 通过，冻结指纹为 `b8d584431299bc4c2294a256edceb4209b40665de2c81f9605b3af865a3b9dc6`。同轮真实 HA 25/25 且清理通过。原件在 `/tmp/imchat-split-retry.5e53ek1e/review/`；`./scripts/verify.sh quality` 校验独立复议 `Accept`，证据为该目录的 `closeout-reissue.raw`、`closeout-reissue-validated.json` 和 `closeout-reissue-quality.log`，原 `Block` 保留。
- T2 关闭证据：后续具体 Adapter 取代早期接口／拆分方案；当前 README、Architecture 和代码以最终冻结输入为准。CLI 端点及原记录重试、learner 的阶段重读与恢复、真实 CLI 后台收尾已由本节的最终证据覆盖；不追认已放弃方案、失败窗口或未执行的专项测试。专属 MySQL 补充验证见下文，不把 full 的条件跳过计为该证据。
- 残余风险：群聊补偿与通知对象继续留在独立设计，不视为修复；CLI 历史端点说明不一致由正式复评列为非阻塞。当前未承诺 scheduler 进程崩溃重启或群聊端到端恢复证据。

- 新增后台恢复测试前的冻结验收：`./scripts/verify.sh full` 退出 0，起止指纹均为 `4dabb61e281fc1c6313e5cbac603aae58dc0bcc56322839ccf0c3f9e2878465c`；同轮 Maven 与真实 Routing HA 通过。`./scripts/verify.sh quality` 的独立复议结论为 `Accept`，保留原 `Revise` 和校验失败回执。原件见 `.harness/verifications/full.json`、`.harness/verifications/routing-ha.json` 和 `/tmp/imchat-full-resume.uQTzpi/review/`。
- 补充验证：专属 MySQL 授权失效与撤销 24 项均通过且无跳过，一次性容器已清理（`/tmp/imchat-iam-mysql.WAxbdd/`）；成员编排、执行器与 Raft Adapter 聚焦测试 153 项均通过（`/tmp/imchat-full-resume.uQTzpi/review/t3-focused.log`）。独立定向复核未发现 T3 必修行为问题（同目录 `adjudication.md`）。
- [x] 在 `im-test/im-e2e-test/src/test/java/com/co/kc/imchat/e2e/OpsCliGovernanceE2ETest.java` 及其 `support/OpsCliGovernanceTopology.java` 补齐真实 CLI 的后台恢复场景。夹具在真实 Raft 提交后暂缓终态提交和定时扫描；CLI 查询为 `RAFT_COMMITTED`，原记录重试返回 `DUPLICATE` 且不推进阶段，恢复扫描后由真实 scheduler 推进同一 operation 至 `SUCCEEDED`。聚焦单例与 `mvn -q -pl im-test/im-e2e-test -am test '-Dtest=OpsCliGovernanceE2ETest' -Dsurefire.failIfNoSpecifiedTests=false` 通过，整类 10/10、无跳过。独立首轮 `Revise` 后将终态延迟限定到本用例的 operation ID，并验证放行前无 `resume`、放行后恰好调用一次真实 `resume`；最终整类仍为 10/10，独立窄复核 `Accept`。证据 `/tmp/imchat-cli-scheduler-final-class.log`、`/tmp/imchat-cli-scheduler-final-review.md`；首轮原件 `/tmp/imchat-cli-scheduler-review2.md` 保留。进程内回归仍分别覆盖 `PENDING` 与 `RAFT_COMMITTED`；新场景不验证进程崩溃重启。未改生产代码或放宽门禁。
- [x] 包含后台恢复测试的冻结版本 `2f8c164dbc7f87901c1d43eb035a011284b11f592f40ce208af4e47ec0a29dc7`：`full` 退出 0，真实 HA 25/25 且清理通过。独立 `quality` 的有效回执为 `Block`，原件与同轮报告见 `/tmp/imchat-final-gate.ATVndx/review/`。不把机器门禁通过视为整体通过。
- [x] Broker 评审项精确修复：`MembershipOperationExecutionTest#retryAdmissionRejectionKeepsPreviouslySubmittedIdentity` 先失败（预期 `CONFLICT`，实际 `ACQUIRED`）；`MembershipOperationService#rejectMembershipAdmission` 仅释放未保留身份，锁外通知与按实例删除保持不变，`BrokerMembershipAppService` 同步注释。`mvn -q -pl im-broker/im-broker-server -am test '-Dtest=MembershipOperationExecutionTest,BrokerMembershipWorkflowTest,BrokerMembershipExecutorTest,BrokerMembershipSchedulerTest' -Dsurefire.failIfNoSpecifiedTests=false` 148 项通过。RED/GREEN 日志为同一 review 目录下 `broker-retention-red.log`、`broker-retention-green.log`。
- [x] Broker 窄修复获独立复核 `Accept`，`git diff --check`、Java style、`./scripts/verify.sh clean` 通过。复核与 clean 原件为同一 review 目录下的 `broker-retention-review.md`、`broker-retention-clean.log`；该结论不替代正式 quality。
- [x] Gateway 独立质量意见：仅修改 WS Gateway 的 `GatewayRouteSnapshotCoordinator.java`、`GatewayRouteSnapshotCoordinatorTest.java` 与模块 README。新增回归先复现一条分片仍在途、同步调用却已抛异常（`/tmp/imchat-gateway-shard-red.log`）；修复后等已提交的分片全部结束，再抛出原异常，不发送整轮 ACK，不改并发上限或 epoch 重试。分片与注册生命周期聚焦测试 11/11（`/tmp/imchat-gateway-shard-green.log`）；`mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server -am test` 重跑退出 0（`/tmp/imchat-gateway-shard-module-retry.log`），首次工具超时不计通过。独立窄复核 `Accept`（`/tmp/imchat-gateway-shard-review.md`），不替代正式 quality。
- [x] Gateway 修复后 `./scripts/verify.sh architecture`、`./scripts/verify.sh clean`、Java style 与 `git diff --check` 通过。架构和 clean 日志为 `/tmp/imchat-gateway-shard-architecture.log` 与 `/tmp/imchat-gateway-shard-clean.log`。
- [x] `im-management/im-admin/src/test/java/com/co/kc/imchat/management/admin/application/ManagedUserAuditDeclarationTest.java` 直接断言密码重置等受审计写操作的命令参数未标注 `@AuditAttribute`。定向测试、`mvn -q -pl im-management/im-admin -am test` 与 Admin UI 的 `npm run test:unit`、`npm run typecheck`、`npm run build` 均通过。日志为 `/tmp/imchat-admin-audit-declaration.log`、`/tmp/imchat-admin-audit-module.log` 和 `/tmp/imchat-admin-audit-ui.log`；仅补已有行为的证据，不改生产代码。
- [x] Ops／Audit 质量意见：`im-management/im-ops/src/test/java/com/co/kc/imchat/management/ops/support/audit/OpsCliAuditTest.java` 覆盖 `ACCEPTED` 与 `REJECTED` 正常返回，验证响应与审计属性保留 Broker `status`，审计 `SUCCESS` 仅表示方法正常返回，既有敏感字段排除断言保留。Ops README 和 Audit SDK README 明确区分两类结果，未改生产逻辑。定向 `OpsCliAuditTest,AuditedAspectTest` 共 14 项通过，`mvn -q -pl im-management/im-ops -am test` 退出 0（`/tmp/imchat-ops-audit-outcome.log`、`/tmp/imchat-ops-audit-module-final.log`）。独立限定复核（`/tmp/imchat-remaining-quality-review.md`）认为结果字段改为全局显式采集属于另行评估的 SDK 契约变更；现有 E2E 文档已区分 SDK 投递、Server 持久化和无重启的 scheduler 恢复，场景索引为非阻塞建议。
- [x] 用户确认将 Social 群变更交接与 Message 通知对象拆为[独立设计草案](../../design-docs/2026-11-18-group-chat-recovery-and-notification-design.md)。当前计划不改群聊生产行为；持久重放缺口及通知对象偏差仍存在，不计作已修复。草案登记设计索引，方案未获批准，不开启第二个实施计划。
- [x] 含修复、审计证据与群聊拆分的冻结版本 `b8d584431299bc4c2294a256edceb4209b40665de2c81f9605b3af865a3b9dc6`：`./scripts/verify.sh clean` 通过；`./scripts/verify.sh full` 退出 0，Maven 3156 项、0 失败、0 错误、24 跳过，真实 HA 25/25 且清理通过。原件见 `/tmp/imchat-split-retry.5e53ek1e/review/`。此前后台启动继承忽略 `SIGINT`，在 Maven 前触发 `parent INT` 自测失败；对照探针和独立自测通过后仅改启动方式重试，保留原件 `/tmp/imchat-split-closeout.63uJP5/`。
- [x] 同一冻结版本执行 `./scripts/verify.sh quality`：回执通过校验并生成快照，结论仍为 `Block`。原始回执 `numbered/response.raw`、按请求并集映射的回执 `numbered/validated-union-response.json`、快照 `quality-snapshot-current.json` 及上一轮回执均保留在上述 review 目录。未改分数、理由和结论，也未缩减证据范围。
- 历史未勾选记录：T2 为 `blocked`。评审仍把群聊的持久交接和通知对象问题列为阻塞，并指出 Account／IAM 关联证据、审计持久化与测试证明的限制。用户已批准拆分，但不等于取得独立 `Accept`；群聊风险未修复。下一步逐条核对哪些属于当前计划的必要验收、哪些属于独立设计或机器证据范围。没有新的处理依据，不重跑全量或重复送审；未达到归档条件，不提交。

- 历史冻结 `full`（Loop #7）在 `im-audit-server` 结束，51 项中 4 项因 HTTP `Connection reset` 报错，未进入真实 HA。失败原件为 `/tmp/imchat-sdk-closeout.T6i8m8/full.log` 和 `/tmp/imchat-audit-reset.5ZYEcA/failed-full.json`；不覆盖本轮机器证据，也不复用历史 HA 结果。
- [x] 隔离 `im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/security/AuditIamSecurityIntegrationTest.java` 的真实 HTTP 端点。失败时 Tomcat 已启动，但请求未触发 DispatcherServlet 初始化；事后同一端口号有另一进程在 IPv4 回环监听。受控 socket 探针复现通配监听与回环监听共用端口号时，请求可能落到另一监听器并被重置（`/tmp/imchat-audit-reset.5ZYEcA/loopback-probe.log`）；未捕获原失败时的连接去向。只让该夹具绑定并访问 `127.0.0.1`，仍由系统分配端口，不增加 HTTP 重试、不改生产代码或四条原断言。
- [x] 使用 `full` 的 `HOME`、`JAVA_HOME`、`PATH` 三键环境运行 `mvn -q -pl im-management/im-audit/im-audit-server -am test '-Dtest=AuditIamSecurityIntegrationTest' -Dsurefire.failIfNoSpecifiedTests=false` 与 `mvn -q -pl im-management/im-admin,im-management/im-audit/im-audit-server -am verify`，均退出 0；Audit 单类 4/4、模块 51/51。日志为 `/tmp/imchat-audit-reset.5ZYEcA/fixed-class.log` 与 `/tmp/imchat-audit-reset.5ZYEcA/fixed-admin-audit-verify.log`，不替代完整门禁。
- [x] 历史阻断：本机当时没有 `eclipse-temurin:21.0.12_8-jre-jammy`；按现有 HA 代理约定查询镜像 manifest，限时复核返回 Docker Hub registry `EOF`（`/tmp/imchat-audit-reset.5ZYEcA/ha-jre-manifest-bounded.log`）。未改 Docker、代理或镜像配置，未启动新一轮 `full`。
- [x] 续跑前置条件已恢复：`docker pull eclipse-temurin:21.0.12_8-jre-jammy` 退出 0，digest 为 `sha256:61d6c7b34d36aee3f45d043101259f97f3c6d428dc2a6f75513789983c5e254f`。保留原 Docker、代理和镜像配置。
- 历史未勾选记录：冻结输入执行 `clean` 和一轮 `full`；以同轮 Maven 与真实 HA 报告判定完整验收，独立正式 quality 通过后才审查状态迁移。

- 上轮阻断（历史）：当时 T2 `blocked`；T1 `blocked`、T3 `not_started`。2026-11-18 上轮 `./scripts/verify.sh full` 在 `im-broker-sdk` 退出 1，未进入真实 HA。Surefire 发现已迁移的 `MembershipPermitDrainedResponseTest` 残留在 `target/test-classes`，因旧生产类型不存在报 `NoClassDefFoundError`；SDK 源码已无旧类型引用。原件 `/tmp/plan-close-final-full.log` 与本轮恢复前保存的 `/tmp/imchat-sdk-closeout.T6i8m8/failed-full.json` 保留；同轮 `worktreeStable=true`、`gateStatus=failed`、`finalizationStatus=passed`。Loop #6 已停止，旧 full/HA 通过仅属于 SDK 重命名和文档更新前的输入。
- [x] 恢复：`mvn -q -pl im-broker/im-broker-sdk -am clean test` 退出 0，SDK 52 项通过，无失败、错误或跳过。确认 `target/test-classes` 不再包含旧响应测试字节码，保留清洁重建日志和上轮失败报告于 `/tmp/imchat-sdk-closeout.T6i8m8/`。未改源码或门禁。
- 历史未勾选记录：当前 T2 `active`，T1 `blocked`、T3 `not_started`。重新执行 clean 后冻结本轮 full/HA；仅在取得同轮通过证据后进入正式 quality 与精确状态迁移 Review，不提前归档。
- 历史 full 已结束且失败（`.harness/verifications/full.json`：1939秒、worktreeStable=true、gateStatus=failed、finalizationStatus=passed），IAM UI已成功安装构建，失败在Ops149项中10项。用户批准继续；准确修复范围仅 `im-management/im-ops/src/test/java/com/co/kc/imchat/management/ops/interfaces/http/OpsCliLeaderHintMappingTest.java` 与 `support/audit/OpsCliAuditTest.java`：七个Leader请求和三个审计请求及精确Command fixture补齐membershipEndpoint，保留所有原断言并增加端点保真。前轮full原件 `/tmp/closeout-resume-full-retry.log` 为RED证据，不改生产校验。
- [x] 聚焦 `mvn -q -pl im-management/im-ops -am test '-Dtest=OpsCliLeaderHintMappingTest,OpsCliAuditTest' -Dsurefire.failIfNoSpecifiedTests=false` 10/10通过（`/tmp/closeout-ops-fixtures-green.log`）；完整 `mvn -q -pl im-management/im-ops -am test` 退出0（`/tmp/closeout-ops-module.log`）。两文件只补精确请求及Command端点，原断言不弱化；独立窄Review Accept/findings none（`/tmp/closeout-ops-fixtures-review.txt`），不代表整体验收。
- 历史未勾选记录：本次定稿后重新 `./scripts/verify.sh clean`、`./scripts/verify.sh full`；通过后才继续quality及状态迁移，保留旧full失败。
- 历史恢复：用户要求继续推进；readiness 与 model-execution 来源检查通过，确认无旧 full 进程。使用该来源产生的 HOME/JAVA_HOME/PATH 三键空环境，在 IAM Server ui 执行 `npm ci`，退出0（`/tmp/closeout-resume-npm-ci.log`），未改代理／镜像源／用户配置。仅证明此次依赖安装成功，不晋级旧 full；下一步冻结后重新 `./scripts/verify.sh clean`、`./scripts/verify.sh full`。正式 quality 和状态迁移 Review 尚待完整验收。
- 本轮关闭账本：T2 的实现与文档步骤已由后续逐项增量取代；历史未勾选的 clean/Review/full 重复项保留原始失败，不逐条冒充通过。当前统一退出条件：当前源码的 Broker 模块回归、真实 OpsCliGovernanceE2ETest、正式 clean/full/HA、独立当前实现 Review；真实 MySQL 的新增修复需确认可用的专属环境，不以跳过充数。Builder alias 的既有风险保留扫描防线，不扩展无关重构。
- 本轮步骤与准确范围：仅本计划、PROGRESS、active README 整理当前状态和验收；若发现生产或测试问题，先记录准确文件和 RED/GREEN，再修复，不以收口授权扩大功能。
- [x] readiness 通过，Docker Engine 可用；目标 JRE tag 本机缺失，旧镜像网络风险仍需实际验证。
- [x] 复核 learner 首次／重复／冲突、UNKNOWN、generation、fault/close、永久预算与恢复测试，当前契约由 Workflow 的历史只读/双向 IN_PROGRESS/恢复无取证、Executor 的 fault/close/4096预算回归承担；不以旧共享结果命名推断当前行为。
- [x] 独立 Review `/tmp/closeout-independent-review.txt` 为 Block，必须保留：Executor.finishSend 持锁通知；第4096个合法槽使已有 continuation 被拒绝。窄修复范围 `infrastructure/cluster/BrokerMembershipExecutor.java`、`application/cluster/BrokerMembershipExecutorTest.java`（测试路径）、Broker Architecture/Server README、治理设计与Harness Guide/矩阵。先定向 RED，最小锁外通知及独立有界 continuation 队列修复，不改永久槽/新准入/generation/worker/fault-close契约，再回归并复审。
- 修复证据：新 Executor 三调用先 RED（`/tmp/closeout-executor-red.log`），六组 GREEN（`/tmp/closeout-executor-green.log`）；完整 Broker 模块重跑通过（`/tmp/closeout-broker-recheck.log`）。独立复审 `/tmp/closeout-followup-review.txt` 对生产两项 Accept、整体 Revise：ACK 证据可能被 timeout 移除冒充；已在原生写前捕获 InvokeFuture 并核对完成响应与真实 ACK 对象相同，聚焦通过（`/tmp/closeout-ack-positive.log`），待最终复审。设计索引的未实施陈述已纠正。
- 模块首轮 `/tmp/closeout-broker.log` 在 im-bolt `BoltOutboundOriginsGateTest.nativeIdleHeartbeatAndAckRemainUsable` 失败：ACK future 未被消费，未到 Broker；先独立复现和上下游时序归因，不弱化断言。
- [x] 执行 `mvn -q -pl im-broker/im-broker-server -am test` 和 `mvn -q -pl im-test/im-e2e-test -am test '-Dtest=OpsCliGovernanceE2ETest' -Dsurefire.failIfNoSpecifiedTests=false`；任何失败先归因。Broker 模块通过；治理整组9/9通过（`/tmp/closeout-governance-e2e-final.log`）。首轮与等待试改均失败在清理：真实角色已LEARNER，fixture只删除request.json而漏confirmation.json；已精确补清理并设置CLEANUP阶段，撤回等待和调试输出。新增范围 OpsCliGovernanceTopology.java、OpsCliGovernanceE2ETest.java、BoltOutboundOriginsGateTest.java；Bolt只修异步完成取证不改生产。未强制历史PENDING/RAFT_COMMITTED跨进程scheduler交错，保留证据缺口。
- [x] 整体验收已取得：最终独立代码级 Review `/tmp/closeout-final-code-review.txt` 为 Accept、无必修项，不等于完整验收；clean/style 通过（`/tmp/closeout-clean-final.log`、`/tmp/closeout-style.log`）。本轮 `./scripts/verify.sh full` 的工具等待1800秒超时后，原进程继续执行，随后实际 Maven 在 `im-iam-server` 的 `install-iam-ui` 退出1：npm ci 下载 whatwg-mimetype-4.0.0.tgz 发生 read ECONNRESET。原件 `/tmp/closeout-full.log`、`.harness/runtime/full-maven.log`、`~/.npm/_logs/2026-11-16T12_29_19_341Z-debug-0.log`。已确认本轮 verify/model-verify/Maven/npm 进程全部退出，Loop #1停止；未启动重复构建、未到HA、未改用户网络配置。`.harness/verifications/full.json` 仍为原 incomplete 启动记录，不能手改为新failed或passed；以本轮实际日志证明失败，保留记录未最终刷新这一证据缺口。恢复前先在相同受控环境证明依赖下载已恢复，再冻结并完整重验，不跨轮复用本轮部分报告。最新完整证据为 `.harness/verifications/full.json`（passed，3152 tests、0 failures、0 errors、24 skipped，Routing HA 25/25）；后续文档修改会改变指纹，必须重新运行适用廉价门禁或重新冻结 full，不能把该证据自动绑定到后续文档状态迁移。
- 历史未勾选记录：原始接受与 Review 均成立后再迁移 T1/T2/T3 状态与归档；按两时点规则做精确状态 Review、廉价 drift/clean、quality scope、cleanup/handoff。
- 已完成：诊断/重传任务输入及 5 个新行为场景通过；成员缓存错误已由现有回归证明并修复；增加 UNAVAILABLE 显式重试断言。
- 验证：readiness 通过；最终窄命令通过，执行器 41、Workflow 75、Admission 16，BrokerClusterAppServiceChangeTest 通过；CLI 60/60，治理 E2E 8/8。
- 阻塞：第一轮模块命令曾在 300 秒与 240 秒窗口终止；后续诊断运行完整结束并取得 `BUILD SUCCESS`，耗时 5 分 21 秒，采样期间显示上游 Bolt/锁等测试并非死锁，JRaft/Broker 隔离测试也通过。最后一次重跑在外部 420 秒窗口终止于 Nacos 清理日志，未取得第二份成功报告，归因仍限定为验证窗口/外部资源条件，不改生产代码。不能用窄通过替代最终完整报告。
- clean：首次因新计划缺少 `设计或产品规格` 固定字段失败，补齐后通过，原件 `/tmp/membership-clean-recheck.log`。无 checker 放宽。
- 本轮恢复：重新执行 readiness、clean 与 drift 通过；历史 `/tmp/membership-raft-isolation.log` 为 RaftRuntimeTest、RaftConfigurationIntegrationTest、BrokerRaftAdapterTest 隔离测试成功，不是完整模块测试。learner 函数接缝试改已精确撤回，未保留 T2 实现。试改期间四组窄回归通过，但不作为撤回后冻结验收。
- 新证据：最终 generation guard 后 `mvn -pl im-broker/im-broker-server -am test` 于 14:16:22 `BUILD SUCCESS`，耗时 5 分 15 秒。
- full 证据：`./scripts/verify.sh full` 后续重跑已通过 Maven/Harness 及治理 E2E；最终仅 routing-ha Compose 构建因 Docker Hub 镜像拉取超时失败。
- Review 历史：最初两项 Block 中，同步异常 pending 的定性后被 Reviewer 撤回；该行为符合批准设计，未保留 UNKNOWN 提前完成。缺失 reservation 创建事实槽的问题已修复；最终窄命令 177 场景通过。
- TOKEN_EXCHANGE 根因已证明是 CLI 响应模型拒绝 OAuth 标准可选 `scope`，不是已证实的环境问题。新增 scope 后 CLI 60/60 和真实三 voter 单场景通过。治理整组首次 7/8，剩余旧 401 异常断言已按现有 CLI 契约修正；再次整组 7/8 唯一失败是 Ops 启动端口 BindException，该场景单独重跑通过。最终整组 8/8（162.4 秒），日志 `/tmp/token-scope-e2e-class-recheck.log`；独立增量 Review Accept 的整组条件已满足。
- 历史 full：Harness 自测、完整 Maven 验证与 clean/drift 均完成；routing-ha `compose-up` 失败，Docker 无法从 `registry-1.docker.io` 获取 `eclipse-temurin:21.0.12_8-jre-jammy`，报 `DeadlineExceeded`。未修改 Docker 配置或生产代码。
- 复核：直接执行 `docker pull eclipse-temurin:21.0.12_8-jre-jammy` 仍报 Docker Hub 连接超时；本机仅缓存 JDK 镜像，不缓存所需 JRE tag。该阻断可归因外部镜像网络，不归因源码。
- 历史下一步：先完成 T2 聚焦验证并供用户 Review；之后再按用户安排恢复最终验收，保留永久预算、generation 和 worker 结果发布。
- 不要修改：与本任务无关的用户工作树；不使用旧 `/tmp` 备份覆盖整文件。

## 回滚与残余风险

- 回滚仅逆向本轮精确差异，不恢复 HEAD、不覆盖用户已有修改。
- 完整门禁仅证明冻结输入及已覆盖场景，不证明所有一致性交错或生产环境健康。
- 归档仅更新执行状态和文档导航；原冻结证据仍归属实现验收时点，不作为归档后 fresh 证明。
