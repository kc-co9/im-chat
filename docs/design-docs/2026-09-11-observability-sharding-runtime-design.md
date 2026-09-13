# 可观测性与分片运行接入设计

## 背景

本设计记录 2026-09-11 通过 `$grill-me` 逐轮确认的运行时改造决策。目标是在同一个版本内交付数据库乐观锁字段、MyBatis-Plus 插件、ShardingSphere 数据源、Prometheus 指标、SkyWalking Trace 日志关联，以及本地一键启动编排。

这批改造横跨数据层、所有 Spring Boot Server、日志体系和本地运行环境。为了避免把临时执行选择散落在聊天记录或 active plan，本文件固定已经确认的产品和工程取舍；执行进度和验证证据仍由对应 execution plan 维护。

## 决策

### 数据库版本字段

所有业务表在原始 DDL 上增加 `version BIGINT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号'`。该字段用于 MyBatis-Plus optimistic lock，不引入 Flyway 或 Liquibase。

本次曾在根目录提供临时 `.tmp_add_version_and_sharding_ddl.sql`，用于人工执行 ALTER 和 Message 分表迁移；该文件已完成临时用途并删除，不作为长期迁移体系或 Git 交付内容。

### MyBatis-Plus 插件

`im-plugin/im-datasource` 统一注册 MyBatis-Plus 插件：

- optimistic locker：配合 `BaseEntity.version` 控制并发更新；领域对象使用不与业务版本冲突的 `Identification.rowVersion` 传递并发令牌。
- pagination：统一分页插件，数据库类型为 MySQL。
- block attack：阻止无条件全表更新或删除。

`BaseEntity` 拥有通用持久化字段，其中 `version` 使用 `@Version` 与 `@TableField("version")` 表达框架级乐观锁语义。领域对象只携带命名为 `rowVersion` 的并发令牌，不直接感知 MyBatis-Plus 注解。

### ShardingSphere 数据源

所有拥有数据库的 Server 都通过 `im.datasource.sharding.enabled` 与 `im.datasource.sharding.config-location` 接入 ShardingSphere 数据源，配置文件命名为 `im-sharding.yml`。`application.yml` 只保存开关和配置位置，具体规则放在独立 ShardingSphere YAML 中。

`im-sharding.yml` 的放置位置是各 Server 自己的 `src/main/resources`，便于各模块保留本地覆盖能力。统一能力归 `im-datasource` 所有，不在业务模块复制数据源创建逻辑。

Message 模块只有高写入消息表真实分片：

- `db_im_private_chat`
- `db_im_group_chat`
- `db_im_private_inbox_message`
- `db_im_group_inbox_message`

这些表按 `user_id` 做表分片，首版 8 张物理表，后缀 `_0` 到 `_7`。群聊消息事实使用 `group_id` 保留业务事实，但本轮分片键仍按 `user_id` 执行；私聊消息事实使用 `chat_id` 保留业务事实。

非 Message 数据库模块不做真实分片，通过 ShardingSphere single table 规则接入同一数据源管线。

### Prometheus 与 Actuator

所有 Spring Boot Server 接入 Actuator 与 Prometheus registry，并使用独立 management port。Nacos 只注册业务端口，metadata 中记录 management port，便于治理和抓取。

端口按服务族分号段，management port = business port + 1000：

| 服务 | 业务端口 | Management 端口 |
|---|---:|---:|
| `im-http-gateway` | 18010 | 19010 |
| `im-ws-gateway-server` | 18011 | 19011 |
| `im-broker-server` | 18020 | 19020 |
| `im-account-server` | 18030 | 19030 |
| `im-social-server` | 18031 | 19031 |
| `im-message-server` | 18032 | 19032 |
| `im-iam-server` | 18040 | 19040 |
| `im-audit-server` | 18041 | 19041 |
| `im-admin` | 18042 | 19042 |
| `im-monitor` | 18043 | 19043 |

其中 WS Gateway 的业务端口 `18011` 是浏览器 WebSocket，Gateway 下行 Bolt 使用同服务族的 `18012`；其 Spring HTTP server 只承载 `19011` 运维端点。Broker 的业务端口 `18020` 是 Bolt，`19020` 同时承载只读管理 API 和 Actuator。两者通过显式 Nacos discovery port 保证注册的仍是业务协议端口。

Actuator 暴露 `health,info,prometheus,metrics`，`health` 细节默认 `never`。Grafana 面板查看聚合指标；原始 `/actuator/prometheus` 作为抓取和临时排查入口。

Prometheus 抓取不携带业务登录态。拥有 Spring Security 策略的应用在自己的安全链中显式放行 `/actuator/**`；Reactive HTTP Gateway 沿用自身规则，IAM SDK 默认链、Admin、Audit 和 IAM Server 分别保留安全所有权。`im-metrics` 不声明 `SecurityFilterChain`，避免使 Spring Boot 默认安全或 IAM 条件链退让。该放行不覆盖业务 URL，management port 必须由内网、防火墙或 Compose 网络隔离，不得直接暴露到公网。

### SkyWalking

SkyWalking 接入方式为 Java Agent + toolkit。所有 Server 都具备日志级 TraceId 能力，日志格式保留现有 MDC `traceId`，并新增 SkyWalking `%tid`，格式为 `[%tid] [%X{traceId}]`。

新增插件模块命名为 `im-tracing`。该模块只声明通用 tracing/logback toolkit 能力和公共 logback include 片段，不依赖 Gateway、Broker 或业务服务。各 Server 保留自己的 `logback-spring.xml`，通过 include 使用公共片段；特殊服务仍可在本地 logback 文件覆盖 appender、level 或 pattern。

版本锁定为 BanyanDB `0.11.0`、SkyWalking OAP `11.0.0`、独立发布的 Horizon UI `1.0.0`，Java Agent/toolkit `9.7.0`。OAP 11 使用 BanyanDB provider，不使用已移除的 H2 provider。Agent 二进制不提交到仓库。

### 本地一键启动

本地运行编排放在 `deploy/local/compose.yml`，提供两个 profile：

- `infra`：启动 MySQL、Redis、Nacos、Kafka、Prometheus、Grafana、BanyanDB、SkyWalking OAP/Horizon UI 等本地基础设施。
- `full`：在 `infra` 之上追加应用服务容器。

应用容器基于 Maven 已构建 jar，统一使用 `deploy/local/app.Dockerfile`，镜像名为 `im-chat/<artifactId>:local`。Dockerfile 专用 ignore 只发送十个 exec jar，Compose 通过通配路径避免项目版本变化导致构建失效。`scripts/local_up.sh` 默认启动 `infra`，选择 `full` 时先执行 clean package；脚本等待服务健康后返回。`scripts/local_down.sh` 默认保留命名 volume，`--volumes` 才清理持久化数据。

infra 与 full 全部使用 `im-chat-local` bridge 网络，不依赖 Docker Desktop host networking。所有浏览器入口、业务端口和 management port 都通过 Compose 显式映射到宿主 `127.0.0.1`；容器内服务监听 `0.0.0.0`，宿主不能通过局域网网卡访问本地弱凭据或匿名 Actuator。应用默认生产配置不变，这些地址只由 full profile 环境变量覆盖。

运行地址按语义分离，不能把监听地址直接发布到注册中心：

| 地址类型 | full 容器值 | 用途 |
|---|---|---|
| Spring/management、Bolt、WS bind address | `0.0.0.0` | 仅用于容器内监听；宿主映射仍固定为 `127.0.0.1` |
| MySQL、Redis、Nacos、Kafka、SkyWalking OAP | 对应 Compose 服务名 | 容器到基础设施的固定依赖地址 |
| Nacos discovery IP | 不显式设置 | 由客户端自动探测当前容器可达 IP，业务端口仍按应用配置注册 |
| Dubbo protocol host | 不显式设置 | 由 Dubbo 自动探测容器可达 IP，禁止发布 `0.0.0.0` |
| WS Gateway advertised host | `im-ws-gateway-server` | Broker 回推到 Gateway Bolt `18012` |
| Broker advertised host | `im-broker-server` | Broker/Gateway 调用 Bolt `18020` |
| Broker management bind/advertised host | `0.0.0.0` / `im-broker-server` | Monitor 通过 Nacos metadata 访问 `19020`；配置模型增加 bind 与发布地址分离 |

Prometheus 使用两个按 profile 区分的服务定义，共享 `prometheus` 网络别名和数据卷。`prometheus-full` 通过 Compose 服务名抓取十个 management port，适用于 Linux 和 Docker Desktop；`prometheus-infra` 通过 `host.docker.internal` 抓取 IDE 服务，仅承诺 Docker Desktop 本机开发路径，Linux 不宣称可抓取只绑定宿主 loopback 的进程。两者都通过服务名抓取 OAP telemetry，并将 `9090` 映射到宿主 loopback。Grafana 不声明无法跨 profile 表达的 `depends_on`，通过稳定别名 `prometheus:9090` 读取并映射 `3000`；Compose `--wait` 负责最终健康收敛。

Compose profile 本身不保证旧 profile 服务退出。切换到 `full` 前，`local_up.sh` 停止并移除 `prometheus-infra`；切换到 `infra` 前，脚本停止并移除 `prometheus-full` 和十个 full-only 应用，等待应用从 Nacos/Dubbo 注销并确认应用端口已经释放。这样既避免两个 Prometheus 并发挂载同一个 TSDB volume，也保证 infra 只保留基础设施。Harness 使用 fake Docker 覆盖 `infra -> full`、`full -> infra` 的命令顺序与精确服务集合；真实验证执行双向切换并检查端口和注册实例。

切换清理默认最多等待 30 秒，可通过 `LOCAL_SWITCH_TIMEOUT_SECONDS` 调整；超时返回非零且不会继续启动目标 profile。每次 Compose `up --wait` 返回后，脚本还会比较实际运行服务与目标 profile 的精确集合，残留旧 profile 服务或缺少目标服务都视为失败。

IAM 的浏览器可见 issuer 保持 `http://localhost:18040`，用于 Authorization、Logout 和 Token issuer；IAM SDK 增加默认回退 issuer 的内部 `service-uri`，full 容器设置为 `http://im-iam-server:18040`，用于 Token、Revocation、Catalog 和全部 Introspection 调用，包括 Audit 独立的 ingestion introspector。只有 issuer 本身是 loopback 开发地址时才允许 `service-uri` 使用 HTTP，否则内部地址也必须使用 HTTPS。

Admin、Audit、Monitor 使用各自独立的 IAM Session Cookie 和 CSRF Cookie 名，前端读取对应名称；Cookie 不按端口隔离，不能继续共享默认名称。`local_up.sh` 在 Maven 构建或 Compose 启动前，预检目标 profile 的全部宿主端口；由当前 `im-chat-local` Compose 项目占用的端口允许复用，其他进程或 Docker 项目占用则快速失败，避免耗时构建后留下部分拓扑。

业务模块 SQL 只使用根 `sql/ddl.sql` 和可选 `sql/dml.sql`，交付 DDL 使用 `CREATE DATABASE/TABLE IF NOT EXISTS` 且禁止 `DROP TABLE`，允许初始化入口安全重放但不把它视为 schema migration。MySQL 本地初始化在 `deploy/local/mysql/init/ddl`、`dml` 保存对应快照，根 `00-initialize.sql` 通过 MySQL 客户端原生 `SOURCE` 固定先执行 DDL、再执行 DML，避免依赖宿主脚本执行权限。Compose full 是开发环境便利入口，不替代生产部署和稳定性验证。

## 决策记录

下表把本次 `$grill-me` 的选择还原为可独立理解的语义。选项字母只属于当时会话，不作为设计事实。

| ID | 来源/问题 | 最终决策 | 理由与影响 |
|---|---|---|---|
| D1 | 交付方式 | 数据字段、分片、Prometheus、SkyWalking 和本地编排合并在一个版本交付，内部按任务分阶段验证 | 避免多版本协调，但最终版本必须通过完整集成门禁 |
| D2 | `version` 语义 | 所有业务表增加 `BIGINT NOT NULL DEFAULT 0`；数据库 Entity 使用 `version`，领域对象使用 `Identification.rowVersion` 跨 Domain/Entity 携带，用于 MyBatis-Plus optimistic lock | Entity 到 Domain 的业务字段和 `pkId/rowVersion` 由 Transformer 完整重建，Repository 只在写成功后回填生成值；公共 MyBatis 层不统一解释 `false`，完整聚合写冲突由具体 Repository 中断；新增权限可批量插入，更新仍逐条检查影响行数，避免 JDBC `SUCCESS_NO_INFO` 隐藏单条乐观锁冲突 |
| D2A | 事务回滚与领域对象 | 事务失败只回滚数据库；参与失败事务的聚合视为失效，重试前必须通过 Repository 重新加载，不为普通 Java 对象实现内存状态回滚 | 当前异常直接离开事务边界且没有复用失败聚合的生产调用方，避免为假设场景引入跨 Repository 的事务同步复杂度 |
| D3 | SQL 变更载体 | 修改当前 `sql/ddl.sql`，初始化数据使用 `sql/dml.sql`；曾提供的根目录临时 ALTER/分表迁移 DDL 已删除；不引入 Flyway/Liquibase | 模块只维护当前 DDL/DML 事实，临时人工迁移不进入长期交付 |
| D4 | MyBatis-Plus 插件 | 统一启用 optimistic locker、pagination 和 block attack | 公共数据源插件集中治理并发、分页和危险全表写操作 |
| D5 | ShardingSphere 覆盖范围 | Account、Social、Message、IAM、Audit 全部通过 ShardingSphere DataSource | 数据源路径一致；非 Message 模块仍保持单表 |
| D6 | 分片配置归属 | 每个数据库 Server 自有 `im-sharding.yml`，`application.yml` 只保存开关和文件位置；YAML 中 `im.datasource.sharding.*` 占位符由 Spring Environment 解析，ShardingSphere inline 表达式保持原样 | 模块规则可独立覆盖，公共插件不持有业务表名；Nacos 可覆盖 URL/账号/密码/SQL 日志开关，生产也可切换外部规则文件 |
| D7 | Message 分片对象 | 只分 `db_im_private_chat`、`db_im_group_chat`、`db_im_private_inbox_message`、`db_im_group_inbox_message` | 聚焦高写入表，不扩大本轮迁移范围 |
| D8 | 分片键、数量与主键 | 四张 Message 表统一按 `user_id` 分 8 张物理表，后缀 `_0` 至 `_7`；`id` 保留数据库自增技术主键，`message_id`/`chat_id` 等业务全局 ID 保留业务语义，UPDATE 同时包含 `user_id`、主键和 version | 用户视角数据局部化；技术主键和业务身份分离，避免把数据库存储优化误当成业务属性；未来扩容仍需要数据迁移 |
| D9 | 消息事实字段 | 群聊事实继续保存 `group_id`，私聊事实继续保存 `chat_id` | 这些字段表达业务事实，不等同于本轮物理分片键 |
| D10 | 非 Message 规则 | 非 Message 数据库模块显式使用 single table rule | 先统一接入管线，不制造无容量依据的物理分片 |
| D11 | Actuator endpoint | 暴露 `health,info,prometheus,metrics`，health details 默认 `never` | 兼顾抓取和排查，同时不默认公开组件细节 |
| D11A | Prometheus 抓取认证 | Actuator base path 允许匿名抓取，业务安全链保持不变，management port 由网络边界隔离 | Prometheus 无需业务 Session 或 OAuth 凭据；公网暴露必须由部署层阻止 |
| D12 | 端口治理 | 按 Gateway、Broker、Service、Management 号段分配业务端口，management port 固定为业务端口加 1000 | 端口可推导且避免同机冲突 |
| D13 | Nacos 注册 | Nacos 注册业务端口，并在 metadata 记录 management port | 服务调用与运维抓取入口分离 |
| D14 | SkyWalking 接入 | Java Agent 与 toolkit 同时接入所有服务 | Agent 提供自动追踪，toolkit 提供代码和日志级 TraceId 关联 |
| D15 | tracing 模块命名 | 新增通用插件 `im-tracing`，不使用 `im-observability` | 本模块只拥有 tracing/logback 能力，Prometheus 仍由 `im-metrics` 所有 |
| D16 | Logback 所有权 | `im-tracing` 提供公共 include，各 Server 保留 `logback-spring.xml` 并允许本地覆盖 | 统一基础格式，同时保留 Message 等服务的特殊 appender 和脱敏需求 |
| D17 | Trace 字段 | 日志同时输出 SkyWalking `%tid` 与现有 MDC `%X{traceId}` | 两类 TraceId 服务不同链路，迁移期保留现有诊断能力 |
| D18 | SkyWalking 版本 | BanyanDB 固定 `0.11.0`、OAP 固定 `11.0.0`、Horizon UI 固定 `1.0.0`，Java Agent/toolkit 固定 `9.7.0` | OAP 11 已移除 H2 provider 和旧内置 UI；BanyanDB 0.11 是 OAP 11 的兼容存储 API，固定独立 Horizon 版本可避免浮动升级 |
| D18A | ShardingSphere SQL 日志 | 五份 `im-sharding.yml` 使用 `${im.datasource.sharding.sql-show:true}`；本地缺省为 `true`，生产由 Nacos 覆盖为 `false`；应用不配置 MyBatis `StdOutImpl` 旁路 | 本地默认便于验证分片路由，生产通过单一开关避免输出 SQL 与参数日志；这是 ShardingSphere 运行配置，不是业务代码开关 |
| D19 | 本地启动模式 | `deploy/local/compose.yml` 提供 `infra` 和 `full` profile，默认启动 infra；全部服务使用 bridge 网络并将所需端口显式映射到宿主 loopback | 可一次启动全部依赖，也允许 IDE 单独启动某个应用；不要求 Docker Desktop host networking |
| D19A | 本地容器寻址 | bind、advertised、registry 和 dependency 地址分离；切换 full 时移除 infra Prometheus，切换 infra 时移除 full Prometheus 和十个应用；IAM 区分浏览器 issuer 与默认回退到 issuer 的内部 `service-uri` | 防止发布 `0.0.0.0`、避免重复抓取或并发挂载 TSDB，确保 infra 精确服务集合，并兼顾浏览器稳定 URL 与两种本地启动模式；生产未配置 `service-uri` 时行为不变 |
| D19B | BanyanDB 单节点地址 | Compose 服务命名为 `skywalking-banyandb`，standalone 显式使用 loopback 节点地址 | 避免 Docker Desktop arm64 下容器 hostname 的 property schema 自连接 RPC 超时，OAP 仍通过服务名访问 |
| D19C | 本地 Cookie 隔离 | Admin、Audit、Monitor 使用各自的 Session/CSRF Cookie 名，前端读取对应 CSRF Cookie | Cookie 不按端口隔离，不能因多个 `localhost` 管理应用共享名称而互相覆盖 |
| D19D | 本地端口互斥 | 启动前预检目标 profile 的全部 infra 与应用端口，并排除当前 Compose 项目已拥有的映射 | 与 IDE、其他进程或其他 Docker 项目冲突时快速失败，不在耗时构建后留下部分拓扑 |
| D19E | 本地 Agent 启动死锁规避 | full 应用统一增加 `-XX:-UseContainerSupport`，同时保留显式堆、metaspace 和容器内存上限 | SkyWalking Agent 9.7.0 的 JVM 指标线程与 JDK 21 启动类加载可能在容器指标初始化路径形成死锁；该开关只用于本地 Compose，不外推到生产 |
| D19F | 本地 IAM 冷启动超时 | full 的 Audit、Admin、Monitor 将 IAM HTTP read timeout 覆盖为 `10s`，SDK 默认仍为 `2s` | 十个应用并发冷启动时，IAM 已健康但 catalog 同步可能超过 2 秒并永久保持 readiness `DOWN`；本地覆盖吸收资源竞争，不改变生产故障窗口 |
| D20 | full 应用镜像 | full 模式先 Maven clean package，再用通用 `app.Dockerfile` 和专用 ignore 构建 `im-chat/<artifactId>:local` | 构建方式统一、不为每个 Server 复制 Dockerfile，并避免旧版本 JAR 与无关仓库文件进入构建上下文 |
| D21 | 本地数据生命周期 | Compose 使用命名 volume；down 默认保留，只有 `--volumes` 显式清理 | 日常重启不丢数据，清理动作保持可见和受控 |
| D22 | IAM 管理员应用角色替换 | 按 `administratorId` 使用分布式锁串行化读、差异计算、删除和新增 | 防止同一管理员的并发替换合并为非预期权限并集，不阻塞不同管理员 |
| D23 | 启停入口 | `scripts/local_up.sh` 默认 infra、支持 full，并等待服务健康；`scripts/local_down.sh` 负责停止和可选清卷 | 避免逐个服务手工启动，失败时返回非零，并保留单服务本地调试路径 |

## 非目标

- 不在应用仓库部署生产 Prometheus、Grafana 或 SkyWalking 平台。
- 不提交 SkyWalking Agent 二进制。
- 不引入 Flyway/Liquibase。
- 不让领域模型依赖 ShardingSphere、SkyWalking 或 Micrometer API。
- 不把非 Message 表真实分片。
- 不把临时 DDL 文件升级为长期迁移机制。

## 风险与约束

ShardingSphere 依赖版本必须与当前 Spring Boot/SnakeYAML 兼容；若旧版 ShardingSphere 无法兼容 Spring Boot 3.5 的 SnakeYAML 2.x，应优先升级 ShardingSphere 或调整数据源创建方式，而不是降低全仓 SnakeYAML。

Message 首版 8 分表是配置和 DDL 层决策。未来扩大到 16 或 32 表需要新增物理表、迁移历史数据并调整分片表达式，不能只改代码。

Management endpoint 必须按内网或本地环境隔离。`metrics` 和 `prometheus` 暴露有排查价值，但不应在公网直接开放。

## 验证

实施时需要分层验证：

- `im-datasource` 单元测试覆盖 `BaseEntity.version`、MyBatis-Plus interceptor 与 ShardingSphere datasource 创建顺序。
- SQL harness 覆盖 DDL 字段和临时 ALTER DDL 形态。
- 受影响 Server 启动测试覆盖 `im-sharding.yml` 可解析和上下文可加载。
- Prometheus/Actuator 配置测试覆盖 endpoint 暴露和 management port。
- SkyWalking logback 片段测试覆盖 `%tid` conversion rule 与公共 include 可加载。
- 最终执行 `./scripts/verify.sh quick`、`./scripts/verify.sh clean`、`./scripts/verify.sh full`，并在可用环境验证 `deploy/local` 的 `infra` profile。

## 执行计划

实施步骤和证据记录在 [可观测性与分片运行接入实施计划](../exec-plans/active/2026-09-11-observability-sharding-runtime.md)。
