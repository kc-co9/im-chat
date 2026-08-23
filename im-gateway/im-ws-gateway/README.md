# im-ws-gateway

## 模块作用

`im-ws-gateway` 是 WebSocket 网关聚合模块，按“调用 SDK”和“运行服务”拆分子模块。

它负责支撑浏览器客户端的实时连接接入，以及 Broker 到 WS 网关的内部推送调用。

## 模块结构

```text
im-ws-gateway/
  im-ws-gateway-sdk/       # WS 网关内部调用 SDK
  im-ws-gateway-server/    # WS 网关运行服务，基于 Netty 接入 WebSocket
  pom.xml                  # WS 网关聚合 POM
```

## 子模块职责

- `im-ws-gateway-sdk`：定义 Broker 推送到指定连接所需的参数、DTO、Bolt RPC 标识和调用客户端。
- `im-ws-gateway-server`：启动 Netty WebSocket Server，维护本机连接，注册网关信息到 Broker，并处理 Broker 下行推送。

## 核心链路

上行消息：

```text
web client
  -> Netty WebSocket
  -> im-ws-gateway-server
  -> Broker
  -> message service
```

下行推送：

```text
message service
  -> Broker
  -> im-ws-gateway-sdk client
  -> im-ws-gateway-server
  -> web client
```

客户端对需确认通知的 ACK 作为上行帧沿同一路径返回 Message service。Gateway 只转发 ACK，不保存回执任务，也不决定重投。

## 边界说明

- WS 网关只负责连接、协议帧和路由转发，不处理消息业务逻辑。
- Broker 负责用户连接和服务转发。
- 业务服务通过 Broker 精准推送到用户所在的 WS 网关。

## 关键技术点

- SDK 与 Server 分离，使 Broker 只依赖调用契约，不依赖 Netty 运行实现。
- 具体连接只存于所属网关；集群层只同步用户与网关位置。
- 上行和下行都经过 Broker，统一在线路由与业务服务调用边界。
- Gateway 的逐连接写入结果表示本机是否接受写入，不等同于客户端已经处理通知。

## 验证命令

```bash
mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-server -am test
```
