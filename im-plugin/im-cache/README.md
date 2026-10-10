# im-cache

`im-cache` 封装 JetCache 缓存创建方式，并包含 Redisson 远程缓存和 Caffeine 本地缓存所需依赖。

## 核心组件

- `JetCacheFactory`：按 `CacheSpec` 创建本地、远程或多级缓存。
- `local(name, expire, capacity)`：返回 JetCache `Cache`，指定本地容量与写入 TTL；不额外包装缓存类型。
- `JetCacheFactory.localValues(cache)`：读取上述缓存的有效值快照，适合需要枚举本机历史的使用方。
- `CacheSpec`：缓存名称、过期时间和缓存类型描述。
- `im-cache.yml`：JetCache 默认配置，由 EnvironmentPostProcessor 自动加载，本地或 Nacos 配置可覆盖。

使用远程缓存前必须提供有效 Redis/Redisson 配置，否则 JetCache 无法创建 `default` remote builder。

## 关键技术点

- `CacheSpec` 将缓存名称、类型和 TTL 从业务 Bean 创建代码中抽离。
- 本地缓存使用 Caffeine，远程缓存使用 Redisson，多级缓存由 JetCache 组合。
- 默认配置由 EnvironmentPostProcessor 在 ConfigData 之前加载，应用配置拥有更高优先级。
- 缓存只作为仓储装饰层，不能成为业务事实的唯一存储。
- 本地快照依赖 JetCache 默认 Caffeine 实现及原始键转换，不能用于远程、多级或自定义键转换缓存。
  只从代理枚举键，值经 JetCache `getAll` 过滤过期数据，返回不可变快照；并发写入可能在下一次快照中可见。
- 容量淘汰由框架决定，不保证 FIFO 或最近 N 个业务版本；调用方不能将缓存顺序当作业务事实。

```bash
mvn -q -pl im-plugin/im-cache -am test
```
