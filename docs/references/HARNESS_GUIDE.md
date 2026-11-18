# Harness Guide

成员执行能力使用具体adapter.cluster.membership.BrokerMembershipExecutor，不建立同形接口/Default配对。LayerBoundary不放宽，ModuleBoundary恢复禁止执行器依赖application，并新增禁止CQRS/SDK及Raft core/model，允许既有角色SPI。应用仍编排Begin/Commit，执行器仅维护本机执行能力；同步进入Runtime由BrokerRaftAdapterTest.membershipConfigurationEntersRuntimeSynchronouslyOnCallingThread验证，映射全字段由BrokerMembershipTransformerTest保护，取消/预算/generation沿用执行器和Workflow行为回归。

已准入成员流程的阶段队列与永久 Permit 预算分别有界，最后一份合法预留不得阻断其收尾；同步 Future 通知必须完全离开执行器锁，包括错误/关闭分支的外层锁。自动回归为 `BrokerMembershipExecutorTest.admittedContinuationCanFinishAfterLastPermanentPermitReservation` 与 `sendCompletionNotifiesOutsideExecutorLock`（正常及同步异常，跨线程重入获取真实公开观察）；fault/close 后允许 pending 的批准契约不因此扩大。Bolt heartbeat 入站探针和 EventLoop barrier 不等于原生异步 processor 完成，`nativeIdleHeartbeatAndAckRemainUsable` 在写出 heartbeat 前捕获原始 InvokeFuture，确认其完成响应为同一个真实 ACK，再使用有界等待校验 future 消费与计数；不能仅凭 future 缺失和零计数排除 timeout 路径，不降低断言或引入 sleep。

跨模块成员写契约必须验证入口请求、不可变恢复记录和真实接收端的组合保真，不能用两端分别 Mock 成功替代。五个 CLI 成员动作均原样传递端点，缺端点恢复记录拒绝且不改写；应用服务同步结果必须区分本轮撤回与历史停用，重复目录不得重复失效授权。窄自动证据为 `OpsCliClientTest` 保存/发送/retry 五动作矩阵、`OperationRequestFileTest`、`OpsCliControllerTest`、`OpsBrokerManagementContractE2ETest` 真实 HTTP 拒绝分类及 `ApplicationPermissionAppServiceTest` 连续同步回归；组合测试不替代真实成功成员迁移或 MySQL 事务验收。

成员诊断指标由 `support.monitoring.MembershipDiagnosticMetrics` 保存和注册，应用服务不读取采样状态作准入凭据。`BrokerClusterConfigurationTest` 验证 Gauge 初始值、不可用、漂移与恢复；`BrokerMembershipDriftTest` 验证失效采样不发布漂移。监控状态是否被业务流程误用继续由 Review 检查，暂不新增跨调用数据流静态规则；可靠正反 fixture 可用后再自动化。

成员请求的本地尝试与永久许可记录不能混为一体：提交前确定拒绝不应耗尽 operationId 容量，Begin 已调用或结果未知时不得按空结果盲目回收。`BrokerMembershipDriftTest` 的 REJECTED_CAPACITY 场景覆盖 4096 次漂移拒绝后的新请求恢复；DRIFT_TERM_CHANGED / DRIFT_REVISION_CHANGED 覆盖不相等配置的失效采样不得发布 drift。回收时点、历史提交标记继承及未知提交安全性继续由独立 Review 检查。

成员 operation 的首次准入 generation 必须贯穿 Begin 返回、后续阶段和结果发布；同 term 重启不能重开旧阶段。`BrokerMembershipWorkflowTest#delayedBeginCannotReservePermitAfterSameTermRoleRestart`、`delayedBeginDoesNotPublishOnCallbackWhenWorkerUnavailable` 及 `BrokerMembershipExecutorTest#queuedApplicationStageInvalidatesOnWorkerAfterRoleRestart` 分别保护迟到回调、fault/close pending 和 worker 消费边界。跨方法完整数据流仍由独立 Review 验证，不新增类名例外或放宽已有门禁。

Broker 的 `adapter.cluster.membership` 执行组件只拥有 worker、角色事件、永久预算计数和待执行队列，不依赖 `application` 类型，不保存 learner 业务身份或结果。业务尝试、在途共享、Future 流程编排、失败映射和稳定重放归应用服务。`ModuleBoundaryTest#brokerClusterExecutionMustNotDependOnApplicationWorkflows` 检查依赖方向，`BrokerMembershipWorkflowTest` 保护共享永久预算、generation、在途共享与重放行为，`BrokerMembershipExecutorTest#learnerExecutionReturnsAsyncContinuationToWorkerAndInvalidatesAfterRoleRestart` 及 `learnerExecutionRejectsSecondPendingStageAndAcceptsNextStageAfterConsumption` 保护标准 Executor 的 worker 发布、代际失效和单 pending 约束。业务策略是否完整留在应用层仍需 Review，包依赖检查不能证明完整行为等价。

项目采用 Harness 的原因和设计原则见根 [README](../../README.md#harness-与质量门禁)。本文维护 Harness 运行流程：将现有仓库入口映射为五个子系统，并定义验证入口、规则生命周期、失败归因、反馈维护与清洁状态。规则所有者、当前传感器及 Review 边界统一登记在[规则矩阵](HARNESS_RULE_MATRIX.md)；业务、架构和编码规则由 [Architecture](../../ARCHITECTURE.md)、[Coding Guide](CODING_GUIDE.md)、[SQL Guide](SQL_GUIDE.md)、[Unit Test Guide](UNIT_TEST_GUIDE.md)、[Security](../SECURITY.md)、[Reliability](../RELIABILITY.md)或当前批准的所属设计定义。

## B2 本地工具来源信任范围

Docker 间接调用的凭据助手也是执行来源，不因 Docker CLI 已批准而自动授信。本批仅准入固定 `docker-credential-desktop` 安装及 lexical／canonical 权限链、binary SHA 和源码 launcher，沿用用户 Docker 原协议，不扩大 PATH 或修改配置。来源文件在 aggregate 只引用一次；正式 reader 只读校验与合成 fixture 分开记录，override reader 的 GREEN 不证明本地来源 schema 可消费。隔离 fixture 保护未知助手、来源／链接 metadata／launcher 漂移及 argv、stdin／stdout／stderr、退出码转发；不调用真实助手取证，不记录凭据。具体来源及运行授权归 owning 覆盖率设计与活跃计划。

间接启动的助手 launcher 必须在解释器初始化前固定其所需的最小系统 PATH；本批仅助手子进程使用 `/usr/bin:/bin`。转发协议 stdin 的 wrapper 不用 heredoc 承载解释器源码；使用 `-e` 保留 stdin。隔离回归直接执行真实 launcher bytes，在缺失／错误 PATH 下严格比较协议标准流和退出码，不放宽 stderr 断言掩盖初始化或转发错误。

共享 reactor reader 对 Java 源码、HA 场景表、JSON manifest、Surefire／JaCoCo XML 显式按 UTF-8 读取，不依赖调用者 locale；二进制 class／exec 及摘要仍按原字节处理。不通过继承 LANG 绕开 reader 缺陷。`scripts/test-reactor-evidence.sh` 以 C locale 的独立 CLI 读取中文启动／测试源码、场景与报告，并校验 startup 清单和 manifest check；非法内容仍由原解析及校验拒绝。

full 的模型/Maven、readiness、clean、自测及报告阶段保持 HOME/JAVA_HOME/PATH 三键。仅 HA 受控子阶段按覆盖率设计「HA 现有宿主代理补充」增加固定 `HTTPS_PROXY=http://127.0.0.1:7890`，由 HA 宿主后代继承；这不是域名白名单，不改变 Docker 配置、应用容器环境或 TLS 校验。父进程任意大小写代理和 Docker 覆盖变量不继承，代理失败保持原失败，不回退或跳过。`test-full-orchestration-harness.sh` 在读取 fixture 控制前断言实际子环境，保护固定值、非 HA 缺席、污染隔离及失败传播。设计 Review 与隔离回归不代替实际窄 build 或完整 full/HA。

本轮 B2 采用[覆盖率设计的集中修订](../design-docs/2026-11-03-owned-namespace-coverage-instrumentation-design.md#b2-验收合同集中修订用户已确认实施与选择观测另审)：用户明确接受精确 rg/PCRE、Docker CLI/Compose 安装及相应管理者/共享写入主体作为本地执行信任根，不泛信任整个 Homebrew；这不是官方认证，不防御恶意同机/同 UID 或观察间 TOCTOU。全祖先 `022`、通用动态闭包和精确插件源码不再作为该精确批准安装的默认前提；未知来源仍失败关闭，不能全局删除权限规则。安装链须按独立固定记录核对实际身份、metadata、已知依赖/links 与观察点漂移；私有输出 0700/0600、独占创建与安全目录规则不变。

来源固定、有限环境/九注入拒绝、全部 reader、同环境真实三采、成功前零删除、锁/取消、实际 Compose 选择及真实 coverage/full 义务不变。用户随后选择仅两处纯 config 自测固定 Compose 直调，取消 OS tracing/插件转发机制；这两处消除 CLI 插件发现层，部署/HA Docker 不变。隔离 fake 不证明实际 standalone 协议/JSON 兼容，新 standalone 合同的真实无 daemon config 实测仍待许可和验证；不能将直调自测扩成 HA 插件选择已证明。合同已确认不等于实现已通过或真实执行获准；当前仅授权最小实现、隔离测试和独立 Review，插件观测/真实工具/清理/full 另需许可。旧 Revise/Accept、网络与权限拒绝保持历史原义。当前实现候选为固定 aggregate v2、精确安装 metadata/bytes 校验、专用源码 `scripts/bin/rg` / `docker` 薄 exec 入口及两处固定 Compose config；专用 bin 纳入有限 PATH，不加入原 Homebrew bin。隔离 fixture 覆盖精确工具/PCRE/launcher 漂移、共享链 metadata、薄入口 argv/退出码和固定 config context/失败传播；整批实际结果及独立 Review 由计划登记，未完成前不宣称新合同已验收，真实兼容性仍待证。

2026-11-04 用户明确批准两处结构自测收敛为 standalone 合同，不再要求原 Docker CLI 等价；输入闭包设计独立 Accept，实施/实测未验收。固定 `--env-file /dev/null` 禁用自动 dotenv，明确改变两处自测的默认插值语义；部署/HA 不变。HOME 必须匹配批准 layout，子环境仅 HOME/PATH/DOCKER_CONFIG；YAML 须在 spawn 前符合源码固定的已审 SHA、普通非链接及 canonical 路径，变更须重新审查，不能动态学习当前 bytes。两处 `.env` 的 metadata/SHA 前后观察继续保留，但不作为插值输入；不记录正文。规则正文及精确 SHA 归覆盖率设计，`test-reactor-evidence.sh` 保护零调用拒绝、固定 argv/环境和既有后置漂移。fake 不证明精确工具确实屏蔽 dotenv，新合同真实无 daemon config 另需许可；旧实测保留旧语义与原指纹。普通前后守卫不保证恶意同用户 replace-and-restore 的原子隔离。

## 普通生产源码所有权首片

报告清单按[已批准 synthetic 补充](../design-docs/2026-11-05-jacoco-synthetic-report-inventory-design.md)接入原 validate/check：完整 class 清单 C 绑定 header flags 与 SHA，固定 JaCoCo 0.8.13 的 synthetic class 分类 F 额外记录原因，XML 唯一 class 集合必须等于 C-F。顶层 MapStruct 实现不得进入 F；实际 counter 类型唯一、数值非负，允许无指令 counter 的 class 保留记录。运行测试的生产模块缺 exec/report 拒绝，两产物必须成对、非空且在本轮时间窗口；无测试无产物模块不伪造 coverage。产物摘要、R 与 F 均进入同一 manifest，check 重派生并比对。原隔离 self-test 保护集合／counter及 verify 后替换和伪造；真实 provenance 及固定未执行类 missed 仍需原正式验收，不以合成 exec 内容冒充实际 JaCoCo 数据。

validate/check 对生产 class 和默认 annotations 下全部 generated Java 记录非空、mtime 不早于本轮 startedAt 且不晚于检查时刻；generated 源集合及 SHA 与 class／mapper 一起进入原 manifest。check 重枚举，拒绝新增、缺失和内容替换。时间检查只是补充：真实同轮 provenance 仍来自正式 prepare 的整批清除与后续重建，不能 touch 旧文件冒充鲜度。隔离 fixture 的旧 class／旧生成源／空生成源及验证后新增源回归保护该子合同，真实 full 和 report manifest 义务保持开放。

正式 `prepare_approved_model` 将本轮受控有效模型的 jar 模块交给既有 prepare，加入 classes、generated-sources/annotations、maven-status/maven-compiler-plugin、jacoco.exec 与 site/jacoco 五种固定输出。旧 XML/JAR、exec 及状态文件只允许普通文件，四种目录槽只允许目录；所有存在祖先须为目录，所有后代拒绝链接及特殊文件。整批预检包括旧 manifest／summary，全部通过后才删除；startup 仍仅清原报告文件。隔离 fixture 保护目录清理、类型互换、晚链接／非目录祖先零部分删除与正式模型接线；真实清除和同轮鲜度须另有运行证据，不能用 fixture 替代。预检不防恶意同 UID 并发替换，沿用已审信任范围。

实际插桩由 `CoverageInstrumentationTest` 随 architecture 测试：真实执行 NestedMapUtils、OpsCliHttpTransformerImpl 与 JSqlParser，核对恰好 JaCoCo／Mockito 两 agent、精确 namespace、保留 VM 参数、live exec 两自有类 true probes、HashUtils／parser 无记录及 session。独享 smoke 必须另行核对退出后 exec 的 classId 与 owner class、唯一 distinct session、唯一实际测试，以及 common／Ops 未过滤 XML 的具体方法计数；重复 dump 的同 session 块不等于多个 fork。具体授权与证据归活跃计划，不把增量编译变成 generated 鲜度或完整 full 证据。自动测试保护 live 合同；退出后读取、owner report 与源码/class 摘要绑定目前为限定实测和独立 Review，不新增通用编排框架。

规则所有者为[自有 namespace 覆盖设计](../design-docs/2026-11-03-owned-namespace-coverage-instrumentation-design.md)。`check-java-style.sh` 对普通 `src/main/java` 及默认 `target/generated-sources/annotations` 复用相同词法器：先消费注释、字符串、字符和 text block，再提取唯一 package，要求 `com.co.kc.imchat` 边界与相对目录一致；拒绝 default／lookalike／错路径／重复声明、Unicode escape 与 JPMS descriptor。生成源码只检查所有权，不套用普通源码的结构反模式规则。该有限词法检查不代替 Java 编译器。标准 `test-java-style-harness.sh` 对两种根使用独立正反例，既有结构断言不变。扫描当前 generated 文件不能证明正式 reactor 集合、生成期待集、产物存在性或同轮鲜度；class 当前身份检查见下段，实际 agent／未过滤 report 及鲜度仍须 B3 后续证明，不以源码通过关闭 coverage 缺口。

`ReactorEvidence.validate` 按既有 canonical raw POM oracle 枚举正式 52 项默认 `target/classes`，与已受控有效模型的默认输出合同一致，不递归加入实验或 test-classes。存在的 class 通过长度受界的 constant pool／`this_class` reader 检查 magic、版本头、全部已定义 tag／双槽和名称引用；内部名仅接受 ASCII Java 标识符路径、嵌套／匿名类及 package-info，必须属于 `com/co/kc/imchat/`、匹配相对路径且跨模块不重复。拒绝 symlink 和特殊文件；每模块清单记录路径、内部名及完整文件 SHA256，接入原 manifest 的 summaries／sha256；`check` 重枚举后拒绝新增、删除或替换。`test-reactor-evidence.sh` 包含目录合法但内部越界的实际漏报 RED、合法 header／全部 tag、错误头／截断／索引、清单变化与链接反例，并检查 manifest／外部 sentinel 未被改写。此 reader 不解析方法体、完整 constant pool 语义或 modified UTF-8，不是 JVM verifier。缺失输出目前显式记空；这不是生产输出存在性、MapStruct 期待集、prepare 清除或同轮鲜度证明，B3 仍未验收。

上述 class 身份首片的「缺失记空」已由 B3 存在性增量收紧：默认生产根中有普通 Java 源码时，class 清单必须非空，资源文件不能充当 class。不存在源码根或仅有无注解文档型 `package-info.java` 可保持空；带注解的包文件仍要求产物。源根及遍历条目拒绝链接、非目录根和特殊文件；普通源码路径清单进入 summaries，`check` 重新枚举后拒绝源集合变化。此处只要求模块非空，不实现每个普通源文件与 class 一一对应，也不证明清除后鲜度。MapStruct 的独立期待集见下文，实际 agent／report 仍开放。

根配置结构子片由 `RuntimeDependencyPolicyTest.rootPomLimitsJacocoInstrumentationToPrepareAgentOnly` 读取真实根 POM，要求唯一 JaCoCo plugin／prepare-agent execution 的直接 goals 精确仅为 prepare-agent，配置只包含精确 namespace include，global／report 无覆盖配置；同一 DOM 函数的内存 XML 正反例拒绝 include 删除、缩窄、移到 global/report、excludes／skip，以及 prepare-agent＋report 混合 execution（七种 mutation）。根 `pom.xml` include 仅落在 prepare-agent execution，原 report 与 Surefire 双 agent 不改。此传感器仅证明根结构，不证明 child/profile/pluginManagement 的有效继承、实际 fork／exec／report 或 generated 鲜度；这些仍属完整设计后续义务。

child 普通声明子片通过同一 root＋child DOM 组合检查，拒绝 child 直接 `build/plugins` 的任何 JaCoCo 重声明；合法无声明和无关 plugin 通过。`childJacocoConfigurationFixturesRejectOverrides` 保护真实 root＋child skip 覆盖负例，根七种 mutation 保留；`reactorChildPomsDoNotOverrideJacocoAgentAndReport` 从默认 modules 逐项递归，检查 canonical containment／无 symlink／重复及有限字面 module 路径，不递归扫描所有 POM，不硬编码52替代遍历。该真实路径正例不代替路径校验各拒绝分支的专门 fixture，也不证明完整 effective 模型；属性间接覆盖、Surefire／processor／额外 roots 等后续义务仍未关闭。

managed／profile 有限子片同样复用组合 DOM：root＋child 每个 POM 的 `build/pluginManagement/plugins` 可无 JaCoCo，或严格唯一 `org.jacoco:jacoco-maven-plugin:0.8.13` 的 G:A:version-only 声明，三字段各恰一次；拒绝 configuration（skip／report include）、executions、dependencies／extensions／inherited／未知字段、重复声明／字段、缺失／不同／动态版本。不解析 `${jacoco.version}`。`managedJacocoConfigurationFixturesRejectOverrides` 在 root／child 各有无声明、version-only、无关插件正例及17种 mutation。`projectProfileFixturesRequireEmptyEntries` 在 root／child 验证无／空 profiles 合法，拒绝任意项目 profile entry，包括无 activation 的无关 profile、仅版本、JaCoCo skip 与 managed 声明；这不表示 settings profiles 为空。真实默认 modules 传感器检查同一合同，不替代 effective/settings/invocation 来源、完整属性、实际双 agent、generated／class／同轮鲜度与 report 证明。

字面身份／属性子片在分类前收集 root／child 所有直接普通及 managed plugin：artifactId 恰一次、groupId 至多一次（省略按 Maven 默认 group），纯文本有限字面 `[A-Za-z0-9_.-]+`；表达式、重复、嵌套或非法字符拒绝，其他插件的合法 version 表达式不受影响。`pluginCoordinateFixturesRejectDynamicIdentity` 保护表达式 JaCoCo skip 遗漏反例及四位置正反例；managed G/A/version 三项嵌套 mutation 已补齐（共17种）。coverage 专用 `coverageScalar` 不改变通用 `childText`。

`jacocoPropertyFixturesRejectIndirectOverrides` 收集全部直接 properties 中的 jacoco.*：根仅唯一纯文本 jacoco.version=0.8.13，child 不得重声明，任何其他 jacoco.* 均拒绝。根普通 JaCoCo version 必须唯一纯文本 `${jacoco.version}`；managed 仍仅字面0.8.13。该固定字段绑定不做通用插值；无关 property、其他插件版本与 Surefire 原模板不改。正反例覆盖 child skip 实际遗漏、双容器重写、缺失／重复／嵌套／不同／动态版本。settings／invocation 属性、argLine 与其他跳过参数、完整 effective 模型和实际 agent／generated／class／鲜度／report 仍待后片，不能由本片宣布 coverage 完整。

Surefire 受保护静态槽由 `surefireArgLineFixturesRejectOverrides`／`surefirePropertyFixturesRejectTestBypass` 及真实默认 modules 组合函数检查。根 managed Surefire 必须唯一声明、唯一 configuration、唯一纯文本 argLine，trim 边缘后与既有完整模板逐字符相等；保留 `@{argLine}`、`${settings.localRepository}` 和 `${mockito.version}`，不以 contains 放行。其他普通／managed 声明及所有 execution configuration 不得覆盖 argLine；所有直接容器均参与。各 Surefire plugin／execution configuration 禁止 skip／skipTests／skipExec／maven.test.skip／forkCount／forkMode，即使值为 false，也不做表达式或数值求值。architecture 既有 additionalClasspathElements 以及其他未受保护配置继续允许。所有受检 Surefire plugin／execution configuration 显式拒绝 `combine.self`／`combine.children` 属性，不按值放行，避免无 argLine 声明也能丢弃继承模板；root 模板 owner 同样受限。fixture 包含真实 classpath＋combine.self=override 遗漏 RED，以及 root／child 普通／managed／execution、owner 位置与 override／append／空值 mutation。此有限拒绝不实现通用 Maven 合并器。

全部项目 properties 容器拒绝确切 argLine、maven.test.skip、maven.test.skip.exec、skipTests、skipExec、forkCount、forkMode 及 surefire.*；普通版本与 Mockito 属性不受此规则禁止。正反例覆盖完整模板修改、重复／嵌套 argLine、第二 managed／configuration 容器、root／child 普通／managed 及 execution 受保护槽、第二 properties 容器。这里只保护列明静态槽，不是完整 Surefire 白名单；settings／invocation／外部属性来源、Mockito 版本与 JAR 来源、实际 fork 双 agent 仍需后续模型与 smoke，不能以静态模板替代。effective 模型 reader 的 Architecture 额外 classpath 另按所属 POM 固定为有序 Admin/Audit 两个 `target/classes`，plugin 与默认 test execution 两层均比较；`scripts/test-reactor-evidence.sh` 的独立正例及每层旧值/缺项/额外/反序/重复负例保护该有限合同。所属 POM 合法变化时须同步设计、reader 和独立 fixture，不从实际输出自动学习白名单。

compiler processor 固定输入由 `compilerProcessorFixturesRejectOverrides`／`compilerPropertyFixturesRejectIndirectOverrides` 与真实默认 modules 组合函数检查。根 managed 默认 Maven group 的 compiler 必须唯一 owner／configuration，直接字段精确为 release=`${maven.compiler.release}`、parameters=`true`、annotationProcessorPaths。paths 精确包含 `org.projectlombok:lombok:${lombok.version}` 与 `org.mapstruct:mapstruct-processor:${mapstruct.version}`，允许交换顺序；各 path 仅唯一纯文本 groupId／artifactId／version。configuration、release／parameters、paths、path 及坐标元素均拒绝 `combine.self`／`combine.children`，不按值豁免。非 owner 的 root 普通及 child 普通／managed compiler 可无配置声明，但 plugin configuration 与所有 execution configuration 均拒绝，owner execution 也不例外。

全部直接 properties 容器参与：根唯一纯文本 `maven.compiler.release=21`、`lombok.version=1.18.42`、`mapstruct.version=1.5.5.Final`；child 不得重声明这三个 key，其他 `maven.compiler.*` 拒绝，无关业务属性仍合法。正反例覆盖实际 child 第三方 processor 遗漏、child MapStruct 版本重写、各声明位置、缺失／重复／嵌套／不同／动态字段、未知 compiler 配置与合并控制。Lombok 是合法 processor，不要求其单独生成 Java 文件。此片不证明 execution phase／goal 全合同、source roots／output、完整 effective 模型、generated／class 所有权及鲜度，实际 fork 双 agent 与 report 仍须独立证据，不能以固定静态输入替代。

有效模型 reader 片目前是 inert 纯输入边界：`ReactorEvidence#read_effective_model` 只消费调用方提供的有效 POM XML、独立 raw GAV／canonical POM／basedir／packaging／modules oracle 与已审 invocation/source context，返回有限语义摘要或拒绝；不采集 Maven、不写 manifest、不接 `prepare`／`validate`／`check`，不清理既有 XML／JAR，也不证明本轮 full。合同一次覆盖 `<projects>` wrapper、独立完整集合、GAV／canonical path／packaging／modules、默认 source/test/output、Lombok＋MapStruct processor、字段化晚绑定、JaCoCo／Surefire/compiler、有限 lifecycle phase／goal、CLI Shade 的 Manifest-only／provided scope／三个签名资源过滤／Boot plugin dependency、UI `exec` 命令与目录以及无 execution 的 load-test `exec`。未知 wrapper、身份、路径、processor、goal／phase、配置或 invocation/source context 失败关闭；不实现通用 Maven resolver；小 fixture 调用时显式 `contract: :local`，`contract: :formal` 绑定reader 实现与测试各自独立冻结的受审 canonical 52-entry GAV→canonical POM→basedir identity set；fixture 不引用 reader 常量；formal 的 52 个 effective XML project 也由独立冻结输入维护，不得由 oracle、reader 常量或历史 XML `.map` 生成，并以独立 GAV/path/module 断言防止 oracle/XML 同步漂移；逐项比较集合与排序，不能以 local 小 oracle 或仅数 52 替代设计附录 D 的正式集合。现有 self-test 仅使用 `$CLAUDE_JOB_DIR/tmp` 专属临时根，当前片不启动 Maven、不新增 manifest、不调用 prepare，来源 bootstrap／真实模型采集／正式绑定与 reader 通过后才 clear 仍属后续片。当前后续片仅增加 inert `bind_model_provenance`／`read_bound_effective_model`：只校验未受信 caller 描述的结构及内容绑定：cwd/canonical root 必须为当前 root，dotMvn 必须为 root/.mvn；实际 verify argv 为 `mvn -q verify`，resolved launcher 单独记录；消费端重验完整 schema、输入键和 SHA 格式、重算 binding digest，并比对 XML bytes SHA 与 effective-pom output path。它仅拒绝描述中的额外 argv/MAVEN_ARGS，不证明宿主 settings、profile、extensions 或真实 launcher 来源可信。它不启动 Maven、不采集宿主配置、不接 prepare/verify/check、不清理旧输出，真实命令采集与 manifest wiring 仍未闭合。

## 五个子系统

im-chat 的 Harness 直接使用现有仓库事实源和执行入口：

| 子系统 | 当前事实源或入口 | 用途 |
|---|---|---|
| Instruction | 最近的 `AGENTS.md`、根与适用的局部 `ARCHITECTURE.md`、设计/产品规格的索引与状态标记、工程规范和模块 `README.md` | 按任务路由执行规则、跨模块与模块内部边界、当前批准行为及所属模块的统一语言和不变量 |
| Tools | Maven、`scripts/verify.sh`、`im-test`、`scripts/check-*.sh` 和 Harness fixture | 将可稳定判断的约束变成可重复执行、可诊断的检查 |
| Environment | `verify.sh readiness`、根及模块 `README.md`、POM/lockfile、应用配置和类型化配置属性 | 说明并验证版本、服务、配置和外部依赖，使本地检查与真实运行环境的边界明确 |
| State | `PROGRESS.md`、Git 工作树、设计文档、`docs/exec-plans/active` 和 `completed` | PROGRESS 提供全仓索引，计划保存详细任务、证据、阻塞项和下一步，使跨会话工作可恢复 |
| Feedback | 单元/行为测试、ArchUnit、漂移检查、指标、日志、`.harness/report.json` 和 Harness 反馈台账 | 用分层信号确认行为、定位失败并持续校正规则的精度与成本 |

这些入口共同构成一个 Harness，不另建并行的 `docs/harness` 工作区或第二套事实源。Instruction 路由任务上下文，Tools 执行约束，Environment 明确运行前提，State 保持工作可恢复，Feedback 提供验证与治理信号。

正常运行资源的关闭反馈应区分「清理尝试完成」与「全部物理终止」：单项等待失败不能无意截断仍由 native owner 持有的后续清理。Bolt 的同步延后失败契约由[生命周期设计](../design-docs/2026-10-26-bolt-client-serialized-lifecycle-design.md)拥有，真实 TLS 中断/处理器回归及自动化限制登记在规则矩阵；不以终态枚举或异常被捕获代替资源清理证据。

## 反馈层级

根据问题性质选择最窄、最快且可靠的传感器：

```text
明确语法反模式 -> drift/style checker
稳定依赖边界   -> ArchUnit
业务规则       -> unit/behavior test
编译与集成     -> Maven module test
运行保障       -> metrics/logs/health
无法准确机械判断 -> Review checklist
```

快速门禁不连接 MySQL、Redis、Nacos 或外部网络。涉及真实运行环境的验证应放到集成测试、部署检查或运行监控，不伪装成本地静态规则。

GitHub Actions 的 Verify 与 Harness GC job 在调用 `readiness`、`clean`、`report` 或 `harness-gc.sh` 前必须安装 `ripgrep`。缺少 `rg` 属于工具环境失败，不能把随后缺失的计划匹配误报为 `PROGRESS.md` 状态错误；报告入口在工具缺失时直接失败并说明安装方式。

## 第三方构建输入证据

第三方 recipe 的审核区分原始声明、descriptor 有效模型、物化输入与实际解析/执行上下文；任何一层不自动替代另一层。先按 parent/dependency/dependencyManagement/plugin dependency 角色判断传播，再核验有效值，不能把外部库自建插件当消费时执行，或用缺少对应 JAR 证明未解析。JRaft 私有制品已随官方依赖切换彻底退役；相关 capture、input review、verify-lock、消费者与采用结论仅保留在执行历史中，不再作为当前自动传感器或采用依据。外部模型参与边界为 Review-only，自动化须具备真实 Maven descriptor/profile/管理覆盖的可靠正反 fixture，不引入通用 resolver 或字符串豁免。第三方输入审核通则及其 Review-only 自动化条件保持不变。

## 固定收口批次与阶段冻结

需要构建私有第三方制品的任务采用固定批次（普通跨模块任务不增加 capture/verify-lock）：实现/稳定文档冻结 → capture 双轮 → 一次性 input review/精确锁 → verify-lock 双轮 → 消费者/单实现 → 一次 clean/full → 独立整体 Review 与状态归档。capture、input review、verify-lock、full 各自拥有独立输入和退出条件，前一阶段的通过、Accept 或历史 freshness 不能冒充后一阶段；同一冻结输入不得无新依据重复辅助取证或重复 input review。full 已产生的 architecture/startup/behavior/E2E/HA 证据不再独立重跑；实现、依赖、共享规则、稳定文档或输入变化会重新打开受影响阶段。失败先回到最窄层归因，重跑须有修复或前提变化，不因等待时间扩大范围，也不降低门禁。

## 有界验证与收尾窗口

Routing HA 在构建前执行有界 Engine `docker info` 前检（默认8秒），关键命令写入本次 `diagnostics/steps.jsonl` 的 started/finished 边界。主执行仍为900秒；到期后最多45秒仅终止和清理，不继续场景或计成功。logs默认5秒，失败也尝试down（默认30秒），down成功才清资源账本；原timeout124、INT130或TERM143优先于清理失败码。独立命令组以本次随机会话登记，原子发布、撤销竞态区分正常退出，未知存活/权限或历史未决登记失败关闭，不凭数字PGID取得旧进程终止权。`scripts/test-routing-ha-process.sh` 保护Engine阻塞不调用Maven、忽略TERM/孙进程/重复信号、有界回收与登记竞态；真实HA仍须另行证明。设计见[HA有界runner](../design-docs/2026-10-21-routing-ha-bounded-runner-design.md)。

旧 chain、attempt、archive-audit 与 journal 仅保留为历史证据；当前入口不消费这些记录来恢复、发布或晋级成功，不删除原失败和中断事实。

验证应减少定位成本，不以反复运行完整门禁代替排查。最终 `clean`、`full` 及风险适用证明仍按根 AGENTS 和所属计划执行；quick 仅用于迭代，本节不减少验收条件，也不改变现有 fingerprint/freshness 算法。

1. **先汇总范围，再扩大验证。** 同一 Review 轮次内的局部命名、注释和等价整理先用聚焦测试、编译或 token 等价检查反馈；范围内修改与文档定稿后，统一进入集成及最终门禁，不对每次小编辑反复启动全套。测试先行要求保持，不能将真实行为变化混成“纯整理”跳过回归。
2. **先安排独占窗口，再冻结输入。** 大门禁前确认没有其他写入者或针对同一构建输出的并发构建；明确本次变更、非目标及证据缺口，完成源码、稳定规范和说明文档编辑。运行期间不回填 tracked 状态文件；命令结束后集中记录结果。若工作树变化，证据按原规则判为无效，先协调写入者，不立刻循环重跑。
3. **失败先回到最窄层定位。** 保留首个失败命令、日志、具体测试/阶段和工作树信息，区分源码缺陷、构建产物、外部环境、验证工具及并发输入变化。先执行能验证假设的单测试、模块或只读诊断，不用另一次 `full` 同时重做所有前置阶段。构建产物问题须先核对源码与 class/报告路径；清理仅限已确认的可重建输出，不直接修改无关业务或删除其他会话资源。
4. **重跑要有新的依据。** 修复、环境恢复、独占窗口建立或最小失败层复核成功后，才恢复所属较宽门禁；记录重跑原因及预期消除的失败。同一原因再次出现且没有新证据或前提变化时停止，不能以反复执行碰到一次绿色作为解决，也不能用单独复跑成功覆盖原失败的稳定性风险。
5. **范围扩展与环境操作单独决策。** 新暴露的无关模块问题先归因并列为阻塞；超出当前任务权限时请求批准，不为收尾无限扩散变更。Docker 重启、全机代理等操作先记录运行资源、自动删除策略、挂载及恢复办法；不能只记录容器名就假定能够恢复。任务实现、后续设计和环境修复在计划中分开，不混成一份无法审查的提交范围。
6. **没有可执行下一步就停止并交接。** 长门禁启动前说明目的与预计覆盖范围；中途遇到新阻塞及时说明，不等多轮全套结束才反馈。若缺少权限、网络、稳定输入或失败定位证据，保留 blocked/Revise 与最小下一步，不标完成、不无限后台轮询。未通过的必要门禁仍阻止正式归档。

### MapStruct 生成期待集

`ReactorEvidence.generated_mapper_inventory` 从正式 raw POM oracle 的默认生产源码根提取 MapStruct 任务，不从旧生成目录反推。有限词法器跳过注释与 Java 字面量，接受显式 `org.mapstruct.Mapper` import 或全限定注解的顶层接口，以及 `imports`、`uses`、`unmappedTargetPolicy` 参数；拒绝通配／冲突绑定、嵌套或 abstract class mapper、config／实现命名／包定制及未知参数。每项要求默认同包 `InterfaceImpl.java` 与内部名一致的 class；生成源码须有唯一预期顶层 class 并直接 implements 原接口。源码／生成源码／class 的路径和摘要接入原 manifest，`check` 重新派生期待集。此合同只证明当前对应与存在性，不证明本轮生成、清除后鲜度、实际 agent 或 report；B3 仍未验收。

### 迭代入口与耗时控制

按本轮修改的责任选择入口，不把 quick 当作默认的单测试替代品。`quick` 实际包含 drift、全部父 Harness 和 architecture；focused 失败时先修复或归因，再扩大范围。

| 修改范围 | 优先反馈入口 | 边界 |
|---|---|---|
| 单个 Java 模块 | 所属模块的聚焦 Maven 测试 | 不替代跨模块与风险适用验收 |
| 混合 Java 工作树 | `./scripts/verify.sh affected` | 共享输入可能使范围保守升级，不能承诺始终便宜 |
| full 编排合成 fixture | `/bin/bash scripts/test-full-orchestration-harness.sh` | 与父 Harness 共用完整 setup→断言→cleanup，不运行真实 full |
| 其他 Harness fixture | 对应现有 `scripts/test-*-harness.sh` | 先核对该入口的环境及真实工具要求，不默认所有 fixture 均离线 |
| 跨边界稳定批次 | `quick` 或所属较宽验证 | 不对每个小编辑重复运行；quick 不是 full 的必需前置 |
| 最终验收 | 规定的 `clean` 与 `full` | 先定稿并冻结输入，不在运行中更新状态文档 |

模型 reader 比较有限合同的语义值，不把某种序列化写法当作业务不变量：effective-pom 可省略默认 packaging `jar`，仅此命名字段缺失时补默认，显式空/重复/错误值及独立 oracle 不匹配仍拒绝。`scripts/test-reactor-evidence.sh` 以缺省/显式等价正例和缺失 `pom`、空、重复、错误包装负例保护；不推广成通用缺字段容错。

耗时归因先使用既有命令起止、Maven 日志和 HA `steps.jsonl`，区分实际串行执行与同轮报告的派生记录；不可把 architecture/startup/behavior/e2e 共用 Maven 窗口的 duration 相加。尚无同输入阶段数据时，不承诺收益比例，也不新增跨轮缓存或调度框架。取消 fixture 的正常观察预算与应急超时分别记账，后者不是正常路径固定睡眠；分层优化必须保留阶段/信号覆盖、实际后代存活证据及异常回收语义。

CI 去重以新 full 的实际验收为前提：验收后再统一 `.github/workflows/verify.yml` 与 `scripts/test-harness.sh` 的合同及正反 fixture，移除 full job 外重复的独立 startup/E2E 执行，保留独立诊断入口及 full 自包含。当前尚未取得该真实验收，不先删除现行 CI 保护。迭代选择和有效重跑理由继续采用上述 Review-only 边界，不通过放宽检查器或删除测试降低耗时。

一次最终门禁窗口可以包含多个规定命令；“一次”是冻结输入后的一个验证批次，不代表失败后永不重跑，也不承诺每次只执行一个脚本。状态回填和归档之后需要刷新哪些证据，按 [计划生命周期](../PLANS.md#生命周期)及实际 review scope 判断；不能因为只改文档就把原工具认定 stale 的结果手工改成 fresh，也不能仅为状态措辞反复编辑、再全量重跑。

本流程目前采用 **Review-only**：现有记录能证明退出码、耗时和开始/结束指纹，尚不能机械判断某次重跑是否具有有效的新假设。只有具备结构化失败签名、原因分类、输入变化及恢复前提，并有必要重试、无效重复、纯状态更新和真正代码变化的正反 fixture 后，才考虑增加调度或提示工具；不设置未经依据的统一重试次数，也不新增绕过失败的自动降级。

## 完整验收与状态归档

### full 的同轮证明

`./scripts/verify.sh full` 当前顺序为 readiness/clean（含 drift）→父 Harness 自测一次→`mvn -q verify` 一次→同轮报告校验→真实 HA 一次。任一前置失败停止下游，保留原退出码；后处理失败不算 overall 成功。quick 不是最终 full 的必需前置；同一成功 full 不再附加重复 architecture/startup/behavior/e2e。独立入口保留，用于开发及失败窄层诊断。

`scripts/lib/harness-reactor-evidence.rb` 与 startup 共享同轮校验责任：Maven 前清除必需旧 XML 和两个应用 exec JAR，之后按当前源码/应用映射和 HA manifest 定位完整测试类及 selector，拒绝缺失、旧时间、零执行、失败、错误或必需 testcase 跳过；保留 startup 唯一映射/Nacos 隔离及 behavior/E2E tag 证明。校验成功为 architecture/startup/behavior/e2e 生成正常 verification 记录，明确 `evidenceSource=full-reactor`、报告/日志路径与摘要、实际执行窗口，而不伪称独立命令另跑过。

Architecture 中显式类级 `@EnabledIfSystemProperty` 条件测试单列为 `conditionalArchitecture`，仍要求本轮有效报告及摘要。整类跳过必须无失败/错误、计数一致且每项有原因，记录 `conditional-skipped`，不冒称已执行；激活后必须全部实际执行。类体还带条件、方法级或未知条件不自动获得整类跳过资格；其他必需组始终拒绝 skipped，不按类名加例外。

HA 内部同轮模式消费本次 manifest、XML/JAR 摘要及固定清单身份，消费前和最终发布前核对；不再重复 package 或聚焦 Maven。真实 Compose 拓扑、故障恢复、25 场景与空资源账本仍须实际证明。standalone HA 保留自行构建/聚焦测试能力。缺失或变化的同轮证明失败关闭，不启动容器冒充通过。

full 全程使用同一 HEAD/完整 Git 指纹冻结，源代码、依赖、共享检查器、稳定文档、设计及状态文件变化均不可忽略。报告清单和测试汇总是本次运行临时证明，不是 phase snapshot、chain 或跨运行缓存；没有恢复跳阶段、实现输入指纹例外或 archive 发布能力。保留原证据时间和 freshness。S1/S2 已完成聚焦验证，真实新流程 full 尚未验收，不把预计减少重复当作实测性能收益。

### 两时点状态关闭

先在冻结实现上取得真实完整验收和独立 `Accept`，才能标记全部任务 passing；首次验收及风险证明不减免。随后只允许 `PROGRESS.md`、active/completed 计划及这两个目录索引的状态内容迁移。独立 Reviewer 对照原验收/Review 记录和精确前后 diff，不仅检查路径；允许路径内改需求、范围、命令或实现事实也须重新验收。其他索引、README、设计、history 和规范在冻结前完成。

迁移后执行廉价 drift/clean、quality scope 校验、cleanup/handoff，不为纯状态归档重跑昂贵 full。旧 full 的时间、完整指纹、stale 和失败均保留，不改为 fresh、不生成假的新 full passed。既有 quality scope 排除保持；原独立 Accept 与 scope 必须适用。未解决失败、验收不成立或非状态差异须重新打开受影响任务，不得擦除错误来关闭。这不是当前外部环境健康证明。

本规则的内容范围与原验收归属为 **Review-only**；现有 drift 只检查路径/任务结构。自动化升级需可靠低成本的状态内容差异正反 fixture，且不重新引入 chain、journal、基线复制或发布状态机。详见[生命周期](../PLANS.md#生命周期)和[精简实施设计](../design-docs/2026-10-22-closeout-process-simplification-implementation-design.md)。

## 当前传感器

| 入口 | 责任 | 典型输出 |
|---|---|---|
| `im-test/im-architecture-test` | 模块依赖、分层、Facade/SDK 和插件边界 | ArchUnit findings |
| `scripts/check-java-style.sh` | 低误报 Java 结构规则 | 文件或包级诊断 |
| `scripts/check-sql.sh` | DDL、Mapper XML 和注解 SQL | SQL 来源及违规原因 |
| `scripts/check-drift.sh` | 聚合静态检查、文档链接和仓库漂移 | 全部违规组 |
| `scripts/test-*-harness.sh` | 检查器正例、反例和非回归 fixture | Harness 自测结果 |
| `scripts/verify.sh affected` | 根据变更范围选择模块 | 受影响模块验证 |
| `scripts/verify.sh quick` | drift、Harness 自测和架构检查 | 日常快速反馈 |
| `scripts/verify.sh behavior` | 路由、Gossip、投递和确认行为 | 跨模块行为证据 |
| `scripts/verify.sh e2e` | 真实 Broker HTTP/Bolt、Netty WebSocket 与受控 Account/Message 边界 | 实时黄金旅程、测试数量和可查询运行日志 |
| `scripts/verify.sh routing-ha` | 隔离的 3 Broker + 2 Gateway Compose 拓扑和 25 类路由高可用断言 | 拓扑一致性、逐节点故障恢复、场景到新鲜 JUnit 证据的映射及资源清理状态 |
| `scripts/verify.sh routing-capacity` | 显式生产候选拓扑的五阶段、三重复路由容量验证 | 30 条 workload 矩阵、有效发压、业务/资源指标、cleanup、普通数据与独立 scope/config freshness |

Broker 应用服务不得依赖 `infrastructure.*` 的 typed properties，也不为只复制一两个字段的用例新增 `*AppProperties` 投影。配置层把这些简单预算值或领域值传给应用服务，源配置仍由基础设施拥有；Domain 不依赖 Spring 或 infrastructure。`LayerBoundaryTest` 强制应用层边界，是否值得为重复投影增加静态检查由后续重复情况决定（Review-only）。
| `scripts/verify.sh load-test` | Node/Java 压测契约、Compose/runner/resource 与失败恢复 fixture | 确定性工具验证，不启动真实负载 |
| `scripts/verify.sh load-test-smoke` | 本机 full 上的 20 用户连接与 70/30 消息链路 | revision/scope/config/data/cleanup/round-bound 真实证据 |
| `scripts/verify.sh full` | 完整 Maven 与 Harness 门禁 | 交付前证据 |
| `scripts/verify.sh report` | 汇总最近验证和治理信号 | `.harness/report.json` |
| `scripts/verify.sh readiness` | Java/Maven/Node 与 Harness 工具就绪 | 版本或缺失工具的 WHAT/WHY/FIX |
| `scripts/verify.sh clean` | drift、脚本语法、diff、计划 WIP、恢复区和临时工件 | 可交接状态或聚合诊断 |
| `scripts/verify.sh handoff` | 当前 report、Git 改动、active plan 恢复状态和下一步命令 | 生成下一会话可直接读取的 JSON/Markdown 交接 |
| `scripts/verify.sh startup` / `init` | 全部 Spring Boot application 的 startup-smoke；环境、基础门禁和启动命令 | 发现新增 Server 漏测；不替代外部基础设施全栈启动 |
| `scripts/verify.sh cleanup [--apply]` | Harness 自有 tmp/PID allowlist、canonical path、provenance 及压测恢复现场 | 默认只读；apply 只清理明确安全目标，压测凭据/锁/中断批次只诊断并指向 runner 恢复 |
| `scripts/verify.sh quality` | AI Reviewer request/response、机器三维分数和硬性上限 | 生成模块 A/B/C/D 快照；request 为每个单元列出其 fingerprint 完整 Git 可见证据闭包，response 只能引用这些 allowlist 路径；缺 response 返回 `review_required` |
| `docs/exec-plans/TEMPLATE.md` | Sprint Contract、事实源、验证、任务状态、恢复和风险的统一骨架 | active plan 实例；模板本身不作为完成证据 |

脚本只负责它能准确识别的范围。规范中存在但矩阵标记为 Review 的规则，不得在文档中暗示已自动执行。

`routing-capacity` 将证据有效性与容量资格分开表达。`runtimeEvidence.routingCapacity.status=verified` 只表示矩阵、字段、cleanup、源码 scope 和配置 freshness 都可验证；`qualificationStatus=passed|failed|uncertain` 才表示候选拓扑的容量结论，`statusCounts` 保留对应轮次数。每个候选拓扑的成功运行归档在 `.harness/routing-capacity/evidence/<topologyId>`，报告的 `candidates` 同时展示 3/5 voter 证据，后一次运行不能覆盖前一拓扑。机器规格只能引用 `verified + passed` 的三次稳定证据，不能把完整但 `failed/uncertain` 的报告解释成通过。

Broker 代码/集成验收与生产候选容量验收分属不同执行计划。日常 `quick/full/routing-ha` 和候选环境短时演练均不能替代正式容量门禁；演练只验证六个 hook、故障注入、真实指标来源与清理。正式资格仍需独立容量计划中的 30 轮矩阵、完整原始证据和独立复评。拆分计划不修改 `routing-capacity` 的 fail-closed 规则或既有阈值。

## 失败归因

验证失败或工作停滞时，先定位失效的 Harness 子系统，再选择修正位置。归因用于改进上下文和反馈链路，不替代对代码缺陷本身的修复。可以在任务范围内直接修正陈旧事实；如果修正会改变已批准的产品意图、设计、Architecture 或其他权威来源，且当前任务没有该权限，必须先升级并取得批准。

| 归因 | 主要子系统 | 识别信号 | Harness 修正 |
|---|---|---|---|
| 任务规格 | Instruction | 目标、非目标或验收口径矛盾、缺失 | 在权限范围内澄清 Sprint Contract；涉及批准意图变化时先升级审批，再更新设计或产品规格 |
| 上下文/文档 | Instruction | 事实源冲突、术语陈旧、入口难以发现 | 更新范围内的所属模块 README、工程规范或文档指针；权威边界变化先审批 |
| 环境 | Environment | JDK、服务、配置或外部依赖与预期不符 | 修正文档化前置条件、本地默认值、类型化配置或部署检查 |
| 工具权限 | Tools | 必需命令因文件、网络、凭据或执行权限受阻 | 明确最小所需能力、批准路径和不依赖额外权限的替代检查；改进工具诊断 |
| 状态 | State | 工作树、当前任务、已完成证据或下一步不清楚 | 重新核对 Git 状态，并在活跃计划内更新跨会话恢复状态 |
| 验证反馈 | Feedback | 信号缺失、误报、偶发失败、诊断模糊或反馈过慢 | 调整反馈层级、传感器、诊断和正反/误报 fixture，并登记 Harness 反馈 |
| 范围控制 | State | 变更扩散到非目标文件或夹带无关清理 | 重新确认 Sprint Contract、所有权和非目标；范围或优先级变化先取得决定 |
| 架构边界 | Instruction / Tools | 依赖方向、数据所有权或公共契约与 Architecture 冲突 | 修复实现以恢复边界；若边界确需改变，先升级审批，再更新批准设计、Architecture 和对应传感器 |

## Broker 类型化 Raft 边界

`support.raft` 仅批准 codec/model/statemachine 三个精确包，不允许任意后代、Runtime/生命周期、具体网络 client、应用或入口依赖，domain 不能反向依赖。`LayerBoundaryTest` 共用规则检查生产类与正反 fixture；不按类名豁免。通过 Transformer 间接渗入业务编排为 Review-only，待可靠调用数据流分析后自动化。规范所有者为 Broker Architecture、MODULE_STRUCTURE_GUIDE 与 CODING_GUIDE，登记见规则矩阵。持久化版本有意重置 v1，不以“格式不变”归因；旧 v5 日志/v6 快照拒绝是预期，程序不删除数据目录。

业务 Peer 读取证明 RPC 的传输归属已按用户批准纠正为 BrokerPeerAdapter 直接复用通用 BoltInvoker，
原生选举/复制/快照仍归 RaftRuntime/JRaft；不为纯转发保留 Raft Peer Client。最窄传感器为
BrokerPeerAdapterTest 的协议/预算/身份/失败断言与 LayerBoundaryTest 的真实 BoltInvoker 负 fixture，
继续禁止 support.raft 网络依赖，不新增类名例外。职责的数据流判断仍为 Review-only，自动化条件见规则矩阵。

## Broker 自动观察反馈边界

观察来源必须是实际TLS连接派生且namespace内唯一URI，不能用payload、Subject/首URI或可信网络回退补票；窗口先选最新事实再判资格，内部Duration只在SDK边界按年龄ceil/失败floor保守舍入。规范所有者为Broker自动故障设计、Architecture、Security/Reliability；最窄传感器为BoltAuthenticatedTlsTest/FailureTest、BrokerObservationAdmission/IdentityTest、BrokerFailureObservationServiceTest及BrokerHealthObservationPrecisionTest。单调acceptance budget与实际完成闸门由BrokerAutomaticFailureObservationTest/FlowTest保护；真实TLS HA证据单独由routing-ha拥有，不能以恢复单测或plaintext容器可重启冒充自动触发。

实际完成闸门的最窄传感器补充为 `BrokerFailureObservationRoundTest.roundRemainsOccupiedUntilCrossThreadFailureAndCanThenRestart`、`BrokerAutomaticFailureObservationTest.aProbeStillRunningKeepsTargetGateAcrossContextReset`、`schedulerAndObservationShareRecoveryUntilUnderlyingQuorumActuallyCompletes`，分别证明跨线程失败后再入、Context 切换不释放底层在途探测、恢复入口共享实际完成边界。不能仅按出现 `whenComplete`、`finally` 或锁类名判定正确。

失败原因与时序副作用证据采用 **聚焦测试 + Review-only**，规范所有者为 UNIT_TEST_GUIDE「Assertions」「Determinism」。`BoltConnectionAuthenticationSpikeTest` 的无 gate 负对照证明 Handler 零次但请求已提前放行；暂停握手用例分别观察 attempted/forwarded，身份拒绝检查受控 cause 链与稳定原因。该证据只属于测试原型，不能单独证明当前受控实现；正式实现另由 BoltUnifiedInvokeTest、BoltProductionResourcesTest、BoltProductionBackgroundTest 和消费方启动/集成测试取证。只有结构化分析能够关联测试声称、异常包装和实际副作用位置，并具备必要拒绝、无关超时、提前发送及合法异步缓冲的正反 fixture 后，才升级通用自动检查；不按异常类名或测试类名增设白名单。

防御边界守卫采用 **聚焦测试 + Review-only**，规范正文由 CODING_GUIDE「校验与防御边界」拥有。合法聚合 Begin/Commit 的 revision 与状态迁移不变量由聚合测试固定；异步报告等待期间任期/Context 变化由观察 Round/State 测试保护，不把已通过入口校验等同于后续事实不变。只有能够关联构造/迁移契约和异步数据流、区分冗余守卫与必要重验，并有正反 fixture 后才自动化。

通用来源/时间数据流仍为Review-only：需要可靠关联传输握手、来源注入、报告构建、准入判定及在途Future实际完成，才能升级低误报通用checker；不新增类名白名单。技术观察窗口是具体 Tracker（support.health），不伪装领域Repository或建立无替换需求的port/实现对。闭合装配、SDK/Domain隔离继续由现有架构与启动测试验证，不放宽层规则。

## Broker 健康观察安全输入边界

Broker 自动健康观察另行批准精确 `support.health`（不允许后代包），当前具体实现为 `BrokerHealthObservationTracker`，允许 Tracker 后缀，不建 Repository、port/实现对或兼容壳。它只维护按当前目标裁剪的有界采样、结构化上下文、freshness 与快照；观察非权威，但属于安全决策输入，不是可选诊断。观察资格由聚合 `BrokerCluster.findObservers` 按已提交业务状态判定：存在非终态成员操作、`LEARNER_SYNCING` 节点或角色/端点绑定不一致时返回空集，否则返回同一 revision 的完整业务 `VOTER` 集合，不读取原生成员配置。领域服务 `BrokerFailureObservationService.evaluate` 判定最新事实、多数与稳定窗口；多数阈值始终为该完整集合大小的一半向下取整加一，不因失联、缺失响应或排除目标自身报告而缩小分母。该业务观察阈值不替代原生 JRaft quorum；应用层仍以同任期 ReadIndex 和 revision/epoch 前后重验确认权威性。允许依赖领域 model、common 和 JDK 时间/集合；禁止 application、interfaces、infrastructure、config/configuration、lifecycle、Adapter、领域 service/repository、Spring、client/network、plugin 及运行资源拥有，domain 不得反向依赖 support。装配归配置、入口归 Handler/Application；此例外不放宽 Gossip、Monitoring 或其他 support。精确包和稳定依赖由生产/fixture 共用架构规则保护；有界性、无决策及实际完成回调语义由聚焦测试和 Review 保护，待可靠资源/决策数据流分析后自动化。

`LayerBoundaryTest.brokerHealthSupportIsAnExactSafetyInputBoundary` 与 `healthSupportRulesAcceptSamplesAndRejectOwnershipLeaks` 共用 `brokerSupportPackages`/`brokerHealthSupportDependencies`/领域反向规则：生产精确包、合法 model/common/time/集合采样、14 个逐名依赖负例、后代包、领域反向依赖。Config、Configuration 与 Lifecycle 负例位于各自独立顶层路径，不依靠 infrastructure 重叠命中。有界性、旧上下文/断档/回退及不可变快照由 BrokerHealthObservationTrackerTest 保护；实际完成闸门由观察 App 用例测试保护，不将安全输入误当可丢弃诊断。规范所有者为 CODING_GUIDE、MODULE_STRUCTURE_GUIDE 与 Broker Architecture，登记见规则矩阵。

成员容器与采样上下文语义采用 **聚焦行为测试 + Review-only**：Broker `ConsensusMembership` 的不可变 Set 归一化同角色重复，构造时拒绝跨角色交叠；Raft 原始快照保留 List<String>，聚合只检查自身角色/绑定一致性。Context 结构化保存 revision 与仅含业务节点、角色及端点绑定的 BrokerClusterTopology，targets 从业务拓扑派生，使用值相等判断，不再依赖 generation/toString 或原生成员快照；Topology 的节点副本隔离、Map/Set 内容相等和冷却不含 revision 保持。ConsensusMembershipTest、BrokerHealthObservationContextTest、Tracker/Transformer及观察用例覆盖独立字段变化、集合排列不变、迟到读写拒绝、采样与冷却区别。业务状态异常读取/异步重验仍须失败关闭并释放闸门；原生成员配置差异、变化或读取失败不影响业务观察资格、采样窗口或冷却。原生成员读取仍供既有成员维护使用。规范所有者为 Broker Architecture 与共识成员边界设计；不新增按类名静态豁免，通用集合/状态身份数据流判断仍为 Review-only，待可关联契约语义、相等性与状态消费并具备正反 fixture 后再自动化。

## 有界非权威诊断辅助边界

有界、非权威且可选的跨层诊断记录辅助类型可进入明确批准的 `support.<capability>` 精确能力包；允许有界内存记录和累计计数，不参与业务决策，不拥有网络、运行资源或生命周期，不依赖 application、interfaces、infrastructure、配置、生命周期、Adapter 或具体网络 client。不得扩展为任意子包许可或按类名豁免，领域仍不得依赖 support。Broker 当前仅批准 `support.gossip` 精确包承担此职责；运行态 Gossip store/Adapter 仍归 `adapter.gossip`，不因此移入 support。 `LayerBoundaryTest` 已实施精确包许可、Gossip 辅助类型依赖禁令与 Domain 反向依赖禁令；`gossipSupportRulesAcceptOnlyApprovedPackagesAndRejectOwnershipLeaks` 以合法记录、11 个逐项依赖负例、后代包和 Domain 反向依赖 fixture 验证边界。有界性、非权威性及可选诊断语义暂为 Review-only；自动化条件为可靠识别业务决策/资源所有权的数据流，容量与诊断查询由 GossipSyncTrackerTest 保护。规范所有者为 CODING_GUIDE、MODULE_STRUCTURE_GUIDE 与 Broker Architecture，登记见规则矩阵。

Broker 的 `support.monitoring` 是另行批准的精确技术观察例外：保留 Spring `@Component`、Micrometer `MeterBinder` 和只读 Domain/Registry/Adapter 查询；不允许后代包，不依赖 application/interfaces/infrastructure/config/lifecycle，不直接拥有网络、RaftRuntime 或执行器，不进行业务决策或写入，domain 不反向依赖 support。不要将纯 support.gossip 的 Adapter/Spring 禁令套到 Monitoring，也不要因此放宽 Gossip 或其他 support。`LayerBoundaryTest` 共用参数化规则检查生产及正负 fixture，逐名验证十个依赖负例、后代包和 domain 反向依赖；合法 fixture 保留真实 Spring/Micrometer 与只读 Registry/Adapter 调用。MeterBinder 必须位于精确包，Transformer 只允许 application/interfaces/adapter/registry 四个能力包。BrokerMetricsTest、RoutingAuthorityMetricsTest 与各 Transformer 测试保护采样及映射不变。只读调用、业务决策/写入语义为 Review-only；自动化条件为低误报的明确写 API 清单或语义 checker。规范所有者为 MODULE_STRUCTURE_GUIDE 与 Broker Architecture，登记见规则矩阵。

## 规则矩阵

规则登记、规范所有者、自动检查与 Review 边界见[规则矩阵](HARNESS_RULE_MATRIX.md)。本文件只维护 Harness 运行流程和规则生命周期，不复制规则正文。

## 从问题到规则

实现或 Review 中确认了可复用的工程约定时，实现者必须在同一任务完成 Harness 反馈评估，不能只修正当前代码。评估至少回答：规则由哪份规范所有、是否代表可复用边界、最窄可靠传感器是什么、仓库基线是否已经满足，以及需要哪些诊断和 fixture。评估结果只能是以下三类之一：

1. **Enforced**：可以低误报机械判断，当前任务同步规范、矩阵、检查器和 fixture。
2. **Review-only**：规则有效但不适合机械判断，或仓库基线尚未收敛；同步规范和矩阵，明确当前由 Review 负责及转为自动检查的条件。
3. **Not a Harness rule**：仅是局部命名选择或一次性实现细节，不形成共享规则；无需增加仓库约束。

不得因为当前任务范围较小而静默跳过评估，也不得为了立即自动化而增加脆弱的类名白名单或扩大误报范围。

边界包装类型规则当前处于 **Observed**：Facade、CQRS、HTTP/RPC、事件和跨层快照仍有历史基本类型，且基本类型在内部计算、计数和谓词返回中是合理表达。现阶段由 Review 检查新增或触碰的边界模型是否会用 `0`、`false` 掩盖缺失输入。只有相关历史基线收敛，且结构化检查能够区分边界模型与内部确定值，并具备必填包装值校验的正反 fixture 后，才升级为 Enforced；不得通过目录白名单强行启用。

CQRS 边界值规则当前处于 **Observed**：历史 Command/Query 仍有直接使用业务枚举和值对象的情况，而 `im-common` 中稳定通用边界值对象可以合法复用。现阶段 Review 检查新增或触碰的 CQRS 输入是否把聚合、业务值对象或 Repository 查询条件泄漏到应用边界。只有能够区分业务领域类型与允许的通用边界类型，并完成历史基线收敛及正反 fixture 后，才升级为 ArchUnit 或结构化检查。

框架回调用例规则采用 **聚焦结构/行为测试 + Review-only**：IAM OAuth 边界测试证明 Spring Authorization Server 的 `save` 回调只提交已经完成协议处理的最终授权状态，统一转换为 `OAuthAuthorizationSaveCmd`，不再执行第二遍授权码兑换或 Refresh Token 刷新。T7 批准设计进一步要求快照的原读版本经不可变内部属性传入保存命令，锁内核对后再修改，不能重新加载最新版本替旧快照补齐前提；仅 requested／current 均为 REVOKED 时允许只读终态幂等。版本传递由 boundary／adapter 聚焦测试验证，并发单赢家、撤销两种锁顺序、回滚及重复撤销由真实 MySQL 用例验证；opt-in 跳过不算该项验收。通用版本来源是否被 rebase 的语义目前由 Review 核对，只有能可靠追踪快照读取、转换和条件写入的数据流并有正反 fixture 后才升级自动检查。Review 先判断回调表达的是最终状态持久化还是要求应用执行业务动作，再决定使用统一保存或独立 Command；仅按 `type`、`save`、`handle` 或空字段扫描无法作出该判断，只有结构化分析能够关联框架回调契约和应用方法后才升级为通用门禁。

应用服务入口转换规则采用 **聚焦行为测试 + Review-only**：Review 检查公开用例是否先将 Command/Query 的基础边界值统一构造成领域值对象和内部查询条件，再执行 Repository 查询与业务编排；后续代码不应同时传播同义的原始值和领域值。聚焦测试应证明无效边界值在访问 Repository 前即由领域构造入口拒绝。通用静态扫描无法可靠判断 `String`、数值和集合是否具有领域语义，也无法区分合法的输出转换与遗漏的输入转换；只有结构化分析能够关联 CQRS 字段、领域构造入口和后续调用数据流，并覆盖可选值、通用 `Paging`/`TimeRange` 与纯技术参数等正反 fixture 后，才升级为自动门禁。

信任来源规则采用 **聚焦行为测试 + Review-only** 边界：Audit SDK 契约测试证明生产者可控载荷不包含 `sourceApp`，Ingestion Transformer 测试证明 HTTP 认证身份或 Kafka Binding 注入的来源会进入扁平 Event 和领域事实。Review 检查新增多租户、身份、来源等信任字段是否仍可被请求体、消息体或多个同义边界字段覆盖。通用检查需要关联认证边界、传输配置与 Event 构建，仅按 `sourceApp` 名称扫描会误伤领域事实和查询条件；只有结构化分析能稳定区分可信注入与不可信载荷，并具备 HTTP、Kafka、领域模型和合法业务来源字段的正反 fixture 后，才升级为自动门禁。

声明式动态审计属性规则采用 **聚焦行为测试 + Review-only** 边界：SDK Aspect 测试证明 `targetId` SpEL 可以按方法声明的真实参数名解析不同参数语义，`@AuditAttribute` 可以采集普通参数、对象的一层属性和成功结果，`include = false` 可以排除明确不记录的字段，并证明属性读取、上下文及提交旁路异常不改变业务结果。Review 检查结果确定的应用用例是否复用 `@Audited`、`targetId` 是否引用真实参数名、是否只在需要采集的参数或字段上声明属性规则，以及应用服务是否重复手写审计成功/失败控制流。登录、Token、Session、协议监听和接收端点等场景可能合法显式提交或禁止递归审计，不能按 `try/catch`、类名或缺少注解机械拦截；只有结构化分析能够识别应用用例、显式事件场景、接收端点和注解属性，并具备跨场景正反 fixture 后，才升级为自动检查。

应用入口命名规则当前处于 **Observed**：直接位于 `application` 根包、向接口层暴露完整用例的服务应使用 `*AppService`，其公开方法继续受单输入对象和跨应用服务依赖规则约束；领域协作、技术客户端和内部通知处理器不因位于应用层附近就机械改名。Broker 与 Ops 的诊断查询入口已经分别收敛为 `BrokerDiagnosticAppService` 和 `OpsQueryAppService`；待其他应用入口基线完成收敛后，可增加仅检查 `application` 根包公开入口的正反 fixture，避免误伤应用层内部组件。

`AssertUtils` 分工规则当前为 **Review-only**：相同类可能同时校验外部方法参数和领域属性，单凭包名或方法名无法可靠判断应使用 `arg*` 还是 `domainProp*`。Review 检查边界对象是否显式拒绝无效输入、领域不变量是否使用领域属性语义，以及是否重复手写等价校验。只有结构化分析能够识别校验发生的语义位置并覆盖混合场景后，才升级为自动检查。

领域包深度规则当前为 **Review-only**：Review 检查只有一个领域能力的模块是否直接使用 `domain/model`、`domain/repository` 和按需存在的 `domain/service`，并在多个相对独立的领域能力实际出现后才增加 `domain/<capability>/...` 分组。目录数量、类名前缀和当前文件数量都不能可靠证明领域边界，因此不使用正则或包数量机械推断。只有仓库具备结构化领域所有权元数据，或其他能够稳定区分独立领域能力的信号，并覆盖单领域扁平结构、多领域分组结构及同名前缀误判等正反 fixture 后，才升级为自动检查。

Broker 已确认的 cluster、authority、connection 能力目录使用显式白名单检查：cluster/authority 允许 model、repository、service，connection 只允许 model、repository；repository 包内必须为接口。该检查验证当前批准的目录结构，不把“所有领域模块都必须这样分包”推广为机械规则；领域边界是否成立仍按上述 Review-only 标准判断。

同名领域概念隔离采用 **聚焦 Schema/行为测试 + Review-only**：Review 检查面向不同授权对象、生命周期或可信来源的角色、权限、会话等概念是否各自拥有领域模型、Repository 和持久化关系，并通过 `Iam*`、`Application*`、`OAuthSession*` 等稳定所有权前缀同步区分其直接 Repository、领域服务和边界模型；不能仅依赖类型枚举、固定业务 ID 或调用方约定隔离。IAM 的 Schema 测试明确证明内部角色与外部应用角色使用独立表，认证与角色管理测试证明 IAM 管理权限只读取内部角色链路，Session 转换测试证明 OAuth2 持久化字段不会替代领域会话身份。通用静态扫描无法仅凭 `Role`、`Permission`、`Session` 等类名判断两个概念是否属于同一边界，只有建立可声明的领域所有权元数据并能关联 Entity/TableName、Repository 与调用链后，才升级为自动门禁。

Transformer 方法命名采用 **Review-only**：规范由 CODING_GUIDE「Transformer 与 JSON」拥有，转换方法按目标模型 + From，跨边界同名类型用必要的 Sdk 标识区分；不增加兼容转发或为统一命名扩大无关清理。当前存量仍有 to/restore 命名，按触碰范围收敛；只有结构化分析可识别 Transformer 的转换方法、返回目标/集合元素和合法非映射辅助方法，并具备同名跨边界、生成映射及显式转换正反 fixture 后，才升级检查，不能仅匹配方法名前缀。

MapStruct 规则当前为 **Review-only**：Review 检查 `@Mapper` 是否至少保留一个由 MapStruct 生成的声明式映射，并优先让框架处理同名属性和枚举。只有检查器能够可靠解析接口方法、排除继承方法、注解辅助方法及含业务逻辑的显式转换，并具备生成映射、纯 `default` 接口和合法混合接口 fixture 后，才升级为自动检查。

HTTP 聚合查询请求规则当前为 **Review-only**：Review 检查参数较多且共同表达一个查询意图的接口是否使用 `*Request + @ModelAttribute`，并由接口 Transformer 一次性构造应用层 Query，而不是在 Controller 中逐字段转换枚举和值。固定参数数量无法证明对象是否形成稳定语义，少量技术参数也不应被强制包装。只有结构化检查能够识别 Controller 参数、Request 绑定和对应 Transformer 调用，并具备简单接口、多参数查询和合法特殊绑定的正反 fixture 后，才升级为自动检查。

时间与时区规则采用 **聚焦行为测试 + Review-only** 边界：`TimeRange` 单测证明闭区间不变量和持续时间，Audit 的应用 Transformer 与 Repository 测试证明领域查询条件使用该值对象；接口 Transformer 测试证明 epoch milliseconds 与 `Instant` 的双向转换，持久化测试证明 `TIMESTAMP(3)` 对应 Entity `Instant`，UI 和导出测试分别证明浏览器 IANA 时区展示与服务端显式时区格式化。全仓中两个时间字段可能表示区间，也可能是创建、失效等独立事实，`LocalDateTime` 也可合法表达本地日历语义和框架技术时间，因此不按字段数量、名称或类型增加全仓正则禁令。Review 检查新增或触碰的领域查询区间是否使用 `TimeRange`、绝对时间是否跨层保持 `Instant`、浏览器边界是否使用时间戳，以及人可读输出是否显式选择时区。只有能够结合契约所有权和字段语义稳定区分区间、独立时间事实、本地日历值及持久化技术字段，并具备跨模块正反 fixture 后，才升级为结构化检查。

时间字段类型与命名规则登记为 **Review-only**，规范正文由 `CODING_GUIDE.md`「时间与时区」拥有。Review 区分绝对时刻、持续时长及单调计时：内部绝对时刻为 `Instant xxxAt`，数字时间戳边界为 `xxxAtMs`；内部时长为 `Duration`，泛指有效时长优先 `validDuration`，具体 `leaseDuration` 等业务语义保留；数字时长明确 `Ms` 等单位；单调计时保留值对象和真实单位。接口转换测试应验证数值与 `Instant` 双向映射、毫秒精度和过期边界。现阶段不增加全仓正则禁令：仅凭后缀无法识别业务语义、第三方约定、已发布字段或合法 `Instant` 服务契约。自动化条件是结构化检查可关联字段语义、契约所有者、单位及 Transformer，并具备绝对时刻/时长/单调值/外部协议正反 fixture。首批迁移限定 Broker 及必要的直接协议调用方，不授权全仓批量改名。

流程梳理与方法提取顺序登记为 **Review-only**，规范正文由 `CODING_GUIDE.md`「Java 与可读性」拥有。重复反馈表明：提前拆成具名私有方法会隐藏执行顺序、状态变化和错误边界，制造流程已经清晰的错觉。Review 先展开调用链核对完整流程，再评价封装是否表达稳定职责或真实复用；不能以方法长度、命名或私有方法数量代替质量判断。当前 Broker 观察流程按用户指定范围内联，不据此禁止所有私有方法，也不授权全仓机械内联。只有结构化分析能够展示状态与副作用数据流、异常传播、异步实际完成和资源释放，并具备合理提取与过早封装的正反 fixture 后，才评估自动化；单纯行数或调用方计数不足以作为传感器。

时源注入必要性登记为 **Review-only**，规范正文由 `CODING_GUIDE.md`「应用服务与 CQRS」拥有。重复反馈来自应用服务仅为控制测试时间而持有 `Clock` 和 `Supplier<MonotonicTimestamp>`，且旧规范曾将时间分支等同于必须注入 `Clock`；本轮修正该冲突，不新增全仓类型禁令。Review 核对每个时源的生产用途、实际装配和调用方，区分生产替换/框架契约与测试专用 seam；时间来源简化不能删除准确阈值、时钟回拨、单调 freshness 和异步实际完成的行为断言。当前存量按触碰范围收敛，不授权全仓清理。只有结构化分析能够识别生产替换需求、框架时钟契约和测试专用依赖，并覆盖直接采样、同轮传值、命令冻结时间、合法注入及冗余注入的正反 fixture 后，才升级自动检查；若框架契约或所属规范改变，重新评估规则范围而非增加类名例外。登记见规则矩阵。

单调时间规则采用 **聚焦行为测试 + Review-only** 边界：Broker authority 的领域测试证明租约过期、fence 后准入拒绝和重启后的 fence 水位生效；Review 检查新增领域服务是否通过 `MonotonicTimestamp` 等值对象表达单调时间，而不是在领域服务构造器中暴露 `LongSupplier` 或混用 `Instant`/`Clock` 判断租约边界。应用服务确有生产用途而保留可控时间源时，应在进入领域前转换为明确的时间值对象；pending 请求、重试窗口或测试需要本身不构成注入理由。只有结构化检查能稳定区分领域服务、合法生产时源和普通墙上时间展示后，才升级为自动门禁。

数据库 JSON 格式规则当前为 **Review-only**：Review 检查数据库 JSON 列是否由 MyBatis TypeHandler 等持久化机制编解码，Entity 是否使用持久化原生属性承接数据，以及领域 Transformer 是否只做对象映射。`JsonUtils` 在 Bolt、Gossip、缓存和第三方协议适配中仍是合法用法，无法按包名可靠判断某次 JSON 转换是否对应数据库列。只有结构化分析能够关联 Entity 字段、TypeHandler、Transformer 映射并排除非持久化 JSON 场景，且具备正反 fixture 后，才升级为自动检查。

MyBatis JSON TypeHandler 采用 **插件统一装配 + 聚焦测试**：`im-datasource` 将应用 `ObjectMapper` 设置给 `JacksonTypeHandler`，保证 Java Time 与项目 Jackson 配置一致；`ImDatasourceAutoConfigurationTest` 使用包含 `Instant` 的 Claims 验证序列化。业务 Repository 不得为规避 TypeHandler 配置而手工改写时间或 JSON 结构。受限自动配置的 Repository 测试也须装配应用 Jackson 配置，不能依赖其他测试先设置静态 TypeHandler Mapper；IAM adapter 往返保存的真实 MySQL 基线覆盖包含 `Instant` 的 Claims。事务并发测试只在首写成功后释放后续参与者，等待期间还须暴露首 worker 的异常，并在失败时释放 gate；异常不能被泛化为锁等待超时。测试上下文与生产装配是否等价目前由 Review 核对，只有能可靠解析条件自动配置、静态初始化及测试顺序依赖并具备缺失／完整装配正反 fixture 后，才升级通用检查。

数据库 Entity 枚举与基类规则采用 **Enforced + Review-only** 边界：SQL checker 强制每个建表语句声明 `id/create_time/update_time/is_deleted/version`，Java style checker 拦截 MyBatis Entity 中以 `String` 声明的 `action/status/result/outcome/kind/type` 字段，以及同时重复声明公共字段却未继承 `BaseEntity` 的标准形状；`BaseEntityTest` 固定 `AUTO` 与 `@Version`。`PersistenceConcurrencyArchitectureTest` 固定 Message 更新含分片键、集合撤销递增 version；具体调用方是否需要检查返回值由 Repository 业务语义决定，公共 Service 不统一将 false 解释为异常。

MyBatis Entity 敏感输出规则采用 **聚焦测试 + Review-only**：IAM 持久化映射测试证明管理员密码、OAuth Client Secret 与 Token 摘要不会进入 Entity `toString()`。Review 检查新增认证材料是否使用 `@ToString.Exclude` 或不生成 `toString()`；不能仅按 `secret/password/token` 字段名全仓禁止，因为业务响应可能合法包含一次性密钥、Token 生命周期信息或脱敏展示值。只有结构化检查能限定 MyBatis Entity、识别 Lombok 展开结果并覆盖摘要、普通业务字段与合法边界响应后，才升级为静态门禁。

MyBatis Service 分层规则采用 **Enforced + Review-only** 边界：新增或触碰的 DDD 业务模块应通过 `infrastructure.mybatis.service` 取得表级 CRUD、Wrapper 和可复用查询能力，Repository 技术实现不直接注入或编排 Mapper。仅服务于一个领域 Repository 的查询语义由 Repository 使用 Service 组装，无需创建只为跨层透传的持久化 `*QueryCondition`；确需复用或自定义 SQL 时再下沉到 Service 或 Mapper/XML。Java style checker 已拦截标准 Repository 实现直接导入 Mapper；框架 SPI 是否承担入站入口职责则由 Review 和聚焦依赖测试判断，不能仅按 `infrastructure/security` 包名禁止 MyBatis，因为纯持久化适配器本身可以合法依赖 MyBatis Service。扩展自动检查前必须具备 Repository、入站框架 SPI、纯技术委托 SPI、合法 MyBatis Service 和自定义多表 SQL 的正反 fixture。

框架 SPI 入口规则采用 **聚焦依赖测试 + Review-only**：IAM 的 `OAuthAuthorizationServiceAdapterTest` 证明 Spring Authorization Server 的授权入口依赖 `OAuthAuthorizationAppService`，且不直接依赖领域 Repository 或 MyBatis 类型。Review 判断某个 SPI 是完整用例入口还是 `PasswordEncoder` 一类纯技术能力委托；仅凭实现接口名称或包路径无法稳定区分。只有建立明确的 SPI 角色清单并覆盖两类合法形态后，才升级为通用静态门禁。

MyBatis Mapper 扫描规则采用 **Enforced + Review-only** 边界：Java style checker 拦截扫描范围内 Mapper 已逐个使用 `@Mapper` 时仍存在的空 `@MapperScan` 配置类，并拦截 Repository 技术实现直接导入 Mapper。逐个注解与集中扫描都是合法方案，但同一扫描范围只保留一种；多数据源绑定、跨应用根包扫描或包含额外配置逻辑的场景仍由 Review 判断。公共 `im-datasource` 始终不得通过业务包名承担 Mapper 扫描。

横切关注点规则当前为 **Review-only**：Review 检查重复的前后置技术策略是否已有稳定注解能力，以及切面是否通过显式接口或注解契约取得上下文、是否保持异常与顺序语义。业务是否真的属于横切关注点无法仅靠方法形态可靠判断，因此不做全仓正则扫描；具体能力应通过注解覆盖清单、切面顺序和成功/失败行为测试建立窄门禁。只有能够按已登记注解识别遗漏、同时排除领域规则和一次性编排后，才考虑升级为通用结构检查。

全限定类名规则当前为 **Review-only**：源码正则无法可靠区分类型引用、字符串常量、Javadoc 示例和同名类型冲突。Review 检查新增或触碰的生产代码是否通过 import 保持表达式可读，并仅在同一文件确有同名类型冲突时保留局部全限定名。只有结构化 Java 解析器能识别类型引用并具备同名冲突、字符串和注释的正反 fixture 后，才升级为自动检查。

聚合删除与 Repository 生命周期规则当前为 **Review-only**：Review 检查聚合删除方法修改的状态是否确实由后续 `save` 持久化，以及逻辑删除场景是否仅执行领域资格校验后交给 Repository 删除。只有结构化分析能够关联同一用例中的聚合调用、Repository `save/remove` 调用和持久化映射，并具备软删除与状态保存两类正反 fixture 后，才升级为自动检查。

值对象、实体身份和领域命名规则采用 **Enforced + Review-only + 聚焦单测**：Java style checker 拦截 `Identification` 子类使用 `onlyExplicitlyIncluded = true` 定制业务 ID 专属相等性，允许 `@EqualsAndHashCode(callSuper = false)` 排除技术主键；Review 检查值对象的规范构造入口是否完整执行不可绕过的不变量、承担校验的 Builder 是否手写 `build()`、Builder 是否持有目标领域对象并直接赋值、持久化技术主键是否仅在 Repository 构建后回填、聚合字段是否适合参与相等性，以及类型名称是否表达领域事实而非内部格式化手段。具体值对象和聚合使用单元测试证明归一化、脱敏、容量、序列化、相等性、Builder 校验和主键回填行为；其他构造器、Builder、record 与持久化重建语义仍不做脆弱静态推断。只有引入能区分领域 Builder、Lombok 生成器、MapStruct 目标属性与 Repository 回填路径的 Java 结构化解析，并具备重建聚合、非领域 DTO 和纯便利 Builder 的正反 fixture 后，才升级为通用静态门禁。

技术主键、业务 ID、可识别编码和聚合基数同样采用 **Review-only + 聚焦单测**：Schema 测试可以证明目标表同时具有数据库自增 `id`、唯一业务 ID 以及关联表使用的业务列，Repository 测试可以证明 `pkId` 只在重建和新增持久化后回填；但通用静态 checker 无法可靠判断某个 `*_id` 的领域语义，也无法仅凭类名判断两个 OAuth Grant Type 是否应共享同一客户端聚合，因此不做字段名正则推断。只有建立结构化的 Entity/DDL 关系模型，并能从明确元数据识别聚合业务 ID 与外键语义后，才考虑升级为通用门禁。

Web 基础能力边界采用 **Enforced + Review-only**：ArchUnit 与 Maven 依赖测试阻止 `im-web` 依赖 Session、IAM 或运行时业务模块，并确认 Admin、Ops、IAM SDK 显式接入统一 Web 能力；聚焦测试证明请求上下文建立、传播、清理及 `HttpResult` 的 MVC/Security 行为。插件中的字符串路径是否属于业务 Controller、重复响应对象是否形成第二套协议、某个上下文属性是否包含业务或安全语义，需要结合路由和调用方判断，当前由 Review 负责。只有结构化检查能够解析 Controller/Security 映射及配置绑定，并具备 Actuator、Swagger、OAuth 回调等技术路径的 false-positive fixture 后，才升级这些语义规则；不得用业务前缀正则代替所有权判断。

Review finding 同时满足以下条件时，才适合转为自动门禁：

1. 代表真实缺陷或稳定架构边界，而不是个人偏好。
2. 能通过语法、依赖、测试或运行信号准确判断。
3. 当前仓库已满足规则，或例外范围和退出条件明确。
4. 失败信息能够指出问题位置、原因和修复方向。
5. 执行成本符合所在反馈层，不显著拖慢日常验证。

不能稳定机械判断的内容写入其所有者规范并由 Review 检查。例如领域服务尺度、Adapter 业务语义、构造器是否包含校验、方法是否过长，都不使用宽泛正则。

## Fixture 要求

每条脚本规则至少具备：

- 一个应失败的最小反例。
- 一个应通过的正常示例。
- 容易误判时增加一个 false-positive 非回归示例。
- 对应的稳定诊断文本断言。
- 独立临时目录，不读取本机服务或污染工作树。

解析多行 SQL、Java 注解或 XML 时，优先提取结构再判断。无法可靠重建的动态形式交给测试与 Review，不为了扩大覆盖牺牲信号质量。

## 规则生命周期

1. **Proposed**：记录问题证据、预期行为和潜在误报。
2. **Observed**：扫描仓库基线，确认影响范围和历史例外。
3. **Enforced**：增加检查、清晰诊断和正反 fixture。
4. **Monitored**：观察耗时、误报、偶发失败和绕过情况。
5. **Revised or Removed**：架构变化、信号价值降低或已有更强检查时，修改或删除规则及引用。

删除失效规则不是降低质量。持续产生噪声的检查会掩盖真实失败，必须连同 fixture、文档和例外一起调整。

## 维护责任

### 管理端 IAM 边界

独立 Ops CLI 的标准流承载公开命令交互，不沿用服务端禁止所有控制台输出的适用范围。`check-drift.sh` 仅允许根路径 `im-management/im-ops-cli/src/main/java/**` 的 `System.out/err`；不按类名、包名或任意同名模块放行。所有生产源的 `printStackTrace()` 和其他生产目录的标准流输出仍被拒绝，测试控制台规则不变。`test-harness.sh` 用两个标准流正例和五个负例保护该边界；CLI 无 Spring／Server 依赖继续由架构传感器保护。该许可只区分交互与诊断，不证明输出无秘密；授权 URL fallback 可用，授权码、verifier 和 Token 禁止输出，内容数据流由 Review 与 CLI 行为测试检查。只有具备低误报跨调用数据流分析及可靠正反 fixture 后才自动化；批准的独立 CLI 交互边界被新设计替代时撤销该目录许可，不扩大服务端排除范围。

CLI 的可信来源必须在相应边界保留：权限目录常量经薄组合注解同时约束 scope／authority；操作审计读取本次认证主体与可信 Introspection Client，不接受终端自报身份；Leader 跟随只允许精确发现的受支持端点，并保留同一操作语义。查询失败只说明结果未知，不能证明未提交。Ops 的 `OpsCliControllerTest`、`OpsCliAuditTest` 和真实 RestClient 的 `HttpBrokerManagementClientTest` 分别保护 HTTP 授权／投影、审计失败隔离和 Broker 查询协议；`im-e2e-test` 的 `OpsBrokerManagementContractE2ETest` 连接正式出站客户端与真实 Broker HTTP／Raft，保护成功 learner 加入、查询／幂等及五写 epoch 不匹配拒绝，外部设施和未启动 learner 的限制由模块 README 明示。IAM SDK／Server、Ops 和独立 CLI 变更经 `affected-modules.sh` 带入该 E2E 模块，`test-harness.sh` 的各拥有方独立路径 fixture 保护映射，避免混合工作树中其他路径掩盖漏选；映射不代表真实 IAM 登录旅程已通过，沿用现有 e2e tag 和入口；跨模块同类代码的数据流仍属 Review-only，自动化条件是出现能区分可信服务发现与任意提示、认证属性与自报 Header 的低误报正反 fixture，不增加类名白名单。

跨服务成员写的业务目标与管理 HTTP 接收者必须分离：首次只从正式可信发现中选首个受支持端点，不用目标身份伪造发现别名，不覆盖原 command／scope；无候选时零写，首次失败不自动换普通节点。仅具名 NOT_LEADER 保留至多一次可信 HTTP origin 跟随及发现重验。`BrokerClusterOperationAdapterTest` 保护首次接收选择与失败边界，既有 `OpsCliControllerTest` 保护可信单跳／不可信提示拒绝；真实 CLI 组合证据由 `OpsCliGovernanceE2ETest` 与活跃计划拥有。受支持不证明存活，单 voter 不证明 Raft PeerId 到 HTTP origin 映射正确。跨模块身份／参数来源的数据流仍为 Review-only；只有出现能可靠区分业务目标、发现身份与出站接收者的低成本正反 fixture 后才自动化，不新增类名例外。

| 规则 | 所有者 | 当前传感器 | 退出条件 |
|---|---|---|---|
| 独立 Ops CLI 仅依赖 JDK、Jackson 和受控的 `im-common` JSON 工具，不依赖 Server、Spring 或管理端 SDK 实现 | CLI authentication design | `ModuleBoundaryTest.opsCliDependsOnlyOnJdkJacksonAndCommonJsonContracts` 导入 CLI 实际字节码；`RuntimeDependencyPolicyTest` 精确登记 management 子模块并检查非 test POM 依赖仅为 `im-common` 与 Jackson，不把 CLI 纳入服务 Nacos 清单；CLI 使用 `JsonUtils` 的严格对象／树入口，`OperationRequestFileTest` 的重复／必填 epoch／null／尾随／类型正反例继续约束恢复安全。目录信任由 root→leaf owner／mode／真实目录及不安全祖先拒绝测试约束；仅常规 POSIX、当前 UID/root 可信，不保证 ACL/NFS 或同 UID 对手 | CLI 客户端技术栈或文件威胁边界经新设计批准替代 |
| `im-iam-sdk` 不依赖 `im-iam-server` 实现 | Architecture | Maven/ArchUnit 模块依赖检查 | IAM 模块边界被新协议替代 |
| Admin、Ops 不实现第二套管理员密码认证与安全 Session | IAM design | `RuntimeDependencyPolicyTest` 所有权包路径检查与 Review | 引入能识别认证语义且保持低误报的结构化检查 |
| OAuth `client_secret_basic` 在 Basic 编码前分别对 Client ID／Secret 做 UTF-8 表单编码，不通过收窄 Secret 字符集规避编码错误 | Security / IAM SDK README | `HttpIamIntrospectionClientTest#formEncodesOAuthClientCredentialsBeforeBasicBase64` 使用真实 RestClient 与 HTTP mock 校验含 `+` 的固定非敏感凭证；其他 OAuth 出站认证路径的协议一致性 Review-only | 认证方式替换；跨路径自动化需具备覆盖各正式出站客户端的低成本确定性 fixture |
| BFF 健康请求实时 Introspection，仅可用性错误使用不超过五分钟缓存；管理员 CLI 不得复用缓存或故障降级 | IAM reliability / Security | SDK 行为测试、Ops 完整双链集成测试 | 授权协议或失效模型变更 |
| 显式多安全链必须保留 BFF 兜底、精确 CLI matcher 和禁用 Servlet Filter 自动注册；可信身份字段必须按原始 JSON 类型校验；Actuator 放行由实际 BFF 配置所有者持有，SDK 默认链与 Ops 兜底必须显式委托该配置 | Security / IAM SDK README | `HttpIamIntrospectionClientTest`、`IamSecurityFilterTest`、`ImIamSdkAutoConfigurationTest`、真实 Tomcat 的 `OpsCliSecurityIntegrationTest`、`ObservabilityConfigurationTest` 的实际配置所有者与委托检查；其他应用新增组合链的完整性 Review-only | 具备能可靠识别自定义安全链及条件默认链退让的低成本正反 fixture |
| Token 加密、权限语义和撤销覆盖；授权状态变为撤销后，本地资源认证与远程 Introspection 都必须立即返回失效 | Security | OAuth2 授权持久化、撤销、本地 Introspector 与远程 Introspection 聚焦行为测试；跨存储数据流 Review-only | 能低误报跟踪跨存储数据流 |
| IAM Token 的 `appKey` 表达来源应用，`aud` 表达目标应用；Spring 默认 Introspection Provider 返回签发 Claims，资源服务按自身应用标识校验 `aud`，不使用自定义 Provider 重复标准协议 | IAM security design/Security | IAM Claims Customizer、SDK 标准响应映射与 Audience 拒绝测试；协议语义 Review-only | OAuth Token、资源受众或 Introspection 主体模型变更 |
| Spring 默认 Introspection 以未知 Token 类型查询授权时，仅将原始 Token 恢复到摘要匹配的凭据，避免已消费授权码遮蔽有效 Access Token | IAM security design/Security | `OAuthAuthorizationServiceAdapterTest` 浏览器授权回归测试；摘要匹配语义 Review-only | Spring Authorization Server 不再使用 `findByToken(token, null)` |
| OAuth JSON Claims 进入 Spring Authorization Server 时将 `iat/exp/nbf` 恢复为 `Instant`；数据库 Transformer 保留 JSON 边界值，不承担框架类型恢复 | IAM adapter/Security | `OAuthAuthorizationServiceAdapterTest` + 真实 Introspection 流程；其他标准 Claim 类型 Review-only | Claims 不再通过 JSON Map 持久化 |
| IAM SDK 配置以当前应用为所有者，Web、Catalog OAuth Client 与本地 Session 使用一个嵌套层级；权限目录同步不复用浏览器 BFF Client，机器客户端只允许 `CLIENT_CREDENTIALS` 和 `iam.catalog.write` | IAM configuration/security design | `IamPropertiesTest`、`ImIamSdkAutoConfigurationTest`、三个管理应用配置绑定测试与 `IamPermissionCatalogRegistrarTest`；跨模块配置语义 Review-only，IAM 中实际 Client 注册状态由 Readiness 与运维检查确认 | IAM SDK 接入模型或权限目录同步协议被替代 |
| SDK 与宿主自有的同类型基础设施 Bean 按稳定名称创建并显式限定注入，不因同时声明多个 `RestClient` 而跳过专用 Client 或注入错误实例 | Configuration/SDK | `ImIamSdkAutoConfigurationTest#createsDedicatedIamRestClientWhenApplicationDefinesAnotherRestClient`、`HttpBrokerManagementClientWiringTest` | SDK 或宿主不再通过 Spring Bean 提供多个专用 HTTP 客户端 |
| IAM、Admin、Audit、Ops 管理后台统一使用 Vue 3、Element Plus、Element Plus Icons、质量脚本和同名 Console Token；禁止浏览器原生 `confirm/alert`，且不得跨应用共享 UI 源码 | Management UI | `scripts/check-management-ui.sh` 与正反 Harness fixture；组件行为由各 UI 聚焦测试 | 管理后台技术栈或独立部署边界被替代 |
| 管理后台采用安静高密度布局、一个行内主操作、Drawer 编辑/详情、明确危险确认、可恢复数据状态和服务端筛选 | Management UI/UX | Drawer、表格、筛选和数据状态聚焦测试；视觉层级、操作优先级、文案与响应式构图 Review-only，并以临时桌面/移动截图验收 | 已批准新的 Management UI 设计 |
| 本地管理业务端口固定为 IAM `18040`、Audit `18041`、Admin `18042`、Ops `18043`，Issuer、OAuth 回调、Vite 代理和前端控制台链接默认值必须一致 | Observability/Sharding runtime design | 配置绑定测试、临时 OAuth Client 迁移 DDL、`ObservabilityConfigurationTest` 和 Management UI Harness | 本地管理端口设计再次被批准的新拓扑替代 |
| IAM 内部角色与应用角色保持两套关系边界；应用角色分配只替换指定应用的关系，权限或 Client 访问配置变更撤销受影响 OAuth 授权；关系资源库继续使用 `findRoles/replace`，不引入无业务收益的 Assignment 聚合 | IAM management authorization design | IAM 应用服务、OAuth Client 领域测试与 IAM Console API/component tests；关系范围和撤销语义保留 Review-only，直到出现跨模块重复实现 | 关系模型改为独立授权聚合或权限快照改为实时计算 |
| OAuth 最终授权状态统一通过 Repository `save` 持久化；查询 SPI 保持无副作用，Authorization Code 单次消费和 Refresh Token 轮换由 Spring 默认 Provider 管理；Repository 不暴露兑换、刷新或历史重放动作 | DDD model/Coding Guide | `IamTokenPersistenceTest`、Spring 默认 Provider 契约与 Repository 查询测试 | OAuth 持久化或 Spring Provider 规范被替代 |
| IAM SSO 绝对过期对当前请求立即失效；已知与未知管理员邮箱均执行等价 BCrypt 校验工作 | IAM security design/Security | SSO Filter 与管理员认证聚焦行为测试 | 会话装载顺序或密码验证实现变更 |

IAM 静态检查只识别管理应用中明确的密码认证和认证 Session Repository，不扫描普通业务
密码更新或一般 Redis Repository。跨应用权限隔离、密钥管理和 Token 是否进入日志属于数据流
语义，现阶段必须由 Review 与行为测试共同确认。

### 管理监控边界

| 规则 | 所有者 | 当前传感器 | 退出条件 |
|---|---|---|---|
| Ops 只读查询使用有界线程数和有界待执行任务容量，节点级超时与失败不得阻塞或丢弃其他健康节点结果 | Reliability | Ops 并发、超时、拒绝和部分失败聚焦行为测试；执行器队列形状 Review-only | 查询模型不再使用进程内并发扇出 |
| Ops 的 HTTP Response、应用 DTO 和 Broker 管理协议模型相互隔离，通过 Transformer 完成边界转换 | Coding/Architecture | 当前 Review-only；待 Ops DDD/CQRS 基线收敛后评估 ArchUnit 与结构化检查 | Ops 不再承担 HTTP 聚合边界 |

固定线程数不等于任务容量有界；使用无界队列的固定线程池在请求积压时仍可能耗尽内存。Review
必须同时检查线程上限、队列容量、拒绝策略、单节点超时和部分成功语义，不能只凭线程池类型判定符合要求。

### 集中管理审计边界

| 规则 | 所有者 | 当前传感器 | 退出条件 |
|---|---|---|---|
| 只有 `im-audit-server` 拥有管理审计 Repository/MyBatis 持久化 | Audit design/Architecture | `RuntimeDependencyPolicyTest` 按包职责和类型形状扫描生产源码 | 审计存储所有权迁移到新的独立边界 |
| Admin/IAM 只能依赖 `im-audit-sdk`，不能依赖 Audit Server 实现 | Architecture | Maven 依赖检查 + ArchUnit | SDK/Server 拆分被新公共协议替代 |
| Kafka 插件只提供 Binder 运行时，不定义业务 MQ SPI | Architecture | Maven 拓扑与源码存在性测试 | 项目不再使用 Spring Cloud Stream |
| 事务时序、敏感信息排除、失败隔离、来源校验、重试与幂等 | Security/Reliability | SDK/Server 聚焦行为测试 | 对应传输或一致性模型发生变更 |
| Audit HTTP 从 IAM 认证上下文取得来源 `appKey`，Token 仅存在于 HTTP Authorization Header；Kafka 来源由独立 Binding 固定注入 | Audit design/Security | Audit HTTP 认证与 Kafka Consumer 聚焦测试；载荷数据流 Review-only | 认证协议或可信来源模型变更 |
| Audit SDK/Server 与 IAM Server 不为已有 HTTP/Kafka 用例维护并行 Dubbo 入口 | Architecture/Audit design | `RuntimeDependencyPolicyTest` 检查三个模块不依赖 `im-dubbo`；接口形态 Review-only | 出现具有独立同步语义和真实调用方的管理域 RPC 用例 |

源码所有权扫描只检查管理应用中明确的审计 Repository 包和 MyBatis 审计类型形状，允许生产者
保留 `support.audit` 下的事件监听器、显式发布器和安全上下文适配。它不按旧类名建立例外清单，
也不尝试从源码文本推断是否泄露 Token、是否在提交后执行或是否发生递归；这些数据流与时序语义
由契约测试和 Review 负责。

通用 RPC 安全规则仍采用 **聚焦行为测试 + Review-only**：Filter 测试证明 Attachment 与
`SecurityContext` 的建立、失败关闭和 `finally` 清理。Review 检查新增 RPC Params、DTO、事件中
是否重复携带 Token、调用方身份或权限。Audit 与 IAM 当前没有 RPC 入口；若未来出现具有独立同步
语义和真实调用方的管理域 RPC 用例，必须先更新设计与本矩阵，不能仅复制已有 HTTP 用例。

- 修改模块的人同步更新模块 README、测试和相关局部规则。
- 修改根架构、脚本或 CI 的人评估全仓影响，并更新根级入口和共享规范。
- 实现或 Review 确认可复用工程约定时，实现者在同一任务更新规范、矩阵和适用传感器，或明确记录为 Review-only 及其自动化条件。
- 没有明确所有者、验证方式或使用场景的规则不进入 Harness。
- 不为让当前变更通过而删除测试、降低断言、关闭规则或扩大排除范围。

## 外部规范吸收审计

本仓库对照 `walkinglabs/learn-harness-engineering` commit `77e7a3e` 与 DTPet revision `e9fd719f41374a2c2e3525254c24bbf14c5cee7d`，按能力映射吸收规则，不复制目录模板。

2026-09-10 首次运行外部 `tools/audit-harness.sh` 得到 `18/70`、Critical `4/7`。补充 PROGRESS、版本 pin、readiness、clean、五子系统报告和诊断后，复跑结果为 `28/71`、Critical `6/7`。该分数不作为本仓库门禁：剩余 Critical “根目录依赖 lockfile”不能识别 Maven dependencyManagement 和四个 UI 各自的 package-lock，其他建议也把 Makefile、feature list 和固定模板设为唯一实现。下面继续按机制和真实落点评估，不为了提高分数复制不适用文件。

| 外部机制 | im-chat 落点 | 状态与边界 |
|---|---|---|
| 仓库作为事实源 | 根/局部 Architecture、模块 README、设计与产品规格、Git | 已应用；权威内容必须可从仓库恢复，不依赖聊天记忆 |
| 渐进披露 | 根 `AGENTS.md` 路由到局部 AGENTS、Architecture、README 和专题规范 | 已应用；入口保存触发条件，细节靠近所有者 |
| Instruction / Tools / Environment / State / Feedback | 本文五子系统表 | 已应用；不另建第二套 Harness 工作区 |
| 初始化与环境就绪 | `verify.sh readiness`、`verify.sh e2e`、AGENTS、README、POM/lockfile、类型化配置 | 部分应用；工具和版本检查、实时 runtime 启动及 Gateway 重启已执行，MySQL/Redis/Nacos 全栈启动检查尚未进入标准入口 |
| 状态恢复与 WIP | `PROGRESS.md`、`docs/exec-plans/active`、Sprint Contract、单 active task、恢复状态 | 已应用；PROGRESS 只做全仓索引，小型单文件维护不强制建计划 |
| 功能清单状态机 | `docs/product-specs/FEATURES.md`、active plan | 已应用于关键行为覆盖目录；catalog 保存行为与验证边界，不缓存某次运行结果，也不作为自动任务队列 |
| 外部化完成判定 | revision-bound verification、`passing` 证据、`quick`/`full` | 已应用；验证记录绑定 HEAD/worktree，旧证据自动标为 stale 或 unavailable |
| 端到端验证 | `verify.sh e2e`、`im-test/im-e2e-test` | 已应用于实时链路：真实 HTTP/WebSocket/Bolt/Broker runtime 与受控 Account/Message 边界；不等同于数据基础设施全栈 E2E |
| 过程与运行可观测性 | active plan、revision-bound verification、Harness report、E2E log、Metrics/Logs/Health | 部分应用；实时黄金 workload 已可重跑并关联日志，尚未统一采集应用 Metrics/Trace 或验证 MySQL/Redis/Nacos restart |
| Review finding 升级 | “从问题到规则”、Fixture 要求、规则生命周期、反馈台账 | 已应用；无法低误报判断的规则保持 Review-only |
| 清洁状态与交接 | AGENTS Definition of Done、`verify.sh clean`、`verify.sh full`、计划恢复区 | 部分应用；仓库状态和交接已检查，full 自包含 readiness/clean，实时 Gateway 重启已验证；全栈标准启动仍未覆盖 |
| 自动 Agent loop / 固定多 Agent 流程 | 无 | 暂不采用；当前任务需要人工确认，且用户可选择单 Agent 工作方式 |
| 有界根 `PROGRESS.md` | 全仓当前状态索引 | 已应用；只引用 active plan 和最近验证，不复制详细任务/证据 |
| 第二套模板目录 | 无 | 暂不采用；现有 design/exec-plan/feedback 已拥有相同职责 |

DTPet 的七类模板用于提示过程工件字段，但除实施计划外未形成稳定实例消费；其 drift 主要检查模板文件存在。IM Chat 因此只吸收唯一计划模板和证据化 Review 契约。启动、交接和清洁状态继续由可执行入口及 active plan 状态承担，避免“模板存在”被误认为能力已经验证。

`docs/product-specs/FEATURES.md` 已作为关键行为覆盖目录落地，但不作为自动任务调度队列。只有长期跨会话恢复成本持续上升、并发工作需要结构化依赖协调，或现有 active plan 无法表达任务图时，才评估自动 loop 或更强状态机；采用前必须定义唯一状态所有者、迁移路径和退出条件。

验证层按“静态/聚焦 -> 架构/行为 -> full -> 运行信号”逐步提高成本。前一层失败必须先修复或完成明确归因，不能用后一层偶然通过覆盖较早失败；真实环境检查不可用时，应记录证据缺口而不是伪造本地通过。

## 反馈与报告

[Harness Feedback](../feedback/HARNESS_FEEDBACK.md) 台账记录误报、偶发测试和性能问题，至少包含复现证据、所有者、退出条件和状态。开放问题进入每周治理检查；阻塞日常开发的误报应立即处理。

本机 Harness 状态统一写入 Git ignore 的 `.harness/`，而不是 Maven 管理的 `target/`。verification、report、handoff、quality response/snapshot、startup manifest 和 E2E log 需要跨 `mvn clean` 保留；full verification 同时保存测试汇总，模块原始 Surefire 报告仍保留在各模块 `target/`。新 clone 没有 `.harness/` 时属于可恢复的空状态：report 必须为 `incomplete`，quality 必须为 `review_required`；response 与当前 request/scope 不匹配时同样要求重新 Review，不能把缺失或陈旧证据解释为通过。

`.harness/report.json` 汇总：

- 当前 HEAD 与包含 tracked diff、非忽略 untracked 文件内容的 worktree fingerprint。
- `sensorCoverage` 形式的 Harness 传感器入口覆盖率；兼容字段 `score` 暂时保留，两者都不是质量评分。
- Instruction、Tools、Environment、State、Feedback 五个子系统的 `missing`、`present`、`verified`、`degraded` 或 `stale` 状态与证据。
- 外部规范中 implemented、equivalent、not_applicable、deferred 的适配结果。
- 最近各验证模式的状态、耗时、命令、revision、fingerprint 和 freshness。
- 最近一次新鲜完整验证的 Surefire 测试统计。
- active/stale plan 和开放技术债务数量。
- Harness Feedback 台账中的开放误报、偶发测试和性能问题。
- E2E workload、测试数、freshness 和 `.harness/runtime/e2e.log` 路径。
- Routing HA 的 3 Broker + 2 Gateway 拓扑、25 类场景数、cleanup 状态和 freshness；逐场景证据位于 `.harness/routing-ha/scenarios.jsonl`。
- 压测 smoke 与正式容量分别显示 status/freshness、batch、config digest、worktree 稳定性、清理、普通数据校验和逐轮三态结论；两类证据不互相替代。
- Routing capacity 使用独立 `routingCapacity` 证据，不复用历史单机 `loadTest.capacity`：固定一个候选拓扑，五个阶段各执行三次连接和消息 workload，共 30 条结果；任一阶段/重复缺失、发压无效、资源采样或报告字段缺失、cleanup/普通数据失败以及执行期间 scope/full fingerprint 漂移都 fail closed。

压测运行开始和结束都记录完整 worktree fingerprint；运行期间任何 Git-visible 内容变化都会使本轮失败。后续 freshness 另用 load-test scope：覆盖全部 tracked 与非忽略 untracked 文件，只排除 `PROGRESS.md` 和 `docs/exec-plans/active|completed/**` 执行状态。因此仅同步任务状态不会迫使容量复测，任何代码、配置、Harness、稳定规范或其他文档变化都会使旧证据陈旧。scope 不包含 HEAD SHA 本身，HEAD 仍作为审计字段单独保存。

真实连接轮在 k6 受控关闭后，通过 Broker `GET /management/connections?userId=...` 对本轮全部用户做有界查询；剩余路由属于 `service_failed`，管理查询不完整属于 `uncertain`。资源 NDJSON 与 k6 summary 由 `round-report.mjs` 合成为 `*-round.json`，消息轮同样以三态结论进入 smoke/formal evidence。普通数据校验使用三业务 Schema 的有序纯数据 dump，不包含 DDL、AUTO_INCREMENT 或 dump 时间；前后不一致直接使 smoke/formal evidence 失败。

`verified` 只表示对应入口在当前 HEAD/worktree 成功执行；`present` 表示入口存在但没有当前证据；`stale` 表示历史结果与当前代码状态不一致；`degraded` 表示当前执行失败。顶层 `incomplete` 表示尚无 full evidence，不能用 drift 成功冒充完成。无法计算 fingerprint 的升级前记录为 `unavailable`，不得支撑当前通过结论。

根 `PROGRESS.md` 只保存 active plan、当前任务、阻塞项和 report 入口，不复制验证状态、耗时或测试数。机器结果由 `.harness/report.json` 唯一拥有，否则更新 PROGRESS 本身会改变 worktree fingerprint，形成证据自引用。

`routing-ha` 是显式慢门禁，不进入 `quick`。它先在本轮运行所生成的 Surefire XML 中定位每个场景对应的确定性断言，再启动隔离 Nacos、Redis、三个持久化 Raft Broker 和两个 Gateway；跨进程部分检查三份权威状态一致、Gateway 单实例退出时另一实例继续健康，以及三个 Broker 逐个停止、恢复后重新收敛。应用层 fence、Lease、Lookup、快照、授权、Receipt 与幂等边界由同一轮聚焦测试证明，不把进程内断言误报成容器级故障。所有 Compose 资源以唯一 project 登记，失败保留日志但仍尝试清理；runner 返回后 ledger 非空、PID 残留、场景缺失或证据字段缺失都会 fail closed。

HA 场景映射前必须有界采集本轮 `diagnostics/compose.log`；采集失败不得发布场景，不能依赖历史文件或 EXIT 清理后才生成证据。EXIT 仍补采失败诊断并清理 owned 资源，保留原失败退出码。`test-routing-ha-harness.sh` 用全新目录证明日志先于映射，并用已有日志下的采集失败反例证明不会误用旧证据。

隔离 HA 的 Nacos 依赖健康条件同时检查 HTTP 8848 和命名 gRPC 9848；仅 HTTP 端口可连接时，Broker 可能在客户端仍为 `STARTING` 的状态尝试注册并启动失败。`test-routing-ha-harness.sh` 静态检查该条件，真实 HA 运行验证应用启动与故障恢复。

HA runner 的超时反例使用独立 1 秒预算验证 `124` 传播；成功、缺场景、子进程失败和资源残留 fixture 使用 15 秒启动预算，避免机器高负载时 Ruby/脚本启动超过 2 秒造成假阳性。fixture 预算与真实 HA 的 900 秒运行上限互不影响。

Gateway/Broker 重启后的就绪等待使用 `ROUTING_HA_RESTART_READY_TIMEOUT_SECONDS`，默认 180 秒，以覆盖本地受限资源下已观测到的约 100 秒冷启动；Harness 自测要求默认值至少 120 秒，并要求两类重启探针都使用同一预算。该参数只控制单节点恢复等待，不改变 900 秒场景总上限、健康判定、权威状态收敛断言或失败传播；fixture 继续使用短总超时，不为预期失败消耗真实拓扑预算。

这些传感器信号不等于源码质量评分。耗时用于发现反馈变慢，失败和台账用于定位噪声，不能通过减少测试或降低规则改善数字。源码质量评分必须使用 `QUALITY_MODEL.md` 定义的机器证据 + 独立 AI Reviewer 流程；任何没有当前 review scope fingerprint 的结果都不能作为交付等级。

Quality scope 使用 Git tracked 与非忽略 untracked 文件，不读取 ignored 运行日志。模块外的根构建、Harness、deploy 和稳定规范作为共享输入加入全部十二个 unit fingerprint；fixture 必须同时证明 ignored 文件不触发复评、共享输入变化会使旧 response 失效。

## 完成时的清洁状态

任务结束前必须形成可复查的清洁状态：

- 执行任务规定及风险适用的验证，记录准确命令和实际结果；其中无法运行的检查记录具体原因与证据缺口。
- 更新实际受影响的规范、模块文档和适用的执行计划状态；活跃计划保留当前任务、已完成证据、阻塞项和下一步。
- 删除或分类任务产生的临时、未跟踪调试工件；`target/` 等标准忽略的验证或构建输出可以保留。
- 已有范围外修改保持原样，并从暂存、提交和本任务归属的变更清单中排除。
- 最终回复按任务规模列出适用的验证证据、受影响的计划/文档状态和残余风险；小型维护可以简短，残余风险可以为 `none`。

## 维护节奏

- Pull Request：检查新增规则的必要性、精度、诊断、fixture 和执行成本。
- 每周 GC：检查陈旧计划、技术债务和 Harness 反馈。
- 架构或规范变化：同步修改矩阵、传感器和文档入口。
- 出现误报：先登记并增加复现 fixture，再收窄解析器或规则；不要全局绕过。

## 落地示例

Gossip TTL 测试曾通过 `Thread.sleep` 等待过期，结果依赖机器调度。该问题依次沉淀为：

```text
Review finding
  -> Unit Test Guide 禁止真实等待
  -> 可控时间回归测试
  -> `check-drift.sh` 扫描 `Thread.sleep`/`TimeUnit.sleep`
  -> `quick`/`full` 持续验证
```

这类链路是 Harness 的基本单位：规范解释原因，测试证明行为，门禁阻止稳定反模式再次出现，Review 处理无法机械判断的语义。

## 锁外副作用与持续拒绝状态

将close、通知等副作用移出锁时，锁内必须先提交持续有效的拒绝状态；副作用完成前不能撤销准入屏障。Bolt生命周期规则由统一invoke设计拥有，最窄传感器为生产 `BoltConnectionLifecycleTest#drainRejectsHeldNativeRequestUntilPhysicalCloseCompletes` 的零在途/最后完成两窗口回归。当前采用聚焦并发测试 + Review-only：静态检查无法仅凭close在锁外或存在draining集合判断状态转换正确性。自动化升级须可靠关联准入线性化、实际副作用完成与回调清理，并具备提前恢复准入、正确拒绝和重复关闭的正反fixture；不新增类名白名单或泛化检查框架。

### Bolt模式授权的事实来源

- owning specification：[统一invoke设计](../design-docs/2026-10-20-bolt-unified-invoke-design.md)“模式授权事实不可变”。观察/自动摘流许可由实际受控传输固定模式导出并保存在Adapter final字段，不从可重绑定业务options读取许可Boolean。
- 最窄检查：`BrokerDevelopmentObservationPermissionTest` 通过真实DEV Spring client装配后修改普通options，直接调用probe/report/reconcileFailures仍在任何恢复/集群读取前拒绝；既有Peer MTLS测试保持正向路径。
- 装配来源（实际client running、Invoker引用相同client、所有入口共享事实）为Review-only，尚无可靠低成本数据流checker；只有具备actual-vs-config mode、后续mutable污染及已有FAIL_BROKER恢复的正反fixture后才考虑自动结构检查，不能用类名白名单代替。

### Bolt 统一调用的分层证据

规则登记在[规则矩阵](HARNESS_RULE_MATRIX.md)，规范由统一 invoke 设计、im-bolt README、Security/Reliability 和 Coding Guide 拥有。`BoltInvocationBoundaryTest` 按包所有权/API 检查生产类，地址契约先于 owner 豁免检查；正反 fixture 使用同一 production predicate，不加类名例外。规则的机器证据与 source Review 分开登记：T5 已在批准的原 JRaft 制品恢复后实际编译，正常 focused 四方法 GREEN、all-rules-missing control 为 4 tests / 2 assertion failures / 0 errors / 0 skips，恢复原 bytes 后四方法 GREEN；architecture 与 quick 独立窗口各 79 tests / 0 failures / 0 errors / 2 既有 localMySQL skips（`/tmp/bolt-t5-wide-gates/`）。此前 29.70s 私有依赖失败保留为历史，不是当前未编译状态。这些 scoped 证据不替代 T6 full 或真实 MySQL 验证。

规则文件及正反 fixture 的五条真实路径均在 `im-test/im-architecture-test/src/test/java/`：

- `com/co/kc/imchat/architecture/BoltInvocationBoundaryTest.java`：实际规则与逐违规 API 断言。
- `com/co/kc/imchat/plugin/bolt/spi/fixture/AddressInvocationBypass.java`：owner 包中的地址契约反例。
- `com/co/kc/imchat/gateway/ws/handler/fixture/DirectBusinessRpcBypass.java`：实际 Bolt 业务包中的 native invoke、factory/codec 构造、transport option、raw/pipeline/unsafe 反例，含无 Bolt 依赖 helper。
- `com/co/kc/imchat/plugin/bolt/client/fixture/ControlledProtocolWrite.java`：受控协议 owner 正例。
- `com/co/kc/imchat/gateway/ws/server/handler/fixture/WebSocketProtocolWrite.java`：合法外部 WebSocket 写入正例。

该检查不证明运行期 all-origin raw 拦截。`BoltOutboundOriginsGateTest#explicitPreencodedContextWriteBypassesTypedGateWitness` 必须保留为旧 broad 主张的 COUNTEREVIDENCE；测试通过表示真实 encoder context 的预编码业务可绕 typed gate，不改称拒绝证据。D11 同 JVM 可信代码前提、全部可达 ctx.write 起点清单、原生 TLS/encoder/response/control/rawbytes 来源审计为 **聚焦集成测试 + Review-only**：静态 API 集合不能证明 pipeline 传播、动态特权修改或所有原生路径。自动化条件是能可靠关联实际装配、每个写起点、命令来源、编码前计数及 native completion，并有违规旁路/合法响应控制帧正反 fixture；不能靠包名或一次启动校验声称永久 containment。

D10 会话数组、严格叶 URI、typed gate、资源/终态排空、后台禁用和材料轮换分别由 im-bolt 聚焦测试拥有；owner 准入和固定实际模式由 Broker/Gateway 测试拥有；消费方 startup 检查真实 Spring/SDK 装配，E2E 检查真实 Gateway，HA 使用真实 mTLS 而非开发观察票。D14 材料更新测试不取消双向 CA 原生拒绝义务，不将历史失败改为 GREEN。

`scripts/affected-modules.sh` 沿现有路径并集：im-bolt 变化带入四个消费方启动模块、Broker/Gateway behavior、E2E 与 architecture；Gossip 带入 Broker/E2E/architecture；Account/Message 配置变化也带入 E2E。`test-harness.sh` 单路径注入 fixture 防止混合工作树掩盖漏映射，仅证明路由，不冒充实际启动或业务通过。没有新 mode、缓存、journal 或 scope 排除；最终 full 的同轮 reactor 报告与真实 HA 仍不可少。

### 固定项目角色身份命名空间

规范所有者为 [Security「固定项目角色身份命名空间」](../SECURITY.md#固定项目角色身份命名空间)，设计依据为[五角色固定前缀设计](../design-docs/2026-10-28-fixed-peer-identity-prefixes-design.md)。项目五角色统一使用 `PeerIdentityConstants` 固定值；bootstrap、committed 目标、Gateway 下行描述和接收方角色授权不能由部署属性改变。通用 `im.bolt.client.allowed-peer-uri-prefixes` 仍可配置，Plugin 不引入项目角色耦合；固定命名空间不替代 CA 隔离或完整身份认证。

采用 **SDK/授权层聚焦契约测试 + Review-only**：Broker SDK 的 `BrokerClientTest`、`BrokerClientRoutingTest` 与 Gateway SDK 的 `GatewayClientTest` 负责目标构造、配对及错误命名空间拒绝；Broker 的 `IdentityAccessInterceptorsTest`、`ServiceCallerRoleInterceptorTest` 和 Gateway 的 `GatewayBrokerAuthorizationTest` 负责角色拒绝与唯一身份约束。测试预期保留独立字面量，不全部引用生产常量。此处登记测试责任，不表示这些测试已在当前输入执行或通过；配置装配及跨边界验证仍按实际报告记录。

九处项目配置入口、YAML/环境占位符和无意义前缀传参的移除，当前由 Review 检查精确差异及生产调用链，并核对迁移说明：旧覆盖不生效、未知属性可能被忽略、自定义前缀部署需协调证书/二进制/配置，不能声称旧键统一 fail-fast。静态移除检查保持 **Review-only**：只有可低成本可靠地区分项目角色绑定、通用 Plugin 参数、历史文档和合法测试 trust domain，并具备配置入口残留反例、通用可配置前缀与历史/测试正例后，才升级自动检查。本次不新增框架、checker、类名例外或旧键拒绝机制。

### 资源采样 fixture 的完成边界

`scripts/test-load-test-harness.sh` 的真实采样器 fixture 用 fake Docker 第五次 stats 调用终止本次 owned 目标；此前四次采集已经返回，再由 Ruby 父进程 wait/reap。不能用固定进程存活秒数推断样本数：宿主调度和命令启动成本会使四条样本要求偶发失败。fixture 的 30s 只防挂，不替代样本数、0600、单调时间与 schema 断言；正式采样器不增加测试开关。采样间隔算法仍由同文件显式 elapsed 向量及 full-interval mutation 检验，不把本 fixture 的零间隔当生产周期保证。最窄回归须包含正常输出与丢输出负例，完整父 Harness 仍需独立实际执行。

### 命令期限 fixture 的成功与超时见证

`scripts/test-routing-ha-process.sh` 的真实 runner stub 将 engine hang 负例与正常 build 路径分开：负例通过 ready PID 和阻塞 pipe 见证真正挂起，要求 engine-info exit124、groupGone、owned PID 消失、无 Maven/Compose 调用及外层有界结束；正例以 engine-info 成功事件、显式返回 marker 和 Maven 启动顺序证明路径，fake Maven exit23 必须原码传播。正例 15s 命令预算和 45s 外层等待仅防挂，不把亚秒内进程启动当性能保证；负例 engine 2s、外层6s仍单独约束。cleanup/down 模式保留原 exit29、logs 超时后恰好一次 down 与资源账本断言，不改生产期限/终止算法。现有 deterministic fixture guards 自动保护这些行为；识别其他测试中不当宿主启动时限仍为 Review-only，待可靠低成本时序分析及正反 fixture 后再自动化，不新增框架。

### 健康恢复 fixture 的状态见证

`scripts/test-load-test-harness.sh` 的 management restore 正例按服务记录重启后 inspect 序列，要求首次 unhealthy、下一次 healthy，并验证真实 runner 最终 BASE_READY；不能仅以固定秒数内成功推断两次检查已执行。正例60s仅防挂，不构成宿主性能保证；永久 unhealthy 负例要求真实 runner exit124、具体超时消息及 RESTORING，不能靠放宽正例预算代替。生产 `wait_for_service_health` 与 timeout 算法不增加测试开关。此约定由现有 fake Docker/runner fixture 自动执行；是否涉及不恰当时间假设仍为 Review-only，待低误报静态时序分析及正反 fixture 可可靠识别后再自动化，不新增 checker/框架。

### HA 测试材料所有权

真实 HA 不用 DEV 替代默认 MTLS。`scripts/lib/routing-ha-tls.rb` 仅在 runner 新建私有空目录内签发测试 CA/独立叶库，移除 CA 私钥，启动前只读校验全部材料及精确部署配对；材料非生产供应，也不实现 D13。`scripts/test-routing-ha-tls.sh` 保护独立密钥、严格 URI/DNS、证书/信任/有效期、缺失/损坏/公开权限/配置篡改拒绝和生成失败。`test-routing-ha-process.sh` 保护 TLS check 必须早于 Compose up、前检失败无创建、down 成功删除材料而 down 失败保留；同轮manifest分支自测用显式stub，不复用真实证书当网络成功。真实 mount 权限、容器 JRE 互操作及多 Broker 互认证须由实际 HA 证明，不能由离线 GREEN 推导。SIGKILL/崩溃残留人工确认后清理，不新增旧目录自动接管规则。

## 可信 Leader 映射与 following 纯拒绝

规范所有者为[可信提示设计 H1—H8](../design-docs/2026-11-02-ops-trusted-leader-hint-mapping-design.md)、[Security](../SECURITY.md#ops-可信-leader-提示) 与 [Reliability](../RELIABILITY.md#管理-leader-提示的可用性边界)。实际初始化 Node 的完整 PeerId 必须先于官方首次 registration 发布；保留键同值保留、冲突失败关闭，管理 origin 独立。Ops 原始列表去重前拒绝歧义，完整字符串唯一配对、不归一化或猜端口；旧 HTTP 精确可信路径保持，最多一次原 command／scope 跟随。

机械约束复用 `RaftRuntimeTest`、`BrokerClusterConfigurationTest` 的实际身份／首次注册装配测试，`NacosBrokerDiscoveryTest` 的 priority／IPv6 文本差异、非法值、HTTP 不回退与无关缺失对照，及正式 `OpsCliLeaderHintMappingTest` 的原始 DiscoveryClient→公共 HTTP 链。`BrokerClusterOperationAdapterTest` 保护原请求与单跳；`OpsCliGovernanceE2ETest` 的真实三 voter 成功／缺失／冲突证明实际两写或一次写零 follow。官方 registration 捕获与受控 NamingService 不等于真实 Nacos 网络证明。

H7／H8 只允许 caller 原子无锁读取事件快照以纯拒绝；冲突／共享、closed／roleFault／满额优先，roleStopped 加有效同 generation 观察才返回 NOT_LEADER，零工作流／永久预算／队列／pump／许可／native。锁序 `rolePublication → listener → executor mutex` 不反锁；独立 FSM failure 与 roleFault 永久失效，事件延迟不证明实时健康，worker／native 门禁保留。机械传感器为 `BrokerMembershipWorkflowTest.healthyFollowerRejectsWithoutWorkerOrNodeAccess`、`followerHintNeverOverridesHardRefusal`，`RaftRuntimeTest.followingObservationIsInvalidatedAndPermanentGatesRejectLateStart`、`followingRejectsInvalidEventAndSwitchesLeaderWithoutChangingGeneration`、`followingGetterDoesNotWaitForBlockedRolePublication`、`firstOfficialConfigurationClosureClearsEstablishedFollowingOnFsmConsumer`，覆盖实际 Runtime 发布→listener 等待 executor mutex 及官方首次配置 closure／FSM consumer。

多 customizer／热更新保留键的全装配后续写者、跨模块 command／scope 来源及任意间接 Node／锁数据流仍为 Review-only；只有能可靠识别实际写者、上下文和副作用的低成本正反 fixture 后才自动化，不新增类名例外或放宽现有 checker。当前 E2E 响应关联仅支持每 Broker 一次 POST，不能外推通用并发／重试场景。

## 成员许可 callback 与角色证据复验

成员配置 native closure 只记录 status/code/message，不得读取 Node；诊断 Leader hint 必须在 native 前由 worker 捕获，并明确可能 stale/null。专用 drain 读取的 term/generation/index 必须通过 Adapter 完整保留，worker 消费和提交 CAS 前复验同一角色快照，不能仅靠任期相等。共享 Lease/learner 读取不因此改变语义。

具名确定性回归为 `RaftRuntimeTest.configurationClosurePreservesNativeCodeAndLeaderHint`（callback 线程零 Node 交互）和 `BrokerMembershipPermitAdmissionTest.successfulReadCallbackThenSameTermRoleRestartBeforeWorkerConsumptionMustNotCas`（callback 成功、worker 暂停、同 term 重启后零 CAS）。通用跨方法数据流仍为 Review-only；自动化须可靠关联 callback 执行上下文、Node 间接调用、证据 generation 的 Adapter 传递与最终消费位置，并有正反 fixture，不能用类名例外或字符串检查替代。


### 本机许可固定视图、等待预算与装配（T4）

规范由[已批准治理设计](../design-docs/2026-10-28-official-jraft-governance-simplification-design.md)、Broker Server README 和 im-raft README 拥有。4096 永久事实槽、实际 RPC 等待者／本次期限、历史重复请求留下的 completion 依赖是三个独立边界，不能以槽容量或同时等待者 cap 推导历史 relay 有界。`BrokerMembershipExecutorTest.duplicateExecuteRequestsShareReadOnlyViewWithoutRepeatingNative`、`duplicateConfirmationRequestsShareReadOnlyViewAndStartExplicitNewRound` 保护固定只读视图；`duplicateConfirmationWaitsDoNotAccumulateRelays`、`confirmationWaitingViewsKeepCompletedRoundsIsolated` 保护每确认轮固定不可取消等待桥、依赖不增长和新旧轮隔离。`runtimeAndExecutorShareFirstFaultAfterPartialRoleAccountingAndNeverReopen` 以真实角色记账前／中途遍历故障验证共享终身 fault，不以完整记账后抛错代替部分交错。

`BrokerMembershipPermitAdmissionTest.pendingRemoteConfirmationsRejectExcessWaitersWithoutCallingApplication`、`remoteConfirmationTimeoutReleasesOnlyRequestBudget`、`remoteConfirmationFailurePreservesCauseAndReleasesBudget`、`interruptedRemoteWaitRetainsFiniteDeadlineAndRestoresInterrupt` 分别约束非阻塞等待者准入、同一 deadline、异常传播及期限内处理中断后恢复标志。超额／超时仅返回 UNKNOWN 并释放本次等待预算，不取消或完成共享事实、不释放永久槽或新增 worker。`BrokerMembershipExecutorTest.queuedCommittedPermitCannotStartNativeAfterLeadershipLoss` 与 `queuedCommittedPermitStartsNativeWithoutLeadershipLoss` 以真实 committed／queued 前提及恰一次 native 正向对照保护失权反例。默认等待数／期限不是实测容量或 SLA，也不约束全部上游队列或抢占任意同步代码。

`BrokerPropertiesTest` 验证独立 inbound typed 配置默认／覆盖及非法值；`BrokerObservationRequestConfigurationTest.membershipConfirmationHandlerUsesOneConfiguredBean`、`invalidMembershipConfirmationBudgetFailsContextStartup` 验证真实 factory、绑定、唯一 Handler／列表身份、processor 装配、参数快照及启动拒绝。配置层提取 typed 值，Handler 不依赖 infrastructure；原分层检查仍由 `LayerBoundaryTest.brokerInterfacesMustNotDependOnConcreteInfrastructure` 承担。扫描候选断言直接以默认 `AnnotationTypeFilter(Component.class)` 检查 Handler metadata，包含组合注解，不以同名 Bean 合并后的实例数证明不存在扫描注册路径；仅加回 Component 的有效断言 RED 证明该断言敏感性。

以上具名测试承担局部机械证明，不替代完整启动、真实 MTLS／HA 或最终验收。通用资源依赖、跨轮所有权和角色回调数据流仍为 Review-only；自动化须能可靠关联 Future 依赖、实际等待、故障交错与最终副作用并具备低成本正反 fixture，不增加类名豁免、通用框架或字符串推断。

### 成员显式重投与固定结果视图（T5）

成员用例不得因入口差异绕开持久化完整目标／许可／排空链；native 最终 Commit 使用当前 revision，learner-sync 保持 acceptedRevision。learner 本地表仅共享在途请求，完成后精确移除；成功重放通过已提交身份检查保持 DUPLICATE 与原 epochs，不重复 Begin、Commit 或证据。不同内容的在途请求立即拒绝，未提交拒绝不长期缓存。`BrokerMembershipWorkflowTest#terminalLearnerReplayUsesCommittedIdentityWithoutNewBegin` 与 `learnerIdentityConflictRejectsInflightImmediatelyAndChecksCommittedIdentityAfterCompletion` 保护这些约束；共享永久预算不随表清理回收。

旧 holder 事实重投只消费本机精确 DRAINED slot，不将未知 native、超时或新 Leader 当作排空。operationId 多 slot 必须按持久化步骤选择：唯一当前待确认优先，无待确认才取最高步骤的精确已确认 currentPermit；不按 map 顺序或 caller target 挑选。operation 发送 owner 在首次 reservation 建立，后续 reservation 只继承；自动／显式同 receiver 共享当前 view，不覆盖 pending、不留多轮历史。`explicitDrainedRetransmissionSelectsPendingStepBeforeHistoricalSlots`、`explicitDrainedRetransmissionNewReservationCannotCreateSecondSendOwner`、`explicitDrainedRetransmissionSharesAutomaticAndExplicitInflightWithoutGrowth` 保护选择与有界引用，`explicitDrainedRetransmissionRevalidatesLifecycleBeforeSendAndPublication` 保护 fault／close 两窗口。跨 slot／publication 数据流仍为 Review-only：可靠自动化条件是能识别 ownership 和 callback 捕获链的低成本正反 fixture，不能用 class-name 字符串豁免替代。

诊断不能调用配置修复或改变普通故障观察；所有 Node 阶段由唯一 worker 执行，合并诊断 pending 在消费时复验 generation／fault／close。`periodicDiagnosticPendingFactCannotReadNodeAfterLifecycleInvalidation` 与 `periodicDiagnosticUsesWorkerAndDoesNotRepairDriftOrChangeObservers` 提供局部自动化。真实 self reduction fixture 证明原配置改变、精确 drain 和迟到不回退；其中替代 peer transport 的测试不承担真实 MTLS 网络或公开 Ops 可达性证明。T5 仍须整体规格与质量 Review，不以矩阵存在代替实际验收。


### Raft 完整初始化后的退出与角色封闭

im-raft 的关闭必须先原子封闭角色 publication：非 Leader observation 与新 generation 先于资源等待，迟到 callback 不重开准入，fault 首因保留；不得持 publication monitor 进入 Node 或等待 join。`closeInvalidatesRoleBeforeBlockedJoinAndLateLeaderCallbackCannotReopenIt` 用保存的真实 StateMachine callback 与阻塞 join 验证。

请求 executor 的 shutdownRequested 不等于 terminated。只有精确自有 Node、RPC 和 executor 退出已确认，才释放 endpoint；中断继续其余非阻塞清理，保留地址并允许后续 close 续等，统一主异常与 suppressed，不能在 finally 中丢失后续失败。`closeWaitsForExactOwnedRequestExecutorBeforeReleasingEndpoint` 用精确自有 executor 阻塞任务验证；`joinInterruptionPreservesLaterRequestShutdownFailureAndInterrupt` 验证未预置 interrupt 的 join 中断与后续 shutdown 失败组合。`occupiedRpcPortFailureReleasesOwnedNodeAndResources` 覆盖完整 init 后真实 bind 失败的精确资源及同目录重启。partial-init 与工厂返回前异常仍是上游公开生命周期限制，不以该测试推导任意阶段全部回收。

learner fixture 不能把 Leader addLearner 的完成当作 follower applied-ready。`learnerReceivesCommittedLogsAndCanBeRemoved` 先提交原业务命令并 bounded 等待 learnerState 真实 apply，再保留实际 apply、ReadIndex、nonleader、完整目标移除与 Leader 观察不可变值断言；旧 follower applied-membership API 专属断言随私有接口退役，不能把 Leader 列表冒称 follower applied 配置；不 ignoreExceptions，不把初始配置当已应用配置。该前提修订不证明已排除所有生产时序回归。跨组件 ownership／异常数据流保持 Review-only；自动化需能可靠识别准确 owner、阶段及异常链的正反 fixture，不能按类名例外跳过规则。

IAM PUBLIC Client 与单向撤销采用 **聚焦协议／真实 MySQL 行为测试 + Review-only**：`IamPublicClientProtocolTest` 通过生产 OAuth 安全链与 Token Generator 验证 NONE、S256、精确 redirect、码单用与完整 Introspection Claims；`IamPublicClientRevocationProtocolTest` 通过实际授权应用服务和 MySQL 验证 HTTP revoke、数据库写失败及提交后 Introspection，可信登录和业务 Claims 查询仍为受控 fixture。`OAuthAuthorizationRevocationConcurrencyTest` 与 `IamAuthorizationInvalidationTransactionTest` 验证不可逆更新、真实锁等待、事务回滚及 Client／管理员／角色／权限撤回。测试连接必须为任务专属数据库，故障注入 trigger 在 finally 删除。Review 核对受影响授权选择、事务提交和已准入请求边界；普通静态扫描不能可靠证明跨请求／事务时序，只有具备低成本确定性数据库时序 fixture 后，才将通用一致性规则升级为共享自动门禁。现有 Maven 枚举测试，未配置专属 MySQL 时这些测试跳过，不将跳过记为真实数据库验收。

### 模型来源与输出合同的验证边界

模型来源与格式检查沿用现有 `ReactorEvidence` 和隔离 self-test。内部 filesystem/模拟 runner seam 不等于生产防绕过；正式 wrapper 固定 `system_root=/`，不开放 caller override。PATH 选择首个普通且当前 UID 可执行 launcher；权限是现场谓词，不由内容 SHA 证明，目录链与 TOCTOU 也不由摘要自动解决。

正式来源只接受独立受审的精确成员/SHA 清单，未知来源先停止 Review，不动态扫描后自动接受。synthetic settings 摘要不证明宿主已审摘要。settings 先比整文件 SHA 再有限语义检查，身份由同次 bytes 解析返回并关联，不 trim/normalize/case-fold；错误固定，不输出 XML、身份或 URL。

raw reader 在受审 canonical 路径读取有限 XML、重算 SHA 并推导继承/packaging/modules；formal/bound 及正文消费每次重读，比较 GAV/pom/basedir/packaging/modules/SHA 后使用新事实，不信 caller 标志或自填摘要，不实现通用 resolver。raw/oracle/effective/body fixtures 保持独立，不能从 reader 常量或同一 map 同时生成输入与预期。

展示投影取同一 SHA 绑定并通过 formal/raw 验证的 XML 原顺序，不从排序 oracle/stdout 自证；name 缺席 fallback artifactId、存在空串保留、不 trim，有限形状失败关闭。填充按 Java UTF-16 code unit；单次 logger INFO 前缀不逐行剥离。段 API 不能证明完整 stdout 的顺序、唯一段、summary/time 与最终 EOF，更不能证明真实采集来源。

保留细粒度确定性正反例，按完整合同/信任边界批次验收，不为每个展示字段冻结 Review。完整纯输入输出、真实来源/生命周期接线、实际 agent/产物/report 分开验收，不跨边界扩大局部 Accept。新能力测试先行；真实缺陷 RED 必须命中原行为，刻意宽松变体只算敏感性/mutation 证明，禁止为拿 RED 故意加入已知错误。missing-method/setup/语法失败不算行为 RED；原证据不改写，误分类由计划账本更正。

运行日志、时间、Review 与当前状态由计划及 `.harness/` 拥有，不在规范逐轮追加。批次划分、原行为与 mutation 归属、授权语义暂为 Review-only；自动化需可靠合同边界/证据归属正反 fixture，不按类名豁免或放宽 freshness。
