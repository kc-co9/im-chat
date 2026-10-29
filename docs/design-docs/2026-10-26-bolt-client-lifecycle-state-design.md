# Bolt 受控客户端轻量生命周期状态设计

> 状态：已批准，实施中。用户已明确授权处理；独立设计 Reviewer `a46adc81a21ae2558` Approved，必须项 none，已核对真实源码及原生父类。四态方案纳入现有 T3，允许生命周期/后台窄验证；clean/full/commit 继续暂停，既有 DECODE 失败不据此视为修复，不扩大至模块/架构验证。设计批准不代表实现 Review 或验收通过。

## 后续增量取代

[生命周期串行化增量](2026-10-26-bolt-client-serialized-lifecycle-design.md) 的最终 ReentrantLock/固定 5 秒 tryLock 方案已获独立设计 Reviewer `a3b3f5a6c96a74ee1` Approved。本文四态、构造 null 放行及唯一 Tracker.closed 保留；owner/future、初始化期取消、有界回滚通知及运行期并发提前返回等冲突契约由该增量取代。本文原批准和旧验证事实不改写，不作为新契约验收。

## 摘要

以 Client 私有四态 `READY / INITIALIZING / RUNNING / TERMINATED` 替换 `assemblyFrozen / ownedStarted / initializing`，集中表达装配冻结后的生命周期阶段和资源清理责任。构造期间保留 Java 默认 `null`，兼容原生父类构造动态调用 `option`；构造完成才进入 READY。

`BoltClientCallTracker.closed` 仍是唯一关闭准入状态，Tracker 实例仍是生命周期与调用记账共享的唯一 monitor。枚举不复制 closed，不引入通用状态机框架、额外 future、包装接口或新协作者。初始化线程保留成功提交前的回滚责任，运行态 shutdown 保留提交后的原生关闭责任；不以无来源的 CLOSING 合并二者。

本设计只取代此前[职责拆包设计](2026-10-25-bolt-responsibility-packages-design.md)中“不新增状态机”非目标在 `BoltControlledClient` 三个布尔字段范围内的限制。其余拆包、Tracker、Factory 回查、预算、安全和 API 决定不变。用户现有 Handler 修改完整保留。

## 背景

Client 当前以三个布尔字段分别控制 option 冻结、初始化进行中以及原生关闭责任是否提交。`initializationOwner` 防止初始化线程在 shutdown 中等待自身，`initializationSettled` 通知其他停机线程初始化及必要回滚的结果。它们共同约束 init/shutdown 竞态，不能只按字段名机械合并。

事实源为根 `ARCHITECTURE.md`、`PROGRESS.md`、`im-plugin/AGENTS.md`、`im-plugin/im-bolt/README.md`、当前 active 计划、[调用追踪设计](2026-10-25-bolt-client-call-tracking-design.md)、[Factory 依赖评估](2026-10-25-bolt-factory-dependencies-design.md)、职责拆包设计，以及当前 Client、Tracker、Manager 和生命周期测试源码。第三方构造及关闭行为已只读核对本机 SOFA Bolt 1.6.6 sources jar。

## 目标与非目标

- 目标：三个布尔状态收敛为一个私有枚举字段，保留原有线性化点、可见性、原生清理责任、异常及等待语义。
- 目标：构造成功后所有阶段均拒绝外部修改 option；初始化成功提交和 shutdown 关闭准入仍在同一 monitor 原子交接。
- 非目标：不改变 caller/共享创建预算、TLS/DEV、目标策略、失败分类、资源上限、原生 future 完成协议或 Handler 校验。
- 非目标：不修改 Tracker、Manager、Factory、Invoker、BoltRpcClient 的生产实现，不增加第二份 closed、状态框架、无实际调用 wrapper、测试专用生产入口或新线程。
- 非目标：不修复现有所有生命周期边界，不承诺每个并发 shutdown 都等待物理关闭或接收同一清理异常，不扩大同 JVM 安全保证。
- 非目标：不关闭或归档当前 T3，不用局部证据替代正式 quality、完整验收或既有失败归因。

## 已知事实与约束

- 所属模块为通用基础设施 `im-plugin/im-bolt`；模块没有独立 ARCHITECTURE，本设计不新增架构文档。
- SOFA Bolt 1.6.6 `AbstractBoltClient` 构造器动态调用两参数 `option` 设置 reconnect 和 monitor。此时子类字段初始化尚未执行，`state` 与 `calls` 都为 null。
- Tracker 的 private volatile closed 是唯一关闭准入标志；admit、init 提交和 shutdown 关闭准入使用同一个 Tracker monitor。
- 当前 acquire 锁外读取 volatile ownedStarted 和 closed。替换字段须保留 volatile 发布，不能失去 Manager 和装配的可见性。
- readiness 已实际 exit0，Ruby PATH warning 保留；本轮设计写入不重跑 readiness 或构建。后续 Maven 使用 `/tmp/jraft-artifact-m2` 严格串行，先确认本仓无并发 Maven。
- 用户暂停 clean/full/commit；当前只授权设计文档。PROGRESS 中已有 DECODE handled expected1/actual0 失败尚未确认根因，后续一次临时诊断通过不能作为修复证明。

## 方案概览

Client 内建议定义 `private enum LifecycleState { READY, INITIALIZING, RUNNING, TERMINATED }` 和 `private volatile LifecycleState state`，替换三个布尔字段。

| 状态 | 含义 | 关闭责任 |
|---|---|---|
| null | 仅构造期间，尚未冻结 option | 尚未进入实例生命周期 |
| READY | 构造完成，未进入 init | shutdown 可永久关闭该实例 |
| INITIALIZING | 初始化或必要回滚尚未结束 | initializationOwner 持有回滚责任 |
| RUNNING | 初始化成功提交 | 首个取得关闭准入责任的 shutdown 持有原生关闭责任 |
| TERMINATED | 生命周期结束，清理尝试已结束或未启动即关闭 | 不重启、不自动重试清理 |

TERMINATED 不表示资源已全部成功关闭。清理失败仍按原异常协议报告，不因终态名称制造更强保证。

Client 继续拥有 initializationOwner、initializationSettled、策略快照和 Manager。Tracker 继续拥有唯一 closed、在途计数、排空和完成包装。局部 managerAllocated、clientInitializationEntered、cleanupFailure 是一次初始化的资源进度和异常记录，不并入全局枚举。

## 详细设计

### 构造期与 option 冻结

state 不设置字段初始化值；在原构造末尾 `assemblyFrozen=true` 的位置赋 READY。两参数 option 的冻结条件替换为 `state != null`，保留原异常及 super 委托。单参数 option getter 不改，构造内部已有 `super.option` 调用不改。

不得 switch(null)、不得只允许 READY 修改 option，也不得在 option 中同步 calls：父类构造回调时 calls 尚不存在。构造返回后所有非 null 状态均拒绝修改 option，不为 READY 留出外部装配窗口。

### 状态转换与线性化

除构造末尾赋值外，下表所有状态迁移及关闭准入判断均在 `synchronized(calls)` 内完成。

| 起点 | 事件及前提 | 终点 | 准入及责任动作 |
|---|---|---|---|
| null | 构造装配结束 | READY | 冻结 option |
| READY | init 且 !closed | INITIALIZING | 记录当前线程为 initializationOwner |
| 非 READY 或 closed | init | 不变 | 抛原 lifecycle already entered 异常 |
| INITIALIZING | 原生 init 和 Tracked 装配完成且 !closed | RUNNING | 原子提交；清除 initializationOwner，关闭责任交给 shutdown |
| INITIALIZING | shutdown 或 init catch | INITIALIZING | closeAdmission，保留 initializer 回滚责任 |
| INITIALIZING | 初始化失败后的实际回滚尝试结束 | TERMINATED | finally 清除 owner，随后按原结果通知 settled |
| RUNNING | 首个 shutdown 且 !closed | RUNNING | closeAdmission，选定本次 shutdown 在锁外关闭原生资源 |
| RUNNING | 上述关闭尝试结束 | TERMINATED | shutdown finally 终结，不因关闭异常重开准入 |
| READY | shutdown | TERMINATED | closeAdmission，未初始化实例永久不可用 |
| TERMINATED | shutdown | TERMINATED | 幂等返回 |

INITIALIZING+closed 表示初始化 owner 仍在完成或回滚；RUNNING+closed 表示运行态 shutdown 已取得关闭责任，或可信技术调用已单独关闭准入。closed 本身不代表物理清理完成，枚举不得成为第二份准入标志。

### init 成功与失败

init 入口只允许 READY 且 !closed，随后在锁外保留现有 Factory 构造、Manager 构造/startup、原生 Client init 和 TrackedRpcClientRemoting 装配顺序。成功提交时在同一 monitor 内先检查 closed，再将状态改为 RUNNING 并清除 owner。

catch 保留 closeAdmission、原生 Client/Manager 关闭条件、RuntimeException/Error 传播和 suppressed 异常处理。finally 仅在 state 仍为 INITIALIZING 时将其改为 TERMINATED；不能无条件终结，否则会覆盖成功提交的 RUNNING，或干扰已接管的 shutdown。初始化 owner 清除及 settled 通知保持现有顺序。

initializationSettled 仍在实际回滚尝试结束后完成：清理成功则 complete(null)，仅清理失败时 completeExceptionally(cleanupFailure)。初始化主异常由 init 调用者接收，不自动广播成 shutdown 失败。

### shutdown 与并发

初始化 owner 自己调用 shutdown 时，只在共享 monitor 内 closeAdmission 后返回，保留 INITIALIZING 和 owner，不等待自身。其他线程在 INITIALIZING 中关闭准入后，退出 monitor，按现有 `connectionCreationTimeout.toMillis()+1000` 毫秒等待 settled。超时不夺走 initializer 责任；中断恢复标志并抛原异常，ExecutionException/TimeoutException 保留原包装。

运行态首个 shutdown 在共享 monitor 中关闭准入并确定本次关闭责任，锁外执行 super.shutdown，finally 在 monitor 内终结状态。责任可由局部布尔值明确捕获，不新增持久化标志；不能锁外根据一个已经丢失来源的 CLOSING 猜测该调用应关闭原生 Client 还是等待 initializer。

保留原 `closed && 非初始化中` 的提前返回语义：后续运行态 shutdown 不重复原生关闭，也不新增关闭完成 future 或额外等待。原生 init/shutdown、Manager 清理和 settled.get 全部保持锁外；Tracker 的 drain/finish 实际 Channel.close 也保持锁外。

### 运行检查

acquire 将 ownedStarted 检查替换为 state==RUNNING，保留 calls.isClosed、目标策略和 DEV 校验。isRunningControlledAssembly 同样替换状态条件，保留原生 isStarted、Manager 非空/started、同源 Manager 和 !stopped 检查。volatile state 保留原锁外发布语义；不增加状态查询公共 API。

### 保留的原有边界

1. init 失败但回滚成功时，已等待的 shutdown 可成功返回；回滚失败时 init 保留主异常并附 suppressed，等待者经 settled 接收清理失败。
2. 运行态第二个 shutdown 可能在首个物理关闭完成前返回，不复述首个调用的关闭异常。
3. 初始化 finally 清除进行中状态与 settled.complete 之间存在短窗口；后到 shutdown 可直接返回。清理失败结束后再调用 shutdown，也不会重新读取 settled failure。本设计保留顺序，不增加全员失败广播。
4. 当前 rollback 仅在 Manager 已 started 时关闭它。构造器创建 ThreadPoolExecutor 对象不等于线程已经启动；构造后、startup 前失败的完整回收未证明，不据此宣称现有线程泄漏，也不改变清理条件。
5. 可信 JVM 代码可单独调用 Tracker.closeAdmission；当前随后 shutdown 可能按原 closed 提前返回。本设计不修订这一技术 API 边界。
6. 继承的公开原生 startup 等特权生命周期入口不在此次重构范围；四态不提供恶意同 JVM sandbox。

## 可靠性与安全

shutdown 与 admit 的准入线性化点仍是共享 Tracker monitor 内的 closed 单向写入。此前已登记调用仍按原生实际终结释放额度，shutdown 不清零计数，也不因 closed 跳过迟到完成。

初始化成功提交前后各有唯一清理责任，不允许 CLOSING 抹去其来源。没有新增重试、定时器、依赖、日志敏感字段或配置。认证、授权、typed 屏障、资源上界和预算保持原约束；不承诺性能提升或硬返回期限。

## 运维与交付

没有 wire、配置键、公开方法签名或持久化迁移，不新增部署步骤。外部业务仍通过既有 BoltInvoker/BoltRpcClient 调用。仅内部状态表达变化，不能当作生产上线或完整验收证明。

本轮准确写入范围只有本文。待独立设计 Review 无必须修订项且获明确实施授权后，最小生产范围为 `im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/client/BoltControlledClient.java`；新增回归优先位于 `im-plugin/im-bolt/src/test/java/com/co/kc/imchat/plugin/bolt/connection/BoltConnectionLifecycleTest.java`。既有 `connection/BoltProductionBackgroundTest.java` 的原生 option 构造场景优先复用，仅真实缺口才追加断言。

## 风险与回滚

- 风险：父类构造时错误拒绝 null、提前结束初始化责任、init finally 覆盖成功状态、并发重复原生 close、失去 volatile 发布，以及把 TERMINATED 误读为清理成功。
- 回滚：仅恢复本轮三个字段及对应检查/转换和实际新增测试差异，不覆盖混合工作区或 Handler 用户修改。
- 延后：既有 DECODE 失败归因、未启动 Manager 回收证明、全员 shutdown 等待/失败广播、正式 quality 和完整验收。任何边界修复都需另行确认，不能以重构名义并入。

## 验证策略

以下为待授权的验证矩阵，不是已运行结果。新增纯重构场景先在旧实现建立基线，不要求凭空产生 RED；关键错误实现可用窄变异反证。

| 不变量/边界 | 验证位置与观察 |
|---|---|
| 父类构造可调用 option，构造后不可改 | 复用 ProductionBackgroundTest.nativeBackgroundOptionsAndPublicCreationEntrypointsStayClosed：原生全局 reconnect/monitor=true 时仍可构造受控 Client，固定开关且拒绝外部开启 |
| 未启动即关闭不可重启，重复/并发 init 不夺 owner | LifecycleTest 通过公开 init/shutdown、运行检查和原生状态断言 |
| 关闭准入先于 held admission，并发 shutdown 不重复清理 | 复用 shutdownLinearizesBeforeHeldAdmissionAndConcurrentShutdownIsIdempotent |
| 初始化期关闭等真实回滚，owner 不自等待 | 复用 closeDuringNativeInitializationWaitsForOwnerRollback；新增 owner 重入 shutdown、两个等待者及受控回滚释放场景 |
| 成功提交只交接一次责任 | 复用 successfulInitializationCommitHandsNativeCloseToShutdown，核对原生 Client/Manager 最终停止 |
| 初始化主失败、回滚成功/失败通知不混淆 | 通过现有公开协作者边界注入故障，检查原异常、suppressed、等待者结果及清理完成前不通知 |
| 等待中断/timeout 不接管回滚 | 有界等待、恢复中断标志、释放 initializer 后核对最终停止；不改变等待预算 |
| 运行关闭失败后仍不可重新准入或重复 native close | 公开关闭故障边界及重复 shutdown；不要求其他调用广播同一失败 |
| Tracker 完成/排空和策略未变 | 复用生命周期、原生完成、TLS、资源、后台禁用及 UnifiedInvoke 回归；静态核对未改这些生产文件 |

测试优先沿现有 Mockito spy 单参数 option(NETTY_FLUSH_CONSOLIDATION) 的真实初始化观察点，使用 latch、有界 future 和真实资源断言，不反射私有 state、不增加生产 seam。若现有公开协作者不能可靠注入精确 cleanup 故障，应先报告证据缺口并修订测试设计，不新增生产资源阶段来迁就测试。

根包 BoltBackgroundLifecycleGateTest 的逆序回收测试使用自有 Endpoint 编排；BoltAuthenticatedTlsTest.endpointInitializationFailureRollsBackActualAllocatedResources 在 Client 初始化成功后由 Endpoint 注入失败。两者不能冒充生产 Client.init 各失败分支覆盖。

待恢复构建权限后，从仓库根严格顺序运行，先失败先归因，逐轮保存完整 argv、UTC、exit 和 same-run XML：

```bash
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=com.co.kc.imchat.plugin.bolt.connection.BoltConnectionLifecycleTest,com.co.kc.imchat.plugin.bolt.connection.BoltProductionBackgroundTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=com.co.kc.imchat.plugin.bolt.invocation.BoltUnifiedInvokeTest,com.co.kc.imchat.plugin.bolt.invocation.BoltAuthenticatedFailureTest,com.co.kc.imchat.plugin.bolt.connection.BoltConnectionLifecycleTest,com.co.kc.imchat.plugin.bolt.connection.BoltAuthenticatedTlsTest,com.co.kc.imchat.plugin.bolt.connection.BoltProductionResourcesTest,com.co.kc.imchat.plugin.bolt.connection.BoltProductionBackgroundTest,com.co.kc.imchat.plugin.bolt.client.BoltDevelopmentModeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
```

静态 Review 检查三个旧字段消失、Tracker 唯一 private volatile closed、共享 monitor、责任提交与物理关闭位置、init finally 不覆盖 RUNNING。窄变异可分别破坏构造 null 放行、回滚前提前 TERMINATED、finally 无条件覆盖 RUNNING、重复 native close，确认关键断言能捕获。

已有 DECODE 失败须先归因或明确其证据缺口，不用更宽层通过覆盖。clean/full/commit 继续暂停；最终用户恢复集中窗口后，完整门禁及独立实现 Review/正式 quality 才能提供整体验收，当前命令清单不代表已执行。

## 后续文档与执行

独立设计 Review 完成前只保留本文待 Review 状态。按本次明确授权，不改设计索引、现有 active 计划或 PROGRESS。

获批并恢复实施后，再登记设计索引，将准确文件与验证步骤追加到 `docs/exec-plans/active/2026-10-25-bolt-package-boundaries.md` 的现有 T3，并同步 PROGRESS，保持唯一 active。实施时最小更新 im-bolt README 的内部状态事实及既有职责设计的限定取代说明；根 Architecture、Reliability/Security 保证不变，不机械改写。此处是内部状态表达，不新增通用工程规则、Harness checker 或类名豁免；若独立 Review 发现可复用规范，按仓库规则另行明确拥有者和反证。

## 附录 A：备选方案与行业依据

- 保留三个布尔字段：零转换风险，现有语义清楚可复核；代价是组合状态和责任关系分散，不满足用户已批准的枚举方向。
- 四态枚举加唯一 Tracker.closed（推荐，细节待 Review）：最小收敛阶段状态，保留既有 owner/future 和资源局部变量；代价是必须明确 closed 与清理责任不是同一事实。
- 显式细分初始化关闭/运行关闭状态：可表达更多清理阶段，但与已有 closed 形成更多组合，若再加关闭完成等待会改变行为；当前没有该需求，不采用。单一不带责任来源的 CLOSING 不是可接受简化。

第三方依据限于本仓库当前 SOFA Bolt 1.6.6 的 AbstractBoltClient、AbstractLifeCycle 和 RpcClient 源码及既有真实原生测试；不引入新库能力或外部性能主张。

## 附录 B：决策记录

| ID | 来源/问题 | 决策 | 理由与影响 |
|---|---|---|---|
| D1 | 用户批准轻量枚举方向 | 建议 READY/INITIALIZING/RUNNING/TERMINATED 替换三个布尔字段，四态细节待 Review | 不引入框架，仅限定取代此前 Client 不新增状态机非目标 |
| D2 | 父类构造动态调用 option | null 仅表示构造未冻结，非 null 一律冻结 | 此时 calls 也为 null，不能同步或 switch null |
| D3 | 准入与清理完成不是同一事实 | Tracker.closed 唯一管准入，Client enum 管阶段/责任 | 同一个 calls monitor，禁止第二份 closed |
| D4 | 初始化关闭不能丢失 rollback owner | closed 后保留 INITIALIZING 至回滚尝试结束 | owner 不自等待，其他调用保持原有界等待 |
| D5 | 运行关闭不能被 init finally 覆盖 | 成功提交为 RUNNING，shutdown finally 才终结运行责任 | 不新增并发 shutdown 完成/失败广播 |
| D6 | 本轮授权范围 | 只新增本文，保留 Handler 和全部混合改动 | 不改索引计划源码，不构建或提交，不伪称已验收 |

## 附录 C：独立 Review

独立设计 Reviewer `a46adc81a21ae2558`：Approved，必须项 none。已逐项核对真实 Client/Tracker/Manager 和 SOFA Bolt 原生父类，包括构造 null 放行、共享 monitor/唯一 closed、两类关闭责任、init finally 条件、settled 完成时机和并发 shutdown 原语义。用户已明确授权实施；批准仅覆盖本设计，不代表实现 Review 或验收通过。无法通过现有公开边界可靠注入的故障场景保留证据缺口，不为测试增加生产 seam。
