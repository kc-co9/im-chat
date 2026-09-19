# IM 核心链路容量压测设计

状态：设计已批准，实施与真实压测已完成并归档。交互设计由用户逐节确认，书面设计完成三轮独立 Review 并获 `Approved`，用户委托的主审修订也已确认；最终任务与证据记录以 completed execution plan 为准。

## 背景

已完成 ShardingSphere、Prometheus、SkyWalking 和本地 full 编排，需要以可复现的真实流量建立连接和消息容量基线。当前没有压测模块；现有实时 E2E 替换了 Account/Message Facade，不能证明真实数据库与完整投递容量。

用户只有一台机器，发压端与 full 服务共享宿主 CPU、内存和磁盘。所有结论必须注明本机共享资源限制，不能直接推导生产容量。

## 目标与非目标

- 目标：真实登录、WebSocket 握手、私聊/群聊发送、通知接收和 ACK；分别探索稳定连接数和消息吞吐，复测边界并保存可比较报告。
- 目标：自动准备批次隔离数据、限制目标地址、可恢复清理、Grafana 展示及发压端/容器轻量资源采样。
- 非目标：生产发压、分布式发压平台、生产接口改造、容错破坏测试、数小时稳定性认证、额外数据库/容器 exporter。
- 非目标：自动提交代码；本次设计批准不继承上一任务的 Git 提交授权。

## 已知事实与约束

- 事实源：根 `ARCHITECTURE.md`，Gateway/Broker/Message 局部 Architecture，各 Server README，`docs/product-specs/im-realtime-approved-scenarios.md`，已完成的可观测性设计。
- Account、Social、Message 分别拥有 `im_chat_account/im_chat_social/im_chat_message`；本地清理工具是明确的运维例外，不授权生产跨 Schema 访问。
- HTTP Gateway 默认 `18010`，WS Gateway 默认 `18011/ws`；通过真实注册、登录、好友和建群接口准备数据，不调用替换依赖的 E2E fixture。
- 群消息同步 fanout-on-write；十人成员群对其他九人通知，发送者自己的持久化副本不计作通知投递。
- 下行 push body 包含字符串 `receiptId` 和 notification；实际 ACK 回执类型为 `PRIVATE_MESSAGE_SEND/GROUP_MESSAGE_SEND`。ACK 成功必须解析响应的业务 code，不仅检查 WebSocket 已发送；JSON 中的 long 字段不得经 JavaScript Number 转换。
- WebSocket 保持协议级 ping/pong；准备阶段的群系统通知也需 ACK，但不计入测量消息。
- 建群系统消息不向 owner push；准备屏障只等待其他 `groupSize - 1` 名成员的通知与 ACK，owner 自有副本通过数据库核对。
- `im-test` 只拥有验证能力；`im-load-test` 作为 Maven 测试模块进入 reactor，以 test classpath 编译和运行安全清理器。k6/Node 工具不进入生产依赖，默认 Maven/quick/full 也不自动发压。新能力需要 focused `verify.sh` 入口、影响映射和确定性 fixture。
- 当前 k6 官方文档提供 `k6/websockets`、Prometheus Remote Write 与 `handleSummary`；实施时固定验证过的 k6 版本，不使用浮动 latest。

## 备选方案

| 方案 | 优点 | 代价 | 决策 |
|---|---|---|---|
| 宿主 k6 + 仓库 Harness | 避免占用 Docker 虚拟机的有限资源；脚本可维护，复用本地入口 | 需要固定本机 k6 版本 | 采用 |
| Compose k6 profile | 镜像复现性好 | 与服务争抢 Docker 12GB 配额，改变网络路径 | 不采用 |
| 单个 k6 脚本 | 初始工作量少 | 准备、测量、清理和探索耦合，失败恢复困难 | 不采用 |

## 决策

### 组件与入口

`im-test/im-load-test` 是 Maven 测试模块，保存 `pom.xml`、`README.md`、集中配置、`scenarios/connection-capacity.js`、`scenarios/message-throughput.js`、`lib/`、`tools/data.mjs` 和 `src/test/java` 下的安全清理器。Node 使用结构化 JSON 与内置 HTTP 能力，清理 SQL 使用参数绑定或严格验证的 ID 集合，不拼接外部输入。Java 清理器通过 test-scope 依赖复用生产 `RedisImMessageConfirmableStore.confirm` 与真实 JetCache 类型，不生成可供生产依赖的主代码或复制 Redisson codec、延迟队列和 hash 分片算法。

为保证该复用在 task map TTL 已过期后仍可清理孤儿队列元素，Message 所有者完善 Store 的幂等 `confirm`：从 canonical receiptId 类型前缀恢复 `ReceiptType`，按既有 hash 定位 delayed/blocking queue，并独立清理 task 与 attempt map；缺失 task 不再阻止队列清理，非法 receiptId 必须拒绝。对精确分片内的 delayed/blocking queue 按调用开始时的 size 上限删除全部既存匹配项，避免重复 offer 留下孤儿，同时避免并发 producer 导致无界循环。测试覆盖 task 存在、task 已过期、重复队列项、重复 confirm、非零类型路由和非法 ID。

`scripts/load_test.sh` 提供 `prepare/connections/messages/smoke/cleanup/all`；负载采样、等待、信号处理和失败传播有中文契约说明。`verify.sh load-test` 运行确定性协议、计数、配置、安全及编排 fixture，不自动发压。`verify.sh load-test-smoke` 是显式、非默认的真实 20 用户门禁，要求 full 健康并生成 revision-bound evidence；`quick/full` 不调用它。

运行产物位于 `.harness/load-test/<batch-id>/` 并保持 ignored：原子写入的批次清单、权限为 `0600` 的凭据文件、每轮 k6 summary JSON、正确性核对数据、资源样本及容量报告。清理凭据不删除性能报告。

### 准备与数据拓扑

默认准备最高档所需的 3200 用户，账号、邮箱和群名带唯一 `loadtest-<batch-id>` 前缀。准备并发有界，每个会产生持久化副作用的请求前先追加并 fsync write-ahead intent，成功后追加 completed；日志记录幂等操作键。失败恢复结合 journal 与本地数据库前缀发现对账，不假设响应和清单写入原子，也不盲目重复注册。

运行 batch ID 限制为 1 到 6 位小写 base36 字符，默认随机生成 6 位值；用户与 cohort 使用固定 3 位 base36 序号。该编码在 3200 用户和最小 `groupSize=2` 的最坏情况下仍满足 Account 用户名/邮箱及 Social 群名列宽。准备命令严格拒绝未知、重复、缺值和冲突参数，避免拼写错误意外退回默认 3200 用户。

本地数据库对账凭据不进入仓库配置或命令行。`IM_LOAD_TEST_DATABASE_TARGETS_FILE` 只传递权限为 `0600` 的临时 JSON 文件路径，文件精确包含 `account`、`social`、`message` 三个本地 MySQL URL，并分别绑定 `im_chat_account`、`im_chat_social`、`im_chat_message`。准备器必须在第一个业务 mutation 前连接三个 owner，核对实际 Schema 以及 host/port 与 `targets.cleanup.mysql` 一致；编排器负责在批次目录创建并在恢复结束后删除该凭据文件。

用户在每个完整 `groupSize` cohort 内建立环形双向好友关系，每组创建一个同规模群；默认 `groupSize=10`。选取连接子集时必须保持 cohort 完整且所有私聊目标在线。每个 VU 管理一个完整 cohort；消息连接数、最小可执行连接数、预期 fanout 和数据选择均从 `groupSize` 派生，不硬编码十人。

通过业务接口查询用户 ID、双方私聊 chatId、groupId 和各成员自己的群聊 chatId。注册和登录后，准备器按 cohort 建立临时 WebSocket，再调用真实建群接口；其他 `groupSize - 1` 名成员各接收并 ACK 一次系统通知，owner 自有消息副本通过数据库核对，确认该 cohort Receipt 已静默后才关闭临时连接。群关系传播与系统通知使用有界同步检查，不用固定 sleep 假定准备成功。每轮重新登录用户并独立记录认证延迟，正式测量前验证会话与连接可用。

好友和建群恢复分成业务 owner 副作用与 Message 派生事实两个阶段。Social 已精确确认双向好友关系或群及成员关系后立即持久化 mutation completed；私聊 Chat、群 Chat、系统消息和 Receipt 随后独立有界收敛，不能因 Message 尚未完成而重发 `addFriend` 或 `createGroup`。群 Receipt ACK 使用独立持久化 journal；恢复时可由 Message 事实补全 receiptId、修复最后一条崩溃截断记录并重发未完成 ACK。

### 分轮执行

- 20 用户是默认冒烟档；连接探索从 `50/100/200/400/800/1600/3200` 开始，20 用户成功不自动冒充稳定容量档。所有请求档位向下取整为 `groupSize` 的倍数，报告同时保存 requested/effective 数量并去除重复 effective 档；不足一个 cohort 时拒绝执行。
- 默认每秒新建 20 个连接，连接阶段保留心跳、记录握手成功率、稳定在线数和非预期断开，不发业务消息。
- 每个连接容量档在建连完成后先稳定 1 分钟，再测量 5 分钟，最后用 1 分钟受控断开并等待 Gateway/Broker 路由清理；登录、建连爬升和受控断开不计入正式窗口。
- 最大稳定连接数的一半向下取整到 `groupSize` 的倍数，用于消息阶段；不足一个完整 cohort 则消息阶段跳过并说明原因。
- 消息速率按 `50/100/200/400/800/1600/3200 msg/s` 分轮倍增；默认最高 3200，全部通过时只报告容量下界。配置中必须有最高消息速率、最大轮数、单轮/总执行超时，禁止无界探索。
- 70% 私聊与 30% 群聊按发送数分配；256B 指 UTF-8 消息正文字节数，唯一标识/时间戳的嵌入不得悄悄扩大正文。
- 每轮登录和建连不计入正式窗口；建连完成后预热 1 分钟，测量 5 分钟，停止发送后保持连接排空 1 分钟，再断开并冷却。
- 一个 VU 运行一个长 iteration 并持有一个 cohort 的全部 socket。全局目标速率按 cohort 确定性分摊，使用单调时钟预排发送槽；迟到槽直接跳过且不突发补发，记录调度延迟和跳过槽。每轮全体接收者在线；目标发送计划不因响应慢而自动减少请求。报告计划、尝试、业务成功、接收和 ACK 完成的实际速率。
- 当前 k6 `k6/websockets.WebSocket` 不暴露浏览器 `bufferedAmount`，因此不得伪造 socket 写缓冲指标。发送槽迟到超过 200ms 时跳过；实际发送速率低于目标 98% 或跳过率超过 1% 时结合 k6 进程 CPU/RSS、宿主可用内存和发送异常判断。k6 进程 CPU 达到一个逻辑核的 90% 并连续三个 5 秒采样低于目标速率，或宿主可用内存连续三个采样低于 1GiB，均判为发压环境饱和。任一发压证据缺失或饱和条件触发，该轮为不确定而非服务失败；RSS 记录趋势但不凭单一绝对值判定。
- 失败后停止升压，复测最后通过档与第一失败档各一次。一致则给出区间，不一致则标为不确定；触顶全通过只能报告容量下界，不称最大容量。

### 正确性与测量

256B UTF-8 正文包含固定宽度 correlation token（批次、轮次、正式/预热阶段、消息序号）和填充内容。接收者从 `notification.messageContent` 解析 token，以 `(token, receiver identity, receiptId)` 核对发送、通知和 ACK；字符串 `receiptId` 是 ACK 和 long ID 的权威来源，不依赖发送响应 body 或 JSON number。解析实际 Frame 结构和业务错误，身份 ID 始终按十进制字符串保存。不自动重试发送失败。

预期接收集合由每条实际发送尝试和批次拓扑产生：私聊一人、群聊其他成员。分母包含发送失败/超时的尝试；发送者副本、准备系统通知和预热消息不计入正式业务投递。跨用户完成核对必须以接收者身份去重，不能只对计数总和。

k6 VU 不共享可变 JS 内存。实现须通过可证明的 cohort 内多 socket 协调或离线身份关联，验证预期、漏投、重复、未知接收者、ACK 失败；不能宣称跨 VU map 能关联消息。核对状态与输出有内存/磁盘上限，超过上限本轮不确定并停止，不静默丢样本。

分开记录发送响应、发送到接收延迟、发送到 ACK 成功响应延迟。消息中的客户端发送时间用于同机接收延迟，不以数据库时间替代客户端时间。正式窗口末尾的消息在排空窗口继续核对；超时/漏投/ACK 失败计为失败，不能只显示成功样本的分位数。

稳定容量默认要求：连接成功率和测量末仍在线比例均 >=99.5%，非预期断开率 <=0.5%；消息发送业务成功率 >=99.9%；预期投递并 ACK 成功率 >=99.9%；接收 P95 <=500ms、P99 <=2s；未知或错投接收者为 0；重复投递率 <=0.1%，且重复不计成功。断开、错误、延迟、队列趋势分别记录，不让高握手成功率掩盖保持在线失败。

有效轮次还要求实际尝试发送速率达到目标的 98% 至 102%，调度槽跳过率 <=1%，调度延迟 P95 <=100ms，正式窗口至少 1000 次发送。正式窗口按一分钟分桶，最后三个桶的未完成数不得连续增长；一分钟排空结束残留 <= 正式窗口预期投递数的 0.1%。任一有效性条件不满足、发压端资源饱和或核对数据缺失时标为不确定，不能作为第一服务失败档。阈值只作用于正式窗口，排空只完成正式窗口消息，不被预热流量稀释。

### 可观测配置与资源

新增压测专用 Compose override，设置 `sql-show=false`、日志 INFO，保留 Agent 和 Prometheus；明确重建/重启服务才能应用，不暗示 yaml 更新会热修改 DataSource。记录生效配置并提供恢复默认 full 的命令，不改日常默认值。

现有 Prometheus 开启 Remote Write receiver，新增独立 Grafana 压测看板。k6 `systemTags` 白名单固定为 `scenario,name,status,method`，HTTP/WS 操作使用有限固定 name；自定义标签仅 `batch,round,phase,message_type`。禁用 `url,vu,iter,group,check,proto,subproto` 等默认标签，禁止 userId/messageId/chatId/Token，并提供结果保留/清理策略以限制批次增长。

周期采集 `docker stats`、k6 CPU/RSS、宿主 CPU/内存和环境版本；缺少采样时报告证据缺口。采样不额外引入 exporter，也不将容器 CPU 百分比直接误认为整机 CPU 百分比。

### 安全、恢复与清理

执行前检查实际 HTTP/WS/监控目标与健康状态，默认只允许 localhost/127.0.0.1；非本地发压需显式解锁并打印脱敏目标，仍不授权非本地直接数据库清理。所有 target URL 禁止 query 参数，Token、批次和运行选项只能走受控配置字段；校验实际连接目标、scheme、端口与库名，禁止通过重定向绕过目标约束。

同机互斥只允许一批执行。健康失败、阈值失败、资源饱和或中断时停止发送和升压、关闭 socket、等待排空、保存报告，清理不能把原始失败退出码覆盖为成功。

默认结束后清理，`KEEP_LOAD_TEST_DATA=1` 保留现场。清理先用 write-ahead journal 和本地数据库前缀发现重建清单，并在任何 Redis mutation 前对三库执行只读所有权核对；恢复快照绑定 seed/WAL/recovered SHA-256 并原子持久化，checkpoint 绑定 manifest digest 并整文件原子发布。待 socket 关闭和异步活动停止后按所属 Schema 顺序事务删除，32 张 Message 物理表都需覆盖。跨 Schema 不宣称全局原子事务；每个 Schema 允许“全部目标仍存在”或“已全部删除”恢复，部分删除或混入普通事实时 fail closed。

Redis 正常路径先通过业务 ACK 清空回执。异常残留清理前先关闭压测 socket、完成可达 ACK 和登出，再停止 `im-account-server`、`im-social-server`、`im-message-server`，等待端口及 Nacos/Dubbo 实例注销以冻结三个数据所有者。随后从批次 Message 事实或准备 journal 恢复 `messageId/chatId/receiverId` 并构造 receiptId，由测试侧 Java 清理器逐项调用已完善的生产 Store `confirm`；task map 已过期时仍按 receiptId 前缀与 hash 清除 delayed/blocking queue 孤儿元素，同时清除 attempt。

非 Receipt Redis 清理由同一测试侧 Java 工具通过真实 JetCache/Redis 配置逐键执行，allowlist 仅包含：Account 的 `USER_ID/USER_EMAIL/USER_EMAIL_CONTAIN/USER_SESSION`；Social 的 `GROUP_ID/GROUP_MEMBERS/GROUP_MEMBER/GROUP_MEMBER_CONTAIN/FRIENDS_USER/FRIEND_EDGE/FRIEND_ACTIVE`；Message 的 `im:message:presence:chat:<userId>`。输入只能是已对账批次的 userId、email、groupId 和关系组合，删除后逐项读取确认不存在。禁止扫描后批量删除、直接复制 JetCache 编码、操作 Redisson 内部编码、删除共享 queue key、FLUSHDB 或宽泛前缀全库清理。

无法证明静默、归属或清理完整时保留现场并维持失败状态；清理成功后重启三个数据所有者并等待业务端口、management health 和 Nacos/Dubbo 注册恢复。恢复失败仍视为清理失败，不让原始压测退出码被覆盖。禁止删日常账号、基础身份租约、管理数据或监控历史。

## 决策记录

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D01 | 目标链路 | IM 登录、WebSocket、私聊/群聊、接收与 ACK | 测真实核心链路，不只 REST |
| D02 | 结论目标 | 逐档探索容量基线 | 固定目标和长期稳定性延后 |
| D03 | 消息模型 | 70% 私聊、30% 群聊发送数 | 分开报告扇出后吞吐 |
| D04 | 发压位置 | 全部本机宿主发压 | 仅有一台机器，报告共享资源限制 |
| D05 | 工具 | k6，版本固定且验证 | HTTP/WS、阶段控制和报告统一 |
| D06 | 探索方式 | 20 冒烟，独立轮次倍增 | 减少前轮积压干扰 |
| D07 | 资源维度 | 连接和消息分别测 | 避免同时增长无法归因 |
| D08 | 稳定阈值 | 99.5% 连接，99.9% 投递并 ACK，接收 P95 500ms/P99 2s，无持续积压 | 正确性优先 |
| D09 | 数据准备 | 业务接口自动创建隔离批次 | 不复用日常账号或绕过约束 |
| D10 | 群规模 | 默认十人群，可配置 | 默认一发九收 |
| D11 | 正文大小 | 默认 256 UTF-8 字节，可配置 | 保持可复现 |
| D12 | 时长 | 1 分钟预热、5 分钟测量、1 分钟排空/冷却 | 登录和建连另计 |
| D13 | 输出 | Prometheus/Grafana + JSON | 趋势展示与机器可读结论同时保留 |
| D14 | 地址保护 | localhost 默认，非本地发压显式解锁 | 数据清理仍仅本地 |
| D15 | 资源证据 | docker stats、k6 进程和宿主轻量采样 | 不加 exporter |
| D16 | 登录计时 | 每轮真实登录，独立指标 | 不污染连接与消息窗口 |
| D17 | 建连速率 | 默认 20 connections/s，可配置 | 不把风暴容量当在线容量 |
| D18 | 消息连接数 | 最后稳定连接档的 50%，十人对齐 | 连接资源保留余量 |
| D19 | 接收在线率 | 100% 在线 | 完整覆盖下行和 ACK |
| D20 | 服务观测 | SQL 日志关闭、INFO、Agent/Prometheus 保留 | 生产近似配置 |
| D21 | 边界可信度 | 通过/失败边界各复测一次 | 不一致报告区间/不确定 |
| D22 | 数据清理 | 本地专用 SQL/Redis 精确清理 | 不增加生产删除 API，不删全卷 |
| D23 | 连接探索上限 | 默认 3200，可配置 | 触顶全通过报告下界 |
| D24 | 方案取舍 | 宿主 k6 + 模块化 Harness | 不采用 Compose k6 或巨型单脚本 |
| D25 | 四节设计批准 | 组件、数据、判定、监控安全均已逐节批准 | 书面 Review 后再建执行计划 |
| D26 | 单条消息关联 | 256B 正文内嵌固定宽度 correlation token，以 token、接收者、字符串 receiptId 核对 | 不修改生产协议且避免 long 精度丢失 |
| D27 | k6 调度模型 | 一个 VU 管理一个 cohort 的多 socket 长 iteration，单调时钟开环预排，迟到跳过不补发 | 兼顾跨接收者核对和受控目标速率 |
| D28 | 准备通知 | 建群前按 cohort 临时建连，完整接收并 ACK 系统通知后关闭 | 防止准备重投污染首轮 |
| D29 | Receipt 清理 | 业务 ACK 优先；异常时停 Message 并复用生产 Store confirm | 不复制或直接操作 Redisson 内部结构 |
| D30 | 崩溃恢复 | write-ahead journal 加本地数据库前缀发现对账 | 覆盖业务提交后清单落盘前崩溃 |
| D31 | 默认判据 | 固定在线、发送、投递、重复、速率、调度、积压和样本阈值 | 禁止看结果后修改容量标准 |
| D32 | 真实 smoke | `verify.sh load-test-smoke` 显式运行并绑定 HEAD/worktree evidence | 不并入 quick/full，不让替换依赖 E2E 冒充真实链路 |
| D33 | 群规模算法 | cohort、连接取整和 fanout 全部由 `groupSize` 派生，默认 10 | 消除可配置项与硬编码冲突 |
| D34 | 指标标签 | systemTags 仅 scenario/name/status/method，自定义仅 batch/round/phase/message_type | 限制 Prometheus 基数且保留必要诊断维度 |
| D35 | 孤儿 Receipt 队列 | 完善生产 Store confirm，使 task 过期时仍按 receiptId 定位并删除精确分片内全部既存重复项 | 测试清理器无需复制 Redisson 内部算法，size 上限避免无界并发循环 |
| D36 | 准备 ACK 数量 | 只等待非 owner 的 `groupSize - 1` 个通知与 ACK | 与现有群消息排除发送者的行为一致 |
| D37 | 非 Receipt Redis | 测试侧 Java 清理器使用真实缓存抽象和固定 key family allowlist | 避免 JetCache 编码漂移与共享数据误删 |
| D38 | 发压端饱和 | 固定 200ms 迟到、1MiB/1s 与 4MiB bufferedAmount、90% CPU 三采样阈值 | 饱和轮次标不确定，不误判服务失败 |
| D39 | 完成证据 | 真实 `load-test-smoke` 是任务完成的显式必跑门禁 | 保持不进入 quick/full，但实施不能跳过 |
| D40 | k6 写缓冲 | 删除不可用的 bufferedAmount 判据，使用调度、速率、CPU/RSS、宿主内存和发送异常 | 不基于 k6 未暴露的接口构造结论 |
| D41 | 清理静默 | 清理前停止 Account、Social、Message，结束后恢复并验证注册与健康 | 防止会话、缓存和异步派生状态回写 |
| D42 | 连接档时长 | 建连后 1 分钟稳定、5 分钟测量、1 分钟受控断开 | 覆盖心跳和持久在线行为 |
| D43 | 档位取整 | requested 向下取整到 groupSize 倍数并报告 effective，重复档去重 | 保持每个 cohort 完整且所有目标在线 |
| D44 | Load test 模块 | `im-load-test` 进入 Maven reactor，Java 清理器只存在于 test classpath | 稳定解析生产缓存依赖，同时不发布生产工具 |
| D45 | 消息探索上限 | 默认最高 3200 msg/s，可配置 | 单机通常可覆盖拐点，触顶全通过只报告下界 |
| D46 | Target URL 参数 | HTTP/WS/Prometheus/MySQL/Redis target 一律禁止 query | 消除秘密参数启发式的漏报与误报，运行参数走受控字段 |
| D47 | 清理恢复快照 | WAL+三库发现结果在 Redis 前原子固化并绑定 seed/WAL/recovered digest | 支持跨 Schema 部分完成重试，防止漏列 Receipt 或快照篡改 |
| D48 | 本地 JVM native memory | full 应用在关闭容器感知时固定线程栈、direct memory 和 code cache 上限 | 真实 smoke 发现 319 线程的 Account 及其他 Agent 应用逼近容器上限；显式 native memory 预算避免依赖关闭后的 JVM 自动推导，但不能替代实际 cgroup 容量证据 |
| D49 | 压测运行服务集合 | base 要求 19/19 健康；load profile 暂停 IAM/Audit/Admin/Monitor，只运行九个基础设施/观测服务和六个核心应用 | 单机 12 GiB 中管理应用不属于目标链路，其资源竞争会使核心服务重启并污染容量结论；cleanup 后必须恢复完整 base |
| D50 | 本地应用、OAP 与 MySQL 容器上限 | 十个应用在 base 使用 1 GiB；load profile 中 Message 使用 2 GiB、SkyWalking OAP 使用 1536 MiB、MySQL 使用 768 MiB，其余核心应用保持 1 GiB，并暂停四个非目标应用；profile 切换显式重建 OAP/MySQL，base 恢复 1 GiB/512 MiB | Message 曾在 1 GiB 下反复 OOM；`4soh4u` 确认 OAP 在 1 GiB 上限参与争用；`z138pu` 的 1000 秒轮末尾 MySQL 达 509.3/512 MiB，而宿主仍有 7.7 GiB、Message/OAP 均有余量。load 已释放四个管理应用的 4 GiB 配额，可为被测服务、观测平台和数据库提供 headroom；base 配置保持不变 |
| D51 | 准备通知超时 | 通知与 ACK 共用有界 deadline；全部 ACK 完成后取消该 deadline，再执行完整 retry quiet window | 真实批次出现 18/18 Receipt 已 ACK，但最后 ACK 完成较晚，旧总超时在静默窗口结束前误报漏通知；缺通知或缺 ACK 仍必须超时失败 |
| D52 | 资源采样周期 | 每轮采集后只等待到固定 5 秒周期的剩余时间，时间戳保留毫秒精度；k6 在采集中结束时不写残缺样本。证据要求中位间隔位于目标周期的 0.75 到 1.25 倍，且任一间隔不得超出 0.5 到 1.5 倍 | 真实连接轮发现 `docker stats` 耗时叠加固定 sleep，实际间隔漂移为 7 到 8 秒；`e0fwiy` 的 101 个完整样本仅有 6.361/6.270 秒两个采集耗时抖动，旧“任一间隔不得超过 1.25 倍”会误删整轮有效资源证据。中位周期约束拒绝持续漂移，单间隔边界拒绝重复采样和真实断档 |
| D53 | 消息轮强制收口 | drain 后先发送 WebSocket close；2 秒后仍在线才记录 `forcedStops` 并调用 `exec.test.abort`，runner 仅凭合法 summary 中的正数 `forcedStops` 接受退出码 108 | 50 msg/s 过载时服务积压使 close handshake 等待约 5 分钟；业务失败必须有界结束，同时不能把任意 k6 abort 当成有效轮次 |
| D54 | 消息发送互斥范围 | 私聊按排序后的双方 userId，群聊按 groupId 使用自动续期、最多等待 100 ms 的分布式锁；私聊锁内重新读取双方 Chat，群聊锁内读取 fanout 成员 Chat，两者均保留 token 幂等检查并提交成员副本，提交成功且释放锁后发布发送事件 | 真实压测证明 `chatId:messageToken` 让每条消息使用不同锁并触发乐观锁冲突，而永久等待又让 50 msg/s 下 Message 从约 385 增到 618 线程并拖住 k6；热点应快速返回操作频繁，客户端以同一 token 重试，不能在 RPC 线程中无界排队；提交后通知不应延长消息写事务的互斥时间 |
| D55 | 提交后首次通知 | Confirmable 通知先保存 ReceiptTask，再由独立虚拟线程执行首次 Broker 投递；虚拟线程启动失败或投递 RuntimeException 由 invoker 隔离，未启用重试时保持同步传播 | 真实压测发现群消息 after-commit 顺序执行九次 Bolt 推送并持有会话锁，Bolt 超时让已提交 RPC 返回失败；可靠重试已接管时不能阻塞或否定已提交事实。虚拟线程只承载已登记重试任务的首次投递，不改变 Receipt 重试节奏；500 人群的并发放大仍由真实 smoke 和资源证据持续观察 |
| D56 | base 恢复健康等待 | management 首次健康失败快速触发一次 restart；restart 后允许短暂 `unhealthy`，在 120 秒内继续等待最终健康 | Compose restart 后应用会经历正常的 readiness 过渡；第二次检查若仍 fail-fast，会把随后恢复健康的服务误报为恢复失败 |
| D57 | 压测批次登出恢复 | `logout-round` 对受控凭据返回 AUTH_FAIL(10001) 视为 Token 已失效并继续，其余业务错误仍失败 | 中断后的首次登出可能部分成功；重跑若从头遇到已退出 Token 就失败，会把批次永久卡在 DRAINING，认证失败本身已满足停止该 Token 活动的清理前置条件 |
| D58 | 真实 smoke 消息窗口 | 20 用户下使用 1 msg/s、1005 秒正式测量和 10 秒排空；连接仍测量 20 秒，10/50 msg/s 留作容量失败边界；仍要求实际发送至少 1000 | `9of5ub` 证明 10 msg/s 为明确失败档；`tonptj` 的 1000 秒计划因一个超过 200ms 的合法跳槽只产生 999 次发送并变为 uncertain。五个恢复槽不降低跳槽、正确性或延迟阈值，只保证单个调度抖动后仍可取得最小样本 |
| D58A | smoke 轮次超时 | smoke 使用独立 1500 秒 round timeout，正式容量探索仍沿用默认 900 秒 | `o0kuqo` 在 900 秒被截断；`t8trxv` 又因服务退化产生约 249 秒调度延迟并在 1200 秒被截断。1500 秒覆盖 1005 秒测量、10 秒 drain、已观测调度漂移和启动余量，且仍受 18000 秒总超时约束 |
| D59 | 好友私聊派生边界 | Social 提交后只调用一次 Message `preparePrivateChat`；Message 按 canonical user pair 有界加锁，并在一个事务内幂等补齐双方视角 | `or1oze` 证明跨服务串行两次 RPC 会在第一步异常时阻止第二步，且 Social 已提交无法回滚；一次 RPC 的双侧原子准备也能修复历史单侧状态 |
| D60 | 已知服务失败优先级 | 核心计数结构、实际速率或调度证据无效仍判 uncertain；已经成立的服务正确性阈值失败，不得被增长 backlog 或独立的资源采样节奏不确定降级 | `j4xrkw` 已明确违反发送、投递和延迟阈值，`e0fwiy` 已明确违反投递 ACK 阈值；这些事实足以确定对应档位不稳定，backlog 和资源采样质量通过独立 evidence 保持可见 |
| D61 | 消息速率与连接取整 | connection requested users 按 groupSize 向下取整；message target rate 保持原值，只对 message connection count 做 cohort 对齐 | `dcnawh` 的 5 msg/s 被旧 runner 当作 5 个连接取整到 0，说明两个量纲混用；消息速率不是用户数量，不要求为十的倍数 |
| D62 | 发送失败诊断维度 | summary 固定记录 private/group 失败数、`body.code` 中的 BUSY_ERROR(10010)、REPEATED_ERROR(10007)、其他业务失败和响应超时；不以任意错误码创建动态指标或标签 | 实时错误帧顶层 `code=-1`，稳定业务码位于 `body.code`；`fq0drm` 与 `e0fwiy` 的旧 summary 无法区分锁竞争、重复、其他响应和无响应。固定桶可定位根因，同时避免 Prometheus 高基数 |
| D63 | push 到达时刻 | 收到 push 时即捕获同机 `receivedAtMs`；若发送响应尚未收到，仅延迟核对、投递计数及 ACK，不重设 push 的到达时刻 | `vf0i6i` 的 1000/1000 发送、3400/3400 ACK 全通过但旧 P95 为 1031ms；真实 k6 `response-after-push` fixture 将发送 response 人为延后 600ms，旧指标误报 P95 607ms，新指标在相同先后顺序下稳定。历史轮无逐条到达时刻，不能重算为稳定档 |
| D64 | Dubbo 自动重试 | 通用 Consumer 默认 `retries=0`；Provider 的稳定 `RpcException` 由 Broker 从直接异常或有限 cause 链透传，未知 RuntimeException 仍映射为 `BROKER_UNAVAILABLE` | `v2i0d5` SkyWalking 证明首次 group Provider 调用成功提交并执行 2563ms，约 3 秒时 Dubbo 自动发起第二次调用，命中同 token 10007；另外两条重试与原调用争锁得到 10010。框架自动重放写 RPC 会把已提交成功变成客户端失败并制造并发，不能依赖重复异常补救 |
| D65 | 群消息批量持久化 | 新 Inbox 使用 batch insert；已有 Inbox 与成员 Chat 使用带 userId 分片键和旧版本、固定更新字段集合的 MyBatis-Plus Lambda Update，通过 `SqlSessionFactory` 按 MyBatis-Plus `executeBatch` 的事务参与方式打开临时 BATCH session，并在提交前检查每个 `BatchResult` 影响行数为 `1`。不能在已经绑定 SIMPLE session 的 Spring 事务中使用同一 Factory 的第二个 Batch `SqlSessionTemplate`。成员 Chat 只更新最新消息、已读消息、未读数、状态和活跃时间。Repository 不感知 MyBatis 和 ShardingSphere；新增/更新须在回填 pkId 前完成分类 | `2rsa1e` 的 1005/1005 发送及 3405/3405 ACK 证明真实 ShardingSphere 可运行，但 P95 仍为 1017ms，private/group 均约 1 秒，不能把 batch 称为性能修复。MyBatis-Plus batch 的整体布尔值不能证明每行更新，事后版本回查也不能证明本次更新的因果，因此改为直接校验 ShardingSphere 合并后的逻辑批次影响行数；当前实现仍需刷新真实 smoke 证明临时 BATCH session 与 Spring/ShardingSphere 事务组合 |
| D66 | 发送响应分段 | 消息场景分别记录发送到业务 response 的 P95/P99，以及发送到首个 push 的 P95/P99；响应超时不制造延迟样本，继续由独立 timeout counter 表达 | `ctykon` 证明 Message 通知 Executor 和 Broker 写入指标已生效，但端到端 P95 仍为 754ms。仅有发送到 push 无法区分上行 Provider 返回慢和下行 push 慢；固定 Trend 不增加协议字段或高基数标签 |
| D67 | Broker/Gateway 下行分段 | Broker 调目标 Gateway 使用 `im.broker.gateway.frame.write`；Gateway 本机 Channel 写入使用 `im.gateway.frame.write`。两者仅使用固定 outcome 和 duration，不记录任何身份标签 | `yca3c0` 的 response P95 435ms、push P95 1035ms，差值位于响应后的下行链路；分开观测 Gateway RPC 与本机 Netty write 后才能判断是 Broker 到 Gateway 还是客户端接收前后的剩余延迟 |
| D68 | Message Provider 聚合与观测预算 | private/group AppService 入口分别使用固定 duration 指标；load profile 将 OAP 提升至 1536 MiB，并在恢复 base 时重建回 1 GiB | `p6edqw` 排除下行后，`4soh4u` 在 OAP 贴近 1 GiB 上限时出现全链路退化；`ypatov` 使用 1536 MiB 后 60/60、204/204，response/push P95 为 411/444ms，private/group 平均约 199/267ms。观测平台资源必须纳入单机压测预算，不能把其争用误归因为业务 SQL |
| D69 | 准备通知重投幂等 | 同 receiver + 同 receipt 的 at-least-once 重投只重发 ACK，保留单一 durable intent，并从 ACK 完成后重新开始完整 quiet horizon；同 receiver + 不同 receipt 仍失败 | `g5e5e5` 在 9 个唯一 intent 均持久化后收到同一 receipt 重投，旧准备器误报 duplicate 并中止。服务契约允许重复投递，准备器必须验证身份一致和最终静默，不能把合法重投当数据错误 |
| D70 | smoke workflow 与容量判定分离 | smoke 顶层 status 表示真实流程、evidence、清理和 checksum 是否有效；connection 必须 stable，message 可为 stable 或明确 service_failed。uncertain、工具错误、缺轮次、工作树变化或清理失败仍使 smoke 失败 | `8y6059` 在完整 1000 条、generator stable、cleanup/checksum 正常时明确 service_failed。T10 已规定性能未达阈值是有效结论；若顶层仍强制 message stable，就会把可信容量失败误报成 Harness 实现失败 |
| D71 | 部署角色机器配置建议 | 最终容量报告按 HTTP Gateway、WebSocket Gateway、Broker、Account、Social、Message、MySQL、Redis、Nacos、Prometheus、SkyWalking OAP 输出配置矩阵；每行区分“当前工作负载下已验证运行规格”和“推荐规划起点”，并给出 CPU、内存、磁盘、容量证据副本数、HA 假设副本数、来源类别与关键说明。Markdown 与 JSON 保持相同角色和字段，`deploy/local/README.md` 只保留报告链接 | 所有资源数值均按每副本表达；已验证规格只表示该配置完成了报告所述本机负载，不声称是启动下限或生产最低值。Compose cgroup 上限只能证明已验证分配，不能冒充峰值；没有逐角色 CPU、磁盘或副本扩展证据时必须写“未验证”。推荐内存取 `max(已验证分配, 实测峰值 x 1.30)` 后向上取整到 256 MiB；推荐 CPU 取实测 P95 x 1.50 后向上取整到 0.5 core 且至少 1 core；推荐磁盘仅在有当前 footprint 时取 footprint x 2 后向上取整到 GiB，否则标记为未验证的工程起始值。容量证据副本数只记录实测拓扑，HA 副本数必须标记为未经过本次容量验证的可用性假设。所有两档数值都受单机 Docker Desktop 12 GiB 与当前工作负载限制，生产使用前必须在目标拓扑重新压测 |
| D72 | `BASE_READY` 清理收尾恢复 | cleanup 已完成并恢复 base 后，`cleanup --resume <batch>` 只在 batch ID 为六位小写字母数字、manifest 摘要和 batch 绑定匹配、七个 cleanup checkpoint 完整时销毁遗留 `credentials*.json` 与 `database-targets.json`；任何缺失或不匹配均保留现场 | 服务恢复完成到敏感文件 unlink 之间仍存在进程中断窗口；全局 `BASE_READY` 不能单独证明某批次已清理，必须用 digest-bound checkpoint 证明。该分支不重新执行 SQL/Redis 清理，也不删除 manifest、journal、summary 或资源证据 |

## 风险与回滚

- 共享本机资源、Agent/采样/正确性核对的开销影响结果；保存实际负载和工具饱和证据，不换算生产容量。
- 大量真实账号与 fanout-on-write 占用磁盘；准备和运行必须检查空间、执行总时长和核对产物上限。
- 清理是高风险路径；归属不明、异步未静默或跨阶段失败时保留现场，不为实现自动清理放宽安全条件。
- 移除 load-test 模块、脚本、监控改动可回滚代码；数据需按清单清理，恢复 Compose 默认配置不自动清除批次数据。
- 延后独立发压机、分布式发压、基础设施 exporter、故障注入和长稳认证。

## 验证策略

- RED/GREEN fixture：消息/ACK 协议、long ID、正文大小、重复/漏投、阶段边界、跨 socket 核对、触顶下界、不确定结果、边界复测与信号失败传播。
- 安全 fixture：非本地目标、重定向、错误 Schema、伪造清单、前缀冲突、Redis 共享队列、失败恢复与日常数据保留。
- 真实小规模验证：显式运行 `./scripts/verify.sh load-test-smoke`，隔离 20 用户并覆盖真实链路、系统通知排空、数据归属与清理，核对前后本地普通数据未变化；证据绑定 HEAD/worktree fingerprint，不自动开展全量 3200 压测。
- 真实连接清理证据：k6 受控关闭后对本轮全部用户查询 Broker 管理路由；剩余路由判为服务失败，查询不完整判为不确定。k6 summary、资源样本和路由结果合成为统一逐轮三态 JSON，不以 k6 进程退出替代路由清理事实。
- 监控契约：Compose override、Remote Write 和 Grafana query，禁止身份类高基数标签；资源采样缺失可见。
- 完整门禁：`readiness`、focused `load-test`、`quick`、`startup`、适用 `e2e`、`clean`、`full`、显式真实 `load-test-smoke`、report/handoff 与独立 `quality`；按 narrow-to-broad 顺序执行。没有 fresh smoke evidence 时不得完成实施计划。
- 配置建议审查：最终 Markdown/JSON 报告中的角色和字段一致；每个部署角色均按每副本列出当前工作负载下已验证运行规格与推荐规划起点，并能追溯到 Compose 限额、当前资源 evidence 或明确标记的未验证工程起始值。检查 30% 内存、50% CPU、100% 磁盘裕量及对应取整，容量证据副本与 HA 假设必须分列；缺少证据时保留“未验证”，不得用估算补成实测。两档全部数值都必须携带生产适用性限制和重新压测要求。

## 官方参考

- [k6 WebSockets](https://grafana.com/docs/k6/latest/using-k6/protocols/websockets/)。
- [Prometheus Remote Write output](https://grafana.com/docs/k6/latest/results-output/real-time/prometheus-remote-write/)。
- [Custom summary / handleSummary](https://grafana.com/docs/k6/latest/results-output/end-of-test/custom-summary/)。

## 执行计划

实施步骤和证据记录在 [IM 核心链路容量压测实施计划](../exec-plans/completed/2026-09-14-im-load-test.md)。
