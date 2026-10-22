# JRaft applied membership 私有制品

本目录保存 JRaft 私有制品的构建契约、补丁入口和来源证明，不保存 JRaft 全量源码，也不提供类路径 overlay。固定消费坐标为：

```text
com.co.kc.thirdparty.sofa:jraft-core:1.4.1-imchat.1
```

当前状态是 **新 POM/recipe 待独立代码 Review，未完成当前候选的双构建验收**。历史 capture 已运行，但不是当前候选的重跑证据。固定完整源码归档、checkout-relative 候选补丁及法律文件 SHA 已登记在 `provenance.yml`。归档与 commit 的对应关系来自本地恢复记录，不冒称独立 tag/签名验证。缓存 sources JAR 不作为正式源码输入。

## 输入

调用方通过 `JRAFT_SOURCE_ARCHIVE` 提供固定完整上游 tar 归档，通过 `JRAFT_BUILD_OUTPUT` 指定新建或空的绝对路径输出目录。脚本不下载源码、不修改输入归档；使用空 Maven settings，不读取 Registry 凭据，允许 Maven 正常解析依赖。

候选 `applied-membership.patch` 相对于固定 checkout 根目录生成，包含访问器、三个 focused suite、批准的 `NodeTest#testSnapshotSync` 确定性修复及已 Review 的五项 lifecycle 测试补充。它不是 Spike 的 sources-JAR 路径补丁。

`LICENSE` 保留上游原文；上游不存在 `NOTICE`，本目录 `NOTICE` 是说明私有修改的本地声明，不冒充上游文件。两者分别进入 binary JAR 和 sources JAR 的 `META-INF/LICENSE`、`META-INF/NOTICE`；既有归档条目若内容不同则拒绝，不能静默覆盖。

## 本地验证

```bash
# 仅在独立实现 Review 和父会话重型构建检查点通过后运行。
JRAFT_SOURCE_ARCHIVE=/absolute/path/to/upstream-source.tar \
JRAFT_BUILD_OUTPUT=/absolute/path/to/new-output \
  ./third-party/jraft-applied-membership/build.sh
```

`build.sh` 委托标准库 `build.py`，实现来源准入、串行完整 `verify`、报告检查、归档元数据规范化及两轮输入/payload 比较；历史实际 Maven capture 已运行至输入清单检查；当前修订仅有 fixture 证据，未重跑 capture。默认 `capture-input` 输出保守的隔离仓库 jar/POM 文件清单和比较结果，不生成 accepted manifest。`verify-lock` 还需已 Review 的 `input-lock.json`、固定 SHA 和 `inputInventoryReview=accepted`，必须精确匹配两轮清单，包括不能出现未登记条目；当前无输入锁，故不能通过此模式。清单不是逐项执行追踪，也不证明未来依赖永久固定。

脚本没有 install 或发布命令。消费者编译、实际 dependency tree/单实现检查、项目 integration 和真实 HA 尚未执行；生产发布/解析还需确认 Artifact Registry 所有权和权限。固定 GAV 不变。

## 允许的源码范围

正式补丁需要覆盖以下生产路径：

- `jraft-core/src/main/java/com/alipay/sofa/jraft/Node.java`
- `jraft-core/src/main/java/com/alipay/sofa/jraft/FSMCaller.java`
- `jraft-core/src/main/java/com/alipay/sofa/jraft/core/NodeImpl.java`
- `jraft-core/src/main/java/com/alipay/sofa/jraft/core/FSMCallerImpl.java`
- `jraft-core/src/main/java/com/alipay/sofa/jraft/storage/LogManager.java`
- `jraft-core/src/main/java/com/alipay/sofa/jraft/storage/impl/LogManagerImpl.java`
- `jraft-core/src/main/java/com/alipay/sofa/jraft/conf/AppliedConfiguration.java`

也允许经过 Review 的根/module POM、归档可复现配置、法律文件打包配置和以下测试树：`jraft-core/src/test/java/com/alipay/sofa/jraft/core/AppliedConfigurationLifecycleTest.java`、`jraft-core/src/test/java/com/alipay/sofa/jraft/storage/impl/AppliedConfigurationSnapshotTest.java`、`jraft-core/src/test/java/com/alipay/sofa/jraft/core/AppliedConfigurationClusterTest.java`。不得把补丁类放入 `im-raft`、Broker 或测试 fixture。

完整上游测试、真实 FSM queue/lifecycle、joint voters/learners、learner 变化、snapshot save/load/install、lagging follower、失败应用、超时/中断、队列满、重入和有界 stress 都是构建验收的一部分。缺少任何来源或补丁证明时保持失败关闭。

报告检查要求实际跳过集合是 reviewed skip 身份及原因的子集；未出现的 reviewed skip 仅在对应 testcase 存在且实际通过时允许。缺少 suite/testcase、空 testcase、重复 testcase、失败/错误或未审核跳过仍失败关闭，required suite 的计数下限不因跳过记录放宽。

## Recipe 修订与实现检查点

修订设计独立 reviewer `675ad592` 第二轮 `Accept`、findings none；以下契约已形成候选实现，但当前候选的实现 Review 和实际双构建尚未完成，不授权开始重型构建。恢复材料如下：

- 固定上游 commit：`cb1a0c7ac16ff146ad8abb04b1bcb035270bb234`。
- 本地源码归档：`/tmp/jraft-patched-artifact-before/upstream-source.tar`，SHA-256 为 `63a95b4cbc7a3384ca22abaa245cb3fd0fb713d5809381ecdac168656e0d6d0d`。
- 旧最终访问器补丁 SHA-256：`ceb67c578c26b59c4c9529c42c0113e83df4ae13931423c838bf20559b80effd`；确定性快照测试修复 diff SHA-256：`171ab874b13b390d687253245d6edeb44ed9426b0c306a83cbeb26377b57cf62`。
- 已接受源码位置：`/tmp/follower-snapshot-deterministic.VD9QwD`；完整测试日志：`/tmp/follower-upstream-complete.log`。既有结果为 99 个 suite、468 项测试、466 项通过、2 项跳过，不是新 recipe 的构建证明。

这些路径是恢复线索，不是构建时隐式搜索或下载来源。补充测试经 reviewer `ed6d9fd4` 最终 `Accept` 后已合入候选补丁，SHA-256 为 `9e7256d2817b9bbcfbaad39ebffa0c97fcea22949877f5d1b35539231f0e4642`。14 个变更路径、无删除；从 951 个文件的 pristine 归档应用后共 957 个文件，逐路径逐字节与 `/tmp/follower-acceptance-tests.xV1CHl` 相同。比较证据为 `/tmp/jraft-candidate-full-source-comparison.json`。

历史 949 文件清单错误排除了任意 `log` 路径组件，漏记真实 `src/**/storage/log` 的 8 个文件，但文件实际存在且 Maven 已编译。审计确认 `storage.log` 三个 suite 的 1/1/5 项均在完整测试中通过；不能把清单缺陷描述成源码或测试缺失。输入 inventory 不排除任何 `log` 路径，fixture 覆盖这一边界。

### 输入、隔离与失败

保留薄 `build.sh`，以一个标准库 `build.py` 实现可测试的准入和构建逻辑，不引入依赖解析框架。计划输入为 `JRAFT_SOURCE_ARCHIVE` 指定的绝对路径本地归档，以及 `JRAFT_BUILD_OUTPUT` 指定的新建或空绝对路径输出目录。不联网获取源码，不安装或发布制品，不切换消费者依赖；构建期间允许 Maven 正常解析依赖。

任何 Maven 调用之前，核验源码、补丁和法律文件 SHA、归档与 commit 对应关系的证据、完整源码结构、补丁精确路径范围及 `git apply --check`。归档仅允许普通文件和目录，拒绝路径穿越、链接、重复条目及设备。来源映射记录必须写清实际证据及限制，不能把本地恢复映射称为独立验证过的 tag 或签名证明。补丁范围包括批准的七个生产类型、三个 focused suite、必要 POM/法律资源，以及仅限批准方法的 `NodeTest#testSnapshotSync` 修改。

输入非法或准入失败使用退出码 65，缺少输入使用 66，子进程失败传播其退出状态。失败输出保留用于诊断，任何失败都不能生成 accepted manifest。输出目录不允许覆盖既有结果。

### 完整构建与工具链

两个构建串行执行，各使用全新源码目录和隔离 Maven 本地仓库。固定 reactor 命令为 `-Pjava9-plus,!deploy -pl jraft-core -am verify`。发布 goal 准入按该命令的 phase 可达性检查：默认 lifecycle 从 `validate` 到 `verify` 的 execution 不得含 `install`、`deploy` 或 `sign` goal；仅明确晚于 `verify` 的标准 `install`、`deploy` phase，以及此命令不执行的独立 site lifecycle 标准 `pre-site`、`site`、`post-site`、`site-deploy` phase 可保留这些 goal 声明。缺失、未知或自定义 phase 均失败关闭，不猜测插件默认绑定，也不按插件名豁免。原有禁止发布插件、环境/profile 注入及测试 skip/ignore 的检查不变。实际 capture 曾因默认 install/deploy/site-deploy 声明被误拒而在测试前退出 65；该失败输出不构成构建或接受证据，该阶段的历史失败已由后续 capture 越过；当前新 POM/recipe 尚待独立 Review 和 capture 重跑。完整 `verify` 每轮只打包一次并附加 sources，不增加第三次构建。不得用测试选择器或 `skipTests`、`maven.test.skip`、`maven.test.failure.ignore` 跳过或忽略失败；拒绝环境及项目配置中未经 recipe 控制的 skip/ignore/profile 注入，包括 `MAVEN_ARGS`、`MAVEN_OPTS`、`JAVA_TOOL_OPTIONS`、`JDK_JAVA_OPTIONS` 和 `_JAVA_OPTIONS`。

声明实际测量并支持的 JDK vendor、Java 21 完整 patch/build、Maven 完整身份及 OS/native 组合，不把工具安装目录或机器路径当作身份。当前 readiness 只确认 Java 21 和 Maven 3.8.4，不能据此猜测完整工具链 pin。支持其他工具链需另行测量和验证，而非仅因其位于不同目录就拒绝。所有 Maven 调用（含 `mvn -version`）强制 `MAVEN_SKIP_RC=1`，阻止启动器读取全局或 `$HOME/.mavenrc`。Java 探测使用 Maven 实际选择的 `$JAVA_HOME/bin/java`；未设置 `JAVA_HOME` 时解析 PATH 中 java 的真实位置并固定同一 JDK。除完整 Java 输出外，还核验 Maven 报告的 Java version/vendor 与所选 runtime home 一致，路径只用于证明同源，不作为工具链身份。

每轮记录实际解析到的 artifact、POM 和 plugin 输入 SHA，拒绝 SNAPSHOT、动态版本及未支持的版本范围。动态版本仅按完整版本值 `LATEST` 或 `RELEASE` 判定（忽略大小写），不按子串判定；`1.5.16.RELEASE` 等固定版本及 group/artifact 路径中的 `LATEST`、`RELEASE` 字样不因此拒绝。未解析的 `${` 属性表达式和版本范围仍由 `fixed_version` 拒绝。SNAPSHOT 分类按 Maven 3.8.4 版本语义：版本以 `SNAPSHOT` 结尾（忽略大小写，不要求连字符），或完整匹配 `^(.*)-([0-9]{8}\.[0-9]{6})-([0-9]+)$`；`snapshot-20080530` 不属于 SNAPSHOT。仓库清单仅从标准 `group/artifact/version/artifact-version[-classifier]` 布局取得版本，不按 group、artifact 或仓库路径中的字符串分类；无法确定该布局且没有 metadata/baseVersion 解析证据时失败关闭。输入清单描述本次实际输入，不声称所有未来依赖已经永久锁定；两个相同 payload 只证明这两次执行的可复现性。

此前只读检查在 Jackson parent 2.8 的 `requireJavaVersion/version=[1.7,)` 误拒。该节点属于 `maven-enforcer-plugin` execution 的配置，不是制品版本。修正后，外部 POM 仅按完整 Maven 模型路径检查坐标版本：`project/version`、`parent/version`、直接依赖及 dependencyManagement、build plugin 及 pluginManagement、plugin dependencies、build extensions、reporting plugins；profile 内对应模型路径同样检查。路径逐级保留 Maven XML namespace，不递归匹配任意同名节点；`configuration`、`properties` 中的 `<version>`，以及 foreign namespace 的同名结构不作为制品坐标。POM 根元素必须是无 namespace 或 Maven 4.0.0 namespace 的 `project`，其他根结构及 XML 解析错误仍失败关闭。

历史策略（已由下文「原始模型审计与有效模型检查」取代）仍严格拒绝上述坐标字段中的固定 SNAPSHOT、动态 token 和版本范围，包括未执行的外部 POM/profile 坐标声明；未把清单实现扩展为通用 Maven resolver。外部 POM 坐标字段的 `${...}` 仍保留既有延后有效模型/输入锁审核策略，不在本次推断解析值。无论字段是否参与坐标检查，全部 JAR/POM 原始字节仍进入 SHA 清单。该清单不是实际执行模型跟踪；不能据此断言某个 dormant dependency range 实际参与解析，也不能据此声称永久锁定。

本轮 30 项 fixture 测试通过，保留此前 27 项版本及其他契约测试；新增 Java 配置范围允许、模型坐标范围/SNAPSHOT/动态 token 拒绝、namespace 和错误 XML 根边界。RED/GREEN 日志分别为 `/tmp/jraft-recipe-coordinate-red.log`、`/tmp/jraft-recipe-coordinate-green.log`。只读复查既有 v4 输出 `/tmp/jraft-real-capture-v4.PG8gOX/build-1` 后，771 个候选输入（247 个 JAR、524 个 POM）已越过 Jackson Java 条件，随后在 `org/codehaus/plexus/plexus/1.0.4/plexus-1.0.4.pom:150` 的未声明实体 `&oslash;` 处发生 `ElementTree.ParseError`。按下一失败即停的约束，未处理该解析问题，未生成完整清单或 accepted manifest。前后全部 4173 个文件 SHA-256 一致；日志为 `/tmp/jraft-recipe-coordinate-inventory.log`。未执行 Maven、install、实际 capture 重跑或提交；独立聚焦 Review 及后续最小 POM 修正后的完整 capture 仍待父会话检查点。

### 消费 POM 与归档

消费 POM 仅针对固定上游结构生成：读取 `jraft-core` effective POM 的直接依赖，解析 dependencyManagement 管理版本，保留 scope、optional、type、classifier、provided 和 exclusions；不展开传递依赖。不能支持的 profile、版本范围、system 依赖或 sibling 模块引用直接失败。消费 POM 使用固定私有 GAV、无 parent、无未解析属性；实际 dependency tree 的官方 core/单实现检查留待后续消费者验证，不能从 POM 静态检查推断其通过。

ZIP 处理仅规范化时间、顺序、权限等元数据，保留原有条目内容和 manifest；仅显式加入核验过的法律文件和确定性输入 provenance。发现重复或签名条目直接拒绝，不静默删除。binary JAR 内 Maven `pom.xml`/`pom.properties` 必须由 reviewed 上游 module POM 正确生成私有 GAV；sources JAR 必须包含补丁后的源码。上游归档没有 `NOTICE`，不得要求不存在的文件；本目录 `NOTICE` 是说明私有修改的本地声明，来源必须如实记录，`LICENSE` 保留上游原文。

比较两轮 binary JAR、sources JAR 和自包含消费 POM 的字节及 SHA。日志、测试报告和运行时间单独存放，不进入确定性 payload。

### 测试、结果与采用门槛

先扩展现有 `test_build.py` fixture 测试并记录 RED，不削弱已有未核验来源拒绝测试。fake Maven sentinel 必须证明准入失败时未调用 Maven；另外覆盖第一轮失败后不启动第二轮、第二轮失败、payload 不一致、缺少必需测试报告。每轮检查全新生成的必需 suite 清单、准确计数及零 failures/errors，并记录跳过数量和原因。正式预期清单来自实际报告；补充验收测试若改变清单，须经 Review 后更新，不能用历史总数代替新结果。

静态 provenance 仅保存输入身份、法律来源和 Review/覆盖状态，不包含自身 SHA 或预先填写的测试通过结论。外部运行结果记录实际工具链、输入摘要、报告、跳过项和产物摘要；仅两轮完整成功且比较一致后生成比较接受记录。

生产采用仍为 **blocked**。补充测试关闭了独立 reviewer 指出的四项行为覆盖缺口，focused 16 项通过；历史完整 468 项不包含新增 5 项，不能称为新候选的完整验收。`requiredSuites` 保留 99 个 reviewed class 的最低计数并将 Lifecycle 提高到 6，合计最低 473 项，不固定发现上限；两个跳过项保留精确 class/method/reason，其他跳过拒绝。先前不稳定 leader 回归评估、实际完整双构建/输入审核、integration/真实 HA 等门槛仍保留。

历史 capture 使用 Maven 3.8.4 内置 `maven-jar-plugin:2.4`。当前候选 POM 已显式固定 `maven-jar-plugin:3.4.2`、`project.build.outputTimestamp=1781481600`、`addDefaultEntries=false` 及四个 manifest 字段；`maven-source-plugin:2.0.2` 保留。此次 POM 修改只涉及打包元数据，不修改业务源码，但不声称 JAR 字节与历史产物相同。既有微型诊断 `/tmp/jraft-plugin-diagnostic.M7fOOU/results.json` 显示新 binary manifest 字段集合相同而顺序不同；该诊断不是当前完整上游 recipe 的双构建证明，也不证明保留的 source plugin 已完成新候选验收。POM 修改摘要见 `/tmp/jraft-repro-pom-summary.txt`；本次未重跑任何 Maven。归档内容不同必须失败关闭，不能用内容重写掩盖。recipe/patch 独立 Review 及父会话重型构建检查点通过前不运行双构建。本节不改变协议，不授权 install、publish、消费者 POM 切换或生产采用。

### Maven 默认命名实体兼容检查点

只读 inventory 的 POM 解析副本现兼容 Maven 3.8.4 默认的 248 个命名实体（包括 `&oslash;`）。映射直接从本机 Maven `lib/plexus-utils-3.3.0.jar` 的 `org.codehaus.plexus.util.xml.pull.EntityReplacementMap.defaultEntityReplacementMap` 反射提取，不使用 Python HTML/HTML5 实体表。该 JAR SHA-256 为 `76d174792540e2775af94d03d10fb2d3c776e2cd0ac0ebf427d3e570072bb9ce`；按原数组顺序输出 `name=decimalCodePoint,\n` 的 248 行提取结果 SHA-256 为 `dd029f4f725dca0574fb24a44e13b28206cc101ac255d1ca054c4e640ed5b7a9`。本机 `maven-model-3.8.4.jar` 的 `MavenXpp3Reader` 字节码确认默认 `addDefaultEntities=true`，读取时把该表传给 `MXParser`。提取程序、结果及字节码分别保存在 `/tmp/ExtractMavenEntities.java`、`/tmp/jraft-maven-default-entities.txt`、`/tmp/jraft-MavenXpp3Reader.javap`，均为本机恢复证据，不是运行依赖。

兼容层只转换有限表内的命名实体为数字引用，不递归替换；XML 五个预定义实体及数字引用仍由 ElementTree 处理，未知实体和错误 XML 不跳过。完整 comment、CDATA、processing instruction 保留原文，不改其字面量；DOCTYPE/ENTITY 声明在交给解析器前拒绝，禁止外部 DTD/XXE。输入上限为 8 MiB，解析副本上限为其三倍；支持 UTF-8、ASCII、ISO-8859-1、UTF-16，其他编码失败关闭。不写缓存 POM，不调用网络或 Maven；全部清单 SHA 仍取原始文件字节，坐标声明在解码后进入独立审计；历史严格拒绝策略已由下文的新策略取代。

测试先行 RED 日志为 `/tmp/jraft-recipe-entities-red.log`；GREEN 为 `/tmp/jraft-recipe-entities-green.log`（35 项，包含原有 30 项、实体/安全/解码坐标 4 项及整张映射指纹与逐项解析 1 项）。只读 v4 inventory 日志为 `/tmp/jraft-recipe-entities-inventory.log`：771 个候选输入已越过 `plexus/1.0.4` 的 `&oslash;`，下一阻塞是 `org/codehaus/plexus/plexus-components/1.1.17/plexus-components-1.1.17.pom:138` 的 `project/profiles/profile[id=m2e]/build/plugins/plugin[org.maven.ide.eclipse:lifecycle-mapping]/version=0.9.9-SNAPSHOT`。它是以 `m2e.version` 属性激活的 profile 中的插件声明，不是 dependency，也不是已解析到该 SNAPSHOT 制品的证据。当前保守契约仍检查 dormant 坐标，因此保持拒绝；是否调整该策略须另行明确设计与 Review，本修正不放宽、不修父 POM、不继续级联处理。前后全部 4173 个 capture 文件 SHA-256 一致；未生成完整 inventory 或 accepted manifest，未运行重型 Maven、修改制品或提交。

实体兼容独立 reviewer `071f703e` 的唯一修订项是畸形输入 CPU 边界：原先 protected-section 正则对重复未闭合 opener 会反复扫描后缀。现改为单向扫描，仅识别 opener、声明及实体 token；遇到 comment/CDATA/PI 时只查找一次对应结束符，缺失立即拒绝，完整区段逐字符保留。扫描位置只前进，有限实体替换无递归；原有 8 MiB 原始输入及 24 MiB 解析副本上限不变。

新增回归对三类 opener 各重复 100000 次，在独立子进程中设置 3 秒超时，超时会终止并回收子进程，避免测试自身挂起；旧实现三类均超时失败。另覆盖 UTF-16 LE/BE、有 BOM 及无 BOM XML declaration 的 DOCTYPE/ENTITY 拒绝。RED 为 `/tmp/jraft-recipe-entities-bounded-red.log`（37 项、3 failures）；GREEN 为 `/tmp/jraft-recipe-entities-bounded-green.log`（37 项通过）。精确增量 diff 为 `/tmp/jraft-recipe-entities-bounded.diff`。本检查点冻结等待独立复审；没有再次运行 inventory/capture 或 Maven，前述 dormant profile 的 `0.9.9-SNAPSHOT` 阻塞和严格策略保持不变。


### 固定构建元数据 user.name 检查点

POM 的 `Built-By=${user.name}` 由 recipe 所有 Maven 调用共用的固定参数 `-Duser.name=kc` 提供，包括工具版本探测、两轮 effective POM 和两轮 `verify`。`kc` 是显式确定性构建元数据标签，不表示操作系统认证用户；不探测或限制调用者的登录身份、安装路径。原有环境选项拒绝及 `MAVEN_SKIP_RC=1` 保留，不允许通过 `MAVEN_OPTS` 等注入另一个值，也不增加可覆盖该标签的参数入口。

只读核对既有微型诊断 `/tmp/jraft-plugin-diagnostic.M7fOOU/c/pom.xml` 的 `${user.name}` 及 `jar-resolved-graph.log:116,176`：实际配置展开和 debug `manifestEntries` 均为 `Built-By=kc`。历史完整 capture `/tmp/jraft-real-capture-v4.PG8gOX/build-1/source/jraft-core/target/surefire-reports/TEST-com.alipay.sofa.jraft.closure.ClosureQueueTest.xml` 同时记录 JVM property `user.name=kc`，同轮 binary manifest 为 `Built-By: kc`。摘要和原始文件 SHA 保存在 `/tmp/jraft-recipe-user-evidence.json`。这些是既有输出的实际值，不是从路径猜测身份，也不是本次参数修正后的 effective POM、debug 或完整重跑证明。

测试先行：`python3 -m unittest discover -s third-party/jraft-applied-membership -p test_build.py`，RED `/tmp/jraft-recipe-user-red.log`（39 项，1 failure：Maven 参数缺少固定值），GREEN `/tmp/jraft-recipe-user-green.log`（39 项通过）。fake Maven 捕获并断言全部五次调用仅有一个 `-Duser.name=kc`，覆盖两轮 effective POM/verify；逐项验证受禁环境注入 `-Duser.name=other` 时 Maven 尚未执行即拒绝。原有 37 项实体及其他契约测试保留。精确三文件 diff 为 `/tmp/jraft-recipe-user.diff`。

本检查点冻结等待独立代码 Review。不修改 patch/provenance，不运行重型 Maven、capture、inventory、root consumer、install、publish、完整门禁或提交。该检查点当时仍严格拒绝 dormant profile 的 `0.9.9-SNAPSHOT`；用户后续已批准下节所述区分策略，不能把本节历史状态作为当前策略。


### 原始模型审计与有效模型检查

用户已批准将缓存原始 POM 的坐标声明与当前模块有效模型、物化制品区分。`input_inventory(repository, raw_model_audit)` 的返回值仍是精确的相对文件路径 → 原始字节 SHA-256 映射，双构建比较和 reviewed lock 的精确相等规则不变。所有物化 JAR/POM 的标准仓库路径版本仍拒绝 SNAPSHOT（含时间戳）、范围、`LATEST`、`RELEASE` 和未解析表达式；不增加 GAV 特例。

原始 POM 继续严格解析，保留 namespace、编码、实体与 DTD/XXE/大小边界；解析错误不能跳过。其 Maven 坐标版本另记于每轮 `work/raw-model-audit.json`，包含 `path`、`modelPath`、`profileId`、`version`、`classification`。分类为固定版本声明、未解析属性声明、动态或快照声明；分类不表示已解析、激活或执行。包括 dormant `m2e` profile 的 `0.9.9-SNAPSHOT` 在内的原始声明只审计，不单独阻断 inventory；不修改缓存字节，不输出虚构的执行图。

`validate_effective` 检查当前 `jraft-core` 合并后有效模型的项目/parent、依赖及 dependencyManagement、build plugin/pluginManagement 及其依赖、extensions、reporting plugin 及其依赖版本。已知保留 profile 声明的对应坐标同样检查，不按 profile blanket ignore。命令仍固定 `-Pjava9-plus,!deploy`，只接受既有 `java9-plus`、`deploy` profile ID，不能由 raw profile 审计推断外部 profile 已激活。消费 POM 直接依赖检查、publication phase、skip/ignore 和单实现验收门槛均保留；skip/ignore 检查在移除已知 profile 声明之前执行。

**证据限制与采用阻塞：** 模块 effective POM 与仓库文件清单不能证明外部传递依赖或 plugin 的有效模型中从未使用动态声明解析到固定 release，也不能证明完整执行图。所有物化 JAR/POM 路径版本及 SHA 均已检查，不等于所有 active 外部模型已获证明。此缺口保持 production adoption blocked，由父会话独立 Review 决定下一项最小证据（如 Maven dependency resolution debug 或相关外部有效模型），本轮不实现通用 resolver、不静默接受该缺口。

测试先行日志：基线 `/tmp/jraft-raw-audit-baseline.log`（原有 39 项通过）；RED `/tmp/jraft-raw-audit-red.log`（新策略及有效模型负例在旧实现失败）；profile skip 补充 RED `/tmp/jraft-raw-audit-profile-red.log`（42 项，1 failure）；最终 GREEN `/tmp/jraft-raw-audit-green.log`（42 项通过）。原有 raw 坐标动态拒绝测试有意改为审计保留断言，未知实体仍拒绝；增加同一 dormant m2e 原文/SHA、有效模型动态坐标拒绝、已知 profile 不豁免、未知 profile/skip 拒绝及两轮审计文件输出断言。其他 39 项原有契约均保留或明确按该批准策略更新。

只读 v4 复查 `/tmp/jraft-raw-audit-v4.log` 完成 771 项输入（247 JAR、524 POM），独立审计 6166 条原始坐标声明；两处 m2e `0.9.9-SNAPSHOT` 均保留。输出仅写入 `/tmp/jraft-input-inventory-v4.json` 与 `/tmp/jraft-raw-model-audit-v4.json`，不是 accepted manifest。前后整个 capture 根目录 4174 个文件原始 SHA 相同，其中 `build-1` 原有全部 4173 个文件不变，另一个是根目录 `toolchain.json`。历史 effective POM 与当前 manifest 的 jar plugin pin 不同，静态复查仍明确拒绝 `PLUGIN_VERSION_MISMATCH: maven-jar-plugin`，不把旧模型当作当前候选证明。patch SHA 保持 `f83c0943a31b2c8e3dd38114c4c388dacec7436b818247e3aa77ba723a5a3b3c`。未运行真实 Maven、capture、install、消费者/运行时构建或提交；当前独立实现 Review、真实双 verify、reviewed lock 及外部模型证据缺口仍未关闭。

独立 reviewer `d74b7d73` 指出仅枚举 `<version>` 节点会漏过缺少该节点的有效模型坐标。最小修订仅对合并模型枚举实际坐标对象：dependencyManagement、build plugin/pluginManagement 及依赖、extensions、reporting plugin 及依赖；对象存在却无版本时由 `fixed_version` 拒绝。直接消费依赖继续使用原有 dependencyManagement 补全规则；可选模型区段不存在不拒绝。原始 POM 审计策略与 profile 移除前的坐标/skip 检查不变，不增加外部 resolver。新增缺版本回归先 RED（43 项、6 failures）再 GREEN（43 项通过），日志为 `/tmp/jraft-raw-audit-missing-red.log`、`/tmp/jraft-raw-audit-missing-green.log`。完整 diff 为 `/tmp/jraft-raw-audit-final.diff`，本次修订 diff 为 `/tmp/jraft-effective-missing.diff`。本轮未再次运行 inventory 或真实 Maven，既有只读清单不是新双构建证明；外部传递有效模型证据和采用阻塞门槛保留，等待同一 reviewer 复核。
