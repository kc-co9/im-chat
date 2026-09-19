# IM 核心链路容量压测实施计划

> Coding Agent 使用 `superpowers:subagent-driven-development`（有子代理时）或 `superpowers:executing-plans` 执行本计划。详细步骤使用 `- [ ]`/`- [x]` 跟踪；除非用户明确要求，不创建提交。本计划在当前工作目录执行，不创建额外 worktree。

## Sprint Contract

- 目标：建立可重复的单机 IM 核心链路容量测试能力，并真实测得本机 full 环境的稳定 WebSocket 连接容量区间和 70% 私聊/30% 十人群聊消息吞吐区间。
- 范围：完善 Message Receipt 幂等清理；新增 Maven 测试模块 `im-test/im-load-test`、k6/Node 场景、批次准备与安全清理工具；新增压测 Compose override、Prometheus Remote Write、Grafana 看板、资源采样、统一执行脚本、Harness fixture/freshness；执行 20 用户 smoke 和正式容量探索；更新测试与运行文档。
- 不做事项：不压生产或共享环境；不引入分布式发压；不改消息公共协议；不新增生产数据删除接口；不把 k6 二进制、Token、批次数据或运行报告提交到 Git；不把本机结果换算成生产容量；不做数小时 soak、故障注入或额外 exporter。
- 验收标准：连接和消息阶段可独立执行；准备数据走真实 HTTP/WS 业务链路；清理只删除已对账批次且恢复三服务健康；正确核对每个接收者的通知与 ACK；容量判定与不确定条件符合设计；20 用户 smoke evidence fresh；正式连接与消息探索完成边界复测并输出 JSON/Grafana/资源证据；日常数据不变。
- 风险链路：测试数据跨 Account/Social/Message 三个 Schema 和共享 Redis；群消息 fanout-on-write 产生写放大；单机发压端可能先饱和；Receipt task TTL 与共享延迟队列可能出现孤儿；停止/恢复数据所有者失败会影响 full 环境；3200 用户准备和 Bcrypt 登录耗时较长。
- 依赖输入与所需权限：JDK 21、Maven 3.8.4+、Node 20.19+、npm、Docker/Compose、MySQL/Redis CLI 或 Java 驱动；k6 `v2.2.0` 已按官方 checksum 安装在 `$HOME/.local/bin/k6`。full Compose 已运行 19/19 健康；Docker Desktop 已配置 12GB。宿主磁盘使用率 98%，正式大批次前必须复查可用空间。

## 事实源

- 设计或产品规格：[IM 核心链路容量压测设计](../../design-docs/2026-09-14-im-load-test-design.md)、[实时链路已批准场景](../../product-specs/im-realtime-approved-scenarios.md)。
- 根与局部 Architecture：`ARCHITECTURE.md`、`im-gateway/ARCHITECTURE.md`、`im-broker/ARCHITECTURE.md`、`im-service/im-message/ARCHITECTURE.md`。
- API、协议或数据契约：`FrameCommand`、`FrameResponse`、Account `UserController`、Social `FriendController/GroupController`、Message notification model/`ReceiptType`、`docs/references/HARNESS_GUIDE.md`、`UNIT_TEST_GUIDE.md`、`SQL_GUIDE.md`。
- 相关代码入口：`RedisImMessageConfirmableStore`、`deploy/local/compose.yml`、Prometheus/Grafana provisioning、`scripts/local_up.sh`、`scripts/verify.sh`、`scripts/affected-modules.sh`、Harness report/handoff/cleanup scripts。

## 验证分层

| 层级 | 命令或证据 | 必须/可选 | 当前结果 |
|---|---|---|---|
| RED fixture | Receipt task 过期、配置/协议、WAL 恢复、清理 allowlist、计数/阈值、runner 失败传播 fixture | 必须 | T1-T7 均取得对应 RED，T7 首个缺失 override 失败 |
| 聚焦测试 | `mvn -q -pl im-test/im-load-test,im-service/im-message/im-message-server -am test`、`node --test im-test/im-load-test/tests/*.test.mjs`、`./scripts/verify.sh load-test` | 必须 | Node 202/202；Group/Private 发送锁 50/50、Message 四组聚焦测试通过；load-test 模式在最近真实 smoke 前运行通过，源码变更后需刷新当前证据 |
| 架构或契约 | affected mapping、模块边界、Prometheus/Grafana/Compose config、Harness 正反 fixture | 必须 | local/load Compose Harness 和真实合并配置通过；源码/文档变更后需刷新 fingerprint |
| 行为或运行环境 | `./scripts/verify.sh load-test-smoke`、正式 `connections/messages`、数据与服务恢复核对 | 必须 | 最终 scope：capacity `j765nm` 的 50/100/200/100/200 五轮全部 stable；smoke `u7apfw` 的 connection/message 均 stable；两轮 cleanup/checksum/base restore、worktree/scope stability 均通过 |
| 完整门禁 | `./scripts/verify.sh quick`、`startup`、`e2e`、`clean`、`full`、`report`、`handoff`、`quality` | 必须 | `affected`、`load-test`、`quick`、`startup`、`e2e`、`clean`、`full` 已在当前工作树通过；`report`、`handoff`、`quality` 及 fresh smoke/capacity evidence 仍待完成 |

## 任务状态

同一时间至多一项任务为 `active`。状态只能使用 `not_started`、`active`、`blocked`、`passing`。

| ID | 行为目标 | 范围 | 状态 | 验证证据 | 阻塞/备注 |
|---|---|---|---|---|---|
| T1 | 让 Receipt confirm 可清理 task 过期后的孤儿队列元素 | Message Store 与测试 | `passing` | RED 46 tests/4 failures；GREEN 聚焦 46/46、Message 全模块通过；spec/code-quality Review Approved | - |
| T2 | 建立 load-test Maven 模块和纯配置/协议契约 | im-test、k6/Node 基础文件 | `passing` | RED 4 missing modules；GREEN Node 33/33、npm 0 vulnerabilities、Maven reactor 通过、k6 2.2.0；spec/code-quality Review Approved | - |
| T3 | 实现可恢复批次清单和安全数据清理 | Java cleaner、WAL/manifest | `passing` | Node 69/69；Java 聚焦 65/65；Message+load-test reactor、dry-run、spec/code-quality Review 均通过 | 官方 GHCR 同版本 Ryuk 解决下载阻塞 |
| T4 | 通过真实业务接口准备 cohort 数据 | Node prepare、临时 WS、ACK | `passing` | RED 多轮覆盖恢复/安全断点；GREEN 聚焦 72/72、Node 131/131、Maven reactor 通过；spec/code-quality Review Approved | 未访问真实 full；真实 20 用户链路由 T8 smoke 验证 |
| T5 | 实现连接容量场景 | k6 connection scenario | `passing` | connection state RED/GREEN 13/13；Node 154/154；真实 k6 六轮为 stable/stable/service_failed/service_failed/service_failed/uncertain；clean/diff、spec/code-quality Review Approved | fixture 仅证明 mock 边界，真实服务由 T8/T9 验证 |
| T6 | 实现消息吞吐与精确核对 | k6 message scenario、report | `passing` | pure contracts 22/22；Node 181/181；真实 k6 message normal/saturated/duplicate/missing/unknown/ACK-failure/send-timeout 三态均符合预期；connection 无回归；spec/code-quality Review Approved | fixture 仅证明 mock 边界，真实服务由 T8/T9 验证 |
| T7 | 接入压测观测配置与统一编排 | Compose/Prometheus/Grafana/runner/resource | `passing` | T7 Harness、Compose JSON、Node 182/182、bash -n、diff check；spec/code-quality Review Approved | - |
| T8 | 纳入 Harness 并完成真实 20 用户 smoke | verify/report/handoff/fixtures | `passing` | 当前 fingerprint 的压测批次 ID `2stgoy`：workflow passed、connection stable、message 1005/1005 发送和 3405/3405 ACK、generator 201/201 stable、cleanup/checksum/worktree/scope 全部有效 | message P95 619.6ms 超 500ms，明确 service_failed；这是容量结论，不是 Harness 失败 |
| T9 | 执行正式容量探索并形成基线报告 | 连接与消息分轮、边界复测 | `passing` | 新 scope capacity `n7k2p4` 五轮 stable；smoke `n8sn14` workflow passed、connection stable、message 明确 service_failed；cleanup/checksum/worktree/scope 全部有效 | 连接下界 `>=200`；消息 1 msg/s 仍因 receive P95 超 500ms 无可重复稳定档；不外推生产容量 |
| T10 | 完整验证、文档与归档关闭审计 | full/quality/docs/plan | `passing` | 独立 Reviewer `Accept`；quality、full、load-test-smoke、report、handoff、cleanup、diff 关闭审计通过 | - |

## 详细任务

### T1 Receipt 孤儿队列幂等清理

输入与边界：不改变 ACK 协议或 Receipt 重投次数，只加深现有 `confirm(receiptId)` 的幂等语义；非法 receiptId 不得扫描共享队列。

文件范围：`im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/infrastructure/notification/redis/RedisImMessageConfirmableStore.java`、`im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/infrastructure/notification/redis/RedisImMessageConfirmableStoreTest.java`。

- [x] 在现有测试增加 task 存在、task map 已过期、delayed/blocking 重复项、重复 confirm、非零 ReceiptType 路由和 canonical receiptId 非法格式用例。
- [x] 运行 `mvn -q -pl im-service/im-message/im-message-server -am -Dtest=RedisImMessageConfirmableStoreTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`；最终一轮 RED 为 46 tests、4 failures，证明旧实现只删除一次且测试未区分队列状态。
- [x] 从 receiptId 第一个字段安全解析 `ReceiptType`，复用现有 hash 定位分片；queue、task、attempt 独立幂等删除，并按初始 size 上限排空全部既存匹配重复项。
- [x] malformed receiptId 在任何 Redis mutation 前拒绝：四段、有效 ReceiptType、三个 canonical 正整数 Long，禁止前导零、符号、空白、零、负数和溢出。
- [x] GREEN：聚焦测试 46/46；`mvn -q -pl im-service/im-message/im-message-server -am test` 通过；独立 spec/code-quality Review 均 `Approved`；主会话复跑聚焦命令退出 0。

完成条件：task 是否存在都能精确删除目标 delayed/blocking 元素和 attempts；普通 receipt 不受影响；聚焦回归通过。

### T2 Load-test 模块与纯契约

输入与边界：`im-load-test` 是测试模块，不发布生产主代码；默认 Maven 验证不触发真实负载。

文件范围：`im-test/pom.xml`、`im-test/README.md`、新增 `im-test/im-load-test/pom.xml`、`README.md`、`package.json`、`package-lock.json`、`config/defaults.json`、`lib/config.js`、`lib/correlation.js`、`lib/protocol.js`、`lib/round-result.js`、`tests/config.test.mjs`、`tests/correlation.test.mjs`、`tests/protocol.test.mjs`、`tests/round-result.test.mjs`。

- [x] 创建 Node RED fixture，覆盖目标保护、档位取整、256 UTF-8 字节正文、lossless long、生产 Frame、fanout、阈值和三态结论；初始 RED 为四个缺失模块。
- [x] 运行 `node --test im-test/im-load-test/tests/*.test.mjs`，初始退出 1，四个 `ERR_MODULE_NOT_FOUND`；后续 Review 缺口均先取得对应 RED。
- [x] 新建 Maven 测试模块并以 test scope 引入后续 cleaner 依赖；固定 Testcontainers 2.0.5、exec-maven-plugin 3.5.0、`ws:8.21.3` 和 `lossless-json:4.3.1`。
- [x] 固定连接/消息档、groupSize、messageBytes、70/30、全部窗口/阈值/超时、system/custom tags 和 max rounds；target URL 禁止 query，远程发压显式解锁但 cleanup 仅本地。
- [x] 实现深冻结配置、correlation、response/push Frame、receiptId 权威 ID、lossless HTTP long 和 stable/service_failed/uncertain classifier；非法/不可能证据不能冒充 stable，严重重复/错投保持 service_failed。
- [x] GREEN：`npm --prefix im-test/im-load-test ci` 0 vulnerabilities；Node 33/33；`mvn -q -pl im-test/im-load-test -am test` 通过；`git diff --check` 通过；spec/code-quality Review `Approved`。
- [x] 从官方 macOS arm64 zip 安装 `~/.local/bin/k6`，SHA-256 匹配 `37a028...e757c7`，`k6 version` 为 v2.2.0；临时下载文件已删除，二进制未进入仓库。

完成条件：模块进入 reactor；纯函数契约可在无 Docker/k6 时验证；真实负载仍只由显式入口触发。

### T3 可恢复清单与安全清理

输入与边界：先建清理能力再创建批量数据；所有删除基于 write-ahead journal 与数据库对账，清理失败必须保留现场。

文件范围：新增 `im-test/im-load-test/tools/lib/journal.mjs`、`im-test/im-load-test/tools/lib/manifest.mjs`、`im-test/im-load-test/tests/journal.test.mjs`、`im-test/im-load-test/tests/manifest.test.mjs`；新增 `im-test/im-load-test/src/test/java/com/co/kc/imchat/loadtest/cleanup/LoadTestCleanupMain.java`、`LoadTestCleanupConfig.java`、`LoadTestBatchManifest.java`、`LoadTestBatchRecovery.java`、`LoadTestReceiptCleaner.java`、`LoadTestCacheCleaner.java`、`LoadTestSqlCleaner.java`、`AccountCacheCleanupConfiguration.java`、`SocialCacheCleanupConfiguration.java`、`MessageRedisCleanupConfiguration.java`；新增同包七个 `*Test.java` 与 `src/test/resources/load-test/valid-manifest.json`。

- [x] RED 覆盖 intent/响应丢失/fsync/截断尾、manifest/snapshot 篡改、系统消息/Receipt 漏列、错误 Schema/实际 catalog、普通 Redis key、task 过期孤儿、重复清理、checkpoint 断写与 commit 后 checkpoint 丢失；每项先取得缺失行为失败。
- [x] Node 与 Java 聚焦 RED 命令均按计划运行；实现后扩大 Java 命令加入 `LoadTestBatchRecoveryTest`，最终 65/65。
- [x] 为 journal 实现 append+fsync/目录 fsync、幂等 operationId、completed/checkpoint 和已提交/未提交 reconciliation；manifest 原子发布且权限 `0600`。T3A spec/quality Review Approved，focused 35/35、全 Node 68/68；覆盖 append 失败重载、完整无换行/截断尾修复、返回值防内存状态污染。
- [x] Java cleaner 强制 localhost、准确 Schema、实际 Connection catalog/metadata URL、batch confirmation 和 WAL/manifest/DB 对账；offline dry-run 输出 structural-only 36 表摘要且不打开 runtime。准确 test-classpath 命令通过。
- [x] Receipt cleaner 只调用 Store confirm；Account/Social 使用独立真实 JetCache context 和生产 converter/prefix/encoder，Message 使用精确 presence key。所有 Redis mutation 前先完成 digest-bound 恢复快照与三库只读 ownership preflight。
- [x] SQL cleaner 使用 PreparedStatement 和 36 张明确表 allowlist。Message inbox：`db_im_private_inbox_message_0`、`db_im_private_inbox_message_1`、`db_im_private_inbox_message_2`、`db_im_private_inbox_message_3`、`db_im_private_inbox_message_4`、`db_im_private_inbox_message_5`、`db_im_private_inbox_message_6`、`db_im_private_inbox_message_7`、`db_im_group_inbox_message_0`、`db_im_group_inbox_message_1`、`db_im_group_inbox_message_2`、`db_im_group_inbox_message_3`、`db_im_group_inbox_message_4`、`db_im_group_inbox_message_5`、`db_im_group_inbox_message_6`、`db_im_group_inbox_message_7`；Message chat：`db_im_private_chat_0`、`db_im_private_chat_1`、`db_im_private_chat_2`、`db_im_private_chat_3`、`db_im_private_chat_4`、`db_im_private_chat_5`、`db_im_private_chat_6`、`db_im_private_chat_7`、`db_im_group_chat_0`、`db_im_group_chat_1`、`db_im_group_chat_2`、`db_im_group_chat_3`、`db_im_group_chat_4`、`db_im_group_chat_5`、`db_im_group_chat_6`、`db_im_group_chat_7`；Social：`db_friend`、`db_im_group_member`、`db_im_group`；Account：`db_user`。顺序固定为 Message inbox、Message chat、Social friend/member/group、Account user；每组用批次 userId/groupId/email 所有权谓词并 checkpoint，测试逐组断言。
- [x] Testcontainers 2.0.5 独占随机端口 `redis:7.2.0`，验证目标 key 删除、相邻普通 key 保留、重复清理和 context/client 关闭；Ryuk 使用官方 GHCR 同版本镜像，本地 tag 不改变版本。
- [x] GREEN：Node 69/69；Java T3 聚焦 65/65；`mvn -q -pl im-test/im-load-test,im-service/im-message/im-message-server -am test` 退出 0；`git diff --check`、clean、准确 dry-run 均通过；完整 T3 spec/code-quality Review Approved。

完成条件：即使操作响应后崩溃也能由 journal+前缀发现恢复；清理只影响批次并可重复执行；普通数据保护有正反测试。

当前恢复证据：T3 内部分为 T3A Node journal/manifest、T3B1 Java manifest/SQL/dry-run、T3B2 Receipt/JetCache/runtime 和 integrated recovery。WAL+三库发现覆盖未发布对象、系统建群消息和 Receipt 完整集合；恢复快照绑定 seed/WAL/recovered digest，checkpoint 绑定 manifest digest 并原子发布；完整 spec/code-quality Review Approved。

2026-09-14 主会话验证：`node --test im-test/im-load-test/tests/*.test.mjs` 为 68/68；`mvn -q -pl im-test/im-load-test -am -Dtest='LoadTestCleanupMainTest,LoadTestBatchManifestTest,LoadTestSqlCleanerTest,LoadTestReceiptCleanerTest,LoadTestCacheCleanerTest' -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test` 退出 0，模块测试 33/33；`verify.sh clean` 与 `git diff --check` 通过。该局部成功不替代包含 Testcontainers 的聚焦/完整门禁。

环境阻塞：`LoadTestCacheBootstrapTest` 拉取 `testcontainers/ryuk:0.14.0` 遇到 `registry-1.docker.io/v2/ context deadline exceeded`；主会话 `docker pull testcontainers/ryuk:0.14.0` 同样失败，宿主 curl 也超时。`redis:7.2.0` 已缓存，旧 `ryuk:0.6.0` 不得用作新版本替代。所有挂起测试已停止，full 未启动，未接触共享本地数据库或执行真实清理。

阻塞状态触发的 Harness 修复：`verify.sh clean` 的 active 计数因 `rg` 零匹配和 pipefail 提前退出，已修正为只接受无匹配退出码 1；`scripts/test-harness.sh` 增加只有 blocked/零 active 的隔离 fixture，规则同步 `HARNESS_GUIDE.md`。脚本变更在 T3 的环境失败归因范围内，完整回归仍待镜像可用后刷新。

该 Harness 修复的 `bash scripts/test-harness.sh` 已退出 0，包含 blocked/零 active 正例与双 active 反例；`verify.sh clean` 复跑通过。

镜像恢复：官方 Ryuk `0.14.0` 发布流程同时发布 Docker Hub 和 GHCR。主会话拉取 `ghcr.io/testcontainers/ryuk:0.14.0` 成功，源 digest 为 `sha256:7c1a8a9a47c780ed0f983770a662f80deb115d95cce3e2daa3d12115b8cd28f0`，本机 arm64 image ID 为 `sha256:52785d2485ac7df684b8740e233427b5ed4d0ee39132f1bb54ddc54df767bf38`；同镜像添加默认 `testcontainers/ryuk:0.14.0` 本地 tag，不修改 Testcontainers/Ryuk 版本或系统代理。

随后真实 Redis fixture 取得 RED：重复清理时 JetCache REMOVE 失败码被错误当作删除失败。新增 absent/readback 单测先失败，修正为以 GET 的 NOT_EXISTS/EXPIRED 后置条件判定，GET 失败或仍有值继续失败。主会话包含全部六类测试的准确聚焦命令退出 0，Ryuk 与随机端口 Redis 均真实启动且资源回收；完整 Review 与回归仍待执行。

### T4 真实业务数据准备

输入与边界：用户、好友、群和 Chat 全部通过 `18010` 真实路由创建；本地 DB 只用于恢复对账与清理，不用于准备写入。

文件范围：新增 `im-test/im-load-test/tools/data.mjs`、`im-test/im-load-test/tools/lib/http-client.mjs`、`im-test/im-load-test/tools/lib/local-reconciler.mjs`、`im-test/im-load-test/tools/lib/setup-websocket.mjs`、`im-test/im-load-test/tools/lib/preparation.mjs` 及其对应 Node 测试；修改模块 README、当前设计和计划以记录凭据文件与分阶段恢复契约。

- [x] RED：HTTP `code!=0`、302 重定向、Token 泄漏、长 ID、部分注册成功、好友事件未收敛、群通知漏投/重复、owner 被错误计入 ACK 屏障，以及 signUp/addFriend/createGroup 三类“业务提交但响应丢失” unresolved intent。
- [x] 运行 `node --test im-test/im-load-test/tests/http-client.test.mjs im-test/im-load-test/tests/setup-websocket.test.mjs im-test/im-load-test/tests/preparation.test.mjs`，预期因 prepare/reconcile 状态机尚未实现而 RED。
- [x] 实现 `POST /account/user/signUp`、`signIn`、`GET userDetail`，解析统一 HttpResult；请求日志只输出稳定 operationId，不输出口令/Token。
- [x] 在完整 cohort 内按环调用 `POST /social/friend/addFriend`，通过 `GET /message/chat/getChatList` 获得双方私聊 chatId；任何派生关系未在时限内出现则停止，不伪造 ID。
- [x] 使用 `ws:8.21.3` 为 cohort 建临时连接后调用 `POST /social/group/createGroup`；等待其他 `groupSize-1` 成员系统通知，按字符串 receiptId ACK 并验证业务响应，owner 副本走 DB 对账。
- [x] 为每个副作用实现 `INTENT_FSYNCED -> INVOKED -> RECONCILED -> COMPLETED_FSYNCED` 状态机：signUp unresolved 先用已知凭据 signIn/本地 Account 只读对账；addFriend unresolved 先查双方 friendList/本地 Social 只读对账；createGroup unresolved 按唯一批次群名查 groupList/本地 Social 只读对账。确认未提交后才允许重试，已提交则补 completed，不创建重复群。
- [x] `IM_LOAD_TEST_DATABASE_TARGETS_FILE` 指向权限 `0600` 的临时 JSON，精确提供 account/social/message 三个本地 owner URL；第一个业务 mutation 前完成连接、Schema 和 cleanup host/port 预检，T7 编排负责创建和销毁该文件。
- [x] Social 好友/群副作用与 Message Chat、系统消息、Receipt 派生收敛分阶段持久化；已确认 Social 提交后禁止重试 mutation，ACK journal 支持截断尾修复、DB receipt seed 与未完成 ACK 重发。
- [x] 每完成一个 cohort 发布 checkpoint；支持 `prepare --resume <batch>`，逐项 reconcile unresolved intent，不只跳过 completed。
- [x] 重跑上述 Node fixture，并用 2 用户/1 cohort 的 mock server 注入三类 response-loss，预期只产生一个用户/双向好友关系/群；不接触真实 full。

完成条件：准备器可恢复、无敏感输出、形成完整 cohort manifest，准备通知静默后才结束。

### T5 WebSocket 连接容量场景

输入与边界：连接测试不发送业务消息；登录单独计时；每个 VU 管理一个完整 cohort。

文件范围：新增 `im-test/im-load-test/scenarios/connection-capacity.js`、`im-test/im-load-test/lib/connection-state.js`、`im-test/im-load-test/tests/connection-state.test.mjs`、`im-test/im-load-test/tools/mock-runtime.mjs`、`im-test/im-load-test/tools/connection-fixture-check.mjs`、`scripts/test-load-test-k6-scenarios.sh` 及对应 fixture 测试；修改 `tools/data.mjs` 与模块 README。

- [x] RED：requested/effective 档位、20 connection/s 调度、握手业务失败、非预期断开、测量末在线比例、受控关闭不计失败、路由清理超时。
- [x] 运行 `node --test im-test/im-load-test/tests/connection-state.test.mjs`，预期因全局连接槽和三态判定尚未实现而 RED。
- [x] runner 在 k6 前通过 `tools/data.mjs login-round` 以有界并发完成全体真实登录，写入 `0600` Token 文件和独立认证指标；成功数不足 effective users 时不启动连接场景。k6 init 使用 `SharedArray` 只读该轮 manifest/Token，形成显式认证屏障。
- [x] k6 options 固定 `per-vu-iterations`、`vus=effectiveUsers/groupSize`、`iterations=1`、有界 `maxDuration`；`__VU-1` 不可变映射 cohort。socket 全局序号为 `cohortIndex*groupSize+memberIndex`，目标时刻为 `roundStart+ordinal/20s`；迟到超过 200ms 跳过且本轮 uncertain，不追赶形成突发。
- [x] 建连完成后执行 1 分钟稳定、5 分钟测量、1 分钟受控关闭；测量连接成功、末尾在线和异常断开，验证 Gateway/Broker 路由有界清理。
- [x] 输出固定 tags 和 JSON summary；任何发压证据缺失标 uncertain。
- [x] `tools/mock-runtime.mjs` 在随机 loopback HTTP/WS 端口提供登录、握手、路由和可控断开，`scripts/test-load-test-k6-scenarios.sh connection` 负责启动、等待 ready、执行真实 `K6_BIN run scenarios/connection-capacity.js` 和 trap 回收。运行 `bash scripts/test-load-test-k6-scenarios.sh connection`，以缩短窗口执行 20/50 requested，验证 k6 options、auth barrier、VU/cohort assignment、全局 ordinal、effective、断开和退出分类；重跑 Node test 预期通过。

完成条件：连接档可独立重复执行且不会发消息；稳定/失败/不确定三态判定可由 fixture 复现。

### T6 消息吞吐与精确投递核对

输入与边界：使用最后稳定连接档 50% 并按 groupSize 取整；每个 cohort 内完成所有发送/接收/ACK 关联。

文件范围：新增 `im-test/im-load-test/scenarios/message-throughput.js`、`im-test/im-load-test/lib/message-scheduler.js`、`im-test/im-load-test/lib/delivery-accounting.js`、`im-test/im-load-test/lib/load-generator-health.js`、`im-test/im-load-test/tests/message-scheduler.test.mjs`、`im-test/im-load-test/tests/delivery-accounting.test.mjs`、`im-test/im-load-test/tests/load-generator-health.test.mjs`；修改 `im-test/im-load-test/tools/mock-runtime.mjs`、`scripts/test-load-test-k6-scenarios.sh`。

- [x] RED：70/30 精确分配、固定 256B、迟到>200ms 跳槽不补发、发送失败进入分母、私聊一收、群聊 groupSize-1、重复不抵漏投、未知接收者为失败、ACK code 失败、窗口隔离、backlog 三桶、排空残留、样本不足、速率偏差和 generator saturation=uncertain。
- [x] 运行 `node --test im-test/im-load-test/tests/message-scheduler.test.mjs im-test/im-load-test/tests/delivery-accounting.test.mjs im-test/im-load-test/tests/load-generator-health.test.mjs`，预期因发送序号、核对和饱和分类缺失而 RED。
- [x] 沿用 T5 的 auth barrier 和 `per-vu-iterations`。消息全局序号 `n` 由每个 VU 从自身 `cohortIndex` 开始、按 `cohortCount` 递增；目标时刻为 `measureStart+n/targetRate`，`n%10<7` 为私聊、其余为群聊，sender/receiver 由 n 在 cohort 内确定，保证全局而非逐 VU 舍入后的 70/30。迟到超过 200ms 跳槽，不追赶；不引用不存在的 bufferedAmount。
- [x] correlation token 嵌入正文并从 notification 解析；以 token+receiver+receiptId 去重，使用 receiptId 构造 `notification.ack`，正式窗口末消息可在排空完成。
- [x] 实现 D31 全部阈值；k6 CPU/RSS、宿主内存或调度证据缺失/饱和只产生 uncertain，不产生 service failure。
- [x] 为正式发送、通知和 ACK 输出 bounded custom metrics；systemTags/custom tags 严格符合 D34。
- [x] 扩展 mock runtime 生成两 cohort 的正常、乱序、重复、漏投、未知接收者和 ACK 失败。运行 `bash scripts/test-load-test-k6-scenarios.sh message`，真实执行 `K6_BIN run scenarios/message-throughput.js` 的正常与失败 fixture，验证 k6 options、全局 message ordinal、Frame 处理、跨 socket 核对及稳定/失败/uncertain 退出分类；重跑上述 Node 命令预期通过。

完成条件：每个有效发送可精确核对所有预期接收者；吞吐三态判断不受预热样本或 generator 瓶颈误导。

### T7 观测配置与统一编排

输入与边界：压测配置不修改日常默认；必须重建/重启应用后才视为 `sql-show=false` 生效。

文件范围：新增 `deploy/local/compose.load-test.yml`、`deploy/local/grafana/provisioning/dashboards/im-chat-load-test.json`、`scripts/load_test.sh`、`scripts/load_test_resources.sh`、`scripts/test-load-test-harness.sh`；修改 `deploy/local/prometheus/prometheus-full.yml`、`deploy/local/compose.yml`、`deploy/local/grafana/provisioning/dashboards/dashboards.yml`、`deploy/local/README.md`、根 `README.md`。

- [x] 创建 `scripts/test-load-test-harness.sh` 的 runner/config RED fixture：override 未关闭 SQL、未保持 Agent/INFO、Prometheus 未开启 receiver、动态标签、缺少 restore、脚本无超时/锁/信号传播、清理覆盖原退出码、资源采样缺失；运行 `bash scripts/test-load-test-harness.sh`，预期在首个缺失契约处失败。
- [x] 配置 Prometheus `--web.enable-remote-write-receiver` 并固定 k6 Remote Write URL；新增独立压测 dashboard，查询仅使用低基数标签。
- [x] override 只为十个应用设置 `im.datasource.sharding.sql-show=false` 和 INFO，保留 SkyWalking/metrics；脚本通过 Compose config 和服务环境验证实际生效。
- [x] runner 提供 `doctor/prepare/connections/messages/smoke/cleanup/all/restore`，互斥锁、单轮/阶段/总超时、signal trap 和原始退出码优先级准确。状态只允许 `BASE_READY/APPLYING/LOAD_READY/RUNNING/DRAINING/FREEZING/FROZEN/CLEANING/RESTORING/BASE_READY`，每次转换原子写 journal。
- [x] doctor 严格要求 k6 2.2.0；官方 macOS arm64 zip checksum 为 `37a028506bf13578de66c906296803775df28cc2504b1a8c5d786b2803e757c7`，仓库不保存二进制。
- [x] resource sampler 每 5 秒采集 docker stats、k6 PID CPU/RSS、逻辑核数和宿主可用内存；指标不可用时显式标 unknown。
- [x] 固定 `BASE_COMPOSE=(docker compose -f deploy/local/compose.yml --profile full)` 和 `LOAD_COMPOSE=(docker compose -f deploy/local/compose.yml -f deploy/local/compose.load-test.yml --profile full)`；应用 allowlist 仅 `im-http-gateway im-ws-gateway-server im-broker-server im-account-server im-social-server im-message-server im-iam-server im-audit-server im-admin im-monitor`。
- [x] apply 先验证健康的 19 服务 base，未就绪才运行 `./scripts/local_up.sh full`；load profile 暂停 IAM/Audit/Admin/Monitor，只 force-recreate 六个核心应用和 `prometheus-full`，轮次结束检查核心健康且零重启；base restore 恢复 IAM 后逐个重建三个 management 应用，避免 IAM 目录注册竞态。freeze 只 `stop im-account-server im-social-server im-message-server` 并等待端口及 Nacos/Dubbo 注销；clean 后严格验证 19/19 health 与 base 环境。
- [x] signal 在 RUNNING 先进入 DRAINING、关闭发送/socket/ACK/登出，再 freeze/clean/restore；`KEEP_LOAD_TEST_DATA=1` 停在 LOAD_READY 并保留 owner 运行。cleaner 失败时状态保持 FROZEN、三个 owner 故意保持 stopped，禁止自动 base restore；重跑 `cleanup --resume` 成功后才恢复。restore 失败记录 `RESTORING` 和原始错误，不能覆盖压测退出码。
- [x] 重跑 `bash scripts/test-load-test-harness.sh`，再运行 `docker compose -f deploy/local/compose.yml -f deploy/local/compose.load-test.yml --profile full config`，预期 fixture 与 Compose 解析均通过。

完成条件：日常 full 与 load-test override 可双向恢复；Prometheus/Grafana/k6 JSON 与资源样本都有稳定入口；失败不会留下假成功状态。

### T8 Harness 接入与真实 smoke

输入与边界：静态 fixture 进入默认 Harness，真实 smoke 只由显式模式执行，但任务完成必须有 fresh evidence。

文件范围：新增 `scripts/lib/load-test-evidence.sh`、`im-test/im-load-test/tools/route-cleanup.mjs`、`round-report.mjs` 及测试；修改 `scripts/test-load-test-harness.sh`、`scripts/test-harness.sh`、`scripts/verify.sh`、`scripts/affected-modules.sh`、`scripts/harness-report.sh`、`scripts/harness-handoff.sh`、`scripts/harness-cleanup.sh`、`docs/references/HARNESS_GUIDE.md`、`im-test/AGENTS.md` 和压测运行文档。`.harness/` 已由既有根规则完整 ignore，无需重复添加子路径。

- [x] 向 `scripts/test-load-test-harness.sh` 追加 `verify.sh load-test/load-test-smoke` 与 evidence RED fixture，覆盖模式路由、失败 evidence、运行中 worktree 变化、cleanup 状态、ordinary-data checksum 及 formal scope freshness；缺失 helper/mode 和 batch evidence 均先取得 RED。
- [x] `load-test` 执行 Node+Java+config fixture；`load-test-smoke` 检查 full/override 后执行固定 20 用户最短有效窗口，并写 `.harness/verifications/load-test-smoke.json` 与 `.harness/load-test/<batch>/smoke-summary.json`。
- [x] `load-test-evidence.sh` 记录 full HEAD/worktree fingerprint 和 load-test scope fingerprint；scope 覆盖全部 Git-visible 文件，只排除 `PROGRESS.md`、`docs/exec-plans/active/**` 与 `docs/exec-plans/completed/**` 执行状态文件。运行期间任一 full/scope 输入变化都失败；运行后仅状态文件变化允许 formal evidence 保持 fresh；config digest 由当前 resolved smoke 配置重算。
- [x] report/handoff 同时显示 smoke 与 formal capacity 的 status/freshness/batch/configDigest/worktreeStable/cleanup/ordinaryDataUnchanged/perRoundDecisions，不把缺失、stale 或 unstable 当通过；cleanup Harness 识别凭据、锁和中断批次但不误删报告。
- [x] affected mapping 让 `im-test/im-load-test/**` 选择该模块和 architecture；共享脚本变化继续保守全量。
- [x] 启动 full+override，记录普通数据 checksum，运行 `./scripts/verify.sh load-test-smoke`；`2stgoy` workflow passed，连接 stable，消息明确 service_failed，清理、恢复及普通数据 checksum 均有效。
- [x] 重跑 `bash scripts/test-load-test-harness.sh`、`./scripts/verify.sh load-test` 并刷新 report/handoff；smoke evidence 的 workflow 与逐轮容量 decision 分开展示。

完成条件：mock fixture 与真实 20 用户 workflow/evidence/cleanup/checksum 均有效；connection stable；message 形成 stable 或明确 service_failed 容量结论，uncertain 不得完成；没有测试批次数据残留。

### T9 正式容量探索与基线报告

输入与边界：这是本机共享资源基线，不是生产 SLA；只有 smoke 通过且清理可恢复后才运行。

文件范围：`.harness/load-test/<batch-id>/` 运行产物（ignored）、`im-test/im-load-test/README.md` 的结果解读入口、最终 Markdown 报告 `docs/load-test-reports/2026-09-16-local-capacity-baseline.md` 与脱敏 JSON 摘要 `docs/load-test-reports/2026-09-16-local-capacity-baseline.json`；不把 Token 和原始批次数据提交。

- [x] 运行 `./scripts/load_test.sh doctor`，确认 k6、19/19、Prometheus/Grafana/WS、Docker 与磁盘；记录 M1 Pro、10 logical CPU、Docker 约 12 GiB 和 scope fingerprint。
- [x] 使用 `load_test_config_digest` 的 canonical JSON、Compose/scenario/k6 digest 规则生成非敏感 config digest；报告和 runtime evidence 不保存 Token、密码或配置正文。
- [x] 磁盘 99%、BanyanDB 多 GiB，按资源不足分支准备 200 用户压测批次 ID `3oykv8`，不虚构 3200 用户能力。
- [x] 运行 50/100/200 连接档并复测 100/200；全部 stable，报告可信下界 `>=200`，400+ 因资源约束未测。
- [x] 20 用户、1 msg/s 已由 `2stgoy` 明确 service_failed，因此消息升压按 `no_stable_message_tier` 跳过；不把服务失败当工具错误。
- [x] 保存逐轮三态、summary、资源和 route evidence；结合 Grafana/SkyWalking/Micrometer 分段指标形成资源归因和不确定项。
- [x] 输出 `docs/load-test-reports/2026-09-16-local-capacity-baseline.{md,json}`，包含环境、阈值、连接下界、消息失败边界、P95/P99、清理和风险，不含凭据。
- [x] 清理 `3oykv8` 并恢复 base；状态 `BASE_READY`、19/19，批次凭据/数据库目标已删除。
- [x] 记录最终 capacity evidence 的 HEAD/full/scope/config、cleanup/checksum 和逐轮 decision；report/handoff 在 T10 刷新。仅状态文件更新不使 scoped evidence 过期。
- [x] 最终 scope 批次 `j765nm` 运行 50/100/200 并复测 100/200，五轮均 stable；cleanup passed、ordinaryDataUnchanged、worktree/scope stable，脱敏 capacity evidence 与批次 summary 一致。
- [x] 最终 smoke 批次 `u7apfw` 的 connection/message 均 stable；cleanup passed、ordinaryDataUnchanged、worktree/scope stable，完整 19 服务恢复成功。
- [x] direct Review 发现 restore 已进入 `BASE_READY`、敏感文件尚未 unlink 时 `cleanup --resume` 无法恢复；RED fixture 复现后，runner 仅在 batch ID 合法且 manifest digest/七个 checkpoint 完整时允许完成最终销毁。新 scope capacity `n7k2p4` 五轮 stable；smoke `n8sn14` workflow passed、connection stable、message 明确 service_failed；两批次 cleanup/checksum/worktree/scope 全部有效，19/19、`BASE_READY` 且无敏感文件残留。

完成条件：连接有可信容量区间、下界或不确定结论；消息有稳定档、明确失败边界或规范 skipped 原因；适用边界复测完成，环境恢复且无批次凭据残留；脱敏 Markdown+JSON 报告已生成。

### T10 集成验证、文档和归档

输入与边界：不修改已批准容量阈值来使结果通过；性能未达阈值是有效测试结论，不等于实现门禁失败，工具错误/数据错误/清理失败才是实现失败。

Review 范围：当前分支相对 `25fc50d` 的全部变更。写入范围：`im-test/im-load-test/README.md`、`deploy/local/README.md`、`docs/load-test-reports/2026-09-16-local-capacity-baseline.md`、`docs/load-test-reports/2026-09-16-local-capacity-baseline.json`、`docs/references/HARNESS_GUIDE.md`、`docs/design-docs/2026-09-14-im-load-test-design.md`、`docs/design-docs/index.md`、`docs/exec-plans/active/2026-09-14-im-load-test.md`、`docs/exec-plans/active/README.md`、`docs/exec-plans/completed/README.md`、`PROGRESS.md`；Review 发现实现缺陷时重新打开其所属任务，不在 T10 混入未计划重构。

- [x] 运行 `./scripts/verify.sh affected`、`load-test`、`quick`、`startup`、`e2e`、`clean`、`full`；全部通过，`full` 为 1187 tests、0 failures、0 errors、2 skipped。首次 `quick` 因本地 MySQL health 收敛失败，环境恢复后重跑通过。
- [x] 在 cleanup runner 修复后的工作树重新运行 `./scripts/verify.sh load-test-smoke`；`n8sn14` evidence fresh、workflow passed、connection stable、message 因 receive P95 超阈值明确 service_failed，cleanup passed、ordinaryDataUnchanged=true、worktree/scope stable，base 恢复 19/19。
- [x] 使用最终 smoke/capacity 资源 evidence 校准 Markdown/JSON 容量报告：按 HTTP Gateway、WebSocket Gateway、Broker、Account、Social、Message、MySQL、Redis、Nacos、Prometheus、SkyWalking OAP，以每副本口径给出当前工作负载下已验证运行规格与推荐规划起点。内存按 `max(已验证分配, 峰值 x 1.30)` 向上取整到 256 MiB，CPU 按 P95 x 1.50 向上取整到 0.5 core 且至少 1 core，磁盘按可得 footprint x 2 向上取整到 GiB；无证据项明确标记未验证。分列容量实测副本和未验证 HA 假设，保证 Markdown/JSON 角色、字段、来源类别和生产边界一致，并在 `deploy/local/README.md` 增加报告链接。
- [x] 同步设计正文和 `docs/design-docs/index.md` 的实施状态；不得把执行状态或过时压测结论留在设计索引中。
- [x] 主会话直接 Review 覆盖清理恢复状态机、formal evidence freshness、容量归因与机器配置建议边界；未发现新的实现缺陷，`git diff --check` 与 `cleanup` 通过。该结论不替代独立 Reviewer。
- [x] 独立 spec/code-quality Review 检查设计一致性、清理安全、结果归因和 Harness freshness；独立 Reviewer 返回 `Accept`，12 个质量单元 fingerprint 与当前 request 一致。
- [ ] Review 若修改 load-test scope 中任一实现、配置、Harness 或非状态文档，重新打开 T9 并刷新正式容量 evidence；不得用旧性能结果匹配新实现。
- [x] 运行 `./scripts/verify.sh quality`，检查报告与十二/新增测试模块评分，清理任务产生的临时调试工件；当前结果为 `Accept`，质量快照已生成。
- [x] 独立 Review 和 quality Accept 后，将本计划移动到 completed，更新 `PROGRESS.md` 为 none；归档后不再改 tracked 文件。
- [x] 独立 Review 通过后执行归档关闭审计：`full`、`load-test-smoke`、`report`、`handoff`、`quality`、`cleanup`、`git diff --check`；任一失败则恢复 active 并重新打开 T10。

完成条件：代码/fixture/真实 smoke 全部通过；正式容量结论和残余风险可复查；计划归档及关闭审计有效；无提交，除非用户另行明确授权。

## 真实压测问题与处置记录

本节记录 T8 真实运行中暴露的问题。`已验证` 仅表示对应回归或真实批次确实通过，不等于正式容量结论；失败批次的非敏感 summary、资源样本和 cleanup checkpoint 保留在 ignored 的 `.harness/load-test/<batch-id>/`，不能将包含 Token 的历史原始文件提交。

| 问题 | 现象与证据 | 根因/归因 | 已实施处置与验证 | 状态/后续 |
|---|---|---|---|---|
| 本地容器 OOM | 768 MiB 启动/20 用户准备时 Java 被 cgroup 杀死；`yom1wm` 消息轮 Message 在 1 GiB 下三次从约 220 MiB 增至 1013/1017 MiB 后重启，`RestartCount=3`，成功发送仅 414/1000 | JVM heap 不是唯一占用；Agent、ShardingSphere、Nacos、Dubbo、Redisson 和线程栈形成约 0.8 GiB 常驻，压测期间线程增加打满 1 GiB | base 十应用改 1 GiB；load 暂停四个非目标管理应用，仅 Message 提升到 2 GiB；Compose 正反 fixture 验证 base/load 精确预算，`ik7ps1` 运行中 Message 0 重启 | 1 GiB OOM 已验证缓解；2 GiB 不代表吞吐容量，正式轮必须继续监测内存/重启 |
| 消息写并发冲突与无界等待 | 旧 `chatId:messageToken` 每条消息锁不同，压测有 Group Chat 乐观锁异常；修正粒度后 50 msg/s 下 Message 约 385 -> 618 个线程，k6 CPU 近 0 仍长时间无法收口 | 同一会话更新成员 Chat 应串行，但 `FOREVER_WAIT` 把热点请求无界堆积于 Dubbo 工作线程 | 群按 groupId、私聊按 canonical user pair 锁内重读；改用项目已有自动续期/100 ms `tryLock`，热点返回操作频繁；Group/Private 共 50 个用例先 RED 后 GREEN，`cyvek2` 50 msg/s 轮日志出现有界锁失败且 Message 未重启 | 正确性互斥与有界锁已通过聚焦回归；50 msg/s 仍是失败/过载档，不声称容量达标 |
| 已提交消息通知拖住发送 | 10/50 msg/s 轮次出现 Broker Bolt 超时、已提交事务对发送方返回失败及 ACK 积压 | 群消息 after-commit 顺序推送九个成员且持有发送锁；首次推送异常会否定已提交事实 | Confirmable 先保存 ReceiptTask，再用 core 8/max 32/queue 1000 的有界执行器发首次通知；拒绝/RuntimeException 留给重试，禁用重试仍同步传播；`ImMessageNotifierInvokerTest` 覆盖异步与失败边界 | 发送线程不再同步等待 Bolt 的聚焦与真实日志已验证；Receipt 与 MySQL 不原子，仍非 Outbox |
| 消息轮 WebSocket close 被拖延 | 50 msg/s 过载轮 close handshake 被积压约 5 分钟；后续 `ik7ps1`、`cyvek2` 在预期窗口外仍未生成消息 summary | 大量消息/ACK 与服务端排队使 k6 事件循环无法及时运行 drain/close 定时器；单纯增加内存和 100 ms 锁等待不能让 50 msg/s 变成健康档 | drain 后正常 close，2 秒仍在线记录 `forcedStops` 并 k6 abort；runner 仅对合法 summary 且 forcedStops>0 接受 108；Node/runner 正反 fixture 通过；smoke 默认改 10 msg/s x 100 秒、drain 10 秒，仍 1000 条 | 50 msg/s 的有效结束与稳定容量尚未证实；不使用交互 PTY Ctrl-C 收口，其信号未传给内层 runner，必须用显式 cleanup 恢复 |
| 资源采样漂移与误判 | 早期采集耗时与固定 5 秒 sleep 叠加为 7-8 秒；修复后 `e0fwiy` 有 101 个完整样本，中位周期约 5 秒，仅两个间隔因 `docker stats` 耗时达到 6.361/6.270 秒，却被旧“任一间隔必须在正负 25%”判为无效 | 首个问题是采样周期未扣除采集耗时；第二个问题是证据规则把偶发采集耗时抖动等同于缺采样 | sampler 只 sleep 剩余周期且不写残缺尾样本；判定要求中位间隔在 0.75-1.25 倍，任一间隔在 0.5-1.5 倍。新增偶发抖动、持续 7 秒漂移和 7.501 秒断档正反回归，聚焦 28/28 通过 | 已验证；持续漂移和真实断档仍判 uncertain，资源采样不确定不再覆盖已成立的服务正确性失败 |
| 建群准备通知竞态 | 实际 18/18 系统 Receipt 已 ACK，最后 ACK 后的重试静默窗口与旧 5 秒 deadline 竞争而误报失败 | 两个不同阶段共享未取消的计时器 | 全部 ACK 完成后取消接收 deadline，再独立运行 quiet timer；setup WebSocket 确定性回归通过 | 已通过准备 fixture，缺通知/缺 ACK 仍有界失败 |
| 部分登出无法恢复 | `ik7ps1`、`cyvek2` 中断后第一次 cleanup 因 HTTP 10000 停于 DRAINING，第二次从头重放已退出 Token 返回 10001 | 登出已部分成功，但 `logout-round` 没有把受控 Token 失效视为幂等完成 | Node RED/GREEN 覆盖 10001 继续和 10000 保持失败；重复 cleanup 成功，两个批次最终 BASE_READY、19/19 健康，普通数据 checksum 为 `cce37b62ae581b31fef1f9593d2e201cbf1f2264fabc0a3288c8343ec989c795` | 已验证；真实 10000 仍需等待恢复后重试，不能跳过登出直接清理 |
| base 管理服务健康误判 | restart 后短暂 unhealthy，runner 立即失败，实际服务随后恢复健康 | 第二次等待仍沿用首次 fail-fast 策略 | 首次检查快速触发最多一次 restart；restart 后 120 秒轮询允许过渡 unhealthy；动态健康 fixture RED/GREEN 与真实 base restore 19/19 通过 | 已验证；到 deadline 仍不健康必须失败并保留 RESTORING |
| 私聊准备事实未收敛 | `or1oze` 只在 WAL 中完成 `friend:cohort-0001:0`，Message 两个私聊视角在 20 次 x 500 ms 的 API+DB 轮询内均缺失，尚未进入 k6；自动清理/base restore 通过且 checksum 不变 | Social 在提交后串行发两次同步 Message RPC；第一步异常会阻止第二步，而 Social 事实已经提交，跨服务无法回滚或保证双侧原子性 | RED：Social 测试捕获第二次调用，Message 缺少 prepare 锁/双侧语义。GREEN：Social 只发一次 RPC；Message 按 user pair 100 ms 锁，在一个事务内补齐双方或历史缺失侧；聚焦测试通过。真实 `jij5mm` 2 用户 prepare 的 manifest 精确包含 A:B、B:A 两条 Chat，两个服务零重启，cleanup/base restore 通过 | 根因修复和最小真实 prepare 已验证；仍需完整 20 用户 smoke 验证多关系/群和消息轮 |
| 已知失败被 backlog 不确定性覆盖 | `j4xrkw` 发压端健康且 1000 次目标准确，但旧报告只给 `minute backlog continuously grows`；实际发送 944/1000、ACK 2896/3400、残留 504、P95 1807 ms、P99 2288 ms | classifier 对任何 uncertain 都优先返回，增长 backlog 因此覆盖了已经成立的服务失败 | 新增“已知失败 + 增长 backlog”RED；仅在没有明确服务失败时用 backlog 判 uncertain，聚焦 26/26 通过；按新逻辑重算 `j4xrkw` 为 service_failed，原因保留发送、投递、P95、P99 | 已验证；10 msg/s 是失败档，下一轮向下探测 5 msg/s，不修改阈值 |
| 消息速率被当作连接人数取整 | `dcnawh` 已完成 20 用户准备和连接 stable，但 `SMOKE_MESSAGE_RATE=5` 在 k6 前报 `requested level 5 has no complete cohort` 并自动清理 | `run_k6_round` 无条件以 groupSize 取整 requested；connection 的人数和 message 的 msg/s 共用了同一变量语义 | fake runner 回归要求 `TARGET_RATE=5` 保持原值且 `EFFECTIVE_USERS=20`；只在 connection 分支取整 requested，完整 Harness 通过，`dcnawh` cleanup/base/checksum 通过 | 工具修复已验证；5 msg/s 真实消息轮待重跑 |
| 5 msg/s 仍超过稳定阈值 | `fq0drm` 在 200 秒内准确发送 1000 条，发压样本 42 个且完整；981/1000 发送成功、3229/3400 ACK、残留 171、P95 710 ms、P99 1842 ms、unknown 36，结论 service_failed | 该档仍有 100 ms 锁竞争失败和群 fanout 延迟。unknown 36 恰为 4x9，可能是四条群消息已提交/推送但调用方收到失败；当前没有响应错误码分布，不能把该推断当根因 | 保持所有阈值和原始 summary，不把 5 msg/s 用作 smoke；批次自动 cleanup/base restore | 下一轮下探 2 msg/s x 500 秒；后续为发送失败增加业务错误码/命令类型计数，区分锁失败、RPC 超时和提交后响应失败 |
| 2 msg/s 仍有单条群消息缺口 | `e0fwiy` 在 500 秒内准确调度 1000 条；999/1000 发送成功恰好达到 0.999 下限，但 3391/3400 ACK、残留 9，投递成功率未达 0.999；P95 496 ms、P99 1480 ms、unknown/duplicate/ACK failure 均为 0。按修正规则重算为 service_failed，cleanup 后 BASE_READY、19/19 健康 | `1` 次发送失败与 `9` 个群接收者缺口形态一致，但旧 summary 未记录消息类型和响应码，无法证明是 10010 锁竞争、其他业务错误或响应超时 | 新增 private/group、10010 busy、其他业务错误、响应超时五个固定低基数计数；`send-timeout` 与 `send-busy` 真实 k6 mock fixture 通过，不修改正确性阈值 | 2 msg/s 是失败档；携带新诊断下探 1 msg/s x 1000 秒，若仍失败则依据固定桶定位服务根因，不再从 fanout 数量猜测 |
| 1 msg/s 正确性全通过但接收 P95 失败 | `vf0i6i` 1000 秒完成 1000/1000 发送和 3400/3400 ACK，private/group/10010/其他业务/超时均为 0，unknown/duplicate/残留/积压均为 0；资源 200 个完整样本、Message 0 重启且约 1 GiB/2 GiB。旧全轮接收 P95 1031ms、P99 1342ms，按旧口径 service_failed；Prometheus 类型级 private/group P95 分别约 842/1069ms（Remote Write Trend 数值单位为秒），batch cleanup/base restore 19/19 通过 | 场景把先于发送响应到达的 push 放入缓冲，收到发送响应后才用新的 `Date.now()` 计算传输延迟，混入响应等待时间；当前证据不能量化真实链路延迟占比，也不能排除 private/group 链路本身慢 | 真实 k6 mock 将一条群消息 push 提前、发送 response 延迟 600ms，旧口径 RED 为 P95 607ms 且正确性全部通过；收到 push 即保留原到达时刻后同一 fixture GREEN 判 stable，全部 8 个 mock 模式通过。历史轮无逐条到达时刻，不能离线重算 | 测量错误已验证修复；`vf0i6i` 的旧结论继续保留，不认定 1 msg/s 稳定；待新真实轮在修复后的代码指纹下再证明 P95 与正确性 |
| Dubbo 自动重试制造重复调用 | 修复时间戳后的 `v2i0d5` 在 1 msg/s 下 P95 221ms/P99 1121ms，但 997/1000 发送、3381/3400 ACK、unknown 40；旧诊断误将 3 次错误都归为 other。SkyWalking 在 10:05-10:26 UTC 精确找到 3 条异常 trace：两次 10010（1 private、1 group）和一次 group 10007 | 实时错误帧稳定业务码在 `body.code`，旧 k6 读了恒为 -1 的顶层 code。10007 trace 中首次 Provider 调用成功、耗时 2563ms；Dubbo 在约 3 秒边界自动发起第二次调用并命中已存在 token，另外两条重试与原调用争会话锁 | k6 改读 `body.code` 并增加 repeat 固定桶；生产形态 busy/repeat fixture 通过。`im-dubbo` Consumer 默认显式 `retries=0` 且可配置覆盖；Broker 对直接/包装 `RpcException` 透传稳定 code，未知异常仍为不可用。Dubbo/Broker 聚焦测试通过 | 根因和代码修复已验证；必须重建应用并跑新真实轮，旧 `v2i0d5` 保持 service_failed。资源样本另有一次 9.273 秒断档，generator evidence 为 uncertain，但不覆盖已知服务失败 |
| 关闭重试后无业务失败但群写仍慢 | 新镜像批次 `1xumgo` 的 999 次已执行发送与 3399 次 ACK 全成功，busy/repeat/other/timeout/unknown/duplicate/残留均为 0，Message 0 重启、资源 200/200 完整；一个槽迟到被跳过，因此未达到 1000 次且结论 uncertain。有效消息 P95 1076ms、P99 1290ms | Dubbo 自动重试问题已消失；持续 P95 不是冷启动，Prometheus 从首分钟到末尾 private/group P95 约 0.8-1.1 秒。10 人群 fanout 仍通过两个 `forEach(this::save)` 逐条写 10 个 Inbox 和 10 个 Chat | 群 Inbox 新记录改 batch insert；已有 Inbox 和成员 Chat 改用包含 userId 分片键和旧版本、固定字段集合的 MyBatis-Plus Lambda Update，由 `SqlSessionFactory` 打开参与 Spring 事务的临时 BATCH session 并检查 `BatchResult`，任一影响行数不是 `1` 即回滚。Repository 聚焦和 Message 全量测试通过 | 待新镜像真实验证 SQL 批量行为和 P95；正式窗口改为至少 1005 个计划槽，仍要求实际发送不少于 1000，不降低 200ms 跳过或正确性阈值 |
| batch 写真实验证与延迟边界 | `ugumd7` 第一 cohort 建群成功、9/9 setup ACK 后准备流程退出，自动清理 BASE_READY；独立 `2rsa1e` 的 20 用户准备成功，随后 1 msg/s x 1005 秒消息轮 1005/1005 发送和 3405/3405 ACK 全成功，0 跳过/业务失败/错投/重复/残留，资源 201/201 完整、generator stable。P95 1017ms/P99 1093ms，Prometheus private/group P95 约 973/1019ms，结论 service_failed；批次已清理并恢复 19/19 | 第一批次准备退出不是 batch SQL 错误：建群已提交且 9 个通知均完成 ACK，SkyWalking 无异常；第二批次证明 batch 路由/版本回查在真实 ShardingSphere 下可运行。两种消息类型均约 1 秒，batch 写没有将端到端延迟降至 500ms。单条成功 trace 中 Message private/group Provider 分别只用 30/150ms，group 锁 87ms、Social 8/6ms、commit 2ms；不能把 P95 归因于同步 SQL，首次通知异步执行及 Broker/Gateway 接收链路尚无分段时间证据 | 保留正确性已真实验证的 batch 写，但不称其为性能修复。新增 RED/GREEN 回归：后一个 Chat 冲突时所有领域 rowVersion 保持不变；纯新增 Chat 不得因 pkId 回填而误进入 batch update；Message 全量、仓库 full 门禁通过 | 1 msg/s 尚无 P95 <=500ms 稳定档；停止重复 17 分钟诊断轮，下一步在首次通知的 commit、Executor 开始、Gateway write、客户端 push 处加有界分段计时，再决定优化还是将真实容量上界如实报告 |
| 声明式指标未实际导出 | Message base 的 `/actuator/prometheus` 无 `im.message.*` 指标，`messageNotificationExecutor` 完成任务为 0；插件级独立 Spring 测试应用调用 `@Observed` Bean 后亦无计数器或 duration；新增断言先 RED，确认 `ObservedAspect` Bean 数量为 0 | `ImMetricsAutoConfiguration` 的 `@ConditionalOnBean(MeterRegistry.class)` 在 Boot registry 创建前判定而被跳过；端点 200 与 JVM 指标不证明业务指标切面生效 | 自动配置改为在 Prometheus/Simple registry 创建后运行；插件运行测试实际调用 Bean 并检查计数器和 duration；插件 README/Harness 记录自动化规则 | 回归通过后重新构建运行镜像，再以短批次核对 Message 的真实 `im.message.broker.frame.write` 样本，不把旧 base 的空样本推断为没有通知 |
| Message 通知/Broker 分段实测 | 重建 Message 后，`ctykon` 在 1 msg/s、60 秒测量中 60/60 发送、204/204 ACK，0 业务失败/错投/重复/残留；P95 754ms、P99 1290ms。426 个 `messageNotificationExecutor` 任务全部完成，当前/Prometheus 队列峰值为 0；`im.message.broker.frame.write` 426 次成功，累计 11.315 秒、平均约 26.6ms、最大 193ms。轮次因少于正式最小发送数判 uncertain，批次已清理并恢复 BASE_READY、19/19 | `@Observed` 修复在真实容器生效；首次通知 Executor 没有排队，Message 到 Broker 的同步写入远低于约 0.75 秒端到端 P95。现有指标仍无法区分上行发送响应和下行首个 push | 新增发送 response P95/P99 Trend；无响应继续使用 timeout counter。源码测试先 RED，`response-after-push` 真实 k6 fixture 证明 response P99 可见而 push P95 保持低值 | 用相同短轮采集 response 与 push 两组分位：response 慢则查上行/Provider 返回，response 快而 push 慢才在 Broker/Gateway 下行增加计时；不直接重复正式长轮 |
| 资源采样 Harness 正例窗口过短 | 最终 full 在业务测试前失败，`resource sampler did not emit enough data`；首次延长后又出现首个 0.3 秒间隔为 0.462 秒。同一 fixture 独立和此前 full 可通过 | 亚秒正例在全仓高负载下会放大进程启动与宿主调度误差，偶发不足 4 条或刚超单间隔 1.5 倍；生产 5 秒采样规则未失败 | 将 fixture 改为 1 秒周期、5.5 秒目标存活；仍要求至少 4 条，单间隔 0.5-1.5 倍、中位数 0.75-1.25 倍，比例规则不变 | Harness、clean/full 已重跑通过；不得降低真实资源 evidence 完整性要求 |
| response/push 分段与 base 二次恢复 | `yca3c0` 同样在 1 msg/s、60 秒测量中 60/60 发送、204/204 ACK，0 正确性错误；发送 response P95/P99 为 435/1122ms，首个 push P95/P99 为 1035/1161ms。Message 到 Broker 426 次平均约 18.8ms，Executor 无排队。轮次因样本少判 uncertain。数据清理完成后首次 base restore 因 Social 在 Agent 初始化期 unhealthy 失败并保留 `RESTORING`；受控 `restore` 重建后成功恢复 19/19 | response P95 比 push P95 低约 600ms，主要剩余延迟位于响应之后的首次通知/下行链路，而非 SQL 或 Message 到 Broker 同步调用。Prometheus Remote Write 未保留本轮按消息类型 series，不能据此比较 private/group。Social 无业务异常和重启，日志停留在 SkyWalking Agent 插件加载，属于本地启动收敛慢 | 保留 summary 和恢复状态；使用既有 `restore` 入口完成恢复，不绕过状态机。下一步在 Broker outbound handler 与 Gateway frame write 增加固定名称 duration，不加 user/message 标签；短轮后比较两个服务端阶段与 push 分位 | T8 仍 active；正式 smoke 不重跑，磁盘仅剩约 4.5 GiB，新增计时验证必须继续使用 20 用户短批次并立即清理 |
| 下行排除与单机观测资源争用 | `p6edqw` 60/60、204/204，response/push P95 796/864ms；Message→Broker、Broker→Gateway、Gateway write 各 426 次，平均约 20.6/11.2/2.2ms，最大约 114/83/60ms，排除下行为主要瓶颈。`4soh4u` 的 AppService 指标首次因放在 Dubbo RPC Bean 上未导出，移到真实 Spring AppService 后生效；但该轮仅 53/60 发送、171/204 ACK，资源 evidence incomplete。120 次含 warmup Provider 调用 private/group 平均约 856/1371ms，Message→Broker 平均约 212ms、最大 7.6s | 下行 RPC/Channel 并不解释 700ms 以上延迟。`4soh4u` 期间 OAP 约 98%/1GiB 并高 CPU，Account/Social/MySQL 同样接近内存上限，属于单机全链路资源争用，不能把异常 Provider 平均值归因成 SQL 热点 | Broker/Gateway 两个固定 duration 指标及真实 AOP 测试保留；AppService private/group 固定指标移到实际 Spring 调用路径。清理悬空镜像与 BuildKit cache，宿主可用空间由约 4.5GiB 恢复到 8.5GiB；load-only OAP 提升到 1536MiB，Harness 先 RED 后固定配置 | 先在新 OAP 配额下重跑 20 用户短轮；资源 evidence 完整且正确性恢复后再决定正式 smoke，仍不外推生产容量 |
| OAP 预算修复后短轮恢复 | `ypatov` 在 OAP 1536 MiB 下 60/60 发送、204/204 ACK，0 错投/重复/残留；generator 36/36 样本 stable。response P95/P99 411/542ms，push P95/P99 444/576ms。含 warmup的 private 84 次平均/最大约 199/229ms，group 36 次平均/最大约 267/480ms；Message→Broker 430 次平均约 27.7ms、最大 96ms。批次已清理，base OAP 恢复 1 GiB，19/19 | 提升观测平台 headroom 后正确性和 P95 恢复，说明此前约 1 秒和 `4soh4u` 全链路退化主要来自单机观测资源争用。P99 仍高于 500ms，但批准阈值只要求 P95<=500ms、P99<=2000ms | 保留 OAP load-only 1536 MiB、AppService/Broker/Gateway 分段指标和 Harness 约束；不继续无证据优化 SQL | 运行最终工作树的正式 20 用户 smoke，要求 1000 条最小样本、fresh evidence、cleanup passed、ordinaryDataUnchanged=true |
| Node 合约文件并行时序抖动 | 正式 smoke 前置 `run_load_test` 两次在 205 项中分别偶发 1/2 个异步测试失败；立即单独重跑 205/205，通过时总耗时约 5-6 秒，失败时约 9 秒，未得到稳定同一失败用例 | 大量 timer、HTTP/WS 和 fsync fixture 默认按文件并行，在连续容器重建和高系统负载下竞争宿主调度；单项断言和超时没有稳定失败证据 | npm test 固定 `--test-concurrency=1`，不修改任何测试断言或超时；重新运行 load-test 门禁后才允许正式 smoke | 仅解决 Harness 确定性，不改变真实压测调度或容量阈值 |
| 最终 10 msg/s smoke 是容量失败档 | `9of5ub` 连接轮 stable；消息轮准确发送 1000 条但仅 981 成功、3229/3400 ACK、残留 171、1 次重复，19 次失败全部为群聊 other business；response P95/P99 1072/1760ms，push P95/P99 1514/2444ms，generator 22/22 样本 stable。cleanup passed、ordinaryDataUnchanged、worktree/scope stable，base 恢复 19/19 | OAP 预算修复没有把 10 msg/s 变成稳定档；它是可信的第一失败边界，继续要求 smoke 在该速率通过会把容量结论误当工具门禁 | smoke 改为 1 msg/s x 1000 秒，仍保留 1000 样本、10 秒 drain 和全部阈值；10 msg/s 结果保留给 T9 报告和边界复测 | 运行 1 msg/s 最终 smoke；不得降低 send/ACK/P95/P99 阈值 |
| 1 msg/s smoke 被 round timeout 截断 | `o0kuqo` 在 900 秒被截断；当前指纹的 `t8trxv` 在 1005 秒窗口内仅调度 797 次、调度延迟 P95 约 249 秒，并在 1200 秒被 runner 终止。两轮均自动清理并恢复 19/19，`t8trxv` ordinary checksum 不变 | 服务退化时递归 timer 调度漂移会叠加到测量窗口；1200 秒不足以覆盖 1005 秒测量、10 秒 drain 和已观测约 249 秒漂移 | smoke 专用 timeout 调到 1500 秒并写入 resolved config/Harness 断言；默认正式探索 timeout 不变 | 重跑最终 smoke；被截断轮没有合法 message round，不作为服务容量结论 |
| 1500 秒 smoke 形成合法失败结论 | `hpxkco` 完成 1005/1005 发送和 3405/3405 ACK，调度 P95 5ms、无业务失败/跳槽/重复/残留，generator 201/201 stable；response P95/P99 949.8/1067.92ms，push P95/P99 966.8/1117.96ms | D65 当前实现的正确性与临时 BATCH session 在真实 Spring/ShardingSphere 事务中通过；本机消息链路仍未满足 P95 <=500ms，属于服务容量结论而非 Harness 失败 | runner 在 1500 秒内正常完成，cleanup、ordinary checksum、19/19 base restore 和 scope-stable evidence 均通过；资源样本用于配置矩阵 | 随后配置报告和 README 修改 scope；保留本轮事实，但需在最终文档指纹下刷新 smoke |
| 准备器误拒绝合法 Receipt 重投 | `g5e5e5` 准备阶段已持久化 9 个唯一 group receipt intent，随后同一 receiver 的同一 receipt 重投触发 `duplicate group notification`；批次自动清理、ordinary data 不变、base 恢复 19/19 | Message 通知是 at-least-once，ACK 与首次/延迟重投存在竞争；准备器把所有重复一律当成不同业务事实 | 同 receipt 重投幂等重发 ACK，不重复写 intent；若已完成则不重复写 completed；每次重投取消并重新开始 quiet horizon。同 receiver 的不同 receipt 仍失败。确定性测试 20/20 | 重跑最终 smoke；合法重投不再阻断，持续重投仍无法通过完整 quiet horizon |
| 1 msg/s 长轮末尾 MySQL 触顶 | `z138pu` 完整 1000 条轮为 998/1000 发送、3398/3400 ACK、unknown 8、duplicate 5、ACK failure 4、P95 964ms；generator evidence 因 199 个样本 incomplete，但服务失败事实成立。末尾 MySQL 509.3/512 MiB，Message 1.12/2 GiB、OAP 0.99/1.5 GiB、宿主可用 7.7 GiB，核心容器均零重启 | 短轮 `ypatov` 可稳定通过，1000 秒后数据库达到 cgroup 上限并伴随全链路延迟/响应错误；当前证据支持本地数据库预算不足，而不是继续改业务阈值 | load-only MySQL 调至 768 MiB；profile 切换显式重建 MySQL，base 恢复 512 MiB；Compose Harness 先 RED 后固定。批次 cleanup/checksum/base 恢复均通过 | 在新 MySQL 预算下重跑最终 smoke；若仍失败则接受本机 1 msg/s 无稳定长轮并进入报告，不继续无界调参 |
| 最终本机 1 msg/s 无稳定长轮 | `8y6059` 在 MySQL 768 MiB、OAP 1536 MiB 下连接 stable；消息 1000 条、generator 200/200 stable，但仅 998/1000 发送、3390/3400 ACK、unknown 4、duplicate 1、残留 10，P95/P99 756/2124ms，结论 service_failed。cleanup passed、ordinaryDataUnchanged、worktree/scope stable，base 19/19 | 增加有证据的本地资源预算后仍未达正确性和延迟阈值；继续调资源或阈值会掩盖真实容量结论。当前本机没有满足标准的 20 用户消息稳定档 | smoke 顶层状态改为验证 workflow 而非强制容量 stable：connection 必须 stable，message 接受 stable/service_failed，uncertain 和工具/清理错误继续失败；Harness 增加 service_failed 正例 | T8 以可信 service_failed evidence 完成；T9 不再升压消息，报告“<1 msg/s 或无已验证稳定档”，连接容量仍按计划探索 |
| 1000 秒计划被单槽跳过降为 uncertain | `tonptj` 计划 1000 条但 1 个槽超过 200ms 后跳过，实际 999 次；资源 200 个样本但完整性不足，decision uncertain，批次清理/checksum/base 恢复正常 | 最小样本要求为 1000，计划值与最低有效值完全相同，没有为合法跳槽保留恢复槽 | smoke 改为 1005 秒，仍要求实际 sends>=1000；不修改 200ms late slot、1% skip、发送/ACK/P95/P99 阈值 | 重跑当前 fingerprint smoke；若形成 service_failed/stable 即完成 T8，uncertain 继续失败 |
| 正式连接 route cleanup 误用全部准备用户 | `3oykv8` 首轮只连接 50 用户，但 route evidence 写 `userCount=200`；实际 remaining=0 且 complete=true，classifier 因 userCount 与 effective=50 不一致判 uncertain | route-cleanup 从完整登录凭据读取全部 200 用户，runner 未传本轮 effective users | 工具新增有界 `--user-count` 并只查询凭据前缀，runner 显式传 effective；测试覆盖 2 个凭据只查 1 个和超界拒绝。修复后 50/100/200 及 100/200 复测均 stable | 连接下界 `>=200`；磁盘约束下不测更高档，不将旧 uncertain 当服务失败 |
| JDK 21 与 SkyWalking Agent 冷启动死锁 | 最终 smoke 前一次 base restore 中 Account 超过 6 分钟保持 unhealthy；SIGQUIT 明确显示 main 持有 `JarFile` 等待 AppClassLoader class lock，`SkywalkingAgent-4-GRPCChannelManager-0` 反向持锁并等待同一 `JarFile` | SkyWalking GRPC 管理线程在 JVM 启动早期首次加载 `jdk.internal.misc.ThreadTracker`，与 Spring Boot 主线程读取应用 JAR 形成锁顺序反转；既有 `-XX:-UseContainerSupport` 只规避另一条 JVM metrics/cgroup 路径 | 本地镜像在 SkyWalking Agent 前加载最小 premain Agent，提前初始化 `ThreadTracker`；Dockerfile/Harness/README/设计/Harness Guide 同步。Account 连续五次冷启动均约 30-31 秒健康，最终 `u7apfw` 完整恢复 19/19 | 修复仅用于本地 Compose 镜像；生产部署仍应跟踪 SkyWalking/JDK 上游修复并独立验证，不据此声明所有 Agent/JDK 组合安全 |
| `BASE_READY` 后敏感文件不可恢复 | cleanup 已完成七个 checkpoint 并恢复 19/19 后，若进程在删除凭据/数据库目标前中断，全局状态已是 `BASE_READY`；旧 runner 拒绝 `cleanup --resume`，cleanup Harness 给出的修复命令无法执行 | 状态机把服务恢复完成与最终敏感文件销毁放在同一无 checkpoint 尾段，缺少 `BASE_READY` 的受限收尾分支 | RED fixture 得到 `cleanup cannot resume from state BASE_READY`；runner 增加 6 位 batch ID 校验，只有 manifest SHA-256、batch 绑定和七个 checkpoint 完整匹配时才允许删除固定敏感文件。9 个历史批次通过该入口收尾，`verify.sh cleanup` 通过 | 实现已完成；变更影响 load-test scope，T9 正在刷新 capacity/smoke |
| cleanup runner 修复后正式证据刷新 | `n7k2p4` 的 50/100/200/100/200 五轮连接均 stable；`n8sn14` connection stable，消息 1005/1005 发送、3405/3405 ACK、无业务失败/错投/重复/残留，receive P95/P99 966/1082.96ms，因 P95 超 500ms 判 service_failed | cleanup 修复只改变恢复安全边界，未改变容量阈值；新 scope 再次证明连接下界和消息延迟失败结论 | 两批次 cleanup passed、ordinaryDataUnchanged、worktree/scope stable；base 19/19、`BASE_READY`，凭据和数据库目标均删除 | T9 passing；进入 T10 最终门禁与独立 quality，不再重复正式压测 |

磁盘风险：最终 smoke 前 `df -Pk` 显示宿主数据卷使用率 98%、可用约 10.5 GiB；20 用户批次可完成安全清理，但 T9 的 3200 用户准备前必须核对真实可用空间和增长率，空间不足时只报告实际可准备规模并清理，不虚构容量边界。

## 恢复状态

- 当前任务：无；T10 最终分层门禁、直接 Review 与关闭审计已完成；base 最近状态 19/19、`BASE_READY`。
- 已完成：T1-T7 passing；T8 的 evidence/verify/report/handoff/cleanup/affected 已接入。消息时间戳、生产形态 busy/repeat、Dubbo retries=0 和 Broker RpcException 透传聚焦回归通过；SkyWalking 已定位 `v2i0d5` 的三条失败调用。
- 待验证：最终 `affected/load-test/quick/startup/e2e/clean/full/report/handoff/quality/cleanup/diff` 和独立 quality Accept。历史批次敏感恢复文件已受控收尾，cleanup Harness 通过。
- 下一步：无；后续容量结论以正式报告和 evidence 为准，生产拓扑仍需重新压测。
- 不要修改：消息公共 Frame/notification 协议、日常 `sql-show=true` 默认、生产数据删除接口、普通用户/管理数据；不要在 T8 smoke 前执行正式 3200 用户准备。

## 回滚与残余风险

- 回滚：移除 `im-load-test` 模块、runner/override/dashboard/Harness 扩展，并恢复 Prometheus command；Store confirm 增强是独立幂等修复，可单独保留或回滚。已创建批次必须先按 journal 清理，不能仅删除代码。
- 未验证路径：生产集群、独立发压机、跨 Broker/Gateway 水平扩容、长时间 soak、基础设施 exporter 和故障注入。
- 已接受但需跟踪的风险：单机 k6 与服务共享资源，只能形成本机容量基线；3200 用户准备和每轮 Bcrypt 登录可能耗时较长；Message 当前同步 fanout-on-write 和非 Outbox Receipt 边界可能成为已知拐点来源，但本任务只测量不重构。
