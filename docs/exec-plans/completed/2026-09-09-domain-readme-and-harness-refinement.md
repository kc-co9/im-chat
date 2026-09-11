# 领域 README 与 Harness 优化实施计划

> **执行本计划的 Agent 必须遵守：** 用户已明确要求后续不使用 subagent，由当前 Agent 顺序执行。各步骤使用 checkbox（`- [ ]`）跟踪；除非用户明确要求，否则不要创建 Git commit。

**目标：** 将详细领域知识从根开发者入口迁移到所属模块 README，说明 WS Gateway 的 Netty 选型，并在不创建竞争工作区的前提下强化现有 Harness 生命周期。

**架构：** `README.md` 作为简明的开发者入口和 Harness 理念说明，模块 README 保存局部业务或运行事实，`ARCHITECTURE.md` 保存跨模块事实，`AGENTS.md` 作为 AI 执行路由。`HARNESS_GUIDE.md` 拥有五个子系统、传感器、规则矩阵、生命周期、失败归因和清洁状态等运行细节。通过明确 Harness 子系统、任务状态、失败归因和清洁状态规则，扩展现有的设计、计划、反馈与验证闭环。

**技术栈：** Markdown、Bash、基于 Ruby 的本地 Markdown 链接校验，以及仓库现有的 Maven/ArchUnit Harness。

---

## 迭代契约（Sprint Contract）

### 目标

实施[领域 README 与 Harness 优化设计](../../design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md)。

### 范围

- 根文档与模块文档的所有权。
- Account、Social、Message、IAM 和 Audit 的领域文档。
- WS Gateway 的 Socket、Netty 与 Tomcat 选型说明。
- 根 README 面向开发者的 Harness 理念、三个问题、五项原则、反馈闭环、简明门禁分工和 `quick`/`full` 入口。
- 现有 Harness Guide、计划生命周期和 Agent 完成规则；运行细节只由 `HARNESS_GUIDE.md` 拥有。
- Git 跟踪的 Markdown 链接校验，包括模块 README、枚举失败和缺失/不可读文件处理。
- 当前任务涉及的开发者文档使用中文解释性正文，AI 专用 `AGENTS.md` 使用英文；技术英语按语境保留。
- Gateway、Broker 的实现流程、故障边界和技术难点文档。
- Message 的 MySQL、Redis、缓存、幂等与一致性存储说明。
- 根 Architecture 与 Gateway、Broker、Message 局部 Architecture 的职责分层。
- 全部 `scripts/*.sh` 的中文文件契约和非显然函数注释。
- 对外部 Harness 规范的应用情况、未采用项和重新评估条件进行审计。

### 非目标

- 生产 Java 行为、公共契约、配置、数据库或依赖变更。
- 第二套 `docs/harness` 工作区、全局 `PROGRESS.md`、feature-list JSON 或自动 Agent 循环。
- 对领域术语、README 正文或文档语言执行静态扫描。
- 关于 Netty 与 Tomcat 的普适性能或流行度结论。
- 为保持格式一致而给不存在的领域概念、聚合或事件造名。
- 为每个聚合 POM、Facade、SDK 或单一插件机械创建重复的 `ARCHITECTURE.md`。
- 用逐行翻译 shell 命令的注释制造噪声，或按注释数量建立静态门禁。

### 验收标准

- 根 README 保留简明的上下文到模块导航，不再拥有详细领域对象表；同时完整保留面向开发者的 Harness 理由、三个问题、五项原则、反馈闭环图、简明门禁分工和 `quick`/`full` 入口。
- `HARNESS_GUIDE.md` 拥有五个子系统地图、传感器、规则矩阵、生命周期、失败归因和清洁状态，不与根 README 重复完整说明。
- 每个拥有业务模型的模块 README 使用经源码核验的名称说明其上下文、统一语言、不变量、协作和状态所有权。
- WS Gateway 文档说明 Socket/WebSocket 流程，以及与当前连接状态所有权匹配的 Netty 选型；明确 Tomcat 同样支持 NIO WebSocket，更适合共享 Servlet/MVC 生态的场景，并记录重新评估条件。
- Git 跟踪的模块 README 中断裂的相对链接会使 drift Harness 失败；有效链接和未跟踪 Markdown 不产生误报；枚举失败或已跟踪文件缺失/不可读不会静默通过，且单文件错误不阻断其余诊断。
- AI 专用 `AGENTS.md` 使用英文；开发者或团队共享的 `README.md`、`ARCHITECTURE.md` 和 `docs/**` 使用中文解释性正文，代码标识符、命令、协议/框架名、精确状态值、skill 名和既定技术术语在更清晰时保留英文。该语义规则保持 Review-only。
- Harness fixture、quick verification、full verification 和 `git diff --check` 通过。
- 根 Architecture 使用中文说明全仓拓扑和所有权；Gateway、Broker、Message 局部 Architecture 分别说明其内部稳定边界，其他简单模块不重复建档。
- Gateway、Broker README 包含可从代码验证的主流程、关键组件、失败路径和技术难点，并与局部 Architecture 分工明确。
- Message README 能说明四张 MySQL 表、Redis Chat View、ReceiptTask/attempts/延迟队列、缓存边界、唯一索引和跨存储一致性限制。
- Message README 能解释当前 fanout-on-write 的写放大/读局部性，与 fanout-on-read 的共享消息、可见性/read cursor 和读放大权衡，并明确替代方案尚未实现及复评指标。
- 13 个顶层 shell 脚本均有中文文件契约；非显然函数说明参数、输出、算法或失败语义，简单语句不堆叠旁白。
- Harness Guide 明确记录外部规范中已采用、暂不采用的机制及重新评估条件。
- 复杂动态流程使用 Mermaid，静态所有权使用表格；不为格式统一重画无复杂分支的既有短图。

### 风险与依赖

- 领域名称可能偏离代码；每个列出的类型和事件都必须对照当前源码路径核验。
- 扩大 Markdown 扫描范围可能暴露既有断链；诊断必须同时指出源文件与目标。
- full verification 可能依赖本地 Maven/npm 工件；环境失败必须与变更失败分开归因并记录。
- 调研快照为 `/Users/kc/Code/open-source/learn-harness-engineering` 的 `77e7a3e`；它是设计输入，不是运行依赖。
- 文档语言质量依赖语境，英文 token 数量不能作为中文解释性正文是否合格的代理指标。

## 任务状态

同一时刻至多一项任务为 `active`。允许的状态是 `not_started`、`active`、`blocked` 和 `passing`；`passing` 必须记录在对应任务中可复查的证据。

| 任务 | 状态 | 证据 |
|---|---|---|
| 1. Git 跟踪的 Markdown 链接传感器 | `passing` | RED：`bash scripts/test-harness.sh` 退出码 1，输出 `Tracked module README with a broken link should fail.`；GREEN（已覆盖断链、缺失/不可读路径、Git 枚举失败、有效链接和未跟踪文件）：`bash scripts/test-harness.sh` 退出码 0，末行输出 `Harness script tests passed.`；`bash -n scripts/test-harness.sh scripts/check-drift.sh` 退出码 0、无输出；`bash scripts/check-drift.sh` 退出码 0，输出 `Drift checks passed.`；`rtk git diff --check -- scripts/test-harness.sh scripts/check-drift.sh` 退出码 0、无输出 |
| 2. 由所属模块维护的领域 README | `passing` | 任务 2 步骤 6 的显式类型核验循环退出码 0，输出 `Validated 112 canonical Java type/event paths.`；`rtk rg -n -e changeNotification -e ensureOwner im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/domain/group/model/Group.java` 退出码 0、5 个匹配，显示 `changeNotification` 调用 `ensureOwner`；`rtk rg -n -e 'interface ImPrivateChatRepository' -e 'interface ImGroupChatRepository' -e 'interface ImPrivateInboxMessageRepository' -e 'interface ImGroupInboxMessageRepository' im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/domain` 退出码 0、4 个匹配；`rtk ./scripts/check-drift.sh` 退出码 0，输出 `Drift checks passed.`；`rtk git diff --check -- im-service/im-account/README.md im-service/im-social/README.md im-service/im-message/README.md im-management/im-iam/README.md im-management/im-audit/README.md` 退出码 0、无输出 |
| 3. 根 README 与 WS 传输选型 | `passing` | `rtk ./scripts/check-drift.sh` 退出码 0，输出 `Drift checks passed.`；`rtk git diff --check -- README.md im-gateway/im-ws-gateway/README.md im-gateway/im-ws-gateway/im-ws-gateway-server/README.md` 退出码 0、无输出 |
| 4. Harness 生命周期与 Agent 路由 | `passing` | `rtk ./scripts/check-drift.sh` 退出码 0，输出 `Drift checks passed.`；`rtk git diff --check -- README.md AGENTS.md docs/PLANS.md docs/references/HARNESS_GUIDE.md docs/design-docs/index.md docs/exec-plans/active/README.md` 退出码 0、无输出；`rtk rg -n '[[:blank:]]+$' docs/design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md docs/exec-plans/active/2026-09-09-domain-readme-and-harness-refinement.md` 退出码 1、无匹配（两个未跟踪文档不在普通 `git diff --check` 范围内） |
| 5. Architecture 文档分层 | `passing` | 新增文档临时 index drift 退出码 0，输出 `Drift checks passed.`；required-doc fixture 先因 `Missing required local Architecture should fail.` RED，登记后 `bash scripts/test-harness.sh` 退出码 0；Architecture 路由和限定 diff 检查通过 |
| 6. Gateway 与 Broker README 深化 | `passing` | `rg` 对照 Gateway/Broker Handler、Lifecycle、Registry、迁移/Gossip/关闭操作均退出码 0；新增动态流程均使用 Mermaid；Markdown 链接与 `git diff --check` 通过 |
| 7. Message 存储文档 | `passing` | DDL 七组唯一索引与四张表核验退出码 0；Redis Key、2 小时 TTL、3 次重投、60 秒 grace、16 分片、10 条 batch、100ms poll 核验退出码 0；临时 index drift 与 diff 通过 |
| 8. 脚本中文自描述 | `passing` | 13 个 `scripts/*.sh` 各匹配 5 项文件契约；所有 Shell/Ruby 函数前有中文契约；`bash -n scripts/*.sh` 与 `bash scripts/test-harness.sh` 退出码 0 |
| 9. 外部 Harness 规范吸收审计 | `passing` | 外部 audit 退出码 1、原始结果 `18/70` 和 Critical `4/7` 已按模板假设归因；`./scripts/harness-gc.sh` 与 `./scripts/verify.sh report` 退出码 0；报告为 9/9 Sensors、1002 tests、0 failures |
| 10. 集成验证与收尾 | `passing` | pre-closeout 临时 index drift 通过；`quick` 退出码 0、141 秒；`behavior` 退出码 0、14 秒、执行 34 个场景；`full` 退出码 0、230 秒；completed 路径临时 index drift、设计链接及 active/completed 索引预检退出码 0 |

证据只对它实际验证过的工作树或 revision、前提假设、共享检查器和验收路径有效。后续修改任一输入都会使受影响证据失效；收尾前必须针对最终变更集刷新全部受影响的集成证据，不能仅沿用早先的 `passing` 结论。

## 跨会话恢复状态

- 当前任务：任务 10 已完成唯一一次计划定稿，状态为 `passing`。
- 已完成证据：任务 1 至任务 10 的准确命令与实际结果已记录在任务表和对应步骤中；最终不可变状态检查紧随本次编辑执行，其结果只在交付说明报告。
- 当前阻塞项：`none`。
- 下一步：执行最终不可变状态命令块；通过后不再编辑文件，直接在交付说明中报告结果。
- 收尾条件：全部任务已为 `passing`，pre-closeout 代码/构建门禁和 post-move 路径预检已通过；最终不可变状态检查必须再次通过。

## 受影响文件与所有权

- `README.md`：简明开发者入口、跨上下文导航和面向开发者的 Harness 设计说明。
- `im-service/im-account/README.md`：User 与在线 Session 的领域语言。
- `im-service/im-social/README.md`：Friend 与 Group 的领域语言。
- `im-service/im-message/README.md`：Chat 与 Message 的领域语言。
- `im-management/im-iam/README.md`：管理身份、应用授权和 OAuth 授权的领域语言。
- `im-management/im-audit/README.md`：集中审计的领域语言和不可变事实所有权。
- `im-gateway/im-ws-gateway/README.md`：WebSocket 聚合说明和传输选型。
- `im-gateway/im-ws-gateway/im-ws-gateway-server/README.md`：具体 Socket 生命周期、Netty pipeline 和容器选型依据。
- `AGENTS.md`：AI 文档路由、启动和清洁状态完成规则。
- `docs/PLANS.md`：Sprint Contract、WIP 和基于证据的任务状态。
- `docs/references/HARNESS_GUIDE.md`：五个子系统地图、传感器、规则矩阵、生命周期、失败归因和清洁状态。
- `scripts/check-drift.sh`：全部 Git 跟踪 Markdown 的链接扫描。
- `scripts/test-harness.sh`：确定性的已跟踪/未跟踪 Markdown fixture。
- `docs/design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md`：本任务的设计决定。
- `docs/exec-plans/active/2026-09-09-domain-readme-and-harness-refinement.md`：任务范围、状态、恢复信息和证据。
- `docs/design-docs/index.md`、`docs/exec-plans/active/README.md`：本任务的导航入口。
- `ARCHITECTURE.md`：中文全仓拓扑、模块边界与数据所有权事实源。
- `im-gateway/ARCHITECTURE.md`：HTTP/WS 入口、认证、连接与 Broker 协作边界。
- `im-broker/ARCHITECTURE.md`：注册表、归属迁移、Gossip、帧路由和一致性边界。
- `im-service/im-message/ARCHITECTURE.md`：Chat/Message、存储、事务后通知与 ACK 边界。
- Gateway、Broker 的聚合与 Server README：实现流程、技术难点、故障和排查说明。
- Message 聚合与 Server README：MySQL、Redis、缓存、幂等与跨存储一致性说明。
- `scripts/*.sh`：文件契约和非显然函数的中文注释。

### 任务 1：扩展 Git 跟踪的 Markdown 链接传感器

**文件：**

- 修改：`scripts/test-harness.sh`
- 修改：`scripts/check-drift.sh`

- [x] **步骤 1：为已跟踪的模块 README 增加失败 fixture**

在 `scripts/test-harness.sh` 中创建隔离的临时 Git 仓库，复制 `check-drift.sh`，提供无操作的 Java/SQL checker stub 和必需的 Harness 文档，再加入一个相对链接目标不存在且已跟踪的 `module/README.md`。断言 checker 失败，且输出同时包含 `module/README.md` 与缺失目标。

- [x] **步骤 2：证明 fixture 处于 RED**

运行：`bash scripts/test-harness.sh`

预期：FAIL，因为修改前的 checker 只扫描根入口文件和 `docs/**/*.md`。

- [x] **步骤 3：精确扫描 Git 跟踪的 Markdown**

使用 `git ls-files -z -- '*.md'` 的 NUL 分隔结果替换 `scripts/check-drift.sh` 中的固定 Ruby 文件列表。继续跳过 `HTTP(S)`、`mailto:` 和纯锚点目标，并保留源文件与目标诊断。`git ls-files` 启动失败或非零退出必须形成违规。

- [x] **步骤 4：增加有效链接、未跟踪文件和缺失文件的非回归场景**

在同一 fixture 中证明：有效的已跟踪相对链接通过；带断链的未跟踪 Markdown 被忽略；已跟踪但工作树中缺失或不可读的 Markdown 会失败并指出文件，同时 checker 继续报告其余断链；Git 仓库不可用导致 Markdown 枚举失败时 checker 也必须失败并给出枚举诊断。fixture 不得访问父仓库或外部服务。

- [x] **步骤 5：运行聚焦 Harness 测试**

运行：`bash scripts/test-harness.sh`

预期：PASS，并输出 `Harness script tests passed.`。

### 任务 2：将领域知识迁移到所属模块 README

**文件：**

- 修改：`im-service/im-account/README.md`
- 修改：`im-service/im-social/README.md`
- 修改：`im-service/im-message/README.md`
- 修改：`im-management/im-iam/README.md`
- 修改：`im-management/im-audit/README.md`

- [x] **步骤 1：说明 Account 上下文与统一语言**

增加 User 和在线 Session 上下文的目的、子域分类、聚合/值对象/领域服务、生命周期不变量、Account 所有的 MySQL/Redis 事实及 Facade 协作。每个类型都对照 `im-account-server/src/main/java/**/domain` 核验。

- [x] **步骤 2：说明 Social 上下文与统一语言**

增加 Friend 和 Group 上下文的目的、聚合/实体/值对象/事件、独立生命周期与 Repository 边界、Account 派生投影和 Message 协作。明确两个上下文共享部署，但不共享聚合。

- [x] **步骤 3：说明 Message 上下文与统一语言**

增加 Chat 和 Message 上下文的目的、聚合/值对象/事件、收件箱副本与当前视图所有权、持久状态与重试状态、Social/Broker 边界，并链接详细的 Server README。

- [x] **步骤 4：完善 IAM 领域导航**

保留现有 IAM 详细说明，同时增加管理身份、应用授权和 OAuth 授权的简明上下文地图与规范语言表。避免重复文件后部已有的协议流程。

- [x] **步骤 5：说明 Audit 领域语言**

使用当前源码名称，为聚合 README 补充审计事实身份、不可变追加/幂等规则、可信来源所有权、查询/导出边界和 SDK/Server 职责。

- [x] **步骤 6：核验领域名称与链接**

针对各领域源码树运行定向 `rg --files`/`rg -n` 检查和 `./scripts/check-drift.sh`。

以下清单覆盖五个 README 新增内容中明确点名的 canonical Java 类型和事件；每项必须在指定 domain source inventory 中恰好对应一个 `.java` 文件：

```bash
rtk bash <<'EOF'
set -euo pipefail
validated=0
while IFS="|" read -r root types; do
  for type in $types; do
    matches=$(rg --files "$root" -g "${type}.java")
    count=0
    while IFS= read -r path; do
      [[ -n "$path" ]] && count=$((count + 1))
    done <<< "$matches"
    if [[ "$count" -ne 1 ]]; then
      printf "Expected exactly one %s.java under %s, found %s\n" "$type" "$root" "$count" >&2
      exit 1
    fi
    validated=$((validated + 1))
  done
done <<'TYPES'
im-common/src/main/java/com/co/kc/imchat/common/domain/user/model|UserId
im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/user/model|User ManagedUser UserEmail UserRawPassword UserPassword UserStatus
im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/user/service|UserService ManagedUserService PasswordService
im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/session/model|Session SessionVersion AccessToken RefreshToken RefreshFingerprint
im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/session/service|SessionService SessionTokenCodec
im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/domain/friend/model|Friend FriendId FriendEdge FriendAlias FriendStatus FriendDisplayName FriendProfile
im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/domain/friend/service|FriendService
im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/domain/friend/event|FriendAddedEvent FriendRemovedEvent
im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/domain/friend/repository|FriendRepository
im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/domain/group/model|Group GroupMember GroupName MemberId MemberCount GroupNotification
im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/domain/group/service|GroupService
im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/domain/group/event|GroupCreatedEvent GroupDismissedEvent GroupMemberJoinedEvent GroupMemberRemovedEvent
im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/domain/group/repository|GroupMemberRepository
im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/domain/account/model|UserProfile
im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/domain/message/model|UserGroupChatSummary
im-common/src/main/java/com/co/kc/imchat/common/domain/chat/model|ImChatId
im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/domain/chat/model|ImChat ImPrivateChat ImGroupChat ImChatName ImChatType ImChatStatus ImChatView
im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/domain/chat/service|ImChatService
im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/domain/chat/repository|ImPrivateChatRepository ImGroupChatRepository
im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/domain/message/model|ImMessage ImPrivateInboxMessage ImGroupInboxMessage ImMessageId ImMessageToken ImMessageContent
im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/domain/message/service|ImMessageService
im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/domain/message/event|ImPrivateMessageSentEvent ImPrivateMessageRevokedEvent ImGroupMessageSentEvent ImGroupMessageRevokedEvent
im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/domain/message/repository|ImPrivateInboxMessageRepository ImGroupInboxMessageRepository
im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/domain/administrator/model|Administrator AdministratorId AdministratorUsername AdministratorEmail AdministratorRawPassword AdministratorPassword AdministratorStatus
im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/domain/administrator/service|AdministratorService PasswordService
im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/domain/application/model|Application OAuthClient AppId AppKey OAuthClientId
im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/domain/authorization/model|IamRole ApplicationPermission ApplicationRole
im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/domain/session/model|OAuthAuthorization OAuthSession OAuthAuthorizationId OAuthPrincipal OAuthAccessToken OAuthRefreshToken OAuthAuthorizationCode
im-management/im-iam/im-iam-server/src/main/java/com/co/kc/imchat/management/iam/domain/session/service|OAuthAuthorizationService
im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/domain/model|AuditEvent AuditId SourceApp AuditType AuditAction AuditActor AuditTarget AuditOutcome AuditErrorCode AuditDescription AuditClientContext TraceId AuditAttributes AuditQueryCondition
im-management/im-audit/im-audit-server/src/main/java/com/co/kc/imchat/management/audit/domain/repository|AuditEventRepository
im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/model|AuditEvent AuditSubmission
im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/client|AuditClient
im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/support|AuditTemplate
im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/context|AuditContextCollector
im-management/im-audit/im-audit-sdk/src/main/java/com/co/kc/imchat/management/audit/sdk/annotation|Audited
TYPES
printf "Validated %d canonical Java type/event paths.\n" "$validated"
EOF
```

实际结果：退出码 0，输出 `Validated 112 canonical Java type/event paths.`。

补充运行 `rtk rg -n -e changeNotification -e ensureOwner im-service/im-social/im-social-server/src/main/java/com/co/kc/imchat/service/social/domain/group/model/Group.java`，退出码 0、5 个匹配，其中 `changeNotification` 方法体调用 `ensureOwner`；运行 `rtk rg -n -e 'interface ImPrivateChatRepository' -e 'interface ImGroupChatRepository' -e 'interface ImPrivateInboxMessageRepository' -e 'interface ImGroupInboxMessageRepository' im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/domain`，退出码 0、4 个匹配，证明正文列出的四个具体 Repository 接口均存在。

预期：所有文档名称均存在，或被明确标记为业务术语；所有本地链接均可解析。

### 任务 3：精简根入口并说明 WS 传输选型

**文件：**

- 修改：`README.md`
- 修改：`im-gateway/im-ws-gateway/README.md`
- 修改：`im-gateway/im-ws-gateway/im-ws-gateway-server/README.md`

- [x] **步骤 1：用导航地图替换根 README 的详细 DDD 内容**

保留将 User、Session、Friend、Group、Chat、Message 和管理上下文映射到所属模块 README 的简明表格。移除通用 DDD 对象教程和逐类型全局词汇表，将通用分层规则链接到 `CODING_GUIDE.md`。

- [x] **步骤 2：保留并恢复根 README 面向开发者的 Harness 说明**

根 README 保留 Harness 理由、三个核心问题、五项原则、反馈闭环图、简明的质量门禁分工，以及 `quick`/`full` 验证入口；通过链接把五个子系统、传感器、规则矩阵、生命周期、失败归因和清洁状态等运行细节交给 `HARNESS_GUIDE.md`。不得删除开发者理解 Harness 所需的叙述，也不得在两处复制完整运行规则。

- [x] **步骤 3：说明 Socket 与 WebSocket 生命周期**

在 WS 聚合 README 中，以开发者视角说明 TCP 连接、HTTP Upgrade、全双工帧交换、心跳、本机连接所有权和 Broker 路由。

- [x] **步骤 4：说明本仓库为何使用 Netty 而非 Tomcat**

在 Server README 中引用 `netty-all` 加非 Web `spring-boot-starter` 的依赖形态、`NettyWebSocketServer` pipeline 和 `WsConfigTest#defaultProfileRunsAsNonWebNettyGateway`。说明 Netty 与 Tomcat 均支持 NIO WebSocket；当前专用连接 Gateway 需要直接控制 Channel、EventLoop、pipeline、writability、资源和协议，因此选择复杂度更高但控制面更直接的 Netty。说明 Tomcat 更适合共享 Servlet/MVC 生态、安全链和会话模型的场景，并记录重新评估条件。不得宣称普适吞吐优势。

- [x] **步骤 5：核验根文档与 WS 文档**

运行：`./scripts/check-drift.sh`

预期：PASS，且根 README 不再包含详细领域类型表，同时保留完整的开发者 Harness 说明。

### 任务 4：强化 Harness 生命周期与 Agent 路由

**文件：**

- 修改：`docs/references/HARNESS_GUIDE.md`
- 修改：`docs/PLANS.md`
- 修改：`AGENTS.md`
- 修改：`docs/design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md`
- 修改：`docs/exec-plans/active/2026-09-09-domain-readme-and-harness-refinement.md`
- 修改：`docs/design-docs/index.md`
- 修改：`docs/exec-plans/active/README.md`

- [x] **步骤 1：增加 Harness 五个子系统地图**

说明 im-chat 的具体 Instruction、Tools、Environment、State 与 Feedback 来源。保留现有传感器表，不引入第二套工作区。

- [x] **步骤 2：分离规则登记与规则所有权**

明确矩阵只记录规范所有者、传感器、Review 状态与自动化条件，Architecture、Coding、SQL、Unit Test、Security 和 Reliability 文档拥有完整规则说明。只移除与所属规范重复的解释，保留仓库特定的自动化条件。

- [x] **步骤 3：扩展现有计划契约**

将现有必填部分组织到 Sprint Contract 下，不创建重复章节。加入 WIP=1、`not_started|active|blocked|passing`、`passing` 证据要求和计划内的跨会话恢复状态。

- [x] **步骤 4：增加失败归因与清洁状态完成规则**

将失败归因到任务规格、上下文、环境、工具权限、状态、验证反馈、范围或架构边界。更新 `AGENTS.md`，使完成条件包括实际验证证据、已更新的计划/文档状态、已分类的残余风险，以及不存在未分类的未跟踪临时工件。

- [x] **步骤 5：登记文档受众与语言约定**

保持 AI 专用 `AGENTS.md` 为英文；开发者或团队共享的 `README.md`、`ARCHITECTURE.md` 和 `docs/**` 使用中文解释性正文，同时按语境保留代码标识符、命令、协议/框架名、状态值、skill 名和既定技术术语。将该规则登记为 Review-only，不增加英文 token 扫描。

- [x] **步骤 6：更新设计与活跃计划导航**

在 `docs/design-docs/index.md` 和 `docs/exec-plans/active/README.md` 中使用中文标签登记本任务，并确保计划状态、证据有效性和跨会话恢复要求准确。

- [x] **步骤 7：运行文档与定向 Harness 检查**

运行 drift、限定范围的 diff 和定向内容检查，并记录可用的实现与规格评审结论。工件质量评审不在本任务证据中标记 approved，留待任务 10 基于最终变更集复核。

预期：检查通过，任务 4 记录的准确命令和实际结果与对应工作树一致；未跟踪设计稿和计划的链接完整性留待任务 10 使用临时索引纳入 drift 检查。

### 任务 5：建立 Architecture 文档分层

**文件：**

- 修改：`ARCHITECTURE.md`
- 新增：`im-gateway/ARCHITECTURE.md`
- 新增：`im-broker/ARCHITECTURE.md`
- 新增：`im-service/im-message/ARCHITECTURE.md`
- 修改：根与对应模块 `AGENTS.md`、README 中的文档路由

- [x] **步骤 1：重构根 Architecture**

将解释性正文改为中文，聚焦全仓运行拓扑、限界上下文关系、模块依赖方向、数据所有权和文档分层，不展开单模块类级实现。

- [x] **步骤 2：增加三个局部 Architecture**

Gateway 说明 HTTP/WS 入口、认证、连接所有权和 Broker 协作；Broker 说明注册表、归属迁移、Gossip、帧路由和一致性；Message 说明上下文、存储、事务后通知和 ACK 边界。

- [x] **步骤 3：建立创建条件和文档路由**

只有复杂内部拓扑、多个运行职责、独立一致性模型或重要跨边界流程才创建局部 Architecture；Facade、SDK、聚合 POM 和单一插件继续使用 README。

- [x] **步骤 4：核验架构事实**

对照 POM、启动类、Repository/Registry、配置与测试核验依赖和状态所有权，运行 Markdown 链接与限定范围 diff 检查。

### 任务 6：深化 Gateway 与 Broker README

**文件：**

- 修改：`im-gateway/README.md`、HTTP/WS Gateway README
- 修改：`im-broker/README.md`、`im-broker/im-broker-server/README.md`

- [x] **步骤 1：梳理 Gateway 流程**

使用流程图说明 HTTP 认证与可信 Header 重建、WS 握手注册、上行帧、下行推送、ACK、会话关闭和异常断线清理；聚合 README 讲整体，Server README 讲组件顺序和失败路径。

- [x] **步骤 2：梳理 Broker 流程**

使用流程图说明 Broker/Gateway 注册心跳、连接归属转发、成员变化迁移、Gossip 同步、上下行帧和 Session 关闭控制。

- [x] **步骤 3：记录技术难点和故障边界**

说明 EventLoop 阻塞隔离、连接本机所有权、双向索引、归属算法、迁移确认顺序、墓碑 TTL、部分投递失败和最终一致性；仅写当前实现与测试可证明的结论。

动态调用、迁移、Gossip 和分支使用 Mermaid `sequenceDiagram`/`flowchart`，数据所有权使用 Markdown 表格。

- [x] **步骤 4：核验并去重**

README 通过链接引用局部 Architecture 和 Server 文档，不复制完整类清单；对照源码、配置和测试运行链接与限定范围 diff 检查。

### 任务 7：补充 Message 存储文档

**文件：**

- 修改：`im-service/im-message/README.md`
- 修改：`im-service/im-message/im-message-server/README.md`
- 对照：`im-service/im-message/im-message-server/sql/ddl.sql`

- [x] **步骤 1：增加存储地图**

说明 MySQL 四张主表、Redis Chat View、ReceiptTask/attempts/分片延迟队列，以及缓存和 Repository 所在层次。

- [x] **步骤 2：说明消息副本与索引**

解释私聊双副本、群聊成员副本、用户视角会话状态，以及 `chatId`、`messageId`、Token 和逻辑删除相关唯一索引承担的幂等语义。

- [x] **步骤 3：说明读写与一致性**

记录事务内聊天/收件箱写入、提交后通知、Redis 当前视图、回执登记/投递/ACK 和崩溃窗口；明确 MySQL 是事实源、缓存不是事实源、Redis 回执不是 Outbox。

同时说明当前 fanout-on-write 与 fanout-on-read 的读写放大、成员可见性、read cursor、热点和一致性权衡，以及基于真实容量指标的复评条件。

- [x] **步骤 4：核验存储事实**

对照 DDL、Entity、Repository、MyBatis Service、Redis key/TTL、通知 Store 和测试核验表名、索引、读写顺序及失效语义。

### 任务 8：为脚本增加中文自描述

**文件：**

- 修改：`scripts/*.sh`
- 修改：`docs/references/HARNESS_GUIDE.md`

- [x] **步骤 1：统一文件头契约**

13 个顶层脚本在 shebang 后说明用途、输入、输出或副作用、依赖和退出码。

- [x] **步骤 2：注释非显然函数与 fixture**

在函数定义前说明参数、输出、关键算法、隔离策略或失败传播；测试脚本说明临时目录、正反例和清理责任。简单打印、直接委托和显然赋值不逐行注释。

- [x] **步骤 3：登记 Review-only 规则**

Harness Guide 记录脚本自描述约定及不做注释数量检查的原因。

- [x] **步骤 4：验证脚本**

运行 `bash -n scripts/*.sh`、`bash scripts/test-harness.sh`、文件头/函数注释审计和 `git diff --check`。

### 任务 9：复核外部 Harness 规范吸收情况

**文件：**

- 修改：`docs/references/HARNESS_GUIDE.md`
- 修改：本设计和计划的审计结论

- [x] **步骤 1：逐项映射外部规范**

对照仓库事实源、渐进披露、五子系统、初始化、状态恢复、WIP、完成判定、端到端验证、可观测性和清洁状态，记录 im-chat 的实际落点。

- [x] **步骤 2：记录未采用项与条件**

说明不引入全局 `PROGRESS.md`、通用 `feature_list.json`、自动 Agent loop、多 Agent 固定流程和第二套模板目录的原因及重新评估条件。

- [x] **步骤 3：检查缺口并运行治理入口**

确认新增 Architecture、复杂模块 README、Message 存储和脚本自描述补上发现成本缺口；运行外部 `tools/audit-harness.sh` 记录原始结果并识别其固定模板假设，再运行 `./scripts/harness-gc.sh`、`./scripts/verify.sh report` 和文档链接检查。外部评分只作差距信号，不作为 im-chat 门禁。

### 任务 10：集成验证与收尾

**文件：**

- 修改：本计划的状态/证据表与恢复状态
- 移动：`docs/exec-plans/active/2026-09-09-domain-readme-and-harness-refinement.md` 到 `docs/exec-plans/completed/2026-09-09-domain-readme-and-harness-refinement.md`
- 修改：`docs/design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md`，随计划移动将执行计划链接从 `../exec-plans/active/2026-09-09-domain-readme-and-harness-refinement.md` 切换为 `../exec-plans/completed/2026-09-09-domain-readme-and-harness-refinement.md`
- 修改：`docs/exec-plans/active/README.md`
- 修改：`docs/exec-plans/completed/README.md`

- [x] **步骤 1：在收尾前运行完整集成门禁**

先验证当前 active 路径下的最终实现和文档。普通 `git diff --check` 不覆盖五个尚未跟踪的新文档，因此同时执行显式空白检查和只修改 index 副本的临时索引检查：

```bash
(
  set -euo pipefail
  rtk git diff --check
  if rtk rg -n '[[:blank:]]+$' \
    docs/design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md \
    docs/exec-plans/active/2026-09-09-domain-readme-and-harness-refinement.md \
    im-gateway/ARCHITECTURE.md \
    im-broker/ARCHITECTURE.md \
    im-service/im-message/ARCHITECTURE.md; then
    printf 'Trailing whitespace found in new task documents.\n' >&2
    exit 1
  fi
  HARNESS_PRE_CLOSEOUT_INDEX=$(mktemp)
  trap 'rm -f "$HARNESS_PRE_CLOSEOUT_INDEX"' EXIT
  cp "$(git rev-parse --git-path index)" "$HARNESS_PRE_CLOSEOUT_INDEX"
  GIT_INDEX_FILE="$HARNESS_PRE_CLOSEOUT_INDEX" git add -N -- \
    docs/design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md \
    docs/exec-plans/active/2026-09-09-domain-readme-and-harness-refinement.md \
    im-gateway/ARCHITECTURE.md \
    im-broker/ARCHITECTURE.md \
    im-service/im-message/ARCHITECTURE.md
  GIT_INDEX_FILE="$HARNESS_PRE_CLOSEOUT_INDEX" ./scripts/check-drift.sh
  rm -f "$HARNESS_PRE_CLOSEOUT_INDEX"
  trap - EXIT
  rtk ./scripts/verify.sh quick
  rtk ./scripts/verify.sh behavior
  rtk ./scripts/verify.sh full
)
```

预期：`git diff --check` 退出码为 0 且无输出；空白 `rg` 退出码为 1 且无匹配；临时索引 drift、`quick`、`behavior` 和 `full` 均退出码为 0。任何环境失败都必须记录准确命令并独立归因，不得削弱门禁。

- [x] **步骤 2：在收尾前评审完整变更面与清洁状态**

运行 `rtk git status --short` 和 `rtk git diff --stat`，再评审全部修改或未跟踪的文档与脚本，确认没有无关改动、重复事实、陈旧类型名、误导性的自动执行声明、未分类的任务临时工件或无退出条件 TODO。记录最终规格评审和工件质量评审结论；未通过时保持任务 10 为 `active`。

- [x] **步骤 3：原子完成计划、设计链接和索引收尾**

在同一个收尾变更组中完成以下动作，中间状态不得提交或标记为 `passing`：

1. 将计划从 active 路径移动到 completed 路径，正文和既有证据保持不变。
2. 将设计文档中的执行计划链接从 active 路径切换为 completed 路径。
3. 从 `docs/exec-plans/active/README.md` 删除本计划条目，并在 `docs/exec-plans/completed/README.md` 增加对应的中文条目。
4. 保持任务 10 为 `active`，直到步骤 4 的移动后预检通过并完成唯一一次计划定稿。

- [x] **步骤 4：执行移动后预检并一次性定稿 completed 计划**

先对新的 completed 计划路径、设计链接和两个索引执行预检。该命令只复制并修改 Git index 副本；trap 与显式清理都引用本步骤自己的 `HARNESS_POST_MOVE_PRECHECK_INDEX`，绝不修改真实 index：

```bash
(
  set -euo pipefail
  [[ ! -e docs/exec-plans/active/2026-09-09-domain-readme-and-harness-refinement.md ]]
  HARNESS_POST_MOVE_PRECHECK_INDEX=$(mktemp)
  trap 'rm -f "$HARNESS_POST_MOVE_PRECHECK_INDEX"' EXIT
  cp "$(git rev-parse --git-path index)" "$HARNESS_POST_MOVE_PRECHECK_INDEX"
  GIT_INDEX_FILE="$HARNESS_POST_MOVE_PRECHECK_INDEX" git add -N -- \
    docs/design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md \
    docs/exec-plans/completed/2026-09-09-domain-readme-and-harness-refinement.md \
    im-gateway/ARCHITECTURE.md \
    im-broker/ARCHITECTURE.md \
    im-service/im-message/ARCHITECTURE.md
  GIT_INDEX_FILE="$HARNESS_POST_MOVE_PRECHECK_INDEX" ./scripts/check-drift.sh
  rm -f "$HARNESS_POST_MOVE_PRECHECK_INDEX"
  trap - EXIT
  DESIGN_LINK_COUNT=$(rtk rg -cF \
    '../exec-plans/completed/2026-09-09-domain-readme-and-harness-refinement.md' \
    docs/design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md)
  [[ "$DESIGN_LINK_COUNT" == "1" ]]
  COMPLETED_ENTRY_COUNT=$(rtk rg -cF \
    '(2026-09-09-domain-readme-and-harness-refinement.md)' \
    docs/exec-plans/completed/README.md)
  [[ "$COMPLETED_ENTRY_COUNT" == "1" ]]
  if rtk rg -nF '(2026-09-09-domain-readme-and-harness-refinement.md)' \
    docs/exec-plans/active/README.md; then
    printf 'Completed plan is still listed in the active index.\n' >&2
    exit 1
  fi
)
```

预检通过后，对 completed 计划做唯一一次最终编辑：写入任务 10 步骤 1 至步骤 4 已知的准确命令、退出码和实际结果，更新恢复状态，勾选任务 10 的全部步骤（包括作为自收尾标记的步骤 5），并将任务 10 状态设为 `passing`。这次编辑之后不得再修改任何文件；步骤 5 的最终不可变状态证据只在交付说明中报告，不回写计划。

- [x] **步骤 5：验证最终不可变状态并在交付说明中报告结果**

立即针对步骤 4 定稿后的文件状态运行以下完整命令块。最终临时 index 使用独立的 `HARNESS_FINAL_STATE_INDEX` 和对应 trap；所有 `git add -N` 都显式设置 `GIT_INDEX_FILE`，不接触真实 index：

```bash
(
  set -euo pipefail
  HARNESS_FINAL_STATE_INDEX=$(mktemp)
  trap 'rm -f "$HARNESS_FINAL_STATE_INDEX"' EXIT
  cp "$(git rev-parse --git-path index)" "$HARNESS_FINAL_STATE_INDEX"
  GIT_INDEX_FILE="$HARNESS_FINAL_STATE_INDEX" git add -N -- \
    docs/design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md \
    docs/exec-plans/completed/2026-09-09-domain-readme-and-harness-refinement.md \
    im-gateway/ARCHITECTURE.md \
    im-broker/ARCHITECTURE.md \
    im-service/im-message/ARCHITECTURE.md
  GIT_INDEX_FILE="$HARNESS_FINAL_STATE_INDEX" ./scripts/check-drift.sh
  rm -f "$HARNESS_FINAL_STATE_INDEX"
  trap - EXIT
  rtk ./scripts/verify.sh quick
  rtk git diff --check
  if rtk rg -n '[[:blank:]]+$' \
    docs/design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md \
    docs/exec-plans/completed/2026-09-09-domain-readme-and-harness-refinement.md \
    im-gateway/ARCHITECTURE.md \
    im-broker/ARCHITECTURE.md \
    im-service/im-message/ARCHITECTURE.md; then
    printf 'Trailing whitespace found in finalized task documents.\n' >&2
    exit 1
  fi
  DESIGN_LINK_COUNT=$(rtk rg -cF \
    '../exec-plans/completed/2026-09-09-domain-readme-and-harness-refinement.md' \
    docs/design-docs/2026-09-09-domain-readme-and-harness-refinement-design.md)
  [[ "$DESIGN_LINK_COUNT" == "1" ]]
  COMPLETED_ENTRY_COUNT=$(rtk rg -cF \
    '(2026-09-09-domain-readme-and-harness-refinement.md)' \
    docs/exec-plans/completed/README.md)
  [[ "$COMPLETED_ENTRY_COUNT" == "1" ]]
  if rtk rg -nF '(2026-09-09-domain-readme-and-harness-refinement.md)' \
    docs/exec-plans/active/README.md; then
    printf 'Completed plan is still listed in the active index.\n' >&2
    exit 1
  fi
  [[ ! -e docs/exec-plans/active/2026-09-09-domain-readme-and-harness-refinement.md ]]
)
```

预期：整个命令块退出码为 0；completed 路径临时索引 drift 与 `quick` 退出码为 0；完整 `git diff --check` 退出码为 0 且无输出；五个新文档无尾随空白；设计 completed 链接与 completed 索引条目计数均为 1；active 索引无本计划条目；旧 active 计划路径不存在。移动前的 `full` 继续作为生产代码、测试和完整构建证据，因为步骤 3 只移动计划并修改文档链接与索引；最终 `quick`、completed 路径临时索引 drift、完整 diff、空白与精确路径/索引断言负责刷新文档证据。

此命令块开始后任何文件都不得再编辑。它的准确命令、退出码和实际结果只在最终交付说明中报告，不写回 completed 计划，从而避免验证证据因记录自身而递归失效。若最终不可变状态验证失败，不得宣称任务完成；保留失败输出并在后续修复前重新打开计划状态。

## 回滚

本变更没有运行时发布。如果局部领域信息难以发现，应增加或改进根文档到模块文档的链接，而不是把完整内容复制回根文档。如果扩展后的 Markdown 扫描发现有意存在的生成文件或未跟踪文件，应保持传感器只覆盖 Git 跟踪的 Markdown，不增加按路径命名的例外。如果某条 Harness 生命周期规则只增加工作量而没有改善可恢复性或验证质量，应同时修正规则及其所属文档。
