# 安全规范

## 信任边界

- 外部 HTTP 流量统一进入 `im-http-gateway`。
- 外部 WebSocket 流量统一进入 `im-ws-gateway`，业务帧必须在认证成功后才可接收。
- HTTP 请求和 WebSocket 握手通过 `im-account` 在线认证 Access Token；超时、线程池饱和、结果格式错误或 Provider 失败时，Gateway 必须 fail closed。
- Gateway 删除外部传入的用户与 Session Header，只根据 Account 认证结果重新构建。
- Broker、Gateway 和 Service RPC 都是内部接口，不得作为公共客户端 API 暴露。
- IAM 保护的 RPC 只通过 `im-dubbo` 保留 Attachment 携带 Access Token。Provider 必须在业务 Adapter 前完成认证与 Scope 检查；认证身份来自 Spring Security，不来自 RPC Params。Filter 每次调用后恢复 Attachment 和安全上下文，不记录 Token 值。
- Broker management HTTP 只服务运维。默认监听 loopback，只能向 `im-monitor` 所在运维网络开放；浏览器必须通过 `im-monitor` 访问 Broker 诊断。
- `im-iam-server` 是管理凭证和 RBAC 的唯一权威。Admin 与 Monitor 只接受 IAM 支持的 BFF Session，不接受普通 Account Token。
- `im-audit-server` 是管理 Audit 持久化的唯一权威。浏览器 API 使用 IAM BFF Session；内部 HTTP 接收仅接受具有 `audit:ingest` 的专用 Client Credentials Token。
- Service Facade 与 SDK 定义传输契约，敏感领域模型或持久化模型不得通过这些边界泄漏。

## 配置与密钥

- 不得提交生产凭证、Token、私钥和环境专属 Namespace ID。
- `im-account` 是唯一 Token 签发方和 Session 认证权威。启用 JWT 时，任何 Profile 下缺少密钥或密钥少于 64 个 UTF-8 字节都必须启动失败。
- 仓库配置可包含非生产本地密钥以保证确定性启动；所有部署环境必须通过 Nacos 覆盖，且不得将其复用为生产密钥。
- 认证与授权日志不得包含密码、Access/Refresh Token、Refresh fingerprint/digest、完整认证 Body、用户 ID 或 Session version。
- 管理应用 Session ID 使用 `HttpOnly + Secure + SameSite` Cookie；命令还要求与 Session 绑定的 CSRF Cookie/Header。可用 OAuth2 Token 在写入 Redis 前使用 AES-GCM 加密。
- IAM 管理员暴力破解防护是带原子失败计数与 TTL 的 Redis 临时状态，不是管理员业务状态。Redis 失败时 fail closed；认证成功或管理员安全属性变化时清除临时状态。
- IAM Access Token 为不透明 Token，有效期 15 分钟；轮换 Refresh Token 有效期 8 小时。本地开发可使用仓库公开的 classpath PKCS12 和 loopback HTTP issuer；生产必须覆盖为 HTTPS issuer、外部挂载 PKCS12 和部署侧密钥，密钥材料无效时 fail closed。
- IAM OAuth Client 区分所属应用和唯一目标应用。Access Token metadata 用 `appKey` 暴露所属应用，用标准 `aud` 表达目标；资源服务必须拒绝 audience 不包含自身应用 Key 的 Introspection 结果。
- Spring Authorization Server 负责 Authorization Code 单次使用和 Refresh Token 轮换。复用或未知凭证返回 `invalid_grant`；IAM 不保存历史 Refresh Token 索引，也不额外实现 authorization-family replay 撤销。
- IAM SSO 到达绝对过期时间时，必须在当前请求加载 SecurityContext 前使 Session 失效。管理员认证对已知和未知邮箱执行等量 BCrypt 校验工作。
- 管理员 Bootstrap 凭证是一次性部署输入。首个超级管理员创建后立即从 Nacos 删除密码，不得写入 SQL 或仓库配置。
- Kafka 凭证和生产 IAM Client Secret 由部署环境拥有。仓库默认值仅用于公开本地开发，不提供生产信任；每个 Producer 只能写入自己的来源 Topic。

## 管理端授权

- 每个管理端点都由应用自有的 Spring Security Authority 和 `@PreAuthorize` 保护；隐藏 UI 控件只改善体验，不构成授权。管理员权限在 Access Token 签发或刷新时固化，高风险账号变化会撤销当前授权。
- 只有 IAM 管理管理员凭证、浏览器应用、机器 Client 和角色。Admin 拥有普通用户操作，Monitor 拥有只读诊断，Audit 拥有全部管理审计事实。
- 内置角色不可修改或删除。角色和管理员变化会撤销受影响的管理员 Session，最后一个活跃超级管理员不可禁用。
- 管理审计注解和显式认证/协议 Publisher 不序列化参数、Body、返回值、凭证、Cookie、Session/CSRF 标识、Token、SQL 或异常堆栈。Audit 投递失败与原业务结果隔离。
- Audit 传输 Payload 不声明 `sourceApp`。HTTP 接收根据已认证机器 Client 推导，Kafka 接收由来源专属 Binding 注入；Producer 控制的 Payload 不能覆盖这一可信事实。

## Session 撤销

- Access Token 和 Refresh Token 只有在 session version 与当前在线 Account Session 一致时才可接受。
- Refresh Token 原子轮换；复用凭证或并发竞争失败的凭证被拒绝，但不使竞争成功的新凭证失效。
- 登出和 Session 替换先提交 Session 状态，再发布 best-effort WebSocket 关闭控制；控制投递不是认证权威。

## 变更要求

- 新增入口必须明确认证和授权决策。
- Broker 诊断保持只读，只接受单用户路由查询，不得暴露全量路由、消息 Payload、凭证、完整堆栈或写操作。
- Broker management、Actuator 和 OpenAPI 路径不要求普通用户 Session Header，默认绑定 loopback。远程开放必须通过部署网络策略限制，因为 User Context Header 不是管理认证。
- 新增跨进程序列化类型必须提供输入校验和兼容性测试。
- 新依赖和 Plugin 自动配置只能作用于确实需要它们的模块。
- 安全敏感修复必须包含聚焦回归测试；信任模型变化时同步更新本文。

安全漏洞应私下报告给仓库 Owner，不要创建包含利用细节的公开 Issue。
