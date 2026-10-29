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

full 的统一 JVM 参数包含 `-XX:-UseContainerSupport`。SkyWalking Java Agent 9.7.0 的 JVM 指标线程与 JDK 21 启动类加载在容器指标初始化路径上可能形成死锁；关闭 JVM 容器感知可避开该路径。JDK 21 的 `ThreadTracker` 首次初始化仍可能与 Agent 的 gRPC 后台线程形成 `JarFile`/`AppClassLoader` 锁顺序反转，因此应用镜像先运行仓库内的微型 premain Agent 预加载该 JDK 类，再启动 SkyWalking Agent。预加载 Agent 不修改字节码、不采集数据。由于 JVM 不再按容器预算推导 native memory，Compose 同时固定 256 MiB heap、512 KiB 线程栈、192 MiB metaspace、128 MiB direct memory 和 128 MiB code cache，应用容器总内存由 1 GiB `mem_limit` 约束。内核日志已确认 768 MiB 会在普通启动和 20 用户准备阶段触发应用 cgroup OOM；1 GiB 依赖 Docker Desktop 至少 12 GiB 的总配额。该设置仅属于本地 Compose，不应直接复制为生产参数。

Audit、Admin、Ops 在 full 中把 IAM HTTP read timeout 覆盖为 `10s`，用于容忍本机十个应用并发冷启动时的资源竞争；SDK 的生产默认值仍为 `2s`。

本地 Broker 与 Ops 使用相同的公开开发管理 token，使运行控制台可执行 dry-run 和受控 operation。跨主机或生产部署必须分别通过 `IM_BROKER_MANAGEMENT_WRITE_TOKEN` 与 `IM_OPS_BROKER_MANAGEMENT_TOKEN` 注入同一短期密钥，并用运维网络、mTLS 或 OAuth2 Client Credentials 进一步保护；不要复用仓库默认值。

只在 IDE 启动单个服务时保持 infra 运行即可，服务继续使用 `localhost` 的标准端口。infra 的 Prometheus 在 Docker Desktop 通过 `host.docker.internal` 抓取 IDE 服务；full 的 Prometheus 直接按 Compose 服务名抓取十个 management port。Grafana 始终通过网络别名 `prometheus:9090` 读取数据。full 中 Redis、MySQL、Nacos、Kafka、IAM 和 SkyWalking OAP 都使用内部服务名，生产 TLS 与外部地址仍由 Nacos 或部署环境决定。

## 路由高可用拓扑

`compose.routing-ha.yml` 只由 `./scripts/verify.sh routing-ha` 使用。它启动隔离的 Nacos、Redis、三个 Broker 和两个 Gateway，不复用 `compose.yml` 的 full/infra 服务，也不占用普通本地数据卷。三个 Broker 使用稳定 `broker-ha-1` 到 `broker-ha-3` 身份、独立 Raft volume 和同一组三 voter 地址；两个 Gateway 通过专用 Nacos 发现 Broker。

runner 使用唯一 Compose project，依次验证五实例健康、三份 `BrokerClusterState` 一致、Gateway-A 停止时 Gateway-B 可用，以及三个 Broker 分别停止和恢复后的多数派服务与状态收敛。HTTP 探针采用有界轮询。失败诊断写入 `.harness/routing-ha/diagnostics/compose.log`，随后仍执行 `down --volumes --remove-orphans`；不要手工使用这份 Compose 作为长期开发环境。

复现入口固定为：

```bash
./scripts/test-routing-ha-harness.sh
./scripts/verify.sh routing-ha
```

宿主映射为 Broker management `19121` 到 `19123`、Gateway WebSocket `18111`/`18211`、Gateway management `19111`/`19211`，均只绑定 loopback。运行时需要约 4 GiB 可用 Docker 内存；这组限制只服务故障门禁，不是生产机器规格。

## 控制台

| 能力 | 地址 | 本地凭据 |
|---|---|---|
| Nacos | `http://localhost:18048` | standalone 未启用认证 |
| Prometheus | `http://localhost:9090` | 无 |
| Grafana | `http://localhost:3000` | `admin` / `admin` |
| BanyanDB | `http://localhost:17913` | 无 |
| SkyWalking Horizon UI | `http://localhost:18050` | `admin` / `e2e-passw0rd` |

Grafana 自动加载 Prometheus datasource、`IM Chat overview` 和 `IM Chat Load Test` dashboard。SkyWalking OAP 11 将数据写入 BanyanDB 0.11；Compose 服务名为 `skywalking-banyandb`，实际容器名带 `im-chat-local-` 项目前缀，stream、measure、trace、property 和 schema-server 根目录均位于 `banyan-data` 命名卷。Horizon UI 通过 OAP 的 Query/Admin API 读取和管理观测数据，默认账号仅用于本地开发。SkyWalking Agent 由应用镜像挂载，`SW_AGENT_NAME` 使用 Maven artifactId；Agent 二进制不提交到仓库。

## 本地压测配置

`compose.load-test.yml` 只能与 base Compose 合并使用。它仅为十个应用设置 `IM_DATASOURCE_SHARDING_SQL_SHOW=false` 和 root INFO，保留 SkyWalking Agent、management metrics 和日常 base 配置。`prometheus-full` 开启本地 Remote Write receiver，k6 固定写入 `http://127.0.0.1:9090/api/v1/write`。

```bash
./scripts/load_test.sh doctor
./scripts/load_test.sh prepare --users 20 --group-size 10
./scripts/load_test.sh connections --batch <batch-id> --levels 20,50
./scripts/load_test.sh messages --batch <batch-id> --connections 20 --levels 50,100
./scripts/load_test.sh cleanup --resume <batch-id>
./scripts/load_test.sh restore
```

统一 runner 会先确保 base full 的 19 个服务健康，再切换 load profile。load profile 暂停不属于被测链路的 IAM、Audit、Admin、Ops，只保留九个基础设施/观测服务和 HTTP Gateway、WS Gateway、Broker、Account、Social、Message 六个核心应用，避免单机 12 GiB 配额下的管理应用资源竞争污染容量结论。base 中十个应用和 load 中除 Message 外的核心应用使用 1 GiB 容器上限；Message 在 load profile 使用 2 GiB，因为 Agent、ShardingSphere、Dubbo、Nacos、Redisson 和 64 个 Receipt consumer 的空载常驻已约 0.8 GiB，1 GiB 在真实消息轮中会反复 cgroup OOM，无法形成有效容量观测。runner 会 force-recreate 核心应用，并用不存在账号的只读登录探针确认 Gateway 已从 Nacos 收敛到新 Account 实例，轮次结束还要求核心容器健康且未发生自动重启。base restore 会恢复 IAM 和三个 management 应用；management 首次权限目录同步导致 readiness 失败时，runner 只重启该服务一次，restart 后允许短暂 `unhealthy` 并在 120 秒内有界复查。默认结束执行 cleanup 和 base restore；`KEEP_LOAD_TEST_DATA=1` 仅用于保留诊断现场。cleanup 的批次登出可从部分成功恢复：受控 Token 已返回认证失败时按已失效继续，其他错误仍停止清理。cleaner 失败时状态保持 `FROZEN`，Account、Social、Message 保持停止，必须重跑 `cleanup --resume`，不能先手工 restore。状态 journal、凭据、k6 summary 和资源样本位于 `.harness/load-test`，其中凭据文件权限为 `0600` 且 cleanup/restore 后删除。

`./scripts/verify.sh load-test` 只验证工具契约；显式 `./scripts/verify.sh load-test-smoke` 才在已启动的 full 上创建 20 个临时用户并执行真实连接/消息。smoke 的消息阶段默认使用 1 msg/s、1005 秒测量和 10 秒排空，仍要求至少 1000 次实际发送；连接阶段为 20 秒。smoke 结束后要求状态为 `BASE_READY`、敏感临时文件已删除、三业务库普通数据 checksum 不变，并生成可由 report/handoff 展示的专用 evidence。

本机实测容量结论及 HTTP Gateway、WebSocket Gateway、Broker、Account、Social、Message、MySQL、Redis、Nacos、Prometheus、SkyWalking OAP 的配置规划起点见[本机容量压测报告](../../docs/load-test-reports/2026-09-16-local-capacity-baseline.md)。报告中的规格不是生产容量承诺，目标拓扑仍需重新压测。

BanyanDB standalone 显式使用 `127.0.0.1` 作为节点注册地址。Docker Desktop 的容器 hostname 虽然可以解析并建立 TCP 连接，但 BanyanDB 0.11 的 property schema 健康 RPC 可能持续超时，导致主 gRPC 端口 `17912` 无法启动；loopback 地址使单节点内部注册稳定，不改变 OAP 通过 Compose 服务名 `skywalking-banyandb:17912` 访问的方式。

## 数据初始化

`mysql/init/ddl` 包含五个 Server `sql/ddl.sql` 的本地快照，`mysql/init/dml` 包含 IAM `sql/dml.sql` 的本地开发种子。MySQL 官方 entrypoint 不递归执行子目录，因此根目录 `00-initialize.sql` 使用客户端原生 `SOURCE` 固定先执行全部 DDL、再执行 DML，避免依赖宿主脚本执行权限。IAM 种子预置四个应用、三个 web client 和三个权限目录机器客户端，供本地浏览器登录及 Admin、Audit、Ops 权限同步；它不用于生产。Message DDL 直接创建 `_0` 到 `_7` 的物理分表。SQL 改变后必须同步所属快照；现有命名卷不会自动重放 init SQL。

### Bolt 受限开发拓扑

默认 Broker、WS Gateway、Account、Message 启用 MTLS；未提供证书材料时启动失败，不会因 TLS 错误切换明文。仅合成数据可显式激活 `bolt-dev`，并设置 `IM_BOLT_SYNTHETIC_DATA=true`。宿主最小装配使用字面回环地址；Account/Message 的该 profile 只配置 Bolt，并不替代其数据库、Redis 或业务依赖。

容器使用独立的 `compose.bolt-dev.yml`，**不得与 `compose.yml` 合并**。先构建本轮 Broker/Gateway exec jar 和该 Compose 的镜像，再运行：

```bash
docker compose -f deploy/local/compose.bolt-dev.yml build
bash scripts/bolt-dev-up.sh --check-only  # create/inspect 后清理，不启动业务
bash scripts/bolt-dev-up.sh               # 同样检查成功后才 start
```

该最小拓扑只有 Broker、Gateway、专用 Redis/Nacos。Nacos 用于发现（config 导入关闭），Redis 用于本地运行态，Redis `/data` 使用 tmpfs；没有共享数据卷，不接入现有 infra 网络，Account/Message/Dubbo 业务远端不在此拓扑内。因此 topology preflight 成功不是完整业务启动或 WebSocket E2E 成功证明。

脚本在查询空项目之前按 Docker 实际 endpoint + project 获取固定 `/tmp/im-chat-bolt-dev-$UID` 中的原子 mkdir 锁（不随调用者 `TMPDIR` 改变），持锁直至启动或清理结束；解析 endpoint 或探测 Docker 状态失败时拒绝创建，不执行 down。并发调用立即拒绝；崩溃遗留锁不自动抢占，须人工核对 owner-pid 与 Docker 状态后清理。锁依赖可信本地操作者，不是恶意本地用户安全边界。脚本只接受空的独占 Compose project，实际创建后、首次 start 前检查 `internal` bridge、`172.30.88.0/24`、唯一网络附着、profile/synthetic/declaration/绑定地址，以及声明端口与实际端口完全一致。实际 inspect 必须提供网络归属 Labels/Containers Hash、精确 false 的 Running/Privileged、Mounts Array 和 PortBindings（无发布时允许明确 null）；缺少事实不当作空值放行。仅发布回环 WebSocket 端口，不发布 Bolt 端口。在独占锁及空项目检查成立后，发起 create 前登记部分资源清理责任。正常 TERM/INT 在 Bash PID 发布前只记录 pending cancel，发布后统一接管/回收；Ruby spawn 与 wait 同处 ensure 保护，PID 尚未发布时同样只记录取消。取消先终止本次 Docker operation 的专属进程组并等待 supervisor 回收，再持锁执行 project down；清理结束后才释放锁。失败和 `--check-only` 删除本次 project 的容器、网络与卷；成功 start 后由调用方按输出命令清理。它不修改旧 infra/Redis。镜像须由调用方预先构建，脚本不拉取或构建镜像。

JVM 仍只校验 profile、synthetic、network declaration 与字面地址；固定 CIDR 或布尔声明本身不是隔离证明。实际 Docker 检查与 JVM 校验在上述受控部署路径联合成立，不生成 JVM 准入文件，不挂载 Docker socket。能够修改 Docker 拓扑或绕开脚本的 privileged 操作者属于可信部署边界，不承诺运行期容器 sandbox 或持续防止附着变更。开发模式不提供观察票或自动故障摘流；安全 HA 继续使用真实 MTLS。

### Bolt 材料与启动配对

旧 full/infra/压测命令不是 MTLS 材料自动供应器：Broker、Gateway、Account、Message 默认 MTLS，无有效 keystore 或 bootstrap 配对时应启动失败，不得从失败隐式回退开发模式。正式材料与 secret 分发/轮换仍需部署方独立交付，测试私钥不能复用为生产。

宿主 bolt-dev profile 的 bootstrap 配对位于 Gateway `im.gateway.ws.broker.bootstrap-targets`、Account `im.account.broker.bootstrap-targets`、Message `im.message.broker.bolt.bootstrap-targets`，每项为 address/expected-peer-identity；仍需与 Nacos 发现候选取交集。Broker Gossip 的 `im.broker.cluster.trusted-peers` 显式绑定 observation-id 与 target.address/target.expected-peer-identity。Broker DEV 下行 `im.broker.gateway-push.bolt.development-targets` 仅描述目标，不改变认证 principal 和租约；受控容器 Compose 通过 Spring indexed environment binding 显式设置 `IM_BROKER_GATEWAYPUSH_BOLT_DEVELOPMENTTARGETS_0_ADDRESS=172.30.88.11:18012` 与 `IM_BROKER_GATEWAYPUSH_BOLT_DEVELOPMENTTARGETS_0_EXPECTEDPEERIDENTITY=spiffe://im-chat/gateway/bolt-dev-gateway`，URI 使用既有 `spiffe://im-chat/gateway/` namespace。此 synthetic URI 仅供出站 pool descriptor 选择；Gateway 实际 DEV principal/注册 marker 仍为 `trusted-network://172.30.88.11`，不改变 DTO、Gossip 或租约授权。Nacos config 已关闭且没有配置挂载，该显式 pair 是此 Compose 的部署配对来源；MTLS 下行继续按已注册 principal 选择目标，不使用 DEV pair。不可混入普通 full 网络。

D14 仅固定启动路径和选项。旧 Channel 保持原 session，新 Channel 可能读取更新文件，不提供 watcher/每次 reload/任意原地并发写一致保证。建议到期前完整原子交付材料，按旧 → 双信任 → 新叶 → 撤旧 CA 验证双向连接和原生拒绝，并滚动重启；不是 only-restart 门禁或零中断承诺。不新增 D13 私钥副本，也没有在线吊销能力。

### HA 专用测试材料

`run-routing-ha-scenarios.sh` 每轮在新建 `0700` 临时目录调用 `scripts/lib/routing-ha-tls.rb`，通过本机 JDK 21 `keytool` 生成一次性测试 CA、三个 Broker 和两个 Gateway 的独立 RSA 私钥、CA 签发叶证书及 `PKCS12`。CA 私钥在签发结束后删除；叶材料与配置为 `0600`，每容器只读挂载自身目录到 `/run/routing-ha-tls`，通过 `SPRING_CONFIG_ADDITIONAL_LOCATION` 加载。容器为 Temurin JRE 21，沿正式 `im.bolt.{client,server}.tls.key-store/key-store-password/key-store-type` 配置读取同一个节点库；其中包含叶私钥、叶链和公开 CA 信任项，没有独立 trust-store 属性。

客户端/服务端保持 `MTLS`，服务端 `need-client-auth=true`，不激活 `bolt-dev`。Broker 叶唯一 URI 为 `spiffe://im-chat/broker/<稳定Raft node-id>`，Gateway 为 `spiffe://im-chat/gateway/<Compose服务名>`，DNS SAN 为服务名。生成和启动前只读检查验证私钥/叶一致、CA 签名与信任、全部库中证书当前有效、唯一 URI/DNS、配置精确配对及私有权限；真实握手和 D10 会话数组检查仍归生产连接层，不由离线检查替代。

生成配置显式设置 Nacos `spring.cloud.nacos.discovery.ip` 为 Broker 服务 DNS，与 Gateway `im.gateway.ws.broker.bootstrap-targets` 的地址取交集；不从 metadata 学习身份。Broker Gossip `im.broker.cluster.trusted-peers` 把 `broker-<host>-18020` 观察 ID 配为服务 DNS 地址与稳定 Broker URI，观察 ID 不作为 Raft 身份。Raft bootstrap/voter、角色/成员/观察授权与默认预算不变。Gateway MTLS 下行不使用 `development-targets`。

材料缺失、权限错误、生成失败或配对不一致在创建容器前返回非零。runner 正常退出/失败/INT/TERM 先清理 owned Compose project，成功后删除本轮材料；down 失败保留私有目录并输出路径，不删除未知资源。SIGKILL/宿主崩溃可能遗留私有临时目录，须人工确认无容器占用后清理，不自动接管旧路径。这是隔离验收 fixture，不是生产证书供应、D13 私钥副本或在线签发机制。离线合同入口为 `bash scripts/test-routing-ha-tls.sh`；只有后续真实 HA 执行才能证明容器挂载读取及互认证。
