# im-plugin

`im-plugin` 是技术能力聚合模块。子模块通过 Spring Boot AutoConfiguration 或轻量 SPI 向运行服务提供能力，不承载 IM 业务规则。

| 模块 | 职责 |
|------|------|
| `im-bolt` | SOFA Bolt 客户端、服务端和请求处理 SPI |
| `im-cache` | JetCache/Redisson 缓存工厂与默认配置 |
| `im-datasource` | MyBatis、事务回调和 ShardingSphere 配置模型 |
| `im-dubbo` | Dubbo + Nacos Registry 自动配置 |
| `im-gossip` | digest/delta 最终一致性同步算法 |
| `im-lock` | Redisson 分布式锁和注解切面 |
| `im-metrics` | 基于 Micrometer 和 Spring AOP 的声明式方法指标采集 |
| `im-mq` | 消息发布订阅 SPI 及内存实现 |
| `im-nacos` | Nacos 配置中心和服务发现公共配置 |
| `im-session` | JWT 与用户上下文 |
| `im-web` | MVC 响应、异常、日志和用户上下文适配 |

引入插件即启用其自动配置时，应优先通过插件配置项覆盖默认值，不在业务模块重复装配同类基础 Bean。

## 关键技术点

- 插件通过 AutoConfiguration 暴露能力，并使用 `@ConditionalOnMissingBean` 保留业务覆盖入口。
- `im-metrics` 通过 `@Observed` 记录方法调用次数、失败次数和执行耗时，业务模块不直接组装 Micrometer 指标。
- `META-INF/config` 中的配置只提供低优先级默认值，本地配置、启动参数和配置中心可以覆盖。
- SPI 模块隔离业务代码与具体中间件；业务服务依赖抽象或插件入口，不直接散落客户端初始化逻辑。

```bash
mvn -q -pl im-plugin -am test
```
