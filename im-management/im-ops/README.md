# IM Ops

learner 在途重复响应 `IN_PROGRESS` 原样透传，不跟随 Leader 或自动重试；它不证明 Raft 已登记，acceptedEpoch/targetEpoch 可为空。与 APPLIED/DUPLICATE 的登记语义区分，调用方保留原 ID 稍后查询或重发原记录。Broker/Ops/CLI 成组部署；旧 Ops primitive epoch 不保证接纳空值。

`im-ops` 是独立部署的运行控制台。它通过 Nacos metadata 发现 Broker 管理 HTTP 端点，聚合诊断，并向 Broker Leader 提交受控集群 operation。

本模块不直接修改 Broker 文件或保存第二份集群状态，也不提供全量用户路由或业务持久化。浏览器只访问本应用，不直接连接 Broker；管理员身份和应用级 RBAC 由 IAM 提供，操作结果通过 Audit SDK 提交集中审计。

内部模型按边界隔离：Broker HTTP 载荷只存在于 `infrastructure/client/model`，Adapter 将其转换为
`model/cqrs/dto` 下的应用投影，Controller 再通过 HTTP Transformer 生成 `model/io` Response。
Broker HTTP 与 Ops 浏览器接口的绝对时间统一为 `Long xxxAtMs`（Unix epoch milliseconds），
JSON wire 为十进制字符串，可空时间保持 null；耗时和运行时长使用同样的 string wire
`durationMs/uptimeMs`。Broker payload 在客户端 Transformer 边界转为 Ops 内部 `Instant`/`Duration`，
HTTP Transformer 再转换为 Long Response，不复用 Broker 协议对象。前端按 string 接收，
显示前验证十进制格式及 JavaScript 安全整数范围，再按浏览器 IANA 时区格式化绝对时刻；
耗时以毫秒呈现，uptime 从毫秒换算展示，不由服务端固定展示时区。

## 运行链路

```text
browser --/api--> im-ops --DiscoveryClient--> Nacos im-broker instances
   `--IAM BFF Session/live Introspection--> im-iam-server
                       |
                       `--management HTTP--> each supported Broker node
```

诊断读可访问任意节点。成员写请求的 `brokerId` 是业务目标，不要求目标已有管理 HTTP 服务；首次请求按可信发现顺序选择第一个受支持端点，跳过缺少管理地址的条目，不以接收者身份替换目标或其他业务参数。没有受支持端点时零出站写入；首次异常、超时或业务拒绝后不自动换普通节点。受支持不代表存活，首次接收者也不保证是 Leader。

写请求保持原 `operationId`、`expectedEpoch`、payload 和 scope；仅具名 `NOT_LEADER` 可触发最多一次 Leader 跟随。跟随时重新发现：旧 HTTP 提示继续要求精确可信管理 origin；Raft 提示按不透明完整字符串与同一原始发现实例的 `raft-peer-id` 精确配对，再取其显式管理 origin。不解析 PeerId，不归一化 hostname、IPv6、index 或 priority，不猜端口或尝试别名。歧义在去重前检查；缺失、非法及冲突配对拒绝，不向未验证目的地发送管理凭据，不形成代理链。解析失败时 CLI 保守返回 `UNKNOWN`，不能推断原命令未执行。

Broker 在官方首次注册前发布实际初始化 Node 的完整身份；metadata 依赖可信注册中心及部署写权限，并非 TLS 身份、committed membership 或授权证明。发现滞后或文本等价但不一致会损失可用性，不能放宽匹配。页面校验只改善体验，Broker 仍复核 epoch、状态迁移、最小 voter 数、quorum 与幂等摘要。真实三 voter 的成功／缺失／冲突旅程已有局部运行证据；完整交付仍由 T11 拥有。

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

## CLI 认证边界

`/api/cli` 与 `/api/cli/**` 使用独立无状态管理员 Bearer 链，其余路径继续原 BFF 链。CLI 每请求向 IAM Introspection，校验 `imOps` audience、可信 `ADMINISTRATOR` 主体、精确 Client 白名单、有效期及 scope，不读取浏览器 Session，也不使用 BFF 缓存／故障降级。Cookie-only 和 Cookie／Bearer 混用都拒绝；认证／授权失败返回结构化 HTTP 401／403，浏览器 BFF 与 CSRF 行为保持不变。

部署通过 typed properties `im.ops.cli.client-ids` 和 `im.ops.cli.scopes` 配置精确允许集合，可由 Nacos 覆盖。未配置时集合为空，CLI 拒绝认证，但不影响本地浏览器 BFF 启动。Client 必须先在 IAM 按 PUBLIC Client 契约注册，不能把 Web Client 或机器 Client 加入白名单来绕过主体校验。scope 必须与已批准命令及 IAM Client 授权配置一致；用例还须检查管理员 RBAC。

CLI 的五个成员写操作均必须显式携带 `membershipEndpoint`，除 `add-learner` 外可同时携带可选 `reason`；端点与原 operationId/expectedEpoch 原样传递，不能在重试时替换为当前绑定。历史身份一致的确认返回 DUPLICATE，只表示已登记，调用方查询阶段；未完成确认由 Broker 现有后台协调器恢复，重发不主动推进，不新增恢复接口。

CLI 使用固定窄入口：`GET /api/cli/cluster-state`、`GET /api/cli/operations/{operationId}` 和五个 `POST /api/cli/brokers/{brokerId}/raft/{add-learner|complete-learner-sync|promote|demote|remove}`，没有动态 action 或 force／snapshot／transfer。两个查询同时要求可信 scope 和管理员 authority 各含 `ops:broker:read`；五写各要求两者同时含 `ops:broker:raft:write`，终端自报 Header 不能补权。

CLI HTTP 使用独立 Response 与生成 Transformer，不直接序列化应用 DTO。集群查询携带实际查询节点 `sourceBroker` 及 `revision`／`epoch`，属于运行投影，不声称线性一致；提交响应不返回内部 `leaderEndpoint`。operation 查询的可空 `createdAtMs`／`completedAtMs` 使用 Unix epoch milliseconds 十进制字符串，内部仍保留 `Instant`。七个固定用例的说明由 Controller OpenAPI 注解发布；权限声明通过仅组合标准 Spring Security 注解的 `@RequiresCliPermission` 引用统一权限目录常量，不新增授权 AOP。

五个成员写操作必须显式提供 `operationId`、非负 `expectedEpoch` 和非空 `membershipEndpoint`；除 `add-learner` 外可提供 `reason`。Ops 不生成新 operationId、不刷新 epoch，Broker 仍拥有状态机和幂等不变量。operation 查询投影区分提交结果的 `status` 与持久操作的 `phase`，保留 `acceptedEpoch`／`resultEpoch`；节点不可用、服务发现失败、404 或查询失败归为 `UNKNOWN`，保留原 operationId，不能推断原请求没有提交或自动创建新操作。

CLI 操作审计复用 Audit SDK，记录本次实际管理员、可信 Introspection 的 `clientId` 和 `operationId`。其他有权限的管理员显式接续同一操作时，记录当前管理员，不声称验证了原发起人关系。审计属性不记录自由文本 reason／message、成员端点或 Leader 地址，不记录凭据；审计发布失败不覆盖原业务结果。审计 `SUCCESS` 表示 Ops 提交方法正常返回，不表示 Broker 已接受或完成操作；Broker 返回的 `REJECTED` 等业务状态独立记录在 `status` 属性中。接口实现的完整验收状态由活跃执行计划拥有，不将局部测试通过当作整体 CLI 交付。

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
