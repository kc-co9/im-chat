# 本机非线程所有权锁设计

> 状态：用户批准在 im-lock 单独实现，独立设计 reviewer `8bcc59eb` Accept；本轮不接入 Broker。

## 摘要

在 `im-plugin/im-lock/core` 新增 `MutexLock implements java.util.concurrent.locks.Lock`。一个实例是一把本机锁，不绑定持有线程，支持异步完成线程释放。Template 是未来便捷层，本轮不实现。

## 背景

Broker 观察流程需要跨异步任务生命周期互斥，但线程重入锁不能由完成线程释放。现有 im-lock 提供 Redisson 分布式锁及同步模板，不能改变其线程所有权契约来满足该需求。

## 目标与非目标

- 目标：独立可构造的通用 MutexLock；非重入、非公平、跨线程释放；完整支持 Lock 获取方法。
- 非目标：Broker 接入、按 key Registry、Template、注解/AOP、自动装配、分布式锁、TTL、Condition、持有凭证。

## 已知事实与约束

所有权在 im-lock，仅依赖 JDK。一个实例一个互斥域，多个实例互不影响。无参数构造，不引入配置或基础设施依赖。使用者必须共享实例并确保每次成功获取只释放一次。

## 方案概览

使用 JDK AbstractQueuedSynchronizer 的独占模式：state=0 空闲、state=1 占用。tryAcquire 用 CAS(0,1)；tryRelease 用 CAS(1,0)，空闲时释放抛 IllegalMonitorStateException。不设置 exclusiveOwnerThread，所有权不绑定线程。AQS 管理阻塞、唤醒、可中断等待及纳秒超时，无重复占用状态。

## 详细设计

公开入口为 `core/MutexLock.java`，内部私有 Sync 扩展 AQS。tryLock 立即 CAS；lock 委托 acquire；lockInterruptibly 委托 acquireInterruptibly；限时 tryLock 委托 tryAcquireNanos，并用 Objects.requireNonNull 校验 TimeUnit；unlock 委托 release；newCondition 明确抛 UnsupportedOperationException。

同一线程第二次 tryLock 返回 false，lock 将等待释放，不视为递归获取。lock 不可中断获取，但等待中的中断状态遵守 AQS 语义；其余可中断获取方法优先响应中断。零或负超时仍尝试立即获取，已占用则返回 false。类和私有 Sync 不向外暴露 AQS。Sync 声明 serialVersionUID，MutexLock 不承诺序列化。

## 可靠性与安全

不自动超时释放占用。同步异常及异步完成后的释放由使用者负责。任意持有锁引用的线程均有释放能力；无 owner token，无法识别占用期间的非所属释放，也无法区分旧回调误释放新占用。空闲 unlock 检测不是持有者认证。调用者必须保证一次获取一次释放，且底层任务实际结束后再释放；此限制写入 Javadoc/README。

## 运维与交付

无 Spring Bean、无 Redis 访问、无 POM 依赖变化。新增能力不改变已有 DistributedLockTemplate、LockClient 或分布式锁注解。独立锁空闲时不保留业务 key；等待队列由 AQS 管理。没有后台线程、网络、运行资源或启动行为变化。

## 风险与回滚

- 风险：调用者误认为它是线程所有权锁、重入锁或自动释放锁；明确 Javadoc 与测试。
- 回滚：删除新增类、直接测试及 README 说明；无消费者迁移。
- 延后事项：Broker 集成、冷却状态拆分、Template。

## 验证策略

先创建 MutexLockTest，编译 RED 证明尚无类型，再最小实现并 GREEN。覆盖独立实例、即时获取、同线程非重入、跨线程释放、空闲/重复释放、阻塞唤醒、可中断等待、超时/零/负值、TimeUnit null、Condition 不支持、并发互斥。异步测试使用 CountDownLatch、Future 限时等待和受控线程，无 sleep。

聚焦：`mvn -q -pl im-plugin/im-lock -am -Dtest=MutexLockTest -Dsurefire.failIfNoSpecifiedTests=false test`；模块：`mvn -q -pl im-plugin/im-lock -am test`；架构：`./scripts/verify.sh architecture`；完成前 quick/clean/full，逐项记录实际结果，前序失败须先归因。独立实现 Review 后交用户复审，不提交 Git。

## 后续文档与执行

更新 im-lock README，根架构不变，不新增局部 Architecture。执行纳入已有 A5 的独立插件实现增量，不增加 active 任务。无新通用工程约定，现有接口契约和测试规则足够。

## 附录 A：备选方案与行业依据

- Semaphore(1)：获取/等待可复用，但直接 release 会扩大许可；增加原子标记导致双份状态，或使用内部监视器再次序列化，不如 AQS 单一状态直接。
- synchronized + wait/notifyAll：可实现，但需自行处理虚假唤醒、中断恢复和超时预算。
- AQS：JDK 已有独占队列机制，最小 CAS 状态即可实现，采用该方案。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 用户要求先在 im-lock 实现以便 Review | 独立 MutexLock，不先集成 Broker | 控制范围，避免抽象和迁移混合 |
| D2 | 是否必须 Registry/独立句柄 | 每次 new 一把锁，由调用方共享实例 | 不增加存储框架；接受无 token 的释放限制 |
| D3 | Lock 名称与线程语义 | 实现 Lock，注释明确非线程所有权 | 不继承 ReentrantLock，不混淆已有 Redisson 锁 |

## 后续决定：Broker 最小接入

用户复审 MutexLock 后确认无问题，并批准先接入，不增加 Template。Broker AppService 持有两个独立的 MutexLock，分别替换 roundRunning 和 recoveryRunning。recoverRoutes 与 advanceFailureEpoch 必须共享 recoveryLock；按目标 probing 集合与冷却状态不动。同步异常和异步完成的释放位置保持原流程，不新增 helper 或构造参数。Broker Server 添加 im-lock 依赖；现有分布式锁自动装配不修改，核对依赖和启动影响。范围为 Server POM、AppService、聚焦测试和 Server README；不扩大 Plugin 能力，不移动领域状态。

MutexLock 对空闲释放严格报错，所以必须保证成功获取后恰好释放一次；原 observeFailures 的非 Leader 返回、异常及两个异步分支逐条核对。增加异步失败后重新进入、跨线程完成后重新进入的行为断言，保留实际底层完成语义。

## 后续决定：按目标探测锁

用户批准进一步替换 beginProbe/endProbe。AppService 使用 `ConcurrentMap<BrokerId, MutexLock>` 保存每个历史探测目标的锁，以 computeIfAbsent 保证同 ID 共享实例；顺序 Stream 的 map 内取得局部 probeLock，tryLock 失败直接返回已完成阶段，不发起探测；完成处理 finally 释放捕获的原实例。移除内嵌 State 的 probing 集合及 begin/endProbe，冷却不变，不引入 Template/Registry/新 helper。

用户追加批准完成链拆分：handle 保留原有单调耗时与成功/失败分类，返回私有 `ProbeSample(status, completedAt)`；thenAccept 重读 Context 并记录；链尾 whenComplete 无论前面哪步异常都释放捕获的锁。同步请求构造/发起的 RuntimeException 仍转 failedFuture，不迁移到 exceptionally，因其发生在异步阶段返回之前。无新 helper，不改变采样时间或状态接受条件。

后续用户批准试用同步完成阶段起链：以 `CompletableFuture.<Void>completedFuture(null).thenCompose(...)` 包含原请求构造与发起，删除 probeResult 局部变量和显式 catch。普通 thenCompose 不增加调度线程；构造/发起的 RuntimeException 仍进入 handle。CompletionStage 的回调边界也会将 Error 及意外 null 返回转为异常阶段，这是相对原 catch(RuntimeException) 的边界差异，不声称所有 Throwable 逐字等价；正常 Adapter 契约返回非空阶段。后续 handle/thenAccept/whenComplete 不改，单调起点仍在起链前读取。

锁表不在调用完成或采样 Context 切换时删除，避免持旧引用与新建锁并存破坏互斥。明确代价：历史探测 BrokerId 条目保留至 AppService 生命周期结束，大小随历史目标数增长，本轮不声称按当前拓扑有界。若后续支持高频大规模节点身份更替，须独立设计引用安全回收；不得用 TTL 或完成后 remove 掩盖问题。

## 附录 C：独立 Review

独立设计 reviewer `8bcc59eb` Accept，findings none；建议补充释放到后续成功获取的内存可见性、跨线程交接仍需 happens-before，并测试已中断入口、不可中断等待的中断标记保留和取消后队列进展，纳入实现。

## 后续决定：observeFailures 完成链收敛

用户批准，独立设计 reviewer `3085c5cd` Accept。生产仅改 `im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/application/cluster/BrokerClusterAppService.java#observeFailures`。成功获取 observationLock 后，以 `CompletableFuture.<Void>completedFuture(null).thenCompose(roundStart -> ...)` 承载原流程，唯一外层 whenComplete 释放锁；移除非 Leader、pending recovery、普通分支的重复释放和外层 catch，busy 返回保持。

报告收集改为顺序 voters.stream/filter/map/reduce(thenCombine)，不串行等待。目标查找、单调耗时与断言、请求构造仍在单请求恢复回调之外；只有 Adapter 发起包在 completedFuture/thenCompose 中，exceptionally 转空报告。所有 membership 读取、守卫、时间采样及 Begin 顺序保持，不新增 helper 或 Template。lambda 参数分别使用 roundStart/requestStart，避免与内部 ignored 冲突。

明确边界差异：原外层 catch(RuntimeException) 不接住 Error；现在外层 Error 进入异常阶段并释放锁。原 Adapter 同步 Error 向外传播；现在进入异常阶段并按单请求失败转空报告。不声称 Error 行为等价。正常 Adapter 返回非空阶段。

验证沿用已有聚焦基线 `/tmp/observe-inline-focused.log` 及 reactor retry exit 0；修改后顺序运行 `mvn -q -pl im-broker/im-broker-server -am '-Dtest=BrokerFailureObservation*Test,BrokerAutomaticFailure*Test' -Dsurefire.failIfNoSpecifiedTests=false test`，独立限定 diff Review，再模块 reactor。父会话 Review 前不继续完整门禁，其他 WIP/Follower Block 保留，不提交。
