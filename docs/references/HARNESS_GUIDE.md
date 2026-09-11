# Harness Guide

项目采用 Harness 的原因和设计原则见根 [README](../../README.md#harness-与质量门禁)。本文是 Harness 运行规则的所有者：将现有仓库入口映射为五个子系统，登记传感器和规则状态，并定义规则生命周期、失败归因、反馈维护与清洁状态。业务、架构和编码规则由 [Architecture](../../ARCHITECTURE.md)、[Coding Guide](CODING_GUIDE.md)、[SQL Guide](SQL_GUIDE.md)、[Unit Test Guide](UNIT_TEST_GUIDE.md)、[Security](../SECURITY.md)、[Reliability](../RELIABILITY.md)或当前批准的所属设计定义。

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
| `scripts/verify.sh full` | 完整 Maven 与 Harness 门禁 | 交付前证据 |
| `scripts/verify.sh report` | 汇总最近验证和治理信号 | `.harness/report.json` |
| `scripts/verify.sh readiness` | Java/Maven/Node 与 Harness 工具就绪 | 版本或缺失工具的 WHAT/WHY/FIX |
| `scripts/verify.sh clean` | drift、脚本语法、diff、计划 WIP、恢复区和临时工件 | 可交接状态或聚合诊断 |
| `scripts/verify.sh handoff` | 当前 report、Git 改动、active plan 恢复状态和下一步命令 | 生成下一会话可直接读取的 JSON/Markdown 交接 |
| `scripts/verify.sh startup` / `init` | 全部 Spring Boot application 的 startup-smoke；环境、基础门禁和启动命令 | 发现新增 Server 漏测；不替代外部基础设施全栈启动 |
| `scripts/verify.sh cleanup [--apply]` | Harness 自有 tmp/PID allowlist、canonical path 和 provenance | 默认只读；apply 只清理明确安全目标 |
| `scripts/verify.sh quality` | AI Reviewer request/response、机器三维分数和硬性上限 | 生成模块 A/B/C/D 快照；缺 response 返回 `review_required` |
| `docs/exec-plans/TEMPLATE.md` | Sprint Contract、事实源、验证、任务状态、恢复和风险的统一骨架 | active plan 实例；模板本身不作为完成证据 |

脚本只负责它能准确识别的范围。规范中存在但矩阵标记为 Review 的规则，不得在文档中暗示已自动执行。

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

## 规则矩阵

矩阵是规则登记表，只记录所属规范、当前传感器、Review 状态和升级或退出等自动化条件。Harness 运行规则由本文定义；业务、架构和编码规则的完整解释只存在于 Architecture、Coding、SQL、Unit Test、Security、Reliability 或当前批准的所属设计中。模块 README 只拥有业务统一语言、不变量和当前模块事实，不承载自动门禁规则正文。

| 规则 | 规范所有者 | 自动检查 | Review |
|---|---|---|---|
| `AGENTS.md` 等 AI 专用指令文档使用英文；面向开发者的 `README.md`、`ARCHITECTURE.md` 和 `docs/**` 使用中文解释性正文，代码标识符、命令、协议名、既定技术术语和技术图标签在更清晰时可以保留英文 | Harness/Documentation | Review-only；是否清晰取决于语境，无法根据英文 token 的出现可靠判断语言质量，机械检测易产生误报 | 是 |
| 根 Architecture 只拥有跨模块拓扑、依赖和数据所有权；复杂模块在本地 Architecture 说明内部拓扑与一致性。只有存在多个运行职责、独立一致性模型或重要跨边界流程时才新增，Facade、SDK、聚合 POM 与单一 Plugin 使用 README | Harness/Architecture | `check-drift.sh` 保证已批准的 Gateway/Broker/Message 局部入口存在；是否新增或移除局部 Architecture 仍为 Review-only，不能按目录或 POM 数量机械判断 | 是 |
| `scripts/*.sh` 文件头说明用途、输入、输出/副作用、依赖和退出码；非显然函数说明参数、算法、隔离或失败传播，简单语句不写逐行旁白 | Harness/Documentation | Review-only；注释质量不能由行数或函数前是否有注释准确判断，`bash -n` 与 Harness fixture 只验证脚本行为 | 是 |
| 根 `PROGRESS.md` 是有界全仓状态索引，所有 active plan 必须被引用；没有 active plan 时明确写 `none`，详细任务和证据仍由计划拥有 | Harness/Plans | `check-drift.sh` + PROGRESS 正反 fixture 自动检查计划链接；当前任务和验证摘要的新鲜度由 Review 确认 | 是 |
| 跨模块、跨会话和高风险工作从唯一执行计划模板创建；active plan 必须包含 Sprint Contract、事实源、验证分层、任务状态、恢复状态和回滚与残余风险，启动与交接信息留在计划内，不创建竞争状态文件 | Harness/Plans | `check-drift.sh` 检查 active plan 稳定章节，`verify.sh clean` 复用 drift；内容质量与业务语义由 Review 判断 | 是 |
| `readiness` 使用 POM/前端工具链约束验证 Java、Maven、Node 和必需命令；`clean` 只读检查可交接状态，不删除文件或误伤正常未跟踪源码 | Harness/Environment | `test-harness.sh` 的兼容/不兼容版本、WIP、恢复区、临时工件与未跟踪源码 fixture | 是 |
| 核心 checker 失败包含 WHAT/WHY/FIX，并保留具体规则与文件位置 | Harness/Feedback | Drift、Java style、SQL Harness fixture | 是 |
| 模块与 DDD 依赖方向 | Architecture/Coding | ArchUnit | 是 |
| 本地同机运行的 Broker Bolt、Broker 管理 HTTP、Gateway Bolt 与 Gateway WebSocket 默认监听端口互不冲突 | Reliability/Configuration | `BrokerConfigTest`、`BrokerManagementPropertiesTest`、`WsConfigTest` | 是 |
| 新增业务运行模块纳入 Architecture 导入范围，避免规则因未扫描而假通过；Spring Boot 可执行模块显式加入原始 `target/classes` 测试类路径 | Architecture/Harness | ArchUnit 模块导入、测试类路径与聚焦 RED fixture | 是 |
| Facade/SDK 不依赖 server | Architecture | ArchUnit | 是 |
| 管理审计持久化只由 `im-audit-server` 拥有；Admin/IAM 依赖 SDK 而非 Server | Architecture/Coding | Maven 依赖与源码所有权扫描 + ArchUnit | 是 |
| 审计提交时序、脱敏、失败隔离、来源校验、重试和幂等 | Security/Reliability | SDK/Server 聚焦行为测试；通用源码判断 Review-only | 是 |
| Facade 公开请求使用 `facade.params/*Params`，不暴露应用层 `command/query` | Coding | Java style checker + fixtures | 是 |
| Facade 分页参数复用 `Paging`，边界包装属性显式拒绝 `null` | Coding | Facade 契约测试；全仓历史基线未收敛前 Review-only | 是 |
| Facade 纯集合结果直接返回 `List<DTO>`，只有存在分页、游标、缺失项或其他元数据时才使用结果包装 DTO | Coding | 当前 Review-only；返回包装是否承载真实元数据需要结合契约语义判断 | 是 |
| Dubbo Provider 通过 `im-dubbo` 统一转换 `RpcException`，不在业务 RPC 实现重复异常包装 | Coding/Reliability | 通用切面单测与自动配置测试；业务侧 Review-only | 是 |
| RPC Token 使用保留 Attachment 传播，Provider 在接口适配器前建立并在调用后恢复 Spring Security 上下文；业务 Params 不承载 Token 或可信来源 | Coding/Security | `RpcTokenConsumerFilterTest`、`RpcTokenProviderFilterTest`；通用载荷语义 Review-only | 是 |
| Facade 与 SDK RPC 公开输入使用独立的 `*Params`，不直接暴露 Event、领域对象或 CQRS 模型 | Coding | `check-java-style.sh` + 正反 fixture | 是 |
| 插件不依赖业务模块 | Architecture | ArchUnit | 是 |
| Snowflake 算法、机器 ID 契约、实现选择和 Bean 装配只由 `im-identity` 持有；运行模块直接声明依赖并通过必填 STATIC/REDIS 配置选择实现，不直接构造基础设施；Redisson 保持可选 | Architecture/Identity design | `RuntimeDependencyPolicyTest` 源码所有权、生产代码构造扫描、Maven 直接依赖与 optional 属性检查；`ImIdentityAutoConfigurationTest` 模式与失败语义 | 是 |
| `im-web` 不依赖 Session 或管理身份实现，管理应用与 IAM SDK 显式接入统一 Web 协议 | Architecture/Coding | ArchUnit + Maven 依赖测试 | 是 |
| 直接依赖 `im-web` 的可部署 Servlet MVC 应用显式配置 `/api/doc.html` 与 `/v3/api-docs`；SDK、Plugin 和 Facade 不声明应用路由 | Architecture/Coding | `RuntimeDependencyPolicyTest.deployableServletApplicationsConfigureOpenApiPaths` | 是 |
| 插件不硬编码业务 Controller 路径；通用 Web 请求上下文不承载安全身份或业务语义，并由 Servlet Filter 在 Spring Security 前建立 | Coding/Security | 请求上下文 Filter 注册顺序、生命周期及安全端点可见性测试；路径与语义归属 Review-only | 是 |
| 可部署 HTTP 应用统一使用 `HttpResult`，Security 失败使用相同协议，不重复声明全局错误响应 | Coding | Web/IAM SDK/Admin/Monitor 聚焦行为测试；重复类型识别 Review-only | 是 |
| 管理 UI 在唯一 HTTP 边界拆解 `HttpResult`、提交同源 CSRF Token，并以契约测试覆盖 URL、Params、Body 和错误映射 | Coding/Security | 各 UI `Http.spec.ts`、API contract tests；跨工程配置一致性暂为 Review-only | 是 |
| 管理 UI 列表区分 loading/data/empty/error 并在失败时保留成功数据；抽屉保留失败输入，局部写入状态不冻结整页，危险或立即失效操作要求后果确认，状态与反馈具备可访问文本语义 | Coding/Security | IAM `InteractionComponents.spec.ts` 与各 Workspace 行为测试；其他 UI 的业务后果和复用范围 Review-only | 是 |
| 独立 Vue/TypeScript UI 提供 lint、format check、unit test、typecheck 和 build 门禁，配置由各 UI 自有 | Coding/Test | 各 UI package scripts；根 verification 继续执行测试与构建，lint/format 脚本完整性暂为 Review-only | 是 |
| 浏览器 wire model 以字符串承载 Java `Long` 业务 ID，禁止用 `Number(id)`；分页总数和 epoch millis 仅在安全整数校验后转换 | Coding/Security | 各 UI wire/date-time tests；待 TypeScript Harness 能区分业务 ID 与计数后再增加静态检查 | 是 |
| JAR 托管且无 SPA fallback 的 UI 使用 Hash Router；IAM BFF `continue` 仅恢复经过校验的同源相对路径 | Coding/Security | 各 UI Router/Navigation tests 与 IAM SDK Authorization Session tests；服务端 fallback 方案 Review-only | 是 |
| IAM BFF 公共页面、静态资源、登录与回调跳过 Session Introspection；IAM 会话端点与 API 必须认证，未知路径默认拒绝；已知前端静态资源缺失返回 NOT_FOUND，不能把所有未映射 API 当成静态资源 | Coding/Security | `IamBffRequestPolicyTest`、`IamSecurityFilterTest`、`ErrorAdviceTest`、既有未映射 API 契约测试；应用自定义安全链复用检查 Review-only | 是 |
| IAM 自身管理登录复用 Spring Form Login 与 CSRF，合法 SPA `continue` 显式恢复；缺少 `continue` 时恢复 OAuth SavedRequest，外部地址不得跳转 | Security/Coding | `IamLoginRedirectHandlerTest`、`IamSecurityBeansTest`、`LoginView.spec.ts`、`Navigation.spec.ts` | 是 |
| 常见静态资源后缀与 `/assets/**` 跳过完整 HTTP 访问日志；无后缀路径按实际 MVC 静态资源 Handler 判断，不能全局硬编码 `/`；Controller、认证入口和业务 API 仍保留日志 | Coding | `LoggingFilterTest`；其他静态资源归属 Review-only | 是 |
| 生产代码显式类型、无字段注入 | Coding | Drift checker | 是 |
| 生产代码优先使用 import，不在方法体、注解和表达式内散落全限定类名 | Coding | 不自动化（Review-only）；同名类型冲突与字符串内容难以由正则可靠区分 | 是 |
| 生产测试替身、控制台输出、手动 `ObjectMapper` | Coding/Test | Drift checker | 是 |
| 成组简单 getter/setter | Coding | Java style checker | 是 |
| AppService 标量业务入参 | Coding | Java style checker | 是 |
| 直接承载公开用例的应用层入口使用 `*AppService`，并通过单个 Command、Query 或其他明确输入对象表达用例 | Coding | 当前 Review-only；待 `im-monitor` 查询入口收敛后评估窄结构检查 | 是 |
| AppService 按聚合、用例主体和事务边界组织；同一主体的紧密命令不按方法类别拆成单一调用方的薄服务，不跨聚合或事务边界机械合并 | Coding | Review-only；方法数量、依赖数量和调用方数量都不能可靠表达业务内聚性 | 是 |
| 拥有独立身份和生命周期且允许分别存在的聚合使用独立注册用例与事务边界；仅业务不变量要求原子共生时组合创建 | Coding | 聚焦应用服务行为测试；聚合生命周期与原子共生语义 Review-only | 是 |
| AppService 不直接依赖其他 AppService | Coding | ArchUnit | 是 |
| 跨上下文外部投影只参与派生领域资料，不作为临时字段写回本上下文聚合 | Coding/DDD | Social `FriendServiceTest` 与 `FriendAppServiceTest`；其他上下文组合语义 Review-only | 是 |
| 可部署应用不使用 `@ConditionalOnProperty` 关闭生产 Bean；条件装配只保留在插件、SDK 和自动配置 | Coding | Java style checker + 正反 fixture | 是 |
| 稳定横切策略优先使用显式注解/接口契约，避免 AppService 重复 Executor/Lambda 包装 | Coding | 不自动化（Review-only）；具体能力使用注解清单与切面行为测试 | 是 |
| 安全身份与授权由 Spring Security 管理，审计等业务注解不复制权限策略；自定义跨线程上下文只传播非安全元数据并及时清理 | Coding/Security | 权限注解清单、授权允许/拒绝测试、上下文传播与清理测试；通用静态判断 Review-only | 是 |
| 管理端业务权限枚举统一承载编码、展示名称和说明，并通过嵌套 `Code` 提供编译期常量；接入应用目录与 `@RequiresPermission` 共源，IAM Server 的标准 `@PreAuthorize` 也引用对应 `Code`，Controller 不重复权限字符串或 Spring EL | Coding/Security | `RequiresPermissionTest` 允许/拒绝测试 + 各管理模块权限元数据和接口授权契约测试；跨模块通用源码识别成熟前不使用全局正则 | 是 |
| IAM 内部管理权限与外部应用权限必须分属不同领域语义：IAM 内部权限只保护 IAM 自身管理端点，外部应用权限由各应用声明并通过权限目录同步；不得混用 Permission 聚合、权限目录或授权入口 | Coding/Security/DDD | IAM 端点授权契约、外部应用权限目录同步和跨应用隔离测试；领域语义与表复用边界由 Review 检查 | 是 |
| 声明式锁键不显式调用 `toString()`，统一由锁切面完成字符串转换 | Coding | Java style checker | 是 |
| CQRS Command/Query/Event 按意图、读取、已发生事实区分并放入对应包；边界对象不直接持有业务领域对象 | Coding | 不自动化（Observed） | 是 |
| `model/cqrs/dto` 中的应用传输结果统一使用 `*DTO` 后缀，不混用 Data/View/裸 Result | Coding | 当前 Review-only；仓库历史 DTO 目录收敛后增加窄路径命名检查和正反 fixture | 是 |
| 框架回调提交协议处理后的最终状态时使用统一保存 Command；只有回调要求应用执行不同业务流程时才拆分用例 | Coding | IAM OAuth 应用边界与适配器聚焦测试；通用语义 Review-only | 是 |
| Spring Security 查询 SPI 不修改领域状态；Authorization Code 单次消费和 Refresh Token 轮换优先使用框架默认 Provider，不增加无明确需求的历史重放状态 | Coding/Security | OAuth Credential 查询无副作用测试、默认 Provider 契约与授权持久化测试；通用语义 Review-only | 是 |
| 同一应用中的浏览器 Session 与 OAuth Client Bearer 接口按 URL 边界使用独立有序 SecurityFilterChain；Client API 无状态，Web 兜底链不混入 Bearer 认证 | Coding/Security | IAM Security Bean 命名/数量契约与权限目录认证测试；通用链配置语义 Review-only | 是 |
| 接入服务的 Opaque Client Credentials 身份和 Audience 校验复用 IAM SDK；业务服务不重复声明仅改名的 Principal/Converter | Coding/Security | IAM SDK Audience 允许/拒绝测试与 Audit 身份上下文测试；跨模块重复能力 Review-only | 是 |
| 通用哈希原语归 `HashUtils`；管理员密码、OAuth Client Secret 与 OAuth Token 保持独立凭据边界，不跨领域复用密码服务和值对象 | Coding/Security | `HashUtilsTest`、OAuth Client Secret 编码/匹配兼容性测试与 IAM Security Bean 测试；跨模块语义 Review-only | 是 |
| `Query`/`QueryCondition` 命名、分页分离和可选过滤条件；可选文本先将空白归一化为 `null`，再执行关联校验和领域转换 | Coding | Audit 查询转换聚焦测试；通用命名与归一化顺序继续 Review-only | 是 |
| 边界输入输出使用包装类型并拒绝意外 `null` | Coding | 不自动化（Observed） | 是 |
| 边界对象与领域对象分别使用 `AssertUtils.arg*`、`domainProp*` 校验 | Coding | 不自动化（Review-only） | 是 |
| 包内能力接口、技术实现和模型明显混放 | Coding | Java style checker 的窄规则 | 是 |
| 包层级与稳定职责匹配；单领域模块保持 `domain/model|repository|service` 扁平结构，多领域模块再按能力分组 | Coding | 不自动化（Review-only） | 是 |
| 运行态诊断属于技术能力，不建模为业务 Domain；技术运行模块可在 `support/<capability>` 下按 model/service/state/tracker 垂直组织，业务服务仍遵循标准 DDD 分层；远端诊断访问使用 Adapter，不命名为持久化 Repository，也不为唯一实现创建同形 Gateway 接口 | Coding | Broker/Monitor 包结构与聚焦行为测试；模块性质、真实替代实现和职责判断 Review-only | 是 |
| SDK 按稳定能力组织包；协议模型、框架适配和传输实现分离，HTTP/Kafka 等形成独立技术边界时使用二级包；不为服务端 DDD 分层在 SDK 中复制 `application/domain/infrastructure` | Coding | 不自动化（Review-only） | 是 |
| 可部署 DDD 业务模块使用统一顶层分层骨架，能力差异保留在层内子包 | Coding | 不自动化（Review-only） | 是 |
| `application` 仅承载应用服务；应用层横切组件、场景常量和应用边界异常放在模块顶层 `support` | Coding | 不自动化（Review-only） | 是 |
| 应用服务编排按输入转换、用例调用、输出转换分段书写，避免将边界构造、适配器调用和结果映射压缩为单行 | Coding | Review-only；需结合方法语义判断，不做脆弱格式正则 | 是 |
| 入站框架适配器只转换框架输入输出；认证、权限解析等同一用例的领域协作由应用服务完整编排并返回应用层结果 | Coding | IAM 管理员认证应用服务与 Provider 聚焦测试；通用边界 Review-only，避免误伤 `PasswordEncoder` 等纯技术适配 | 是 |
| 确定性应用用例优先通过 `@Audited` 记录审计；动态属性由参数级 `@AuditAttribute` 与成功结果的一层属性提供，不记录字段显式使用 `include = false`，不在应用服务创建专用 Resolver 或手写成功/失败控制流 | Coding/Security | SDK Aspect 与 Audit 导出聚焦测试；字段敏感性与注解完整性 Review-only | 是 |
| 信任来源由认证上下文或固定传输通道注入，不在不可信载荷中重复声明；应用 Command/Event 仅保留一个扁平化来源字段 | Security/Coding | Audit SDK 契约结构测试与 Ingestion Transformer 聚焦测试；通用语义 Review-only | 是 |
| 普通说明使用块注释，类注释使用多行 Javadoc，字段和 record 组件的简短说明优先使用块注释 | Coding | Review-only；格式和语义需人工判断 | 是 |
| record 属性较多时每个组件单独换行，并为每个组件提供简短块注释 | Coding | Review-only；不使用固定属性数量阈值 | 是 |
| MapStruct `@Mapper` 保留生成映射，不退化为全手写 `default` 方法集合 | Coding | 不自动化（Review-only） | 是 |
| MapStruct 跨层异名属性使用显式映射，关键时间与标识字段不静默丢失 | Coding | `BrokerDiagnosticAppTransformerTest` 等模块转换测试；通用异名语义判断 Review-only | 是 |
| 无运行时协作者的无状态 MapStruct Transformer 优先使用 `INSTANCE`，需要注入协作者时才使用 Spring component model，且不混用两种获取方式 | Coding | 不自动化（Review-only）；需识别 `uses`、Decorator、抽象 Mapper 及容器协作者，单纯匹配 `componentModel` 会误报 | 是 |
| Transformer 按转换目标归属；应用/CQRS 输入到领域对象使用 `*DomainTransformer`，领域对象到应用 DTO 使用 `*AppTransformer`，业务推断不放入字段 Mapper | Coding | IAM OAuth 授权边界聚焦测试；通用规则 Review-only，待历史双向 Transformer 收敛并能解析泛型返回类型后再自动化 | 是 |
| 多参数 HTTP 查询使用 `*Request + @ModelAttribute`，Request 到 CQRS 的跨层转换由接口 Transformer 完成 | Coding | 不自动化（Review-only）；参数多少及是否形成稳定请求语义需结合接口判断 | 是 |
| HTTP `@RequestBody` 使用接口层独立的 `*Request` 类型，不直接暴露 SDK、领域或 CQRS 对象 | Coding | `check-java-style.sh` + 正反 fixture | 是 |
| 绝对业务时刻在应用、领域、Java 服务契约和 MyBatis Entity 中使用 `Instant`；浏览器 HTTP 边界使用 epoch milliseconds | Coding | Audit HTTP/Transformer/持久化聚焦测试；通用语义 Review-only | 是 |
| 领域 `*QueryCondition` 中共同表达闭区间的绝对起止时刻使用共享 `TimeRange` | Coding | `TimeRange` 与 Audit Transformer/Repository 聚焦测试；通用识别 Review-only | 是 |
| 用户可见时间按显式 IANA 时区格式化为 `yyyy-MM-dd HH:mm:ss`，服务端导出接收用户时区而非使用机器默认时区 | Coding | Audit UI 与 Excel 导出聚焦测试；跨模块通用规则 Review-only | 是 |
| 领域业务标识使用值对象 | Coding | 不自动化 | 是 |
| 聚合根继承 `Identification`，按需实现 `Validator`；Lombok 相等性使用 `callSuper = false`，不使用 `onlyExplicitlyIncluded` 定制业务 ID 专属相等性 | Coding | Java style checker 拦截 `Identification` 子类的 `onlyExplicitlyIncluded = true`；是否需要生成相等性由 Review 判断 | 是 |
| 新增聚合封闭状态写入口，不使用类级 `@Data`/`@Setter`；统一校验不变量，以领域方法维护生命周期，并将同一阶段的成组字段组合为不可变值对象 | Coding | `OAuthGrantTest` 及 OAuth 持久化聚焦测试；通用检查暂为 Review-only，仓库仍有历史可变聚合，待其收敛且具备字段级 setter 误报样例后再启用全局门禁 | 是 |
| 承担领域校验的聚合 Builder 内部持有私有无参构造的聚合，直接填充其字段而不复制 Builder 属性或保留长参数构造器，`build()` 返回前统一校验；`Identification.pkId` 不进入领域构造器或 Builder，由 Repository 在构建后回填 | Coding | `AuditEventTest` 与 Repository/Transformer 聚焦测试；通用判定 Review-only | 是 |
| 领域对象必填属性不超过 4 个且顺序清晰时优先直接构造；达到 5 个及以上，或参数组合容易错位时使用手写 Builder 并在 `build()` 统一校验 | Coding | 不自动化（Review-only）；参数阈值与业务语义需结合判断 | 是 |
| 值对象规范构造入口统一保证格式、容量、归一化和脱敏不变量 | Coding/Security | 聚焦值对象单测；通用静态判断 Review-only | 是 |
| 聚合相等性由聚合自身字段表达，排除 `Identification.pkId` 技术主键 | Coding | 聚焦相等性单测；Java style checker + 正反 fixture 拦截业务 ID Include 定制 | 是 |
| 模型名称表达领域事实，不把截断、脱敏等内部处理方式作为类型语义 | Coding | 不自动化（Review-only） | 是 |
| 对外接口枚举与领域枚举隔离，跨层枚举转换由 Transformer 完成 | Coding | 不自动化（Review-only） | 是 |
| MyBatis Entity 的闭集状态、动作、结果和种类使用数据库层枚举；数值列枚举声明稳定值和 `@EnumValue` | Coding | Java style checker 检查低误报字段；Audit Schema 聚焦测试验证数值映射；其他列类型与开放协议值 Review-only | 是 |
| 数据库 JSON 列由 MyBatis TypeHandler 编解码，领域 Transformer 不处理 JSON 存储格式 | Coding | 不自动化（Review-only）；需区分数据库列与协议、缓存等合法显式 JSON 转换 | 是 |
| 所有 MyBatis Entity 继承 `BaseEntity`，所有表保留 `id/create_time/update_time/is_deleted` 标准模板字段；数据库主键与领域业务 ID 分离 | Coding/SQL | SQL checker + fixture 强制模板字段；Java style checker 拦截重复声明完整公共字段；Entity 继承与双 ID 语义由聚焦测试及 Review 补充 | 是 |
| `Identification.pkId` 仅标识当前表行；跨表关系使用领域业务 ID；独立身份或生命周期的一对多接入配置拆成独立聚合，不按技术分支复制平行聚合 | Coding | IAM 聚合/Repository/Schema 聚焦测试；字段语义、基数和聚合边界 Review-only | 是 |
| Repository 通过 MyBatis Service 完成持久化，不为单一查询语义增加透传 QueryCondition 或直接编排 Mapper | Coding | Java style checker 拦截 Repository 直接导入 Mapper；查询语义层次由 Review 判断 | 是 |
| 承担入站入口职责的框架 SPI 只做协议适配，完整用例经过应用服务，不直接访问领域 Repository 或 MyBatis | Coding | IAM Security SPI 聚焦依赖测试；通用职责识别 Review-only | 是 |
| 完整聚合由所属 Repository 返回，关联 Repository 仅返回关系事实 | Coding | IAM `RoleRepository`、`AdministratorService` 和 Repository 聚焦测试；聚合所有权通用判断 Review-only | 是 |
| Repository 使用明确的标识值对象表达查询语义，多种外部标识在应用边界识别，不创建宽泛联合标识或退化为裸 `String` | Coding | 不自动化（Review-only）；需结合调用方是否已知标识类型以及联合标识是否具有真实领域语义判断 | 是 |
| MyBatis Mapper 扫描策略单一，逐个 `@Mapper` 时不重复声明空 `@MapperScan` 配置；公共 datasource 插件不扫描业务 Mapper | Coding | Java style checker + 正反 fixture；多数据源和跨根包扫描由 Review 判断 | 是 |
| `im-admin` 通过 Account Admin Facade 管理普通用户，不拥有本地用户持久化，不依赖 `im-datasource` 或保留失效 MyBatis 映射 | Architecture/DDD | `RuntimeDependencyPolicyTest#accountAdminFacadeHasDedicatedRuntimeBoundary` 与 Admin Context 测试 | Account 管理边界或数据所有权迁移 |
| 四个独立部署的 Management 应用直接依赖 `im-nacos`，统一获得动态配置和服务存活注册 | Architecture/Reliability | `RuntimeDependencyPolicyTest#managementModulesIncludeIamAndIndependentApplications` 检查四个运行 POM | Management 部署拓扑或配置中心方案迁移 |
| 领域服务包含真实业务决策 | Coding | 不自动化 | 是 |
| 跨多个领域对象的全量同步、差集计算和状态迁移集中在领域服务；应用服务只做输入转换、调用与持久化编排 | Coding | 不自动化（Review-only） | 是 |
| CQRS 输入保留边界类型；应用服务在用例入口先统一构造领域值对象和内部查询条件，后续编排不继续传播同义的原始 `String`、数值或集合 | Coding | 不自动化（Review-only）；字段业务语义和合法通用边界类型无法仅按 Java 类型可靠判断 | 是 |
| 自动过期的限流、防暴力破解计数和临时限制留在 `support`/`infrastructure`，不映射为聚合状态或业务表字段 | Coding/Security | `LoginProtectionTest` 与 IAM 认证/审计聚焦测试；通用语义 Review-only | 是 |
| 聚合删除校验与 Repository 生命周期删除语义一致 | Coding | 不自动化（Review-only） | 是 |
| Adapter 使用领域对象作为业务边界 | Coding | 不自动化 | 是 |
| 条件 Bean 装配闭合 | Coding | 聚焦启动测试 | 是 |
| Starter/SDK 接入 Bean 只由 AutoConfiguration 注册；Server 不直接或传递获得自身客户端 SDK，可选 SDK 集成依赖由消费者显式选择 | Coding/Architecture | `RuntimeDependencyPolicyTest` 禁止 IAM Server 直接依赖 IAM SDK并要求 Audit SDK 将 IAM SDK 标记为 optional；IAM 真实启动回归 | 是 |
| 本地配置提供可绑定的 localhost 与开发凭据默认值，生产通过 Nacos 整体覆盖 issuer、签名密钥、Client Secret 和 Session 加密密钥 | Coding/Security | IAM classpath RSA 加载测试 + Admin/Monitor/Audit 配置绑定测试；生产覆盖完整性由部署检查和 Review 验证 | 是 |
| 可水平扩容服务的分布式 ID 生成器不共享硬编码机器标识 | Coding/Reliability | 当前 Observed + Review-only；待现有 StaticSnowflake 基线收敛后增加配置与多实例唯一性测试 | 是 |
| 固定提交后 Adapter 副作用使用注解，复合步骤由应用服务显式编排 | Coding/Reliability | 聚焦事务时序测试；通用静态识别易误判，其他场景 Review-only | 是 |
| 不增加测试专用生产 API | Coding/Test | 本轮不自动化 | 是 |
| 测试不使用真实等待 | Unit Test | Drift checker | 是 |
| 禁用测试必须说明原因 | Unit Test | Drift checker | 是 |
| DDL、危险 SQL、`${...}`、静态无条件写入 | SQL | SQL checker + fixture | 是 |
| 服务私有 DDL 位于所属 Server 模块根 `sql/`，Schema 与表不能跨服务混放 | SQL/Architecture | SQL checker 递归路径扫描 + 各服务 Schema 所有权测试；表业务归属仍由 Review 判断 | 是 |
| Mapper `SELECT *` | SQL | SQL checker + fixture | 是 |
| 动态 SQL、索引合理性和执行计划 | SQL | 不自动化 | 是 |
| 未上线且无历史数据兼容需求时直接更新当前 DDL，不创建迁移脚本 | SQL | 不自动化（Review-only）；发布状态和历史数据需求无法从文件名可靠推断 | 是 |

“是”表示 Review 仍需检查语义和例外，不代表重复执行同一机械扫描。

## 从问题到规则

实现或 Review 中确认了可复用的工程约定时，实现者必须在同一任务完成 Harness 反馈评估，不能只修正当前代码。评估至少回答：规则由哪份规范所有、是否代表可复用边界、最窄可靠传感器是什么、仓库基线是否已经满足，以及需要哪些诊断和 fixture。评估结果只能是以下三类之一：

1. **Enforced**：可以低误报机械判断，当前任务同步规范、矩阵、检查器和 fixture。
2. **Review-only**：规则有效但不适合机械判断，或仓库基线尚未收敛；同步规范和矩阵，明确当前由 Review 负责及转为自动检查的条件。
3. **Not a Harness rule**：仅是局部命名选择或一次性实现细节，不形成共享规则；无需增加仓库约束。

不得因为当前任务范围较小而静默跳过评估，也不得为了立即自动化而增加脆弱的类名白名单或扩大误报范围。

边界包装类型规则当前处于 **Observed**：Facade、CQRS、HTTP/RPC、事件和跨层快照仍有历史基本类型，且基本类型在内部计算、计数和谓词返回中是合理表达。现阶段由 Review 检查新增或触碰的边界模型是否会用 `0`、`false` 掩盖缺失输入。只有相关历史基线收敛，且结构化检查能够区分边界模型与内部确定值，并具备必填包装值校验的正反 fixture 后，才升级为 Enforced；不得通过目录白名单强行启用。

CQRS 边界值规则当前处于 **Observed**：历史 Command/Query 仍有直接使用业务枚举和值对象的情况，而 `im-common` 中稳定通用边界值对象可以合法复用。现阶段 Review 检查新增或触碰的 CQRS 输入是否把聚合、业务值对象或 Repository 查询条件泄漏到应用边界。只有能够区分业务领域类型与允许的通用边界类型，并完成历史基线收敛及正反 fixture 后，才升级为 ArchUnit 或结构化检查。

框架回调用例规则采用 **聚焦结构/行为测试 + Review-only**：IAM OAuth 边界测试证明 Spring Authorization Server 的 `save` 回调只提交已经完成协议处理的最终授权状态，统一转换为 `OAuthAuthorizationSaveCmd`，不再执行第二遍授权码兑换或 Refresh Token 刷新。Review 先判断回调表达的是最终状态持久化还是要求应用执行业务动作，再决定使用统一保存或独立 Command；仅按 `type`、`save`、`handle` 或空字段扫描无法作出该判断，只有结构化分析能够关联框架回调契约和应用方法后才升级为通用门禁。

应用服务入口转换规则采用 **聚焦行为测试 + Review-only**：Review 检查公开用例是否先将 Command/Query 的基础边界值统一构造成领域值对象和内部查询条件，再执行 Repository 查询与业务编排；后续代码不应同时传播同义的原始值和领域值。聚焦测试应证明无效边界值在访问 Repository 前即由领域构造入口拒绝。通用静态扫描无法可靠判断 `String`、数值和集合是否具有领域语义，也无法区分合法的输出转换与遗漏的输入转换；只有结构化分析能够关联 CQRS 字段、领域构造入口和后续调用数据流，并覆盖可选值、通用 `Paging`/`TimeRange` 与纯技术参数等正反 fixture 后，才升级为自动门禁。

信任来源规则采用 **聚焦行为测试 + Review-only** 边界：Audit SDK 契约测试证明生产者可控载荷不包含 `sourceApp`，Ingestion Transformer 测试证明 HTTP 认证身份或 Kafka Binding 注入的来源会进入扁平 Event 和领域事实。Review 检查新增多租户、身份、来源等信任字段是否仍可被请求体、消息体或多个同义边界字段覆盖。通用检查需要关联认证边界、传输配置与 Event 构建，仅按 `sourceApp` 名称扫描会误伤领域事实和查询条件；只有结构化分析能稳定区分可信注入与不可信载荷，并具备 HTTP、Kafka、领域模型和合法业务来源字段的正反 fixture 后，才升级为自动门禁。

声明式动态审计属性规则采用 **聚焦行为测试 + Review-only** 边界：SDK Aspect 测试证明 `targetId` SpEL 可以按方法声明的真实参数名解析不同参数语义，`@AuditAttribute` 可以采集普通参数、对象的一层属性和成功结果，`include = false` 可以排除明确不记录的字段，并证明属性读取、上下文及提交旁路异常不改变业务结果。Review 检查结果确定的应用用例是否复用 `@Audited`、`targetId` 是否引用真实参数名、是否只在需要采集的参数或字段上声明属性规则，以及应用服务是否重复手写审计成功/失败控制流。登录、Token、Session、协议监听和接收端点等场景可能合法显式提交或禁止递归审计，不能按 `try/catch`、类名或缺少注解机械拦截；只有结构化分析能够识别应用用例、显式事件场景、接收端点和注解属性，并具备跨场景正反 fixture 后，才升级为自动检查。

应用入口命名规则当前处于 **Observed**：直接位于 `application` 根包、向接口层暴露完整用例的服务应使用 `*AppService`，其公开方法继续受单输入对象和跨应用服务依赖规则约束；领域协作、技术客户端和内部通知处理器不因位于应用层附近就机械改名。Broker 与 Monitor 的诊断查询入口已经分别收敛为 `BrokerDiagnosticAppService` 和 `MonitorQueryAppService`；待其他应用入口基线完成收敛后，可增加仅检查 `application` 根包公开入口的正反 fixture，避免误伤应用层内部组件。

`AssertUtils` 分工规则当前为 **Review-only**：相同类可能同时校验外部方法参数和领域属性，单凭包名或方法名无法可靠判断应使用 `arg*` 还是 `domainProp*`。Review 检查边界对象是否显式拒绝无效输入、领域不变量是否使用领域属性语义，以及是否重复手写等价校验。只有结构化分析能够识别校验发生的语义位置并覆盖混合场景后，才升级为自动检查。

领域包深度规则当前为 **Review-only**：Review 检查只有一个领域能力的模块是否直接使用 `domain/model`、`domain/repository` 和按需存在的 `domain/service`，并在多个相对独立的领域能力实际出现后才增加 `domain/<capability>/...` 分组。目录数量、类名前缀和当前文件数量都不能可靠证明领域边界，因此不使用正则或包数量机械推断。只有仓库具备结构化领域所有权元数据，或其他能够稳定区分独立领域能力的信号，并覆盖单领域扁平结构、多领域分组结构及同名前缀误判等正反 fixture 后，才升级为自动检查。

同名领域概念隔离采用 **聚焦 Schema/行为测试 + Review-only**：Review 检查面向不同授权对象、生命周期或可信来源的角色、权限、会话等概念是否各自拥有领域模型、Repository 和持久化关系，并通过 `Iam*`、`Application*`、`OAuthSession*` 等稳定所有权前缀同步区分其直接 Repository、领域服务和边界模型；不能仅依赖类型枚举、固定业务 ID 或调用方约定隔离。IAM 的 Schema 测试明确证明内部角色与外部应用角色使用独立表，认证与角色管理测试证明 IAM 管理权限只读取内部角色链路，Session 转换测试证明 OAuth2 持久化字段不会替代领域会话身份。通用静态扫描无法仅凭 `Role`、`Permission`、`Session` 等类名判断两个概念是否属于同一边界，只有建立可声明的领域所有权元数据并能关联 Entity/TableName、Repository 与调用链后，才升级为自动门禁。

MapStruct 规则当前为 **Review-only**：Review 检查 `@Mapper` 是否至少保留一个由 MapStruct 生成的声明式映射，并优先让框架处理同名属性和枚举。只有检查器能够可靠解析接口方法、排除继承方法、注解辅助方法及含业务逻辑的显式转换，并具备生成映射、纯 `default` 接口和合法混合接口 fixture 后，才升级为自动检查。

HTTP 聚合查询请求规则当前为 **Review-only**：Review 检查参数较多且共同表达一个查询意图的接口是否使用 `*Request + @ModelAttribute`，并由接口 Transformer 一次性构造应用层 Query，而不是在 Controller 中逐字段转换枚举和值。固定参数数量无法证明对象是否形成稳定语义，少量技术参数也不应被强制包装。只有结构化检查能够识别 Controller 参数、Request 绑定和对应 Transformer 调用，并具备简单接口、多参数查询和合法特殊绑定的正反 fixture 后，才升级为自动检查。

时间与时区规则采用 **聚焦行为测试 + Review-only** 边界：`TimeRange` 单测证明闭区间不变量和持续时间，Audit 的应用 Transformer 与 Repository 测试证明领域查询条件使用该值对象；接口 Transformer 测试证明 epoch milliseconds 与 `Instant` 的双向转换，持久化测试证明 `TIMESTAMP(3)` 对应 Entity `Instant`，UI 和导出测试分别证明浏览器 IANA 时区展示与服务端显式时区格式化。全仓中两个时间字段可能表示区间，也可能是创建、失效等独立事实，`LocalDateTime` 也可合法表达本地日历语义和框架技术时间，因此不按字段数量、名称或类型增加全仓正则禁令。Review 检查新增或触碰的领域查询区间是否使用 `TimeRange`、绝对时间是否跨层保持 `Instant`、浏览器边界是否使用时间戳，以及人可读输出是否显式选择时区。只有能够结合契约所有权和字段语义稳定区分区间、独立时间事实、本地日历值及持久化技术字段，并具备跨模块正反 fixture 后，才升级为结构化检查。

数据库 JSON 格式规则当前为 **Review-only**：Review 检查数据库 JSON 列是否由 MyBatis TypeHandler 等持久化机制编解码，Entity 是否使用持久化原生属性承接数据，以及领域 Transformer 是否只做对象映射。`JsonUtils` 在 Bolt、Gossip、缓存和第三方协议适配中仍是合法用法，无法按包名可靠判断某次 JSON 转换是否对应数据库列。只有结构化分析能够关联 Entity 字段、TypeHandler、Transformer 映射并排除非持久化 JSON 场景，且具备正反 fixture 后，才升级为自动检查。

MyBatis JSON TypeHandler 采用 **插件统一装配 + 聚焦测试**：`im-datasource` 将应用 `ObjectMapper` 设置给 `JacksonTypeHandler`，保证 Java Time 与项目 Jackson 配置一致；`ImDatasourceAutoConfigurationTest` 使用包含 `Instant` 的 Claims 验证序列化。业务 Repository 不得为规避 TypeHandler 配置而手工改写时间或 JSON 结构。

数据库 Entity 枚举与基类规则采用 **Enforced + Review-only** 边界：SQL checker 强制每个建表语句声明 `id/create_time/update_time/is_deleted`，Java style checker 拦截 MyBatis Entity 中以 `String` 声明的 `action/status/result/outcome/kind/type` 字段，以及同时重复声明 `id/createTime/updateTime/isDeleted` 却未继承 `BaseEntity` 的标准形状；模块聚焦测试补充验证所有 Entity 的基类。`targetType`、`eventType` 等字段可能是协议扩展值，只有确认其为当前领域闭集时才由 Review 要求数据库层枚举。数据库技术主键与领域业务 ID 是否需要同时存在，也必须依据对象是否拥有稳定业务身份判断，不按类名或字段数量机械推断。

MyBatis Entity 敏感输出规则采用 **聚焦测试 + Review-only**：IAM 持久化映射测试证明管理员密码、OAuth Client Secret 与 Token 摘要不会进入 Entity `toString()`。Review 检查新增认证材料是否使用 `@ToString.Exclude` 或不生成 `toString()`；不能仅按 `secret/password/token` 字段名全仓禁止，因为业务响应可能合法包含一次性密钥、Token 生命周期信息或脱敏展示值。只有结构化检查能限定 MyBatis Entity、识别 Lombok 展开结果并覆盖摘要、普通业务字段与合法边界响应后，才升级为静态门禁。

MyBatis Service 分层规则采用 **Enforced + Review-only** 边界：新增或触碰的 DDD 业务模块应通过 `infrastructure.mybatis.service` 取得表级 CRUD、Wrapper 和可复用查询能力，Repository 技术实现不直接注入或编排 Mapper。仅服务于一个领域 Repository 的查询语义由 Repository 使用 Service 组装，无需创建只为跨层透传的持久化 `*QueryCondition`；确需复用或自定义 SQL 时再下沉到 Service 或 Mapper/XML。Java style checker 已拦截标准 Repository 实现直接导入 Mapper；框架 SPI 是否承担入站入口职责则由 Review 和聚焦依赖测试判断，不能仅按 `infrastructure/security` 包名禁止 MyBatis，因为纯持久化适配器本身可以合法依赖 MyBatis Service。扩展自动检查前必须具备 Repository、入站框架 SPI、纯技术委托 SPI、合法 MyBatis Service 和自定义多表 SQL 的正反 fixture。

框架 SPI 入口规则采用 **聚焦依赖测试 + Review-only**：IAM 的 `OAuthAuthorizationServiceAdapterTest` 证明 Spring Authorization Server 的授权入口依赖 `OAuthAuthorizationAppService`，且不直接依赖领域 Repository 或 MyBatis 类型。Review 判断某个 SPI 是完整用例入口还是 `PasswordEncoder` 一类纯技术能力委托；仅凭实现接口名称或包路径无法稳定区分。只有建立明确的 SPI 角色清单并覆盖两类合法形态后，才升级为通用静态门禁。

MyBatis Mapper 扫描规则采用 **Enforced + Review-only** 边界：Java style checker 拦截扫描范围内 Mapper 已逐个使用 `@Mapper` 时仍存在的空 `@MapperScan` 配置类，并拦截 Repository 技术实现直接导入 Mapper。逐个注解与集中扫描都是合法方案，但同一扫描范围只保留一种；多数据源绑定、跨应用根包扫描或包含额外配置逻辑的场景仍由 Review 判断。公共 `im-datasource` 始终不得通过业务包名承担 Mapper 扫描。

横切关注点规则当前为 **Review-only**：Review 检查重复的前后置技术策略是否已有稳定注解能力，以及切面是否通过显式接口或注解契约取得上下文、是否保持异常与顺序语义。业务是否真的属于横切关注点无法仅靠方法形态可靠判断，因此不做全仓正则扫描；具体能力应通过注解覆盖清单、切面顺序和成功/失败行为测试建立窄门禁。只有能够按已登记注解识别遗漏、同时排除领域规则和一次性编排后，才考虑升级为通用结构检查。

全限定类名规则当前为 **Review-only**：源码正则无法可靠区分类型引用、字符串常量、Javadoc 示例和同名类型冲突。Review 检查新增或触碰的生产代码是否通过 import 保持表达式可读，并仅在同一文件确有同名类型冲突时保留局部全限定名。只有结构化 Java 解析器能识别类型引用并具备同名冲突、字符串和注释的正反 fixture 后，才升级为自动检查。

聚合删除与 Repository 生命周期规则当前为 **Review-only**：Review 检查聚合删除方法修改的状态是否确实由后续 `save` 持久化，以及逻辑删除场景是否仅执行领域资格校验后交给 Repository 删除。只有结构化分析能够关联同一用例中的聚合调用、Repository `save/remove` 调用和持久化映射，并具备软删除与状态保存两类正反 fixture 后，才升级为自动检查。

值对象、实体身份和领域命名规则采用 **Enforced + Review-only + 聚焦单测**：Java style checker 拦截 `Identification` 子类使用 `onlyExplicitlyIncluded = true` 定制业务 ID 专属相等性，允许 `@EqualsAndHashCode(callSuper = false)` 排除技术主键；Review 检查值对象的规范构造入口是否完整执行不可绕过的不变量、承担校验的 Builder 是否手写 `build()`、持久化技术主键是否仅在 Repository 构建后回填、聚合字段是否适合参与相等性，以及类型名称是否表达领域事实而非内部格式化手段。具体值对象和聚合使用单元测试证明归一化、脱敏、容量、序列化、相等性、Builder 校验和主键回填行为；其他构造器、Builder、record 与持久化重建语义仍不做脆弱静态推断。只有引入能区分领域 Builder、Lombok 生成器、MapStruct 目标属性与 Repository 回填路径的 Java 结构化解析，并具备重建聚合、非领域 DTO 和纯便利 Builder 的正反 fixture 后，才升级为通用静态门禁。

技术主键、业务 ID、可识别编码和聚合基数同样采用 **Review-only + 聚焦单测**：Schema 测试可以证明目标表同时具有自增 `id`、唯一业务 ID 以及关联表使用的业务列，Repository 测试可以证明 `pkId` 只在重建和新增持久化后回填；但通用静态 checker 无法可靠判断某个 `*_id` 的领域语义，也无法仅凭类名判断两个 OAuth Grant Type 是否应共享同一客户端聚合，因此不做字段名正则推断。只有建立结构化的 Entity/DDL 关系模型，并能从明确元数据识别聚合业务 ID 与外键语义后，才考虑升级为通用门禁。

Web 基础能力边界采用 **Enforced + Review-only**：ArchUnit 与 Maven 依赖测试阻止 `im-web` 依赖 Session、IAM 或运行时业务模块，并确认 Admin、Monitor、IAM SDK 显式接入统一 Web 能力；聚焦测试证明请求上下文建立、传播、清理及 `HttpResult` 的 MVC/Security 行为。插件中的字符串路径是否属于业务 Controller、重复响应对象是否形成第二套协议、某个上下文属性是否包含业务或安全语义，需要结合路由和调用方判断，当前由 Review 负责。只有结构化检查能够解析 Controller/Security 映射及配置绑定，并具备 Actuator、Swagger、OAuth 回调等技术路径的 false-positive fixture 后，才升级这些语义规则；不得用业务前缀正则代替所有权判断。

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

| 规则 | 所有者 | 当前传感器 | 退出条件 |
|---|---|---|---|
| `im-iam-sdk` 不依赖 `im-iam-server` 实现 | Architecture | Maven/ArchUnit 模块依赖检查 | IAM 模块边界被新协议替代 |
| Admin、Monitor 不实现第二套管理员密码认证与安全 Session | IAM design | `RuntimeDependencyPolicyTest` 所有权包路径检查与 Review | 引入能识别认证语义且保持低误报的结构化检查 |
| 健康请求实时 Introspection，仅可用性错误使用不超过五分钟缓存 | IAM reliability | SDK 行为测试 | 授权协议或失效模型变更 |
| Token 加密、权限语义和撤销覆盖；授权状态变为撤销后，本地资源认证与远程 Introspection 都必须立即返回失效 | Security | OAuth2 授权持久化、撤销、本地 Introspector 与远程 Introspection 聚焦行为测试；跨存储数据流 Review-only | 能低误报跟踪跨存储数据流 |
| IAM Token 的 `appKey` 表达来源应用，`aud` 表达目标应用；Spring 默认 Introspection Provider 返回签发 Claims，资源服务按自身应用标识校验 `aud`，不使用自定义 Provider 重复标准协议 | IAM security design/Security | IAM Claims Customizer、SDK 标准响应映射与 Audience 拒绝测试；协议语义 Review-only | OAuth Token、资源受众或 Introspection 主体模型变更 |
| Spring 默认 Introspection 以未知 Token 类型查询授权时，仅将原始 Token 恢复到摘要匹配的凭据，避免已消费授权码遮蔽有效 Access Token | IAM security design/Security | `OAuthAuthorizationServiceAdapterTest` 浏览器授权回归测试；摘要匹配语义 Review-only | Spring Authorization Server 不再使用 `findByToken(token, null)` |
| OAuth JSON Claims 进入 Spring Authorization Server 时将 `iat/exp/nbf` 恢复为 `Instant`；数据库 Transformer 保留 JSON 边界值，不承担框架类型恢复 | IAM adapter/Security | `OAuthAuthorizationServiceAdapterTest` + 真实 Introspection 流程；其他标准 Claim 类型 Review-only | Claims 不再通过 JSON Map 持久化 |
| IAM SDK 配置以当前应用为所有者，Web、Catalog OAuth Client 与本地 Session 使用一个嵌套层级；权限目录同步不复用浏览器 BFF Client，机器客户端只允许 `CLIENT_CREDENTIALS` 和 `iam.catalog.write` | IAM configuration/security design | `IamPropertiesTest`、`ImIamSdkAutoConfigurationTest`、三个管理应用配置绑定测试与 `IamPermissionCatalogRegistrarTest`；跨模块配置语义 Review-only，IAM 中实际 Client 注册状态由 Readiness 与运维检查确认 | IAM SDK 接入模型或权限目录同步协议被替代 |
| SDK 与宿主自有的同类型基础设施 Bean 按稳定名称创建并显式限定注入，不因同时声明多个 `RestClient` 而跳过专用 Client 或注入错误实例 | Configuration/SDK | `ImIamSdkAutoConfigurationTest#createsDedicatedIamRestClientWhenApplicationDefinesAnotherRestClient`、`HttpBrokerManagementClientWiringTest` | SDK 或宿主不再通过 Spring Bean 提供多个专用 HTTP 客户端 |
| IAM、Admin、Audit、Monitor 管理后台统一使用 Vue 3、Element Plus、Element Plus Icons、质量脚本和同名 Console Token；禁止浏览器原生 `confirm/alert`，且不得跨应用共享 UI 源码 | Management UI | `scripts/check-management-ui.sh` 与正反 Harness fixture；组件行为由各 UI 聚焦测试 | 管理后台技术栈或独立部署边界被替代 |
| 管理后台采用安静高密度布局、一个行内主操作、Drawer 编辑/详情、明确危险确认、可恢复数据状态和服务端筛选 | Management UI/UX | Drawer、表格、筛选和数据状态聚焦测试；视觉层级、操作优先级、文案与响应式构图 Review-only，并以临时桌面/移动截图验收 | 已批准新的 Management UI 设计 |
| 本地管理端口固定为 IAM `18090`、Audit `18091`、Monitor `18092`、Admin `18093`，Issuer、OAuth 回调、Vite 代理和前端控制台链接默认值必须一致 | Management configuration/security | 配置绑定测试、OAuth Client 迁移 SQL 测试和 Management UI Harness | 本地拓扑设计被新的统一入口替代 |
| IAM 内部角色与应用角色保持两套关系边界；应用角色分配只替换指定应用的关系，权限或 Client 访问配置变更撤销受影响 OAuth 授权；关系资源库继续使用 `findRoles/replace`，不引入无业务收益的 Assignment 聚合 | IAM management authorization design | IAM 应用服务、OAuth Client 领域测试与 IAM Console API/component tests；关系范围和撤销语义保留 Review-only，直到出现跨模块重复实现 | 关系模型改为独立授权聚合或权限快照改为实时计算 |
| OAuth 最终授权状态统一通过 Repository `save` 持久化；查询 SPI 保持无副作用，Authorization Code 单次消费和 Refresh Token 轮换由 Spring 默认 Provider 管理；Repository 不暴露兑换、刷新或历史重放动作 | DDD model/Coding Guide | `IamTokenPersistenceTest`、Spring 默认 Provider 契约与 Repository 查询测试 | OAuth 持久化或 Spring Provider 规范被替代 |
| IAM SSO 绝对过期对当前请求立即失效；已知与未知管理员邮箱均执行等价 BCrypt 校验工作 | IAM security design/Security | SSO Filter 与管理员认证聚焦行为测试 | 会话装载顺序或密码验证实现变更 |

IAM 静态检查只识别管理应用中明确的密码认证和认证 Session Repository，不扫描普通业务
密码更新或一般 Redis Repository。跨应用权限隔离、密钥管理和 Token 是否进入日志属于数据流
语义，现阶段必须由 Review 与行为测试共同确认。

### 管理监控边界

| 规则 | 所有者 | 当前传感器 | 退出条件 |
|---|---|---|---|
| Monitor 只读查询使用有界线程数和有界待执行任务容量，节点级超时与失败不得阻塞或丢弃其他健康节点结果 | Reliability | Monitor 并发、超时、拒绝和部分失败聚焦行为测试；执行器队列形状 Review-only | 查询模型不再使用进程内并发扇出 |
| Monitor 的 HTTP Response、应用 DTO 和 Broker 管理协议模型相互隔离，通过 Transformer 完成边界转换 | Coding/Architecture | 当前 Review-only；待 Monitor DDD/CQRS 基线收敛后评估 ArchUnit 与结构化检查 | Monitor 不再承担 HTTP 聚合边界 |

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

`verified` 只表示对应入口在当前 HEAD/worktree 成功执行；`present` 表示入口存在但没有当前证据；`stale` 表示历史结果与当前代码状态不一致；`degraded` 表示当前执行失败。顶层 `incomplete` 表示尚无 full evidence，不能用 drift 成功冒充完成。无法计算 fingerprint 的升级前记录为 `unavailable`，不得支撑当前通过结论。

根 `PROGRESS.md` 只保存 active plan、当前任务、阻塞项和 report 入口，不复制验证状态、耗时或测试数。机器结果由 `.harness/report.json` 唯一拥有，否则更新 PROGRESS 本身会改变 worktree fingerprint，形成证据自引用。

这些传感器信号不等于源码质量评分。耗时用于发现反馈变慢，失败和台账用于定位噪声，不能通过减少测试或降低规则改善数字。源码质量评分必须使用 `QUALITY_MODEL.md` 定义的机器证据 + 独立 AI Reviewer 流程；任何没有当前 review scope fingerprint 的结果都不能作为交付等级。

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
