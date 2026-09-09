# Management UI Convergence Execution Plan

## Objective and non-goals

统一 IAM、Audit 和 Admin UI 与当前 Java HTTP 契约、安全边界和前端工程规范，修复 IAM 接口漂移、CSRF、Long wire type、时间展示、路由刷新和本地代理问题，并补充能够发现这些回归的测试。

本次不抽取公共前端模块，不共享业务页面、Router 或应用状态，不调整 IAM、Audit、Admin 的业务所有权，也不重新设计视觉系统。

## Design and specification references

- `ARCHITECTURE.md`
- `docs/design-docs/2026-08-26-management-iam-design.md`
- `docs/design-docs/2026-08-28-central-management-audit-design.md`
- `docs/references/CODING_GUIDE.md`
- `docs/references/UNIT_TEST_GUIDE.md`

## Affected modules and ownership boundaries

- `im-management/im-iam/im-iam-server`: IAM 自身管理 UI、Web CSRF 和管理 HTTP 契约。
- `im-management/im-audit/im-audit-server`: 审计查询 UI、IAM 主体展示和时间边界。
- `im-management/im-admin`: 普通用户管理 UI、IAM BFF 开发代理和时间边界。
- `im-management/im-iam/im-iam-sdk`: BFF 登录后续地址的安全保存与恢复；不依赖 Server。
- `im-plugin/im-web`: 仅在确有必要时补充通用 wire type 约定，不引入管理业务语义。

## Ordered implementation tasks

- [x] 1. 为 IAM UI 增加真实 HTTP 契约测试，覆盖 `HttpResult`、应用作用域角色查询、管理员命令和会话撤销 Body。
- [x] 2. 重写 IAM UI HTTP 边界，使用精确 Request/Response 类型，移除旧通用 `command(resource,id,action)`。
- [x] 3. 为 IAM Web Security 增加可供同源 SPA 使用的 Cookie CSRF，并验证 Cookie 发布、延迟生成和前端 Header 提交。
- [x] 4. 将 IAM 角色和权限导航收敛到选中 Application 上下文，移除手工输入 `appId` 的流程。
- [x] 5. 删除未被 Spring 登录流程使用的 Vue Login 页面，保留 Spring Security 登录入口。
- [x] 6. 统一三个 UI 的 wire Long：业务 ID 使用字符串；分页总数和 epoch millis 在 UI 边界显式安全转换。
- [x] 7. 修正 Audit 的 `appKey`、真实时间序列化测试和导出/详情边界。
- [x] 8. 修正 Admin 的 `/iam` Vite 代理、用户 ID 类型和本地时区时间展示。
- [x] 9. 统一三个 UI 的 Router 刷新策略，并验证深链接入口。
- [x] 10. 让 IAM SDK 安全保存并恢复 `continue`，限制为当前应用内相对路径，防止开放重定向。
- [x] 11. 整理 IAM UI 文件格式、稳定表格 key、分页、错误反馈和权限可见性；不改变服务端授权边界。
- [x] 12. 更新 README、Coding Guide 与 Harness，记录管理 UI wire type、路由、CSRF 和契约测试规则。

## Test and verification strategy

- 每项行为先增加失败测试，再进行最小实现。
- 分别执行三个 UI 的 `npm run test:unit`、`npm run typecheck`、`npm run build`。
- 执行 IAM Server 与 IAM SDK 聚焦测试。
- 执行 `./scripts/verify.sh quick`，完成后执行 `./scripts/verify.sh full`。

## Rollout, compatibility, and rollback

- 项目尚处开发阶段，不保留旧 UI 路径兼容层。
- Java Long 业务 ID 的 wire 形式固定为字符串，避免浏览器精度丢失。
- 登录后续地址只允许 `/` 开头且不允许 `//` 的站内路径；无效值回退 `/`。
- UI 与对应 Server 必须同时发布，避免请求路径和类型契约错配。
- 回滚时按应用整体回滚 UI 与 Server，不单独回滚静态产物。

## Completion criteria

- IAM 管理列表和写操作使用当前 Server 路径与 Body。
- IAM 写操作具备有效 CSRF 保护且前端可以正常提交。
- 三个 UI 不把 Snowflake ID 转为 JavaScript number。
- 时间统一以用户 IANA 时区展示为 `yyyy-MM-dd HH:mm:ss`。
- 生产深链接刷新可用，本地 Admin IAM 登录链路可用。
- 登录后能够安全恢复原站内页面。
- 前端契约测试能够捕获响应 envelope、ID、时间和接口路径漂移。
- 所有聚焦测试、quick 和 full verification 通过。
