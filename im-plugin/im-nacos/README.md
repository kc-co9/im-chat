# im-nacos

Nacos 配置中心与服务发现插件。引入依赖后会自动加载公共 Nacos 默认配置，无需在业务 `application.yml` 再导入插件自身配置文件。

## 默认规划

- Namespace：`im-chat-box` 对应的 Namespace ID。
- 服务发现 Group：`IM_CHAT_GROUP`。
- 公共配置：`common.yml` / `COMMON_GROUP`。
- 各服务配置由应用自行导入 `SERVICE_GROUP` 或 `INFRA_GROUP`。

核心配置位于 `META-INF/config/im-nacos.yml`，可通过本地配置、启动参数或 Nacos 配置覆盖。非 Web 应用必须配置 `spring.cloud.nacos.discovery.port`，插件会在应用启动阶段主动注册。`spring.cloud.discovery.enabled=false` 或 `spring.cloud.nacos.discovery.enabled=false` 可关闭发现扩展。

## 关键技术点

- EnvironmentPostProcessor 在 ConfigData 处理前注入 `spring.config.import`，引入插件即可加载公共配置。
- Namespace 使用 ID 而不是控制台显示名称，环境之间通过 Namespace 隔离。
- Web 服务沿用 Spring Cloud 自动注册；非 Web 服务在 `ApplicationStartedEvent` 主动触发注册。
- Validator 在发现启用时校验注册基础设施和注册开关，避免应用启动成功但没有进入 Nacos。

```bash
mvn -q -pl im-plugin/im-nacos -am test
```
