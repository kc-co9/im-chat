# im-tracing

SkyWalking Java Agent 的日志和代码链路关联插件。模块提供 Logback toolkit、Trace toolkit、公共 include 和统一的 `TracingUtils.currentTraceId()` 入口，不包含 Agent 二进制。

Server 自有的 `logback-spring.xml` 引入 `com/co/kc/imchat/plugin/tracing/logback-common.xml`，统一输出 SkyWalking `%tid` 和 MDC `traceId`。服务可在本地覆盖 appender、日志级别和 pattern；公共默认使用控制台 appender。通过 `IM_LOG_PATTERN` 系统/环境属性，或在 include 前声明同名 Logback property，可覆盖公共 pattern。

```bash
mvn -q -pl im-plugin/im-tracing -am test
```
