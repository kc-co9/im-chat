# im-web

Servlet MVC 服务公共适配插件。

## 能力

- `ResultAdvice`、`ErrorAdvice`：统一 HTTP 成功与异常响应。
- 枚举 Converter：将请求参数代码转换为 `BaseEnum`。
- `LoggingFilter`、`MdcFilter`、`HttpLoggingInterceptor`：请求日志和 Trace 上下文。
- `UserContextInterceptor`：从内部可信请求头恢复用户上下文。

本模块面向 Spring MVC，不适用于 WebFlux Gateway。业务 Controller 保持业务入参与返回值语义，统一包装由 Advice 完成。

## 关键技术点

- ResponseBodyAdvice 统一包装普通返回值，避免 Controller 重复构造 HTTP 响应模型。
- Exception Advice 将异常层次映射为稳定错误码，同时保留服务端日志上下文。
- Filter 负责 Trace/MDC，Interceptor 负责 MVC 用户上下文，职责按 Servlet 生命周期分离。
- 枚举 Converter 通过 `BaseEnum` 统一代码值解析和非法参数处理。

```bash
mvn -q -pl im-plugin/im-web -am test
```
