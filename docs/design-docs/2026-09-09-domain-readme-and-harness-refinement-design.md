# 领域 README 与 Harness 优化设计

## 背景

根 `README.md` 当前同时承担项目入口、DDD 概念说明、全部业务子域与限界上下文、统一语言、运行架构和 Harness 原理。开发者可以在一处看到大量信息，但领域知识离所属代码较远，Account、Social、Message 等模块 README 反而缺少足够的领域说明。随着管理域扩展，这种集中维护方式容易产生重复和衰减。

当前 Harness 已有分层验证、规则矩阵、执行计划、反馈台账和验证报告，但任务范围、跨会话状态、完成证据和失败归因主要散落在不同文档中。`HARNESS_GUIDE.md` 也同时承载治理流程、规则总表和大量规则解释，后续继续追加会提高发现成本。

本次仅调整文档、文档检查和 Harness 工作流，不改变生产运行行为、协议或公共契约。

## 调研依据

本设计对照以下本地源码快照：

- `walkinglabs/learn-harness-engineering`，本地路径 `/Users/kc/Code/open-source/learn-harness-engineering`，调研 commit `77e7a3e`。其第二至第十二讲将 Harness 拆为指令、工具、环境、状态与反馈，并强调仓库事实源、就近文档、任务范围、完成证据、运行观测和清洁状态。
- DTPet 仓库，origin `git@codeup.aliyun.com:60375bf4b1b9f4a16c0208ee/unicorn/dtpet.git`，调研 revision `e9fd719f41374a2c2e3525254c24bbf14c5cee7d`，本地 worktree `/Users/kc/Code/work/buc-bigdata/dtpet/.worktrees/feature-kc-my-character-harness-requirement`。其根 `AGENTS.md`、`docs/references/HARNESS_GUIDE.md`、`docs/harness/README.md` 和 `docs/harness/records/2026-09-07-harness规则融合记录.md` 展示了上述原则在 Java Maven 仓库中的一次落地。

可吸收的核心不是目录或模板本身，而是以下机制：仓库作为事实源、信息靠近所有者、长任务显式记录范围和状态、完成结论必须附带验证证据、失败先归因到 Harness 子系统、会话结束保持可恢复状态。

以下内容不直接引入：通用 `feature_list.json`、自动 Agent 循环、三 Agent 工作流、统一 `PROGRESS.md`、复杂运行 trace 和完整模板工作区。im-chat 已有 `docs/design-docs`、`docs/exec-plans`、`docs/feedback` 与 `.harness`，再复制一套目录会形成竞争事实源。

## 文档所有权

### 根 README

根 `README.md` 保留：

1. 项目定位和核心能力。
2. 精简的子域、限界上下文与所属模块导航。
3. 跨模块运行链路和状态所有权摘要。
4. 模块地图、外部入口、本地运行和验证入口。
5. 面向开发者的文档导航。
6. 面向开发者的 Harness 设计说明，包括引入理由、三个核心问题、五项设计原则、反馈闭环图、简明的质量门禁分工，以及 `quick`/`full` 验证入口。

根 README 不再保存完整 DDD 教程、全量领域对象清单或各上下文内部规则。通用 DDD 分层和编码约束由 `CODING_GUIDE.md` 维护；具体统一语言和业务不变量由所属模块 README 维护。

根 README 负责让开发者理解 Harness 为什么存在、怎样融入日常开发，不展开运行治理细节。`HARNESS_GUIDE.md` 负责五个子系统地图、传感器清单、规则矩阵、规则生命周期、失败归因和清洁状态。两者通过链接衔接，不复制对方拥有的完整内容。

### 领域模块 README

拥有业务模型的聚合模块 README 使用一致的信息骨架，但只写真实存在的内容：

- 模块目的与子域类型。
- 模块承载的限界上下文。
- 统一语言，包括主要聚合、实体、值对象、领域服务、领域事件和只读投影。
- 关键业务不变量与生命周期。
- 跨上下文协作、公开契约和反腐转换边界。
- 数据与运行状态所有权。
- 子模块导航和聚焦验证命令。

首轮迁移覆盖 `im-account`、`im-social`、`im-message`、`im-iam` 与 `im-audit`。其中一个部署模块可以承载多个有明确内部边界的限界上下文，例如 Social 内的好友与群组、Message 内的聊天与消息。README 必须明确这种“模型边界不等于部署边界”的关系。

`im-admin` 和 `im-monitor` 主要承担管理用例与查询投影，不为了格式一致强行声明不存在的聚合或领域事件。它们继续说明业务所有权、权限边界和外部事实来源。

### 技术模块 README

`im-common`、`im-plugin`、`im-gateway`、`im-broker` 和 `im-test` 等技术、运行或测试模块不套用业务 DDD 模板。它们说明模块职责、状态模型、接口、依赖边界、配置和验证方式。

`ARCHITECTURE.md` 继续作为跨模块运行拓扑、依赖方向和数据所有权的当前事实源。`AGENTS.md` 只承担 Coding Agent 的执行步骤、硬约束、文档路由、验证和完成标准；领域解释通过明确指针路由到模块 README。

### Architecture 分层

根 `ARCHITECTURE.md` 使用中文解释性正文，负责全仓运行拓扑、限界上下文关系、模块依赖方向和跨模块数据所有权。局部 `ARCHITECTURE.md` 只在模块具有复杂内部拓扑、多个运行职责、独立一致性模型或重要跨边界流程时创建，负责解释该模块内部组件关系及稳定设计约束。

本轮为 `im-gateway`、`im-broker` 和 `im-service/im-message` 增加局部 Architecture：

- Gateway 说明 HTTP 与 WebSocket 两类入口、认证和可信上下文、连接所有权、Broker 协作与协议边界。
- Broker 说明注册表、用户归属、迁移、Gossip、帧路由、会话关闭控制与最终一致性边界。
- Message 说明 Chat/Message 上下文关系、MySQL/Redis 状态分工、事务提交后通知、ACK 与有界重投。

Facade、SDK、聚合 POM 和单一能力插件不机械创建 Architecture；这些模块没有独立内部架构时由 README 说明契约、依赖和用法。后续模块只有满足上述触发条件才增加局部 Architecture，避免与 README 和根架构形成三份重复事实。

### 文档语言

`AGENTS.md` 等仅供 AI 使用的指令文档保持英文，减少执行规则在翻译中的歧义。面向开发者或团队共享的 `README.md`、`ARCHITECTURE.md` 和 `docs/**` 使用中文解释性正文；代码标识符、命令、协议名、框架名、精确状态值、skill 名称、既定技术术语和技术图标签在英文更清晰时保留英文，不做逐 token 翻译。

语言是否自然、技术词是否应保留英文依赖上下文，机械扫描无法可靠判断，因此该约定登记为 Review-only。Review 关注完整叙述是否面向目标读者清晰一致，而不是消除英文 token。

## WebSocket Gateway 传输说明

`im-gateway/im-ws-gateway/README.md` 与 Server README 增加 Socket 与容器选型说明，并以当前实现为依据：

- 浏览器通过 TCP 上的 HTTP Upgrade 建立 WebSocket，升级后在同一全双工长连接上交换帧；Gateway 维护连接生命周期、本机 Channel 和心跳状态。
- `im-ws-gateway-server` 直接依赖 Netty 和基础 `spring-boot-starter`，没有 Servlet Web/Tomcat 运行时。当前 pipeline 使用 `HttpServerCodec`、`HttpObjectAggregator`、`WebSocketServerProtocolHandler`、空闲检测和自定义帧 Handler。
- Netty 与 Tomcat 都支持基于 NIO 的 WebSocket，差异不在“是否支持异步长连接”，而在应用希望直接控制的抽象层级。
- 专用、以连接为中心的 Gateway 通常会受益于对 EventLoop、Channel、pipeline、writability、资源上限和协议处理的直接控制。Netty 为这些能力提供显式模型，但代价是应用必须承担更多生命周期、背压、资源治理和协议细节，复杂度更高。
- Tomcat 更适合 WebSocket 需要与现有 Servlet/MVC、安全过滤链、会话和运维体系共享容器的应用，由容器承接更多标准 Web 生命周期。它不是能力不足或性能必然较差的备选。
- im-chat 当前由 WS Gateway 持有本机 Channel、连接身份、心跳、协议 pipeline 和写入状态，HTTP 短请求另由 `im-http-gateway` 承载，因此直接使用 Netty 与现有状态所有权和控制需求一致。
- 如果未来 WebSocket 需要与 Servlet/MVC 端点共享部署、安全链和会话模型，或者连接级控制显著减少而容器统一治理收益更高，应重新评估 Tomcat 或其他 Servlet 容器。文档不使用流行度或普适性能结论证明当前选型。

## 复杂运行模块 README

Gateway 与 Broker 的聚合 README 解释整体职责、关键状态、端到端流程和技术难点；具体 Server README 解释组件协作、处理顺序、失败路径、配置和排查。Architecture 保存稳定边界与一致性决策。三层文档通过链接衔接，不复制类清单和同一流程正文。

Gateway 文档补齐 HTTP 认证与可信上下文重建、WebSocket 握手与连接注册、上行帧、下行推送、ACK、会话关闭和异常断线清理。Broker 文档补齐实例与 Gateway 注册、用户连接归属、成员变化迁移、Gossip digest/delta、上行/下行帧和关闭控制的时序图，并明确部分失败、最终一致和本机状态边界。

动态调用链、迁移、Gossip、ACK 和决策分支使用 Mermaid `sequenceDiagram` 或 `flowchart`；数据所有权使用 Markdown 表格。只有节点很少且没有复杂分支的静态依赖摘要保留 ASCII，避免中文宽度和后续增删导致图形错位。

技术难点只记录能从当前实现和测试证明的内容，例如阻塞认证离开 EventLoop、双向连接索引、迁移先远端确认后本地删除、删除墓碑 TTL、逐连接写入结果和诊断隔离，不写没有测量依据的容量或性能数字。

## Message 存储模型

Message 聚合 README 提供开发者可快速定位的存储地图，Server README 展开实现细节：

- MySQL `im_chat_message` 是聊天和收件箱事实源，四张主表分别保存用户私聊会话、用户群聊会话、私聊收件箱副本和群聊收件箱副本。
- 私聊为发送者与接收者分别维护消息副本；群聊按群成员维护用户自己的会话和收件箱副本。唯一索引同时承担 Token 幂等和用户视角消息唯一性。
- 当前群消息采用 fanout-on-write，以按成员写入 Inbox/Chat 的写放大换取历史、未读和状态读取的局部性；文档对照 fanout-on-read 的共享消息、成员可见性、read cursor 和读热点代价，并给出基于群规模/QPS/事务耗时数据的复评条件。
- Redis `ImChatView` 是带 TTL 的当前查看状态，只影响未读判断，不成为聊天或消息事实源。
- Redis `ReceiptTask`、attempts 与分片延迟队列保存通知确认和有界重投状态，不与 MySQL 事务原子提交，也不是 Outbox。
- Repository、MyBatis Service/Entity/Mapper、缓存与 Redis Store 的边界按当前源码记录；文档明确读写顺序、失效/过期语义、失败窗口和恢复来源。

文档不把缓存命中结果描述成权威事实，也不承诺当前实现不具备的 exactly-once 或跨 MySQL/Redis 原子性。

## 脚本自描述规范

`scripts/*.sh` 每个文件在 shebang 后增加中文文件头，说明用途、输入、输出或副作用、依赖和退出码。脚本函数在定义前说明参数、输出/返回约定、关键算法或失败语义；简单打印、直接委托和显而易见赋值不逐行注释。

注释解释为什么存在该步骤、扫描范围为何这样选择、失败如何传播以及哪些状态会被写入，不重复翻译每条 shell 命令。测试脚本还说明 fixture 隔离、正反例和清理责任。该约定由 Harness Guide 登记为 Review-only；自然语言完整性不使用脆弱的注释数量检查。

## Harness 调整

### 五个子系统

`HARNESS_GUIDE.md` 增加 im-chat 的五子系统地图：

| 子系统 | 当前事实源或入口 | 本次改进 |
|---|---|---|
| 指令 | 根/模块 `AGENTS.md`、Architecture、References、模块 README | 明确 README 领域所有权和按任务触发的文档路由 |
| 工具 | Maven、`scripts/verify.sh`、ArchUnit、静态检查脚本 | 扩大 Markdown 链接检查到受版本控制的模块文档 |
| 环境 | README、本地配置、类型化配置和运行命令 | 通过执行计划记录任务所需环境与不可用依赖 |
| 状态 | Design、active/completed plan、技术债务与 Git | 增加 Sprint Contract、WIP=1 和窄状态枚举 |
| 反馈 | 测试、检查器、运行指标、验证报告和反馈台账 | 增加失败归因与清洁状态判定 |

该地图描述已有能力及缺口，不以增加脚本数量作为目标。

### 执行状态与完成证据

`docs/PLANS.md` 在现有执行计划生命周期中增加：

- Sprint Contract：目标、范围、不做事项、验收标准、风险和依赖输入。
- WIP=1：一份活跃计划同一时刻只有一个任务处于 `active`。
- 状态枚举：`not_started`、`active`、`blocked`、`passing`。
- `passing` 必须引用实际验证命令或可复查证据。
- 跨会话恢复信息直接保存在活跃计划中，不新建全仓 `PROGRESS.md`。

小型单文件维护仍不要求执行计划，避免把 Harness 变成文档负担。

### 失败归因与清洁状态

Harness 失败先归因到任务规格、上下文、环境、工具权限、状态、验证反馈、范围或架构边界，再选择修复规格、文档、工具、测试或代码。归因用于找到失效子系统，不替代缺陷修复。

任务结束前确认：风险匹配的验证已执行或说明未执行原因、文档和计划状态已更新、临时工件未进入版本控制、未留下无退出条件的 TODO、最终说明包含实际证据和残余风险。该要求进入根 `AGENTS.md` 和 Harness 指南，不额外创建每次都要填写的清单文件。

### 规则矩阵

现有规则矩阵继续作为“规范所有者、Sensor 与 Review 边界”的登记处。本次整理重复解释并强化以下原则：

- 完整规则正文只存在于 owning specification。
- 矩阵只登记状态、传感器和自动化条件，不复制大段业务背景。
- 领域模块 README 是业务语言事实源，不成为自动检查规则正文。
- 新增检查必须有明确诊断和正例、反例、必要的 false-positive fixture。

本轮不把领域术语表做成静态类名扫描。领域归属和术语完整性需要业务判断，采用模块 README、聚焦测试和 Review；机械检查只验证文档存在性与链接完整性。

### Git 跟踪的 Markdown 传感器

Markdown 链接检查通过 `git ls-files -z -- '*.md'` 获取范围，只扫描 Git 已跟踪的 Markdown；未跟踪的草稿或临时文件不进入门禁。读取使用 NUL 分隔结果，避免空格等合法路径破坏枚举。

传感器对枚举失败和文件缺失采用失败关闭：`git ls-files` 启动失败或非零退出必须形成违规，不能静默跳过；Git 已跟踪但工作树中缺失或不可读的 Markdown 也必须报告源文件。单个文件读取失败后继续检查其余文件，使同一次运行仍能报告后续断链，不因前一个错误而 fail-open 或丢失其他诊断。相对链接继续报告源文件和目标；`HTTP(S)`、`mailto:`、纯锚点与空路径不作为本地文件解析。

### 外部 Harness 规范吸收审计

对 `learn-harness-engineering` 与 DTPet 实践逐项复核：

- 已应用：仓库作为事实源、渐进披露、Instruction/Tools/Environment/State/Feedback 五个子系统、执行计划 WIP、证据化完成、分层验证、运行可观测性、规则生命周期、失败归因和清洁状态。
- 本轮补强：复杂模块的局部 Architecture、Gateway/Broker/Message 可发现的流程与状态说明、脚本自描述，以及新增 Markdown 的失败关闭验证。
- 暂不应用：全局 `PROGRESS.md`、通用 `feature_list.json`、自动 Agent loop、多 Agent 固定工作流和第二套 Harness 模板目录。当前仓库已有 active plan、Git 状态、验证报告和反馈台账；引入这些机制会产生竞争事实源或与用户当前协作方式冲突。
- 重新评估条件：当活跃任务需要独立机器队列、跨会话恢复成本持续上升、并发工作需要结构化协调，或现有状态文件无法表达任务依赖时，再评估 feature list、自动 loop 或更强状态机。

外部 `tools/audit-harness.sh` 的原始结构评分仅作为输入，不作为验收门禁。它固定要求 npm/pip 风格 lockfile、根 `PROGRESS.md`、Makefile 和 feature list，无法识别 Maven reactor、`scripts/verify.sh` 与 active execution plan 的等价能力；本仓库按机制是否闭环评估，而不是为提高分数复制模板文件。

吸收审计进入 Harness Guide，说明每项机制的现有落点和未采用原因，不把外部项目目录原样复制到本仓库。

## 修改范围

- 精简根 `README.md` 并重建文档导航。
- 完善 Account、Social、Message、IAM、Audit 聚合模块 README 的领域说明。
- 补充 WS Gateway 聚合与 Server README 的 Socket、Netty 和 Tomcat 选型说明。
- 深化 Gateway、Broker 的实现流程、技术难点与故障边界说明。
- 补充 Message 的 MySQL、Redis、缓存、幂等与一致性存储模型。
- 说明 Message fanout-on-write 与 fanout-on-read 的读写放大权衡和替代方案触发条件。
- 将根 `ARCHITECTURE.md` 调整为中文全仓事实源，并增加 Gateway、Broker、Message 三份局部 Architecture。
- 为 `scripts/*.sh` 增加中文文件头与非显然函数契约注释。
- 更新根 `AGENTS.md`、`docs/PLANS.md` 和 `docs/references/HARNESS_GUIDE.md`。
- 扩展 `scripts/check-drift.sh` 的本地 Markdown 链接输入范围，并补充 Harness fixture。
- 更新相关索引和本设计对应的执行计划。

不修改 Java 生产代码、协议、配置默认值、数据库或模块依赖。

## 执行计划

实施范围、任务状态、跨会话恢复信息和验证证据记录在[领域 README 与 Harness 优化实施计划](../exec-plans/completed/2026-09-09-domain-readme-and-harness-refinement.md)中。

## 验证

1. 对照领域源码核验 README 中的模型名、事件和所有权。
2. 为 Markdown 链接范围扩展增加失败与通过 fixture。
3. 运行 `./scripts/test-harness.sh`。
4. 运行 `./scripts/verify.sh quick`。
5. 运行 `./scripts/verify.sh full`。
6. 对照 Gateway、Broker、Message 源码、DDL、Redis key 和测试核验文档事实。
7. 检查全部脚本具有文件契约，非显然函数具有中文职责或失败语义说明。
8. 运行 `git diff --check` 并复核没有无关改动。
