# IM Ops

`im-ops` 是独立部署的运行控制台。它通过 Nacos metadata 发现 Broker 管理 HTTP 端点，聚合诊断，并向 Broker Leader 提交受控集群 operation。

本模块不直接修改 Broker 文件或保存第二份集群状态，也不提供全量用户路由或业务持久化。浏览器只访问本应用，不直接连接 Broker；管理员身份和应用级 RBAC 由 IAM 提供，操作结果通过 Audit SDK 提交集中审计。

内部模型按边界隔离：Broker HTTP 载荷只存在于 `infrastructure/client/model`，Adapter 将其转换为
`model/cqrs/dto` 下的应用投影，Controller 再通过 HTTP Transformer 生成 `model/io` Response。
浏览器接口中的绝对时间统一为 epoch milliseconds，页面按浏览器 IANA 时区格式化；不复用
Broker 协议对象，也不由服务端固定展示时区。

## 运行链路

```text
browser --/api--> im-ops --DiscoveryClient--> Nacos im-broker instances
   `--IAM BFF Session/live Introspection--> im-iam-server
                       |
                       `--management HTTP--> each supported Broker node
```

诊断读可访问任意节点。写请求携带同一个 `operationId` 和 `expectedEpoch`；Follower 返回 Leader 管理地址后，Ops 最多重试一次，不形成代理链。页面的按钮和状态校验只改善体验，Broker 仍会复核 epoch、状态迁移、最小 voter 数、quorum 与幂等摘要。

Broker operation 响应和 Ops 运行投影中的 Leader 地址统一使用 `leaderEndpoint` 字段，
HTTP 载荷、DTO、前端类型及单次重试读取保持同名，不保留 `leaderHint` 别名。

Broker 必须在 Nacos metadata 中显式发布 `management-host` 和 `management-port`。缺少或无效 metadata 的节点会保留在总览中并标记为 `UNREACHABLE`，Ops 不根据 Bolt 端口猜测管理端口。节点查询由固定大小线程池和有界队列并发执行，单节点超时、错误或局部调度拒绝不会丢弃其他节点的有效结果；容量完全耗尽时返回统一的资源耗尽错误。

`BrokerDiagnosticAdapter` 通过 Nacos 与管理 HTTP 适配 Broker 发现和远端诊断查询；它不是持久化
Repository，也不为单一实现额外声明 Gateway 接口。集群查询并发访问所有节点，单节点
详情只访问所选 Broker，不先执行全量集群查询。

## 配置

| 配置 | 默认值 | 说明 |
|---|---:|---|
| `server.port` | `18043` | Ops HTTP 和 UI 端口 |
| `management.server.port` | `19043` | Actuator 与 Prometheus 抓取端口 |
| `im.ops.broker.service-name` | `im-broker` | Nacos 中的 Broker 服务名 |
| `im.ops.broker.request-timeout-millis` | `3000` | 每个 Broker 管理请求的连接和读取超时 |
| `im.ops.broker.management-audience` | `im-broker-management` | Broker 管理凭证的目标 audience |
| `im.ops.broker.management-token` | 本地开发值 | Ops 到 Broker 的服务凭证；部署必须覆盖 |
| `im.ops.query.threads` | `8` | Broker 节点查询工作线程数，必须为正数 |
| `im.ops.query.queue-capacity` | `128` | 等待执行的节点查询上限，必须为正数 |

权限目录使用独立的 `im-ops-catalog` 机器客户端同步。OpenAPI 页面为 `GET /api/doc.html`，
API description 为 `GET /v3/api-docs`，两者均沿用 IAM Session 认证。

本地默认值可直接启动，部署环境可通过 Nacos 覆盖。Ops 本身不依赖 `im-broker-server` 的 DTO 或实现。

## 查询接口

```text
GET /api/overview
GET /api/brokers
GET /api/brokers/{brokerId}
GET /api/gateways
GET /api/connections?userId={positive user id}
GET /api/gossip/records?limit={1..100}
GET /api/cluster-state
POST /api/brokers/{brokerId}/cluster-state/{activate|drain|deactivate}
POST /api/brokers/{brokerId}/raft/{add-learner|complete-learner-sync|promote|demote|remove}
POST /api/brokers/{brokerId}/force-deactivate
```

写接口分别要求 `ops:broker:cluster-state:write`、`ops:broker:raft:write`、`ops:broker:force`。管理操作只接受正式提交；Ops 页面在提交前要求确认，不再提供可能过期的预检查。生产应使用 mTLS 或 OAuth2 Client Credentials 保护运维网络；仓库内 token 仅用于 loopback 本地环境，不能作为生产信任根。

集合项携带来源 Broker；同一用户的相同 Gateway 路由会去重。总览统计取健康节点所报告集群快照的最大值，避免把多个 Broker 已同步到的同一份注册表数量重复相加。

## UI 与构建

四个管理端使用一致的 Element Plus 高密度运维控制台：页面只展示本应用拥有的功能。
“其他控制台”链接由各 UI 的 `src/config/consoleLinks.ts` 管理，本地默认地址可分别通过
`VITE_IAM_CONSOLE_URL`、`VITE_AUDIT_CONSOLE_URL`、`VITE_OPS_CONSOLE_URL` 和
`VITE_ADMIN_CONSOLE_URL` 在构建时覆盖。创建、编辑和详情使用右侧抽屉，危险操作必须显式确认，
刷新失败时保留最近一次成功数据。

```bash
cd im-management/im-ops/ui
npm run dev
npm run test:unit
npm run typecheck
```

Vite 开发服务把 `/api` 代理到 `127.0.0.1:18043`。Maven 在 `package` 阶段自动执行 `npm ci` 和生产构建，产物写入 `target/classes/static` 并进入 `im-ops.jar`；不会复制到源码资源目录。

```bash
mvn -q -pl im-management/im-ops -am test
mvn -q -pl im-management/im-ops -am package
```
