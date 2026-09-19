# IM Chat 本机容量压测报告

## 结论

本报告记录 2026-09-16 在单机 Docker Desktop、12 GiB Docker 配额下的脱敏基线结果。连接链路在 200 用户档及复测中稳定，结论为 **至少 200 个稳定 WebSocket 连接**；受宿主磁盘仅约 3.9 GiB 可用限制，未继续准备 400/800/1600/3200 用户，因此不能推导更高上限或生产容量。

消息链路在 20 用户、1 msg/s、1005 秒窗口下，两轮都完成 1005/1005 发送和 3405/3405 ACK，但延迟结论不一致：`hpxkco` 的 response/push P95 为 949.8/966.8ms，判 service_failed；`2d6cln` 为 260.6/342.8ms，判 stable。两轮调度 P95 均为 5ms、generator 均为 201/201 stable，且无业务失败、错投、重复或残留，因此当前只能报告 **1 msg/s 是运行间波动边界，尚无可重复稳定档**。10 msg/s 另有明确失败证据：981/1000 发送成功、3229/3400 ACK、P95 1514ms，作为第一失败边界保留。

## 环境与方法

| 项目 | 值 |
|---|---|
| 平台 | Apple M1 Pro，10 logical CPU |
| Docker 配额 | 约 12 GiB |
| Base | 19/19 服务，最终恢复 `BASE_READY` |
| Load profile | 15 服务，暂停 IAM/Audit/Admin/Monitor |
| Message load 上限 | 2 GiB |
| SkyWalking OAP load 上限 | 1536 MiB；base 恢复 1 GiB |
| MySQL load 上限 | 768 MiB；base 恢复 512 MiB |
| 连接阈值 | 成功率和在线率 >= 99.5%，异常断开 <= 0.5% |
| 消息阈值 | 发送/ACK >= 99.9%，P95 <= 500ms，P99 <= 2000ms，unknown=0 |
| Smoke evidence | 以 `.harness/verifications/load-test-smoke.json` 当前 fresh 记录为准 |
| Capacity evidence | 以 `.harness/verifications/load-test-capacity.json` 当前 fresh 记录为准 |

## 结果

### 连接

| 档位 | 结果 | 成功率 | 在线率 | 异常断开 | 调度 P95 |
|---:|---|---:|---:|---:|---:|
| 50 | stable | 100% | 100% | 0 | 3ms |
| 100 | stable | 100% | 100% | 0 | 4.05ms |
| 200 | stable | 100% | 100% | 0 | 6ms |
| 100 复测 | stable | 100% | 100% | 0 | 见 round evidence |
| 200 复测 | stable | 100% | 100% | 0 | 见 round evidence |

### 消息

| 项目 | `hpxkco` | `2d6cln` |
|---|---:|---:|
| 计划窗口 | 1 msg/s x 1005 秒 | 1 msg/s x 1005 秒 |
| 发送/成功 | 1005/1005 | 1005/1005 |
| ACK 成功/预期 | 3405/3405 | 3405/3405 |
| unknown/duplicate/ACK failure/residual | 0/0/0/0 | 0/0/0/0 |
| response P95/P99 | 949.8/1067.92ms | 260.6/721.48ms |
| push P95/P99 | 966.8/1117.96ms | 342.8/775.92ms |
| Generator evidence | 201/201，stable | 201/201，stable |
| 结论 | service_failed，仅 P95 超阈值 | stable |

10 msg/s 第一失败边界：1000 发送中 981 成功，3229/3400 ACK，19 次群聊其他业务失败，P95/P99 为 1514/2444ms；generator stable。该结果不是 Harness 失败，而是服务容量失败证据。

## 资源与归因

分段指标显示 Message 到 Broker、Broker 到 Gateway、Gateway Channel write 均为毫秒级，不能解释 600ms 级端到端延迟。短轮中将 SkyWalking OAP 从 1 GiB 提升到 1536 MiB、MySQL 从 512 MiB 提升到 768 MiB 后正确性恢复；长轮仍存在 P95 超阈值，说明单机观测和数据库资源预算是重要影响因素，但本报告不把它外推为生产 SQL 根因。

所有批次均执行安全清理；最终 smoke 的普通数据 checksum 前后相同，工作树和 load-test scope fingerprint 稳定，base 恢复 19/19。

## 部署角色配置建议

下表所有资源均为**每副本**口径。“当前负载已验证”表示该分配完成了本报告的 20 用户、1 msg/s 本机 smoke，不是启动下限或生产最低配置；CPU 未设置 cgroup 限额，仅列出 `hpxkco` 与 `2d6cln` 合计 402 个完整样本的 P95/峰值。推荐内存取 `max(已验证分配, 峰值 x 1.30)` 后向上取整到 256 MiB；推荐 CPU 取 P95 x 1.50 后向上取整到 0.5 core 且至少 1 core。

本轮没有逐角色磁盘 footprint 或横向扩容证据，因此磁盘全部是**未验证工程起始值**：应用与无本地数据的运行组件用于镜像、临时文件和受控日志；MySQL、Redis、Nacos、Prometheus 的持久化空间必须按目标保留期、增长率和备份策略重新计算。实测副本均为 1；HA 副本只是可用性拓扑假设，没有经过本次容量验证。

| 部署角色 | 当前负载已验证 CPU P95/峰值 | 当前负载已验证内存 峰值/限额 | 推荐规划起点 CPU/内存 | 磁盘起点（未验证） | 副本 实测/HA 假设 | 说明 |
|---|---:|---:|---:|---:|---:|---|
| HTTP Gateway | 0.070/0.539 core | 652/1024 MiB | 1 core / 1024 MiB | 10 GiB | 1/2 | 无状态入口；生产需按 HTTP 请求率复测 |
| WebSocket Gateway | 0.114/0.845 core | 593/1024 MiB | 1 core / 1024 MiB | 10 GiB | 1/2 | 长连接入口；副本扩展需验证连接分布与清理 |
| Broker | 0.147/0.619 core | 687/1024 MiB | 1 core / 1024 MiB | 10 GiB | 1/2 | 路由与 Gateway 写入；未验证多 Broker Gossip 容量 |
| Account | 0.094/1.384 core | 919/1024 MiB | 1 core / 1280 MiB | 10 GiB | 1/2 | 内存按峰值 30% 裕量上调 |
| Social | 0.185/1.521 core | 873/1024 MiB | 1 core / 1280 MiB | 10 GiB | 1/2 | 内存按峰值 30% 裕量上调 |
| Message | 0.435/3.365 core | 1142/2048 MiB | 1 core / 2048 MiB | 10 GiB | 1/2 | 保留已验证 2 GiB 分配；1 msg/s 延迟有运行间波动 |
| MySQL | 0.081/0.501 core | 514/768 MiB | 1 core / 768 MiB | 50 GiB | 1/3 | 磁盘、IOPS、复制与备份未验证；HA 数为拓扑假设 |
| Redis | 0.025/0.080 core | 10/256 MiB | 1 core / 256 MiB | 10 GiB | 1/3 | 数据集很小；生产内存必须按 key 数量和持久化策略重算 |
| Nacos | 0.044/0.270 core | 481/768 MiB | 1 core / 768 MiB | 10 GiB | 1/3 | 当前为 standalone；三副本仅是生产可用性起点 |
| Prometheus | 0.006/0.077 core | 105/256 MiB | 1 core / 256 MiB | 20 GiB | 1/2 | 本轮保留期短；磁盘必须按 series、采样周期和保留期重算 |
| SkyWalking OAP | 0.462/2.313 core | 1080/1536 MiB | 1 core / 1536 MiB | 10 GiB | 1/2 | 1 GiB 曾参与单机资源争用；遥测存储由外部 BanyanDB 承担 |

这些推荐值是目标环境容量测试的起点，不是生产容量承诺。当前报告没有覆盖独立发压机、生产 TLS、跨节点网络、数据库复制、Redis/Nacos 集群、Prometheus HA、OAP/BanyanDB 分离部署或故障切换；采用前必须在目标拓扑重新执行容量与稳定性验证。

## 范围与风险

- `>=200` 是本机连接稳定下界，不是生产容量或最大连接数。
- 1 msg/s 同配置轮次分别得到 stable 与 service_failed，不能报告可重复稳定消息吞吐上限。
- 宿主磁盘约 99% 使用率，BanyanDB 观测卷占用显著；更高用户档被安全跳过。
- 报告只引用脱敏 summary/evidence，不包含 Token、密码或凭据文件内容。

## 证据

- Smoke evidence：`.harness/verifications/load-test-smoke.json`。
- Capacity evidence：`.harness/verifications/load-test-capacity.json`。
- 当前配置矩阵资源证据：`.harness/load-test/hpxkco/message-1-1-resources.jsonl` 与 `.harness/load-test/2d6cln/message-1-1-resources.jsonl`（合计 402/402 完整样本）。
- 计划与问题台账：[2026-09-14-im-load-test.md](../exec-plans/completed/2026-09-14-im-load-test.md)。
