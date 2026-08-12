# im-gateway

## 模块作用

`im-gateway` 是 IM 系统的网关聚合模块，负责承载外部访问入口相关代码。

当前网关按协议拆分为两类：

- `im-http-gateway`：HTTP API 统一入口，基于 Spring Cloud Gateway 转发到后端业务服务。
- `im-ws-gateway`：WebSocket 实时连接入口，负责浏览器 WS 长连接接入和内部推送入口。

## 模块结构

```text
im-gateway/
  im-http-gateway/    # HTTP 网关服务
  im-ws-gateway/      # WebSocket 网关聚合模块
  pom.xml             # 网关聚合 POM
```

## 边界说明

- HTTP 流量通过 `im-http-gateway` 进入后端服务。
- WebSocket 流量通过 `im-ws-gateway-server` 接入，再由 Broker 路由到业务服务。
- 网关层只做接入、安全、协议转换、路由和连接管理，不承载业务领域逻辑。

## 关键技术点

- HTTP 与 WebSocket 按短请求路由和长连接管理拆成独立进程，扩容策略互不影响。
- 两类网关都通过 Nacos 注册；HTTP 使用服务发现路由，WS 通过 Broker 维护在线位置。
- 外部协议在网关终止，内部调用使用 Facade/Bolt SDK，避免业务服务依赖接入协议。

## 验证命令

```bash
mvn -q -pl im-gateway/im-http-gateway,im-gateway/im-ws-gateway/im-ws-gateway-server -am test
```
