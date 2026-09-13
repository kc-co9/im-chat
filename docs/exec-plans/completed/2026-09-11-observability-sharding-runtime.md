# 可观测性与分片运行接入实施计划

## Sprint Contract

- 目标：在同一版本中完成数据库 `version` 乐观锁字段、MyBatis-Plus 插件、全数据库模块 ShardingSphere 接入、Prometheus 指标暴露、SkyWalking 日志 Trace 关联，以及本地 infra/full 一键启动编排。
- 范围：`im-plugin/im-datasource`、`im-plugin/im-metrics`、新增 `im-plugin/im-tracing`；Account、Social、Message、IAM、Audit 等拥有数据库的 Server DDL、Entity 与 ShardingSphere 配置；所有 Spring Boot Server 的端口、Actuator、日志配置；`deploy/local` 本地 Compose、Prometheus、Grafana、MySQL 初始化 SQL 和启动/停止脚本；根与相关模块文档。
- 不做事项：不引入 Flyway/Liquibase；不把 SkyWalking Agent 二进制提交到仓库；不把生产 Prometheus/Grafana/SkyWalking 平台部署纳入应用仓库；不改变领域层对持久化乐观锁的感知边界；不把非 Message 表真实分片。
- 验收标准：所有业务表原始 DDL 与 MyBatis Entity 具备 `version` 乐观锁字段；模块只保留当前 `sql/ddl.sql` 与可选 `sql/dml.sql`，临时迁移 DDL 已按用户要求删除；`im-datasource` 统一注册乐观锁、分页、防全表更新/删除插件；所有数据库 Server 通过 `im-sharding.yml` 接入 ShardingSphere，Message 四张高写入表按 `user_id` 分 8 表，其它模块显式 single tables；所有 Spring Boot Server 暴露独立 management port 与 `health,info,prometheus,metrics`，health details 默认 `never`；所有 Server 日志输出 `[%tid] [%X{traceId}]`；本地 `deploy/local/compose.yml` 提供 `infra` 与 `full` profiles，并完成真实双向切换和全量健康验证。
- 风险链路：乐观锁字段全表加入会影响历史 SQL、插入默认值和批量更新；ShardingSphere 数据源替换可能影响启动顺序、连接池、事务和 MyBatis Mapper；Prometheus endpoint 暴露必须依赖 management port 内网隔离；SkyWalking toolkit 只用于日志 Trace 关联，不能把业务代码绑定到 APM SDK；Compose full 是开发编排骨架，不能替代生产部署验证。
- 依赖输入与所需权限：JDK 21、Maven、Node、Docker/Compose；必要时允许 Maven 写入 `~/.m2`；本地 `infra` 验证需要 Docker 可用；SkyWalking 版本锁定为 OAP `11.0.0`、Horizon UI `1.0.0`，Java Agent/toolkit `9.7.0`。

## 事实源

- 设计或产品规格：[可观测性与分片运行接入设计](../../design-docs/2026-09-11-observability-sharding-runtime-design.md)，以及本计划的 Sprint Contract；如实施中发现不可逆取舍，补充该设计。
- 根与局部 Architecture：`ARCHITECTURE.md`、`im-gateway/ARCHITECTURE.md`、`im-broker/ARCHITECTURE.md`、`im-service/im-message/ARCHITECTURE.md`。
- API、协议或数据契约：`docs/references/CODING_GUIDE.md`、`docs/references/UNIT_TEST_GUIDE.md`、`docs/references/HARNESS_GUIDE.md`、`docs/references/SQL_GUIDE.md`、各拥有模块 README。
- 相关代码入口：`im-plugin/im-datasource/src/main/java/com/co/kc/imchat/plugin/datasource/ImDatasourceAutoConfiguration.java`、`im-plugin/im-datasource/src/main/java/com/co/kc/imchat/plugin/datasource/dao/BaseEntity.java`、`im-plugin/im-metrics`、各 Server `src/main/resources/application.yml`、各 Server `sql/ddl.sql`、各 `infrastructure/mybatis/entity`、`scripts/verify.sh`。

## 验证分层

| 层级 | 命令或证据 | 必须/可选 | 当前结果 |
|---|---|---|---|
| RED fixture | datasource、Sharding DDL、metrics、tracing、local compose 和 Harness 规则的单元测试或 fixture | 按行为变更 | T1-T6 新增规则均取得预期 RED 后修复；详细证据见各任务小节 |
| 聚焦测试 | `mvn -q -pl im-plugin/im-datasource -am test`、`mvn -q -pl im-plugin/im-metrics -am test`、新增 `im-plugin/im-tracing` 测试、受影响 Server focused tests | 必须 | datasource、Sharding、metrics、tracing 及相关 Server 聚焦测试通过；T4/T5 startup 10/10 通过 |
| 架构或契约 | `./scripts/verify.sh architecture`、SQL drift、文档 drift、端口/配置规则检查 | 必须 | Harness/drift、Local Compose Harness、infra/full config 与 architecture 均通过；H2 runtime 仅允许由 `im-datasource` 所有 |
| 行为或运行环境 | `./scripts/verify.sh startup`、`./scripts/verify.sh e2e`、`./scripts/local_up.sh infra`、`./scripts/local_up.sh full` | 必须 | 本地 MySQL 五 Schema、32 张 Message 物理表和 `user_id=9 -> _1` 路由通过；真实双向切换、端口释放、Nacos/Dubbo 注销、infra 9/9、full 19/19 与观测链路通过 |
| 完整门禁 | `./scripts/verify.sh quick`、`./scripts/verify.sh clean`、`./scripts/verify.sh full`、`./scripts/verify.sh quality` | 必须 | 首次关闭审计发现并修复 `PROGRESS.md` 规范 `none` 标记；重新执行 affected/quick/startup/e2e/clean/full/quality 均通过，Harness 报告为 `passed`、score 100、1061 tests、0 failure、0 error，十二个模块质量等级均为 A（12/12） |

## 任务状态

同一时间至多一项任务为 `active`。状态只能使用 `not_started`、`active`、`blocked` 或 `passing`。

| ID | 行为目标 | 范围 | 状态 | 验证证据 | 阻塞/备注 |
|---|---|---|---|---|---|
| T1 | 固化计划、端口与设计事实 | plan、PROGRESS、Architecture/README 预备 | `passing` | `./scripts/verify.sh readiness` 已通过；新增 `docs/design-docs/2026-09-11-observability-sharding-runtime-design.md`；旧 Harness 计划归档后 `startup`、`cleanup`、`e2e`、`clean`、`full`、`quality` 已通过 | - |
| T2 | 引入乐观锁字段和 MyBatis-Plus 插件 | `im-datasource`、BaseEntity、Entity、DDL、临时 DDL | `passing` | OAuthClient 与 ApplicationPermission H2 陈旧副本冲突测试通过；Account/Message 更新冲突不推进 rowVersion；Entity 到 Domain 的技术状态由 Transformer 完整重建；权限新增和角色权限关系批量插入，更新逐条检查影响行数 | 数据库 Entity 使用 `version`，领域对象使用 `Identification.rowVersion`；公共 MyBatis 层不统一解释 false，具体 Repository 按用例副作用决定 |
| T3 | 接入 ShardingSphere 配置 | 各数据库 Server `im-sharding.yml`、application、Message 8 分表、本地初始化 SQL | `passing` | 数据库自增技术主键、业务全局 ID、领域 Builder 持久化字段边界与分片规则静态测试通过；真实 MySQL 验证 `user_id=9 -> _1` 及跨分片同 ID 更新隔离；五份规则默认 `props.sql-show: true` 且生产可由 Nacos 覆盖为 `false` | 最终门禁与独立复审归 T7 |
| T4 | 接入 Prometheus 与端口治理 | `im-metrics`、所有 Server application、端口文档、Nacos metadata | `passing` | `mvn -q -pl im-plugin/im-metrics,im-plugin/im-nacos,im-test/im-architecture-test -am test` 通过；四个安全 owner 模块测试通过；`verify.sh startup` 10/10；四 UI 99 tests 与 Management UI Harness 通过；spec/code-quality Review Approved | Actuator 匿名抓取由安全 owner 显式放行，management port 必须网络隔离 |
| T5 | 接入 SkyWalking 日志 Trace | 新增 `im-tracing`、logback include、各 Server logback | `passing` | Logback/Trace toolkit dependency tree 为 9.7.0；`TracingUtils.currentTraceId()` 无 Agent 降级和真实 Logback layout/converter/override 测试通过；十 Server 资源契约通过；`verify.sh startup` 10/10 且输出 `%tid`/MDC 双字段 | 无 Agent 时代码入口和 `%tid` 均提供确定性降级，Agent 真实关联归 T6 |
| T6 | 本地 infra/full 编排 | `deploy/local`、Dockerfile、Prometheus/Grafana/SkyWalking、本地脚本 | `passing` | infra/full config、隔离 MySQL init、真实双向切换、端口与注册释放、精确服务集合、full 19/19、Prometheus 11/11、Grafana/Horizon/OAuth/OAP Trace 均通过 | full 最终保持运行；命名 volume 保留 |
| T7 | 集成验证与收尾 | quick、clean、full、infra compose、文档和临时文件归类 | `passing` | affected/quick/startup/e2e/clean/full/quality 均重新通过；Harness 报告为 `passed`、score 100、1061 tests、0 failure、0 error，E2E fresh，十二个模块质量等级均为 A（12/12） | 首次关闭审计失败已按规则恢复 active 并修正；再次归档后刷新关闭审计证据 |

## 文件清单

所有 Server POM、运行配置和 T5 目标日志文件使用以下准确路径；任务小节中的“十个应用”均指此表，不再依赖目录猜测。

| 应用 | POM | application.yml | logback-spring.xml 目标 |
|---|---|---|---|
| HTTP Gateway | `im-gateway/im-http-gateway/pom.xml` | `im-gateway/im-http-gateway/src/main/resources/application.yml` | `im-gateway/im-http-gateway/src/main/resources/logback-spring.xml` |
| WS Gateway | `im-gateway/im-ws-gateway/im-ws-gateway-server/pom.xml` | `im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/resources/application.yml` | `im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/resources/logback-spring.xml` |
| Broker | `im-broker/im-broker-server/pom.xml` | `im-broker/im-broker-server/src/main/resources/application.yml` | `im-broker/im-broker-server/src/main/resources/logback-spring.xml` |
| Account | `im-service/im-account/im-account-server/pom.xml` | `im-service/im-account/im-account-server/src/main/resources/application.yml` | `im-service/im-account/im-account-server/src/main/resources/logback-spring.xml` |
| Social | `im-service/im-social/im-social-server/pom.xml` | `im-service/im-social/im-social-server/src/main/resources/application.yml` | `im-service/im-social/im-social-server/src/main/resources/logback-spring.xml` |
| Message | `im-service/im-message/im-message-server/pom.xml` | `im-service/im-message/im-message-server/src/main/resources/application.yml` | `im-service/im-message/im-message-server/src/main/resources/logback-spring.xml` |
| IAM | `im-management/im-iam/im-iam-server/pom.xml` | `im-management/im-iam/im-iam-server/src/main/resources/application.yml` | `im-management/im-iam/im-iam-server/src/main/resources/logback-spring.xml` |
| Audit | `im-management/im-audit/im-audit-server/pom.xml` | `im-management/im-audit/im-audit-server/src/main/resources/application.yml` | `im-management/im-audit/im-audit-server/src/main/resources/logback-spring.xml` |
| Admin | `im-management/im-admin/pom.xml` | `im-management/im-admin/src/main/resources/application.yml` | `im-management/im-admin/src/main/resources/logback-spring.xml` |
| Monitor | `im-management/im-monitor/pom.xml` | `im-management/im-monitor/src/main/resources/application.yml` | `im-management/im-monitor/src/main/resources/logback-spring.xml` |

五个数据库 Server 的 Sharding 规则文件分别为 Account、Social、Message、IAM、Audit 上述 application 路径同目录下的 `im-sharding.yml`；原始 DDL 分别为对应 Server 的 `sql/ddl.sql`。

## 详细任务

### T1 固化计划、端口与设计事实

输入与边界：以本次 `$grill-me` 的逐项选择为输入，只固化已经确认的工程决策，不提前实现运行代码。

文件范围：`docs/design-docs/2026-09-11-observability-sharding-runtime-design.md`、`docs/design-docs/index.md`、`docs/design-docs/TEMPLATE.md`、`docs/exec-plans/active/2026-09-11-observability-sharding-runtime.md`、`docs/exec-plans/active/README.md`、`docs/exec-plans/completed/2026-09-10-harness-handoff-startup-and-quality.md`、`docs/exec-plans/completed/README.md`、`docs/exec-plans/TEMPLATE.md`、`docs/PLANS.md`、`docs/references/HARNESS_GUIDE.md`、`scripts/check-drift.sh`、`scripts/test-harness.sh`、`AGENTS.md`、`PROGRESS.md`。

- [x] 创建本计划和对应设计文档，记录目标、非目标、版本选择、模块所有权与回滚风险。
- [x] 固定十个 Spring Boot Server 的业务端口和 management port 映射，management port 统一为业务端口加 1000。
- [x] 固定 `im-sharding.yml`、`im-tracing`、`deploy/local/compose.yml`、`infra/full` profile 等命名和职责。
- [x] 将计划登记到 `PROGRESS.md` 和文档索引，并归档已完成且不再活跃的旧计划。
- [x] 增加 active plan 设计链接、任务详情、checkbox 和任务 ID 一致性 RED/GREEN fixture；运行 `env SQL_HARNESS_ROOT="$PWD/im-plugin" ./scripts/test-harness.sh`，预期通过。

完成条件：设计文档可独立还原全部确认决策；active plan 与 `PROGRESS.md` 只有一个当前任务；`./scripts/verify.sh readiness` 通过。

### T2 引入乐观锁字段和 MyBatis-Plus 插件

输入与边界：所有业务表使用统一 `version BIGINT NOT NULL DEFAULT 0`；不引入 Flyway/Liquibase，不改变领域对象语义。

文件范围：`im-plugin/im-datasource/pom.xml`、`im-plugin/im-datasource/src/main/java/com/co/kc/imchat/plugin/datasource/ImDatasourceAutoConfiguration.java`、`im-plugin/im-datasource/src/main/java/com/co/kc/imchat/plugin/datasource/dao/BaseEntity.java`、`im-plugin/im-datasource/src/test/java/com/co/kc/imchat/plugin/datasource/ImDatasourceAutoConfigurationTest.java`、`im-plugin/im-datasource/src/test/java/com/co/kc/imchat/plugin/datasource/dao/BaseEntityTest.java`、`im-service/im-account/im-account-server/sql/ddl.sql`、`im-service/im-social/im-social-server/sql/ddl.sql`、`im-service/im-message/im-message-server/sql/ddl.sql`、`im-management/im-iam/im-iam-server/sql/ddl.sql`、`im-management/im-audit/im-audit-server/sql/ddl.sql`、`.tmp_add_version_and_sharding_ddl.sql`。

- [x] 先为 `BaseEntity.version` 和 MyBatis-Plus interceptor 注册顺序增加聚焦测试，并确认测试在实现前失败。
- [x] 在 `BaseEntity` 增加 `Long version`、`@Version` 与 `@TableField("version")`，默认值为 `0L`。
- [x] 在 `im-datasource` 统一注册 optimistic locker、block attack 和 MySQL pagination interceptor，并保持应用自定义 Bean 可覆盖。
- [x] 修改 Account、Social、Message、IAM、Audit 原始 DDL，为每张业务表增加 `version`。
- [x] 创建并执行 `.tmp_add_version_and_sharding_ddl.sql`，承载本次人工执行的 version ALTER 和 Message 分表迁移 DDL；用户确认使用完成后已删除，不作为长期迁移方案，也不进入最终提交。
- [x] 运行 `mvn -q -pl im-plugin/im-datasource -am test` 和 `./scripts/test-sql-harness.sh`，预期通过。
- [x] 增加真实数据库连续更新与冲突测试，确保持久化 version 不在 Entity/Domain 转换后重置；公共 MyBatis 层不统一抛异常，Repository 只在写成功后回填技术状态，完整聚合写冲突中断后续副作用，允许无匹配的 bulk/remove 保留自身语义。
- [x] Review 事务失败后的对象生命周期：异常继续离开事务边界且当前调用方不复用失败聚合，因此不引入内存状态回滚组件；重试前必须通过 Repository 重新加载。
- [x] 全量扫描生产 Repository，将 Entity 到 Domain 的业务字段与 `pkId/rowVersion` 重建收敛到 Domain Transformer；新增架构门禁，并将权限新增和角色权限关系改为批量插入，保留逐条乐观锁更新检查。

完成条件：`BaseEntity` 契约测试和 `im-datasource` 测试通过；SQL harness 通过；原始 DDL、Entity 继承关系与执行后删除的临时 DDL 已验证一致。

### T3 接入 ShardingSphere 配置

输入与边界：所有数据库 Server 进入同一 ShardingSphere 数据源管线；只有 Message 四张表真实分 8 表，其余模块使用 single rule。

文件范围：`pom.xml`、`im-plugin/im-datasource/pom.xml`、`im-plugin/im-datasource/src/main/java/com/co/kc/imchat/plugin/datasource/ImDatasourceAutoConfiguration.java`、`im-plugin/im-datasource/src/test/java/com/co/kc/imchat/plugin/datasource/ImDatasourceAutoConfigurationTest.java`；文件清单中 Account、Social、Message、IAM、Audit 的 `application.yml` 及其同目录 `im-sharding.yml`；`im-service/im-account/im-account-server/src/test/java/com/co/kc/imchat/service/account/ImAccountApplicationTest.java`、`im-service/im-account/im-account-server/src/test/java/com/co/kc/imchat/service/account/AccountConfigTest.java`、`im-service/im-social/im-social-server/src/test/java/com/co/kc/imchat/service/social/ImSocialApplicationTest.java`、`im-service/im-social/im-social-server/src/test/java/com/co/kc/imchat/service/social/SocialConfigTest.java`、`im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/ImMessageApplicationTests.java`、`im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/MessageConfigTest.java`、`im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/support/ImChatSpringBootTest.java`、`im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/infrastructure/config/IamDatasourceConfigTest.java`、`im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/infrastructure/config/AuditDatasourceConfigTest.java`；`im-service/im-message/im-message-server/sql/ddl.sql`；`im-test/im-architecture-test/pom.xml`、`im-test/im-architecture-test/src/test/java/com/co/kc/imchat/architecture/ShardingConfigurationTest.java`；`.tmp_add_version_and_sharding_ddl.sql`。

- [x] 通过 dependency tree 证明 ShardingSphere JDBC 5.5.2 生效且没有旧版混入。
- [x] 为 `ImDatasourceAutoConfiguration` 增加条件化 ShardingSphere `DataSource` 创建，保证它先于 Spring 默认数据源自动配置，并在应用自定义 `DataSource` 时退让。
- [x] Account、Social、Message、IAM、Audit 的 `application.yml` 只配置 `enabled` 与 `classpath:im-sharding.yml`。
- [x] 为五个数据库 Server 创建独立 `im-sharding.yml`；连接属性使用 5.5.x 扁平数据源结构，非 Message 模块显式使用 `!SINGLE`。
- [x] Message 对 `db_im_private_chat`、`db_im_group_chat`、`db_im_private_inbox_message`、`db_im_group_inbox_message` 配置 `user_id` inline 分片，物理表后缀为 `_0` 至 `_7`。
- [x] 运行仓库级配置契约测试，直接解析五份真实 YAML 并断言规则、分片键、节点数量和算法表达式；启动烟测通过测试属性关闭外部数据库连接，只验证应用上下文。
- [x] 生成 Message 物理分表 DDL 和现有逻辑表迁移 DDL，并由 T6 的 MySQL init 快照复用。
- [x] 删除五个数据库 Server `application.yml` 中重复且不会生效的 `spring.datasource` 配置；运行 `mvn -q -pl im-test/im-architecture-test -am -Dtest=ShardingConfigurationTest -DfailIfNoTests=false -Dsurefire.failIfNoSpecifiedTests=false test`，预期通过。
- [x] 运行 `mvn -q -pl im-plugin/im-datasource,im-service/im-account/im-account-server,im-service/im-social/im-social-server,im-service/im-message/im-message-server,im-management/im-iam/im-iam-server,im-management/im-audit/im-audit-server -am test`，预期五个数据库 Server 和 datasource 测试通过。
- [x] 保留 Message 四张分片表数据库自增技术主键和已有业务全局 ID；所有更新条件必须包含 `user_id`，新增跨分片同 ID 或广播更新隔离测试。
- [x] 五份 `im-sharding.yml` 使用 `${im.datasource.sharding.sql-show:true}`，本地默认开启 SQL 日志；Spring 只解析 `im.datasource.sharding.*`，保留 ShardingSphere inline 表达式，生产由 Nacos 覆盖为 `false`。
- [x] 删除 Message 的 MyBatis `StdOutImpl` 旁路，确保生产关闭 `sql-show` 后不会继续向 stdout 输出 SQL 与参数。

完成条件：配置契约测试在 ShardingSphere 5.5.2 下通过；`im-datasource` 和五个数据库 Server 的聚焦测试通过；原始 DDL 与临时迁移 DDL 覆盖全部物理表。真实 MySQL 建表和路由归 T6 的 infra 运行验证。

### T4 接入 Prometheus 与端口治理

输入与边界：所有 Spring Boot Server 接入 Actuator/Prometheus；management endpoint 使用独立端口，Nacos 继续只注册业务端口。

文件范围：`im-plugin/im-metrics/pom.xml`、`im-plugin/im-metrics/README.md`、`im-plugin/im-metrics/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`、`im-plugin/im-metrics/src/test/java/com/co/kc/imchat/plugin/metrics/ImMetricsAutoConfigurationTest.java`、`im-plugin/im-metrics/src/test/java/com/co/kc/imchat/plugin/metrics/ImMetricsManagementEndpointTest.java`；文件清单中十个应用的 POM 与 `application.yml`；`im-plugin/im-nacos/src/main/resources/META-INF/config/im-nacos.yml`、`im-plugin/im-nacos/README.md`；`im-test/im-architecture-test/src/test/java/com/co/kc/imchat/architecture/ObservabilityConfigurationTest.java`、`im-test/im-architecture-test/src/test/java/com/co/kc/imchat/architecture/RuntimeDependencyPolicyTest.java`；IAM SDK、Admin、Audit、IAM Server 的安全配置；HTTP/WS/Broker 的配置属性与 ConfigTest；Admin/Audit/IAM/Monitor 的 IAM ConfigTest；四个管理 UI 的 `vite.config.ts`、`src/config/consoleLinks.ts` 与端口断言测试；`scripts/check-management-ui.sh`、`scripts/test-management-ui-harness.sh`；`.tmp_add_version_and_sharding_ddl.sql`；本次更新端口的根及模块 README。

附属文件准确路径：`im-gateway/im-http-gateway/src/test/java/com/co/kc/imchat/gateway/http/HttpGatewayEndpointConfigTest.java`；`im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/java/com/co/kc/imchat/gateway/ws/config/properties/GatewayProperties.java`、同模块 `src/test/java/com/co/kc/imchat/gateway/ws/config/WsConfigTest.java`；`im-broker/im-broker-server/src/main/java/com/co/kc/imchat/broker/config/properties/BrokerManagementProperties.java`、同模块 `src/test/java/com/co/kc/imchat/broker/config/BrokerConfigTest.java`、`src/test/java/com/co/kc/imchat/broker/config/properties/BrokerManagementPropertiesTest.java`；`im-management/im-admin/src/test/java/com/co/kc/imchat/management/admin/AdminIamClientConfigTest.java`；`im-management/im-audit/im-audit-server/src/test/java/com/co/kc/imchat/management/audit/AuditIamClientConfigTest.java`；`im-management/im-iam/im-iam-server/src/test/java/com/co/kc/imchat/management/iam/infrastructure/config/properties/IamAuthorizationPropertiesTest.java`；`im-management/im-monitor/src/test/java/com/co/kc/imchat/management/monitor/MonitorIamClientConfigTest.java`。UI 修改路径为四个 UI 各自的 `vite.config.ts`、`src/config/consoleLinks.ts`、`tests/App.spec.ts`，以及 IAM `tests/ApplicationDetailView.spec.ts`、Monitor `tests/Views.spec.ts`。README 修改路径为 `README.md`、Broker/WS Gateway/Account/Social/Message Server README、IAM Server/SDK README、Audit Server README、Admin README、Monitor README、`docs/design-docs/2026-09-11-observability-sharding-runtime-design.md` 与 `docs/references/HARNESS_GUIDE.md`。

- [x] 扩展 `im-metrics`，集中声明 Actuator 和 Prometheus registry；由 IAM SDK、Admin、Audit、IAM Server 等安全所有者显式放行 Actuator，并以真实随机双端口测试证明 Prometheus 200、业务端点 403，`im-metrics` 本身不声明安全链。
- [x] 让十个 Server 显式依赖 `im-metrics`，并统一配置 `health,info,prometheus,metrics` 与 `health.show-details=never`。
- [x] 按设计表整理业务端口，设置独立 management port；更新运行配置、OAuth 回调、管理 UI、测试 fixture 和当前 README 中的旧端口。
- [x] 在 `im-nacos` 公共 metadata 中暴露 management port，并保持 Broker/WS 显式注册业务协议端口。
- [x] 增加配置契约测试，检查十个 Server 的端口唯一性、号段、management 差值、endpoint 暴露、依赖完整性和 IAM 当前 OAuth client DML。
- [x] 运行 `mvn -q -pl im-plugin/im-metrics,im-plugin/im-nacos,im-test/im-architecture-test -am test` 和 `./scripts/verify.sh startup`，预期通过。

完成条件：十个 Server 配置契约测试、`im-metrics` 测试及 startup gate 通过；不存在业务端口或 management port 冲突；`/actuator/prometheus` 由独立 management port 提供。

### T5 接入 SkyWalking 日志 Trace

输入与边界：Java Agent 在运行环境挂载，仓库只接入 9.7.0 toolkit 和日志关联；保留 MDC `traceId`。

文件范围：`pom.xml`、`im-plugin/pom.xml`、新增 `im-plugin/im-tracing/pom.xml`、`im-plugin/im-tracing/README.md`、`im-plugin/im-tracing/src/main/resources/com/co/kc/imchat/plugin/tracing/logback-common.xml`、`im-plugin/im-tracing/src/test/java/com/co/kc/imchat/plugin/tracing/ImTracingLogbackTest.java`；文件清单中十个应用的 `logback-spring.xml`；`im-test/im-architecture-test/src/test/java/com/co/kc/imchat/architecture/ObservabilityConfigurationTest.java`。

- [x] 新建 `im-plugin/im-tracing` 并登记到聚合 POM、dependency management；模块不依赖任何业务或运行 Server。
- [x] 在 `im-tracing` 声明 `apm-toolkit-logback-1.x:9.7.0`，提供可被 Logback include 的公共资源。
- [x] 公共日志片段注册 `%tid` converter，并将基础 pattern 固定为同时包含 `[%tid] [%X{traceId}]`。
- [x] 为十个 Server 保留各自 `logback-spring.xml`，通过 include 引入公共片段；Message 保留现有 rolling file appender 并使用公共 SkyWalking layout/pattern。
- [x] 增加资源契约和 Logback 加载测试，验证无 Agent 时输出 `[TID: N/A]` 且应用仍可启动；Agent TraceId 的真实关联由 T6 本地 SkyWalking Agent 运行验证。
- [x] 运行 `mvn -q -pl im-plugin/im-tracing,im-test/im-architecture-test -am test` 和 `./scripts/verify.sh startup`，预期通过。

完成条件：`im-tracing` 测试、十个 Server 日志资源契约和 startup gate 通过；Agent 二进制未进入仓库；服务可局部覆盖 appender、level 和 pattern。

### T6 本地 infra/full 编排

输入与边界：本地开发提供一次性全量启动，也允许单独在 IDE 启动某个服务；Compose 不是生产部署定义。

文件范围：新增 `deploy/local/compose.yml`、`deploy/local/app.Dockerfile`、`deploy/local/app.Dockerfile.dockerignore`、`deploy/local/README.md`、`deploy/local/mysql/init/ddl/01-account.sql`、`deploy/local/mysql/init/ddl/02-social.sql`、`deploy/local/mysql/init/ddl/03-message.sql`、`deploy/local/mysql/init/ddl/04-iam.sql`、`deploy/local/mysql/init/ddl/05-audit.sql`、`deploy/local/prometheus/prometheus-infra.yml`、`deploy/local/prometheus/prometheus-full.yml`、`deploy/local/grafana/provisioning/datasources/prometheus.yml`、`deploy/local/grafana/provisioning/dashboards/dashboards.yml`、`deploy/local/grafana/provisioning/dashboards/im-chat-overview.json`、`scripts/local_up.sh`、`scripts/local_down.sh`、`scripts/test-local-compose-harness.sh`、`scripts/test-harness.sh`、十个 Server POM 的 `spring-boot-maven-plugin` classifier、`im-plugin/im-bolt` 的 server bind host 配置、WS Gateway 的 Netty bind host 配置与测试、IAM SDK `IamProperties`、IAM HTTP 客户端配置及测试、Audit Introspection 配置及测试、`im-test/im-architecture-test/src/test/java/com/co/kc/imchat/architecture/LocalShardingRoutingIntegrationTest.java`、`README.md`。

bridge 补充文件范围：新增 `deploy/local/prometheus/prometheus-infra.yml` 与 `deploy/local/prometheus/prometheus-full.yml`；`deploy/local/mysql/init/00-initialize.sql`、`dml/06-iam-seed.sql` 和 IAM `sql/dml.sql`；Broker management properties、`application.yml` 与对应测试；IAM SDK `IamCsrfTokenRepository`、`ImIamSdkAutoConfiguration`、`IamProperties` 及其测试；Admin、Audit、Monitor 的 `application.yml`、README、UI `src/api/http.ts` 与测试；Grafana datasource、根 README 和本地编排 README。

- [x] 创建 `deploy/local/compose.yml`，`infra` profile 包含 MySQL、Redis、Nacos、Kafka、Prometheus、Grafana、BanyanDB、SkyWalking OAP/Horizon UI，版本固定、宿主端口仅绑定 loopback，且持久状态使用命名 volume。
- [x] 模块 SQL 固定为 `sql/ddl.sql` 与可选 `sql/dml.sql`，交付 DDL 使用 `CREATE TABLE IF NOT EXISTS` 并删除 `DROP TABLE`；Compose init 按 `ddl/`、`dml/` 分层并由 `00-initialize.sql` 原生 `SOURCE` 先 DDL 后 DML，六个快照与所属模块 SQL 字节一致。
- [x] 启动本地 MySQL 后确认五个 Schema 和全部 32 张 Message 物理表存在；运行 `LocalShardingRoutingIntegrationTest`，证明 `user_id=9` 经逻辑表写入 `_1` 并自动清理。
- [x] 配置 Prometheus 十个 management scrape targets、Grafana datasource/dashboard provisioning，以及 SkyWalking OAP/Horizon UI 环境变量和健康检查。
- [x] 创建通用 `deploy/local/app.Dockerfile`；十个 Server 生成保留 thin jar 的 `-exec.jar`，full profile 定义 `im-chat/<artifactId>:local`、Java Agent 和运行参数；十个镜像已在本机完成构建。
- [x] 创建 `scripts/local_up.sh`：默认 `infra`，`full` 模式先 Maven clean package 再启动全部应用，两种模式等待健康并传播失败；创建 `scripts/local_down.sh`：默认保留 volume，只有 `--volumes` 删除本计划命名的数据卷。
- [x] 将 infra/full 改为 bridge 网络并显式映射宿主 loopback 端口；bind、advertised、registry 和 dependency 地址分离，Nacos/Dubbo 自动发布容器 IP，WS/Broker 发布服务名。
- [x] 为 infra/full 提供独立 Prometheus 服务与配置：infra 在 Docker Desktop 抓 `host.docker.internal` IDE 服务，full 抓 Compose 服务名；Grafana 统一读取网络别名 `prometheus`；切换 full 时移除 infra Prometheus，切换 infra 时移除 full Prometheus 和十个应用并等待注册注销，Harness 和真实运行覆盖双向切换及精确服务集合。
- [x] IAM 分离浏览器 issuer 与内部 `service-uri`，内部 URI 默认回退 issuer 并保留 HTTPS 边界，覆盖 SDK 与 Audit Introspection；Admin/Audit/Monitor 使用独立 Session/CSRF Cookie。
- [x] 启动前预检目标 profile 的全部 infra 和应用端口；当前 Compose 项目已拥有的映射可复用，其他监听进程或 Docker 项目冲突时快速失败。
- [x] 将 BanyanDB 服务命名为 `skywalking-banyandb`，并用显式 loopback 节点地址修复 Docker Desktop arm64 下 property schema 自连接超时；Harness 已覆盖该参数。
- [x] 以隔离的新 MySQL volume 验证 `00-initialize.sql`，确认五个 Schema、32 张 Message 物理分表、四个 IAM 应用和六个 OAuth client；验证后删除隔离容器与 volume。
- [x] full 应用统一增加 `-XX:-UseContainerSupport`，规避 SkyWalking Agent 9.7.0 JVM 指标线程与 JDK 21 容器指标类加载死锁；显式 JVM 和容器内存上限保持不变。
- [x] Audit、Admin、Monitor 在 full 中使用 `10s` IAM read timeout，吸收十应用并发冷启动压力；SDK 的生产默认 `2s` 保持不变。
- [x] 验证 infra 9/9、full 19/19 健康；Nacos 18 个注册条目均有健康实例，HTTP Gateway 实际转发 Account，三个 OAuth 登录入口生成正确 client/callback，Prometheus 11/11 target 为 `up`，Grafana datasource/dashboard、Horizon 和 OAP Trace 均可查询。
- [x] 运行 `./scripts/test-local-compose-harness.sh`、`docker compose -f deploy/local/compose.yml --profile infra config`、`./scripts/local_up.sh infra` 和 `./scripts/local_up.sh full`；真实双向切换成功，最终保留 full 运行且命名 volume 未删除。

完成条件：infra 与 full 配置可解析，双向 profile 切换不会并发运行两个 Prometheus 或挂载同一 TSDB；实际启动十个应用并全部健康；Nacos、Dubbo、Bolt、HTTP Gateway 和 OAuth 登录路径可用；Prometheus targets、Grafana datasource、Horizon UI 与至少一条 SkyWalking Agent Trace 可查询；脚本参数、端口预检、退出码、副作用和清理语义有中文注释及 README 说明。

### T7 集成验证与收尾

输入与边界：只收尾本计划创建的变更，不清理或提交用户的无关工作；没有用户明确指令时不创建 Git commit。

文件范围：T1-T6 文件范围和文件清单中列出的全部路径；`README.md`、`im-plugin/im-datasource/README.md`、`im-plugin/im-metrics/README.md`、`im-plugin/im-tracing/README.md`、`im-service/im-message/im-message-server/README.md`、`deploy/local/README.md`、`docs/design-docs/index.md`、`docs/exec-plans/active/README.md`、`docs/exec-plans/completed/README.md`、`PROGRESS.md` 和 `.harness` 派生证据。

Architecture 判定：本计划只改变数据库物理分表、运行配置和本地基础设施，不改变 `ARCHITECTURE.md` 及局部 Architecture 拥有的运行拓扑、模块边界、数据所有权或一致性语义，因此不计划修改 Architecture 文件；T7 Review 负责再次确认该判断。

- [x] 从聚焦测试向外依次运行 datasource、metrics、tracing、数据库 Server、management UI、architecture、startup 和适用的 E2E 验证。
- [x] 运行当前工作树的 `./scripts/verify.sh affected`、`architecture`、`startup`、`e2e`，逐项归因任何失败。
- [x] 在临时 DDL 删除后的最终工作树重新运行 `./scripts/verify.sh quick`、`clean`、`full`，三项均通过。
- [x] `.tmp_add_version_and_sharding_ddl.sql` 已完成临时用途并删除，不进入长期 SQL 结构或 Git 交付。
- [x] 更新文件范围列出的 README、设计索引、active plan 和 `PROGRESS.md`，并 Review 确认 Architecture 判定仍成立，确保配置名、端口、启动方式及限制与代码一致。
- [x] 按独立上下文完成最终 spec review 和 code-quality review；profile 切换等待、Harness 精确服务集合、DDL 幂等性、Audit 状态回填和 quality scope 缺口均修复并复审通过。
- [x] 所有任务通过后移动计划到 completed，更新索引，并执行归档后关闭审计。
- [x] 运行 `./scripts/verify.sh affected`、`./scripts/verify.sh quick`、`./scripts/verify.sh startup`、`./scripts/verify.sh e2e`、`./scripts/verify.sh clean`、`./scripts/verify.sh full` 和 `./scripts/verify.sh quality`；全部通过，无门禁例外。

完成条件：所有适用验证有当前工作树证据；未验证的真实生产环境路径被明确列为残余风险；临时文件已按用户要求删除；计划归档状态与 `PROGRESS.md` 一致。

## 恢复状态

- 当前任务：无；T1-T7 均为 `passing`，计划已完成并归档。
- 已完成：infra 9/9、full 19/19 健康，五个 Schema/32 张 Message 物理表和真实路由测试通过，Nacos、HTTP Gateway、OAuth、Prometheus、Grafana、Horizon 与 SkyWalking Agent Trace 均有真实运行证据；profile 切换会等待应用端口及 Nacos/Dubbo 实例释放，并验证精确运行服务集合；首次关闭审计发现的 `PROGRESS.md` 规范标记问题已修正，最终仓库门禁和独立质量评审通过。
- 阻塞项：无外部阻塞。
- 下一步：无；归档后关闭审计负责刷新最终机器证据。
- 不要修改：业务领域模型语义、Facade/SDK 公共契约语义、旧 completed plan 历史证据文本。

## 回滚与残余风险

- 回滚：移除新增 `im-tracing` 模块、Actuator/Prometheus 依赖与 endpoint 配置、ShardingSphere 开关和 `im-sharding.yml`、Compose 与脚本、`version` 字段 Entity/DDL 变更；已部署数据库需要由发布流程提供与目标版本匹配的反向 DDL，不依赖已删除的本地临时文件。
- 未验证路径：真实生产 MySQL/Nacos/Kafka/Redis/Prometheus/Grafana/SkyWalking 集群部署和生产抓取权限；`full` compose 全量应用长期运行稳定性。
- 已接受但需跟踪的风险：Message 分表数首版为 8，后续扩到 16/32 仍需要 DDL 与数据迁移；health details 默认不暴露，详细诊断依赖日志、Grafana、SkyWalking 或临时配置。
