# 本地运行编排

`deploy/local/compose.yml` 提供两种 profile：

- `infra`：MySQL、Redis、Nacos、Kafka、Prometheus、Grafana、BanyanDB、SkyWalking OAP/Horizon UI。
- `full`：包含全部 infra，并构建、启动十个 IM Chat 应用镜像。

## 前置条件

- JDK 21、Maven、Docker Compose。
- 所有服务使用 bridge 网络，不要求 Docker Desktop host networking。full 同时运行 10 个 Java Agent 应用、SkyWalking、监控和基础设施，Docker Desktop 至少分配 12 GiB 内存；8 GiB 环境会在并发启动峰值触发容器 `exit=137`。
- 首次启动需要访问镜像 registry。当前固定镜像包括 MySQL 8.0.39、Redis 7.2.0、Nacos 3.0.2、Kafka 4.3.1、Prometheus 3.5.0、Grafana 12.1.1、BanyanDB 0.11.0、SkyWalking OAP 11.0.0、Horizon UI 1.0.0、Java Agent 9.7.0 和 Eclipse Temurin JRE `21.0.12_8-jre-jammy`。
- 所有 published port 只绑定 `127.0.0.1`。MySQL、Redis、Nacos、Grafana 和 SkyWalking 的本地弱凭据不会监听局域网网卡；需要跨主机访问时应显式增加受控代理或防火墙规则。

## 启停

```bash
./scripts/local_up.sh
./scripts/local_up.sh full
./scripts/local_down.sh
./scripts/local_down.sh --volumes
```

默认 `local_up.sh` 只启动 infra。full 模式先执行端口预检和 `mvn -q -DskipTests clean package`，再通过通用 `app.Dockerfile` 构建 `im-chat/<artifactId>:local`。Dockerfile 专用 ignore 只把十个 `*-exec.jar` 发送给 builder，JAR 路径不绑定项目版本。容器在内部监听 `0.0.0.0`，Nacos/Dubbo 自动注册容器 IP，依赖通过 Compose 服务名访问；所有宿主映射仍只绑定 loopback。两种模式都最多等待 600 秒直至服务健康，可通过 `LOCAL_WAIT_TIMEOUT_SECONDS` 调整；启动失败返回非零。切换到 infra 会移除 full-only 应用和 `prometheus-full`，并等待应用端口及 Nacos/Dubbo 实例释放，等待上限默认 30 秒，可通过 `LOCAL_SWITCH_TIMEOUT_SECONDS` 调整；切换到 full 会移除 `prometheus-infra`。启动完成后脚本验证 profile 的精确运行服务集合。默认 down 保留命名卷；`--volumes` 会删除全部本地持久化数据。

full 的统一 JVM 参数包含 `-XX:-UseContainerSupport`。SkyWalking Java Agent 9.7.0 的 JVM 指标线程与 JDK 21 启动类加载在容器指标初始化路径上可能形成死锁；关闭 JVM 容器感知可避开该路径。应用堆和 metaspace 仍由显式 JVM 参数约束，容器总内存仍由 `mem_limit` 约束。该设置仅属于本地 Compose，不应直接复制为生产参数。

Audit、Admin、Monitor 在 full 中把 IAM HTTP read timeout 覆盖为 `10s`，用于容忍本机十个应用并发冷启动时的资源竞争；SDK 的生产默认值仍为 `2s`。

只在 IDE 启动单个服务时保持 infra 运行即可，服务继续使用 `localhost` 的标准端口。infra 的 Prometheus 在 Docker Desktop 通过 `host.docker.internal` 抓取 IDE 服务；full 的 Prometheus 直接按 Compose 服务名抓取十个 management port。Grafana 始终通过网络别名 `prometheus:9090` 读取数据。full 中 Redis、MySQL、Nacos、Kafka、IAM 和 SkyWalking OAP 都使用内部服务名，生产 TLS 与外部地址仍由 Nacos 或部署环境决定。

## 控制台

| 能力 | 地址 | 本地凭据 |
|---|---|---|
| Nacos | `http://localhost:18048` | standalone 未启用认证 |
| Prometheus | `http://localhost:9090` | 无 |
| Grafana | `http://localhost:3000` | `admin` / `admin` |
| BanyanDB | `http://localhost:17913` | 无 |
| SkyWalking Horizon UI | `http://localhost:18050` | `admin` / `e2e-passw0rd` |

Grafana 自动加载 Prometheus datasource 和 `IM Chat overview` dashboard。SkyWalking OAP 11 将数据写入 BanyanDB 0.11；Compose 服务名为 `skywalking-banyandb`，实际容器名带 `im-chat-local-` 项目前缀，stream、measure、trace、property 和 schema-server 根目录均位于 `banyan-data` 命名卷。Horizon UI 通过 OAP 的 Query/Admin API 读取和管理观测数据，默认账号仅用于本地开发。SkyWalking Agent 由应用镜像挂载，`SW_AGENT_NAME` 使用 Maven artifactId；Agent 二进制不提交到仓库。

BanyanDB standalone 显式使用 `127.0.0.1` 作为节点注册地址。Docker Desktop 的容器 hostname 虽然可以解析并建立 TCP 连接，但 BanyanDB 0.11 的 property schema 健康 RPC 可能持续超时，导致主 gRPC 端口 `17912` 无法启动；loopback 地址使单节点内部注册稳定，不改变 OAP 通过 Compose 服务名 `skywalking-banyandb:17912` 访问的方式。

## 数据初始化

`mysql/init/ddl` 包含五个 Server `sql/ddl.sql` 的本地快照，`mysql/init/dml` 包含 IAM `sql/dml.sql` 的本地开发种子。MySQL 官方 entrypoint 不递归执行子目录，因此根目录 `00-initialize.sql` 使用客户端原生 `SOURCE` 固定先执行全部 DDL、再执行 DML，避免依赖宿主脚本执行权限。IAM 种子预置四个应用、三个 web client 和三个权限目录机器客户端，供本地浏览器登录及 Admin、Audit、Monitor 权限同步；它不用于生产。Message DDL 直接创建 `_0` 到 `_7` 的物理分表。SQL 改变后必须同步所属快照；现有命名卷不会自动重放 init SQL。
