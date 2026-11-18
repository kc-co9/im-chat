# Bolt 按实际职责拆包设计

> 状态：已批准。用户已明确认可设计，独立设计 Reviewer `ad3341b14c207845a` Approved，必须项 none；现按明确恢复指令进入实施。批准 invocation / connection / channel / client 职责重组与最小 public，唯一 active 计划为 [Bolt 包职责与 Broker 快照归位](../exec-plans/completed/2026-10-25-bolt-package-boundaries.md) 的 T3。clean/full/commit 继续暂停。

## 摘要

不再以 `client` 收纳全部出站协作者。调用编排与预算归 `invocation`，连接创建、认证和原生池管理归 `connection`，Netty 出站校验归 `channel`，客户端装配和业务调用外观归 `client`。只将 Factory / Manager 的 Authenticated 前缀改为 Controlled；不机械修改其他名称中的 Client。

包访问控制不能代替职责边界。保留真实协作，以必要 public 支持跨包调用，不增加桥接接口、兼容壳、无调用 wrapper 或可替换装配入口。状态字段尽量 private，唯一 monitor 和 closed 仍在同一个 Tracker 实例，不复制生命周期状态或安全策略。

## 背景与事实源

当前八个出站类均位于 `im-plugin/im-bolt/.../client`。Invoker 与 Handler 共享 InvocationContext；Client 的原生 future hook、Manager 的失效池处理与 Handler 的发送屏障共享 Tracker；Factory 经 Client 回查同一个 Manager。原「acquire 仅同包」和「Tracker 包级」决定依赖原目录布局，不能作为此次职责拆分后继续放在 client 的理由。

事实源：根 `ARCHITECTURE.md`、根和 `im-plugin/AGENTS.md`、`im-test/AGENTS.md`、im-bolt README、八类完整源码、ImBoltAutoConfiguration、实际测试调用库存、BoltInvocationBoundaryTest、CODING_GUIDE、UNIT_TEST_GUIDE、DESIGN_DOC_GUIDE 及当前 active 计划。Tracker、受控 Client API 和 Factory 依赖评估的已批准设计与既有局部 Accept 保留其原适用范围；本设计仅取代与新包边界冲突的包级可见性决定，不撤回 Factory 继续回查 Client 的结论。

## 目标与非目标

- 目标：生产目录、真实 imports/FQCN、测试归属、脚本路径、ArchUnit fixture 与当前文档一致；所有安全、预算、原生完成和生命周期行为不变。
- 非目标：不改 `BoltClientInvoker(RpcClient, LongSupplier, ImBoltProperties.Client)`；保留 instanceof、策略快照和失败分类；保留 BoltRpcClient 独立 snapshot / selectPeerUriPrefix 校验。
- 非目标：不实施已撤回 bindManager，不复制 mode/stopped/deadline，不新增接口、Supplier、状态机、线程、依赖或可替换安全组件。
- 非目标：不复活 D6 列表 RPC，不回退 URI 前缀、Tracker 或 ControlledClient 成果；不改 wire、配置键、TLS/DEV 规则，不机械重写历史归档，不关闭 T3 或宣称完整验收。

## 已知约束

readiness 本轮实际执行 `./scripts/verify.sh readiness`，exit0，Java21 / Maven3.8.4 / Node24.15.0；Ruby PATH warning 保留，不构成 readiness 失败。实施后 Maven 严格顺序，使用 `/tmp/jraft-artifact-m2`，所有临时文件放 `$CLAUDE_JOB_DIR/tmp`。本轮设计阶段不运行 Maven、clean/full 或提交，不终止任何既有 rg 进程。

Plugin 仍只拥有通用技术能力，不引入业务模块依赖。server/security 不搬迁、不改名；无独立 im-bolt ARCHITECTURE，内部事实更新现有 README，不新建架构文档。

## 方案概览

```text
bolt/
  invocation/ BoltClientInvoker, BoltInvocationContext, BoltClientCallTracker
  connection/ BoltControlledConnectionFactory, BoltControlledConnectionManager
  channel/    BoltOutboundHandler
  client/     BoltControlledClient, BoltRpcClient
  server/     （不变）
  security/   （不变）
```

调用链仍为 `BoltRpcClient → BoltClientInvoker → BoltControlledClient.acquire → Manager → Factory`；Handler 在原来的三个 pipeline 位置检查上下文/绑定/会话/预算并调用 Tracker。Client 创建并持有唯一 Tracker，Client 生命周期同步与 Tracker.admit/finish/drain 继续同步在该实例自身。Factory 仍从 Client 读取固定模式并取得同一个 Manager。

## 详细设计

### 精确 visibility 矩阵

表中未列出的字段和方法不批量公开。既有 public/protected 原生 override 保持原访问级别和完整方法体。类继续 final。

| 类型 / 成员（目标包） | 目标访问级别 | 真实调用与理由 |
|---|---|---|
| invocation.BoltClientInvoker、构造器、invoke、usesControlledClient | 保持 public | AutoConfiguration、Broker 装配/Adapter/测试、HA 等真实调用；构造签名不变 |
| invocation.BoltInvocationContext | public final | channel.Handler 必须读取具体上下文；不是新可替换接口 |
| InvocationContext 构造器、remainingMillis | package-private | 仅同包 Invoker 使用 |
| InvocationContext.KEY | public static final | Invoker 写 InvokeContext，Handler 读同一键；值随 FQCN 一致变更，不上 wire |
| InvocationContext.target、expired | public | Handler 跨包比较目标和预算，Invoker 仍使用 expired |
| InvocationContext target/startedNanos/budgetNanos | private final | 不开放可写预算或目标 |
| invocation.BoltClientCallTracker、构造器 | public final / public | client.Client 创建唯一实例 |
| Tracker.track 两重载、finishSync | public | Client 的原生 RpcClientRemoting future hook，不能删减原生委托 |
| Tracker.admit / drain | public | channel.Handler 准入/排空，connection.Manager 失效池排空 |
| Tracker.closed | private volatile | 仍是唯一 closed；不公开 writable field |
| Tracker.isClosed() | public | Client 在原生命周期锁块及 acquire 锁外读取；直接返回同一 volatile 值 |
| Tracker.closeAdmission() | public synchronized | Client 原三类写入位置：初始化 catch、初始化线程 shutdown、自普通 shutdown；只将同一 closed 置 true，不 reopen，不做物理关闭 |
| Tracker.Completion、COMPLETION、计数/集合与内部 finish | 保持 private / 原内部最小访问 | 不公开原生包装实现，不新增并行状态 |
| client.Client.calls | private final | 原包级字段不改成 public；Client 仍直接 synchronized(calls) |
| Client.callTracker() | public | Handler/Manager 取得 Client 已创建的同一个 Tracker；直接返回 final 引用，有真实调用，不创建、不替换、不委托一套平行准入 API |
| Client.acquire(BoltTarget,int) | public | invocation.Invoker 已跨包；恢复真实必要入口，所有 running/closed/目标/DEV 校验和异常完整保留 |
| Client.manager() | public | connection.Factory 继续回查所属唯一 Manager；不新增 setter/bindManager |
| Client.developmentMode() | public | Factory/Manager/Handler 读取已固定 snapshot；不追加 mode 参数 |
| connection.BoltControlledConnectionManager、构造器 | public final / public | Client.init 真实创建、装配；构造参数名称与类型同步改名，参数个数不变 |
| Manager.acquire / stopped | public | Client 获取连接、运行装配检查；Factory 交付前检查停止 |
| Manager.binding(Url) | package-private | Factory 与 Manager 同在 connection，仅需包级；完整重验保留 |
| Manager.Binding | public 嵌套 record | Handler 跨包读取 target；record 成员不可变，生成 canonical 构造器/访问器按 Java public record 规则可见，不新增可写字段 |
| connection.BoltControlledConnectionFactory、构造器 | public final / public | Client.init 必须构造；无新的装配替换能力 |
| Factory.BINDING / DEVELOPMENT / FACTS | public static final | Handler 跨包读取相同 AttributeKey，Manager/Factory 仍读写原属性；键字符串不变 |
| Factory.client/scope/candidate、Manager.policy/client/creation/pending/stopped | 保持 private | 不批量公开状态，也不把 stopped 复制到 Factory |
| channel.BoltOutboundHandler、构造器 | public final / public | Client 原三个 pipeline 安装点跨包构造；保留 client/commandPosition 两参数与原 pipeline 名 |
| Handler.client/commandPosition | private final | 不增加安装后可变模式或替换字段 |
| client.BoltRpcClient | 不变 | 独立目标策略验证保留，委托仍为 BoltInvoker |

`isClosed` / `closeAdmission` 是对唯一状态的受控读取和单向迁移，不是仅供编译的空 wrapper。Client 原所有 `synchronized(calls)` 外层块保持原边界，closeAdmission 的重入同步不释放外层锁；admit 与停机标记仍以同一个 monitor 线性化。初始化状态、owner、settled、ownedStarted 不移动。track、finishSync、admit、drain 和 Completion 委托算法不改；Channel.close 仍在 Tracker 锁外。callTracker 只返回已有实例，不返回 monitor 副本，不新增锁对象。

### public 与安全装配规则

Java public 不是业务许可。Factory/Manager 的 public 构造器是 Client 跨包装配所必需；外部业务直接构造 ConnectionFactory 仍被现有 ArchUnit `isAssignableTo(ConnectionFactory)` 构造调用规则拒绝。Client.setConnectionManager 继续无条件拒绝，option 继续冻结；实际 running manager 与 Invoker 同源校验不变，无法靠新建一个 Manager 或 Handler 替换受控 Client 的装配。

AttributeKey 的 public final 固定引用不等于 Channel 属性不可写；同样，public Tracker 方法不是恶意同 JVM sandbox。现有 D11 已明确信任同 JVM 装配代码，不承诺特权代码不能改 Channel/pipeline 或调用管理 API。本轮不扩大安全承诺，也不以「新增 public」为由弱化 checker。业务 invoke/oneway、option/manager 替换、ConnectionFactory 构造及 raw/pipeline/unsafe 禁令全部不变；若实际编译/架构结果揭示冲突，先停止并提交精确冲突，不扩排除包、类名例外或 rule 范围。

### 测试归属与最小支持

按主要断言职责迁移，不再把测试留在 client 只为 package-private acquire：

- `BoltUnifiedInvokeTest`、`BoltAuthenticatedFailureTest` → invocation：编排、预算、分类与 RpcClient 契约；两个 acquire Mockito stub 现在使用真实 public API，不新增 seam。
- `BoltConnectionLifecycleTest`、`BoltAuthenticatedTlsTest`、`BoltProductionResourcesTest`、`BoltProductionBackgroundTest` → connection：共享创建、复用/退休、TLS、原生完成、资源和后台禁用。保持原测试类名，不机械把 Authenticated 测试改成 Controlled。
- `BoltDevelopmentModeTest` 保留 client：客户端 DEV 构造/取连接拒绝，沿用原行为断言。其余根包安全 gate / AutoConfiguration / Processor 测试不为目录整齐迁移。

当前 Unified 与 ProductionResources 使用 Lifecycle 的两参数 server helper，Lifecycle 自身还使用四参数版本。不得公开测试类充当跨包 support：将 Lifecycle.server 的两个重载及原完整实现移动到既有 `BoltSecurityGateTestSupport`；两参数版本供 invocation/connection 测试调用，四参数版本供 connection.Lifecycle 调用，因此两个重载均需 public static。不留 Lifecycle 转发或复制实现，不新增 support 类；support 中其他成员维持现状。两个重载都有真实调用，不为可能的测试复用增加新方法。

根 support 已有最小 public TLS 材料、policy、SessionFacts/PresentedWindow，按实际编译只修必要访问，不批量公开字段。所有测试方法、参数化输入、断言、latch、清理与实际原生边界保留。

### 全仓引用与路径

更新 AutoConfiguration、Broker 的 ClientConfiguration/BrokerClusterConfiguration、实际 Broker/Account 启动和集成测试、HA 及所有实际 `BoltClientInvoker` import/FQCN；不修改名称相似但无关的 Client 类型。对 `git ls-files -co --exclude-standard` 的有效文件做有界逐文件库存，包含未跟踪源码，不依赖已卡住 rg。

`ControlledProtocolWrite` 正例 fixture 从 bolt.client.fixture 迁到 bolt.channel.fixture，BoltInvocationBoundaryTest 只同步其 importPackages 正例位置，violations 与所有反例/断言原样保留。scripts/test-harness.sh 当前硬编码 ControlledClient 路径不变，但逐项核对其余脚本/源码路径，发现实际旧 Invoker/Factory/Manager/Handler 路径才同步；不为展示新树添加无必要 fixture。

## 可靠性与安全

所有目标验证、TLS/DEV、证书数组窗口、绑定、迟到/停止复查、失败分类和中断处理不变。唯一 closed 由 Client 在既有共享锁内单向关闭；admit 的拒绝、future-before-admit 补偿、多次/迟到完成幂等与锁外关闭均保留。Factory ThreadLocal 嵌套恢复、候选所有权及未交付关闭原样保留。Manager stopped/任务代次/共享预算没有新增副本。

## 运维、兼容与回滚

旧六个移动类的 FQCN 移除（Invoker/Context/Tracker/Factory/Manager/Handler），Factory/Manager 同时改名；不保留兼容类。外部未知消费者必须同步重建，Java 源码/二进制兼容破坏明确接受，wire/配置/证书/数据无需迁移。acquire 再次 public 是职责拆包的明确取舍，不应继续宣称包外不可访问。

回滚只恢复本轮精确包名、必要 visibility、测试移动和文档，不覆盖混合用户更改，不复活 D6 或旧前缀。没有性能/容量/生产部署声明。

## 验证策略

实施获准后先快照精确文件/SHA，保存六个迁移测试原 FQCN baseline、源码方法/参数化输入清单和 XML invocation 名；生产改动前 baseline 必须成功或先归因。先核实本仓没有并行 Maven，不触碰外仓进程。

沿用上轮 allowlist 隔离：只允许 im-bolt target/classes 的 `client/{BoltClientInvoker,BoltInvocationContext,BoltClientCallTracker,BoltAuthenticatedConnectionFactory,BoltAuthenticatedConnectionManager,BoltOutboundHandler}.class` 及对应 `$*.class`；test-classes 的 client 直属上述六个迁移测试及 `$*.class`；architecture target/test-classes 的旧 client/fixture/ControlledProtocolWrite 及 inner。不包含保留 Client/RpcClient/DevelopmentMode 或新目录。以 pathlib 列清单、大小/SHA 后人工核对再搬至 job tmp，拒绝符号链接、越界、清单外新增、SHA 变化和目标已存在；不删除未知文件、不运行 clean。每轮扫描旧 class 重现，若重现立即停止归因，不猜 writer。

每条 Maven 记录完整命令、UTC 起止和 exit；只收本轮 marker 后更新且立即复制的 XML。baseline/focused/module 六测试按 classname/name 参数 invocation 比较，仅归一已批准包名，旧 FQCN 不得出现，新 invocation 无重复，源码方法和参数输入清单一致；其余模块测试不得以旧报告计数。时戳不足时先隔离对应旧 XML 再跑。不把 simpleName 匹配或旧 class 残留成功当迁移证明。

顺序窄门禁（完整 FQCN focused 为六迁移组加保留 DEV）：

```bash
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=com.co.kc.imchat.plugin.bolt.invocation.BoltUnifiedInvokeTest,com.co.kc.imchat.plugin.bolt.invocation.BoltAuthenticatedFailureTest,com.co.kc.imchat.plugin.bolt.connection.BoltConnectionLifecycleTest,com.co.kc.imchat.plugin.bolt.connection.BoltAuthenticatedTlsTest,com.co.kc.imchat.plugin.bolt.connection.BoltProductionResourcesTest,com.co.kc.imchat.plugin.bolt.connection.BoltProductionBackgroundTest,com.co.kc.imchat.plugin.bolt.client.BoltDevelopmentModeTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=ImBrokerApplicationTest,BrokerPeerAdapterTest,BrokerAutomaticFailureObservationTest,GatewayDevelopmentTargetConfigurationTest,BrokerGossipAdmissionIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=BoltConsumerConfigurationTest,ImWsGatewayApplicationTest,ImAccountApplicationTest,ImMessageApplicationTests -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-e2e-test,im-test/im-routing-ha -am -DskipTests test-compile
bash scripts/test-harness.sh
git diff --check
```

baseline 首条使用当前 client 包七个精确 FQCN，其余参数一致。新旧源码归一对比限于明确包名/Factory-Manager 类名、访问修饰符、calls.closed→受控方法和 calls→callTracker 真实访问；admit/drain/Completion 及所有安全/预算方法 token 应完全一致。另检查唯一 private volatile closed、无公开可写状态、Client 所有原同步块与关闭物理动作位置、构造签名与 RpcClient 独立校验。架构四类正反场景继续使用同一 checker，不新增豁免。

窄门禁不等同 full；E2E/HA test-compile 不是网络执行。正式 quality、完整混合工作区 Review、真实 E2E/HA 和 clean/full 缺口保留，等待用户另行解除暂停。

## 后续文档与执行

Approved 后才追加现有 active `2026-10-25-bolt-package-boundaries.md` 的 T3 checkbox 和准确文件/命令，并同步 PROGRESS，仍唯一 active。本设计与索引目前只记录待 Review，不把未批准方案写成运行事实。实施时更新 im-bolt README 包职责、public acquire 与跨包内部 API 说明；当前有效设计增加本设计取代链接，不改过去批准/失败事实或历史命令。根 Architecture 无具体旧包无需强改；可靠性/安全保证不变，无须制造规范变更或 Harness 新例外。

## 附录 A：备选方案

- 维持 client 紧耦合包：包级封装成本最低、改动较少，但把调用、连接与 channel 混成 client，用户已明确不采用。
- 按职责拆包并最小 public（采用）：目录表达真实职责，代价是 Java API 面扩大和外部重编译；通过 private 状态、final 协作者、固定装配和原规则保留行为边界。
- 以桥接接口/Client 转发方法隐藏跨包类型：能够减少直接可见类型，但增加无业务职责抽象，掩盖真实协作，用户明确拒绝。

只使用现有 Java 包访问规则和仓库 SOFA Bolt1.6.6 协作，不新增外部库能力主张。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 用户批准实际职责重组 | 采用明确目标树，server/security 不变 | client 不再兜底，Factory/Manager 改 Controlled，不改其他 Client 名称 |
| D2 | 真实跨包调用 | 按矩阵最小 public，不新增桥接接口或可替换装配 | Java 可见性与业务安全许可分离，保留原 checker |
| D3 | Tracker 状态封装 | private volatile closed，isClosed/closeAdmission；同实例 monitor 与 private final calls | 不暴露 writable closed，不复制状态或锁，原交接线性化点不变 |
| D4 | Factory 依赖评估 | 保留 public client.manager()/developmentMode() 的真实回查 | 不实施已撤回 bindManager，不新增初始化失败阶段 |
| D5 | 用户限定行为不变 | RpcClient 独立校验与 Invoker 原生 RpcClient 构造完整保留 | 不借目录调整偷渡安全/契约重构 |
| D6 | 旧 class / 测试残留 | allowlist 隔离、same-run 精确 FQCN/method/参数清单 | 防漏跑双跑，不以旧产物假通过，不运行 clean |
| D7 | 用户暂停验收 | 独立 Approved 后再实施，窄 Maven 严格顺序 | clean/full/commit 禁止，T3 不关闭 |

## 追加：两处可读性提取（已批准，实施中）

用户最终明确要求「你先调整一下看看」，授权实施以下 Optional 版本；独立设计 Reviewer `a38eedb4de94875cc` Approved、必须项 none，仅覆盖 Optional failure 与 Tracked 静态内部类。原职责拆包的批准和局部 Accept 不覆盖本追加，本次设计批准不代表实现 Review 或验收通过。

- `client/BoltControlledClient.java`：将 `init()` 中匿名 `RpcClientRemoting` 提取为 `private static final TrackedRpcClientRemoting`。构造器显式接收原生 `CommandFactory`、`RemotingAddressParser`、`ConnectionManager` 和已有 `BoltClientCallTracker`，前三者原样传给 super，init 仍传 new RpcCommandFactory()；仅保存同一个 Tracker final 引用，不隐式持有外层 Client。三个 override 的方法体及签名原样迁移，保留 super 委托、terminal 标记、仅 catch InvokeTimeoutException 和 finally finishSync。
- `channel/BoltOutboundHandler.java`：用户追加要求返回 failure，因此将请求上下文读取及原 if-else 校验块提取为 `private Optional<BoltInvocationException> validateInvocation(ChannelHandlerContext, RpcRequestCommand)`；同一拒绝位置返回 Optional.of 包裹的原 BoltInvocationException，成功返回 Optional.empty()。`write()` 对结果 isPresent 时 setFailure(get()) 并 return，之后原 Connection 读取、admit 的 RuntimeException 处理及 super.write 不变。不返回 null，不新增 try/catch 或结果类。原上下文/属性读取顺序、模式判断、短路检查、Instant.now/System.nanoTime 的读取时机、分类、message、cause、drain 动作保持不变，其他异常不扩大捕获。

范围仅为上述两个生产文件及本设计、现有 T3 计划、PROGRESS 的追加记录；不重命名其他成员，不修改 client 策略字段、生命周期、Tracker、Invoker 或测试断言。新增类、字段、构造器和方法补充简洁中文注释。无新工程规则或 Harness 例外。

验证：readiness 已实际 exit0（Ruby PATH warning 保留）。保存两个文件原件/SHA 及包含未跟踪源码的精确 diff；严格顺序运行七测试 focused（本设计验证策略第一条）、完整 Bolt module（第二条）、架构 LayerBoundaryTest/BoltInvocationBoundaryTest（第五条），均使用 `/tmp/jraft-artifact-m2`，逐条保存完整 argv、UTC 起止、exit 和 same-run XML。先失败先归因；静态比较三个 override 原始方法体与校验条件/顺序/时钟/排空，git diff --check。证据放 `$CLAUDE_JOB_DIR/tmp/readability-*`，不停止已有 rg，不运行 clean/full/commit，不关闭 T3；实现完成交独立 Review，不冒称完整验收。

### 最终追加：两个校验方法与统一 promise 失败（已批准，实施中）

用户已明确回复「可以」，批准本节取代上面的 Optional 校验方案。独立设计 Reviewer `a38eedb4de94875cc` 对本节复核 Approved、必须项 none，已收到明确恢复实施指令。此前 Optional 证据仍仅覆盖旧输入；本节设计批准不代表实现 Review 或新行为验证通过。

最终 `write()` 仅有一个 try/catch：try 内先 `validateMessageType(message)`；若为 RpcRequestCommand，再 `validateInvocation(context, command)`、读取 Connection、admit。catch 仅 RuntimeException，调用 promise.setFailure(rejected) 并 return。`super.write(context, message, promise)` 在 try/catch 外。保留唯一 RPC if，不为消除 if 增加分发对象。

- 新增 `private void validateMessageType(Object message)`：接受原五类 RpcRequestCommand、ByteBuf、HeartbeatCommand、HeartbeatAckCommand、RpcResponseCommand；其余 throw 原 SecurityException，message 不变。
- `private void validateInvocation(ChannelHandlerContext, RpcRequestCommand)`：原上下文/属性读取、development 判断、原 if-else 短路顺序、Instant.now/System.nanoTime 位置、drain 动作保留；四类拒绝在原位置 throw 同一分类、message、cause 的 BoltInvocationException。无 Optional、null 返回或内部 try/catch。
- **明确的行为变化**：原校验或 Channel 属性读取、development 判定、drain 的意外 RuntimeException 原先向外传播，现在与 admit 拒绝统一使 promise 失败且不继续出站。Error 不捕获；下游 super.write 异常不捕获。不得把本节称为全部行为等价提取。Tracker/Client 生命周期、预算和认证规则本身不变；TrackedRpcClientRemoting 静态内部类保持上一节实现，不再修改。

精确新增范围：上述 Handler 和既有 `invocation/BoltUnifiedInvokeTest.java` 的聚焦回归；Client 已完成静态内部类继续保留。测试直接调用真实 Handler.write，Mock 直接框架协作者，不让 Netty pipeline 自身 catch 掩盖 Handler 边界；同包使用真实 BoltInvocationContext、真实 Tracker 和固定时间输入，不反射私有方法、不新增生产 seam。覆盖：原五类接受/unsupported 拒绝；上下文/目标/会话过期/deadline 的分类和 cause；过期 drain 动作；admit 拒绝；属性读取或 drain 意外 RuntimeException 统一 promise 同一 cause 且不转发；Error 透传；super 经 context.write 的下游 RuntimeException 透传且不被改为 promise 失败。优先复用现有真实生命周期/发送屏障测试作为集成补充，新意外异常边界先 RED（当前 Optional 版失败）再实现 GREEN。

本节不增加复用工程规则、生产 seam 或 Harness 例外，仅记录本 Handler 的具体异常责任。若现有规则与测试呈现冲突，先归因并提交审查，不扩大豁免。

Optional 阶段实际证据：focused103 tests exit0（2026-10-26 02:45:49–02:47:20 UTC）；Bolt267 + common37 共304 tests exit0（02:47:40–02:50:49 UTC），均无失败/错误/跳过。仅对应前一 Optional 输入，不作为本节新异常边界的通过证明。旧版架构未运行。完整 argv/UTC/exit/fresh XML 分别在 `$CLAUDE_JOB_DIR/tmp/readability-focused` 与 `readability-module`；原 before、effective-before、exact diff、三 override/校验归一比较及 javap 无外 Client 字段证据保留。

新版获独立 Approved 后，保存 Optional 阶段快照和最终精确 diff，先跑新增 focused 回归 RED，再最小修改 Handler 并同命令 GREEN；随后严格顺序执行上面验证策略的七测试 focused、Bolt module、LayerBoundaryTest/BoltInvocationBoundaryTest（同一私有 m2 `/tmp/jraft-artifact-m2`）。每条独立保存 argv、UTC 起止、exit 和 fresh XML；先失败先归因，不以旧结果覆盖。T3 唯一 active，不关闭；clean/full/commit 继续禁止，独立实现 Review 由 main 后续安排。

### 可读性修订：内联消息类型白名单（2026-10-26，用户已批准）

本节取代上一节中提取 `validateMessageType()` 的结构决定，其余异常契约不变。用户明确认为该判断无需封装，并批准类型白名单直接放回 `write()` 的 try 内；删除无其他调用者的 private 方法，五种允许类型、判断顺序和 SecurityException 文案原样保留。`validateInvocation()` 仍为 private void，失败抛异常；统一 catch RuntimeException 和 catch 外的 super.write 均保留。Client 静态内部类、测试断言及其他生产逻辑不改。本次仅单处内联，以精确展开比较和空白检查核对；不以静态核对替代尚未通过的 focused/module/architecture 或完整验收。

## 附录 C：独立 Review

独立设计 Reviewer `ad3341b14c207845a`：Approved，必须项 none。用户亦明确认可设计，已收到恢复实施指令。Review 覆盖 visibility 矩阵、唯一锁/closed、跨包 public 与固定安全装配、测试支持和旧产物隔离方案；设计 Approved 不代表实现 Review 或验收通过。
