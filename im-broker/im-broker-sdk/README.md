# im-broker-sdk

## 模块作用

`im-broker-sdk` 定义 Broker 对其他内部模块暴露的调用契约和默认客户端。

它包含 Bolt RPC service/operation 标识、Params、DTO、共享模型和基于 Bolt 的 `BrokerClient`，不包含 Broker 运行服务端实现。

## 目录结构

```text
im-broker-sdk/
  src/main/java/com/co/kc/imchat/broker/sdk/
    BrokerRpcOperation.java              # Broker RPC operation 标识
    BrokerRpcService.java                # Broker RPC service 标识
    client/                              # Broker 默认客户端
    dto/                                 # RPC 返回对象
    model/                               # 共享连接位置模型
    params/                              # RPC 入参对象
```

## 边界说明

- WS 网关和消息服务可以依赖本模块发起 Broker 内部调用。
- 运行实现、缓存、Redis、Bolt Handler 都放在 `im-broker-server`。
- Params 和 DTO 保持内部通信语义，不承载业务领域模型。

## 关键技术点

- `BrokerClient` 集中封装 service/operation、JSON 和超时，调用方不接触 Bolt 细节。
- 地址选择支持 HASH、ROUND_ROBIN、RANDOM；轮询计数使用溢出安全的取模逻辑。
- Broker/Gateway/Connection/Frame 使用不同服务维度，避免单一 RPC service 无限扩张。
- SDK 模型是跨进程契约，Server 内部领域状态需通过 Transformer 转换。
