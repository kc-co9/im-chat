# IM Admin

`im-admin` 是独立部署的业务管理后台。管理账号、登录、应用级角色和安全会话由
`im-iam-server` 统一管理；本模块只拥有普通用户管理及其 UI。业务审计通过
`im-audit-sdk` 投递到独立的 `im-audit-server`，本模块不保存或查询本地审计表。
普通用户事实也由 `im-account` 持久化；本模块不配置本地 DataSource、不引入 `im-datasource`，
也不保留 MyBatis Entity、Mapper 或 XML。

```text
browser --OAuth2/OIDC BFF--> im-admin --live introspection--> im-iam-server
                                  |-- Account Admin Facade -> im-account -> ordinary users
                                  `-- Kafka im.audit.im-admin.v1 -> im-audit-server
```

浏览器只持有 HttpOnly 的随机 BFF Session Cookie，Access/Refresh Token 经 AES-GCM 加密后
保存在 Redis。写请求使用 `XSRF-TOKEN` Cookie 与 `X-XSRF-TOKEN` Header。每个服务端接口
仍通过 `@PreAuthorize` 校验 Admin 自有权限；UI 菜单隐藏不是授权边界。

UI 使用 Hash Router，由 JAR 直接托管时刷新业务页面不依赖服务端 SPA fallback。Vite 开发环境
同时代理 `/api` 与 `/iam`。Java `Long` 业务 ID 在浏览器 wire model 中保持十进制字符串，时间戳
在校验为安全整数后按浏览器 IANA 时区展示为 `yyyy-MM-dd HH:mm:ss`。

Admin 权限目录仅包括普通用户查询、更新、密码重置、封禁/解封和删除。管理账号、角色、
应用和在线会话属于 IAM；BUSINESS/SECURITY 审计查询和导出属于独立 Audit 应用。

四个管理端使用一致的 Element Plus 高密度运维控制台：页面只展示本应用拥有的功能。
“其他控制台”链接由各 UI 的 `src/config/consoleLinks.ts` 管理，本地默认地址可分别通过
`VITE_IAM_CONSOLE_URL`、`VITE_AUDIT_CONSOLE_URL`、`VITE_MONITOR_CONSOLE_URL` 和
`VITE_ADMIN_CONSOLE_URL` 在构建时覆盖。创建、编辑和详情使用右侧抽屉，危险操作必须显式确认，
刷新失败时保留最近一次成功数据。
Admin 本地服务默认运行在 `http://localhost:18093`。

Admin 写操作使用稳定动作码和安全目标标识发布完成态审计事实。事务成功事件在提交后发送，
失败保留原业务异常；审计投递失败只记录日志与有限基数指标，不改变用户管理结果。

本地配置中的 IAM client secret 和 Session 加密 key 只是必填占位，部署时必须由 Nacos/
密钥系统覆盖。IAM 不可用时仅连接失败、超时或 `5xx` 可使用最长五分钟的成功 Introspection
缓存，且不得越过 Access Token 过期时间。

权限目录使用独立的 `im-admin-catalog` 机器客户端同步。OpenAPI 页面为 `GET /api/doc.html`，
API description 为 `GET /v3/api-docs`，两者均沿用 IAM Session 认证。

```bash
mvn -q -pl im-management/im-admin -am test
cd im-management/im-admin/ui
npm run lint && npm run format:check
npm run test:unit && npm run typecheck && npm run build
```
