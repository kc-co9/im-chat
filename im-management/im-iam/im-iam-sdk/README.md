# IM IAM SDK

管理应用的 OAuth2/OIDC BFF 接入组件。它负责登录回调、AES-GCM 加密 Redis Session、
CSRF、实时 Token Introspection、最多五分钟的 stale-if-error、权限目录注册和 Spring
Security 主体构建。

机器接口可以复用 `IamOpaqueTokenAuthenticationConverter` 校验标准 `aud`，并建立
`IamApplicationPrincipal`；接入服务无需重复声明自己的 Opaque Token Converter 和应用身份类型。

SDK 只依赖协议和通用基础设施，不依赖 `im-iam-server` 实现。接入配置以当前管理应用为所有者，
在 `im.iam.application.clients` 下分别声明 Web Client 与 Catalog Client：

```yaml
im:
  iam:
    enabled: true
    issuer: http://localhost:18090
    application:
      key: imAudit
      clients:
        web:
          client-id: im-audit-client
          client-secret: REQUIRED_FROM_NACOS
          redirect-uri: http://localhost:18091/iam/callback
          post-logout-redirect-uri: http://localhost:18091/
        catalog:
          client-id: im-audit-catalog
          client-secret: REQUIRED_FROM_NACOS
      session:
        encryption-key: REQUIRED_FROM_NACOS
        secure-cookie: true
```

Web Client 负责浏览器 BFF 登录和 Introspection；Catalog Client 负责权限目录同步，必须是当前
应用拥有、以 `imIam` 为 audience、仅允许 `CLIENT_CREDENTIALS` 和 `iam.catalog.write` 的机器
客户端。两者不能复用。Session 加密与 Cookie 配置属于当前应用，不属于任一 OAuth Client。
Issuer 只有在 loopback 本地开发时允许 HTTP，其他环境必须使用 HTTPS；`im.iam.http` 下的连接与
读取超时同时约束 SDK 请求和接入服务使用该配置建立的 IAM Introspection 请求。
SDK 始终创建并显式注入独立的 `iamRestClient`。宿主应用可以同时声明 Broker、Audit 等其他
`RestClient`，但这些 Client 不会替代 IAM Client，也不能影响 IAM issuer 的基础地址解析。

`/iam/login?continue=...` 的站内恢复地址与 PKCE verifier 一起加密保存在一次性 OAuth state
记录中，回调消费 state 后跳回该页面。SDK 只接受以 `/` 开头且不以 `//` 开头的相对路径，
无效值统一回退 `/`，避免形成开放重定向。
使用 Hash Router 的接入应用在会话失效后重新发起登录时，应把当前 Hash 路由转换为上述
`continue` 参数，不能固定跳转 `/iam/login`，否则回调只能回到应用根页并丢失原访问位置。

## 包结构

- `oauth` 保存 OAuth 授权客户端、授权请求和会话编排；其不可变 Token 契约放在
  `oauth.model`。
- `introspection` 保存 Token Introspection 客户端、缓存和可用性控制；结果契约放在
  `introspection.model`。
- `session` 保存 BFF Session 契约、加密和 Redis Repository，分别位于 `session.model`、
  `session.crypto` 和 `session.repository`。
- `security` 保存 Spring Security 适配；框架无关的认证主体放在 `security.model`。
- BFF Controller 与 HTTP 异常位于 `interfaces.http`；权限目录相关健康状态属于 `catalog`。

SDK 采用按稳定能力分包，不套用服务端的 `application/domain/infrastructure` 分层；自动配置
仍由根包下的 `ImIamSdkAutoConfiguration` 统一提供默认组件。

`/iam/me`、`/iam/logout` 和 `/iam/platform-logout` 都要求有效 BFF Session。未携带 Session
或 Session 已失效时，SDK 按统一 `HttpResult` 返回认证失败，由前端跳转 `/iam/login`，不作为
服务端系统异常记录。

页面入口、`index.html`、favicon、`assets`、错误页、`/iam/login` 和 `/iam/callback` 是公共
前端请求，不读取应用 Session，也不执行 Token Introspection。`/iam/**` 的其他端点和
`/api/**` 必须认证；其他未声明路径默认拒绝。
