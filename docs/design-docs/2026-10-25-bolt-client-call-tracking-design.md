# Bolt 客户端调用追踪职责拆分设计

> 状态：已批准。用户已批准实际执行最小职责拆分及本文范围；独立设计 Reviewer `a35e419d309f04bf8` Approved，必须项 none，可进入实施。本文追加到现有包职责设计，不撤销已完成的 D6 列表 RPC 删除或 Peer 身份 URI 前缀迁移。

## 后续职责拆包

本文的 Tracker 状态与算法决定保留；`client` 归属、package-private 类和原字段访问方式由已批准的[按实际职责拆包设计](2026-10-25-bolt-responsibility-packages-design.md)取代。新设计采用 invocation Tracker、必要跨包 public 与 private 唯一 closed 受控方法；历史批准及验证事实不改写。

## 摘要

从 `client.BoltControlledClient` 提取一个 package-private 的 `BoltClientCallTracker`，集中拥有调用准入、在途计数、Channel 排空和原生 `InvokeFuture` 的完成包装与幂等释放。Client 保留固定装配、`init` / `shutdown`、连接获取和初始化资源关闭责任交接，不引入责任链，也不拆分每个处理步骤。

原 `accounting` 同时保护生命周期和调用计数，不能机械拆成两把锁。新协作者实例本身作为唯一共享 monitor；调用追踪状态及唯一的 `closed` 标志归协作者，Client 的初始化提交、关闭准入与协作者的 `admit` 使用同一 monitor。允许 Client 在该 monitor 内直接修改包级关闭标志，这是有意保留的最小内部协调面，不是第二套生命周期状态机。

## 背景

当前 `BoltControlledClient` 同时承担受控装配、初始化/关闭竞态、连接获取、`admit` / `drain` 及内部 `Completion` 的完整原生委托。`BoltOutboundHandler` 在发送前调用 Client 的准入，过期时触发排空；Manager 在退休连接时触发同一排空。原生同步及异步 future 创建点位于 Client 的 `RpcClientRemoting` 匿名子类中。

当前准入、排空及完成释放共用 `accounting`，`shutdown` 在同一锁内设置 `closed`，初始化提交也在该锁内检查 `closed` 并转移关闭责任。这里只移动稳定职责，不修订这些既有线性化点或终结定义。

## 目标与非目标

- 目标：一个真实被调用的包级协作者拥有全部调用追踪状态与完成包装，Client 主线仍能直接阅读初始化和关闭流程。
- 非目标：不删除 `BoltRpcClient`，不大改 Factory / Manager，不新增公共 API、测试专用 seam、无调用 wrapper、责任链或额外子包。
- 非目标：不修改 TLS / DEV、身份策略、typed 屏障、池与资源上界、caller 或共享创建预算、失败分类、原生协议、callback / timer / resolver 语义。

## 已知事实与约束

- 所属模块为通用基础设施 `im-plugin/im-bolt`；依赖和包约束见根 `ARCHITECTURE.md`、`im-plugin/AGENTS.md` 和模块 README。模块没有独立 Architecture，毋须为此新增。
- `Completion` 是原生 future 的薄委托。`putResponse` 先委托原生再完成记账；同步调用仅在正常返回、原生 `InvokeTimeoutException` 或 future 已完成时终结。调用方放弃等待不等于底层终结。
- 当前 `closed` 是 volatile，连接获取有锁外读取；移动后仍须保持可见性。`ownedStarted`、`initializing`、`initializationOwner`、`initializationSettled` 继续只由 Client 拥有，不复制到协作者。
- 环境 readiness 已实际 exit0。Maven 使用 `/tmp/jraft-artifact-m2`，串行运行；临时证据在 `$CLAUDE_JOB_DIR/tmp`。clean / full 由用户暂停，最终集中执行，不提交。

## 方案概览

调用流保持：原生创建 future → Tracker 包装 → typed 出站 Handler 完成原校验 → Tracker 准入 → 原生响应/同步终结 → Tracker 幂等释放 → 排空 Channel 的最后一个额度释放后锁外关闭。

状态归属如下：

- Tracker：monitor（实例自身）、volatile `closed`、`inflight`、`draining`、`active`、不可变准入上限、`Completion` 内的 `ended` / `channel` / `admitted` / `released`，以及同步上下文完成键。
- Client：策略快照、工厂/Manager 装配、`assemblyFrozen`、`ownedStarted`、`initializing`、`initializationOwner` 和 `initializationSettled`。
- 唯一共享边界：Client 生命周期代码以 `synchronized (calls)` 使用 Tracker monitor，并在锁内读写 Tracker 的包级 `closed` 标志。其他代码不修改它；Tracker 不回调 Client 查询生命周期，也不保留第二份关闭状态。

## 详细设计

### 最小协作接口

`BoltClientCallTracker` 为 final package-private 类型，Client 构造时按已冻结策略创建唯一实例。Tracker 的 `admit(Connection, int)`、`drain(Channel)` 和完成包装方法均为包级方法，有实际生产调用。

`BoltOutboundHandler` 直接调用同一 Tracker 的准入/排空，Manager 的既有退休分支直接调用同一 Tracker 的排空；通过 Client 的包级 final 协作者引用获取实例，不保留只为转发而存在的 Client `admit` / `drain` 方法。不改变 Handler 校验顺序、pipeline 名称或 Manager 的其他逻辑。

Client 中 `RpcClientRemoting` 的原生 override 保留，因为它们属于固定装配。两个 future 创建 hook 分别调用 Tracker 的同步包装（登记原生上下文）和普通包装；`invokeSync` 保留原生调用与异常判断，在 finally 将上下文和原 terminal 判定交给 Tracker 释放。`COMPLETION` 键与 `Completion` 类型细节由 Tracker 私有持有。方法仅对应这些真实边界，不再按子步骤拆分。

### 原子关闭准入与初始化交接

Client 所有原 `synchronized (accounting)` 原位改为 `synchronized (calls)`。`closed` 原位移动到 Tracker，写入仍在同一锁中；锁外读取仍为 volatile。初始化开始、成功提交、失败标记、finally 清理、运行装配检查及 shutdown 的控制分支保持现有顺序。

`shutdown` 的线性化点仍是在 monitor 内写 `closed=true`：若 `admit` 先取得 monitor 并提交额度，它属于既有在途；若 shutdown 先提交关闭标志，后续准入必须拒绝。不能只在 Client 锁外检查一次关闭状态再到另一把锁记账。

初始化成功提交仍在同一 monitor 内先检查 `closed`，再写 `ownedStarted=true` 并清除初始化拥有者。提交前 shutdown 只关闭准入并按原逻辑等待初始化方回滚；提交后原生关闭责任归 shutdown。初始化线程发起 shutdown 时只标记关闭，不等待自身。原生 init / shutdown、Manager 清理与有界 `initializationSettled.get` 均保持锁外执行，异常及 suppressed cause 原样传播。

### 完成竞态与 exactly-once 释放

`admit` 在 monitor 内检查关闭、排空和总额度，要求连接登记的 future 为受控包装，拒绝重复准入，再关联 Channel、增加两级计数并标记 admitted。退出 monitor 后，如 `ended` 已为 true，补做 finish。

`finish` 先记录原子 `ended=true`，再在 monitor 内仅对 `admitted && !released` 扣减一次额度。先完成后准入，由 admit 的锁外补偿释放；先准入后完成，由 finish 释放；多次响应/同步 finally/迟到完成只扣减一次。这里的 exactly-once 仅指本机已登记额度的释放，不表示远端副作用恰好一次。

shutdown 不清零计数、不重置 Completion 状态、不移除尚未终结的登记。关闭后迟到的原生响应或断连响应仍通过同一 Tracker 完成释放，不把 closed 当作跳过 finish 的理由。

### 排空与锁外关闭

`drain` 在 monitor 内加入 draining 并读取当前在途数；首次排空在锁外登记 closeFuture listener，实际 close 同样在锁外调用。draining 标记直到物理 closeFuture 回调才移除，避免请求在 close 发出与物理关闭之间重新准入。

最后一个 `finish` 在 monitor 内扣减并选出待关闭 Channel，退出后才调用 `Channel.close()`。不把 `finish` 或 `drain` 包在 Client 的外层 synchronized 中，否则即便方法内部已退出 synchronized，仍会在外层持锁时关闭 Channel。Tracker 自行管理这两个操作的临界区；生命周期代码不调用它们完成资源关闭。

### 原生 future 委托

`Completion` 整体迁移到 Tracker，保留所有 `InvokeFuture` 方法的原生委托，包括两个 waitResponse、协议编码、类加载器、InvokeContext、cause、callback、计时器取消与 connection-closed response。不得用 CompletableFuture、业务 callback 完成、caller 超时或额外 timer 替换原生终结协议；不得为简化代码扩大 finally 的释放条件。

## 可靠性与安全

准入上限、关闭拒绝、受控 future 类型检查、重复准入拒绝及排空拒绝原样保留。TLS 会话事实、发送屏障和预算语义不变，没有重试、认证降级、持久化或新审计字段。本次不承诺性能收益、硬返回期限或恶意进程内隔离。

新增及实际修改的生产字段、构造器和方法（含 private）补适当中文块注释/Javadoc，遵循 CODING_GUIDE；说明共享锁、关闭交接及原生终结原因，不以注释取代验证。

## 运维与交付

无配置键、公开 API、wire 或数据迁移；原 `BoltRpcClient` 保留。调用追踪对象与 Client 等寿命，不创建线程、timer 或新关闭资源。已有混合用户修改、D6 和前缀迁移均保留。最终完整验收仍需用户指定的集中窗口，本轮不执行 clean / full / commit。

## 风险与回滚

- 风险：误拆锁、重复 closed 状态、原生 future 方法遗漏、同步终结条件扩大、锁外关闭被外层锁意外包住，以及测试反射固化旧嵌套类名。
- 回滚：仅恢复本次 Tracker 提取及其真实调用点、测试和文档精确差异，不覆盖现有混合改动。
- 延后事项：Factory / Manager 内部重构、完整验收、生产证书及私有 JRaft 后轨不在本次窄实现内。

## 验证策略

优先通过公开调用和现有原生入口保留以下行为断言；不为测试暴露 Tracker 状态：

- `BoltConnectionLifecycleTest`：shutdown 先于 held admission 时拒绝发送、并发 shutdown 幂等；初始化与 shutdown 竞态保持唯一清理责任；最后一个实际完成后才能关闭排空连接；物理关闭前仍拒绝 held request。
- `BoltAuthenticatedFailureTest`、`BoltProductionResourcesTest`、`BoltUnifiedInvokeTest`：原生超时/异常、caller 放弃等待不释放其他请求、额度回收后下一轮可进入、资源上限与结果分类保持。
- 新增必要回归优先放在现有生命周期测试：公开原生异步 callback 路径证明业务 callback 尚未执行时实际响应已释放额度；重复/迟到终结不使其他在途请求提前释放，随后真实终结才能准入下一轮。若已有同等用例，复用而不重复。精确 protocol / timeout / callback 委托按已有原生接口契约检查，不能因拆分类名删除原断言。
- 并发采用受控 latch、pipeline 观察点及有界 future，不增加 sleep、反射入口或生产测试 seam。纯移动不是缺陷修复，不声称既有行为测试必须在移动前 RED；新场景先在原实现执行建立基线，并用窄变异反证关键锁/释放断言可捕获错误实现。
- 顺序窄命令：`mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=BoltConnectionLifecycleTest,BoltAuthenticatedFailureTest,BoltProductionResourcesTest,BoltUnifiedInvokeTest -Dsurefire.failIfNoSpecifiedTests=false test`；随后同仓库本地库的 Bolt 全模块测试及 `LayerBoundaryTest,BoltInvocationBoundaryTest` 聚焦架构测试，失败先归因。
- `git diff --check` 和真实调用点/旧状态搜索；独立实现 Review 检查单锁、唯一关闭状态、原生方法完整委托及 close 调用的外层持锁情况。
- 完整门禁：最终冻结后由 main 集中执行 clean / full，覆盖 startup、behavior、E2E / HA 与正式 quality；窄验证不替代这些证据。

## 后续文档与执行

独立设计批准后，更新现有 `2026-10-25-bolt-package-boundaries` 活跃计划与 PROGRESS，将提取并入唯一 active 的 T3，不新建并行 active 任务。实施时更新 im-bolt README 的内部职责事实；根架构、Reliability / Security 保证不变，不机械改写。单锁所有权是此实现不变量，沿用既有共享状态/原生完成 Review 规则，不新增类名豁免或专用 checker；若 Review 产生新的通用规范，再同步 owning 规范与 Harness 矩阵。

## 附录 A：备选方案与行业依据

- 保留 Client 内聚实现：没有移动风险，单锁关系直观；但装配/生命周期与完整 future 委托继续混杂，不满足本轮已批准的最小职责拆分。
- 采用独立 Tracker、共享单 monitor（本方案）：只新增一个包级类型，调用状态集中；代价是 Client 与 Tracker 有明确共享锁及关闭标志约定，需要 Review 和并发行为测试保护。
- Tracker 独立锁并复制生命周期状态：可通过更复杂的原子桥接设计实现，但增加双锁顺序与提交协议；当前没有独立生命周期需求，不采用。按每一步拆类或引入责任链同样无独立职责收益。
- 第三方事实绑定仓库当前 SOFA Bolt 1.6.6 的 `InvokeFuture` / `RpcClientRemoting` 接口及既有原生运行测试，不引入新库或声称未经验证的新原生能力。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 用户批准最小职责拆分 | 仅新增包级 BoltClientCallTracker，整体迁移准入、排空和 Completion | 形成真实调用边界，不机械拆步骤 |
| D2 | 原 accounting 保护两类状态 | Tracker 实例为唯一共享 monitor，closed 唯一归 Tracker，初始化状态仍归 Client | 保持 shutdown / admit 原子性与初始化关闭责任交接，不复制状态 |
| D3 | 原生完成协议不能变弱 | 原生 hooks 留 Client，包装/上下文追踪/幂等释放归 Tracker，完整委托不变 | 保留 timeout / callback / protocol 行为和 late completion |
| D4 | 防止 wrapper 堆积 | Handler / Manager 直接使用包级同一 Tracker，删除 Client 原转发入口 | 不新增公共 API、兼容壳或测试 seam |

## 附录 C：独立 Review

独立设计 Reviewer `a35e419d309f04bf8`：Approved，必须项 none。实施须核对 Completion 仅捕获 Tracker、唯一 monitor / closed、future-before-admit 与重复/迟到 finish、锁外 close，并保留 Handler / Manager 原流程与原生协议语义。实施纳入[现有唯一活跃计划](../exec-plans/completed/2026-10-25-bolt-package-boundaries.md)的 T3；设计批准不代表实现或验收通过。
