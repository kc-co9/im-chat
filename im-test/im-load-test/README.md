# IM 负载测试模块

本模块保存 IM 核心链路容量测试的纯配置、correlation、实时协议和单轮结论契约，并为后续测试侧数据准备与清理工具提供 Maven test classpath。模块没有 `src/main` 生产代码，也不向其他模块发布运行能力。

## 当前验证

纯 Node 契约不依赖 Docker、Compose、运行中的服务或 k6：

```bash
npm --prefix im-test/im-load-test ci
node --test im-test/im-load-test/tests/*.test.mjs
```

Maven 从仓库根目录验证模块及其 reactor 依赖：

```bash
mvn -q -pl im-test/im-load-test -am test
```

Maven `test` 和仓库日常 `quick/full` 验证不会启动 Compose、执行 k6 或产生真实负载。确定性聚合入口为：

```bash
./scripts/verify.sh load-test
```

真实 20 用户 smoke 只能显式运行 `./scripts/verify.sh load-test-smoke`。它要求 1 GiB 应用上限的 base full 19 个服务已健康，随后暂停 IAM/Audit/Admin/Monitor，以六个核心应用和九个基础设施/观测服务执行连接与消息最短有效窗口；Message 在 load profile 使用 2 GiB，SkyWalking OAP 使用 1536 MiB，MySQL 使用 768 MiB。连接测量 20 秒，消息默认按 1 msg/s 测量 1005 秒并排空 10 秒，允许最多五个迟到槽仍取得至少 1000 次实际发送；已确认失败的 10/50 msg/s 留给容量边界报告，不再作为工具可用性的前置条件。轮次结束要求核心容器健康且未自动重启，再清理批次、恢复完整 base，并核对前后普通数据 checksum。结果写入 `.harness/verifications/load-test-smoke.json` 与 `.harness/load-test/<batch>/smoke-summary.json`。

当前脱敏容量结论见 [`docs/load-test-reports/2026-09-16-local-capacity-baseline.md`](../../docs/load-test-reports/2026-09-16-local-capacity-baseline.md)；机器可读摘要为同目录 JSON。报告不包含 Token、密码或原始凭据。

连接场景的确定性 fixture 会启动随机 loopback HTTP/WebSocket mock，并真实执行 k6 2.2.0：

```bash
bash scripts/test-load-test-k6-scenarios.sh connection
bash scripts/test-load-test-k6-scenarios.sh message
```

连接 fixture 覆盖 20/50 用户稳定轮次，以及握手失败、测量期异常断开、路由清理超时和发压调度迟到。消息 fixture 覆盖 70/30 调度、精确 fanout、重复/漏投/错投、ACK 失败、发送超时、drain 和 generator saturation 三态。两者只验证 k6 场景、认证屏障、计数和 mock 边界分类，不证明真实 Gateway/Broker/Message 容量；真实服务证据由显式 smoke 和正式容量流程生成。

真实连接轮会在受控关闭后按本轮全部用户查询 Broker 管理接口，确认路由清空。`round-report.mjs` 将 k6 summary、资源样本和连接路由证据合成为 `stable/service_failed/uncertain`；smoke 只有连接和消息两轮都为 `stable` 才通过。服务阈值失败与 runner/清理失败分别记录，不能互相覆盖。

准备阶段的群系统通知先在有界时间内完成接收和 ACK；全部 ACK 完成后才开始独立的重试静默窗口。静默窗口用于发现重复通知，不占用前一阶段的 deadline；缺少通知或 ACK 仍会在 deadline 到期时停止准备。

资源采样以固定 5 秒周期调度，`docker stats` 等采集耗时计入周期，采集结束后只等待剩余时间。若 k6 在采集中结束，该次样本不会以 unknown 追加；已有完整样本仍由 round-report 按时间间隔和字段完整性判定。

消息轮在 drain 后先正常关闭 WebSocket；2 秒后仍在线的 socket 会计入 `forcedStops` 并通过 k6 abort 有界结束。runner 只在合法 message summary 明确包含正数 `forcedStops` 时接受退出码 108，随后仍按发送、投递、ACK、积压和资源证据判定为服务失败或不确定，不把强制结束包装成成功。

## 安全边界

默认 HTTP、WebSocket 和 Prometheus 目标只允许 `localhost`、`127.0.0.1` 或 `::1`。非本地发压必须显式解锁；数据库与 Redis 清理目标始终只允许本机。HTTP 客户端必须拒绝重定向，避免绕过目标检查。

仓库配置只保存非敏感默认值。Token、口令、Cookie、数据库密码和批次凭据不得写入 `defaults.json`、命令行记录、测试输出或提交文件；后续运行器应通过权限受限的临时文件传递敏感值。

数据准备命令通过环境变量 `IM_LOAD_TEST_DATABASE_TARGETS_FILE` 接收数据库目标文件路径。该文件必须是权限 `0600` 的 JSON 文件，并且只包含 `account`、`social`、`message` 三个带凭据的本地 MySQL URL，分别指向 `im_chat_account`、`im_chat_social`、`im_chat_message`。准备器在第一个业务写请求前连接三个 owner，核对实际 Schema 及其 host/port 与公开的 `targets.cleanup.mysql` 一致。运行编排负责在批次临时目录创建和删除该文件；URL、凭据和文件内容不得进入 argv、日志、journal 或公开 manifest。

示例仅描述文件结构，实际值由本地运行编排写入：

```json
{
  "account": "mysql://<user>:<password>@127.0.0.1:3306/im_chat_account",
  "social": "mysql://<user>:<password>@127.0.0.1:3306/im_chat_social",
  "message": "mysql://<user>:<password>@127.0.0.1:3306/im_chat_message"
}
```

`prepare` 的 batch ID 必须是 1 到 6 位小写 base36 字符；未指定时自动生成 6 位值。该长度与 3 位 base36 用户/cohort 序号共同保证 3200 用户、最小 `groupSize=2` 时生成的用户名、邮箱和群名不超过生产列宽。命令会拒绝未知、重复、缺值参数，以及同时使用 `--batch` 和 `--resume`，防止参数拼写错误退回 3200 用户默认值。

宿主 k6 固定使用 `v2.2.0`，安装位置为 `$HOME/.local/bin/k6`。k6 二进制和下载压缩包不属于仓库文件，不得提交。

Harness cleanup 发现压测凭据、数据库目标、runner lock 或非 `BASE_READY` 状态时只报告恢复命令，不直接删除。按状态使用 `./scripts/load_test.sh cleanup --resume <batch-id>` 或 `./scripts/load_test.sh restore`；round、smoke 和 capacity 报告会保留用于复查。
