# im-dubbo

Dubbo 内部服务调用插件，统一 Application、Protocol、Registry 和 Consumer 配置，并使用 Nacos 作为注册中心。

## 配置

默认值位于 `META-INF/config/im-dubbo.yml`：

```yaml
im:
  dubbo:
    enabled: true
    registry:
      address: nacos://127.0.0.1:8848
      namespace: ""
      group: DUBBO_GROUP
    protocol:
      name: dubbo
      port: -1
    consumer:
      timeout: 3000
      check: false
```

`im.dubbo.enabled=false` 会关闭整个自动配置和 `@DubboService` 扫描。Registry 地址不能为空；Namespace 默认复用 `im.nacos.namespace`。这些配置在应用启动时创建 Dubbo 对象，远程修改后不会自动重建运行中的 Dubbo Registry。

## 关键技术点

- Facade 接口作为 Dubbo 契约，Provider 通过 `@DubboService` 暴露，Consumer 只依赖 Facade。
- `RpcExceptionAspect` 统一拦截所有 Dubbo Provider：保留已有 `RpcException`，将 `BaseException` 转换为仅含稳定错误码和安全消息的 `RpcException`，并记录、隐藏未知运行时异常。业务 Provider 不需要逐个编写异常转换模板。
- `imRpcTokenConsumer` 从通用 `RpcAccessTokenProvider` 取得 Access Token，只在当前调用期间写入保留 Attachment；`imRpcTokenProvider` 使用 `RpcTokenAuthenticator` 完成认证，并只在当前 Provider 调用期间建立 Spring Security 上下文。两个过滤器都会在 `finally` 中恢复调用前状态，且不记录 Token。
- 业务 RPC Params 不承载 Access Token、调用方应用或权限；Provider 在进入接口适配器前完成认证，接口适配器从 `SecurityContext` 获取可信身份。业务模块负责提供 Token 获取与认证实现，`im-dubbo` 不依赖 IAM 或具体业务。
- Registry 使用独立 `DUBBO_GROUP`，与 Spring Cloud 服务发现数据分开管理。
- `im.dubbo.enabled` 在自动配置边界生效，关闭后不扫描 Provider，也不创建网络组件。
- Registry、Protocol 等属于启动期配置；Nacos 修改后需要重启服务才能完整生效。

```bash
mvn -q -pl im-plugin/im-dubbo -am test
```
