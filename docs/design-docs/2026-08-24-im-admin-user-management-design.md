# IM Admin 用户与权限管理设计

> 演进说明：管理员身份已迁移至 IAM，业务/安全审计已迁移至独立 Audit 应用。本文中相关
> 第一版目标仅保留为历史背景，当前所有权以 `ARCHITECTURE.md` 和后续专题设计为准。

执行计划：[IM Admin User Management Implementation Plan](../exec-plans/completed/2026-08-24-im-admin-user-management.md)

## 1. 背景

`im-management/im-admin` 当前只保留 Maven 模块和所有权占位。项目需要一个与普通 IM 用户体系完全独立的管理后台，用于查询和管理账号用户，并为后续业务管理能力建立稳定的管理员认证、授权和审计基础。

普通用户事实由 `im-account` 持有。`im-admin` 不得直接访问 `db_user`，也不得复用普通用户的 Account 登录、Access Token 或 Session。高权限用户管理操作必须通过 Account 所有的独立管理 Facade 执行。

## 2. 目标与边界

### 2.1 第一版目标

- 提供普通用户分页列表、状态筛选和用户详情；
- 支持按用户 ID、邮箱精确查询和按用户名查询；
- 已逻辑删除用户仍可在后台查询，并明确标记为“已删除”；
- 支持修改用户名和邮箱、重置密码、封禁、解封及逻辑删除；
- 重置密码、封禁和删除后立即撤销普通用户 Session，并关闭匹配的 WebSocket 连接；
- 建立独立管理员账号、登录 Session 和密码安全策略；
- 提供预定义权限、自定义角色和管理员角色分配；
- 提供管理账号创建、启用/禁用和密码重置；
- 对登录和管理操作记录可查询的审计日志；
- 提供独立 Vue 管理页面并随 `im-admin.jar` 发布。

### 2.2 非目标

- 不复用普通 Account 用户身份作为管理员；
- 不允许 `im-admin` 直接读写 Account 数据库或 Redis Session；
- 不提供物理删除普通用户或级联删除消息、群组、好友等历史事实；
- 不允许恢复已删除普通用户；
- 不允许后台查看普通用户或管理员的密码哈希、Token、Cookie 或 Session ID；
- 不在第一版提供组织、数据范围、字段级权限、审批流或多租户；
- 不在第一版提供 MFA，但管理员认证边界应允许后续增加；
- 不把用户管理能力加入 `im-monitor`。

## 3. 总体架构

```text
Admin Browser
    |
    | HTTPS + HttpOnly Cookie + CSRF
    v
im-management/im-admin
    |-- administrator domain/application
    |-- RBAC and audit
    |-- Admin MySQL
    |-- Admin Redis Session
    |
    `-- Dubbo --> im-account-admin-facade
                       |
                       v
                 im-account-server
                       |-- user domain rules
                       |-- Account MySQL/cache
                       `-- Session revocation and connection close
```

新增契约模块：

```text
im-service/im-account
├── im-account-facade
├── im-account-admin-facade
└── im-account-server
```

`im-account-admin-facade` 只包含高权限用户管理契约、Command、Query 和 DTO，不依赖 Server。`im-account-server` 实现普通 Facade 和 Admin Facade。只有 `im-admin` 可以依赖 Admin Facade；其他 Gateway、Broker、Service 和 Plugin 模块不得依赖它，该规则由 `im-test/im-architecture-test` 强制执行。

`im-admin` 使用独立的管理数据库 schema 和带命名空间的 Redis Key，不与 Account 用户表或 Session 表共享所有权。第一版默认服务名为 `im-admin`，HTTP 端口为 `18091`。

## 4. Account 管理契约

### 4.1 契约结构

```text
im-account-admin-facade
└── com.co.kc.imchat.service.account.admin.facade
    ├── AccountAdminService
    ├── params
    └── dto
```

应用服务入参和出参均使用明确对象，遵循现有 CQRS 约定。建议契约能力：

- `pageUsers(UserPageParams)`：分页查询全部用户，并通过删除标记区分逻辑删除记录；
- `getUser(UserGetParams)`：查询单个用户详情，包括已删除用户；
- `updateUser(UserUpdateParams)`：修改用户名和邮箱；
- `resetPassword(UserPasswordResetParams)`：管理员重置密码；
- `banUser(UserBanParams)`：封禁用户；
- `unbanUser(UserUnbanParams)`：解除封禁；
- `deleteUser(UserDeleteParams)`：逻辑删除用户。

分页默认每页 `20` 条，最大 `100` 条，按 `userId` 倒序。查询条件包括：

- 用户 ID 精确匹配；
- 邮箱精确匹配；
- 用户名前缀或关键字匹配；
- 用户列表支持 `NORMAL`、`BANNED` 状态筛选，同时展示未删除和已逻辑删除用户；
- 已删除用户在统一列表中明确标记，并且只允许查看详情。

逻辑删除不是业务状态，不允许通过 `status=DELETED` 在 Repository、Persistence Service
或 Mapper 内切换查询实现。管理端统一分页使用显式的原始数据查询，删除状态不作为筛选条件。

列表和详情只返回用户 ID、用户名、邮箱、账号状态、逻辑删除标记、创建时间和更新时间，
不返回密码字段。已删除用户保留删除前的 `NORMAL` 或 `BANNED` 业务状态，并通过
`deleted=true` 明确表达逻辑删除事实。

### 4.2 用户状态

Account 用户领域新增业务状态：

```text
NORMAL  -> BANNED -> NORMAL
```

- `NORMAL`：允许登录、刷新和在线认证；
- `BANNED`：拒绝登录、刷新和 Access Token 在线认证，可由管理员解封；

`DELETED` 不是用户业务状态，不进入 Account、Facade 或 Admin 的用户状态枚举。
逻辑删除由独立的 `deleted` 事实表达：删除后保留删除前的业务状态，但只允许通过管理查询读取，
不允许解封、修改或登录。应用服务判断写操作边界和删除幂等性时检查 `deleted`，不比较状态枚举。

`db_user` 新增业务状态字段，删除继续沿用项目逻辑删除规则：`is_deleted=主键 ID`。已删除用户的邮箱可由新用户重新使用，符合现有 `(email, is_deleted)` 唯一键。

管理查询需要使用 Account 自有的只读查询实现显式包含逻辑删除数据，不能通过关闭全局逻辑删除规则影响普通仓储。

### 4.3 公共分页模型

页码分页属于跨业务可复用的稳定值语义，由 `im-common` 提供不可变的 `Paging` 与
`PagingResult<T>`，领域和应用内部不再为每个列表重复声明页码、每页数量、记录和总数。
公共分页模型不依赖 MyBatis、Spring 或具体业务类型，并提供总页数、前后页判断及元素映射能力。

当前用户管理链路和 Account Admin Facade 直接使用公共分页模型。Facade 返回
`PagingResult<AccountUserListDTO>`，列表项使用独立 DTO；Admin HTTP Response 继续保留浏览器所需的
扁平字段并在 HTTP Transformer 转换。`Paging`、`PagingResult` 和 Facade 列表 DTO 均具备 Dubbo
序列化能力。游标分页 `IdCursor` 只在出现实际游标查询场景时引入，不与本次页码分页重构绑定。

分页边界与业务筛选条件分别建模：Repository 和跨边界 Adapter 使用
`page(Paging, QueryCondition)`，`Paging` 表达如何获取结果，`QueryCondition` 只表达筛选事实。
查询条件对象不得再次包含页码或每页数量。

普通运行时查询继续通过 `DbUserService.pageUsers(Paging, DbUserQueryCondition)` 使用
MyBatis-Plus 自动附加逻辑删除条件。管理端 Repository 的 `pageUsers` 则调用基础设施
`pageRawUsers(Paging, DbUserQueryCondition)`，通过显式 SQL 同时读取未删除和已删除记录。
原始分页 SQL 不包含删除状态谓词，也不得根据特殊状态切换查询实现。

### 4.4 管理命令行为

- 修改资料：校验用户名、邮箱和邮箱唯一性，保存后清理用户 ID/邮箱相关缓存；
- 重置密码：只接收新明文密码，使用现有 `PasswordService` 生成 BCrypt 哈希，提交后撤销当前 Session；
- 封禁：状态改为 `BANNED`，提交后撤销当前 Session；重复封禁保持幂等；
- 解封：只允许 `BANNED -> NORMAL`，不恢复旧 Session；
- 删除：逻辑删除账号并撤销 Session，不级联删除消息、聊天、好友或群组事实；重复删除返回稳定结果；
- 已删除用户：所有写命令返回明确的状态冲突错误。

同一用户的管理写操作按 `userId` 串行化，避免封禁、资料修改和删除并发覆盖。用户状态是认证事实：登录、Refresh Token 和 Access Token 在线认证都必须检查用户状态。Session 删除或连接关闭是加速失效机制，即使通知失败，非 `NORMAL` 用户也不能再次通过认证。

## 5. 管理员领域与 RBAC

### 5.1 管理员账号

管理员账号与普通 IM 用户没有关联。核心状态：

- `ACTIVE`：允许登录和执行授权操作；
- `DISABLED`：拒绝登录并撤销全部管理员 Session；
- `LOCKED`：连续登录失败后的临时锁定状态。

管理员密码至少 12 位，使用 BCrypt 保存。管理员用户名唯一，不允许读取密码哈希。管理员被禁用、密码被重置或角色发生关键变化后，其全部现有 Session 立即失效。

### 5.2 角色与权限

权限编码由代码预定义，后台不能创建任意权限编码。第一版权限目录至少包含：

```text
user:read
user:update
user:password:reset
user:ban
user:delete
admin:read
admin:create
admin:update
admin:password:reset
role:read
role:manage
audit:read
```

后台允许创建自定义角色并组合预定义权限。内置角色：

- `SUPER_ADMIN`：全部权限，不可删除、停用或移除权限；
- `USER_ADMIN`：普通用户查询和管理权限；
- `AUDITOR`：用户、管理员、角色及审计日志只读权限。

自定义角色可以修改、停用和删除；仍被管理员账号引用的角色不能删除。系统必须保证至少存在一个有效 `SUPER_ADMIN`，并禁止管理员禁用自己、删除自己或移除自己的最后一个超级管理员权限。

### 5.3 数据表

Admin schema 至少包含：

- `db_admin_user`：管理员账号、密码哈希、状态、失败次数、锁定截止时间和时间戳；
- `db_admin_role`：角色编码、名称、内置标识、状态和时间戳；
- `db_admin_permission`：代码预定义权限目录；
- `db_admin_user_role`：管理员与角色关联；
- `db_admin_role_permission`：角色与权限关联；
- `db_admin_audit_log`：操作审计记录。

所有表遵循项目 SQL 规范，DDL 放在根 `sql` 目录的明确管理 schema 文件中，不在模块源码目录保存副本。

## 6. 管理员认证与 Session

### 6.1 登录

管理员使用独立用户名和密码登录 `im-admin`。登录失败累计达到配置阈值后临时锁定账号，阈值、锁定时间、Session 空闲时间和绝对有效期均使用类型化 `@ConfigurationProperties`。

推荐本地默认：

- 连续失败阈值：`5`；
- 锁定时间：`15m`；
- Session 空闲超时：`30m`；
- Session 绝对有效期：`8h`。

### 6.2 Session 与 Cookie

- Session ID 使用密码学安全随机值，仅保存在 Redis 和 `HttpOnly` Cookie；
- Cookie 使用 `SameSite=Strict`，生产环境要求 `Secure`；
- 写请求同时校验与 Session 绑定的 CSRF Token；
- 浏览器不把 Session ID 或管理员凭证写入 `localStorage`；
- Session Redis Key 使用 `im:admin:session:*` 命名空间；
- 管理员维度维护可撤销 Session 索引，以支持禁用、重置密码和角色变更后的全量撤销。

### 6.3 首个超级管理员

首个 `SUPER_ADMIN` 通过一次性启动配置创建：

```yaml
im:
  admin:
    bootstrap:
      enabled: true
      username: admin
      password: admin
```

仅当管理员表为空时允许创建，密码立即 BCrypt 哈希保存。已有管理员时忽略配置并告警，不提供公开注册或 Bootstrap HTTP 接口。初始化完成后必须从 Nacos 删除明文密码并关闭 Bootstrap。

## 7. 审计

审计日志至少包含：

- 审计 ID；
- 管理员 ID 和用户名快照；
- 目标类型和目标 ID；
- 操作类型和所需权限；
- `PENDING`、`SUCCESS`、`FAILED` 结果；
- 稳定错误码；
- 非敏感变更摘要；
- 请求 IP、User-Agent 摘要和时间。

密码、密码哈希、Cookie、Session ID、CSRF Token、普通用户 Token 和完整异常堆栈不得进入审计日志。

危险写操作先创建 `PENDING` 审计记录；无法创建审计记录时不执行操作。Account 调用完成后更新为 `SUCCESS` 或 `FAILED`。如果最终状态更新失败，保留 `PENDING` 记录并输出运维告警，避免操作完全没有审计痕迹。管理员登录成功、失败、锁定、退出，以及管理员/角色/普通用户管理操作均需要审计。读取操作可在审计写入异常时保留业务响应并输出运维告警，不能让审计故障扩大为后台只读查询故障。

## 8. HTTP API 与页面

### 8.1 API

`im-admin` 浏览器只访问本应用 `/api`。接口遵循项目现有的“GET 查询、POST 命令”风格，不为了形式上的 RESTful 把业务动作伪装成资源 CRUD。所有命令使用明确的 Command 请求体，查询使用 Query 参数对象：

```text
POST /api/auth/signIn
POST /api/auth/signOut
GET  /api/auth/session

GET  /api/users/page
GET  /api/users/detail
POST /api/users/update
POST /api/users/resetPassword
POST /api/users/ban
POST /api/users/unban
POST /api/users/delete

GET  /api/adminAccounts/page
GET  /api/adminAccounts/detail
POST /api/adminAccounts/create
POST /api/adminAccounts/update
POST /api/adminAccounts/resetPassword
POST /api/adminAccounts/enable
POST /api/adminAccounts/disable

GET  /api/roles/page
GET  /api/roles/detail
POST /api/roles/create
POST /api/roles/update
POST /api/roles/enable
POST /api/roles/disable
POST /api/roles/delete
GET  /api/permissions/list

GET /api/auditLogs/page
```

逻辑删除是包含 Session 撤销、连接关闭和审计的业务命令，因此使用 `POST /api/users/delete`，不使用容易被理解为物理资源删除的 HTTP `DELETE`。Controller 只负责认证上下文、参数校验、权限声明、应用服务委托和转换。业务规则属于应用或领域层，Account DTO 不直接暴露给浏览器。

### 8.2 页面导航

```text
用户管理

权限管理
├── 管理账号
└── 角色配置

审计日志
```

用户管理以统一分页列表为主工作台，支持用户 ID、用户名、邮箱和状态筛选，并同时展示未删除和已删除用户。用户详情使用右侧抽屉或独立详情区域，展示账号状态、删除标记与时间信息；已删除用户行只提供详情操作，不提供恢复或其他写操作。

封禁、删除、密码重置、管理员禁用和角色删除必须显示目标和影响说明，并要求二次确认。按钮和菜单可以按权限隐藏或禁用，但服务端权限校验始终是最终边界。

UI 使用 Vue 3、TypeScript、Vite、Router、Vitest、Element Plus 和 Axios。前端源码由 `im-admin` 独立持有，不与 `im-monitor` 共用页面、Router 或应用状态。Maven 在 `package` 阶段构建到 `target/classes/static` 并进入 `im-admin.jar`。

## 9. 错误处理

- `400`：参数、密码强度或状态转换非法；
- `401`：管理员未登录或 Session 失效；
- `403`：缺少权限或 CSRF 校验失败；
- `404`：用户、管理员或角色不存在；
- `409`：邮箱/用户名冲突、角色仍被使用、最后一个超级管理员保护或并发状态变化；
- `502/503`：Account Admin Facade 或关键基础设施暂时不可用；
- `500`：未分类内部错误。

HTTP 响应只返回稳定错误码和安全摘要，不返回 SQL、堆栈、密码、Cookie、Session ID 或内部 RPC 细节。Dubbo Provider 通过 `im-dubbo` 将项目业务异常统一转换为只含通用错误码和安全消息的 `RpcException`，未知异常仅在 Provider 记录完整日志；`im-admin` 在 HTTP 与审计边界解释通用 RPC 错误码。

## 10. 测试与 Harness

### 10.1 后端测试

- Facade 契约和序列化测试；
- 统一用户分页、精确查询、用户名查询、状态筛选和删除标记；
- 资料修改、邮箱唯一性、密码重置、封禁/解封和逻辑删除；
- 登录、刷新和 Access Token 在线认证拒绝非 `NORMAL` 用户；
- Session 撤销和连接关闭失败不绕过用户状态事实；
- 管理员登录、失败锁定、Session 空闲/绝对过期和全量撤销；
- CSRF、权限矩阵和无权限请求；
- 自定义角色、内置角色保护、引用保护和最后一个超级管理员保护；
- 审计 `PENDING/SUCCESS/FAILED` 以及敏感字段不落日志；
- Controller、Dubbo、MyBatis、Redis 和应用启动测试。

### 10.2 UI 测试

- 登录和 Session 失效；
- 用户统一列表、筛选、分页、详情和已删除行只读行为；
- 修改、重置密码、封禁/解封和删除确认；
- 管理账号、角色配置和权限显示；
- 审计列表；
- loading、empty、error、forbidden 和冲突状态。

### 10.3 架构与 Harness

- 只有 `im-admin` 可以依赖 `im-account-admin-facade`；
- Admin Facade 不依赖 Account Server；
- `im-admin` 不依赖 Account Server 实现或直接访问 `db_user`；
- Admin Domain 不依赖 Spring、HTTP、Dubbo、MyBatis 或 Redis；
- 管理应用服务仍遵循 CQRS 对象入参/出参约定；
- SQL DDL、Mapper SQL 和索引遵循 SQL Harness；
- UI 生成物、`node_modules` 和本地配置不进入 Git；
- `affected-modules.sh` 覆盖新 Facade 和 Admin Runtime。

完成前运行聚焦测试、`./scripts/verify.sh affected`、`quick` 和 `full`。

## 11. 发布与回滚

推荐发布顺序：

1. 执行 Account 和 Admin schema DDL；
2. 发布包含用户状态和 Admin Facade 的 Account Server；
3. 配置并启动 `im-admin`，一次性创建首个超级管理员；
4. 删除 Bootstrap 明文密码配置；
5. 验证封禁、删除、Session 撤销、权限和审计后再开放运维网络访问。

回滚 `im-admin` 不影响普通用户业务。Account 用户状态字段必须保持向后可读；回滚 Account Server 前不得遗留旧版本无法识别的状态。Admin 管理端口只暴露在受控运维网络，正式开放公网或引入外部身份提供商需要单独安全设计。
