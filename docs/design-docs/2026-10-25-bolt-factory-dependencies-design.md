# Bolt 连接工厂依赖评估

> 状态：复核 Approved。原 Reviewer `a12ce5a7edf571b89` 复核 Approved、findings none，两个必须项均关闭；仅批准评估后保留现状、零生产修改。独立设计 Reviewer `a12ce5a7edf571b89` 对初版一次性 Manager 绑定方案给出 Revise；本版接受成本判断，选择保留现状，不实施 Factory 生产改动。Tracker 局部实现 Review `ac1bb6179c9cf8a01` Accept、findings none，不覆盖本评估或完整混合工作区验收。

## 后续职责拆包

保留 Factory 回查 Client 唯一 Manager 与固定模式、不实施 bindManager 的结论不变。后续已批准的[按实际职责拆包设计](2026-10-25-bolt-responsibility-packages-design.md)仅调整 Factory/Manager 到 connection、命名为 Controlled 以及必要跨包 public；本文原零生产修改评估及其 Review 事实仍按原范围保留。

## 摘要

保留 Factory 通过 `client.manager()` 获取唯一 Manager，并通过 `client.developmentMode()` 读取固定模式的现有协作。不引入 `bindManager`、nullable / volatile Manager 字段、额外构造模式参数、接口、Supplier 或新协作者。本次优化评估以不改生产代码结束，修订结论已获原 Reviewer 复核 Approved，仅覆盖保留现状的评估结论。

初版希望减少 Client 导航，但一次性绑定会新增部分初始化状态、装配顺序和失败路径，而没有消除 Factory / Manager 原生协作关系。固定模式目前已经来自 Client 的策略快照，没有模式漂移 bug；仅为省一次导航增加参数没有足够收益。

## 背景

当前 `BoltAuthenticatedConnectionFactory` 存有 Client；`createConnection(Url)` 调用 `client.manager().binding(url)`，在 DEV / MTLS 候选交付前读取同一 Manager 的 `stopped()`，并通过 `client.developmentMode()` 读取 Client 固定 policy snapshot 的模式。原生 SOFA Bolt 1.6.6 `RpcConnectionFactory` 父类构造同样需要 Client。

Manager 拥有唯一 stopped、池键与 Url 校验、共享 deadline 和有界创建执行器；Factory 拥有 ThreadLocal scope / candidate、原生建连和认证、会话事实发布及失败候选清理。现有 `Client.manager()` 有真实生产调用，不是应删除的兼容 wrapper。

## 目标与非目标

- 目标：判断减少 Factory 回查 Client 是否值得实际修改，保留成本和拒绝理由作为仓库事实。
- 非目标：不为了无环外观增加绑定状态机或复制停止/预算/信任，不实施 Factory / Manager / Client 生命周期修复。
- 非目标：不改 Tracker、BoltRpcClient、Peer 身份 URI 前缀、TLS / DEV、类型屏障、失败分类或候选所有权，不执行 Maven / clean / full / commit。

## 已知事实与约束

- 所属为 `im-plugin/im-bolt/client` 包级协作，受根 Architecture、plugin AGENTS 和模块 README 约束。
- Client 当前 init 顺序为 Factory 构造 → Manager 构造 → `managerAllocated=true` → `manager.startup()` → 原生 Client init。Factory 和 Manager 的真实相互协作不能通过改名或 Supplier 消除。
- Manager 构造器会创建执行器对象。当前 rollback 只有 Manager 已 started 时才调用其 shutdown，因此不能证明新增「Manager 已构造但未 startup」失败点的执行器回收完整。执行器对象已构造不等于线程已启动；没有证据证明现有线程泄漏，本评估不作该断言。
- Tracker quiet baseline 日志不单独证明退出码或冻结输入，没有专用 mutation；局部 Accept 不代替完整 quality / full。用户要求集中 full，仍暂停。

## 方案概览

采用零生产修改方案：Client 继续固定策略并装配 Factory / Manager；Factory 继续经 Client 获取同一 Manager 并读取固定 mode。Manager 继续唯一拥有 stopped、Binding 校验与共享 deadline，Factory 不保存这些状态的副本。

不新增初始化交接、不增加可空 Manager 依赖，也不将 `client.manager()` 改写成 Supplier。依赖关系有明确的原生和资源所有权原因，保留比形式上减少导航更清楚。

## 详细设计

### 保留现有调用路径

Factory 的 superclass 所需 Client、Client 字段及 `client.manager()` / `client.developmentMode()` 调用全部保留。不删除仍有调用者的 `manager()`，不向 Factory 构造器追加 mode 参数。固定模式来自 Client 已校验的 policy snapshot，当前没有需通过构造参数修复的漂移问题。

### 拒绝一次性绑定协议

初版拟在 Manager 构造后、startup 前执行 `bindManager`，用 volatile nullable 引用和重复绑定 guard 固定协作者。该方法会引入未绑定、已绑定和绑定失败等装配状态；同时新增 Manager 未 started 的潜在失败阶段，现有 rollback 对该阶段回收不具备完整证明。不能用「复用现有 rollback」为新增协议背书。

本版撤回该实施建议，不把回收证明缺口转为生命周期修复任务，也不据此声称现有线程泄漏。未来如有独立业务需求要求改变初始化协议，应另行明确资源分配、启动和回滚所有权后设计及验证。

### 不改变创建与清理语义

Url / Binding 校验、scope / candidate 嵌套保存恢复、原生连接、共享预算、DEV / MTLS 校验、交付前停机/迟到复查、FACTS 发布、异常分类、中断恢复和未交付候选关闭均不修改。没有新增参数、线程、timer 或公共 API。

## 可靠性与安全

保留唯一 Manager stopped 与原池发布复查，保留共享 deadline 和固定信任策略。没有认证降级、预算复制、停止状态缓存或候选所有权迁移。本次只是优化评估，不新增可靠性保证，也不提供生命周期缺口修复或性能改善证据。

## 运维与交付

无生产、配置、wire 或数据变更，因此不需要部署或迁移。只更新本设计、设计索引、现有计划与 PROGRESS，记录真实 Revise 和保留结论；没有 Factory 实施完成的宣称；复核 Approved 仅批准零生产修改的评估结论。BoltRpcClient 契约留后续单独评估。

## 风险与回滚

- 保留风险：Factory 继续依赖 Client 导航到 Manager；这是明确接受的内部协作成本，不是已经证实的功能缺陷。
- 未证明事项：当前 rollback 在新增构造后未 startup 失败阶段的完整回收；既不扩大为已有线程泄漏，也不静默扩大实施范围。
- 回滚：本次只有文档评估，无生产回滚事项；保持 Tracker、前缀迁移与混合用户修改。

## 验证策略

- 本轮只按实际代码审阅依赖与初始化顺序，`git diff --check` 检查文档差异。
- 不运行 Maven，既有 Tracker 聚焦结果不作为 Factory 重构证据，因为没有 Factory 重构。
- 原 Reviewer 复核应检查 Revise 事实、保留路径、模式快照、一次性绑定成本与资源回收证明边界，Approved 依据原 Reviewer 实际复核，不因文档已改而自行认定。
- 完整 quality / full 及真实 E2E / HA 仍属于既有计划后续验收，不以本次零生产修改关闭 T3。

## 后续文档与执行

设计索引登记为「首轮 Revise、复核 Approved，仅保留现状」。现有 `2026-10-25-bolt-package-boundaries` 计划及 PROGRESS 保留 T3 唯一 active，记录不实施 Factory 依赖改动。当前运行事实未变化，无须改根 Architecture、模块 README 或 Harness 规则。

## 附录 A：备选方案与行业依据

- 保留当前导航（选择）：零新增协议，固定 snapshot 与唯一 Manager 不变；代价是显式接受 Factory 对 Client 的内部依赖。
- 固定 mode 参数 + 一次性 Manager 绑定（初版，撤回）：减少回查，但增加 nullable / volatile 引用、guard、装配顺序及部分初始化失败状态；收益不足，原 Reviewer Revise。
- 新协作者统一 Binding / 停止状态：需要迁移真实所有权，超出本次优化需求；不采用。
- Supplier / callback 导航：仅隐藏同一依赖，不提升所有权清晰度；不采用。复制 stopped / deadline / trust 会制造双重事实，同样不采用。
- 第三方范围限于当前仓库 SOFA Bolt 1.6.6 原生 superclass 和调用路径；没有新增外部能力主张。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决定 | 理由与影响 |
|---|---|---|---|
| D1 | 减少 Factory 通过 Client 回查 | 保留 client.manager() 现有协作，不做生产修改 | 一次性绑定成本高于省略导航的收益 |
| D2 | 模式直接参数是否必要 | 保留 client.developmentMode() | 当前已读取固定 snapshot，无漂移 bug，不为导航增加参数 |
| D3 | 初版 bindManager 的失败路径 | 不实施绑定协议，不扩生命周期修复 | 当前 rollback 不能证明新增未 startup 阶段完整回收，但不能断言现有线程泄漏 |
| D4 | 评估结论状态 | 接受 Revise 并修订为保留现状，原 Reviewer 复核 Approved | 仅批准零生产修改，不运行 Maven 或扩大到 BoltRpcClient |

## 追加：Factory / Manager 完整职责方法提取（2026-10-26）

状态：用户批准本增量方向，独立设计 Reviewer `a3b3f5a6c96a74ee1` Approved、findings none、必须项 none，批准范围为本追加节；现按该边界实施，独立实现 Review 待完成。原文零生产修改及历史 Review 仅适用于原依赖评估，本增量不改变保留 client.manager()、固定模式与不新增绑定协议的结论。

本次只改 connection 包的两个现有类：

- Factory 增加 `private BoltInvocationException.Failure classifyCreationFailure(Exception failure)`，完整搬迁 catch 中分类 if 链为返回分类的方法。TimeoutException / InterruptedException → LOCAL，SSLException / SecurityException → AUTHENTICATION，ConnectException / SocketTimeoutException / ConnectTimeoutException → TRANSPORT_UNAVAILABLE，其他 → LOCAL，顺序及原注释不变。中断恢复、`Bolt candidate authentication or creation failed` 文案、原 cause 包装仍在 createConnection catch；delivered 及 finally 的候选关闭、嵌套 scope/candidate 恢复不改。
- Manager 增加 `private ConnectionPool createAndPublishPool(BoltTarget target, Url owned, long deadline, AtomicReference<RunStateRecordedFutureTask<ConnectionPool>> own, CompletableFuture<Void> ended) throws Exception`。五个参数仅将既有闭包捕获显式化，不新增 wrapper、registry、状态对象或框架。方法承担完整的一次候选创建、工厂认证、锁内重验并发布池、失败候选清理和实际结束信号职责；保留 candidate/published 与整个原 try/finally，不再细拆。
- 创建任务只保留一次调用：`new CreationTask(() -> createAndPublishPool(target, owned, deadline, own, ended), ended)`。既有 own 引用容器不替换，构造 task → own.set(task) → connTasks.put → execute 顺序不变。own.get 仍在 callable 的 finally 中执行，不能提前变成实参 own.get()，否则 task 初始化时会取得 null；不引入 task 引用闭环或额外任务索引。
- Manager 删除 acquire 末尾仅 `throw failure` 的 ExecutionException catch；remove 中真实使用仍保留 import。InterruptedException catch、标志恢复及传播不变。

所有权和时序保持：工厂未 delivered 才关闭候选；管理器未 published 才关闭候选并按 own.get 仅删除本代 task，随后 ended.complete(null)。不将 ended 完成移至 caller 超时、Future 完成或资源关闭之前；原 candidate.close 若抛异常可跳过后续 remove/complete 的行为也不趁机修改，不宣称新增清理保证。锁边界、共享 deadline、认证、异常类型及包装层级、pending 释放时点均不变。

非目标：不改 Client（包括用户最新 startupAttempted / 回滚修改）、Handler、Tracker、配置、公共 API、Factory 依赖或原生入口；不新增测试专用生产接口。实现前已保存两类及 Client/Handler/设计/状态 before 和 SHA 至 `$CLAUDE_JOB_DIR/tmp/factory-manager-methods-before`。普通 df 可用约25 GiB，readiness exit0（保留 PATH world-writable 警告）；未删除任何材料或改变临时路径。

待独立设计 Approved 后再追加 T3 具体实施 checkbox。验证采用精确原体归一比对和 Factory/Manager 实际测试由窄到宽，不为等价提取强造 RED；保存完整 argv、UTC、exit、SHA、fresh XML 及最终精确 diff。旧 DECODE 不执行也不修复，module/architecture/full/clean/commit 暂停；遇 ENOSPC 停止，不清材料。完成后需要本增量独立实现 Review，旧 Review 和旧测试不覆盖新输入。

## 追加：连接创建作用域合并（2026-10-28）

用户已确认使用 `ConnectionCreationScope` 并授权调整。将 Factory 的两个 ThreadLocal 合并为一个 `ThreadLocal<ConnectionCreationScope>`；私有 static final 内部类保存 final binding 与可变 candidate。入口保存旧 scope、安装新 scope；底层从当前 scope 读取 binding 并登记 candidate；finally 保持原关闭选择和执行顺序，仅整体恢复或移除旧 scope。保留原候选关闭抛异常时可能跳过恢复的行为，不扩大为清理修复。

选择内部作用域对象而非两个独立 ThreadLocal，避免成对保存恢复；不引入通用栈、公开类型、getter 或新管理器。仅 Factory 生产文件及本设计、现有 T3、PROGRESS 记录变化；Client、Manager、Handler、认证、预算、异常分类、交付责任和公共 API 均不变。独立设计 Reviewer `a5158f39a13936936` Approved、必须项 none；实现后运行 Resources/Background 窄测试，随后 TLS/Lifecycle 边界测试并独立 Review。嵌套恢复采用新旧状态转换逐项对照；若无专用真实嵌套测试则明确保留覆盖限制。不运行旧 DECODE、module/architecture/full/clean/commit，T3 仍唯一 active，完整验收不关闭。

## 附录 C：独立 Review

第一轮：Reviewer `a12ce5a7edf571b89`，Revise。初版一次性绑定方案增加部分初始化状态与失败协议，成本不值得；当前 rollback 不能作为新增未 started 失败阶段已完整回收的证明。

本轮修订：撤回 bindManager / 新 Manager 字段 / mode 参数，保留现状；准确区分对象构造与线程启动，不声称已有线程泄漏，不扩修复范围。原 Reviewer `a12ce5a7edf571b89` 复核 Approved、findings none，两个必须项均关闭；批准范围仅为评估后保留现状、零生产修改，不批准 Factory 重构或其他优化。
