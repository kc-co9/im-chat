# im-plugin

`im-plugin` 是技术能力聚合模块。子模块通过 Spring Boot AutoConfiguration 或轻量 SPI 向运行服务提供能力，不承载 IM 业务规则。

| 模块 | 职责 |
|------|------|
| `im-bolt` | SOFA Bolt 客户端、服务端和请求处理 SPI |
| `im-cache` | JetCache/Redisson 缓存工厂与默认配置 |
| `im-datasource` | MyBatis、事务回调和 ShardingSphere 配置模型 |
| `im-dubbo` | Dubbo + Nacos Registry 自动配置和 Provider 异常隔离 |
| `im-excel` | 基于 Apache Fesod 的通用流式 Excel 导出能力 |
| `im-gossip` | digest/delta 最终一致性同步算法 |
| `im-identity` | Redis 租约驱动的 Snowflake 机器 ID 动态分配 |
| `im-lock` | Redisson 分布式锁和注解切面 |
| `im-metrics` | 基于 Micrometer 和 Spring AOP 的声明式方法指标采集 |
| `im-mq-kafka` | Spring Cloud Stream Kafka Binder 运行时集成 |
| `im-nacos` | Nacos 配置中心和服务发现公共配置 |
| `im-session` | JWT、用户上下文及可选 Servlet 身份适配 |
| `im-web` | 通用 MVC、`HttpResult`、异常映射、日志、CORS 和请求元数据 |

引入插件即启用其自动配置时，应优先通过插件配置项覆盖默认值，不在业务模块重复装配同类基础 Bean。

## 关键技术点

- 插件通过 AutoConfiguration 暴露能力，并使用 `@ConditionalOnMissingBean` 保留业务覆盖入口。
- `im-dubbo` 将 Provider 的 `BaseException` 统一转换为不含内部原因的 `RpcException`，未知异常记录完整日志后只暴露系统错误；业务 RPC 实现不重复编写 `try/catch` 翻译模板。
- `im-metrics` 通过 `@Observed` 记录方法调用次数、失败次数和执行耗时，业务模块不直接组装 Micrometer 指标。
- `im-excel` 管理 Writer 生命周期、分批写入和通用转换器；业务模块保留行模型、权限、查询和导出规则。
- `META-INF/config` 中的配置只提供低优先级默认值，本地配置、启动参数和配置中心可以覆盖。
- 基础设施插件隔离业务代码与具体中间件；Kafka 业务契约和 Binding 归使用方所有，具体 Binder 运行时由 `im-mq-kafka` 提供。
- `im-web` 不依赖 Session、IAM 或业务模块；插件不拥有业务公开路径，使用方通过类型化配置或安全策略声明。

```bash
mvn -q -pl im-plugin -am test
```
