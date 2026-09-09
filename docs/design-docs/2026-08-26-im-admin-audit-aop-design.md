# IM Admin 声明式审计与安全上下文设计

> 历史说明：本文记录了 Admin 本地审计阶段的设计。当前审计所有权、投递和失败语义以
> [集中管理审计设计](2026-08-28-central-management-audit-design.md)为准；Admin 不再持有本地审计表。

## 背景

`AccountAdminAdapter` 曾直接把两种 Account Facade DTO 构造成 `ManagedUser`，Admin 写用例也曾显式注入审计执行器并用 Lambda 包裹业务操作。第一版声明式审计又把操作者、权限、客户端信息放入每个 Command，导致业务输入被 HTTP 安全上下文污染，并重复实现了 Spring Security 已有的授权能力。

## 目标

- Account Facade DTO 到本地防腐模型的转换统一交给 MapStruct Transformer。
- Admin 写用例使用声明式审计，应用服务只表达业务编排。
- 管理员认证与授权统一由 Spring Security 管理，不自建权限注解和 MVC 权限拦截器。
- Command 只携带业务边界值，不携带审计、HTTP 或安全上下文。
- 仅将客户端地址和 User-Agent 等非安全元数据放入可跨线程传播的请求上下文。
- 保持审计先行、成功/失败终态、错误码映射和最终状态写入失败隔离语义。

## 设计

### Adapter 转换

`ManagedUserDomainTransformer` 分别声明 `AccountUserDTO` 和 `AccountUserListDTO` 到 `ManagedUser` 的映射。`AccountAdminAdapter` 只组织 Facade 参数、调用远程契约和调用 Transformer。

### Spring Security 授权

`AdminAuthenticationFilter` 校验独立的管理员 Session 和 CSRF 后，将 `AdminPrincipalDTO` 及其权限码转换为 Spring Security Authentication。管理端 HTTP 写入和查询方法使用 `@PreAuthorize` 声明权限，UI 权限只控制菜单与按钮展示，不作为安全边界。

管理员身份和 Authorities 只存在于 `SecurityContext`。不再保留 `@RequirePermission`、`AdminPermissionInterceptor` 或 Request Attribute 身份副本。

### 非安全请求上下文

`AuditRequestContext` 只包含客户端地址和经过长度限制的 User-Agent，`AuditRequestContextHolder` 使用 Alibaba TTL 保存。请求拦截器在进入 Controller 前建立上下文，并在请求完成后清理。需要在线程池传播时必须显式使用 TTL 包装的 Executor；管理员身份不随该上下文传播。

### 声明式审计

`@AdminAudited` 只声明审计动作、目标类型、目标表达式和安全描述。它不声明权限，也不复制操作者或客户端字段。

`AdminAuditAspect` 的处理顺序为：

1. 从 `AuditRequestContextHolder` 获取客户端元数据。
2. 普通管理操作从 Spring Security 获取操作者；登录操作按 Command 用户名记录，并在用户名存在时补全管理员 ID。
3. 根据注解中受控的 `#command` 表达式提取审计目标。
4. 在业务方法前保存 `PENDING`。
5. 成功后回写 `SUCCESS`；运行时异常或 Error 时回写 `FAILED` 并原样抛出。
6. 最终状态回写失败只记录 warn，不改变已经发生的业务结果。

审计记录中的认证事件使用固定授权依据；其他操作记录由 Spring Security 完成授权这一事实，不把权限策略重新维护在审计注解中。

## 边界与非目标

- 不把 Admin 专属审计抽到通用 Plugin。
- 不把 Spring Security 身份放入 TTL、自定义 ThreadLocal 或 Command。
- 不通过 TTL Java Agent 隐式增强全局线程池。
- 不让审计切面承担领域规则、状态机或跨聚合编排。
- 不改变审计先行、终态回写和异常传播等既有运行语义。

## 审计领域模型收敛

审计领域模型必须保证安全描述和生命周期约束不能被调用方绕过：

- `AuditDescription` 在构造时统一取首行、脱敏敏感键并限制为 512 字符，不再依赖调用方选择特殊工厂方法。
- `AuditUserAgent` 自身限制为 255 字符；`AuditClientAddress` 和其他持久化文本值对象按照表字段容量维护约束。
- `AdminAudit` 使用 `@EqualsAndHashCode(callSuper = false)`，由聚合自身字段表达相等性，不单独定制业务 ID 专属相等性。
- 审计使用双 ID：`BaseEntity.id` 是数据库自增主键，`AuditId` 是 Snowflake 生成的领域业务身份；首次插入回填 `pkId`，技术主键不参与聚合相等性。
- `succeed`、`fail` 拒绝早于审计创建时间的完成时间。
- 审计记录不保存授权来源：IAM 负责身份认证与权限提供，Spring Security 负责本地授权执行，两者并非互斥来源；操作动作已经表达登录、登出和业务管理事实。
- 删除仅用于切面字段转运的 `AuditRequest`；切面直接使用聚合 Builder 创建待处理审计。
- 聚合参与 Java 序列化时，组成它的值对象必须同步可序列化。

代码、持久化字段和 HTTP 响应均不保留 `permission` 或 `authorizationSource`。如果未来需要审计授权证据，应记录实际校验的权限码；如果需要记录认证方式，应使用独立的认证方式模型。当前功能仍保持审计先行、成功/失败终态以及最终状态写入失败隔离，不增加兼容字段。

## 验证

- Transformer 测试覆盖两种 Facade DTO。
- 权限测试覆盖 Authority 写入、允许与拒绝路径，并由端点清单测试约束管理端方法必须声明 `@PreAuthorize`。
- 请求上下文测试覆盖显式 TTL 线程池传播以及清理后不泄漏，且上下文模型不包含管理员身份。
- Aspect 测试覆盖审计先行、成功、异常错误码、最终状态写入失败、目标表达式和登录身份补全。
- 应用服务测试证明 Command 不再携带审计字段，业务返回和异常保持不变。
