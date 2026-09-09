# Web 基础能力边界收敛设计

实施记录见 [Web Infrastructure Boundary Implementation Plan](../exec-plans/completed/2026-08-27-web-infrastructure-boundary.md)。

## 1. 背景

`im-plugin/im-web` 当前同时持有通用响应包装、异常处理、Jackson、日志、CORS、MVC 扩展和 C 端
`UserContext` 解析。`im-admin` 与 `im-monitor` 又分别维护管理端异常响应；`im-admin` 还自行采集客户端地址和
User-Agent。结果是通用 HTTP 能力、Session 接入策略和 Admin 审计能力混在不同位置，并形成多套响应协议。

本设计收敛这些边界，但不移动 `im-management/im-iam/im-iam-sdk`，也不创建按 C 端、管理端命名的 Web 插件。

## 2. 目标与非目标

### 2.1 目标

- `im-web` 只拥有与具体业务、Session 和 IAM 无关的通用 HTTP/MVC 能力；
- 所有应用统一使用 HTTP 200 与 `HttpResult` 表达业务结果；
- 将客户端地址、User-Agent 等客观请求信息抽成通用 HTTP 请求上下文；
- 将 Session Header 到 `UserContext` 的适配归还现有 `im-session`；
- 保留 Admin 审计、权限和锁场景的业务所有权；
- 保持 `im-iam-sdk` 的模块位置和 IAM 聚合关系不变。

### 2.2 非目标

- 不新增 `im-web-user`、`im-web-management` 或 `im-session-web` Maven 模块；
- 不把 Admin 审计领域模型或权限目录迁入插件；
- 不让 `im-web` 依赖 Session、IAM、Admin 或 Monitor；
- 不移动或重命名 `im-iam-sdk`；
- 不在本次调整中改变 IAM 的 OAuth2/OIDC、BFF Session 或实时权限查询策略。

## 3. 模块边界

目标结构：

```text
im-plugin
├── im-web
│   ├── advice
│   ├── context
│   ├── convert
│   ├── logging
│   ├── mvc
│   ├── properties
│   └── support
└── im-session
    ├── context
    ├── token
    └── web

im-management
├── im-iam
│   ├── im-iam-server
│   └── im-iam-sdk
└── im-admin
    └── support
        ├── audit
        ├── lock
        └── security
```

依赖方向：

```text
im-session -> Spring MVC / Servlet（可选依赖，仅 Web 适配启用时）
im-admin   -> im-web, im-iam-sdk
im-monitor -> im-web, im-iam-sdk

im-web -X-> im-session / im-iam-sdk / im-admin / im-monitor
```

`im-session` 通过可选 Servlet/MVC 依赖提供 Web 适配，非 Servlet 应用不会创建对应适配组件；该实现不需要
`im-session -> im-web` 依赖，也不为一个拦截器新建 Maven 模块。

## 4. `im-web` 职责

`im-web` 保留：

- `ResultAdvice` 与统一 `HttpResult` 响应；
- 通用异常到 `HttpResult` 的映射；
- Jackson、`BaseEnum` 和 MVC 参数转换；
- HTTP 日志、MDC、Servlet 工具；
- 可配置且默认安全的 CORS 能力；
- HTTP 请求元数据上下文。

`im-web` 只向 Spring Boot 自动配置清单暴露 `ImWebAutoConfiguration`。结果处理、Jackson、日志、请求上下文、
MVC 扩展和 CORS Bean 由该入口集中注册；需要独立条件边界的能力使用入口类内部的条件化配置分组。`im-session`
同样只暴露 `ImSessionAutoConfiguration`，并在该文件内部隔离 Servlet 适配配置。这样保留一个清晰的插件入口，
同时不改变 CORS 显式启用、Servlet 环境限定和应用自定义 Bean 覆盖等装配语义。

一个插件是否收敛为单个自动配置入口属于模块内部组织判断，不作为 Harness 的全局强制规则。仅当配置类数量少、
职责紧密且合并后仍能清晰保留条件边界时采用；复杂插件仍可按独立能力拆分自动配置类。

### 4.1 请求上下文

以下 Admin 类型泛化并迁入 `im-web/context`：

| 当前类型 | 目标类型 |
|---|---|
| `AuditRequestContext` | `HttpRequestContext` |
| `AuditRequestContextHolder` | `HttpRequestContextHolder` |
| `AuditRequestContextInterceptor` | `HttpRequestContextFilter` |

`HttpRequestContext` 只保存客户端地址和 User-Agent 等客观请求属性，不保存管理员、权限或其他安全身份。上下文由 Servlet Filter 在 Spring Security 之前建立，使登录、OAuth 等不进入 MVC Controller 的端点同样可读取，并在请求结束时统一清理。
拦截器负责建立上下文并在请求完成后可靠清理。Admin 审计 AOP 将请求属性转换为自己的
`AuditClientAddress` 和 `AuditUserAgent` 领域值对象。

### 4.2 统一异常协议

`ErrorAdvice` 统一处理参数错误、`BaseException`、RPC 边界异常和未知异常。`AdminExceptionHandler`、
`MonitorExceptionHandler` 中与应用无关的处理不再重复实现；统一后删除对应的独立错误响应对象。

Spring Security 未登录与无权限响应由 `im-iam-sdk` 按同一 `HttpResult` 协议输出。OAuth 回调、重定向、
资源下载和流式响应继续绕过普通响应包装。

浏览器自动请求的缺失静态资源由 `im-web` 按统一 `HttpResult` 返回 NOT_FOUND，不进入未知异常 ERROR
日志。favicon 等固定资源是否存在由各 UI 构建产物负责，不在通用插件内伪造业务资源。

favicon 与 `/assets/**` 属于高频静态资源，不生成完整访问日志；页面、认证入口和业务 API
继续保留请求与响应日志。

## 5. Session Web 适配

当前 `UserContextInterceptor` 的职责是把可信 HTTP Header 中的 `userId`、`sessionVersion` 转换为
请求范围内的 `UserContext`，并在请求结束后清理上下文。

它不再属于通用 `im-web`。实施前先确认 Account、Social、Message 等服务的实际调用链：

- 仍需要 Servlet Header 适配时，将其放入现有 `im-session/web`；
- 如果身份已通过明确 RPC 参数传递且没有真实调用方，则删除该拦截器；
- 不创建 `im-session-web` Maven 模块；
- `/user/signIn`、`/user/signUp` 等业务公开路径不得硬编码在插件中，应由入口安全策略或应用配置拥有。

## 6. Admin 保留能力

以下类型继续留在 `im-admin`：

- `AdminAudited`、`AdminAuditAspect`：依赖 Admin 审计聚合、Repository 和错误语义；
- `ImAdminLockScene`：表达 Admin 业务锁场景；
- `AdminPermission`：表达 Admin 向 IAM 注册的权限目录。

这些类型虽然位于 `support`，但仍包含明确的 Admin 业务语义。插件抽取以业务无关和可复用为前提，不能把
整个 `support` 目录机械迁移。

## 7. 请求流程

C 端服务：

```text
请求 -> im-web 日志/MDC -> Session 身份适配 -> Controller
     -> ResultAdvice -> HttpResult -> 清理请求上下文
```

Admin 与 Monitor：

```text
请求 -> im-web 日志/MDC和请求上下文 -> im-iam-sdk 恢复并校验应用 Session
     -> Spring Security -> Controller -> ResultAdvice -> HttpResult
```

## 8. Harness 与验证

本设计形成以下可复用规则：

- `im-web` 不得依赖 Session、IAM 或运行时业务模块；
- 插件不得硬编码业务 Controller 路径；
- 可部署应用采用 `HttpResult` 时，不得另建结构重复的全局错误响应协议；
- 请求上下文 Filter 必须在安全过滤链前建立上下文，并在 `finally` 中清理；
- 审计、权限和锁场景等业务语义不得仅因复用意图下沉到插件。

`im-web` 的依赖方向通过 ArchUnit 和 Maven 依赖测试机械执行。业务 Controller 路径、重复响应协议和业务语义
归属需要理解配置用途与接口语义，当前作为 Review 规则；只有能够结构化识别 Controller 映射及其应用所有权，
并覆盖 Actuator、Swagger、OAuth 回调等技术路径的正反 fixture 后，才升级为自动检查。实施需覆盖 `im-web`、
Admin、Monitor 和 IAM SDK 的聚焦测试，并最终执行完整验证。
