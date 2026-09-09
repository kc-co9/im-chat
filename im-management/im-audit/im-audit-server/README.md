# IM Audit Server

集中管理审计服务，负责接收、幂等追加、查询和导出 BUSINESS/SECURITY 审计事实。
审计记录以 `auditId` 去重，只提供追加和读取能力；标准 Entity 的更新时间和逻辑删除字段
保留但不参与审计业务。

## 接收方式

- HTTP：`POST /internal/audits`，调用方必须使用 IAM Client Credentials 获取仅含
  `audit:ingest` Scope 且 `aud` 包含 `imAudit` 的 Opaque Access Token。服务端复用 IAM SDK
  的通用应用身份转换器，并以 Token 的 `appKey` 为可信来源，
  请求体不提供可覆盖该身份的来源字段。
- Kafka：每个来源使用独立 Topic 和生产凭据。Binder 在处理成功或确认重复后提交消费位点，
  消费 Binding 注入固定的可信来源。持久化异常最多尝试三次，之后进入来源专属 DLQ。

生产 ACL 必须限制为来源应用只能写自己的 Topic，Audit 服务只能读业务 Topic并写对应 DLQ：

| 来源 | Topic | DLQ |
| --- | --- | --- |
| `imAdmin` | `im.audit.im-admin.v1` | `im.audit.im-admin.v1.dlq` |
| `imIam` | `im.audit.im-iam.v1` | `im.audit.im-iam.v1.dlq` |
| `imMonitor` | `im.audit.im-monitor.v1` | `im.audit.im-monitor.v1.dlq` |
| `imAudit` | `im.audit.im-audit.v1` | `im.audit.im-audit.v1.dlq` |

HTTP 与 Kafka 不互相回退。数据库、IAM Introspection、Kafka Binder 和生产凭据由 Nacos/
运行环境覆盖，仓库配置不保存真实密钥。

## 查询、权限与导出

Audit 作为独立 IAM 浏览器应用运行，使用 `appKey=imAudit`。`audit:read` 允许查询 BUSINESS/
SECURITY 列表和详情，`audit:export` 额外允许导出。查询和导出都要求明确的起止时间；Excel
导出时间跨度不超过 31 天，并以 100 行批次持续读取和写入。导出内容不包含
数据库主键、逻辑删除字段或内部错误详情，对可能触发电子表格公式的文本进行安全处理。

浏览器查询参数和响应中的绝对时间使用毫秒级 Unix 时间戳，服务内部与 Java SDK 契约继续
使用 `Instant`，数据库 `occurred_at` 使用 `TIMESTAMP(3)`。页面按浏览器 IANA 时区显示为
`yyyy-MM-dd HH:mm:ss`；Excel 导出请求必须携带浏览器 IANA 时区，并按相同格式生成时间列，
不使用 Audit Server 所在机器的默认时区。

UI 的 wire model 按 `im-web` 规则以十进制字符串接收 Java `Long`，在格式化时间和分页计算前
显式校验并转换。页面使用 Hash Router，Axios 统一拆解 `HttpResult` 并为退出等 POST 请求提交
同源 CSRF Token。会话失效重新登录时保留当前 Hash 路由；空白筛选文本在应用 Query 边界
归一化为未传条件，不构造空值领域对象。

Audit Server 自身的导出动作会发布一条 `AUDIT_EXPORT` 审计事实；内部接收端点不使用
`@Audited`，避免审计投递递归。

权限目录使用独立的 `im-audit-catalog` 机器客户端同步。OpenAPI 页面为 `GET /api/doc.html`，
API description 为 `GET /v3/api-docs`，两者均沿用 IAM Session 认证。

## 运行验证

执行模块根目录 [`sql/ddl.sql`](sql/ddl.sql) 创建 `im_chat_audit` 及审计表，并在部署配置中覆盖数据库、Kafka、IAM issuer/client、
Session 加密密钥和回调地址。服务默认端口为 `18091`。

四个管理端使用一致的 Element Plus 高密度运维控制台：页面只展示本应用拥有的功能。
“其他控制台”链接由各 UI 的 `src/config/consoleLinks.ts` 管理，本地默认地址可分别通过
`VITE_IAM_CONSOLE_URL`、`VITE_AUDIT_CONSOLE_URL`、`VITE_MONITOR_CONSOLE_URL` 和
`VITE_ADMIN_CONSOLE_URL` 在构建时覆盖。创建、编辑和详情使用右侧抽屉，危险操作必须显式确认，
刷新失败时保留最近一次成功数据。

```bash
mvn -q -pl im-management/im-audit/im-audit-server -am test
cd im-management/im-audit/im-audit-server/ui
npm run lint && npm run format:check
npm run typecheck && npm run test:unit && npm run build
```
