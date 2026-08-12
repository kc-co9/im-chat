# im-bolt

基于 SOFA Bolt 的内部 RPC 插件，统一客户端调用、服务端启动和请求分发。

## 核心组件

- `BoltInvoker`：调用抽象。
- `BoltClientInvoker`、`BoltRpcClient`：同步 RPC 客户端实现。
- `BoltRequestHandler`：业务 Handler SPI。
- `BoltRequestProcessor`：按 service/operation 分发请求。
- `ImBoltProperties`：客户端、服务端端口和超时配置。

依赖方只实现 `BoltRequestHandler`，不直接操作 SOFA Bolt 请求对象。启用服务端时使用 `im.bolt.server.enabled=true` 并配置端口。

## 关键技术点

- 请求使用 `service + operation` 两级标识分发，避免 Handler 依赖传输层命令对象。
- `BoltRequestProcessor` 建立 Handler 索引并统一处理 JSON 请求和响应。
- 客户端与服务端可独立启用，适配只调用、只提供或双向 peer 场景。
- 显式依赖 Hessian，避免 Bolt 默认序列化器在运行时缺类。

```bash
mvn -q -pl im-plugin/im-bolt -am test
```
