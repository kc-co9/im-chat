# im-web

Servlet MVC 服务公共适配插件。

## 能力

- `ResultAdvice`、`ErrorAdvice`：统一 HTTP 成功与异常响应。
- 枚举 Converter：将请求参数代码转换为 `BaseEnum`。
- `LoggingFilter`、`MdcFilter`、`HttpLoggingInterceptor`：请求日志和 Trace 上下文。
- `HttpRequestContextFilter`：在安全过滤链之前建立客户端地址、User-Agent 等非安全请求元数据，并在请求结束时清理。
- `WebCorsProperties`：通过 `im.web.cors` 提供默认关闭、显式允许列表的 CORS 配置。
- Springdoc WebMVC UI：统一提供 OpenAPI 文档运行时依赖，具体文档路径、扫描范围和访问权限由应用配置。

本模块面向 Spring MVC，不适用于 WebFlux Gateway。业务 Controller 保持业务入参与返回值语义，统一包装由 Advice 完成。

Spring Boot 仅通过 `ImWebAutoConfiguration` 一个入口装配本模块能力。CORS 仍需显式启用，应用自行声明的可覆盖
Bean 仍优先于插件默认实现。

## 关键技术点

- ResponseBodyAdvice 统一包装普通返回值，避免 Controller 重复构造 HTTP 响应模型。
- Exception Advice 将异常层次映射为稳定错误码，同时保留服务端日志上下文。
- 缺失静态资源按统一 `HttpResult` 返回 NOT_FOUND，不作为系统异常打印 ERROR 堆栈。
- 常见静态资源后缀和 `/assets/**` 不生成完整访问日志；无后缀路径仅在 MVC 实际解析为静态资源 Handler 时跳过，Controller、认证入口和业务 API 继续记录请求与响应信息。
- Filter 负责 Trace/MDC 和通用请求元数据，使登录、OAuth 等由安全过滤链直接处理的端点也能读取请求上下文。
- 枚举 Converter 通过 `BaseEnum` 统一代码值解析和非法参数处理。
- Springdoc 只提供通用运行时能力，不默认公开文档路径；应用的 SecurityFilterChain 仍决定文档是否需要认证。
- 可部署 MVC 应用显式配置 `/api/doc.html` 和 `/v3/api-docs`；SDK、Plugin 与 Facade 不声明应用路由。
- 模块不解析 Session 或管理身份，也不持有业务 Controller 路径。Session Web 适配由 `im-session` 提供。

## CORS 配置

```yaml
im:
  web:
    cors:
      enabled: true
      allowed-origin-patterns:
        - https://admin.example.com
```

未显式启用时不注册全局 CORS 映射。生产环境使用明确来源列表，不使用通配来源与凭证组合。

```bash
mvn -q -pl im-plugin/im-web -am test
```
