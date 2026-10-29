# Gateway 目标职责内聚设计

> 状态：用户已批准方案，独立设计 Reviewer `aa00ec4801ac133f0` Approved、必须修改项 none；不代表实现验收。

## 目标与范围

删除 `GatewayIdentityConfiguration`，将仅供 GatewayClient 使用的模式、开发目标不可变快照、目标选择和校验收回 GatewayClient。构造器改为 `(BoltInvoker, int timeoutMillis, BoltTransportMode, List<BoltTarget> developmentTargets)`，不保留兼容包装或构造器。同步 Broker ClientConfiguration、GatewayClientTest、FrameProcessHandlerTest、FrameDispatchAppServiceTest、GatewayDevelopmentTargetConfigurationTest 和 SDK README。

## 不变量

原构造校验、List.copyOf/Map.copyOf、重复地址拒绝及校验顺序保留。MTLS 使用注册的完整 Gateway URI，不能用开发配对覆盖；开发明文模式必须验证合法端点、精确 trusted-network host 标记及显式部署配对。IPv6 地址格式、固定 Gateway 命名空间、非空后缀及 query/fragment 拒绝不变。Broker 仍从实际 running controlled client 取得模式并校验 Invoker 同源。调用超时、协议、认证、注册和租约不改。

## 实施与验证

在现有 T3 追加精确范围，不新建 active 任务。先保存 before 并执行 GatewayClientTest 基线，再等价搬迁方法和构造逻辑；测试通过真实 writeFrame/closeConnections 入口验证，不暴露 private 目标方法。执行同一 SDK 聚焦测试并比较原体；Broker 测试与跨模块编译保留既有私有 JRaft 缺失阻塞事实，不用 SDK 通过替代。完整验收、architecture/full/clean 等既有暂停保持；独立实现 Review 必须保留必要验证缺口。外部 Java 调用方须同步参数并重编译。历史设计不重写，仅追加取代链接。
