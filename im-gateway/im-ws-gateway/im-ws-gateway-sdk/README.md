# im-ws-gateway-sdk

## 模块作用

`im-ws-gateway-sdk` 是 WS 网关对内部服务暴露的调用 SDK。

当前主要给 Broker 使用，用于把实时帧写入指定 WS 网关中的用户连接，或关闭指定旧会话版本的连接。SDK 内包含调用参数、返回对象、Bolt RPC 标识，以及按网关地址发起调用的客户端。

## 目录结构

```text
im-ws-gateway-sdk/
  src/main/java/com/co/kc/imchat/gateway/ws/sdk/
    GatewayClient.java           # Broker 访问 WS 网关的 Bolt 客户端
    enums/                       # Bolt service / operation 与写入状态
    model/
      params/                    # RPC 入参
      dto/                       # 单次写入明细
      result/                    # RPC 结果
```

## 契约说明

- `GatewayFrameWriteParams`：包含目标用户和需要写入的实时帧；具体连接由目标网关本地查找。
- `GatewayFrameWriteDTO`：单个连接的接受或失败明细。
- `GatewayFrameWriteResult`：聚合当前网关的逐连接写入结果。
- `ConnectionCloseParams`：包含目标用户和需要关闭的旧 `sessionVersion`。
- `GatewayBoltService` / `GatewayBoltOperation`：Bolt 内部通信使用的服务名和操作名。
- `GatewayClient`：按明确的 WS 网关地址发起写帧或关闭连接调用；网关查找由 Broker 负责。

## 依赖边界

- SDK 模块只包含内部调用所需的轻量对象和客户端，不依赖 Netty、Spring Boot 运行服务。
- SDK 不维护连接索引，不做路由决策。
- 内部处理实现放在 `im-ws-gateway-server`，Broker 依赖 SDK 发起调用。

## 关键技术点

- RPC service/operation 使用稳定枚举，避免调用方散落字符串协议。
- 写帧结果逐连接返回状态，支持部分成功而不是整批二元成功/失败。
- DTO 保持纯传输语义，不引用 Netty Channel 或 Server 内部 Registry。

## 使用场景

```text
Broker
  -> GatewayClient
  -> GatewayBoltService.FRAME / CONNECTION
  -> GatewayBoltOperation.WRITE_FRAME / CLOSE_CONNECTIONS
  -> WS Gateway Server
  -> local ConnectionRegistry
  -> matching Netty Channel
  -> WebSocket client
```

写帧成功仅表示目标 Gateway 已接受对应连接的本次写入，不表示客户端已经处理通知。客户端业务 ACK 会沿上行链路返回 Message service，由 Message service 确认回执任务。

## 验证命令

```bash
mvn -q -pl im-gateway/im-ws-gateway/im-ws-gateway-sdk -am test
```
