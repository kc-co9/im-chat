# 管理平台统一身份与权限中心设计

> 2026-09-06 更新：上线前实现已收敛为 Spring Authorization Server 默认 Introspection
> Provider。管理员权限在 Access Token 签发/刷新时形成快照，资源应用校验标准 `aud`；角色权限变化
> 最迟随 15 分钟 Access Token 过期生效，高风险账号操作继续主动撤销授权。下文关于服务端实时权限
> 解析的内容保留为历史设计背景。

> 演进说明：集中审计持久化和机器客户端接入以
> [集中管理审计设计](2026-08-28-central-management-audit-design.md)为准，该设计替代本文早期的
> IAM 本地安全审计所有权和不支持 Client Credentials 的非目标。

执行计划：[Management IAM Implementation Plan](../exec-plans/completed/2026-08-26-management-iam.md)

## 1. 背景

`im-admin` 已经拥有独立的管理员账号、密码认证、Redis Session、RBAC 和审计能力，
`im-monitor` 则保持独立、只读且尚未接入管理端认证。随着管理应用增加，如果每个应用分别实现登录、
密码策略、会话撤销和角色管理，会形成重复安全边界，也无法提供跨应用单点登录和统一账号治理。

本设计引入独立的管理平台身份与权限中心 `im-iam`。它由 `im-iam-server` 和 `im-iam-sdk` 组成，
只服务于管理应用，不复用普通 IM 用户的
Account 认证体系。`im-admin` 和 `im-monitor` 仍是相互独立的业务应用，但通过标准 OAuth2/OIDC
协议接入同一套管理员身份、单点登录和集中授权管理。

本设计替代
[IM Admin 用户与权限管理设计](2026-08-24-im-admin-user-management-design.md)
中由 `im-admin` 持有管理员身份、登录 Session 和 RBAC 的目标状态。原文关于普通用户管理、
Account Admin Facade、Admin 业务审计和 DDD 分层的决策继续有效。

## 2. 目标与非目标

### 2.1 目标

- 为所有管理应用提供独立于普通用户体系的统一管理员身份；
- 使用 Spring Authorization Server 提供标准 OAuth2/OIDC 登录和单点登录；
- 采用 Authorization Code + PKCE，浏览器不持有 Access Token 或 Refresh Token；
- 在 IAM 中统一管理应用、权限目录、角色和管理员角色分配；
- 权限保持应用隔离，`im-admin` 权限不能用于访问 `im-monitor`；
- 每次业务请求实时查询 IAM 获取当前账号状态和权限；
- IAM 临时不可用时，允许使用最后一次成功结果进行有界失效兜底；
- 支持多设备登录、当前应用退出、平台退出、全部设备退出和指定会话下线；
- 提供可复用的 `im-iam-sdk`，避免每个管理应用重复实现安全接入；
- 将认证、授权和会话变更纳入统一安全审计。

### 2.2 非目标

- 不复用 `im-account` 的普通用户、Token、Session 或密码认证；
- 不把 `im-admin` 与 `im-monitor` 合并为同一运行进程或共享业务 UI；
- 不把各应用的业务授权判断迁移到 IAM；
- 不在第一版提供多租户、组织架构、数据权限、字段权限或审批流；
- 不在第一版提供 MFA、验证码或外部身份提供商；
- 不为旧认证方式保留长期双读、双写或兼容接口；
- 不使用 JWT 携带长期权限快照。

## 3. 方案选择

采用 Spring Authorization Server 与 OAuth2/OIDC Authorization Code + PKCE。

没有采用以下方案：

- **每个应用独立认证和 RBAC**：实现简单，但重复维护高风险安全逻辑，无法形成 SSO；
- **复用 Account 登录认证**：普通用户与管理员属于不同安全边界，会混淆账号所有权和风险策略；
- **JWT 自包含权限**：避免实时查询，但权限撤销只能等待 Token 过期，不满足实时授权要求；
- **统一认证、各应用分别持久化角色**：账号统一但授权治理仍然分散，角色配置和审计体验不一致；
- **将 SDK 放入 `im-plugin`**：IAM SDK 包含管理认证协议、权限注册和失效策略，不是业务无关的通用插件。

## 4. 模块与所有权

目标模块结构：

```text
im-management
├── im-iam
│   ├── im-iam-server # 可启动、可部署的统一身份与权限中心
│   └── im-iam-sdk    # 管理应用的 IAM 接入契约和 Spring 集成
├── im-admin     # 普通用户及后台业务管理
└── im-monitor   # 运行诊断与监控
```

依赖方向：

```text
im-admin   ─┐
            ├──> im-iam-sdk
im-monitor ─┘

im-iam-sdk     -X-> im-iam-server implementation
im-iam-server  -X-> im-admin / im-monitor
```

`im-iam-server` 拥有：

- 管理员账号、密码摘要和账号状态；
- 应用注册、回调地址和客户端凭据；
- 权限目录、应用角色、角色权限和管理员角色分配；
- IAM SSO Session、OAuth2 Authorization、Token 状态和撤销事实；
- 登录、授权、角色、会话等安全审计；
- 管理账号、角色、应用、会话和安全审计页面。

应用与 OAuth 客户端采用两个独立聚合：

- `Application` 表示接入 IAM 的业务应用，持有内部关联用 `AppId`、可识别编码 `AppKey`、名称和状态；
- `OAuthClient` 表示应用下的一种 OAuth 接入配置，持有 `OAuthClientId`、所属 `AppId`、凭据、Grant Type、Scope、回调白名单和状态；
- 浏览器 Authorization Code + PKCE 客户端和机器 Client Credentials 客户端使用同一个 `OAuthClient` 模型，通过 Grant Type 与字段约束区分，不再拆成 `RegisteredApplication` 和 `ServiceClient` 两套聚合；
- 一个 `Application` 可以拥有多个 `OAuthClient`，OAuth 客户端停用不改变应用自身状态，应用停用则使其全部客户端失效；
- 应用注册和 OAuth 客户端注册是两个独立用例与事务。先创建 `Application`，再按需为其创建一个或多个浏览器或机器 `OAuthClient`；暂时没有客户端的应用是合法状态，客户端注册失败可以独立重试。

IAM 标识分为三层：

| 标识 | 语义 | 可见范围 |
| --- | --- | --- |
| 数据库 `id` | 单表自增技术主键，通过 `Identification.pkId` 表示当前持久化行 | 领域聚合内部与持久化层 |
| `AppId` / `RoleId` 等 | Snowflake 业务 ID，用于领域身份和跨表关联 | IAM 内部契约 |
| `AppKey` | 简短、可识别的应用编码，例如 `imAdmin` | 配置、管理页面、Token Claim |

IAM 聚合根继承 `Identification`，允许 Repository 在重建或新增持久化后恢复数据库自增主键；该
`pkId` 只标识当前表行，不进入领域 Builder、CQRS、Facade、HTTP 或 RPC 契约，也不承担跨表关联。
IAM 表之间只通过业务 ID 关联。`AppKey` 的自动生成或分配算法不在当前阶段设计；当前由受信任的
注册命令或初始化配置提供，并校验唯一性。

`im-iam-sdk` 提供：

- OAuth2/OIDC 登录重定向和回调集成；
- 应用 Session、Token 自动刷新和 Token Introspection；
- Redis 失效兜底缓存和恢复探测；
- Spring Security `SecurityContext` 构建；
- 权限目录注册契约及客户端；
- 稳定配置、错误语义和自动装配。

SDK 不包含管理员、角色等 IAM 领域实现，也不依赖 `im-iam-server` 的实现代码。各应用自己的权限定义、
`@PreAuthorize` 和业务审计继续留在应用内部。

`im-iam-server` 内部采用与业务服务一致的 DDD 结构：

```text
application
domain
├── administrator
├── app
├── authorization
└── session
interfaces
infrastructure
support
transformer
model/cqrs
```

## 5. 认证流程

```text
浏览器访问 im-admin / im-monitor
              │
              ▼
      应用检查本地 Session
        │无              │有
        ▼                ▼
重定向 IAM /authorize   服务端携带 Access Token
        │                调用 IAM Introspection
        ▼                         │
IAM 登录或复用 SSO                ├─ IAM 正常：实时权限
        │                         └─ IAM 故障：读取有界缓存
        ▼
返回 Authorization Code
        │
        ▼
应用服务端使用 Code + PKCE
换取 Access Token / Refresh Token
        │
        ▼
创建应用 Session，浏览器仅保存
HttpOnly + Secure + SameSite Cookie
        │
        ▼
构建 Spring SecurityContext
并通过 @PreAuthorize 执行业务鉴权
```

约束如下：

- 浏览器不接触 Access Token 和 Refresh Token；
- Authorization Code 只能使用一次并具有很短的有效期；
- 每个应用拥有独立的 `clientId`、回调地址和应用 Session；
- IAM SSO 证明管理员身份，不代表管理员自动拥有当前应用权限；
- 未分配当前应用角色时，登录成功后访问业务资源仍返回 `403`；
- 回调地址必须精确匹配应用注册白名单，禁止任意跳转；
- OAuth2/OIDC 外部协议保留 `client_id`、`redirect_uri`、`access_token` 等标准字段，
  Java 类型、内部消息和配置统一使用 `clientId`、`redirectUri`、`accessToken`、`appKey` 等小驼峰命名。

## 6. Token 与 Session 生命周期

第一版默认生命周期：

| 状态 | 默认期限 | 行为 |
|---|---:|---|
| Access Token | 15 分钟 | 过期前由应用服务端自动刷新 |
| Refresh Token | 8 小时 | 仅保存在应用服务端 Session |
| IAM SSO 空闲期限 | 8 小时 | 活跃访问可以延长空闲期限 |
| IAM SSO 绝对期限 | 24 小时 | 达到后必须重新输入凭据 |
| Introspection 失效兜底 | 5 分钟 | 不能超过 Access Token 的过期时间 |

Refresh Token 过期后，应用重新跳转 IAM。如果 IAM SSO 仍然有效，IAM 可以直接完成授权跳转，
用户无需重新输入密码。

Access Token 使用密码学安全的不透明随机值，不把权限快照编码到 Token 中。IAM 只持久化 Token
摘要和元数据。应用 Session 因 Introspection 和刷新需要保存可用 Token 时，必须使用应用独立密钥
加密存储；缓存键使用 `tokenDigest + appKey`，不得使用原始 Token。

浏览器 Cookie 只保存随机应用 Session ID，并启用 `HttpOnly`、`Secure` 和合适的 `SameSite`
策略。应用服务端 Session 保存授权状态和 Token，前端不得将认证凭据写入 `localStorage`。
所有非安全 HTTP 方法必须校验与应用 Session 绑定的 CSRF Token；CSRF Cookie 可以供同源前端读取，
但不能包含 Session ID 或 OAuth2 Token。

## 7. 实时授权与失效兜底

每个受保护请求优先调用 IAM Token Introspection，返回 Token 活跃状态、管理员身份、`clientId`、
当前应用权限和 Token 过期时间。IAM 必须根据通过客户端认证的 `clientId` 推导 `appKey`，不能相信
调用方额外提交的应用标识。应用根据结果构建一次请求范围内的 Spring Security
`Authentication`，最终仍由 `@PreAuthorize` 执行业务授权。

IAM Introspection 使用 Spring Authorization Server 原生 `AuthenticationProvider` 链。授权码
Access Token 由管理员 Provider 处理，客户端凭据 Access Token 由机器客户端 Provider 处理；选择
依据是服务端授权记录的 Grant Type，不使用自定义 Success Handler、内部 Dispatcher 或自定义
主体类型 Claim。管理员业务 ID 作为稳定主体标识写入管理员 Token 的标准 `sub`，并用于服务端授权记录和实时权限解析；
管理员用户名仅作为展示 Claim，不作为授权关联键。
框架默认 Introspection Provider 从端点移除，避免不支持的 Grant Type 绕过应用状态和 Audience 校验。

每个应用在自己的 Redis 命名空间缓存最后一次成功且 `active=true` 的结果：

```text
key   = tokenDigest + appKey
value = administratorId + username + authorities
        + tokenExpiresAt + lastVerifiedAt
```

缓存只在以下情况使用：

- IAM 连接失败；
- IAM 请求超时；
- IAM 明确返回 `5xx`。

以下情况禁止使用缓存：

- IAM 返回 `active=false`；
- Token、客户端、受众或应用不匹配；
- 管理员被禁用、删除或未获当前应用权限；
- 响应格式错误、签名或传输不可信；
- 缓存超过 5 分钟或 Access Token 已过期。

连续失败达到阈值后 SDK 打开熔断，避免每次请求等待失败的 IAM；恢复探测成功后立即回到实时查询。
所有权限采用同一失效策略，不按操作风险分级。其明确代价是：IAM 不可用且已存在成功缓存时，
账号禁用、会话撤销或权限收回最多延迟 5 分钟生效。

建议默认配置：

```yaml
im:
  iam:
    introspection:
      connectTimeout: 300ms
      timeout: 500ms
      staleCacheTtl: 5m
      failureThreshold: 3
      recoveryProbeInterval: 5s
```

## 8. 权限目录与角色

### 8.1 IAM 内部权限与应用权限边界

IAM Server 同时承载两种权限，但它们不是同一个领域概念：

- IAM 内部管理权限只保护 IAM 自身的管理员、应用、角色、权限和会话管理接口，来源是 IAM Server 内部的静态权限定义；
- 外部应用权限由 `im-admin`、`im-monitor` 等应用声明，通过权限目录同步注册到 IAM，供对应应用范围内的角色使用；
- IAM 内部权限不通过外部应用权限目录同步，也不允许外部应用声明覆盖；外部应用权限不能直接保护 IAM Server 的管理端点；
- 两类权限可以在基础设施层复用数据库表或查询组件，但领域对象、应用服务、Repository 和接口契约必须保持语义隔离。

数据库实现采用物理表隔离：

- IAM 自身角色使用 `db_iam_internal_role`，管理员内部角色关系使用
  `db_iam_internal_administrator_role`；
- 外部应用角色和权限分别使用 `db_iam_application_role`、`db_iam_application_permission`；
- 外部应用的角色权限、管理员角色关系分别使用
  `db_iam_application_role_permission`、`db_iam_application_administrator_role`。

IAM 内部权限不建立动态权限目录表，由 IAM Server 静态定义权限编码；内部角色只保存所授予的静态编码。

领域命名也必须显式体现这条边界。外部接入应用使用 `ApplicationPermission`、
`ApplicationRole` 及其 `ApplicationPermissionId`、`ApplicationRoleId` 等值对象；IAM 自身使用
`IamPermissionCode`、`IamRole`、`IamRoleCode` 和 `IamRoleName`。两套模型不复用无范围语义的
`Permission`、`Role`、`RoleCode` 或 `RoleName`，避免调用方仅凭导入包无法判断权限归属。

OAuth 授权会话作为独立 Session 领域边界保留在 `domain/session`，领域身份和应用、接口边界统一
使用 `OAuthSession*` 前缀。持久化模型可以继续使用 OAuth2 协议中的 `authorizationId` 字段，
但领域层不使用泛化的 `AuthorizationId`，避免与权限授权概念混淆。

因此，IAM 管理端点使用 IAM 自身的静态权限常量进行授权；权限目录同步接口只处理调用方所属应用的外部权限目录。

采用“应用定义、IAM 统一管理”：

```text
应用代码中的权限定义
        ↓ 注册
IAM 权限目录
        ↓ 配置
应用范围内的角色
        ↓ 分配
管理员账号
```

- `im-admin`、`im-monitor` 分别维护自己的权限编码、名称和说明；
- 应用使用自身 `clientId` 和客户端凭据向 IAM 注册权限完整清单；
- 权限清单按 `appId` 隔离，角色只能组合同一应用的权限；
- 同一管理员可以在不同应用拥有不同角色；
- 权限同步使用无版本的全量快照，重复提交通过复用既有权限业务标识保持结果幂等；
- 第一期不处理滚动发布期间旧实例晚于新实例同步造成的目录回退；需要该保证时再基于应用构建版本设计明确的修订机制；
- 代码中移除的权限先标记停用；仍被角色引用时拒绝物理删除并产生告警；
- IAM 管理页面只能选择应用已注册的权限，不允许手工创建任意权限编码；
- 权限目录同步失败时应用进程可以启动，但就绪检查不通过，不能承接正式流量。

角色和权限变更无需强制管理员退出。下一次成功 Introspection 直接返回新权限；IAM 故障时受 5 分钟
失效兜底窗口约束。

## 9. 登录安全

第一版只支持管理员用户名或邮箱与密码登录，不提前创建未使用的 MFA 抽象。

- 密码使用项目认可的强散列算法保存，日志、响应和审计禁止出现明文或摘要；
- 连续失败 5 次后锁定 15 分钟，成功登录后清零失败次数；
- 登录失败统一提示“账号或密码错误”，不暴露账号是否存在；
- 禁用、已删除或锁定期内的账号不能登录；
- 登录、失败、锁定、退出、刷新和会话撤销进入安全审计；
- 首个超级管理员通过受控初始化配置或初始化脚本创建，初始化完成后关闭入口并移除明文配置；
- 启用首个管理员初始化前，必须先初始化 `imIam` 应用及其内置 `SUPER_ADMIN` 角色；任一前置事实缺失时启动初始化失败，不创建无角色管理员；
- 第一版不实现验证码、MFA 和外部身份提供商，也不保留无调用的扩展接口。

## 10. 多端登录、退出与撤销

同一管理员允许在多个设备和浏览器同时登录。IAM 提供会话列表，展示登录时间、最后活动时间、
IP 和浏览器摘要，并支持强制下线指定会话。

- **退出当前应用**：删除该应用 Session，撤销对应 Access Token 和 Refresh Token，不影响其他应用和 IAM SSO；
- **退出管理平台**：结束当前浏览器 IAM SSO，撤销由该 SSO Session 建立的全部应用授权；
- **退出所有设备**：撤销该管理员全部 SSO Session 和 Token；
- **禁用或删除管理员**：立即撤销全部 Session 和 Token；
- **修改密码**：撤销全部 Session 和 Token，要求重新登录；
- **修改角色或权限**：不强制退出，通过实时 Introspection 生效。

IAM 不需要直接删除各应用 Redis 中的 Session。Token 被撤销后，应用实时 Introspection 会拒绝访问；
IAM 故障时仍受 5 分钟失效兜底窗口影响。

## 11. 数据归属

MySQL 保存：

- 管理员账号、密码摘要、状态和锁定事实；
- 应用、`clientId`、客户端凭据摘要和回调白名单；
- 权限目录；
- 应用角色、角色权限和管理员角色关系；
- Token 摘要、授权元数据和撤销事实；
- 安全审计记录。

应用相关表采用以下关系：

```text
db_iam_app
├── id       数据库自增主键，通过 Application.pkId 恢复当前表行
├── app_id   Snowflake 业务 ID，唯一，跨表关联
└── app_key  可识别应用编码，唯一

db_iam_oauth_client.app_id       -> db_iam_app.app_id
db_iam_application_permission.app_id -> db_iam_app.app_id
db_iam_application_role.app_id       -> db_iam_app.app_id
db_iam_authorization.app_id      -> db_iam_app.app_id
db_iam_authorization.oauth_client_id -> db_iam_oauth_client.oauth_client_id
```

管理员角色、角色权限等关联表同样使用 `administrator_id`、`role_id`、`permission_id` 等业务 ID。
各聚合表仍保留独立自增 `id`。Repository 在聚合构建完成后将其恢复为 `Identification.pkId`，但
不得用该技术主键替代领域业务 ID，也不得用它建立 IAM 表之间的关系。

Redis 保存：

- IAM SSO Session；
- 短期 OAuth2 Authorization 状态；
- 登录失败计数和锁定辅助状态；
- SDK 所属应用加密后的应用 Session 与 Introspection 失效兜底缓存。

IAM Redis 丢失后允许要求管理员重新登录，但账号、应用、角色、权限、撤销事实和审计不能丢失。
各应用使用独立 Redis Key 命名空间，不能共享或直接读取 IAM 内部 Session。

## 12. 页面与业务边界

IAM 独立提供：

```text
权限管理
├── 管理账号
├── 角色配置
├── 应用管理
├── 在线会话
└── 安全审计
```

`im-admin` 保留普通用户管理及其业务审计；`im-monitor` 保留 Broker、Gateway、消息链路和指标监控。
两个应用的导航可以跳转 IAM，但不复制 IAM 页面、Controller 或应用服务。

IAM、Admin 和 Monitor 前端仍分别随各自应用构建和部署。共享认证不代表共享 Router、页面状态或业务 UI。

## 13. 异常处理与可观测性

- IAM 登录或授权端点不可用：无法创建新会话，应用展示统一的认证中心不可用页面；
- Introspection 临时失败：按第 7 节使用有界缓存；
- 应用 Redis 不可用：返回 `503`，不得降级为匿名或绕过鉴权；
- Refresh Token 明确失效：删除应用 Session 并重新进入登录重定向；
- 刷新时 IAM 临时不可用：Access Token 有效时继续使用，过期后停止访问并提示稍后重试；
- 未认证返回 `401`，已认证但无当前资源权限返回 `403`；
- 登录、Token、Introspection、缓存命中、熔断、权限同步和撤销记录有限基数指标；
- 日志禁止输出 Access Token、Refresh Token、Authorization Code、Cookie、密码或客户端密钥，
  只记录摘要、`appKey`、管理员 ID、稳定结果码和 Trace ID。

## 14. 验证策略

- IAM 领域测试覆盖账号状态、登录锁定、角色权限隔离和会话撤销；
- OAuth2/OIDC 集成测试覆盖 Authorization Code + PKCE、回调白名单、刷新、Introspection 和登出；
- SDK 契约测试覆盖应用 Session、权限转换、SecurityContext 和 Token 自动刷新；
- 故障测试覆盖 IAM 超时、`5xx`、Redis 故障、缓存过期、熔断和恢复探测；
- 安全测试覆盖跨应用权限隔离、Code 重放、无效 Token、Token 泄露防护以及 `401`/`403` 语义；
- 浏览器流程测试覆盖首次登录、跨应用 SSO、当前应用退出、平台退出和强制下线；
- 架构与 Harness 约束业务应用不得自行实现管理员密码认证、SDK 不得依赖 IAM 实现，
  并检查 Token 不进入日志、缓存键和业务 DTO。

### 14.1 Review 后的安全与分层收敛

IAM 自身的管理端点不是“任意已登录管理员”都可访问的普通资源。登录认证只证明管理员身份，
不能直接授予应用、角色、管理员账号、会话和安全审计的管理权限。IAM 为自身管理界面注册独立权限目录，
Controller 对每个查询和写入端点声明最小 `@PreAuthorize` 权限；内置 `SUPER_ADMIN` 角色获得完整 IAM
管理权限，普通管理员必须经过显式角色授权。安全测试维护完整端点清单，并同时证明未授权管理员返回
`403`、具有对应权限的管理员可以访问。

管理员禁用、删除、密码重置与其全部 Authorization 撤销属于同一个业务操作，必须在同一事务中完成；
任何一步失败都不能留下“账号状态已改变但旧 Token 仍有效”的中间状态。登录失败计数和锁定状态更新必须
使用按账号串行化或数据库原子条件更新，避免并发错误登录丢失计数。

`im-iam-server` 与其他可部署 DDD 模块遵循相同内部边界：应用层不依赖 MyBatis Mapper、数据库 Entity
或数据库枚举；管理查询通过领域 Repository 或专用查询 Repository 获取结果，并由 Transformer 转换。
可增长的管理员、应用、角色、Session 和审计列表使用 `Paging`/`PagingResult`，不以全表加载模拟分页。
Repository 技术实现通过 `infrastructure.mybatis.service` 使用 MyBatis-Plus；同一 Mapper 扫描范围只保留
逐个 `@Mapper` 或集中 `@MapperScan` 中的一种。

明确声明为聚合根的 `Administrator`、`Application`、`OAuthClient` 和 `Role` 继承共享
`Identification`。业务 ID 通过持久化 Transformer 恢复；数据库自增主键在聚合构建完成后由
Repository 回填为 `pkId`，仅用于当前表行的持久化生命周期。聚合使用
`@EqualsAndHashCode(callSuper = false)`，由自身字段表达相等性，不定制业务 ID 专属相等性。

## 15. 迁移策略

采用一次性切换，不保留双认证兼容逻辑：

1. 创建 `im-iam-server` 和 `im-iam-sdk`，完成身份、应用、权限、角色、SSO、OAuth2/OIDC 与审计能力；
2. 注册 `im-admin`、`im-monitor` 的 `clientId`、回调地址和客户端凭据；
3. 两个应用同步各自权限目录，在 IAM 中配置应用角色；
4. 将现有管理员账号、密码摘要和角色分配一次性迁移到 IAM；
5. 两个应用接入标准登录重定向、应用 Session、Introspection 和失效兜底；
6. 验证 SSO、实时权限、故障缓存、退出、强制下线和安全审计；
7. 删除 `im-admin` 原有管理员认证、Redis 登录 Session、角色存储和权限管理代码；
8. 更新前端导航、部署配置、架构文档、README、Security、Reliability 和 Harness。

切换期间只有 IAM 可以修改管理账号和角色，避免新旧系统同时写入。失败时按部署版本整体回滚，
不在生产代码中长期保留旧认证分支。

## 16. 影响与后续工作

该方案集中管理高风险认证与授权事实，并保留应用业务边界和 Spring Security 最终授权职责。
代价是 IAM 成为管理平台关键依赖，需要高可用部署、完善监控和受控密钥管理；5 分钟失效兜底提高了
IAM 故障期间的可用性，同时明确接受短暂的权限撤销延迟。

设计批准后需要单独编写执行计划，分阶段实现 IAM、SDK、Admin/Monitor 接入、数据迁移、文档与 Harness，
并在每个阶段设置可验证的安全和回滚检查点。
