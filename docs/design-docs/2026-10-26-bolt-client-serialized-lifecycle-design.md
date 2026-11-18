# Bolt 客户端生命周期串行化增量设计

> 状态：已批准，实施中。最终 ReentrantLock 与固定 5 秒 tryLock 方案经独立设计 Reviewer `a3b3f5a6c96a74ee1` Approved，必须项 none；本轮进入新契约 RED/GREEN 及 lifecycle/background 窄验证。旧四态 Reviewer `a46adc81a21ae2558` 的 Approved 只作历史，不替代本次 Review。设计批准不表示实现验收通过；clean/full/commit/module/architecture 继续暂停，旧 DECODE 缺口保留。

## 2026-10-29 Review 修复决定：关闭失败延后传播（已批准，实施中）

独立设计 Reviewer `a1f91e577618ebd65` Approved，必须项 none；只批准本增量设计，不是实现或完整验收 Accept。真实中断回归旧实现已 RED：处理器关闭次数预期 1、实际 0，原异常及中断断言此前通过。

当前收口 Review 发现：正常 RUNNING Client 的受控 Manager 在等待创建线程中断/超时后抛错，会使 Bolt 1.6.6 的 RpcClient.shutdown 跳过 private scanner 和 UserProcessor 的后续清理；Client 随后终态化，无法补偿。本增量修复此确定控制流，不扩展为原生部分 startup 或任意 UserProcessor 自身失败的通用回收保证。用户要求尽最大努力完成当前计划，此修复仍归现有 T3，不实施官方 JRaft 延期方案。

推荐在 Manager 新增仅供拥有者调用的 `shutdownOwnedClient(Runnable nativeClientShutdown)`，以实例 ThreadLocal 保存本次同步关闭的异常作用域；拒绝同线程嵌套。Client 的 CLOSE_CLIENT 路径（及实际调用原生 Client shutdown 的初始化回滚路径）将 `super.shutdown()` 作为同步回调传入。回调不逃逸、不换线程、不成为可注入 transport。Manager.shutdown 仍执行停止准入、等待 creation、原生管理器关闭；只有同一线程在该拥有者作用域内时，暂存 RuntimeException/Error 并返回，使原生 Client 继续关闭其 private scanner 和处理器；直接 Manager.shutdown 保持抛错契约。作用域最终清除，随后重抛首个 Manager 异常；若原生后续清理又失败，后者作为 suppressed，避免 self-suppression；Manager 无失败时原样传播后续异常。中断标志始终在 finally 恢复，即使原生管理器关闭也抛错。无重试、无第二份 closed、无反射、不复制 native processor/scanner 所有权。

测试先证明旧实现：使用现有真实 TLS hold 与公开注册的 UserProcessor，确认 shutdown 线程实际进入 Manager awaitTermination 后中断，断言原异常/中断位、处理器已关闭（意味着原生流程越过 scanner.shutdown）、终态拒绝新调用及迟到候选不发布。额外覆盖直接 Manager 仍抛错、作用域清除、后续异常 suppression。所有等待有界，不用 sleep 猜时序。只保证其他 native 清理得到执行机会，不保证全部资源物理终止或任意处理器抛错后其余处理器仍可关闭。

精确范围：BoltControlledConnectionManager.java、BoltControlledClient.java、已有 connection 测试、im-bolt README、本设计、Harness 规则矩阵及现有 T3 状态。新增约定优先由真实资源测试证明，不增类名例外或泛化静态 checker。实施前取得独立设计 Review，再按 focused→Bolt module→架构/完整门禁推进；当前 JRaft 失败与 Docker 环境阻塞分别保留，不冒称完整验收。

## 摘要

以 Client 私有 ReentrantLock 串行执行完整 init/shutdown，删除 initializationOwner、initializationSettled 及旧 Future 等待方法。Tracker.calls monitor 仅用于短准入/状态临界区，不包围原生启动、关闭或创建线程等待。

最终方案取代前一无限 monitor 等待方案：类内固定 `private static final Duration LIFECYCLE_LOCK_TIMEOUT = Duration.ofSeconds(5)`，不增加配置；init/shutdown 均以计时 tryLock 有界、可中断地等锁。其他线程获锁后才处理关闭，不中途取消初始化；同线程在 INITIALIZING 重入 shutdown 明确抛 IllegalStateException。5 秒仅限制等锁，不限制锁内工作或方法总耗时；Manager 内部既有创建线程关闭等待预算不变。

这是契约变化，不是纯重构。本文取代[原四态设计](2026-10-26-bolt-client-lifecycle-state-design.md)中 owner/future、有界等待、初始化中取消、并发后到 shutdown 提前返回等冲突决定；保留四态、构造 null 放行、唯一 Tracker.closed、原生清理条件及异常主因/suppressed 原则。

## 背景

当前源码已包含四态、rollbackInitialization、settleInitialization、awaitInitializationRollback、ControlledChannelHandler 和 TrackedRpcClientRemoting。用户当前排版、单数 ControlledChannelHandler 名称、静态 remoting 实现和 Handler 修改均为本增量输入，不回退或改名。

事实源为当前 Client、Tracker、ControlledConnectionManager/Factory、生命周期测试及本机 SOFA Bolt 1.6.6 sources jar。本文基于只读分析，不将未执行的回归或死锁压力实验写成通过。

## 目标与非目标

- 目标：用一个独立 ReentrantLock 消除 initializer owner/future 交接，明确串行启动、回滚和关闭。
- 目标：短 calls 临界区继续把准入关闭与 Tracker.admit 原子化，不能持 calls 等待资源。
- 非目标：不修改预算、策略、认证、wire、Manager/Factory、Handler、Invoker、BoltRpcClient，不提供通用锁框架、第二份 closed 或测试 seam。
- 非目标：不承诺任意进程内扩展不会制造锁循环，不修复原生部分启动清理缺口，不以此解决既有 DECODE 失败。

## 已知事实与约束

- AbstractBoltClient 构造会动态调用 option；此时子类 state、calls 和新增生命周期锁字段均尚未初始化。option 保持 state==null 构造放行，不能获取任一子类锁。
- RpcClient.startup 在调用线程同步启动已注册 UserProcessor，随后装配 remoting、启动 taskScanner；没有 join 外部 shutdown 线程的原生步骤。
- RpcTaskScanner.startup 仅调度扫描；shutdown 只请求 scheduledService.shutdown，不 await scanner 线程。
- ControlledConnectionManager.shutdown 等待 creation executor 结束，预算仍为 connectionCreationTimeout+1000ms；其 finally 调用原生 manager shutdown。
- 原生 Connection.close 发起 Channel.close 并添加 listener，不同步等待 closeFuture。原生 ConnectionPool 清理调用该非阻塞 close。
- 本次扫描当前生产调用，未发现 Manager/Factory/Tracker/ControlledChannelHandler 调用 Client.init/shutdown 或获取新增生命周期锁。当前自动配置注册 UserProcessor 的位置属于服务端。

## 方案概览

新增 `private final ReentrantLock lifecycleLock = new ReentrantLock()` 和私有静态 Duration 常量，只由 Client.init/shutdown 使用。state 继续 volatile，构造结束 READY；Tracker.closed 仍唯一管理准入。不使用 owner、Future 或 Condition。

固定锁顺序是 lifecycleLock → calls。资源操作在持 lifecycleLock、未持 calls 时执行。acquire、isRunningControlledAssembly、Tracker、Factory/Manager 及 pipeline 回调不得为本次改造新增 lifecycleLock 获取；不得在 calls 块中进入 init/shutdown。

## 详细设计

### 有界等锁与异常

init/shutdown 入口均调用 `lifecycleLock.tryLock(LIFECYCLE_LOCK_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)`，不退化为无限 lock。返回 false 时抛明确 IllegalStateException，说明生命周期锁等待超时；未拿锁的调用不能修改 state/closed、取得清理责任或执行 unlock。InterruptedException 时先恢复 `Thread.currentThread().interrupt()`，再抛 IllegalStateException 并保留 cause。

成功拿锁后才进入 try/finally，所有正常、拒绝、回滚及关闭异常出口均在 finally unlock。计时 tryLock 遇已有中断也可能优先抛 InterruptedException，包括可重入线程；不承诺该情况下优先获得 INITIALIZING 重入拒绝。正常同线程重入成功拿锁后检查状态，拒绝时只释放本次重入计数，不释放外层 init 的锁责任。不增加通用锁执行 wrapper。

### init

成功拿锁后，整个生命周期工作位于 lifecycleLock 的 try/finally 内：短 calls 块检查 READY 且 !closed、置 INITIALIZING；退出 calls 后按当前顺序分配/startup/init；短 calls 块提交 RUNNING。保留提交前 closed 检查，以兼容可信技术代码直接 closeAdmission 的现有边界，但普通并发 shutdown 不再能中途写 closed。

失败时短 calls 块 closeAdmission，按原进度调用 rollbackInitialization，cleanup 异常附加到主异常。finally 用短 calls 块将仍为 INITIALIZING 的状态改 TERMINATED，不覆盖成功 RUNNING。完成回滚和状态终结后才释放 lifecycleLock。删除 initializationOwner/initializationSettled；settleInitialization 若仅剩单段状态迁移则就地展开，不保留无职责通知 wrapper。

### shutdown

先获取 lifecycleLock，再短 calls 检查状态。由于 ReentrantLock 可重入，此时若 state==INITIALIZING，调用者必为当前初始化调用栈的重入：立即抛 IllegalStateException，不先 closeAdmission，也不等待自身。若异常逃逸原生启动回调，init 按原失败回滚；若回调捕获该拒绝，初始化可继续成功，不能把“重入拒绝”等同“必然初始化失败”。

普通其他线程在 tryLock 内最多等锁 5 秒，不改变 closed。获取锁后，init 已提交 RUNNING 或完成回滚并终结：未关闭时短 calls 块 closeAdmission，并按取得的原生关闭责任在 calls 外关闭。finally 短 calls 块置 TERMINATED。READY 直接永久关闭；已有 closed 且非初始化状态保持现有幂等返回边界，不趁此修改可信直接 closeAdmission 的语义。

运行态并发后到 shutdown 等首个调用释放 lifecycleLock 后再检查终态，不再在首个关闭尝试尚未结束时提前返回。终态仍不保证物理 Channel 已关闭或全部资源成功清理；不新增关闭异常广播和失败重试。

### 转换和等待结果

| 起点/调用 | 动作 | 结果 |
|---|---|---|
| READY + init | 生命周期锁内启动，短 calls 状态提交 | INITIALIZING → RUNNING 或回滚后 TERMINATED |
| INITIALIZING + 同线程 shutdown | tryLock 重入后检查 | 抛 IllegalStateException，不关闭准入 |
| INITIALIZING + 其他线程 shutdown | 在独立 ReentrantLock 上最多等锁 5 秒 | 获锁后关闭或按失败终态返回；超时/中断则拒绝，不取消 init |
| RUNNING + 首个 shutdown | 短 calls 关闭准入，calls 外关闭原生资源 | finally TERMINATED |
| RUNNING + 并发后到 shutdown | 最多等锁 5 秒，成功后检查终态 | 获锁后返回或等锁超时/中断拒绝，不广播首个失败 |
| READY + shutdown | 关闭准入 | TERMINATED，后续 init 拒绝 |

初始化失败和 suppressed cleanupFailure 由 init 调用者接收；其他 shutdown 不再通过 settled future 接收 cleanupFailure。这是删除旧错误通知契约的明确影响。旧 rollback Future 等待异常删除，改为统一等锁超时/中断异常，不将等锁失败描述为回滚失败。

### 死锁分析及边界

当前受控生产路径未发现锁循环：生命周期锁持有者可能等待创建线程；创建线程只读取 Client 固定模式/Manager 导航及停止状态，使用 Manager/Channel/Tracker，但不获取生命周期锁。Tracker 完成/排空、pipeline handler 和 close listener 不调用 Client 生命周期方法；非阻塞 Channel.close 不要求 lifecycle owner 等待执行 shutdown 的事件线程。scanner 停止也不 join 扫描任务。

可构造的外部扩展反例仍必须记录：RpcClient 同步执行自定义 UserProcessor.startup/shutdown；若该回调启动另一个线程调用 client.shutdown，再同步等待它结束，便形成“生命周期锁拥有者等待回调子线程，子线程等待生命周期锁”的循环。自定义创建/Channel 回调若同步等待另一个执行 init/shutdown 的线程，也有同类风险。最终 tryLock 使子线程最多等锁 5 秒后失败，可打破仅由此次等锁形成的永久循环；但扩展若吞掉失败后无限等待或重试，仍无全局进展保证。不能宣称任何继承公开 API/任意特权回调都死锁安全。当前仓库受控 Client 默认装配未发现此跨线程回调依赖；本设计不新增这些扩展，也不通过禁止/扫描所有第三方回调扩大范围。

同线程 RUNNING shutdown 回调再次 shutdown 时，因 closed 已置 true 直接返回，避免重复原生关闭。禁止向 isRunningControlledAssembly 等读取方法加生命周期锁，否则会新增原本不存在的回调等待边。

## 可靠性与安全

Tracker.closed 与 admit 仍通过同一 calls monitor 线性化；原生实际完成、排空和锁外 Channel.close 不变。生命周期锁最多等待 5 秒，但已获锁的工作仍可能长期不返回；等锁失败不抢占持锁者或关闭准入。5 秒不是方法总耗时、停机完成或调度硬期限保证，不取消 Manager 内部既有有界等待。

当前注入原生部分启动失败会保留 taskScanner 未启动时 cleanup LifeCycleException suppressed；不改变清理条件或声称完整回收。没有新认证降级、线程、timer 或第二套关闭准入事实。

## 运维与交付

无 wire、配置或公开签名迁移，但 init/shutdown 的并发等待、同线程重入和异常观察行为变化。模块 README 必须披露新契约，旧“初始化期有界等待取消”和“运行期后到 shutdown 提前返回”说明须替换。

仅在本设计独立 Review 完成后才进入实施。准确生产范围为 `im-plugin/im-bolt/src/main/java/com/co/kc/imchat/plugin/bolt/client/BoltControlledClient.java`；测试范围为既有 `connection/BoltConnectionLifecycleTest.java`，必要时仅在 `connection/BoltProductionBackgroundTest.java` 补实际缺口。README、原四态设计的取代链接、设计索引及现有 active T3/PROGRESS 在获准实施时同步。最终设计独立 Review 已完成，当前实施仅涉及 Client、既有 LifecycleTest、README 及设计状态同步；ProductionBackgroundTest 复用未修改。实际 RED/GREEN 及残余覆盖缺口见 active T3，不据设计矩阵宣称全部已验证。

## 风险与回滚

- 风险：锁顺序倒置、误把 calls 变成长锁、回调跨线程等待循环、误将 INITIALIZING 重入转换为取消、删除 future 后仍保留失效测试承诺。
- 回滚：恢复本增量生命周期锁和等待契约的精确差异，不覆盖用户当前 ControlledChannelHandler、排版、Handler、枚举位置/注释或其他混合修改。
- 延后：任意第三方扩展的全局死锁证明、原生部分启动资源缺口、既有 DECODE 失败、正式 quality/完整验收。

## 验证策略

| 场景 | 必须修改或新增的观察 |
|---|---|
| 初始化中其他线程 shutdown | 替换 closeDuringNativeInitializationWaitsForOwnerRollback：受控 latch 持 init，shutdown 线程在ReentrantLock 有界等锁路径（通常 TIMED_WAITING，线程状态不是唯一证明）；释放后 init 成功、shutdown 再完成原生关闭，不再预期 closed during initialization |
| 同线程初始化回调 shutdown | 替换 initializerRetainsRollbackOwnershipAfterSelfShutdownOrNativeFailure 的 selfShutdown 分支：精确断言 IllegalStateException；分别区分回调捕获后 init 可提交与逃逸导致回滚 |
| 两个并发 init | 在已持生命周期锁期间第二个 init 排队，不再由主测试线程直接调用造成自阻塞；释放首个后第二个按非READY拒绝 |
| calls 不能被长时间占用 | 初始化暂停期间，独立线程能执行 Tracker.closeAdmission/受控状态查询等短 calls 操作；不使用该观察混淆正常 shutdown 的排队契约 |
| 运行态两个 shutdown | 第二个等待首个关闭尝试结束；复用真实资源和受控观察点，不用Channel非阻塞closeFuture完成冒充方法仍持生命周期锁 |
| 等锁超时 | 持真实初始化观察点超过预算，另一 init/shutdown 明确等锁失败；不修改 state/closed，释放后原初始化仍可完成 |
| 等锁中断 | 中断竞争线程后拒绝，恢复标志并保留 cause，不影响持锁者；成功拿锁后的异常不混入等锁处理 |
| 原生初始化失败 | 保留主异常和已观察 suppressed cleanupFailure；后到 shutdown 不广播该失败 |
| 构造/冻结/准入 | 保留 null 构造 option、未init关闭、成功提交、held admission拒绝、完成释放与后台禁用回归 |

采用 latch、有界测试 Future 和明确原生公开观察点控制测试，生产 tryLock 固定 5 秒等锁；测试 Future 上界应更大并留调度余量，不作为生产总耗时保证。不增加生产 seam 或反射；若现有回调无法可靠控制运行态关闭时长，先报告覆盖缺口，不堆叠复杂 fixture。

设计阶段未构建；获最终独立 Review 后，实施按以下最窄生命周期/后台命令保存 argv/UTC/exit/fresh XML，实际结果由 active T3 记录：

```bash
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=com.co.kc.imchat.plugin.bolt.connection.BoltConnectionLifecycleTest,com.co.kc.imchat.plugin.bolt.connection.BoltProductionBackgroundTest -Dsurefire.failIfNoSpecifiedTests=false test
git diff --check
```

先旧实现运行新契约回归取得明确 RED，再最小实施 GREEN。旧四态 baseline/GREEN 只对应旧契约，不能证明本增量。既有 DECODE 阻塞未解除，不扩大 module/architecture，不运行 full/clean/commit。

## 后续文档与执行

提交本文供独立设计 Review，先检查外部同步生命周期回调风险是否为接受边界，以及生产锁与短 calls 的锁顺序。不得复用旧 Approved 代替本增量审查。获审查后并入现有 T3，保持唯一 active，不新增并行计划或通用规则豁免。

## 附录 A：备选方案与行业依据

- 原 owner/future 有界交接：可在初始化中及时关闭准入并等待结果，但需要更多责任/通知状态，保留原 cancellation 契约。
- 独立 monitor 串行完整生命周期（前一方案，已取代）：状态少，但不可中断且无等锁期限。
- 独立 ReentrantLock 加固定 5 秒 tryLock（最终方案）：等锁有界且可中断；不取消持锁初始化，不限制锁内工作。
- 用 calls monitor 包完整生命周期：阻塞 admit/finish/drain，可能在资源等待中阻塞完成回调，不能采用。

原生事实绑定 SOFA Bolt 1.6.6 AbstractBoltClient、RpcClient、RpcTaskScanner、DefaultConnectionManager、Connection/ConnectionPool 源码，不推断未验证的任意扩展行为。

## 附录 B：决策记录

| ID | 问题 | 增量契约 | 影响 |
|---|---|---|---|
| D1 | 完整生命周期串行 | 独立私有 ReentrantLock 覆盖 init/shutdown | 不使用 calls 长锁 |
| D2 | 初始化中 shutdown | 其他线程有界等锁，同线程重入明确拒绝 | 不取消初始化；等锁最多 5 秒，失败不更改 state/closed |
| D3 | 交接状态 | 删除 owner/settled/await，仅四态和唯一 Tracker.closed | 不再向 shutdown 广播 rollback cleanupFailure |
| D4 | 锁顺序 | 生命周期锁 → calls，资源等待不持 calls | 读取/Tracker/创建路径不新增生命周期锁 |
| D6 | 固定等锁预算 | 私有静态 Duration.ofSeconds(5)，不增加配置 | 仅等锁，超时明确异常，中断恢复标志，成功后 finally unlock |
| D5 | 保留用户源码 | 保留当前 ControlledChannelHandler 单数名、排版、Handler及枚举底部布局 | 不夹带可读性或API优化 |

## 附录 C：独立 Review

最终方案独立设计 Reviewer `a3b3f5a6c96a74ee1` Approved，必须项 none。该结论覆盖独立 ReentrantLock、固定 5 秒有界等锁、重入拒绝和短 calls 边界，不表示实现 Review 或运行验收通过。

## 追加：锁边界与重复等锁维护

本次最小维护仅删除三处冗余外层 calls monitor：init catch 仅调用自身 synchronized 的 closeAdmission、init finally 纯状态终结、shutdown 资源 finally 纯状态终结。独立审计已认可这三处；生命周期状态写已有 lifecycleLock 串行保护，终结阶段准入已关闭。保留 init 入口检查/进入、提交检查/RUNNING、shutdown 关闭准入/责任快照和 isRunningControlledAssembly 复合查询四处 calls 边界，Tracker 内部 monitor 不改。

将两个入口相同的 tryLock/timeout/interrupt 原体提取为 private void acquireLifecycleLock；成功返回后立即进入原外层 try/finally unlock，未拿锁路径不能进入 unlock。异常文案、cause、5秒预算、中断恢复及拒绝状态边界不变。不提取 initializeResources，不增加 withLock lambda、结果 wrapper 或更多职责拆分。保留用户排版、ControlledChannelHandler、底部枚举和 Handler。

本维护是局部可读性及冗余锁清理，不改变查询为 lock-free，不扩大 catch 或吞 cleanup 原因。复用52项 Lifecycle/ProductionBackground 窄验证及精确原体比较；更宽验证及既有 DECODE 缺口保持原状态，局部实现 Review 待完成。

## 追加：受控 startup 入口迁移

本次仅将受控 Client override 从弃用 init 迁至 startup，内部 super.init 改为 super.startup，默认 Spring Client Bean initMethod 同步 startup。SOFA Bolt 1.6.6 RpcClient.init 标注 Deprecated，方法体动态调用 startup；因此保留继承 init 时仍进入本受控 override，不新增兼容 wrapper。startup 原生声明的 LifeCycleException 是 RuntimeException，不引入 checked exception；super.startup 直接调用原生实现及父类生命周期，不回调本 override，不构成递归。若仅改override而仍super.init反而会动态递归，必须同步修改。

仅迁移已确认运行对象为BoltControlledClient的直接调用及method reference，包括Plugin生命周期/资源/TLS/DEV/装配和两条Unified真实受控fixture、Broker Gossip集成fixture与HA客户端fixture。原生RpcClient、rawClient、测试原型自定义RpcClient、服务端init和TLS/key manager init不改。异常、锁/状态/回滚及用户Handler/ControlledChannelHandler排版不变。继承init只保留一个明确兼容性行为回归；业务与默认装配使用startup。

原生命周期增量Review结论仅覆盖原范围，本迁移不冒称已取得独立实现Review；实际测试及编译边界记录于T3。旧DECODE缺口不顺手重跑或修复，更宽验证暂停。

## 最终追加：恢复 monitor 串行协议并迁移 startup

本节取代前述 ReentrantLock、5秒tryLock、acquireLifecycleLock及其timeout/interrupt退出契约。最终采用 private final Object lifecycleMonitor，完整 startup/shutdown synchronized，删除显式锁和Duration常量。monitor竞争没有超时/中断退出，预中断不取消获取；持锁工作不返回会使shutdown无期限排队。外部重启是运维约定，不是已验证健康或恢复保证。自定义生命周期回调若跨线程等待另一个startup/shutdown仍可能死锁，此风险不隐去。此前monitor独立Review结论仅按其原范围保留，本次实现仍需局部独立Review。

锁顺序 lifecycleMonitor→短calls；保留四处复合calls、Tracker唯一closed、清理进度/suppressed及底部枚举。same-thread INITIALIZING重入shutdown拒绝；资源finally不得覆盖外层拒绝。仅将受控入口及super委托/Spring initMethod改startup，继承init动态转入startup，不新增wrapper。保留用户ControlledChannelHandler、排版及Handler。

测试取消旧timeout/interrupt拒绝预期，改真实BLOCKED串行排队、预中断仍等待并在获锁后保留中断事实；重入caught/escaping、第二startup排队、短calls进展保留。新契约先RED，再GREEN。旧DECODE不重跑，full/clean/module/architecture/commit暂停。

## 追加：关闭责任命名提取

本次用户批准以 beginShutdown 替代 shutdown 内原复合 calls 块，取代上一增量“不提取shutdown责任选择”的范围限制。调用方持 lifecycleMonitor；INITIALIZING 保留原异常，closed 返回 NONE，closeAdmission 后 RUNNING 返回 CLOSE_CLIENT，READY 先迁 TERMINATED，其他返回 CLOSE_MANAGER。private ShutdownAction 三值逐值中文说明，位于类底部 LifecycleState 前；不使用 boolean/null 或通用迁移框架。NONE 返回仍在资源 try/finally 之前，资源关闭分支及 finally 终结责任不变。保留 Handler、record、排版及四个 calls 复合边界；仅复用58项聚焦验证，不扩旧 DECODE 或完整验收。

## 追加：初始化阶段命名提取

仅将 startup 原位三个边界提为 private beginInitialization、commitInitialization、finishFailedInitialization。begin完整保留calls内closed/READY检查和INITIALIZING迁移，调用仍在资源try之前；commit完整保留calls内closed复检和RUNNING迁移，调用仍在成功路径；finish完整保留资源finally内INITIALIZING条件终结。三个方法注释声明调用方持有lifecycleMonitor；不增加锁、条件、异常或通用changeState。不提取shutdown责任选择或initializeResources，保留用户排版、record、底部enum与Handler。本轮作为独立增量验证，不改写monitor-startup旧证据或正在进行的Review范围。
