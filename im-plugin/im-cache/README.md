# im-cache

`im-cache` 封装 JetCache 缓存创建方式，并包含 Redisson 远程缓存和 Caffeine 本地缓存所需依赖。

## 核心组件

- `JetCacheFactory`：按 `CacheSpec` 创建本地、远程或多级缓存。
- `CacheSpec`：缓存名称、过期时间和缓存类型描述。
- `im-cache.yml`：JetCache 默认配置，由 EnvironmentPostProcessor 自动加载，本地或 Nacos 配置可覆盖。

使用远程缓存前必须提供有效 Redis/Redisson 配置，否则 JetCache 无法创建 `default` remote builder。

## 关键技术点

- `CacheSpec` 将缓存名称、类型和 TTL 从业务 Bean 创建代码中抽离。
- 本地缓存使用 Caffeine，远程缓存使用 Redisson，多级缓存由 JetCache 组合。
- 默认配置由 EnvironmentPostProcessor 在 ConfigData 之前加载，应用配置拥有更高优先级。
- 缓存只作为仓储装饰层，不能成为业务事实的唯一存储。

```bash
mvn -q -pl im-plugin/im-cache -am test
```
