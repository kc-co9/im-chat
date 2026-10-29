# Bolt 受控客户端命名与连接获取封装设计

> 状态：已批准。用户明确批准 `BoltAuthenticatedClient` → `BoltControlledClient` 及 `acquire(BoltTarget, int)` 收为 package-private 两项最小调整；独立 Reviewer `a515ed306ad89329e` 复核 Approved，三项必须项关闭；已明确恢复实施。本轮不批准 Invoker 构造参数类型调整，不运行 clean/full，不提交。

## 后续职责拆包

本文 Client 改名与校验保留；同包 Invoker、acquire 包级可见与七测试统一位于 client 的布局由已批准的[按实际职责拆包设计](2026-10-25-bolt-responsibility-packages-design.md)取代。新设计接受 Invoker 跨包调用必需的 public acquire，按真实职责重分测试；不改本文历史 Review/验证记录。

## 摘要

将客户端名称统一为 `client.BoltControlledClient`，表达 MTLS 与显式受限 DEV 模式下的受控装配，而非把所有运行方式统称为认证。连接获取方法仅供同包 Invoker 协作，不再作为跨包公共 API。目标选择、typed 调用、TLS、策略检查和生命周期算法均不改变。

编译影响不能只按生产调用者判断：七个 Plugin 测试直接调用或 Mockito stub `acquire`，三个消费方配置测试也直接调用。前者按实际职责移至 `client` 包，后者保留 owning module，通过正式 `BoltInvoker.invoke` 观察拒绝；不增加 public wrapper、反射或测试 seam，不删除安全断言。

## 背景

当前 Client 同时支持 MTLS 和受限 `DEVELOPMENT_PLAINTEXT`，已有 `isRunningControlledAssembly`、`usesControlledClient` 等受控术语。`acquire` 返回原生 Connection，仓库内唯一生产外部调用者为同一 `client` 包的 `BoltClientInvoker`。Factory/Manager 仍是认证连接职责，不随 Client 名称级联改名。

事实源为当前 `im-plugin/im-bolt/README.md`、根 `ARCHITECTURE.md`、Client/Invoker/RpcClient 源码、模块测试和全仓有效文件库存。本轮已按 `git ls-files -co --exclude-standard` 有界逐文件核实，不依赖现存卡住的 rg 进程。

## 目标与非目标

- 目标：真实源码、测试、import、脚本路径及当前文档统一新类名；`acquire` 仅包级可见；保留所有现有拒绝、资源和生命周期断言。
- 非目标：不调整 `BoltClientInvoker(RpcClient, LongSupplier, ImBoltProperties.Client)` 的参数或字段类型；不删除 instanceof 检查；不改变 `BoltRpcClient` 独立 snapshot/目标策略校验；不级联改 Factory/Manager 名称；不改 wire、配置键、预算、失败分类或 Tracker。
- 非目标：不机械改 completed/history 等历史归档，不添加旧类兼容壳，不借机统一搬迁全部测试，不开展完整验收或提交。

## 已知事实与约束

- 所属模块为通用 Plugin；Broker/Gateway/Service 只同步实际类型引用，业务和状态所有权不变。
- 七个直接访问测试为 `BoltUnifiedInvokeTest`、`BoltDevelopmentModeTest`、`BoltProductionBackgroundTest`、`BoltProductionResourcesTest`、`BoltConnectionLifecycleTest`、`BoltAuthenticatedTlsTest`、`BoltAuthenticatedFailureTest`。
- Gateway、Account、Message 各自的 `BoltConsumerConfigurationTest` 第一个成功启动场景包含跨包 acquire 拒绝断言；三者不能迁入 Plugin，也不能仅删除该断言。
- E2E `RealtimeMessagingE2ETest`、HA `AutomaticFailureTlsHaTest` 使用 Client 类型，但库存未发现它们调用 acquire，仅同步类名。
- `BoltSecurityGateTestSupport` 现为根测试包 package-private，既被待移动的四个测试使用，也被六个留在原包的测试使用。`ImBoltAutoConfigurationTest` 另调用 Lifecycle 的 `policy` helper。移动后必须显式解决测试源码可见性。
- 未发现七个测试的相对资源加载或 `src/test` 硬编码路径；TLS fixture 由临时目录和 keytool 生成。脚本 `scripts/test-harness.sh` 包含旧 Client 源码路径。架构生产扫描排除测试；不放宽任何规则或新增 class-name 豁免。
- readiness 已实际 exit0：`MAVEN_OPTS="${MAVEN_OPTS:-} -Dmaven.repo.local=/tmp/jraft-artifact-m2" ./scripts/verify.sh readiness`。日志 `$CLAUDE_JOB_DIR/tmp/controlled-client-readiness.log`。后续 Maven 同样使用该私有 m2 并严格顺序执行。

## 方案概览

正式调用链仍是 `BoltRpcClient → BoltInvoker / BoltClientInvoker → BoltControlledClient.acquire → 既有 Manager/Factory`。RpcClient 和 Invoker 各自的策略快照和校验都保留；只有类符号和 acquire 的 Java 访问修饰符变化。Tracker 继续持有原 monitor、closed、在途计数和完成包装，不改算法。

## 详细设计

### 类名与访问面

Client 文件、类声明、构造器、内部自引用、字段/参数类型、import、instanceof 和实际类文字引用统一新名字，不改普通变量名或原生 pipeline 名。`usesControlledClient` 参数同步新类型，方法名不改。`acquire` 删除 public，参数、返回类型、throws 和方法体不改。

全仓实际引用包括 Broker 装配/Adapter/测试、消费方启动测试、Plugin 自动配置/协作者/测试、E2E/HA 和 Harness 路径。按库存更新当前 README、有效设计说明及 active 计划的当前恢复说明；历史验收命令和归档路径保持原事实。若测试按 Java simpleName 构造本地 ApplicationContext 查找键，同步新类名所致的本机键变化；不改 wire service/operation、配置或身份。

### 精确测试迁移范围

只将上述七个测试由 `src/test/java/com/co/kc/imchat/plugin/bolt/` 移至同目录的 `client/`，package 改为 `com.co.kc.imchat.plugin.bolt.client`。`BoltDevelopmentModeTest` 增加根包 `ImBoltAutoConfiguration` import；其余依实际使用补 import，清理同包冗余 import。保留方法、参数化数据、断言、同步和清理逻辑。

共享 fixture 不迁移，以免为了一个包级 API 搬迁其他六个测试。根测试包 `BoltSecurityGateTestSupport` 仅开放四个迁入测试确实需要的测试源码 API：类、无参构造器、`NativeMaterial` 与 `CertificateMaterials` 类型及其所需构造器/材料方法、`URI_IDENTITY`、`authenticate(Channel)` 及其返回的 SessionFacts/PresentedWindow 类型。其余 fixture 内部成员维持现状；编译报告若暴露额外访问，必须先核对真实使用而非全量 public。此处 public 只存在于 test 源集，不新增生产访问口，不开放新的连接获取委托。

`ImBoltAutoConfigurationTest` 保持原包，Lifecycle 测试类保持 package-private。把 Lifecycle 已有七行 `policy(NativeMaterial)` helper 原样移动到 `BoltSecurityGateTestSupport`，仅该共享 helper 改为 public static；更新 Lifecycle、Unified、ProductionResources、ProductionBackground 和 AutoConfiguration 的全部实际调用，不保留 Lifecycle 转发方法，不新增工具类或重复 TLS 配置。跨包使用的 record 若存在显式 canonical 构造器（例如 PresentedWindow），须与 public record 保持合法访问级别；只对真实需要公开的 record 调整，不批量公开其他构造器或内部成员。

三个消费方测试保持原文件/包；将 `client.acquire(invalidDevTarget, 1000)` 替换为上下文真实 `BoltInvoker` Bean 的 typed `invoke`，不 mock 或绕过装配。仍验证同一个 `10.0.0.1:18020` 非 DEV 地址被拒绝。当前 Invoker 在 acquire 前已把 phase 设为 TRANSPORT_UNAVAILABLE，该 DEV 地址 IllegalArgumentException 经 typedFailure 映射为 `Failure.PROTOCOL`，不是 LOCAL。实施前用真实 Bean 基线确认这一现有分类；迁移后断言明确的 `BoltInvocationException`、PROTOCOL、直接 cause 为 IllegalArgumentException 及其 `development address` 原因，不改变生产分类来迎合测试。不把任意异常、间接 root cause 或网络 timeout 当作指定拒绝。消费方同时核对调用前后真实 Client 的 `getAllManagedConnections()` 为空，证明拒绝未创建受管连接；Plugin 的 `fixedAddress...` 现有空受管连接断言继续保留。若真实基线与上述推断不同，先记录实际原因并修订设计，不能悄悄改生产。

### 兼容性

本轮明确破坏 Java 源码/二进制兼容：旧 FQCN 移除，外部 acquire 调用不可再编译。外部消费者未知，必须同步重建并经 BoltInvoker/BoltRpcClient 调用，不提供旧壳或临时 public wrapper。协议载荷、目标身份、配置键及 TLS/DEV 行为不变。

## 可靠性与安全

不改变任何校验、拒绝或资源边界。Client 类名更准确不等于取消认证：MTLS 原生 TLS、URI 身份、会话区间、逐命令屏障、DEV 显式条件以及装配同源校验全部保留。同步/异步原生完成、超时/失败映射、预算和 shutdown 交接均不变。包级可见性是 API 封装，不声称提供恶意同 JVM 代码隔离。

## 运维与交付

无配置/证书/持久化迁移，无新依赖，无容量声明；只要求使用新 API 重新编译。保留 D6 删除、Peer URI 前缀迁移、Tracker 和所有无关混合改动，不推断外部并发保存的 writer。临时库存、日志或反证文件仅放 `$CLAUDE_JOB_DIR/tmp`；不触碰现存 rg 进程。clean/full/commit 继续暂停，聚焦通过不构成验收。

## 风险与回滚

- 风险：旧 class 残留可能掩盖类型迁移；测试 FQCN 变化可能遗漏显式脚本路径；共享 test fixture 可见性遗漏导致编译失败；未知外部消费者不兼容。
- 回滚：只回滚本轮精确改名、访问修饰符、七个测试移动及三处正式入口断言调整，不覆盖混合工作区，不复活 D6 或旧前缀。
- 延后事项：Invoker 构造类型调整本轮未批准，禁止顺手做。完整 E2E/HA/full、正式 quality 与全局独立实现 Review 等待用户重新授权验收窗口。

## 验证策略

实施前保存本轮触碰文件快照与库存。编译期访问边界用临时外包 Java 调用方检查：调整前 acquire 可见、调整后 javac 明确因不可访问失败；临时文件不加入仓库，不用反射。新类型由模块及消费者 test-compile 验证，旧类型没有源码声明/可执行引用，不运行 clean。

### 旧产物隔离与同轮报告证据

当前 target 实际已有旧 Client、七个旧测试 FQCN 及 inner class 残留；不能以 simpleName 的 `-Dtest` 成功冒充只运行新测试。先运行原 FQCN 的七测试基线和三个消费方真实 invoke 分类基线，保存退出码、XML、源码方法/参数化输入清单；基线的消费者改动只属于待保留的测试入口迁移，不改生产。

移动源码后、首次 GREEN Maven 前执行有界隔离，只允许以下两组文件：

- `im-plugin/im-bolt/target/classes/com/co/kc/imchat/plugin/bolt/client/BoltAuthenticatedClient.class` 和同目录 `BoltAuthenticatedClient$*.class`。
- `im-plugin/im-bolt/target/test-classes/com/co/kc/imchat/plugin/bolt/` 直属目录中，上述七个测试各自的 `<Test>.class` 和 `<Test>$*.class`，不包含新 `client/` 后代目录或其他测试。

用 `python3 -I` 的 pathlib 对这些明确 target 目录和八个类名前缀逐一列出候选，保存相对路径、大小和 SHA-256 到 `$CLAUDE_JOB_DIR/tmp/controlled-client-stale-artifacts.tsv`，先检查清单再移动至 `$CLAUDE_JOB_DIR/tmp/controlled-client-stale-artifacts/` 保留相对路径。移动阶段拒绝符号链接、越界路径、非 `.class`、清单外新增文件、SHA 变化及目的已存在；绝不碰源码、未知 target 文件、其他模块产物或全量 target。若权限或并发输出使精确隔离不允许/不稳定，停止使用该输出，通过 Maven 显式隔离输出配置到无旧产物的新目录并记录实际有效 classpath；未证明无旧产物前不计算 GREEN。不得用 clean 代替隔离。

基线和每次 GREEN Maven 均在 `$CLAUDE_JOB_DIR/tmp` 保存启动 marker、命令和退出码；本轮 XML 只接受执行后更新并立即复制到独立轮次目录的报告。解析 `testcase@classname` 和 `name`：七个迁移测试必须只出现 `com.co.kc.imchat.plugin.bolt.client.<Test>`，旧根包 FQCN 不得出现，新 FQCN 无重复条目；比较基线与 GREEN 的方法/参数化 invocation 名和计数（只归一包名），并结合源码参数清单确认无漏跑或双跑。完整模块运行也检查这七组且保留其余原有测试。旧 XML 可以留在 target，但不得纳入 same-run 汇总；若时间戳粒度无法区分则先归档隔离对应旧 XML，再运行。每次 Maven 后重新扫描旧 class，发现并发重现先停止归因，不推断 writer，也不据该轮结果宣称迁移验证通过。

同包 Mockito stub 不应改变行为。拟顺序门禁如下；基线首条使用旧根包七个完整 FQCN，GREEN 首条使用下列新完整 FQCN，其他命令为 GREEN。三个消费者真实 Bean 的分类基线命令与下方 consumer 命令相同，放在原七测试基线后、生产修改前执行。任何较窄层失败先归因处理，不用后续通过覆盖：

```bash
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am -Dtest=com.co.kc.imchat.plugin.bolt.client.BoltUnifiedInvokeTest,com.co.kc.imchat.plugin.bolt.client.BoltDevelopmentModeTest,com.co.kc.imchat.plugin.bolt.client.BoltProductionBackgroundTest,com.co.kc.imchat.plugin.bolt.client.BoltProductionResourcesTest,com.co.kc.imchat.plugin.bolt.client.BoltConnectionLifecycleTest,com.co.kc.imchat.plugin.bolt.client.BoltAuthenticatedTlsTest,com.co.kc.imchat.plugin.bolt.client.BoltAuthenticatedFailureTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-plugin/im-bolt -am test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-broker/im-broker-server -am -Dtest=ImBrokerApplicationTest,BrokerPeerAdapterTest,BrokerAutomaticFailureObservationTest,GatewayDevelopmentTargetConfigurationTest,BrokerGossipAdmissionIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-gateway/im-ws-gateway/im-ws-gateway-server,im-service/im-account/im-account-server,im-service/im-message/im-message-server -am -Dtest=BoltConsumerConfigurationTest,ImWsGatewayApplicationTest,ImAccountApplicationTest,ImMessageApplicationTests -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-architecture-test -am -Dtest=LayerBoundaryTest,BoltInvocationBoundaryTest -Dsurefire.failIfNoSpecifiedTests=false test
mvn -q -Dmaven.repo.local=/tmp/jraft-artifact-m2 -pl im-test/im-e2e-test,im-test/im-routing-ha -am -DskipTests test-compile
bash scripts/test-harness.sh
git diff --check
```

最后两类跨模块只 test-compile 不提供真实网络证据，`RaftBoltCoexistenceSpikeTest` 本轮只随编译检查，不冒充执行过。脚本门禁可能检查其他混合改动，失败按实际位置归因。禁止启动 clean/full、真实 HA 或 commit；最终完整验收仍未完成。

## 后续文档与执行

获独立 Approved 后，才把实施 checkbox 和准确文件范围追加到现有 active 计划 `2026-10-25-bolt-package-boundaries.md` 的 T3，T3 仍唯一 active，并同步 PROGRESS。当前文档重点为 `im-plugin/im-bolt/README.md`、当前有效设计中的类型说明、脚本当前路径；根/局部 Architecture 没有旧类名时不为改名强行修改。可靠性与安全保证不变，不创建新通用规则或 Harness 例外。Review 若发现需新增规则，先明确拥有者和最窄反证，不弱化既有门禁。

## 附录 A：备选方案与行业依据

- 保留原类名/public acquire：不需改调用方，也不会引入 Java 兼容破坏；但不能表达 DEV 下受控客户端的实际语义，且暴露无跨包生产调用者的 Connection 获取接口。用户已拒绝保留现状。
- 改名并仅移动实际访问测试：推荐；代价是外部源码/二进制破坏和少量测试 fixture 可见性调整，收益是包级协作不通过 public seam 暴露。
- 把全部 Plugin 测试搬进 client 或新增 public 测试桥：前者混淆安全/服务端/自动配置归属，后者抵消 acquire 封装目的，均不采用。

本轮只用既有 Java 包访问控制和 SOFA Bolt 1.6.6 封装，不引入新第三方能力或算法；外部行业研究不适用。

## 附录 B：决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 用户明确批准命名 | Client 改名为 BoltControlledClient，无旧壳 | 覆盖 MTLS 与受限 DEV；Java 兼容破坏明确公开 |
| D2 | 用户明确批准封装 | acquire(BoltTarget, int) 改 package-private | 仅同包生产 Invoker 调用，不新增桥或反射 |
| D3 | 本轮权限边界 | 保留 Invoker 原生 RpcClient 构造类型、instanceof 与 RpcClient 独立校验 | 第三项未批准；不可用改名偷渡职责/安全调整 |
| D4 | 跨包测试访问 | 仅七个访问测试迁 client，fixture 最窄 test-only public，消费方用正式 invoke | 保留安全断言及 owning module，不统一搬全部测试 |
| D5 | 验证暂停 | readiness 和顺序窄 Maven，禁止 clean/full/commit | 窄验证不能冒称验收；T3 不关闭 |

## 附录 C：独立 Review

- 第一轮独立 Reviewer `a515ed306ad89329e`：Revise。三项必须修订：消费者 DEV 拒绝分类误写 LOCAL；不应公开 Lifecycle 测试类充当 fixture；旧 class 和测试 FQCN 残留隔离及 same-run XML 证据不够可执行。
- 已按反馈修订设计：保留现有 PROTOCOL 分类并要求真实 Bean 基线、直接 cause/message 与空受管连接证据；Lifecycle 保持 package-private，原 policy helper 移入共享 support，不留转发；列明精确 target allowlist、校验后隔离及新旧 FQCN/参数化报告核对。不代表必须项已由 Reviewer 关闭。
- 当前状态：待原 Reviewer 复核，没有 Approved 结论；本轮只修订设计，未实施源码或测试修改，未执行产物移动或 Maven。clean/full/commit 禁止。

- 第二轮：原 Reviewer `a515ed306ad89329e` Approved，三个必须项均关闭；仅批准进入实施，不代表实现 Review 或验收通过。首轮 Revise 保留。
