# 自有 namespace 覆盖插桩边界设计

> 状态：草案（Proposed），2026-11-03。仅获授权起草与独立技术 Review，尚未批准实施。原方案记录 `.harness/t11/coverage-approach-review.json` 为 `Revise`，不是本设计批准；当前外部 JaCoCo 异常仍开放。本文不改变现有 T11 状态或验收门槛。

## 摘要

推荐仅在根 POM 的 `prepare-agent` execution 中将 JaCoCo 插桩范围限定为 `com.co.kc.imchat.*`，不设置 report 的 includes／excludes，不改变 Surefire 的 `@{argLine}` 与 Mockito agent，不排除单个第三方类。该边界按项目生产代码所有权定义，不按失败类名定义。

namespace 限定必须同时具有源码、有效构建配置和同轮生产字节码证明。generated 产物校验放入现有 full 的 reactor evidence 流程：Maven 前清除受控生产输出，`mvn -q verify` 后验证全部正式 reactor 模块的 class 内部名称、generated sources 与未过滤 report，并纳入原 manifest 摘要。不能在 architecture 模块运行时读取其他模块的旧 target 来代替此证明。不新增跨轮缓存、恢复链或第二份验收 journal。

## 背景

根 `pom.xml` 使用 JaCoCo 0.8.13，`prepare-agent` 当前未限定范围，`report` 绑定 `verify`。`scripts/verify.sh` 的 full 使用 `mvn -q verify`，随后由 `scripts/lib/harness-reactor-evidence.rb` 验证同轮必需测试与 exec JAR，再启动真实 HA。当前没有独立的 JaCoCo 数值阈值或 `jacoco:check`，不能把 Harness sensorCoverage 当作代码覆盖率。

真实 E2E reactor 曾出现 `net/sf/jsqlparser/parser/CCJSqlParserTokenManager` 的 `MethodTooLargeException`，Maven 仍可能 exit0。运行证据位于 `.harness/t11/leader-hint-e2e-module-attempt1/`；测试通过与覆盖插桩异常是独立事实。原方案 Review 要求可靠所有权 guard、配置正反例及实际 agent／report 证据，不能只加 include。

只读盘点观察到项目普通生产 Java 源码均在自有 namespace，现有 generated sources 主要包括 MapStruct 实现。但盘点读取的 target 可能陈旧，不构成 generated 所有权或本轮 report 的验收证据。

## 目标与非目标

- 目标：停止无意插桩第三方依赖，同时让所有正式 reactor 自有生产代码及生成代码保持可观测；配置和输出越界、缺失或陈旧时失败关闭。
- 目标：保持原测试、report、同轮证据校验、真实 HA、clean／full 和独立 Review，不降低已有规则。
- 非目标：修复 JSqlParser 或 JaCoCo 上游字节码算法、增加覆盖率百分比阈值、证明所有第三方行为正确、修改业务安全或运行语义。
- 非目标：通用 Maven 求值器、通用 Java parser、任意构建插件供应链审计、跨轮缓存和恢复框架。

## 已知事实与约束

所有权边界为 binary name 等于 `com.co.kc.imchat` 下的类；配置 wildcard 使用 `com.co.kc.imchat.*`。`com.co.kc.imchatOther` 不属于该边界。namespace 是受控工程约定，不是密码学身份。

正式模块集合必须从根有效 reactor 模型得到，不能从残留 target 或源码目录猜测，也不能只检查 architecture 的依赖闭包。当前独立 `im-plugin/im-raft/experiments/official-jraft` 不在主 reactor，不能因递归发现 POM 而把实验加入 full；未来正式模块变更随 reactor 模型重新枚举。

受控构建只允许普通 `src/main/java`、默认 `target/classes`、默认 annotation processor 输出 `target/generated-sources/annotations`。接受现有 Lombok 1.18.42 与 MapStruct 1.5.5.Final，不能因 Lombok 不生成独立 Java 文件而拒绝。接受 CLI 现有 Shade 3.6.0 的 Manifest 打包形状，不允许 relocation 或改写 `target/classes`。具体插件与 processor 合同见下文；未知配置先失败关闭，不把所有 Shade 或非 MapStruct processor 一概禁止。当前实际有效模型是否完全满足合同仍须取得真实模型证明。

## 方案概览

```text
source/config guard
    -> full reactor prepare: enumerate effective modules, clear owned production outputs
    -> existing mvn -q verify: compile + test + unfiltered report
    -> existing reactor verify: class/generated/report ownership + fresh proof + original tests/JAR proof
    -> existing manifest hashes/check + actual HA
```

沿用唯一 reactor manifest 的生命周期。新增信息只描述本轮产物，不接受上一轮证明，不新增跳过或恢复入口。

## 详细设计

### 1. 插桩与配置边界

根 POM 仅在具有 `prepare-agent` goal 的 execution 下加入：

```xml
<configuration>
    <includes>
        <include>com.co.kc.imchat.*</include>
    </includes>
</configuration>
```

不得将配置放在 plugin 全局或 report execution，不加 excludes，不改变 `skip`、`propertyName`、`destFile`、`append` 或 Mockito agent。report 继续分析所属模块的全部默认生产 class。

配置 guard 用 XML DOM／REXML 按结构检查根及正式 reactor 子 POM、pluginManagement 和 profiles，拒绝覆盖 agent 范围、report 过滤、skip、重置 Surefire argLine 和额外生产 source roots。重复 execution、属性间接改写及无法判定的动态形式失败关闭，不靠字符串搜到 include 就放行。保留实验独立构建的范围分离，不创建类名豁免。

根配置的结构正例不能替代继承结果。固定候选入口为从仓库根执行 `mvn -o -q -f pom.xml org.apache.maven.plugins:maven-help-plugin:3.5.1:effective-pom -Dverbose=false -Doutput=<本轮绝对路径>/effective-pom.xml`，不带 lifecycle phase、`-N`、`-pl`、`-am` 或 `artifact`。输出路径仅属本轮 `.harness/runtime/`。固定离线；缺插件或模型依赖时停止，不能静默联网或换版本。初次静态研究只读取本机 3.5.1 JAR 的 descriptor 与 `javap` 字节码，当时未执行命令。2026-11-03 已获单独授权并成功运行该精确离线模型命令，结果见本节证据；这只证明本次模型采集离线可用，不证明 lifecycle 依赖、实际 agent/report 或实现验收。

缓存 descriptor 声明 aggregator=true、requiresOnline=false、projects=`${reactorProjects}`，不要求 dependency resolution。缓存 `EffectivePomMojo.shouldWriteAllEffectivePOMsInReactor` 表明 CLI 调用且 projects.size>1 时写 `<projects>`，每项为带 Maven POM namespace 的 `<project>`；单项目写单 `<project>`。正式根默认 full 必须获得 `<projects>` 中完整 52 项；单 `<project>` 只允许隔离 fixture 的单模块正例，不能在 full 接受。拒绝其他 wrapper、重复 GAV、重复路径、少项、多项；GAV、roots、outputs 与 processor 坐标必须具体，不接受表达式。晚绑定仅按下述精确字段合同接受，不能全局豁免未展开字段。现有 ReactorEvidence 依测试推导报告，并不枚举完整 reactor，不能作为这个 oracle。

默认集合由附录 D 的 52 个 canonical POM 路径作独立 oracle。对受控原始 POM 只解析当前有限 parent/GAV、根 `${project.version}` 和 modules 语法，建立唯一 GAV→canonical POM→basedir 映射；不实现 Maven 通用插值。有效模型的 GAV、packaging、modules 必须逐项匹配该映射；packaging 按 Maven 语义比较，effective-pom 省略该字段时仅取默认 `jar`，不继承父 `pom`，显式字段仍须唯一且非空。缺省后仍与独立 oracle 比较，重复、空值、未知或不匹配值继续拒绝；不将这条例外扩展到其他必填字段。sourceDirectory=`<basedir>/src/main/java`、build.directory=`<basedir>/target`、outputDirectory=`<basedir>/target/classes`、testSourceDirectory 及 testOutputDirectory 也必须匹配。effective-pom XML 不包含可靠的 `MavenProject.file` 字段，不能假造该字段或只凭 artifactId 绑定路径；绑定依据是独立原始 POM 映射与有效绝对路径交叉核对。任一原始表达式超出有限语法则停止。

模型采集与实际 verify 必须使用同一 canonical 根、Maven/JDK、settings、`.mvn` 输入和环境；只允许 goal 与专属 output/verbose 控制项不同。记录两个实际 argv 及相关输入摘要到原 manifest。当前 full 为无 `-P`、无选择器、无额外构建 `-D` 的 `mvn -q verify`；模型命令额外 `-o` 只限制下载，不能改变项目模型。拒绝未审阅的显式/自动 profile 激活、MAVEN_ARGS 注入及不同模型属性；项目 POM 的受控 profile 集为空；settings profile 必须另行记录来源摘要、激活方式及是否改变模型属性，不能从 effective POM 无 profiles 推断 settings 未激活。未审阅的 settings profile 或新激活来源先停止并 Review，而非猜默认。冻结期间模型输入、模块集合或 verify invocation 改变即失效。要支持 profile 或额外属性，须先同步两次 invocation 的有限合同和 fixtures。

真实模型证据位于 `.harness/runtime/coverage-effective-model-probe-attempt1/`：命令于 `2026-11-03T00:42:29.464361+00:00` 开始、`2026-11-03T00:42:32.294178+00:00` 结束，exit0，Git-visible 输入双清单一致。XML SHA256 为 `51161b8864e30171934da20dbfb7c10b69d9c07d75c16ed4152e2ba21478c710`。实际 `<projects>` 含 52 个唯一 GAV，与原始 modules 递归集合及附录 D 逐项一致；canonical POM/basedir、packaging/modules、默认生产/测试 roots 和 outputs 均匹配。原 Reviewer 已确认这项真实模型证据成立；当时整体仍为 Revise。后续来源验证与技术 Approved 状态见下文及附录 C，不是实现验收。

随后只读来源研究确认实际 launcher 为 `/Users/kc/Program/maven/apache-maven-3.8.4/bin/mvn`，其 SHA256 为 `464159d2d22c3b161419fc649dae34ac2f6f0b9367175e57fec5b8120f725453`，M2_HOME 与该安装一致。全局 `conf/settings.xml` SHA256 为 `3a0b266572fe73c802667e082a18b73d9a65f93974fda69e02085afea49c4672`，含 2 个 profile、1 个显式 activeProfile；两个 profile 均无 activation 条件、无 properties，仅声明 repositories。用户 `.m2/settings.xml`、三个 launcher mavenrc 来源、仓库及祖先 `.mvn` 均不存在。研究时 MAVEN_ARGS、MAVEN_OPTS、JAVA_TOOL_OPTIONS、JDK_JAVA_OPTIONS、MAVEN_PROJECTBASEDIR、MAVEN_CONFIG 均未设置。只记录摘要与结构，不输出 repository URL、servers 或秘密。这是 probe 后来源快照，不能倒推为 probe 时完整外部输入清单。

settings 没有显式 localRepository，effective POM 保留 `${settings.localRepository}`，所以随后单独授权两条固定离线、非 lifecycle 命令，以实际结果取代默认路径假设。原件位于 `.harness/runtime/coverage-maven-source-probe-attempt1/` 的 `evaluate-local-repository/` 和 `active-profiles/`，各含原始 stdout/stderr、输入及来源双快照、result 和安全摘要；目录 0700、文件 0600，原始输出不回显，未运行 effective-settings。

第一条实际 argv 为 `/Users/kc/Program/maven/apache-maven-3.8.4/bin/mvn -o -q -f pom.xml org.apache.maven.plugins:maven-help-plugin:3.5.1:evaluate -Dexpression=settings.localRepository -DforceStdout`，UTC `2026-11-03T00:50:17.848922+00:00` 至 `2026-11-03T00:50:19.258494+00:00`，exit0。唯一 canonical local repo 为 `/Users/kc/.m2/repository`，是命令观测而非默认目录假设。有效模型 Mockito 版本为 5.17.0，对应 JAR 存在，size 709090，SHA256 为 `dff59ad8c61b026ef86cc113f94f2301eb06caaf81dec30c78a96a1a65595c5e`；没有实际 fork 路径验收。stdout SHA256 为 `0d2894c9107a3cbf97c876cb5f7e672af18913b2507b7bb2da9bfb344d3a4350`，stderr 为空。

第二条实际 argv 为 `/Users/kc/Program/maven/apache-maven-3.8.4/bin/mvn -o -f pom.xml org.apache.maven.plugins:maven-help-plugin:3.5.1:active-profiles`，UTC `2026-11-03T00:50:19.655052+00:00` 至 `2026-11-03T00:50:20.555596+00:00`，exit0。52 个唯一 project 的 G:A:packaging:V 集合与真实模型逐项一致；每个 project 恰好激活同一个已审全局 settings 显式 repository-only profile，无额外激活来源。source 类别为 external，52 条记录、1 个唯一 identity；identity SHA256 为 `aca5b36fe4d0bbcf3203544c6b13da4c303d9431eaa317f373d7305250ceb4ca`，不输出原 ID。stdout SHA256 为 `d16a95f53d57f8be6abf7243367e23dd8d2e811b559e9cf4af463b457b7d9f9b`，stderr 为空。

两条命令各自及跨命令的 Git-visible 双清单、launcher/settings/env 来源快照一致。文件来源前后只记录 exists/SHA；环境只记录有无及值摘要，不输出原值。mtime/size 是 `post-command-metadata.json` 中命令结束后的独立观察，不是命令前后 mtime 证明，也不能倒推到 00:42 的模型 probe。来源独立复核已通过；正式 full 仍须在当轮重新采集模型及来源并绑定实际 verify，不能复用这组旧 probe 作当轮证据。模型摘要最终只并入既有 manifest，不新增跨轮缓存；用户尚未授权 include、输出清理或 coverage 实施。

执行前来源合同补充：canonical Maven home/launcher/JDK/root 与实际 argv 分开记录；`maven.conf` 仅允许 home/conf。`bin/m2.conf` 固定 SHA `95fe6e030a0415a01edcac463063c877fccfec1bbfdefcc40ea87a35b3c5875f`，仅已审 main/set/realm/logging load/optional ext/lib load 形状；global toolchains 为 TOOLCHAINS 1.1.0 空结构，SHA `15d9efa2f3e0471ffb8e70dcbd207cf6fd8dac26436ceb267940af2cd2796d3a`，user toolchains 缺席。受信本地发行版的 boot 唯一 ClassWorlds 2.6.0 JAR、lib 40 个核心 JAR、conf/logging 唯一 simplelogger.properties、实际 jansi-native、lib/ext 唯一普通 README，以及其他发行版普通文件均须由独立受审精确成员与 SHA 清单绑定，不能动态学习当前扫描结果作允许清单。三 rc、根及所有祖先 .mvn 缺席；settings 沿用已审 repository-only profile 合同。环境另外拒绝 MAVEN_BASEDIR、MAVEN_DEBUG_OPTS、_JAVA_OPTIONS，连同既有注入变量；检查 JAVA_HOME/实际 java、HOME、PATH 的 launcher 解析链，不宣称穷尽所有环境。SHA 只证明受审 baseline 一致，不证明官方身份或供应链安全。未知成员、来源或额外 -s/-gs/-t/-gt/-P/-D 在 runner 前失败。

#### Settings repository-only 有限语义补充（技术 Approved）

本小节技术 Approved/findings none，原件 `.harness/t11/coverage-settings-design-review-attempt2.json`；原 Revise 保留。协调者在后续写前测得设计 SHA `c247faf069426cc28b1f8495601290f1568a1562ea689abca9fbacd6c4d22258`，Reviewer 未独立计算该 SHA。技术批准不是新用户执行授权；仅继续既有非破坏性 fixture/checker 范围，不授权真实 Maven 或 wiring。

两种义务必须同时成立，不能互代：

1. 正式来源先逐字节核对既审整文件 SHA256 `3a0b266572fe73c802667e082a18b73d9a65f93974fda69e02085afea49c4672`。未知摘要在任何命令前停止并送 Review，不动态扫描后学习新 baseline。摘要锁定既审非模型子树及所有实际文本，包括 URL、策略值、schemaLocation；不是官方身份或秘密安全证明。
2. 有限语义仅检查显式 profile 的模型影响：恰好两个 profile、一个显式 activeProfile；无 activation/properties/pluginRepositories，仅 repositories；身份与激活引用只在内存处理，以 SHA 摘要比较并输出安全摘要。仅内容摘要一致不能代替语义 guard，合成 fixture 的语义通过也不证明实际宿主摘要一致。不得据此实现通用 settings resolver 或 servers/mirrors/proxies 秘密字段解释器。

2026-11-03 安全结构核定只读取本机已审文件，结构化输出元素名、数量、namespace、属性名；未输出任何元素文本、属性值、profile ID、URL、server 或秘密，未调用 Maven/effective-settings。SHA 与既审值一致。核定结果及拟定有限语法如下：

- 根为 `settings`，namespace 精确 `http://maven.apache.org/SETTINGS/1.2.0`。根仅有 `{http://www.w3.org/2001/XMLSchema-instance}schemaLocation` 属性；该属性不解析、不取用远程 schema，实际属性值仅由整文件 SHA 锁定。namespace 声明仅绑定上述 settings 与 xsi，按 namespace declaration 处理，不误计为普通属性；所有子元素解析后的 namespace 必须精确等于 settings namespace，且无普通属性，不接受 namespace 替换或额外属性。
- 根直接子元素恰好各一个 `pluginGroups`、`proxies`、`servers`、`mirrors`、`profiles`、`activeProfiles`。前四者在当前已审文件均无元素子节点；不解释其秘密字段，也不将它们作为未来任意内容的语义通行口。若后续需要非空形状，须先审核新整文件摘要和适用语义，不沿用本合同。根不接受显式 localRepository 或其他新元素。
- `profiles` 只有两个 `profile`；每个 profile 恰好一个 plain scalar `id` 和一个 `repositories`，无其他元素。ID 非空且唯一，不接受属性、嵌套元素或表达式。两个 ID 作为集合只输出各自 SHA；正式已审整文件摘要锁定具体身份，不写入公开文档或诊断。
- Profile identity SHA 的字节规则固定如下：plain scalar 合并 XML 解析器解码后的全部直接文本片段，拒绝子元素、属性、空 ID，以及首尾 XML 空白（U+0020、U+0009、U+000A、U+000D）；不 trim、不做 Unicode normalize、不改大小写，以所得字符串的 UTF-8 字节计算 SHA256。XML 标准换行与字符引用解码不属于额外 normalization。`activeProfile` 使用完全相同算法，先绑定唯一两个 ID 的摘要集合，再比较既审 external identity SHA；不能匹配时停止并 Review，不调整算法迁就摘要。
- `activeProfiles` 只有一个 plain scalar `activeProfile`，非空；其 SHA 必须匹配两个 profile ID 摘要中的一项，并与设计既有 external 激活 identity SHA `aca5b36fe4d0bbcf3203544c6b13da4c303d9431eaa317f373d7305250ceb4ca` 相符。合成 fixture 使用独立非敏感 ID 和独立预期摘要，不能冒充宿主身份验证。
- 每个 `repositories` 恰好一个 `repository`；每个 repository 恰好各一个 `id`、`url`、`releases`、`snapshots`。`id`/`url` 为非空 plain scalar，无属性/子元素/表达式；URL 不解释、不输出，其实际值由整文件 SHA 固定。`releases` 只有一个 `enabled` plain scalar；`snapshots` 恰好一个 `enabled` 和一个 `updatePolicy` plain scalar。结构核定没有读取这些文本，故实际 boolean/策略值未在本小节确定：实现不得猜值枚举或放行新值，正式文本由既审整文件摘要精确固定。需要对值增加独立语义判断时，另行安全核定并 Review。
- 标量拒绝子元素、属性及未知表达式；容器只允许空白文本，拒绝混合文本。拒绝重复 singleton、未知/嵌套元素、额外 profile/repository、activation/properties/pluginRepositories、DTD/ENTITY；不进行外部实体或远程 schema 解析。允许 XML 注释，不把注释当语义；正式注释变更仍改变整文件摘要。

本轮独立设计 Review 为 Revise，仅必修 profile identity SHA 字节规则必须确定；其余有限设计获认可。上述固定算法与 namespace declaration 区分已按必修改写，原 Reviewer 已复核 Approved/findings none；不扩大 repository policy 值枚举或解析框架。

#### Active-profiles stdout 有限语法补充（技术 Approved）

独立技术 Review 已 Approved/findings none，原件 `.harness/t11/coverage-active-profiles-design-review-attempt1.json`，受审设计 SHA256 为 `b307f1f891a83dd080cab42c5058abf3c0ae23af6f72d556bfb40cc94ed90e39`。本记录为后置事实同步，不当新用户授权；先实施 inert 正文与同轮 settings bytes 关联，外壳独立后续片。

本小节仅设计草案，不授权 parser/fixture 或真实 Maven。事实来源为本机 Help Plugin 3.5.1 JAR（SHA256 `db1296f90c93cd1ac763f8262674dc376cf7670166dc2c270a498b1141a51865`）的 `ActiveProfilesMojo` javap 字节码，以及既有归档 stdout 的程序化去值结构核定；不读取真实 settings，不输出原始行、身份、URL 或路径。专属 scratch 为 `$CLAUDE_JOB_DIR/tmp/active-profile-grammar-20261103/`，不是新同轮执行证据。

已确定的插件正文生成规则：`getActiveProfileStatement` 读取 `MavenProject.getId()` 与 `getInjectedProfileIds()`，每项目先追加一个 LS，然后精确标题 `Active Profiles for Project '<G:A:packaging:V>':`、两个 LS、精确 `The following profiles are active:`、一个 LS；每条记录再追加一个 LS、` - `、原始 profile identity、` (source: `、source key、`)`；块末追加一个 LS，外层循环再追加两个 LS。固定合同只允许一条记录且 source key 精确 `external`；`There are no active profiles.` 分支虽然插件存在，正式合同拒绝。正文不带逐行 INFO 前缀：插件将整个 StringBuilder 一次交给 Log.info；下述固定 logger 在消息第一空行精确写入 `[INFO] `，随后正文行不再加前缀，不能给所有正文行随意剥前缀。

归档核定为有效 UTF-8、0 CR、0 ESC，52 个标题、52 个精确 active header、52 个 external 记录、260 个空行；真实值未输出。这与上述字节码分隔规则一致。拟定解析字节合同为严格 UTF-8，拒 BOM、ANSI/ESC、NUL 和除 LF 外控制字符；当前平台仅 LF，CRLF/孤立 CR 均拒绝，不默默 newline normalize。identity 在固定 ` - ` 与末尾 ` (source: external)` 之间提取，不 trim/Unicode normalize/case-fold；按 settings 已批准 UTF-8 字节算法，拒首尾 XML 空白/空值，比较同轮已通过整文件 SHA 和有限语义的 settings bytes 派生 active SHA。不能传一个与该 settings 结果无关联的 caller identity 摘要替代绑定。

正文 packaging 修复事实：canonical 52 原始 POM 为 39 jar/13 pom（缺席 packaging 按 Maven 默认 jar），安全路径/显式或默认分类/SHA 清单位于 `.harness/t11/coverage-active-body-packaging-source-attempt1/raw-pom-packaging.json`。正文冻结这些 GAV→packaging 事实，不信任 caller 联合改 oracle 与正文；独立 literal fixture 不从生产常量生成。此前 formal reader synthetic oracle/XML 中 im-common=pom 等仅为合成自洽，不等实际 packaging；历史 reader Accept 只涵盖 canonical GAV/路径及 XML 与 supplied oracle 比较，原始 POM 来源/packaging 绑定需重开，事实更正原件 `.harness/t11/coverage-packaging-fact-correction.json`，本片不并行修改旧 reader。

项目集合必须从独立 formal 52 oracle 推导 G:A:packaging:V，逐项唯一、无缺项/多项/重复/错误 packaging 或身份；不能从 stdout 自生成 expected set。合成正文 fixture 与 oracle 独立冻结，不以同一 map 生成两者自证。只输出计数/摘要/固定安全错误，不回显失败行。

外层来源已通过本地发行版静态核定，不再从单样本猜语法。Maven 3.8.4 `maven-embedder` JAR SHA256 为 `0baaed4ba30967d975d2f90ada7e8a9c7d927e7ca300d54deea8264d22ed51ec`：`ExecutionEventLogger.projectDiscoveryStarted/sessionStarted/projectStarted/mojoStarted/append/logReactorSummary/logResult/logStats/sessionEnded` 决定下述顺序和填充，`CLIReportingUtils.formatDuration/formatTimestamp` 决定时间字段。`maven-slf4j-provider` JAR SHA256 为 `210c13e6fee49f50a53863d6a7ee460354d281226ba6f381a06fb3a537374dce`：`MavenSimpleLoggerFactory.getLogger` 选择 `MavenSimpleLogger`，其 `renderLevel(20)` 为 INFO；继承的 `SimpleLogger.log/write` 只拼接一次前缀、一次 `println`，不重写消息内换行。发行版 `conf/logging/simplelogger.properties` SHA256 为 `2a9a027f8951cde95502e84de5bf6914bc356a58109d34e170dba1eb6a1cff3d`，明确 INFO、无日期/线程/日志名、levelInBrackets=true、System.out。该文件须加入独立受审 baseline，不能本轮扫描后自动登记。颜色或 logger override 不规范化，出现 ANSI/额外前缀即拒绝。

独立展示输入：同轮通过 formal reader 的 effective XML bytes 在原 XML 顺序上投影 52 个 identity、packaging、version、可选直接 `name`；`name` 缺席时回退 artifactId，存在但为空不当缺席。该规则来自 Maven 3.8.4 `MavenProject.getName`，`maven-core` JAR SHA256 为 `2415e64ffbc3ff4e7265268f651623342c7b0a9e0a77c5c54b4e82d1522f3189`。名称须为单行无控制字符、无未展开表达式的 XML 纯文本，不 trim；不扩大 business model 或新增 resolver。当前 reader 尚未提供此投影，后续必须显式实现并绑定相同 XML SHA，不能接受独立 caller 名称表。Help Plugin `META-INF/maven/plugin.xml` 中 effective-pom 与 active-profiles 均 aggregator=true，projects 均注入 `${reactorProjects}`；`EffectivePomMojo.execute` 与 `ActiveProfilesMojo.execute` 均按该列表遍历。故展示顺序来自另一条已验证模型采集，不以 stdout 的 reactor order 自证；formal oracle 仍独立核对完整身份集合。该顺序投影与 XML 来源前后快照须绑定同轮三命令，排序后的 oracle 本身不冒充 reactor 顺序。

有限外壳采用固定模板比对，不做通用展示解释器。定义 `I(s) = "[INFO] " + s + LF`、`E = I("")`、`H = I("-" * 72)`，长度均按 Java UTF-16 code unit 计算，填充仅 ASCII 空格/横线/点；不存在任意可跳过行。固定单线程、52 项、根项目执行一次 aggregator goal 的完整顺序为：

1. `I("Scanning for projects...") + H + I("Reactor Build Order:") + E`；随后按独立模型顺序 52 行 `I(name + spaces(max(1,72-len(name)-len(packaging)-2)) + "[" + packaging + "]")`。
2. `E`；只允许根项目一次 project banner。令 `c=groupId+":"+artifactId`、`n=len(c)+8`、`a=max(0,(72-n)/2)`（整数截断），banner 为 `I("-"*a + "--< " + c + " >--" + "-"*max(0,72-n-a))`。随后 `b="Building "+name+" "+version`、`q=" [1/52]"`，行精确为 `I(b+spaces(max(0,72-len(b)-len(q)))+q)`。packaging 行令 `a=max(0,(72-len(packaging)-4)/2)`，精确为 `I("-"*a+"[ "+packaging+" ]"+"-"*max(0,72-len(packaging)-4-a))`。只实现根模板，不允许任意项目重复 banner 或扩展 counters。
3. `E + I("--- maven-help-plugin:3.5.1:active-profiles (default-cli) @ "+rootArtifactId+" ---")`；CLI execution id 来自 `DefaultLifecycleExecutionPlanCalculator.calculateMojoExecutions` 的固定 `default-cli` 分支（上述 maven-core JAR）。随后精确 `"[INFO] " + 正文 + LF`。正文自身起始 LF，因此第一行是 E；正文按前述字节码规则拼接 52 块，logger 末尾再有一个 LF，不能另外剥离或折叠空行。
4. `H`；所有模型 version 相等时标题为 `I("Reactor Summary for "+rootName+" "+rootVersion+":")`，否则精确 `I("Reactor Summary:")`；随后 E 和按独立模型顺序的 52 summary 行。每行先取 `s=name+" "`，仅 mixed-version 分支再追加 `version+" "`；若 len(s)≤52，补点到 52 后再追加一个空格，否则不补。根行为 `I(s+"SUCCESS ["+spaces(max(0,9-len(duration)))+duration+"]")`，其余 51 行为 `I(s+"SKIPPED")`。此 SKIPPED 表示 aggregator 未产生其他项目 BuildSummary，不能误认生命周期测试跳过；额外 SUCCESS/FAILURE 均拒绝。
5. `H + I("BUILD SUCCESS") + H + I("Total time:  "+duration) + I("Finished at: "+timestamp) + H`，随后必须 EOF。固定 argv 无并发参数，拒绝 ` (Wall Clock)`。未知 INFO、WARNING/WARN、ERROR、DEBUG、下载、fork、额外摘要、前后空行或任意后缀均失败关闭；不改 `mvn -o -f pom.xml org.apache.maven.plugins:maven-help-plugin:3.5.1:active-profiles` 为 `-q` 或增加 output 参数。

时间字段独立于身份语义，只验证有限输出格式，不当性能/鲜度证据。`formatDuration` 对非负毫秒值分解 days、hours（0–23）、minutes/seconds（0–59）、milliseconds（0–999）；按首个非零高位选择 `D d HH:MM h`（D≥1、无前导零）、`HH:MM h`（HH=01–23）、`MM:SS min`（MM=01–59）或 `S.mmm s`（S=0–59、无前导零，mmm 恰三位）。只能接受 ASCII 数字及固定字面量，不将负时钟差或本地化数字猜作合法值；summary 左填充与 Total time 双空格均精确比较。`formatTimestamp` 使用 `SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")`；有限合同只接受四位正年份、真实月/日/时/分/秒与 `Z` 或有效 `±HH:MM` 时区，不接受尾串、毫秒或无冒号 offset。该有意收窄会对异常时钟/locale 失败关闭，不新增 JVM/CLI locale 参数。

B1 的公开纯格式接缝为 `format_active_profiles_summary(projects, duration)`：显式输入仅项目展示字段和 duration，返回有限模板字符串，不返回 validated/provenance/binding，也不能直接发布成功证据；它有完整 stdout consumer 这一生产调用者。mixed-version 仅在此用独立 literal 预期验证，不构造虚假 canonical GAV 或放宽 formal 合同。完整入口 `read_active_profiles_stdout` 必须自行重读 raw/bound/settings，使用自身派生的 displayProjects 调用格式函数，不接受 caller 展示表替代校验；已有段公共 consumer 的每次重读合同不变。完整流的 source/顺序矛盾与段缺失/重复/重排/额外 bytes 负例证明纯格式能力不能绕过来源守卫。此为既定纯格式方案的具体接缝，纳入 B1 整批独立 Review，不是宿主采集授权。

最小实现边界明确分为两层：正文 parser 证明 52 项和 settings active identity/source 语义；外壳 validator 仅从独立已验证模型投影生成固定字节模板，对两处 duration 和一处 timestamp 做字段匹配，再绑定整个原始 stdout SHA、固定 argv、exit0、同轮来源快照与 settings SHA。外壳不证明 profile 语义，正文正确也不能掩盖额外外壳行；原始 stdout 不进入公开错误或报告。归档去值核对显示只有一个根 banner/一次 plugin invocation，summary 为一个根 SUCCESS 与 51 SKIPPED，顺序与模板一致；这是历史一致性核对，不替代将来的 synthetic fixture 或真实同轮来源验收。

本 Draft 的外层语法与动态字段现已足以提交独立设计 Review；未定项不是缺失日志 class，而是尚未取得该补充的独立批准及后续实现/测试证据。实现前仍须 Review 确认模型 name/原顺序投影、settings active SHA 返回关联、固定 logger baseline 与有意收窄的时间/字节合同。当前没有 parser/fixture、真实 Maven/runner、reader wiring 或新的完整 bootstrap 验收，T11 唯一 active、bootstrapComplete=false；不以静态核定替代这些义务。

晚绑定按字段而非字符串全局放行：仅既有 jar/Boot 的 `mainClass` 可保留精确 `${start-class}`；仅 Surefire 的既有 `argLine` 模板可保留 `@{argLine}` 和 Mockito JAR 路径中的 `${settings.localRepository}`，同时保留既有 UTF-8/headless/Xshare 参数及解析后具体 Mockito 版本。原始模板固定为 `@{argLine} -Dfile.encoding=UTF-8 -Djava.awt.headless=true -Xshare:off -javaagent:${settings.localRepository}/org/mockito/mockito-core/${mockito.version}/mockito-core-${mockito.version}.jar`；有效模型中的 `${mockito.version}` 必须已解析为具体版本，不能作为额外晚绑定白名单。模板的顺序、边界和其余文本须与既有配置一致，不能借保留 token 拼接任意参数。Mockito JAR 必须以实际有效 local repo 加相对 artifact 路径解析，并与实际 fork agent 的 canonical 路径/摘要绑定，不能用默认用户目录替代。其他字段中的相同 token、未知表达式、跨字段注入或 GAV/roots/outputs/processors 未展开一律拒绝。fixture 正例保留这两个精确字段形状；负例覆盖未知 token、模板追加 agent、token 移到 output/processor、错误 repo 及 fork Mockito 路径不匹配。

#### Raw POM oracle 身份与来源补充（技术 Approved）

本轮只读事实研究原件 `.harness/t11/coverage-raw-pom-oracle-research-attempt1/raw-oracle-research.json`，SHA256 `086de20f464f703edfa079e67a7833bdad7e1844b02d25b9705afc345a400c9a`，保留52项原始字段、POM SHA、父路径、继承推导与常量差异。路径取附录D，独立根 modules DFS恰好一致，不从常量给原始POM贴GAV。root显式 `com.co.kc.im:im-chat:0.0.1-SNAPSHOT`；51 child均缺直接group/version，继承对应父 `com.co.kc.im`／`0.0.1-SNAPSHOT`。现 `FORMAL_CANONICAL` 非root51项标签 `com.co.kc.imchat:*:1.0.0-SNAPSHOT` 全不符实际；packaging为39缺省jar/13显式pom。当前无相关表达式、重复字段或自定义relativePath。正文已有Accept只证明synthetic GAV集合合同与真实packaging组合，不是实际52身份验收；旧reader Accept亦不补足raw来源绑定。保留历史证据，不重写其含义。

拟定最小修复只涉及既有 Ruby／self-test、设计/索引/Guide/Matrix/两状态，不改生产POM、Java、verify入口或采集wiring。先独立修正52 canonical GAV、正文与reader独立literal fixture；再增加有限raw oracle读取，formal reader与正文均不得信任任意caller GAV/packaging。输入仅canonical根及受审52路径，输出含GAV/POM/basedir/packaging/modules/原始SHA及有限推导来源；拒绝新增/缺失/重复、symlink与越根，不实现通用Maven resolver。原始输入bytes须与消费时相同并重算摘要，不能caller伪造sourceReviewed或自填SHA冒充绑定。

本补充首次独立 Review 为 Revise，唯一必修 F1 是容器语法未具体，原结论保留。修订后原 Reviewer 技术 Approved/findings none，原件 `.harness/t11/coverage-raw-pom-design-review-attempt2.json`，受审设计 SHA256 `486378fca88387426306a24b513688473cb9245b23cf55a131124199b8494312`；此后置同步不是新用户授权，也不是运行验收。

有限语法提案：

- XML 解析前拒绝任何 `DOCTYPE` 或 `ENTITY` 声明；不加载或处理外部 DTD/entity，不以解析器默认行为代替该拒绝。文档必须恰有一个根元素，其 expanded name 为 `{http://maven.apache.org/POM/4.0.0}project`。本轮检查的是该根的 direct 身份字段、`parent` 和 `modules`；不枚举或禁止与身份推导无关的 `dependencies`、`build` 等内容。
- direct `groupId`、`artifactId`、`version`、`packaging` 均须使用上述 POM namespace，各至多一次；存在的字段必须无属性、无子元素、仅含非空纯文本，且无首尾 XML 空白。`artifactId` 必需；root 的 G/A/V 必需且精确为 `com.co.kc.im:im-chat:0.0.1-SNAPSHOT`。child 的 group/version 缺席则继承下述父；存在时只允许受审具体 literal。packaging 缺席为 `jar`，存在时仅允许当前显式 `pom`。
- 每个 project 必须恰有一个 direct `parent`，namespace 精确为上述 POM namespace。`parent` 容器无属性，只允许 XML 空白容器文本和下述子元素；拒绝重复 `parent`、非空白容器文本及未知子元素。所有 parent 字段同样须为该 namespace、无属性、无子元素的纯文本，拒绝重复字段。G/A/V 各恰好一次、非空且无首尾 XML 空白。root parent 固定为 `org.springframework.boot:spring-boot-starter-parent:3.5.14`，并恰有一个完全空的 `relativePath`（无文本、属性或子元素）；不得省略或改成空白文本。child parent 仅有 G/A/V，不允许 `relativePath`；按缺省 `../pom.xml` 定位集合内已独立解析的父，其坐标必须与 parent literal 精确相等。root 外部 parent 不参与 root 显式 GAV 求值，不解析外部 POM。
- direct `modules` 至多一个，namespace 精确为上述 POM namespace；容器无属性，只允许 XML 空白容器文本与同 namespace 的 `module` 子元素。每个 `module` 必须无属性、无子元素、仅含非空纯文本且无首尾 XML 空白，值仅为受审直接相对单段目录；拒绝未知子元素、非空白容器文本、重复容器和同一父下重复 module 值。无 `modules` 与空 `modules` 均表示零个子模块，只有该路径在受审 DFS 中为叶子时才可接受；非 `pom` 不得含任何 module。最终按声明顺序 DFS，路径、父子关系及顺序均须与附录 D 完全一致，不能仅比较集合。
- root `${project.version}` 只可在已批准专属字段使用，不作为本轮 GAV 通用插值入口。未来任何未知表达式、新 parent/relativePath 形状均先停 Review，不扩展为通用 resolver。

验证提案：独立 literal 原始 POM fixture 与独立预期 GAV/body/effective XML，先合法 52 正例，再共同篡改 oracle/XML 或正文的 GAV/packaging 取得真实行为 RED→最小 GREEN。负例包括多个根或错误根 namespace；重复 parent/modules 容器；parent/modules 的属性、非空白容器文本、未知或错误 namespace 子元素；parent 字段缺失、重复、属性、嵌套，以及 root parent 坐标/空 relativePath 声明形状变化和 child 新增 relativePath；module 属性、嵌套、空值、重复或错误 namespace；非叶子缺失/空 modules、模块缺多/顺序变化，以及非 pom 含 module；`DOCTYPE`/内部或外部 `ENTITY` 声明。继续覆盖 parent 坐标不匹配、相对路径/循环/越根/symlink、身份字段重复/嵌套/表达式、POM bytes 替换及伪造摘要，错误只使用固定安全原因。正例明确覆盖叶子无 modules 与空 modules 的等价策略。不启动 Maven、不拿历史 effective XML 当当前来源，不清旧输出；本补充仅技术批准，实施与证据由当前 T11 拥有，T11 active/bootstrapComplete=false。

### B2 有界来源取证与正式入口修订（待集中 Review）

协调方转达用户选择「授权有界取证」：仅 B2 只读检查本机 Maven/JDK/settings、rc/.mvn、相关环境与路径权限。候选 baseline 必须先经独立 Reviewer 审定；来源及本节设计获批准后，才可在运行前登记 canonical root/launcher/JDK、三条精确离线 argv 和新专属绝对输出目录。不得联网、修改已有权限、清旧输出、运行 prepare/verify/full 或 commit。原始内容仅本地私有保存，不回显；新目录 0700、文件 0600，不以此 chmod 已有路径。来源不符、离线缺依赖或工具权限拒绝时停止，不另路绕过。技术批准不代替工具许可。

正式入口与 fixture seam 分离，但沿用现有 `ReactorEvidence`：正式入口固定 `system_root=/`，不接受 caller runner、来源 baseline 或 settings identity 覆盖；仅从独立受审精确记录取 baseline。隔离 mock 可显式注入这些依赖，但不得发布宿主 provenance。候选来源逐项记录 Maven/JDK 的普通文件 SHA、精确成员、链接及实际 launcher/java 路径链；目录 mode/uid/gid、可执行性和其他现场权限谓词单列，不混成内容 SHA，也不宣称不可变或 TOCTOU 安全。既知 settings/logger/m2.conf/toolchains/launcher SHA 必须先匹配，未知项只成为待审 candidate，不动态学习允许清单。已有本地安装及历史静态摘要是来源依据，不是官方发行版认证；若需下载或外部认证，另报缺口。

输出合同：由正式入口独占创建本轮新目录，先验证既有父路径与无链接，再以创建 mode 写私有文件；既有目录、同名文件、非私有现场谓词均失败，不修复权限或覆盖旧 probe。三个命令保持既定顺序、flags、cwd 与环境，`argv[0]=mvn` 和 resolved launcher 分字段保存。effective-pom 的 `-Doutput` 必须为该目录下预登记的唯一绝对路径；仅这项有限动态路径取代原固定 `.harness/runtime/effective-pom.xml`，禁止通用 output 参数。evaluate stdout 采用独立严格单路径语法，不再以任意 `strip` 放宽；stderr/exit/文件缺失均按精确合同失败关闭，错误不回显内容。

统一有限 provenance schema：保留 canonicalRoot/cwd、实际 argv、resolved Maven/JDK、settings/source/environment 摘要、缺席来源和本轮私有输出身份；对齐现有 host 的九个拒绝注入变量及 HOME/PATH/JAVA_HOME 绑定，不将 caller `sourceReviewed` 当现场证明。三命令前后、跨命令以及消费前均重验来源；settings 实际 bytes 必须在同次有限语义与整文件 SHA 关联中使用。正式入口自行生成 raw oracle，调用 bound effective model 与完整 active stdout 公共 consumer；不接受 caller 展示表，不优化掉各公共 consumer 的 raw 重读。

成功摘要只并入原 reactor manifest，记录三 argv/输入输出摘要及固定 verify invocation；不新增 journal、跨轮缓存或恢复链。现有 `prepare` 的 clear 必须后移到整批来源、三命令及全部 reader 成功之后；任意失败应证明 clear=0 且 verify=0。当前有界取证不授权实际接入 prepare 或删除原报告/JAR：可先做隔离 mock 的顺序测试和真实 collect-only；正式 shell verify 实际调用的绑定若需修改 `scripts/verify.sh`，须先集中审定最窄范围，不把预定 argv 冒称实际执行。B3 的生产输出清理和产物鲜度义务不被此接线取代。

本次取证尝试准备私有新目录 `.harness/t11/coverage-b2-source-candidate-attempt1/`，但执行采集脚本时工具明确拒绝 actual settings 复制到该 repository destination，要求直接用户许可；命令未获执行，未形成候选 baseline、settings 副本或来源快照。未换工具、未调整权限、未尝试其他来源读取来绕过。协调方随后转达用户已直接选择「授权指定来源与目标」，明确点名该 settings 来源与私有副本目标；据此仅恢复原入口一次，命令包含目标不存在/非链接前置检查。工具再次以相同 Sensitive-Source Provenance 理由拒绝，仍不接受转达确认；整条命令未执行，不能将前置检查冒称实际通过。第二次拒绝后不再重试、不换入口或工具、不让 peer 代跑。需权限系统接受执行本入口的直接确认，而非再次转述批准。随后用户亲自执行原脚本，归档 `.harness/t11/coverage-b2-source-candidate-attempt1/` 实际窗口为 `2026-11-03T09:57:01.567874+00:00`–`2026-11-03T09:57:04.520664+00:00`；不是代理获准重试或绕过前两次拒绝。只读复核确认五文件齐全，目录 0700/全部文件 0600，双来源快照相等，五个已知摘要全匹配，76 个 Maven 普通文件/336 个 JDK 普通文件，缺席来源均缺席。实际 settings 私有副本的既知 SHA 与现有有限语义校验通过，不输出身份/URL/原文；Ruby 的 `/opt/homebrew/opt` world-writable PATH 警告保留。

候选有 211 个 nonregular 成员，全部为 JDK symlink，均解析到同一 canonical JDK 内现存普通文件，无悬空或越 JDK 目标；Maven 无 nonregular。现有 snapshot guard 对所有 symlink 拒绝，故不能直接把 candidate 交给现代码当生产 baseline。待 Review 的最窄建议是仅允许独立基线逐项固定的 JDK 文件链接（精确相对路径/链接文本/canonical 目标/目标 SHA），拒绝目录链接、链循环、悬空、越 JDK、新增或变更链接；launcher/java 本体与生产根/输出链仍禁止链接。此为待批准合同，不自动放宽现有拒绝。若不批准此形状，应停并另选受审 JDK，不改文件或权限。实际 PATH 的部分目录链含 group/world-writable 谓词，须独立风险判断，不能用 launcher/JDK 内容 SHA 消除此事实。来源依据仍只是本地既有安装与设计中少量既审摘要，缺少独立官方发行版/JDK 来源认证；candidate 未自批，三 Maven 命令仍未执行。

#### B2 集中 Revise 修订：执行信任根、受控 PATH 与输出字节合同

原 Reviewer `ab6d8fd96b16acbd7` 对 scope SHA `74cccc46c95ecf63892f635c67910ff5272f71ec1fbcd9e4d12deedb464dc226` 给出 Revise：F1 执行信任根未明确，F2 launcher 的 PATH helper 旁路，F3 evaluate/effective 输出语法未定。原件后置归档 `.harness/t11/coverage-b2-source-design-review-attempt1.json`，不改写成历史通过。

**F1：显式本地执行信任。** 用户随后选择接受 candidate SHA `05c9eec880b19fe7560ef11d256a54327c7cfcd4223c5755562018658e0260fd` 对应的现有 Maven/JDK 为执行信任根，并信任当前 host OS 与同 UID；仅用于精确一致性/变化检测，不是官方来源认证。用户同时选择设计受控执行 PATH，不接受原可写 PATH 风险、不修改全局权限。本地信任决定不将候选自动变成实现 Approved；本节和精确来源仍待同 Reviewer 集中复核。JDK 例外进一步限定为候选精确列举的 `legal/` 下 211 个文件链接，链接文本与 canonical 内部目标逐项固定，目标 SHA 显式引用同份 members；整条目标解析链不得经过目录链接、循环、悬空或越 JDK。其余新增链接仍拒绝，launcher/java/root/POM/output 不适用该例外。

**F2：固定本平台执行环境。** 已只读检查已知 SHA 的 `bin/mvn` 第 30–202 行：无 rc、无 launcher 链接、显式 JAVA_HOME 且只允许 Darwin 的分支下，外部 helper 为 `uname` 和 `dirname`；`-f pom.xml` 的 find_file_argument_basedir 也调用 dirname。`pwd/cd/echo/test/exec` 是受信 `/bin/sh` 内建。`ls/expr` 仅 launcher symlink 循环可达，`cygpath` 仅 CYGWIN/MINGW 分支可达，`tr` 仅存在 `.mvn/jvm.config` 时可达；前置检查排除这些条件，不能因此删去原拒绝。未知 OS、缺失 JAVA_HOME、出现 rc/.mvn 或 launcher 链接均在命令前停止。限定当前 Darwin，不实现通用 shell 解释器或逐 JAR 逆向。

进程内显式构造执行 PATH 为 `<受审MavenHome>/bin:<受审JdkHome>/bin:/usr/bin:/bin:/usr/sbin:/sbin`；不继承原 PATH，不以清洗冒称原环境未改变。保留原 PATH 的有无/摘要及拒绝采用原因，另记录执行 PATH 的确切组成/摘要/解析结果。模型三命令及后续固定 `mvn -q verify` 必须使用同一个执行环境 schema，实际 argv[0] 仍为 `mvn`，resolved launcher 另列。前置检查按该 PATH 的首个普通可执行候选解析 mvn、uname、dirname，分别要求精确受审 launcher、`/usr/bin/uname`、`/usr/bin/dirname`；同时记录 `/bin/sh` 和实际 java 链。必要路径各级 group/world-writable、未知影子或链接失败关闭（系统 `/bin` 等如实际为系统受信链接，须精确列举并 Review，不能自动豁免）。OS 系统工具归显式 host OS 信任，不将当前扫描 SHA 宣称官方认证；每次快照仍记录身份/摘要/现场权限分开。不得先执行不受信 uname 再判 OS；OS 条件由宿主受信运行时提供并与已记录平台绑定。确定性 fixtures 覆盖影子helper、前置可写目录、未知分支、执行PATH/verify不同、额外环境注入以及原PATH变更不能静默混入。

该 PATH 足够 launcher 启动，不等于完整 reactor 的 npm 工具已批准：当前 UI exec 需要 npm，固定 PATH 下可能缺失。不得到真实 verify 时回退原 PATH；若现有模型要求的非 OS 构建工具不在固定路径，须在执行前集中补充受审工具位置与同一环境的精确 PATH、来源和 fixtures，再获设计复核。当前不启动 verify，也不以 B2 来源批准跳过此缺口。新增环境 schema 同时保留原始来源摘要与 explicit execution environment，九个注入变量的缺席要求不变；不可利用构造环境掩盖原输入违规。

Node/npm 有限补充：用户已接受候选 SHA `0e223c5ff1e894ac60540c90b339c84385f4afa2ea56641daa45e92712a9e038` 对应本地 v20.19.5 的 node binary 与 npm 自身精确安装成员为执行信任根，仅作一致性检查，不是官方认证。该候选独立来源 Review 已获原 Reviewer `ab6d8fd96b16acbd7` Approved/findings none，后置归档 `.harness/t11/coverage-b2-node-npm-source-review-attempt1.json`；Reviewer 亲验 2384 成员及权限链、首候选解析，补 `/usr/bin/env` 的 host OS 信任/变化检测身份。此结论仅来源，不是 B2 实施 Accept，实际执行仍为零；正式基线应按本节独立受审记录合同持久保存，不以 job scratch 为长期唯一依赖，也不将机器绝对来源内容提交 Git。来源 Approved 后，在受审 Maven/JDK bin 之后、OS dirs 之前加入该精确 Node 安装 bin；三条模型命令与后续固定 verify 使用同一 PATH/schema。npm 的精确入口链接与 `#!/usr/bin/env node` 解析须分别绑定：npm canonical CLI 必须在同份受审成员中，`/usr/bin/env` 按 host OS 信任，node 按受审 binary 首个候选解析；不推广为 JDK/legal 或 Maven launcher 的通用链接许可。不运行 node/npm 取证，不下载、不读秘密配置、不改已有权限；不增加真实 verify/清理/联网/提交许可。

B2 最窄 shell 接线范围经协调方确认已包含在原委托：只改 `scripts/verify.sh` 的 `run_full` 现有删除/prepare/Maven 顺序及环境绑定，并加隔离 shell mock；不真实执行入口、不改清理 allowlist 或其他门禁语义。正式 collect 与所有 reader/source proof 成功后才允许旧 manifest/summary 删除、prepare；verify 通过同一 proof/显式环境并关联原 manifest。固定本地受控记录为 `.harness/t11/coverage-b2-approved-sources-v1/`，两候选逐字节副本及 `aggregate.json`（SHA `f5c90e294afb6c5979be38cd1ea7a6b7c7192a38ee5358f74a9b1106ed76b74c`）仅本地忽略保存，目录 0700/文件 0600，不复制 settings。该聚合身份随 B2 整批 Review，未获实施 Accept 前不执行真实 runner。既有 readiness 的工具检查不因此自动成为受控来源证明，原 PATH 工具执行边界尚须统一确认。

#### B2 原始环境跨进程绑定补充（待限定 Review，未实施）

当前中间代码将 `model-attest` 输出 eval 到父 shell，后续重验因此误把受控 PATH 当原始 PATH；这是实现缺口，不是授权放宽。限定设计 Review 曾返回 Revise；现采用 Reviewer 收敛的更小方案：**父环境保持不变，仅子执行环境受控，现 verify shell 仍是唯一锁/报告/取消 owner**。撤销环境 eval，不引入常驻 worker、匿名 pipe 协议、RPC、journal 或已证明 marker。顶层只通过固定 `/usr/bin/ruby` 启动 source-only，自行读取继承的 original ENV 和固定受审来源记录；只返回有限 schema 的身份摘要，不返回可执行 shell 文本，不接受 caller baseline/proof。原始环境摘要仅用于同次一致性比较，不授予来源信任，也不承诺抵御已受信同 UID 任意进程。

父 shell 与 dispatch 不 export 受控 PATH、不 unset 原 ENV，也不导入任何子阶段环境。`run_full` 的 attest/collect/prepare/verify 均从这一未改变的原环境以固定 `/usr/bin/ruby` 启动；每次自行读取固定 baseline、现场来源和原始 HOME/JAVA_HOME/PATH 的有无/摘要，拒绝九个注入变量，构造同一显式执行环境。各 Ruby 调用在入口捕获 original 身份，内部前后检查均与其比较；shell 另与顶层保存的摘要比较，拒绝跨调用漂移，不能用摘要相等替代各入口自行验证。collection 及既有私有输出可被后续入口重新读取，但调用者提供的 proof、环境或已通过标记不构成授权；prepare/verify 必须自行重读来源、输出和所有 binding，沿既有严格 reader 合同验证。

shell 按 source-only → 受控 readiness → 原 clean/selftests → collect 三命令及全部 reader → proof → prepare → verify → 原 HA/报告的顺序驱动。readiness/clean/selftests 及后置工具只在有限显式环境的子 shell 执行；子 shell 内不以 execution ENV 调用 original attestation，必要重验回到外层未变环境发起。模型三命令及固定 verify 由各自 Ruby 调用仅对子 Maven 使用显式 env 与 `unsetenv_others: true`，schema/PATH 与 collection 绑定一致，不继承 Harness 控制变量。保留父 ENV 不意味着允许原 PATH 找工具：Git、时间、报告辅助及 Ruby 内部 Git 调用均须固定 OS 路径或在受控子执行边界运行，不能只修 run_full 而留下入口/后置旁路。未知 rg/Docker 在首个工具执行前失败关闭，不以 mock 或缺工具当 passing。

阶段/owner 固定如下，不复制第二套锁或报告所有权；Harness控制状态不进入构建环境。

| 阶段/事实 | 唯一owner与传递边界 | 失败/取消与释放义务 |
| --- | --- | --- |
| root/mode/command 与 original 身份 | shell 自行 canonical root、固定 full/verification command；各 Ruby 入口独立验证 root/schema 并捕获继承的 original ENV；shell 只保留摘要供比较 | source-only 失败不启动版本/Git/rg 等工具，不采集 collection；不得 caller 覆盖 |
| 锁 owner-pid/释放方 | 仍由 verify 父 shell 的 `harness_execution_lock` 创建，owner-pid 为该 shell `$$`；仅其 EXIT 释放函数删除本轮锁 | Ruby/子 shell 不抢锁或释放锁；父 shell 必须等阶段子孙退出且回收责任完成才 EXIT |
| 开始 epoch/HEAD/fingerprint、incomplete | source 通过后 shell 沿既有逻辑生成 started_at/HEAD/fingerprint，写 full incomplete；辅助工具固定 OS 路径或受控执行 | 无安全工具入口则在此前失败，不伪造 HEAD/新鲜报告；已有 incomplete 时取消不得发布 passed |
| readiness/clean/selftests | shell 持有门禁状态，只对子阶段设置有限环境，返回后外层 original ENV 不变 | 任一步非零保留原失败，零 collect/clear/verify；不得跳过缺工具 |
| collection/reader/proof、reactor identity | 各 Ruby 入口独立读固定来源和本轮输出，完整消费 reader/binding；prepare 写原 reactor manifest，shell 固定本轮 manifest SHA | reader/proof 成功前不删除旧 manifest/summary、不 clear；后续身份变化失败，不以 caller proof 或 marker 放行 |
| gate/finalization 与 exit_code | verify 父 shell 保持原 gate_status/finalization_status 和 exit_code；Ruby 只返回阶段结果，不发布独立 gate 报告 | gate 结束先发布 incomplete 并记录实际 gate_status；report/handoff 后才发布最终 passed/failed。非零 gate 优先，否则 report 非零优先，再否则 handoff 非零；最后仓库/manifest 漂移仍可拒绝成功，不能抹去已有失败 |
| HA manifest/SHA | shell 固定本轮 reactor manifest 路径/SHA，经 `ROUTING_HA_REACTOR_EVIDENCE`/`ROUTING_HA_REACTOR_SHA256` 仅传 HA 阶段 | 两项只作 HA 阶段控制，不注入模型 Maven/verify；HA 身份检查及真实拓扑义务保持 |
| INT/TERM、dispatch 进程组与 Maven 子孙 | shell 是唯一取消 owner，沿现有 trap 向 dispatch PGID 发 TERM；受控子 shell、Ruby/Open3、Maven/Java 子孙均保持该组，不 setsid、不另建进程组或遗留后台 | Ruby 的取消路径须停止派生、关闭管道、等待并回收其直接子进程，派生链须等待各自后代；父 shell 不能仅 wait dispatch 就提前释锁。必须确认阶段子孙退出/回收完成；INT=130、TERM=143，已有 full 保留 incomplete |

重验点固定为：source-only 完成后、readiness 后、clean/selftests 后、每条 Maven 前后、所有 reader 消费前后、clear 前、verify 前后。外层各入口从未改变的 original ENV 自行重验，比较入口捕获身份、显式 environment/schema 与现场 source/权限/首候选，并与顶层摘要核对；不得把受控子 ENV 重建成 original。clear 前或 verify 前漂移均失败关闭；若清理已经发生而 verify 后漂移，保留实际清理事实和失败，不伪称 zero-clear。report/handoff 及最终 HEAD/指纹仍由 shell 按原顺序处理。

静态可行性依据：现 `verify.sh` 已由父 shell 创建锁、记录开始状态，再以独立 dispatch 进程组运行阶段，最后处理 gate/report/handoff；因此无需转移生命周期所有权。待改点仅为撤销顶层 eval、将执行环境限于子阶段、封闭原 PATH 的工具查找及补齐取消等待。`harness-evidence.sh` 的 bare Git/Ruby/Open3 Git 查找和 `verify.sh` 的 date/报告辅助也属于该边界，不能假定现代码已安全。现 `Open3.capture3` 和 verify 的 `Process.spawn` 尚无完整取消回收证明；不能以现有 trap 的单次 wait 宣称子孙已回收。这些是实施及 fixture 的待证义务，不在本次设计修订中实现。

先行 fixture 必须真实使用不同的 original/execution PATH：受控子阶段前后，外层 Ruby 均观察原 PATH，而 Maven 替身只观察受控 PATH；不得 fake attest 输出 `:`。还须覆盖九注入变量拒绝、caller baseline/proof 不授信、source 失败零工具、readiness 失败零 collect/clear/verify、各重验点漂移、原 PATH 影子工具不执行，以及 gate/report/handoff 的退出优先级。取消 fixture 让 Open3/Maven 替身产生可观测子孙，分别发送 INT/TERM，证明 130/143、incomplete、锁在子孙存活时不释放且回收后才释放。合法 fixture 只证明合成串联，不证明宿主工具来源获准。此补充待一次限定设计复核，未据此更改实现；不增加真实门禁、三 Maven 或清理许可。

#### B2 生产编排隔离 fixture 接缝补充（限定条件批准，实施待验证）

完整中间实现 Review 为 Revise；原 callback lifecycle/fake helper 只证明合成模拟，不能作为生产 collect/reader/clear/verify 贯通证据。最小补充拟将 CLI 的 prepare/verify 分支分别提取为有实际 CLI caller 的 `prepare_approved_model(collection_file, manifest, summary)`、`verify_approved_model(collection_file, manifest)` 实例方法；不新增 caller runner/baseline/environment 参数。现 `read_approved_collection` 的完整校验体提取为私有共享核心，正式公开入口仍先执行原正式入口限制，再调用该核心；fixture 不以 flag 自证来源，不放宽任何正式 host 入口。

fixture 仅在新私有合成 root 复制生产 helper，并在测试进程中加载类而不运行正式 CLI。只替换固定来源记录/宿主现场解析接缝，使用合成 executable 经生产 `run_model_command` 执行；不得替换 collect、完整 reader/binding、prepare/verify 编排或 clear。隔离结果明确没有 host 来源证明，`hostProvenance=false`/`bootstrapComplete=false` 仅为拒绝晋级的标签，不是可由 caller 切换为可信的开关；正式入口必须拒绝此隔离结果。测试进程中的有限适配只将隔离结果交共享校验核心，不绕开 reader 或来源重验。正例构造合法完整 effective XML、严格 evaluate bytes、完整 active stdout，经过生产 collect→消费→prepare→verify；负例在输出/来源/binding 漂移时调用同一生产 prepare/verify 编排，断言漂移在 clear 前失败，旧 manifest/summary/report/JAR 逐字节保留且 zero verify。仅 standalone 私有 reader 通过不满足本合同；clear 后实际漂移仍记录真实失败，不冒称 zero-clear。协调方已转达原 Reviewer 的限定条件批准，可据此实施，不增加真实 Maven/容器或清理许可。测试专属子类仅覆来源授权解析、最终分类降为 false/false，以及严格要求 false/false 后委托同一私有完整校验的公开 reader；不得覆写执行器、消费、清理或 prepare/verify 核心。collector 必须真实执行三条固定 argv 的合成 executable 并产生独立 literal 输出，不能手写 collection。clear 前及 verify 前后重验和执行器留在生产方法内；CLI 不可选择 fixture 对象、模式、baseline 或 runner。最少覆盖合法贯通、消费中/clear 前/verify 前来源或输出/binding 漂移、成功清理不越界、manifest 关联变更、verify 后漂移的真实已清理事实、false 结果被正式 reader/CLI 拒绝及伪 true 现场/绑定不符拒绝。合成来源授权层另允许无参数 `approved_settings_identity_sha`：生产恒返原固定 SHA，由 collector/consumer 实际调用，无配置、ENV 或 CLI 覆盖；测试专属子类仅返回独立 literal 合成 settings 的 identity SHA，不以生产返回值反造 expected。settings 语法、实际 bytes SHA、identity 计算、前后来源检查、完整 reader/binding 均不可替换。不声称抵御同进程任意方法覆写；正式标记限制与固定 baseline/现场/输出/reader/binding 校验必须同时存在。

#### B2 验收合同集中修订（用户已确认；实施与选择观测另审）

用户随后经集中选择明确「批准集中修订」：接受下述精确本机安装及相关安装管理者/共享写入主体为本轮信任根，接受列明非保证；本合同及所属规范从该决定向前适用，不追改历史。授权仅包括合同、规范同步、最小实现、隔离测试及独立 Review，不包括真实工具执行、插件选择观测、清理或 full。五脚本原有限 Accept 保持原范围，新实现尚待整批验证/独立 Review。下列有界插件选择观测方法仍是待技术 Review 提案，不因合同获确认而自动 Approved；一般「继续」仍不构成额外真实执行许可。旧来源 Revise 在其原合同下仍是事实，不能追改为通过。

**待确认的执行信任根。** 在既有精确 Maven/JDK 与 Node/npm 清单之外，拟接受本机已取证的 rg binary（SHA `468681470b3ed6291dc85691e83c5d2f8fc6c6ec25b164ffa66248713a7e28a3`）、直接 PCRE dylib（SHA `689fff363f155ed88a5b4bbd79c3d4ea5871ac8f5701f7a0e17e22cebebc0adb`）、Docker CLI（SHA `3afd9fef319616a55f4ee837ea6dfa3919f8696254c6a3ea421546dbdc9c1198`）、Compose（SHA `9849a621c88ece3680c45cda968e6bf75fa9dd38b5f93909338457d966cbcb5a`）及其精确安装/链接记录，连同能写入这些已知安装链的安装管理者与共享写入主体，作为本轮本地执行信任根。信任范围仅限逐项固定记录，不推广至整个 Homebrew bin、Docker 插件目录或任意现场候选；不从现场自动学习允许清单。用户须明确接受已记录的共享可写链风险，不能仅接受 binary SHA 却将相应写入主体仍视作已防御攻击者。

本合同不提供官方发行认证、恶意同机/同 UID 隔离、安装不可变性、观察点之间的 TOCTOU 安全或任意供应链闭包保证。现有父链 `022` 拒绝旨在排除其他本地写入者替换工具/依赖；通用动态闭包及精确版本插件源码要求旨在排除未知加载/插件发现路径。若上述本地写入主体已明确列入信任根，则这些额外攻击者保证不再作为本轮默认验收条件，而不是「为通过删除检查」。精确安装路径、已知非 OS 依赖与 links、首候选解析、观察点身份/摘要/权限事实与漂移拒绝仍保留；未知路径、未知替身和变化不能借此获准。敌意共享写入防护、完整动态依赖闭包或精确源码认证若以后成为目标，须另行提出范围与证据，本轮不声称提供。

**保持不变的正确性义务。** 既有 Maven/JDK/Node 成员与允许 links、固定 OS helper/正式入口、独立批准 baseline、原始 ENV 与子执行 ENV 分离、有限受控 PATH、九个注入变量拒绝、全部来源前后重读和漂移拒绝均保留。正式 CLI 不接 caller runner/baseline，不以 host flag 或 proof SHA 自证可信。三个固定离线模型命令与后续固定 `mvn -q verify` 必须使用同一最终环境，环境/来源变化使整批旧结果失效；严格 effective XML、无宽容 evaluate bytes、完整 active stdout、raw POM/settings/binding reader 不变。来源、三命令与全部 reader 成功前旧 manifest/summary/report/JAR 零删除；clear/verify 前后重验、原 manifest 关联、实际已清理失败事实、父 shell 唯一锁/报告 owner、INT/TERM 子孙回收、退出优先级与 incomplete 语义均保留。输出私有独占创建、无越界覆盖、coverage 完整性/鲜度、真实 reactor/full/HA 验收不因本地工具信任免除，合成 fixture 与限定 Accept 不能替代真实证据。

**Docker 的有限配置与选择证据。** 保留现有两处 `docker compose` config 调用及其 argv/项目目录/输入合同，不偷偷改为独立 Compose。使用新建私有无凭据 config，并绑定实际命令、有限子环境、config bytes、Compose 输入文件与期望精确 CLI/插件身份。候选 config JSON 存在或命令生成 JSON 成功，均不能自证选中了受审插件。拟在获准后执行一次有界选择观测，并用隔离负向干扰验证未知同名候选不能静默替代受审 Compose；观测应能把实际启动的插件身份与本次调用关联，不只相信插件自行打印的声明。观测方法/权限/确定性判据先集中 Review，不新增通用 tracing 框架；若现平台无法取得充分观察，结果必须记为 `unknown` 并保留此验收缺口，不用猜测、strings 或成功输出填补。精确版本源码可以作为补充证明，不再默认要求完整源码审计；直接 Compose 替代仍须另批合同与等价性证据，不能当观察失败后的自动回退。

**未来最窄实现与 fixture 范围。** 确认后仅集中调整 `scripts/lib/harness-reactor-evidence.rb` 的固定工具来源 schema/现场谓词与受控环境边界，以及 `scripts/verify.sh` 必需的有限工具接线；如共享辅助确有调用关系才纳入 `scripts/lib/harness-evidence.sh`。在 `scripts/test-reactor-evidence.sh` / `scripts/test-harness.sh` 增补已知可写链显式接受而未知路径仍拒绝、binary/PCRE/link/环境观察点漂移拒绝、伪 baseline/flag 不授信，以及 Docker 有限 config/选择观测与负向干扰 fixture；保留已 Accept 的 collector/reader/prepare/verify 贯通与六取消覆盖。是否存在其他必要 caller 由实际静态调用点决定，未知影响先报告，不扩成通用工具审计、全局权限改造、Mach-O 修改或 Compose 重构。候选路径/来源私有保存，不将机器绝对清单提交 Git；本节不提前实施上述变更。

**真实验收顺序与许可。** 用户须分别明确确认本节信任范围及接受的非保证，并授权所列真实命令/配置准备/必要选择观测与受限负向干扰；一般「继续」不替代这些决定，也不覆盖权限系统拒绝。实施后先静态/确定性 fixture，再在最终冻结工具与环境运行 readiness 和适用门禁；登记三条真实离线 Maven argv 与新独占输出目录，整批采集并完整 reader/来源关联通过后，才在另有许可时运行 prepare/固定 verify、真实 coverage/full/HA 与既有最终验收。任何早层失败先修复或准确归因，不用后层通过覆盖；按仓库当前 full 同轮证据规则避免无意义重复，不把当前工具准备当验收。本节不新增当前执行、清理或提交许可。

**历史与安全边界。** 原 Revise、限定 Accept、失败批次、settings 权限拒绝、rg/Docker 官方 WebFetch 网络安全拒绝，以及 Reviewer 被拒后错误改用 curl、随后停止且未取得内容的事实全部保留。不能把无内容的尝试当源码证据，不能以新合同重试/换入口/域名/peer 绕过拒绝。既有本地 0600 副本及 candidate config 仍只是候选，不自动晋级。当前 B2 未验收、`bootstrapComplete=false`、T11 唯一 active、不推进 B3；待一次集中用户确认，不再开逐项来源审计。

##### 两处纯 config 固定 Compose 入口（用户已选择；后续合同收敛见下）

**2026-11-04 合同收敛决定（用户明确批准，待独立设计 Review）。** 两处纯 config 自测以精确批准的 standalone Compose 为唯一解析入口，不再以与原 Docker CLI 等价作为验收目标，不运行未知插件来补等价证明。理由是这两处只验证既有 Compose 文件的配置结构，移除插件发现层后，应验收确定的解析器与输入合同，而非另一个入口的行为；部署/HA Docker 入口不变，不能据此推导其插件身份或兼容性。下文原「CLI 差分待证」保留为历史未完成事实，不再作为此收敛合同的退出条件；不把历史缺口改写为已实测。

新合同继续要求精确 executable 身份与来源重验、固定 argv/文件及 profile 顺序、仓库 cwd、批准 HOME 与有限环境、配置/YAML/dotenv 已绑定观察点、私有权限、前后漂移拒绝、实际 JSON 结构断言及失败传播。现有两处 dotenv 观察不等于完整选择闭包；必须独立审查 COMPOSE_* 等环境/文件输入能否使实际解析跳出批准输入。若现有限环境与绑定不足，须先登记精确缺口、修订最小实现与确定性负例，再取得相应实际证据；不得以用户取消 CLI 等价目标豁免此安全检查。不动态接受任意当前输出、不删除现有保护、不新增通用 tracing/缓存框架。

**2026-11-04 输入闭包修订（用户明确批准，独立设计 Review Accept；实施与新合同实测待验收）。** 两处结构自测禁用自动 dotenv，固定在现有 `-f`/profile/config 参数前加入 `--env-file /dev/null`；这是有意改变这两处自测的默认 dotenv 插值语义，不改变部署/HA。子进程仍 unsetenv_others，仅传既有 HOME/PATH/DOCKER_CONFIG，caller COMPOSE_* 不继承；不额外引入 COMPOSE_* 开关。`/dev/null` 必须是宿主固定的非链接 character device，不允许 caller 替代；精确 Compose 对此参数确实不再搜索自动 dotenv 的行为须在最小新合同实测中验证，不能由 fake 自证。

YAML 采用固定已审 bytes 准入，而非实现通用 YAML/依赖图解析器：`deploy/local/compose.yml` SHA256=`4e7e6a7d84750a985eba9ead7e480104d73d8fae21e67c2967d59956c93084c7`，`deploy/local/compose.load-test.yml` SHA256=`518a9670d12094827ea6f9dde391b85263e72740bae94b3dc7c1a8279233bd43`。审查当前两个文件没有 include、extends、env_file、外部 secrets/configs 文件等 config 阶段额外内容读取声明；volume/build/Dockerfile 路径仅在此 `config --format json` 合同下作为配置值处理，不声称启动或构建时的文件闭包。生产入口在任何 Compose spawn 前检查所选文件为普通非链接、canonical 精确路径、字节 SHA 与源码内固定表一致；不可由 caller/环境或本次扫描生成批准表。改变 YAML 必须重新审查内容读取形状、更新规格及固定表/独立 fixture。保留原 config/YAML/tool/dotenv 前后漂移拒绝；dotenv 观察只作漂移保护，不再用其值进行本次插值。

最小实现范围仅既有 Ruby helper/self-test、两处 Harness 的相关 argv 断言及 owning 规格/Harness Guide/Matrix/计划/PROGRESS；不修改 Compose YAML，不变更 v2 工具来源信任。回归先 RED，覆盖主 YAML 稳定而指向 include/extends.file/env_file 等外部文件（分别改变外部内容）的输入在启动前拒绝、零 Compose 调用；覆盖任意未批准 YAML、链接、caller COMPOSE_* 不继承及准确空 env-file argv，保留既有后置漂移负例。fixture 可在显式合成 seam 注入自己的已审 bytes 表，但正式 CLI 只用生产固定表，不能由该 seam 晋级正式来源证明。实际新合同无 daemon config 实测须另获精确授权；旧实测只保留旧语义，不刷新其时间或指纹。不要求重跑父链、真实三采或 full。

退出须经独立设计 Review 确认当前明确输入边界和残余风险，再评估既有 standalone/helper local/load 实测及隔离正反例是否足够；不充分则补缺，不自动重跑父链/三采。此次只批准合同收敛及审查，不授权新增真实工具执行、来源扫描、配置读取、网络、daemon、清理或 full；B2 保持未验收，整体 Revise 在收尾前可见。

用户集中选择「固定 Compose 直调」：仅 `test-local-compose-harness.sh` 与 `test-load-test-harness.sh` 的两处纯 config 自测移除 Docker CLI 插件发现层，改用固定批准 Compose canonical 入口。保留文件与 profile 顺序、cwd/project、插值/path 解析、JSON 类型/数组和错误传播断言；部署/HA Docker 命令不变，不能声称本选择解决 HA 插件身份。取消此前 OS tracing/转发/观测目标，不实现或申请该机制。用户仅授权最小实现与隔离测试，未授权真实 config/工具执行；后续受控 PATH 仅加入仓库受审 `scripts/bin` 的 rg/docker 两薄 exec 源码入口，由固定 Ruby/v2 baseline 解析并在 exec 前重验，不复制 binary、不采用 caller target/原 PATH，不改变部署/HA argv。两个入口源码摘要纳入 v2 与观察点一致性。隔离 fake 只证明固定 argv/环境/cwd/失败传播，不证明实际 standalone 协议或真实与 Docker CLI 等价。后续集中申请无 daemon 的真实 config 差分执行，未实测明确待证。新入口不可由 caller PATH/ENV 任意替换，未知来源拒绝；既有 v1/Review 保留，v2 显式新版本。

固定 config 的 HOME 来自批准 Maven/JDK layout，caller HOME 不匹配即拒绝。cwd 固定仓库根目录；调用前后绑定已知的 cwd `.env` 与首份 Compose 文件所在目录 `deploy/local/.env`，同路径去重。记录存在/缺失、普通文件身份、canonical、mode/uid/gid 与 bytes SHA；链接拒绝，正文不写入日志或证据。不传空 env-file，不解析 dotenv，不改变其默认插值语义。这两处观察点不构成完整 dotenv 搜索或输入闭包；dotenv 中的 `COMPOSE_FILE`、`COMPOSE_ENV_FILES`、`COMPOSE_DISABLE_ENV_FILE` 或项目目录设置可能影响实际选择，不能据此声称已排除。完整选择边界及 standalone 差分仍未实测、未验收。生产核心隔离测试须由实际子进程分别修改 config、YAML、工具和 dotenv，证明后置拒绝，并覆盖 HOME 不匹配、dotenv 缺失转出现及正文变化；这些 fake 不替代真实兼容性证据。

##### 最窄实施与选择观测提案（已由上述用户选择取代，保留历史）

来源实现拟固定新增 `.harness/t11/coverage-b2-approved-sources-v2/`，新 aggregate schema/version 及其 bytes SHA 显式固定；v1/候选/Review 原件不覆盖。v2 引用既有 Maven/JDK/Node 逐字节清单及两份已取证工具/PCRE 记录，不新扫描或读取来源生成允许项。权限例外按 record 中精确路径、uid/gid/mode、类型、链接文本/canonical 目标逐项匹配；未列路径仍按原安全谓词拒绝，文件 SHA 和前后快照一致性独立验证。只有已有记录覆盖的安装链可例外，记录缺口失败关闭，不动态学习、不全局删除 `0022`，私有 output/approved-record 0700/0600 检查完全不动。受控 PATH 不加入整个 Homebrew；rg/Docker 仅拟以已审 canonical 绝对入口调用现有专用工具位置，实际 caller 接线范围先静态核对，不能把包含未知 sibling 的原 bin 直接并入 PATH。Compose 选择未证明前，full attestation 仍失败关闭。

选择观测最小候选为一次受限的宿主进程 exec 事件观察：只关联该次已登记 Docker PID/子孙、记录实际 executable canonical 身份与本次 Compose argv/config 摘要，不采集其他进程或凭据。必须先确认平台存在获准、无需改变全局安全设置的可靠原生事件接口；本阶段不运行探测或实现 observer。以隔离合成 CLI/插件产生可控 exec 事件，正例关联精确插件，负例为私有干扰目录中的不同身份同名插件；若无法可靠关联、事件缺失/丢失、观察需要额外权限或命令启动前无法确保观察就绪，统一返回 unknown 并阻断。实际负向干扰不触碰系统/用户插件目录，只在本次私有配置/环境构造；真实执行及观测另需许可。此方案不以 wrapper 自述/JSON 输出/metadata 字段代替 OS 层实际启动身份，也不预承诺 Darwin 当前可用。若平台证据不足，保留 unknown，不引入通用 tracing 框架或偷偷切独立 Compose；技术 Reviewer 可直接判本候选不可用并保留缺口。

#### B2 有界工具准备补充（候选准备设计，待集中 Review）

本阶段仅准备保持现有 `docker compose` 合同的工具来源与安全落点，不修改已 Accept 的五脚本或 fixture 接缝，不改 Compose 调用为独立命令。协调方转达用户选择「授权有界工具准备」：可查询官方来源并下载适用 rg 构建、复制已取证 Docker CLI/Compose 精确 binary 为新私有普通文件、生成无凭据隔离配置。工具权限仍以实际权限系统为准；被拒即原路停止，不换入口。readiness 仍因 rg/Docker 来源与落点未闭合而不执行，不回退原 PATH，不将来源准备计为 readiness/B2/full 通过。

Docker CLI 原件只接受 SHA `3afd9fef319616a55f4ee837ea6dfa3919f8696254c6a3ea421546dbdc9c1198`，Compose 原件只接受 SHA `9849a621c88ece3680c45cda968e6bf75fa9dd38b5f93909338457d966cbcb5a`。复制前重验已取证 lexical/canonical 身份、普通文件、SHA、权限与直接依赖；新副本 SHA 必须逐字节一致，原件及原父目录权限不变。不读取用户 Docker config、credential helper 或登录凭据，不继承其配置目录。新配置仅包含经精确版本官方源码核验所需的插件目录项，无 auths、credsStore、credHelpers 或远端 context；不以 strings 证明插件优先级。须从现有静态身份材料定位 CLI/Compose 对应版本及官方源码 revision，核对插件搜索顺序、同名冲突、默认系统目录、metadata 子进程及配置路径选择；无法排除未知插件/配置时失败关闭。不得执行候选 `version`、`config`、metadata、rg 或任何新工具来补静态证据。

rg 优先查询项目官方 release 的 macOS arm64 构建及其发布校验依据，目标是不加载当前可写 Homebrew PCRE 链。构建名和下载地址须由实际官方记录确认，不预断已有适用产物。下载每件放入各自新建空目录；保存官方 release/校验依据、精确 URL、获取时间、实际 bytes SHA 及来源关系。SHA 一致只证明与该记录一致，不称官方认证；若无独立发布校验依据则显式记录缺口，不自制 expected。下载/解包内容不执行；解包前拒绝绝对路径、`..`、链接、设备与额外未知成员，仅将批准成员作为新普通文件落地。用固定宿主工具或隔离解析器静态核对实际 Mach-O 架构、全部直接 load commands/rpath、依赖与权限链；发现未知非 OS 依赖则停止，不改 Mach-O、不临时拼接库搜索路径。

拟新增文件仅在一个尚不存在的独占私有候选根内：`downloads/<item>/` 各件原始下载与校验资料、`unpacked/<item>/` 经成员校验的新普通文件、`tools/bin/docker`、`tools/cli-plugins/docker-compose`、待定 `tools/bin/rg`、`docker-config/config.json`、`evidence/` 下来源/依赖/权限/官方源码引用清单及总 manifest。根与所有新子目录创建为 0700，所有新文件先创建为 0600，使用 exclusive create，不覆盖旧输出、不链接原 executable。准备脚本放在下载/解包目录之外的独立 script 目录，Python 使用 `-I`，不从下载目录启动解释器，不加载其中模块。机器绝对来源清单仅私有保存，不进入 Git 文档。候选根及全部父链须现场核验；私有副本不自动消除父链风险，也不宣称抵御已受信同 UID 任意修改。

当前集中授权对「新副本取证后设置执行位」未逐字明确：本阶段只创建 0600 候选，不 chmod 原件，也不自行将新副本改为 0700。交付时集中请求仅对这三个已取证新副本设置 owner execute 的明确授权；设置权限即使获准也不等于工具执行许可。正式受控 PATH、Docker 隔离配置与插件选择的生产绑定另待候选集中 Review，不先改 source baseline 或已 Accept 实现。查询/下载/复制的实际允许程度服从权限工具；不得借协调消息绕过已有拒绝。

候选一次交付包含新文件完整清单与权限、官方来源/版本源码证据、原件/副本 SHA、Mach-O 依赖与路径链、插件选择论证、未决缺口及明确未执行项。集中 Review 前不运行新工具、真实 Maven/prepare/clear/full，不连接 daemon，不做业务网络调用，不清旧输出或提交。若查询、复制、磁盘写入或权限检查失败，保留实际失败并停止，不另启小切片或静默 cleanup；先完成设计与权限边界交付，再进入受限准备。

**F4：环境变化使证据整体失效。** Reviewer attempt2 仍为 Revise，仅剩本规则；F1/F2/F3 设计缺口关闭及 JDK legal 有限例外原则认可，不是执行许可或 B2 验收。schema、精确 PATH、工具来源或环境身份任一变化，旧三命令结果及其 binding 全部失效，不改旧记录冒称同环境。E0 collect-only 只能提供接缝反馈，不能晋级 E1 正式 manifest。应优先在首次正式采集前冻结包含必要 npm/node 的完整 verify 环境，完成独立复核并取得执行许可，避免有意先采 E0 再重采；不因此扩展为无限依赖审计。若实际发生环境变更，必须在最终同一新环境与新独占输出目录重新执行全部三条固定命令、重跑所有 reader 并重新绑定，之后才可进入 prepare/verify；清理与 verify 权限仍另需，不能由重采自动获得。

**F3：有限输出字节合同。** 不以真实 stdout 样本推断。已核缓存 Help 3.5.1 JAR SHA `db1296f90c93cd1ac763f8262674dc376cf7670166dc2c270a498b1141a51865`。纯 Python classfile 静态提取的 EvaluateMojo class SHA `15bded67cb83b53ae360cfa97ceab0428a9d9142d96e1360dd698fe8a3e36d4f`；handleResponse 的 quiet/forceStdout 分支是 `System.out.print(builder.toString())` 后 `flush()`，不是 println；该局部依据只支持该分支不追加 println，不能保证整命令没有其他输出。File 分支追加 File.getAbsolutePath（不等于 canonicalize），`settings.localRepository` 也可能走 String 分支；canonical 路径必须由独立现场路径验证证明，不从该字节码推导。固定 evaluate 命令的失败关闭合同仍要求 stdout 精确为一个 canonical localRepository 绝对路径的 UTF-8 bytes，**没有终止 LF**；整命令无额外输出是验收限制，不是局部实现保证。不接受可选 LF、CRLF、BOM、ANSI/ESC、NUL/控制字符、前后空白、任何额外行或尾串；允许路径内部普通空格但拒首尾空白及未展开表达式，必须与实际 canonical 目录及 model Mockito 路径绑定。不能 `.strip`，也不把路径猜成默认 `.m2/repository`。stderr 必须零字节，exit 必须 0。

已有 EffectivePomMojo 静态 disassembly 的 execute offset 200–284 表明 output 非空时调用 writeXmlFile，成功仅 Log.info；固定 `-q` 下 INFO 不输出。因此 effective-pom stdout 和 stderr 均必须为零字节，exit0，并存在该次新路径的非空 XML 文件。不得允许“写入成功”INFO、warning、ANSI、空行或任意额外字节。XML bytes 独立 SHA 绑定并交现有 strict formal XML reader，不能以 stdout 空替代模型校验。logger/Jansi/异常环境若仍产出附加 bytes 则失败关闭，不规范化或根据真实样本扩大合同；离线失败即停。active-profiles 完整 stdout 仍按 B1 已批准合同，stderr 零字节。静态依据与 fixture 设计待集中复核，不宣称已有 B2 实现或三命令验收。

### 2. 源码 guard

复用 `scripts/check-java-style.sh` 的 root 参数和 `test-java-style-harness.sh` 的隔离 fixture。新增小型词法扫描，仅提取 package，不引入通用 Java AST：先识别行／块注释、普通字符串、字符字面量与 text block，再识别 token。注释或字符串里的 `package` 不是声明；Unicode escape 可能改变词法结构时保守拒绝该源码形状并要求显式支持，不默读错包。

普通类和 `package-info.java` 必须有唯一自有 package，且与生产 source root 下相对目录一致；default package、lookalike namespace 或路径不匹配拒绝。`module-info.java` 不是可任意跳过的默认包类：本设计的 classpath 构建不支持新增 JPMS production descriptor，发现时失败关闭并要求设计 Review。fixture 明确覆盖此拒绝，不能靠忽略文件绕过所有权。

扫描全部受控普通生产根及本轮 generated production 根；额外 source roots 由配置 guard 拒绝。generated 源的词法与路径检查使用相同规则，不单凭 `target/generated-sources` 的目录名赋予可信身份。

### 2.1 有限插件与 processor 合同

Architecture Surefire 的额外 classpath 以所属 `im-test/im-architecture-test/pom.xml` 为事实源：有序且仅包含根目录下 `im-management/im-admin/target/classes`、`im-management/im-audit/im-audit-server/target/classes`，供检查 Boot 可执行包中的应用字节码。effective reader 对 plugin configuration 与默认 test execution configuration 均精确比较，不接受旧 Broker/Gateway 路径、缺项、额外项、重复项或反序；这不是忽略 classpath 或动态接受任意当前输出。

不审计任意插件行为，只接受当前原始 POM 中已经列明且不改变生产 roots、class 输出及 namespace 的有限形状：默认 lifecycle 的 resources/compiler/test/jar/install/deploy 与根 enforcer、Surefire、JaCoCo；现有 Spring Boot repackage 只包装 JAR，不修改 `target/classes`；四个 management UI 模块的 exec 3.6.3 只允许现有 `prepare-package` 的 `npm ci --prefer-offline --no-audit --no-fund` 与 `npm run build`、工作目录 `<basedir>/ui`，仅向既有 static 资源目的地交付；load-test exec 3.5.0 无绑定 execution、仅 test classpath。实际有效模型须记录默认 lifecycle 插件版本和这些完整 execution/configuration；出现未列明 execution、运行命令、Java 生成、class 写入或 source 注册即停止。不是允许所有 exec、也不是禁止所有 exec；任意脚本恶意写入属供应链边界，不宣称 XML 能证明其内部所有行为。

CLI Shade 仅接受 `im-management/im-ops-cli` 的 `org.apache.maven.plugins:maven-shade-plugin:3.6.0`、package/shade、唯一 `ManifestResourceTransformer`、Main-Class=`com.co.kc.imchat.management.ops.cli.OpsCli` 与现有 finalName。按 plugin 全局和 execution 配置的 Maven 合并语义核验实际 execution：继承的全局 `createDependencyReducedPom=true` 必须被 execution 的 `false` 覆盖，不能只检查其中一处。接受既有 `keepDependenciesWithProvidedScope=true`，以及 artifact=`*:*` 下恰好三个签名资源 exclude：`META-INF/*.SF`、`META-INF/*.DSA`、`META-INF/*.RSA`。这些是打包时移除依赖签名资源，不是覆盖率过滤，不得影响 report 分析集合。拒绝 `.class` 过滤、额外或扩展匹配模式、relocations、minimize、自定义输出、额外 transformer 或改写 classes 的配置。fixture 正例必须使用本次 effective 模型的完整合并形状，包括 provided-scope 保留及三个资源模式；负例分别删除 execution 的 false 覆盖、改变 provided-scope 值、增加/扩大资源模式、加入 `.class` filter、relocation、minimize、输出重定位或另一 transformer，均失败，不以签名资源正例放行任意 filter。

实际有效模型还声明默认 site 3.3、clean 3.4.1、install 3.1.4、deploy 3.1.4；它们的 execution 分别绑定 site/site-deploy、clean、install、deploy，不在本次默认 `verify` 调用执行范围。仅记录这项无 verify 范围绑定事实，不新增运行这些 goal/phase 的许可，也不把插件声明等同于 verify 执行。合同根据 effective execution 的实际 phase/goal 判定；任一上述插件新增绑定到默认 lifecycle 中 verify 及之前的 phase，或 invocation 新增相关 goal，必须拒绝并重新 Review。fixture 正例保留当前默认绑定；负例将 site/clean/install/deploy 任一 execution 重绑定到 compile/package/verify，或增加会执行它的 invocation，均拒绝。

compiler 的 processor path 只接受现有 `org.projectlombok:lombok:1.18.42` 与 `org.mapstruct:mapstruct-processor:1.5.5.Final`；Lombok 可在既有编译单元内生成成员，仍由最终 class 所有权校验。拒绝额外 processor、processor class 显式覆盖、`-s`/`-d` 改写、build-helper/add-source、antrun/其他代码生成、未知 compilerArgs、namespace/implementation 定制和非默认生成目录。已存在参数以有限 key/value 清单明确接受，不用「所有 compilerArgs 都危险」替代盘点。

### 2.2 MapStruct 期待集的有限语法

同一词法器先去除注释、字符串、字符与 text block，再读取 package、import、顶层 interface 与其注解。只将 `@org.mapstruct.Mapper` 或显式 `import org.mapstruct.Mapper;` 绑定的 `@Mapper` 视为生成任务；允许当前明确 imports/uses、unmappedTargetPolicy 等不改输出身份的成员，参数按平衡括号/花括号 token 读取，不实现 Java 表达式求值。支持这两种身份的 fixtures；同名自定义注解不是 MapStruct，注释/字符串里的文本不计数。通配 import、冲突同名声明、嵌套 mapper、abstract-class mapper 或无法唯一绑定的形状停止并要求支持，不能漏掉真实任务。

每个受控顶层接口 `<package>.<Interface>` 的默认期待为同 package 的 `<Interface>Impl.java` 与 `<Interface>Impl.class`，生成源须声明该 class 并 implements 原接口。拒绝 `@Mapper(config=...)`、`@MapperConfig` 关联、`implementationName`、`implementationPackage`、对应 processor options 及未知定制；即便写成默认值也不默许，需要先支持其有限语法。fixture 分别覆盖显式 import、FQ 注解、合法当前 imports/uses、多行参数、注释/字符串伪装、同名自定义注解、冲突/通配 import、缺实现，以及 config/name/package 定制拒绝。期待集不从旧 generated 目录反推，不以目录缺失解释为没有 mapper。

### 3. generated 与 class 的同轮接入

#### HA 现有宿主代理补充（2026-11-06，独立设计 Accept，实施待验收）

用户在明确询问是否将现有 `http://127.0.0.1:7890` 代理纳入正式验证后要求继续。本次决定仅接受该无凭据 loopback HTTP 代理作为 Docker Hub HTTPS 请求的转发入口，不接受任意 caller 代理、远程代理、额外 CA、TLS 跳过或 Docker 配置变更。既有诊断对照 `.harness/t11/advance-20261106T035708Z/` 中三键环境 build 失败，增加该 `HTTPS_PROXY` 后 manifest 与两个本地镜像构建成功；对照不代替本补充的正式验收。

最小实现只在 `scripts/verify.sh` 的 `run_full_controlled` 选择 `run_full_ha` 时，把固定 `HTTPS_PROXY=http://127.0.0.1:7890` 加入该空环境子阶段。正式模型三采、prepare、完整 Maven verify、readiness、clean、self-test、report 与 handoff 仍保持原三键环境；不改 source aggregate、PATH、工具来源或原模型环境摘要。代理不进入应用容器配置，不传入 build args，不接受父 shell 的 HTTPS_PROXY、HTTP_PROXY、ALL_PROXY、NO_PROXY 或 Docker 变量覆盖。该有限补充属于当前已绑定本机来源的 full，不承诺其他机器存在同一代理。

`scripts/test-full-orchestration-harness.sh` 应真实观察 HA 进程的固定代理与其他受控阶段的代理缺席，另以错误父代理证明不继承。代理不可用时真实 build/HA 必须保留原失败；不增加 fallback、缓存验收或跳过网络步骤。独立 Reviewer 先审本设计，再审精确实现与隔离正反例；正式三键加固定代理的窄 build 通过后才能恢复更宽验证。B4 与冻结完整 full/真实 HA、取消协议及完整独立 Review 的剩余义务不变。

独立设计 Review 原件为 `.harness/t11/proxy-admission-20261106/design-review.json`，结论 `Accept`、findings `none`。接受边界为 HA 宿主后代继承代理，不是域名白名单；当前 full 使用同轮 manifest，不在 HA 内重复模型 Maven，内部 HTTP loopback 请求不依赖该 HTTPS 代理。实现与实际执行仍须另行验收。

#### HA Docker Desktop 凭据助手补充（用户确认，设计技术 Accept）

固定三键环境保留用户 HOME，HA/deploy 沿用真实 Docker 配置语义。2026-11-05 新 full 的 HA 已通过 reactor-check、TLS 与 Compose config，实际 compose-up 构建因 `docker-credential-desktop` 不在批准 PATH 失败；只读配置选择观察为 `credsStore=desktop`，未访问或输出 auths 字段值，也未调用助手。该助手不在已有 rg／Docker CLI／Compose 精确信任清单，不能以一般“继续”静默扩大信任。

只新增该精确 Docker Desktop 助手的来源／metadata／SHA 与专用固定 launcher，允许 Docker 经原协议调用它，保留当前用户配置及凭据存储语义；不直接输出凭据、不把 `/usr/local/bin` 或 Docker 安装目录整体加入 PATH，不手改 config 或关闭认证。用户已确认采用此精确范围，独立设计技术 Review Accept，首轮实现 Block 及修正证据保存在 `.harness/t11/docker-credential-source/independent-review.json`。设计批准不代替实现验收或 full／HA 通过。

来源固定为 `/usr/local/bin/docker-credential-desktop` 解析到 `/Applications/Docker.app/Contents/Resources/bin/docker-credential-desktop`，binary SHA256 `5889711e8bb75da8633babca6a012961eb13f9c78c2abb0c19f1d0504c303b8a`。精确 lexical 链接、本体及父链的 mode／uid／gid／link 进入既有 `tools.json`；aggregate 只保留该文件的一个摘要引用，并由 reader 固定 aggregate 摘要，不增加重复来源槽或新框架。已读 Mach-O 依赖均属既有 host OS 信任，签名观察仅作来源事实，不代替内容摘要或官方供应链保证。

`scripts/bin/docker-credential-desktop` 使用既有 `approved_local_tool` 校验来源并 exec；参数、stdin／stdout／stderr 和退出码原样传递，不缓存、记录或解析凭据。隔离 fixture 使用合成 target，覆盖未知助手、canonical／metadata／binary／launcher 漂移和原协议流转发；正式来源 reader 另作只读校验，不能仅凭 override reader 的 GREEN 接受 schema。实际窄验证由 Docker 正常触发助手，不直接调用助手探测凭据；完成独立实现 Review 后才运行。

2026-11-06 监督窄 build 暴露 BuildKit 子进程的 PATH 无法解析 Ruby 初始化所需的 `uname`。最小修复仅将此助手 launcher 改为 `/bin/sh` 入口，在启动 `/usr/bin/ruby` 前固定子进程 `PATH=/usr/bin:/bin`，不扩大 full 或模型环境。Ruby 使用 `-e` 加载既有来源校验器，不能用 heredoc 占用原协议 stdin；仓库根参数只消费一次，其余参数原样 exec 给精确助手。合成 fixture 以缺失 PATH 和错误 PATH 校验空参数、含空格参数、stdin、严格 stdout／stderr 与非零退出码；不得容忍额外 stderr 来绕过失败。来源 aggregate 的 launcher SHA 与 reader 固定摘要同步更新，其他工具及认证配置不变。本补充须独立 Review 后再恢复正式窄 build，不将诊断或局部 GREEN 当作 full／HA 验收。

无凭据的隔离 Docker 配置涉及认证、context 和插件选择语义变化；与当前 HA/deploy 不变合同不一致，本批不采用。若另行选择，须重新审查完整配置与选择边界。当前先完成精确助手的窄验证，再冻结重验 full，不盲重跑。

优先扩展现有 `ReactorEvidence.prepare/validate/check`，不新增 verify hook。`scripts/verify.sh` 的时序仍为 prepare → 一次完整 `mvn -q verify` → reactor verify → HA → check；prepare 内增加前述有效模型采集。

prepare 先完成整批路径校验，再清除每个正式生产模块的以下受控输出：`target/classes`、`target/generated-sources/annotations`、`target/maven-status/maven-compiler-plugin`，以及 `target/jacoco.exec`、`target/site/jacoco`。清除 compiler 状态防止输出缺失但增量编译误判已完成；清除 exec/report 防止累积旧 probe 或旧 XML 冒充本轮。现有必需 XML／exec JAR 删除逻辑保留。新增删除范围是必须 Review 的行为变化，不允许通配删除其他目录、整个 target、未知模块或仓库外路径。

路径必须来自受控有效模型，位于当前模块真实 target 内，不接受任一级 symlink、父目录逃逸或重复指向其他模块。先验证全批再删除，任何未知布局停止且不部分清理。仅清理可重建构建输出，不触及运行数据、容器、volume、Raft 日志或用户 home。prepare 不在 Maven 构建中并行运行；现有冻结与独占要求保留。

validate 在 Maven 成功后执行以下检查：

1. 对有效模型中含普通生产源码的模块，要求非空本轮 `target/classes`；对配置了实际 MapStruct `@Mapper` 生成任务的模块，要求本轮生成实现及对应 class，不能以整个 generated 目录缺失当作「没有生成代码」。生成期待集从受控源码中的 `@Mapper` 接口与有效 processor 配置取得；未知实现命名策略先拒绝。聚合 POM／真正无生产源码模块显式记录为空，不按固定类名例外豁免。
2. 枚举全部生产 `.class`，解析 classfile 的 constant pool 和 `this_class`，检查内部名称及路径一致、自有 namespace、无重复或非法 class。只实现长度受界、tag 完整的 class header 读取，不解析方法体；未知 tag／截断／错误 magic 失败，不用文件路径替代 binary name。generated sources 同时执行 package guard，生成实现必须对应本轮 class。
3. 所有应有输出必须在本轮清除后重新产生；检查存在性、非空、时间窗口并记录路径／SHA256。mtime 只是补充，核心 provenance 是本轮 prepare 已删除受控旧产物，不能仅 touch 旧文件。模块输出在 verify 之后新增、丢失或被替换，会被既有 check 的重新枚举／摘要比较拒绝。
4. 每个有生产 class 且运行了测试 agent 的模块须有本轮 exec 与未过滤 `jacoco.xml`。不要求无测试模块伪造 exec，但要求其生产 class 仍进入所有权 inventory；不能用「无 exec」豁免 class 检查。report 中自有未执行类必须保留 missed 计数，真实 MapStruct 生成实现须有可定位分析记录；不通过 source 列表伪称 report 已包含。
5. JaCoCo 内置 generated-code filter 可能过滤某些方法，不能据此保证所有 generated 方法都有指令计数。实际 MapStruct fixture 应选择当前真实生成实现，检查 report class 记录与源码／class 对应；如果官方分析确实整类消失，先提交设计 Review，不能静默把 generated report 义务降为 Review-only 或修改report过滤。

上述类清单、生成对应关系、report 清单及摘要与原测试/JAR证明一起进入同一 manifest。`check` 重新验证同一集合及摘要，拒绝额外／缺失输出，不新增链式 sidecar 证明。模型输入和 Git-visible 冻结依旧由现有 full 指纹保证。

文本 reader 对 Java 源码、场景 TSV、manifest JSON 与 Surefire／JaCoCo XML 显式使用 UTF-8，不能依赖 full 外层 locale，也不增加 LANG 绕开解析。主模型三键与后续「HA 现有宿主代理补充」的有限阶段环境分别适用。二进制产物和所有摘要仍使用原字节。C locale 独立 CLI 的中文源码／场景／报告 fixture 保护此约定，不改变模型、C/F/R、鲜度或来源验收合同。

2026-11-05 已批准的[synthetic 报告清单补充](2026-11-05-jacoco-synthetic-report-inventory-design.md)精确限定第 3.5 节的整类差异：完整 class inventory 不变，固定 JaCoCo 0.8.13 的 `ACC_SYNTHETIC` class 以 flags／身份／摘要／理由进入同一 manifest 的过滤分类，XML 集合必须等于其余 class。顶层 MapStruct 实现仍必须有 XML 记录；无 counter 不允许 class 缺失。该补充不改变真实同轮验收与运行授权。

### 4. 实际 agent／report smoke

在 architecture 测试侧新增单个 `CoverageInstrumentationTest`，不启动 Spring，不 mock parser。固定执行 `im-common` 的 `com.co.kc.imchat.common.utils.NestedMapUtils.prune`：建立两级 Map、删除唯一二级值后断言一级 key 消失。固定未执行类为同模块 `com.co.kc.imchat.common.utils.HashUtils`，本 smoke 不加载/调用它；未执行性由 exec 清单和 report missed 共同证明，不由假设保证。

固定真实 generated 类为 `im-management/im-ops` 的 `com.co.kc.imchat.management.ops.transformer.interfaces.OpsCliHttpTransformerImpl`。通过公开 `OpsCliHttpTransformer.INSTANCE.submissionResponseFrom(new BrokerOperationDTO("coverage-op", "APPLIED", 1L, null, null, null))` 执行生成映射并核对 operationId/status。源码与当前 generated 源的静态盘点确认默认 InterfaceImpl 形状；当前 target 不是新鲜生成证据。窄 smoke 仅证明实际 agent/probe/report 数据流及所用普通源码、generated 源和 class 的身份/摘要一致，允许 Maven 增量编译复用已有实现，不声称它由本次 smoke 新生成。generated 同轮鲜度仍必须由第 3 节正式 full 的 prepare→verify→check 机械证明，不降为 Review-only，也不为窄 smoke 新增清理。调用 `net.sf.jsqlparser.parser.CCJSqlParserUtil.parse("SELECT 1")`，断言返回 Select 并核对表达式为 1；不只看调用未抛异常。

architecture POM 拟新增 test scope 的 `org.jacoco:org.jacoco.agent:0.8.13:runtime` 与 `org.jacoco:org.jacoco.core:0.8.13`，只供公开 agent API 和 exec 解析，不增加生产依赖。缓存 runtime JAR 的 `RT.getAgent()`、`IAgent.getExecutionData(boolean)` 与 `dump(boolean)` 已静态确认。测试使用 `RT.getAgent().getExecutionData(false)` 交给 `ExecutionDataReader`：NestedMapUtils 和真实 Impl 各有至少一个 true probe；HashUtils 无执行记录；所有 `net/sf/jsqlparser/` class 均无记录。随后 `dump(false)`，不调用 reset、dump(true) 或 getExecutionData(true)，不清空其他覆盖数据。fork 退出后重新读取独享 exec 核对同样条件。

固定窄测试命令从仓库根运行：`mvn -q -pl im-test/im-architecture-test -am -Dtest=CoverageInstrumentationTest -Dsurefire.failIfNoSpecifiedTests=false -DforkCount=1 -DreuseForks=false -Djacoco.destFile=<本轮新建绝对目录>/smoke.exec test`。该新目录和 exec 必须事先不存在；本轮只选这一测试，upstream 不应有匹配测试/fork，若出现其他测试或多个写入 fork 则拒绝本轮证据。新路径不靠删除旧 exec 取得干净状态。不能用 `-DargLine` 替换双 agent。

测试通过 `ManagementFactory.getRuntimeMXBean().getInputArguments()` 核对实际 fork 参数，安全归档两个 agent 项：JaCoCo runtime 0.8.13、destfile 精确本轮 smoke.exec、includes=`com.co.kc.imchat.*`；Mockito 路径必须为有效模型解析出的 `mockito-core-${mockito.version}.jar`。必须恰有这两个 `-javaagent`，保留 UTF-8、headless、Xshare 原参数；不只核对 POM 的 `@{argLine}`。不输出无关 VM 参数或环境值。exec 中 SessionInfo 与该实际 fork 对齐。

architecture fork 的默认 report 只分析 architecture 自己的生产 classes，不会自动分析 im-common/Ops。必须依次将同一个独享 exec 明确交给各 owner 的未过滤 report：

```bash
mvn -q -pl im-common org.jacoco:jacoco-maven-plugin:0.8.13:report -Djacoco.dataFile=<本轮新建绝对目录>/smoke.exec
mvn -q -pl im-management/im-ops org.jacoco:jacoco-maven-plugin:0.8.13:report -Djacoco.dataFile=<本轮新建绝对目录>/smoke.exec
```

两次 direct goal 不运行 lifecycle、不带 `-am`；产出 XML 分别来自 im-common/target/classes 与 im-ops/target/classes 的完整分析集合。XML 结构断言：common 的 `class[@name='com/co/kc/imchat/common/utils/NestedMapUtils']/method[@name='prune']/counter[@type='INSTRUCTION']` covered>0；`HashUtils` 的 class 级 INSTRUCTION covered=0、missed>0；Ops 的 `OpsCliHttpTransformerImpl` class 存在、`submissionResponseFrom` INSTRUCTION covered>0，并与实际使用的 generated 源/class 内部名和摘要对应；此处不证明生成鲜度。不得仅断言 package 总计或命令 exit0。任何目标被 JaCoCo 内建 generated filter 整体移除则停止 Review，不过滤 report 迁就测试。

缓存 0.8.13 descriptor 已确认 dataFile 的用户属性为 `jacoco.dataFile`，outputDirectory 没有用户属性，不能使用无效的 `-Djacoco.outputDirectory`。因此两个 owner report 明确写默认 `target/site/jacoco/jacoco.xml`，使用独享 smoke.exec，而不是假设可命令行重定位 report。实际执行前须另行批准清除这两个精确 report 目录；先整批校验路径/无 symlink，随后归档本轮新 XML/摘要。未批准清理则停在 smoke 前，不复用旧 report。该候选删除属于第 3 节待批准输出清理，不是本次文档授权。

真实 parser 在 architecture classpath 的公开类型及双 agent 完整参数仍须经授权的实际 smoke 确认；编译或 fixture setup 错误不算行为 RED。当前不执行 smoke、不授权清理。全量验收仍恢复正式默认输出与一次原 full，不使用 smoke.exec 冒充 full 数据。

## 可靠性与安全

这是构建证据边界，不改变业务超时、重试、授权、quorum 或持久化格式。失败即停止，不跳过模块、插件异常或生成器。fixture 只使用本次临时目录并精确清理；实际 smoke 不需要共享数据库或容器。

额外 source root、输出重定位和未知生成器当前均受控拒绝，不以未来任意插件无法静态证明为理由取消本次 generated 机械检查。任意恶意 Maven 插件、构建期间外部写者、同 namespace 的恶意依赖 shadowing 不在本方案完整保证内，仍需依赖／供应链 Review；不能将此限制解释为允许本轮正式产物漏检。

## 运维与交付

未上线，无配置迁移或兼容双写。生产 agent 参数不受影响；修改仅作用 Maven 测试插桩。清除生产 class／编译状态使 full 强制重建这部分输出，可能增加编译成本；不重复完整 reactor、不承诺性能收益。有效模型采集只运行一次，随同轮 manifest 使用，不跨轮缓存。

## 风险与回滚

- 风险：有效模型输出形状、模块枚举和生成期待集处理不完整；任何无法识别情况应停止并补设计，不降级放行。
- 风险：清除范围扩展误删；必须通过路径／symlink／先整批验证后删除的正反例，且只限上述构建输出。
- 回滚：同时撤销 prepare-only include 与依赖它的 guard／文档合同，保留原异常及失败记录；不删除运行数据或改写历史验收。
- 延后：支持自定义 source roots、额外生成器、JPMS、relocation，以及新的数值覆盖率策略，均另行设计。

## 验证策略

| 不变量 | 既有接缝与拟补正反例 |
|---|---|
| package 与路径真实属于自有边界 | `test-java-style-harness.sh`：合法普通／generated 包；注释、字符串、text block 伪声明；default、lookalike、错路径、Unicode escape、module-info 拒绝 |
| includes 只属于 prepare-agent，有效继承不变 | architecture `RuntimeDependencyPolicyTest` 的 DOM POM 接缝及隔离配置 fixture：删除／缩窄 include、移到global/report、child/profile覆盖、skip、excludes、丢失双agent组合、额外root失败 |
| generated/class 都来自本轮 | `test-reactor-evidence.sh`：prepare 清旧class/generated/exec/report及编译状态；正常新产物通过，缺模块／缺generated实现／缺class／旧report／错误时间／输出篡改拒绝 |
| 检查内部名称而非路径 | 同一reactor fixture构造最小class header：合法this_class；目录合法但内部越界、lookalike、截断、未知constant tag、路径不符拒绝 |
| 删除边界不逃逸 | 同一fixture：target／中间路径symlink、越界模型、重复目录、后项非法时前项不删除，未知输出目录拒绝 |
| report不隐藏自有代码 | 真实agent／report smoke：自有covered、未执行missed、真实MapStruct class、外部SQL解析可用且无外部probe、无MethodTooLarge；配置fixture拒绝report过滤 |
| 原验收不变 | 既有完整父Harness自测、冻结clean/full、同轮报告校验及真实HA，独立实现Review |

批准后的候选命令（目前均未执行）：

```bash
bash scripts/test-java-style-harness.sh
bash scripts/test-reactor-evidence.sh
./scripts/verify.sh drift
mvn -q -pl im-test/im-architecture-test -am -Dtest=RuntimeDependencyPolicyTest,CoverageInstrumentationTest -Dsurefire.failIfNoSpecifiedTests=false test
# 第 4 节固定独享 smoke.exec、两个 owner report 命令与 XML oracle；清理另需批准
# 模型命令见第 1 节；已授权的单次 probe 完成，不重跑；普通 jacoco:report exit0 不是证据
./scripts/verify.sh clean
./scripts/verify.sh full
```

第 4 节的 owner report 明确关联同一独享 exec，不能用 architecture 自身 report 或命令 exit0 代替 XML 断言。模型 fixtures 还须覆盖完整 52 项、单 project 被 full 拒绝、GAV/路径错配、重复/漏项、profile/属性/invocation 不一致及离线缺依赖停止。先取得配置／所有权 guard 的确定性反例，再最窄修复；真实 smoke 只报告实际结果，不制造历史 RED。

## 后续文档与执行

批准后纳入现有联合计划 T11，不新建并行 active 任务。拟议实现范围如下；本草案不授权修改这些文件。

- `pom.xml`：仅 prepare-agent execution 的 namespace includes。
- `scripts/check-java-style.sh`、`scripts/test-java-style-harness.sh`：source guard 及正反fixture。
- `scripts/lib/harness-reactor-evidence.rb`、`scripts/test-reactor-evidence.sh`：受控模型、production/generated输出清除与同轮校验、class内部名和摘要；沿用现有prepare/verify/check。
- `scripts/verify.sh`：仅在现有prepare调用处传递／采集有效模型所需参数与更新合同；不加第二reactor、恢复阶段或额外verify hook。若helper能完全内聚采集，保持此文件零行为修改。
- `im-test/im-architecture-test/src/test/java/com/co/kc/imchat/architecture/RuntimeDependencyPolicyTest.java`：配置结构与受控模型约束；同目录必要新增 `CoverageInstrumentationTest.java` 承载真实agent窄测试。
- `docs/references/UNIT_TEST_GUIDE.md`、`HARNESS_GUIDE.md`、`HARNESS_RULE_MATRIX.md`：插桩所有权、report不滤、generated机械边界及供应链限制。
- 本设计、设计索引及现有计划／PROGRESS：批准和实际证据状态。业务 Architecture／README 无运行行为变化，不为本任务新增说明页。

独立 Reviewer 已核验有效模型入口与完整 reactor、有限生成/插件/晚绑定合同以及来源证据，技术必修全部闭合，技术结论为 Approved，待本次最小事实回填的精确复核后确认落文档。该结论是设计技术 Review，不是用户对 include、实施或输出清理的授权。当前只有第 1 节模型与来源 probe，无 coverage 实现、实际 agent/report 或 full 验收；实际 fork/生成鲜度等实现验证义务不得默认为成立。

## 附录 A：备选方案与依据

| 方案 | 收益 | 代价与选择 |
|---|---|---|
| 推荐：prepare-only自有namespace与同轮所有权guard | 不改report统计集合，不再插桩第三方；owned越界可机械拒绝 | 需有效模型、generated/class和实际smoke证据；不是只加一行include |
| 升级JaCoCo／JSqlParser或修复上游生成方法 | 可保留第三方插桩 | 尚无本仓库可验证的兼容版本修复证据；扩大依赖风险，不作为当前默认方案 |
| 对所有依赖继续插桩，接受异常 | 无构建配置变化 | 不能关闭当前覆盖完整性缺口，只能保留失败事实，不作为修复 |
| 单类exclude或report过滤 | 表面消除错误或报告缺口 | 按事故类名规避问题且可能隐藏自有代码，不采用 |

依据为本机缓存 `org.jacoco:jacoco-maven-plugin:0.8.13` 的 `META-INF/maven/plugin.xml`：prepare-agent includes控制运行时插桩，report includes/excludes独立控制分析class集合。官方参数说明见 [prepare-agent](https://www.jacoco.org/jacoco/trunk/doc/prepare-agent-mojo.html) 与 [report](https://www.jacoco.org/jacoco/trunk/doc/report-mojo.html)；网页为当前版本导航，实施以0.8.13缓存descriptor及真实smoke核对，不把trunk当当前二进制证明。

## 附录 B：拟议决策记录

| ID | 问题 | 拟议决策 | 理由与影响 |
|---|---|---|---|
| C1 | 外部方法插桩过大 | 仅prepare-agent限定自有namespace，report不滤 | 维护源码所有权，而非单类例外；需guard和运行证据 |
| C2 | architecture前置时序不能证明全部generated | 复用full reactor prepare/verify/check，Maven前清受控生产输出、后验fresh class/generated/report | 不接受旧target，不新建缓存或多链；增加输出重建成本 |
| C3 | 额外root／生成器使边界不可靠 | 当前仅默认root和MapStruct受控模型，未知配置失败关闭 | 不构建通用求值器，也不把本次generated义务降为Review-only |
| C4 | 旧Review是否等同授权 | 保留Revise，专门设计批准与独立Review后才实施 | 不扩大可信hint设计范围或默改根POM |

## 附录 C：独立 Review

旧候选 Review 原件 `.harness/t11/coverage-approach-review.json` 为 `Revise`。本设计首轮独立技术 Review `.harness/t11/coverage-design-review-attempt1.json` 同为 `Revise`，三项必修为有效 reactor 模型、实际 agent/report 数据流及有限插件/MapStruct 合同。后续原 Reviewer 依次确认 smoke 鲜度表述 B、真实 52 项模型证据、精确 Shade 签名资源/合并配置、字段化晚绑定及默认非 verify 插件绑定合同闭合；当时只剩来源证据，整体仍为 Revise。两条获授权来源 probe 随后独立核验通过，技术必修全部闭合，Reviewer 技术结论为 Approved，待本次事实回填精确复核确认。全部旧 Revise 原件、原时间/摘要及分阶段缺口保留，不改写为历史通过。技术 Approved 不等于用户设计或实施授权：尚无用户 include、coverage 实施/输出清理授权，无实现 Accept、实际 agent/report 或 full 新证据；外部 MethodTooLarge 与整体 T11 不由此关闭。

## 附录 D：默认 52 个 POM 的独立 oracle

以下清单来自本轮只读递归根 `<modules>`，不是递归所有 POM，更不是现有报告推导。实施 fixture 固定完整集合并与实际 effective reactor 逐项比较；未来模块变动须同步 oracle 与 Review，不能只比较数量。各项相对仓库 canonical 根解析，必须是无 symlink 的真实 POM。

```text
pom.xml
im-common/pom.xml
im-plugin/pom.xml
im-plugin/im-cache/pom.xml
im-plugin/im-identity/pom.xml
im-plugin/im-excel/pom.xml
im-plugin/im-metrics/pom.xml
im-plugin/im-tracing/pom.xml
im-plugin/im-lock/pom.xml
im-plugin/im-datasource/pom.xml
im-plugin/im-session/pom.xml
im-plugin/im-web/pom.xml
im-plugin/im-dubbo/pom.xml
im-plugin/im-bolt/pom.xml
im-plugin/im-mq-kafka/pom.xml
im-plugin/im-gossip/pom.xml
im-plugin/im-raft/pom.xml
im-plugin/im-nacos/pom.xml
im-gateway/pom.xml
im-gateway/im-http-gateway/pom.xml
im-gateway/im-ws-gateway/pom.xml
im-gateway/im-ws-gateway/im-ws-gateway-sdk/pom.xml
im-gateway/im-ws-gateway/im-ws-gateway-server/pom.xml
im-broker/pom.xml
im-broker/im-broker-sdk/pom.xml
im-broker/im-broker-server/pom.xml
im-service/pom.xml
im-service/im-account/pom.xml
im-service/im-account/im-account-facade/pom.xml
im-service/im-account/im-account-admin-facade/pom.xml
im-service/im-account/im-account-server/pom.xml
im-service/im-social/pom.xml
im-service/im-social/im-social-facade/pom.xml
im-service/im-social/im-social-server/pom.xml
im-service/im-message/pom.xml
im-service/im-message/im-message-facade/pom.xml
im-service/im-message/im-message-server/pom.xml
im-management/pom.xml
im-management/im-iam/pom.xml
im-management/im-iam/im-iam-server/pom.xml
im-management/im-iam/im-iam-sdk/pom.xml
im-management/im-admin/pom.xml
im-management/im-ops/pom.xml
im-management/im-ops-cli/pom.xml
im-management/im-audit/pom.xml
im-management/im-audit/im-audit-sdk/pom.xml
im-management/im-audit/im-audit-server/pom.xml
im-test/pom.xml
im-test/im-architecture-test/pom.xml
im-test/im-e2e-test/pom.xml
im-test/im-load-test/pom.xml
im-test/im-routing-ha/pom.xml
```
