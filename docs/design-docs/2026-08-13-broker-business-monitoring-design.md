# Broker 业务监控管理接口设计

实现过程见[已完成执行计划](../exec-plans/completed/2026-08-13-broker-business-monitoring.md)。当前版本保留既有 Micrometer/JMX 指标，并用本设计的有界诊断记录补充最近 Gossip 与迁移结果；两者职责不同。

## 1. 背景

`im-broker-server` 当前通过 Bolt 承担 Broker、Gateway、用户连接注册以及消息转发，并通过 Gossip 在 Broker 节点间同步状态。Nacos 能展示服务实例是否注册，但无法回答以下业务问题：

- 当前 Broker 管理了多少 Broker、Gateway 和用户路由；
- 指定用户当前路由到哪些 Gateway；
- Gossip 最近是否同步成功、失败发生在哪个 peer；
- 连接迁移最近是否执行成功、迁移了多少路由；
- Broker 当前状态异常时应从哪里开始排查。

第一版由 Broker 提供节点级只读诊断接口，并由独立的 `im-monitor` 应用完成节点发现、集群聚合和页面展示。不引入 Prometheus、Grafana、数据库或长期审计能力。

## 2. 目标与边界

### 2.1 目标

- 使用独立 HTTP 端口提供 Broker 业务状态查询；
- 通过独立监控应用聚合多个 Broker 节点并提供监控页面；
- 提供业务总览、Broker/Gateway 明细和按用户查询连接路由；
- 在内存中保留最近 100 条 Gossip 同步和连接迁移记录；
- 管理接口与 Bolt 业务端口隔离；
- 不修改 Broker 注册、路由和 Gossip 的核心语义。

### 2.2 非目标

- 不提供管理写操作；
- 不允许获取全量用户路由快照；
- 不持久化诊断记录，Broker 重启后历史清空；
- 不在第一版提供应用层 Token、RBAC、Prometheus 指标或 Grafana 面板；
- 不在诊断记录中保存消息正文、完整异常堆栈或其他敏感载荷。
- 不将监控页面或集群聚合逻辑放入 Broker；
- 不合并管理后台与监控应用的运行进程、页面或权限边界。

## 3. 总体架构

Broker 继续使用 Spring Boot，但从纯非 Web 应用调整为可启动轻量 HTTP 管理服务。HTTP 管理端口与 Bolt 端口使用不同配置：

- Bolt 业务端口：默认 `12200`；
- HTTP 管理端口：默认 `12201`；
- HTTP 管理地址：默认 `127.0.0.1`，允许显式配置内网地址，拒绝默认绑定 `0.0.0.0`。

新增 Broker 节点级查询服务作为诊断门面。Broker Controller 不直接组合多个 Registry，也不直接读取生命周期组件，只负责请求参数校验和响应转换。

业务状态来源分为两类：

1. 当前状态：从 `BrokerRegistry`、`GatewayRegistry`、`ConnectionRegistry` 和 `BrokerProperties` 实时读取；
2. 最近执行记录：由 Gossip 和连接迁移执行点写入固定容量的内存记录器。

项目根目录新增与 `im-service`、`im-broker` 平级的 `im-management` 聚合模块：

```text
im-management
├── pom.xml
├── im-admin
│   ├── pom.xml
│   └── README.md          # 本阶段只预留所有权和 Maven 位置
└── im-monitor
    ├── pom.xml
    ├── ui
    └── src
```

- `im-management` 仅负责 Maven 模块聚合，不包含启动类；
- `im-admin` 未来作为独立业务管理应用承载业务数据管理、权限与操作审计；本阶段不创建运行代码或 UI；
- `im-monitor` 是独立的只读监控应用，负责 Broker 发现、状态聚合、故障诊断和页面展示；
- 两个应用分别构建、注册到 Nacos、启动和部署，不共享运行进程或页面资源。

## 4. 模块与职责

### 4.1 Broker 节点诊断服务

职责：

- 聚合 Broker 实例信息、启动时间和运行时长；
- 查询 Broker、Gateway 列表；
- 按 `userId` 查询用户当前所在 Gateway；
- 汇总注册表数量、Gossip 状态和迁移状态；
- 返回最近 Gossip 与迁移记录。

该服务只依赖领域接口和监控记录接口，不依赖 HTTP 类型。

### 4.2 Registry 只读能力

- `BrokerRegistry` 继续使用现有 `list()`；
- `GatewayRegistry` 增加 `list()`，用于管理接口展示当前有效 Gateway；
- `ConnectionRegistry` 增加 `count()`，用于总览统计；
- `ConnectionRegistry.find(userId)` 用于单用户诊断；
- 管理服务禁止调用 `ConnectionRegistry.list()` 返回全量路由，现有 `list()` 仅保留给内部迁移业务。

### 4.3 诊断记录器

分别维护 Gossip 和连接迁移记录，使用有界、线程安全的内存结构，默认最大容量为 100。超过容量时淘汰最早记录。

记录接口应与生命周期解耦，例如：

- `GossipSyncRecorder`：记录同步成功或失败；
- `ConnectionMigrationRecorder`：记录一次目标 Broker 迁移结果。

记录字段：

- 执行时间；
- 目标节点 ID 或地址；
- 状态：`SUCCESS`、`FAILED`；
- 处理数量；
- 耗时毫秒；
- 截断后的错误摘要。

错误摘要不得包含完整堆栈，长度应受限，避免异常内容持续占用内存。

### 4.4 状态采集点

`BrokerGossipLifecycle` 在调用 `GossipSynchronizer.syncPeer` 前记录开始时间，并在成功或异常后写入 Gossip 记录。

`BrokerConnectionService` 在每个目标 Broker 的迁移批次完成或失败后写入迁移记录。记录动作不能改变原有异常处理结果；记录器自身异常也不能中断 Gossip 或迁移流程。

### 4.5 im-monitor

`im-monitor` 不直接访问 Broker 内部 Registry，也不依赖其实现类。它通过 Nacos 获取全部 Broker 实例及其管理地址，再调用节点级诊断接口完成聚合。

后端职责划分：

- `application`：监控查询用例和多节点状态聚合；
- `infrastructure/discovery`：Nacos Broker 节点发现；
- `infrastructure/client`：Broker 管理 HTTP 客户端、超时和错误隔离；
- `interfaces/http`：向监控 UI 提供集群级 JSON API；
- `model`：集群总览、节点状态和诊断记录等展示模型。

单个 Broker 不可访问时，集群查询不能整体失败。响应应保留该节点并标记为 `UNREACHABLE`，同时返回错误摘要和最后查询时间。

### 4.6 im-admin

`im-admin` 与 `im-monitor` 同属 `im-management`，但不在本设计中实现业务后台功能。本次只预留聚合模块位置，避免后续将管理后台错误归入 `im-service` 或监控应用。

`im-admin` 与 `im-monitor` 不共享 Controller、应用服务、Session 或 UI 源码。将来确有稳定且通用的组件时再评估抽取，第一版不预建公共 UI 模块。

### 4.7 UI 与构建

两个应用各自维护一个完整的 Vue 工程，目录统一命名为 `ui`：

```text
im-admin/ui
im-monitor/ui
```

统一技术栈：

- Vue 3；
- TypeScript；
- Vite；
- Vue Router；
- Element Plus；
- ECharts；
- Axios；
- Pinia 仅在出现明确的跨页面共享状态时引入。

开发环境分别启动 Vite 与 Spring Boot，由 Vite 将 `/api` 代理到对应 Java 服务。生产构建由 Maven 自动执行前端依赖安装和 Vite 构建，产物直接进入 `target/classes/static`，最后随对应 Spring Boot JAR 发布。

UI 构建产物、`node_modules` 和 `dist` 不提交 Git，也不复制回 `src/main/resources/static`，避免污染源码目录。最终仍然只部署 `im-admin.jar` 和 `im-monitor.jar`，不单独部署前端服务。

## 5. Broker 节点 HTTP 接口

所有接口以 `/management` 为统一前缀，只提供 `GET` 请求。

### 5.1 Broker 总览

`GET /management/broker/overview`

返回：

- 当前 Broker ID、Bolt 地址、启动时间、运行时长；
- Broker、Gateway、用户路由数量；
- Gossip 累计成功/失败次数和最后成功/失败时间；
- 连接迁移累计成功/失败次数和最后执行时间。

### 5.2 Broker 列表

`GET /management/brokers`

返回当前本地注册表已知的有效 Broker，不返回 Gossip 内部原始 delta。

### 5.3 Gateway 列表

`GET /management/gateways`

返回当前本地注册表已知的有效 Gateway。

### 5.4 用户连接路由

`GET /management/connections?userId={userId}`

要求 `userId` 必填且为正数，仅返回该用户的 Gateway 路由。接口不支持空条件查询，防止误拉全量连接数据。

### 5.5 Gossip 状态与记录

- `GET /management/gossip`：返回聚合状态；
- `GET /management/gossip/records?limit={limit}`：按时间倒序返回最近记录。

`limit` 默认 20，最大 100。

### 5.6 迁移记录

`GET /management/migrations?limit={limit}`

按时间倒序返回最近迁移记录，`limit` 默认 20，最大 100。

## 6. im-monitor HTTP 接口

监控页面只访问 `im-monitor`，浏览器不得直接访问 Broker。`im-monitor` 对 UI 提供 `/api` 前缀的集群级接口：

```text
GET /api/overview
GET /api/brokers
GET /api/brokers/{brokerId}
GET /api/gateways
GET /api/connections?userId={userId}
GET /api/gossip/records?limit={limit}
GET /api/migrations?limit={limit}
```

聚合接口应为每条节点数据标明来源 Broker。用户路由查询可以并发查询可用 Broker，并对相同 Gateway 路由去重；节点超时作为部分失败返回，不应丢弃其他节点的有效结果。

## 7. 数据模型

HTTP 响应使用独立的 management DTO，不直接暴露 Registry 实现或可变集合。时间统一使用 ISO-8601 字符串或 `Instant` 的 Jackson 标准格式。

总览响应示例：

```json
{
  "broker": {
    "id": "broker-01",
    "address": "10.0.0.11:12200",
    "startedAt": "2026-08-13T02:00:00Z",
    "uptimeSeconds": 3600
  },
  "statistics": {
    "brokerCount": 3,
    "gatewayCount": 8,
    "connectionCount": 120000
  },
  "gossip": {
    "lastSuccessAt": "2026-08-13T02:59:55Z",
    "lastFailureAt": null,
    "successCount": 720,
    "failureCount": 0
  },
  "migration": {
    "lastExecutedAt": "2026-08-13T02:30:00Z",
    "successCount": 5,
    "failureCount": 1
  }
}
```

## 8. 配置

新增 Broker 管理配置：

```yaml
im:
  broker:
    management:
      host: 127.0.0.1
      port: 12201
      history-capacity: 100
```

约束：

- `port` 必须为有效端口，并且不能与 Bolt 端口相同；
- `history-capacity` 必须大于 0，第一版最大限制为 1000；
- 默认 `host` 为回环地址；生产环境需要跨主机访问时显式配置内网 IP。

`im-monitor` 使用独立配置：

```yaml
spring:
  application:
    name: im-monitor

server:
  port: 18090

im:
  monitor:
    broker:
      service-name: im-broker
      request-timeout-millis: 3000
```

Broker 注册到 Nacos 时需要通过实例 metadata 暴露管理地址或管理端口，`im-monitor` 不应根据 Bolt 端口硬编码推算管理端口。

## 9. 错误处理与安全

- 参数错误返回 HTTP 400；
- 未找到用户路由时返回 HTTP 200 和空列表，表示用户当前不在线；
- Broker 节点自身无法完成查询时返回 HTTP 500；Monitor 聚合单节点失败时标记为 `UNREACHABLE`，保留其他节点的有效数据且不伪造健康统计；
- 所有接口只读，不提供注册、注销、迁移触发或 Gossip 触发能力；
- 响应不返回消息载荷、认证信息、完整异常堆栈和全量用户路由；
- 部署层应限制管理端口仅对运维网络开放。
- Broker 管理端口只允许 `im-monitor` 所在运维网络访问；
- 浏览器只访问 `im-monitor`，不感知 Broker 的真实地址；
- `im-admin` 与 `im-monitor` 分别配置认证和权限，不复用登录态。

## 10. 测试策略

- Registry 测试：验证 Gateway `list()` 和 Connection `count()` 的并发更新结果；
- 记录器测试：验证容量淘汰、倒序查询、limit 上限和并发写入；
- Service 测试：验证总览聚合、单用户查询和空状态；
- Lifecycle/Service 测试：验证 Gossip 与迁移成功、失败均产生正确记录，且记录失败不影响原业务；
- HTTP 测试：验证接口路径、参数校验、响应结构和禁止全量连接查询；
- 配置测试：验证默认绑定地址、端口冲突和容量边界。
- 节点发现测试：验证 Nacos 实例到 Broker 管理地址的解析；
- 聚合测试：验证多 Broker 并发查询、去重、部分失败和超时隔离；
- UI 构建测试：验证 Maven 打包会生成静态资源并写入最终 JAR；
- UI 测试：验证总览、节点列表、用户路由和诊断记录页面的加载与错误状态。

## 11. 后续演进

当前 Broker 已通过 Micrometer/JMX 暴露实例、连接与 Gossip 条目指标，本设计新增的有界记录用于回答最近一次 Gossip/迁移执行结果，不替代时序指标。后续可将现有 Micrometer 指标接入 Prometheus；若需要跨重启审计，再单独设计持久化事件或日志采集，不在本次内存诊断记录上直接扩展数据库职责。
