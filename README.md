# ImChat

## 概述

`ImChat` 是一个基于 `Spring Boot` 的即时通讯服务，主要覆盖用户、好友、群组、会话、私聊消息与群聊消息等业务场景。项目按照 `DDD`（`Domain-Driven Design`，领域驱动设计）进行建模和模块拆分，将领域模型、用例编排、接口协议和基础设施实现分离，降低业务逻辑与技术细节之间的耦合。

当前项目采用 `Java 21`、`Spring Boot 3` 和 `Maven` 多模块组织，根项目命名为 `im-chat`。运行入口按职责拆分为 HTTP gateway、WebSocket gateway、broker 和业务 service。

## 设计

即时通讯系统的核心并不只是消息收发，还需要围绕用户关系、群组关系、会话状态和消息状态建立一致的业务规则。本项目主要围绕以下用例展开：

1. 用户注册、登录、登出和用户详情查询。
2. 好友添加、删除、拉黑、解除拉黑、备注修改和好友查询。
3. 群组创建、成员邀请、成员移除、群解散、群退出、群主转让、群昵称和群通知设置。
4. 私聊会话和群聊会话的打开、退出、隐藏和列表查询。
5. 私聊消息和群聊消息的发送、接收、已读、撤回、详情查询和历史查询。
6. WebSocket 连接认证、在线会话维护和消息推送。

项目在实现上将业务规则放在领域层，由应用层负责组织用例流程。接口层只负责协议适配，基础设施层只负责技术实现，例如 `MySQL` 持久化、`Redis` 会话缓存、分布式锁、事件发布、密码加密和 `JWT` 令牌。

## 建模

本项目基于 `DDD` 对即时通讯领域进行建模。`DDD` 强调围绕业务语言建立模型，并让代码结构与业务概念保持一致。项目中使用聚合根、实体、值对象、领域服务、领域事件和仓储接口表达核心业务。

### 划分子域

| 子域 | 子域类型 | 限界上下文 | 说明 |
|------|----------|------------|------|
| 消息子域 | 核心域 | 消息上下文 | 负责私聊消息、群聊消息、消息投递、消息状态和消息撤回 |
| 会话子域 | 核心域 | 会话上下文 | 负责私聊会话、群聊会话、用户会话列表和会话状态 |
| 群组子域 | 核心域 | 群组上下文 | 负责群组、群成员、群主、群昵称、群通知和成员关系 |
| 好友子域 | 支撑子域 | 好友上下文 | 负责好友关系、好友备注、拉黑关系和好友展示信息 |
| 用户子域 | 通用子域 | 用户上下文 | 负责用户身份、登录认证、密码和用户基础信息 |
| 在线会话子域 | 支撑子域 | 在线会话上下文 | 负责用户在线状态和连接会话缓存 |

### 统一语言

#### 用户上下文

| 业务术语 | `DDD` 建模对象类型 | 建模名称 |
|----------|--------------------|----------|
| 用户 | 聚合根 | `User` |
| 用户ID | 值对象 | `UserId` |
| 用户名称 | 值对象 | `UserName` |
| 用户邮箱 | 值对象 | `UserEmail` |
| 原始密码 | 值对象 | `UserRawPassword` |
| 加密密码 | 值对象 | `UserPassword` |
| 用户服务 | 领域服务 | `UserService` |
| 密码服务 | 领域服务接口 | `PasswordService` |

#### 好友上下文

| 业务术语 | `DDD` 建模对象类型 | 建模名称 |
|----------|--------------------|----------|
| 好友关系 | 聚合根 | `Friend` |
| 好友关系ID | 值对象 | `FriendId` |
| 好友边 | 值对象 | `FriendEdge` |
| 好友备注 | 值对象 | `FriendAlias` |
| 好友展示名 | 值对象 | `FriendDisplayName` |
| 好友状态 | 值对象 | `FriendStatus` |
| 好友服务 | 领域服务 | `FriendService` |

#### 群组上下文

| 业务术语 | `DDD` 建模对象类型 | 建模名称 |
|----------|--------------------|----------|
| 群组 | 聚合根 | `Group` |
| 群ID | 值对象 | `GroupId` |
| 群名称 | 值对象 | `GroupName` |
| 群状态 | 值对象 | `GroupStatus` |
| 群成员 | 实体 | `GroupMember` |
| 成员ID | 值对象 | `MemberId` |
| 成员数量 | 值对象 | `MemberCount` |
| 群内别名 | 值对象 | `GroupUserAlias` |
| 群通知设置 | 值对象 | `GroupNotification` |
| 群组服务 | 领域服务 | `GroupService` |

#### 会话上下文

| 业务术语 | `DDD` 建模对象类型 | 建模名称 |
|----------|--------------------|----------|
| 聊天会话 | 聚合根 | `ImChat` |
| 私聊会话 | 聚合根 | `ImPrivateChat` |
| 群聊会话 | 聚合根 | `ImGroupChat` |
| 会话ID | 值对象 | `ImChatId` |
| 会话名称 | 值对象 | `ImChatName` |
| 会话类型 | 值对象 | `ImChatType` |
| 会话状态 | 值对象 | `ImChatStatus` |
| 在线会话 | 聚合根 | `Session` |
| 在线状态 | 值对象 | `SessionStatus` |
| 会话服务 | 领域服务 | `ImChatService` |

#### 消息上下文

| 业务术语 | `DDD` 建模对象类型 | 建模名称 |
|----------|--------------------|----------|
| 消息 | 聚合根 | `ImMessage` |
| 消息ID | 值对象 | `ImMessageId` |
| 消息令牌 | 值对象 | `ImMessageToken` |
| 消息内容 | 值对象 | `ImMessageContent` |
| 消息类型 | 值对象 | `ImMessageType` |
| 消息发送者 | 值对象 | `ImMessageSender` |
| 消息接收者 | 值对象 | `ImMessageRecipient` |
| 私聊收件箱消息 | 聚合根 | `ImPrivateInboxMessage` |
| 群聊收件箱消息 | 聚合根 | `ImGroupInboxMessage` |
| 私聊消息状态 | 值对象 | `ImPrivateMessageStatus` |
| 群聊消息状态 | 值对象 | `ImGroupMessageStatus` |
| 出站消息 | 值对象 | `ImOutboundMessage` |
| 消息服务 | 领域服务 | `ImMessageService` |

### 领域事件

领域事件用于表达领域内已经发生的事实，并由应用层或接口层完成后续通知与推送。

| 事件 | 所属上下文 | 说明 |
|------|------------|------|
| `FriendAddedEvent` | 好友上下文 | 好友关系已建立 |
| `FriendRemovedEvent` | 好友上下文 | 好友关系已移除 |
| `GroupCreatedEvent` | 群组上下文 | 群组已创建 |
| `GroupDismissedEvent` | 群组上下文 | 群组已解散 |
| `GroupMemberJoinedEvent` | 群组上下文 | 群成员已加入 |
| `GroupMemberRemovedEvent` | 群组上下文 | 群成员已移除 |
| `ImPrivateMessageSentEvent` | 消息上下文 | 私聊消息已发送 |
| `ImPrivateMessageRevokedEvent` | 消息上下文 | 私聊消息已撤回 |
| `ImGroupMessageSentEvent` | 消息上下文 | 群聊消息已发送 |
| `ImGroupMessageRevokedEvent` | 消息上下文 | 群聊消息已撤回 |

## 架构

项目采用基于依赖倒置的服务化架构。HTTP 和 WebSocket 入口由 gateway 承载，IM 实时连接索引与在线投递由 broker 承载，业务规则、用例编排和技术实现先集中在业务服务内部，服务之间通过 SDK、facade 或 Dubbo 契约解耦。

```text
        ┌────────────────┐        ┌────────────────┐
        │ im-http-gateway│        │ im-ws-gateway  │
        │ HTTP ingress   │        │ WS ingress     │
        └───────┬────────┘        └───────┬────────┘
                │                         │
                │                 ┌───────▼────────┐
                │                 │   im-broker    │
                │                 │ connection idx │
                │                 │ gossip sync    │
                │                 └───────┬────────┘
                │                         │
        ┌───────▼─────────────────────────▼────────┐
        │               im-service                 │
        │ message/account/social business modules  │
        └───────┬─────────────────────────┬────────┘
                │                         │
        ┌───────▼────────┐        ┌───────▼────────┐
        │   *-facade     │        │   im-common    │
        │ service api    │        │ common utility │
        │                │        │ realtime frame │
        └────────────────┘        └────────────────┘
```

### 依赖方向

项目按“网关、Broker、业务服务、契约”拆分模块：

```text
im-gateway/*-server      -> gateway sdk, broker sdk, service facade
im-broker/*-server       -> broker sdk, gateway sdk, message facade, im-gossip
im-service/*-server      -> own facade, dependent service facade, broker sdk
im-service/*-facade      -> im-common
im-common                -> no business module dependency
```

约束原则：

1. `*-facade` 只保存服务间契约、内部 DTO 和错误语义，不依赖 server。
2. server 可以实现自己的 facade，也可以通过 Dubbo、Bolt adapter 调用其他服务；后续替换协议时只替换 adapter。
3. WebSocket 连接、连接索引、业务处理分别放到 gateway、broker、service。
4. 当前 `im-message-server` 承载原 DDD 应用层、领域层、基础设施和 HTTP 接口；后续迁移 account/social 时，先迁移 provider 实现，再迁移领域代码。
5. 消息主链路不直接依赖 account/social 内部领域服务，而是通过 `AccountFacade`、`SocialFacade` 边界访问。

## 模块

| 模块 | 职责 | 主要内容 |
|------|------|----------|
| `im-common` | 公共模块 | 通用常量、异常、基础枚举、响应模型、工具类、跨服务实时帧基础类型 |
| `im-plugin` | 技术插件 | Bolt RPC、Dubbo、Gossip 同步、Web、缓存、分布式锁、MQ、数据源、Session 等通用基础能力 |
| `im-gateway/im-ws-gateway-*` | WebSocket 网关 | Netty WebSocket 连接、协议 adapter、gateway facade |
| `im-broker/im-broker-*` | IM Broker | Broker/Gateway/Connection/Frame RPC 入口、用户到 gateway 的连接索引、Broker 间 Gossip 同步、精确投递 |
| `im-service/im-account-*` | 账号服务边界 | 账号 facade，登录态、用户资料、在线状态查询契约 |
| `im-service/im-social-*` | 社交服务边界 | 好友关系、群成员、群消息收件人查询契约 |
| `im-service/im-message-*` | 消息服务 | 消息应用层、领域层、基础设施、HTTP 接口、消息 facade |

### 消息投递架构

项目支持多应用实例部署。客户端 WebSocket 连接只会落到某一个 gateway，但消息发送方、接收方和群成员可能连接在不同 gateway 上。为了解耦“消息写入”和“在线投递”，项目使用 MySQL 保存消息事实，通过 broker 维护用户与 gateway 的连接索引，再精确投递到目标 gateway。

```text
       sender client
            │
            │ websocket command
            ▼
┌──────────────────────┐
│   im-ws-gateway      │
│ Netty WebSocket      │
│ Protocol Adapter     │
└──────────┬───────────┘
           │ broker frame
           ▼
┌──────────────────────┐
│      im-broker       │
│ Connection Index     │
│ Gossip Sync          │
└──────────┬───────────┘
           │ call message service
           ▼
┌──────────────────────┐
│  im-message-server   │
│ Application Service  │
└──────────┬───────────┘
           │
           │ 1. validate chat/member/friend/token
           │ 2. save inbox copies and chat state
           ▼
      ┌─────────┐
      │  MySQL  │
      └─────────┘
           │
           │ 3. publish domain event
           ▼
┌──────────────────────┐
│ Message Listener     │
│ ImMessageNotifier    │
└──────────┬───────────┘
           │
           │ 4. BrokerClient.writeFrame(receiverId, payload)
           ▼
      ┌─────────┐
      │ Broker  │
      │ Index   │
      └────┬────┘
           │ 5. locate user -> gateway mapping
           ▼
    ┌───────────────┐
    │ im-ws-gateway │
    │ Netty WS      │
    └───────┬───────┘
            │ 6. push to local websocket session
            ▼
      receiver clients
```

#### 投递链路

1. 客户端 WebSocket 连接进入 `im-ws-gateway`，网关负责连接认证、会话保持和协议解析。
2. WebSocket 命令经 broker 写入消息业务服务，最终调用 `PrivateMessageAppService` 或 `GroupMessageAppService`。
3. 应用服务在事务内完成权限校验、幂等校验、消息副本落库、会话状态更新，并发布领域事件。
4. `ImMessageNotifierInvoker` 根据通知命令类型选择具体 notifier，例如 `PrivateSentNotifier`、`PrivateRevokedNotifier`、`GroupSentNotifier`、`GroupRevokedNotifier`。
5. notifier 通过 `BrokerClient` 精确投递到目标用户所在的 gateway，不再使用 Redis Pub/Sub 做全实例广播。
6. broker 维护用户与 gateway 的连接索引，gateway 只向本机持有的 WebSocket session 推送。broker 不保存具体 `connectionId` 列表，具体连接由 gateway 本机管理。
7. 需要确认的通知会在 Redis 写入 `PENDING` 状态，并注册延迟重试任务；客户端 ACK 后写为 `CONFIRMED`，重试任务触发时会跳过已确认通知。

#### 回执重投队列

需要客户端回执的通知会同时写入确认状态和延迟重投任务。重投任务内容按 `receiptId` 保存，延迟队列里只保存 `receiptId`，避免队列元素携带过大的通知内容。

延迟重投队列按 `ReceiptType` 拆分业务类型，再按 `receiptId` 拆分分片。每个分片有独立消费任务，只拉取自己绑定的队列，避免某一类回执或某一个热点队列长期占用整体消费入口。客户端 ACK 时，服务端通过 `receiptId` 找到任务内容，再定位对应分片并清理队列中的残留任务；如果队列里后续仍弹出已确认任务，只要任务内容不存在就会直接跳过。

这种架构的核心是：消息事实以数据库为准，broker 只负责在线连接索引和精确推送。即使接收方当前不在线，消息副本仍已保存；接收方重新打开会话或查询历史时，从数据库读取消息。

#### 通知通道

| 通知场景 | 内部通知类型 | broker 投递目标 |
|----------|--------------|------------------|
| 私聊发送 | `ImPrivateSentNotification` | 接收方用户 |
| 私聊撤回 | `ImPrivateRevokedNotification` | 接收方用户 |
| 群聊发送 | `ImGroupSentNotification` | 在线群成员，排除发送者 |
| 群聊撤回 | `ImGroupRevokedNotification` | 群成员 |

客户端连接 `im-ws-gateway` 暴露的 WebSocket 入口，消息协议由 gateway 层解析。跨服务传递的实时帧基础类型放在 `im-common`，避免 broker/message 反向依赖 gateway server。当前实现不保留 STOMP 兼容层，后续如果需要其他协议，可以在 gateway 协议 adapter 中扩展。

服务间实时链路通过 Bolt 和 Dubbo 连接：

```text
im-ws-gateway -> im-broker      Bolt RPC
im-broker     -> im-message     Dubbo
im-message    -> im-broker      Bolt RPC
im-broker     -> im-ws-gateway  Bolt RPC
im-broker     -> im-broker      Gossip digest/delta over Bolt
```

这些内部调用只服务于服务间链路，不作为外部 API 暴露。外部 HTTP 请求统一进入 `im-http-gateway`，当前技术升级阶段仍路由到承载原 HTTP controller 的 `im-message`，路径为 `/user/**`、`/friend/**`、`/im/**`。

客户端收到需要回执的通知后，统一发送 ACK 命令。`receiptType` 为发送通知时，服务端会先把消息副本置为 `RECEIVED`，再确认对应发送通知任务；`receiptType` 为撤回通知时，服务端只确认对应撤回通知任务。

#### 投递语义

1. MySQL 中的收件箱消息副本是消息事实来源；broker 只承载在线连接索引和推送通知。
2. broker 不保存历史通知。实例重启、网络抖动或客户端离线时，客户端应通过历史查询和消息详情接口补齐数据。
3. 发送接口使用 `messageToken` 做幂等校验，同一会话内重复提交同一 token 会被拒绝，避免客户端重试造成重复消息。
4. broker 按用户定位目标 gateway。是否真正推送到客户端，取决于该 gateway 本地是否持有目标用户的 WebSocket 会话。
5. `ImMessageConfirmable` 通知使用 Redis 保存确认状态，回执标识由 `ReceiptType + receiverId + chatId + messageId` 组成。重试任务只负责重新投递未确认通知，不改变消息已落库这一事实。
6. 客户端仍应按 `messageId` 或 `messageToken` 做幂等处理，因为网络超时、客户端 ACK 丢失或重试任务先于 ACK 到达时，仍可能看到重复通知。

### 模块协作

运行时入口按职责拆分到 `im-gateway`、`im-broker` 和 `im-service`。`im-ws-gateway` 负责 WebSocket 连接，`im-broker` 负责连接索引、Broker 间同步和用户到 gateway 的映射，`im-service` 负责业务处理。HTTP 入口保留在 service server 内，后续可通过独立 HTTP gateway 做统一流量入口。

`im-message-server` 当前承载消息业务的应用层、领域层、基础设施和 HTTP 接口。消息主链路访问账号/社交能力时只依赖 `AccountFacade`、`SocialFacade`，当前 provider 是本地实现，后续迁移 account/social 独立服务时可替换为 RPC provider。

`im-account-facade`、`im-social-facade`、`im-message-facade` 是服务间契约模块，保存 facade 接口、内部 DTO 和错误语义。server 模块只实现契约，不反向依赖调用方。

`im-message-server` 内的领域层保存核心业务规则。消息发送、消息状态流转、群成员校验、好友关系校验、会话归属校验等规则仍在领域模型或领域服务中完成。基础设施提供 MySQL 仓储、MyBatis Mapper、Redis 会话/确认状态、分布式锁、JWT、BCrypt 和 Spring 事件发布。

## 接口

### HTTP

| 资源 | 路径前缀 | 说明 |
|------|----------|------|
| 用户 | `/user` | 注册、登录、登出、用户详情 |
| 好友 | `/friend` | 好友列表、详情、搜索、添加、删除、拉黑、备注 |
| 聊天会话 | `/im/chat` | 会话列表、打开私聊、打开群聊、退出和隐藏会话 |
| 群组 | `/im/group` | 群组创建、成员管理、群设置、群详情、群消息查询 |
| 私聊消息 | `/im/private` | 私聊消息详情和历史查询 |

接口文档使用 `springdoc-openapi` 暴露：

| 地址 | 说明 |
|------|------|
| `/swagger-ui.html` | Swagger UI |
| `/v3/api-docs` | OpenAPI 文档 |

### WebSocket

WebSocket 连接入口为 `/ws`。当前版本不再使用 STOMP destination，而是使用统一 JSON 帧，gateway 解析后通过 broker 转发到 message 服务。

请求帧基础结构：

```json
{
  "version": "1",
  "type": "request",
  "cmd": "message.private.send",
  "seq": "10001",
  "traceId": "trace-1",
  "body": {}
}
```

| 命令 | 说明 |
|------|------|
| `message.private.send` | 发送私聊消息 |
| `message.private.read` | 已读私聊消息 |
| `message.private.revoke` | 撤回私聊消息 |
| `message.group.send` | 发送群聊消息 |
| `message.group.read` | 已读群聊消息 |
| `message.group.revoke` | 撤回群聊消息 |
| `notification.ack` | 确认通知已收到，发送通知回执也会触发消息接收 |

## 消息收发核心流程

### 私聊消息发送

1. 客户端通过 WebSocket 发送 `cmd=message.private.send` 的 JSON 帧，`body` 为 `ImPrivateMessageSendRequest`。
2. `im-ws-gateway` 解析帧后通过 `im-broker` 转发到 `im-message`，并转换为 `ImPrivateMessageSendCmd` 调用 `PrivateMessageAppService.sendMessage`。
3. 应用层按 `chatId + messageToken` 加分布式锁，避免同一会话内重复提交同一客户端消息。
4. 应用层加载发送方私聊会话，校验当前用户拥有该会话，再加载对端会话。
5. `FriendService` 校验双方好友关系正常，`ImMessageService.ensurePrivateMessageUnique` 校验消息 token 未写入。
6. 应用层生成同一个 `messageId` 的两份收件箱消息：发送方副本和接收方副本。
7. 发送方副本立即带有发送、接收、已读时间；接收方副本初始为 `SENT`。
8. 两份消息写入 `ImPrivateInboxMessageRepository`，双方会话通过 `receiveLatestMessage` 更新最近消息、未读数和活跃时间。
9. 应用层发布 `ImPrivateMessageSentEvent`。
10. `PrivateMessageListener` 监听事件后调用 `onMessageSent`；如果接收方在线，则通过 `ImMessageNotifierInvoker` 创建确认状态，并经 broker 精确推送到接收方所在的 `im-ws-gateway`。

### 私聊消息接收、已读与撤回

客户端收到私聊发送通知后，通过 `cmd=notification.ack` 上报 `PRIVATE_MESSAGE_SEND` 回执。`NotificationAckAppService` 会通过回执策略加载当前用户消息副本并调用 `ImPrivateInboxMessage.receive`，只更新本地副本的接收状态，然后统一确认对应发送通知任务。私聊已读命令 `message.private.read` 会调用消息副本的 `read`，再更新当前用户私聊会话的 `readMessageId` 和未读数。

私聊撤回命令 `message.private.revoke` 会加载发送方和接收方两份副本。`ImMessageService.revokePrivateMessage` 先确认两份副本的原始发送人都是当前用户，再同时撤回两份副本。持久化后发布 `ImPrivateMessageRevokedEvent`，事件监听器负责推送撤回通知。客户端收到撤回通知后通过 `notification.ack` 确认。

### 群聊消息发送

1. 客户端通过 WebSocket 发送 `cmd=message.group.send` 的 JSON 帧，`body` 为 `GroupMessageSendRequest`。
2. `im-ws-gateway` 解析帧后通过 `im-broker` 转发到 `im-message`，并转换为 `GroupMessageSendCmd` 调用 `GroupMessageAppService.sendMessage`。
3. 应用层按 `chatId + messageToken` 加分布式锁，加载发送方群聊会话并校验会话归属。
4. 应用层加载群组，`GroupService.ensureGroupMember` 校验发送方仍是有效群成员。
5. `ImMessageService.ensureGroupMessageUnique` 按发送方会话、用户和 token 做幂等校验。
6. `GroupService.findMessageRecipients` 找到群内所有接收者会话，并结合 `UserService.isChatting` 标记在线会话状态。
7. `ImMessageService.transmitGroupMessage` 为每个群成员生成一份 `ImGroupInboxMessage`。发送方副本直接为 `READ`，其他成员副本为 `SENT`。
8. 每个成员的群聊会话通过 `receiveLatestMessage` 更新最近消息、未读数、已读位点和活跃时间。
9. 应用层批量保存群消息副本和群聊会话，并发布 `ImGroupMessageSentEvent`。
10. `GroupMessageListener` 监听发送事件后加载群成员会话，跳过发送者和离线用户，为每个在线成员创建确认状态，并经 broker 精确推送到成员所在的 `im-ws-gateway`。

### 群聊消息接收、已读与撤回

客户端收到群聊发送通知后，通过 `cmd=notification.ack` 上报 `GROUP_MESSAGE_SEND` 回执。`NotificationAckAppService` 会通过回执策略校验会话归属和群成员身份，只更新当前成员消息副本的接收状态，然后统一确认对应发送通知任务。群聊已读命令 `message.group.read` 会更新当前成员的消息副本和群聊会话读位点，不影响其他成员副本。

群聊撤回命令 `message.group.revoke` 会先校验当前用户拥有发送方会话且仍是群成员，再加载同一 `groupId + messageId` 下的全部消息副本。`ImMessageService.revokeGroupMessage` 要求待撤回集合中包含当前用户的发送方副本，然后撤回全部副本。持久化后发布 `ImGroupMessageRevokedEvent`，监听器为群内所有成员会话通过 broker 发布撤回通知。客户端收到撤回通知后通过 ACK 命令确认。

### 状态与投递边界

消息状态保存在每个用户自己的收件箱副本中。私聊有两份副本，群聊按群成员数量生成多份副本，因此接收、已读和撤回都可以在用户维度表达。会话上的 `lastMessageId`、`readMessageId`、`unreadMessageCount` 和 `activeTime` 用于会话列表展示，不作为消息明细的唯一事实来源。

消息写入与事件发布由应用服务在事务内编排，实时推送通过领域事件之后的监听流程完成。这样可以把核心写模型与在线通知解耦：用户离线时消息仍然落库，用户在线时再通过 broker 定位到所在 gateway 推送通知。

## 技术栈

| 分类 | 技术 |
|------|------|
| 基础框架 | `Spring Boot 3.5.14` |
| 构建工具 | `Maven` |
| 语言版本 | `Java 21` |
| 持久化 | `MyBatis`、`MyBatis-Plus`、`MySQL`、`H2` |
| 数据源 | `Druid` |
| 缓存 | `Redis`、`JetCache`、`Caffeine` |
| Redis 客户端 | `Redisson`、`Spring Data Redis` |
| 接口协议 | `Spring MVC`、`Dubbo`、`SOFA Bolt`、`Netty WebSocket`、`Springdoc OpenAPI` |
| 注册与配置 | `Nacos Discovery`、`Nacos Config` |
| 对象转换 | `MapStruct` |
| 认证与安全 | `JWT`、`BCrypt` |
| 测试 | `JUnit 5`、`Mockito`、`H2` |

## 运行

### 环境准备

1. 安装 `JDK 21`。
2. 安装 `Maven`。
3. 准备 `MySQL`，默认数据库为 `im_chat`。
4. 准备 `Redis`，默认地址为 `127.0.0.1:6379`。
5. 准备 `Nacos`，默认地址为 `127.0.0.1:8848`，用于服务注册、发现和配置覆盖。
6. 执行 `sql/ddl.sql` 初始化数据库表结构。

默认运行配置位于：

```text
im-gateway/im-http-gateway/src/main/resources/application.yml
im-gateway/im-ws-gateway/im-ws-gateway-server/src/main/resources/application.yml
im-broker/im-broker-server/src/main/resources/application.yml
im-service/im-message/im-message-server/src/main/resources/application.yml
im-service/im-account/im-account-server/src/main/resources/application.yml
im-service/im-social/im-social-server/src/main/resources/application.yml
```

### Nacos 配置规划

项目保留各服务本地 `application.yml` 作为完整默认配置，Nacos Config 只提供运行期的动态覆盖层。所有 Nacos 配置均通过 `optional:nacos:` 导入；对应 Data ID 尚未创建时，服务仍使用本地配置启动。

本地开发默认使用 namespace `im-chat-box`，namespace ID 为 `b0b51fe2-fd46-463e-96df-dfba4a3b41a1`。其他环境可以通过更高优先级的 `im.nacos.namespace` 配置覆盖该值。

人工维护的配置和注册分组如下：

| Group | 用途 | Data ID 示例 |
|-------|------|--------------|
| `COMMON_GROUP` | 所有服务共享的动态配置 | `common.yml` |
| `SERVICE_GROUP` | 业务服务动态配置 | `im-account.yml`、`im-social.yml`、`im-message.yml` |
| `INFRA_GROUP` | 基础设施服务动态配置 | `im-broker.yml`、`im-http-gateway.yml`、`im-ws-gateway.yml` |
| `IM_CHAT_GROUP` | Spring Cloud 服务实例注册 | 由 Nacos Discovery 自动维护 |
| `DUBBO_GROUP` | Dubbo 服务注册 | 由 Dubbo 自动维护 |

Dubbo 还会创建 `mapping`、`dubbo`、`DUBBO_SERVICEDISCOVERY_MIGRATION` 等框架分组，用于接口与应用映射、动态治理和迁移规则，不作为项目配置 Data ID 使用。

插件内置默认配置位于各插件的 `META-INF/config` 目录：

```text
im-nacos: META-INF/config/im-nacos.yml
im-dubbo: META-INF/config/im-dubbo.yml
im-cache: META-INF/config/im-cache.yml
```

配置优先级按“插件默认值、本地 `application.yml`、Nacos 动态配置、启动参数”逐层覆盖。本地配置负责保证服务可独立启动，数据库地址、Redis 地址、超时、日志级别和业务开关等内容可按需在 Nacos 中覆盖。

默认服务名和端口：

| 服务 | application name | 默认端口 |
|------|------------------|----------|
| HTTP 网关 | `im-http-gateway` | `18080` |
| WebSocket 网关 Bolt 端口 | `im-ws-gateway` | `12201` |
| WebSocket 网关 WS 端口 | `im-ws-gateway` | `19090` |
| Broker Bolt 端口 | `im-broker` | `12200` |
| 消息服务 | `im-message` | `8888` |
| 账号服务壳 | `im-account` | `8886` |
| 社交服务壳 | `im-social` | `8887` |

`im-account` 和 `im-social` 当前是可注册、可健康检查的轻量服务壳，先保留业务拆分边界；账号和社交领域实现仍由 `im-message` 内部本地 provider 承载。`im-message` 当前既提供业务处理，也承担消息主链路的内部 facade；后续如果把 account/social 迁为独立服务，只需要把本地 provider 换成对应 RPC adapter。

### 构建

```bash
mvn clean package
```

### 测试

```bash
mvn test
```

### 启动

推荐启动顺序：

1. 启动基础设施：`MySQL`、`Redis`、`Nacos`。
2. 启动 `im-message`，让消息业务和内部 facade 先完成注册。
3. 启动 `im-broker`，准备连接索引并接收 message 的实时投递请求。
4. 启动 `im-ws-gateway`，承接 WebSocket 连接并把上行帧转给 broker。
5. 启动 `im-http-gateway`，提供统一 HTTP 对外入口。
6. 启动 `im-account`、`im-social` 壳服务，补齐服务发现和后续拆分边界。

```bash
mvn -pl im-service/im-message/im-message-server -am spring-boot:run
mvn -pl im-broker/im-broker-server -am spring-boot:run
mvn -pl im-gateway/im-ws-gateway/im-ws-gateway-server -am spring-boot:run
mvn -pl im-gateway/im-http-gateway -am spring-boot:run
mvn -pl im-service/im-account/im-account-server -am spring-boot:run
mvn -pl im-service/im-social/im-social-server -am spring-boot:run
```

### 目标架构链路

默认 `application.yml` 直接启用目标架构链路，不再需要额外 profile：

| 链路 | 默认配置 |
|------|----------|
| `im-ws-gateway -> im-broker` | Bolt RPC |
| `im-broker -> im-ws-gateway` | Bolt RPC |
| `im-broker -> im-message` | Dubbo |
| Dubbo 注册中心 | Nacos，默认 `nacos://127.0.0.1:8848` |
| Broker 集群同步 | `im-gossip`，Broker 之间通过 Bolt 交换 digest/delta |
| Broker/Gateway ID | 根据 host 和 port 自动生成 |
| gateway 注册、心跳与连接同步 | 注册、心跳、连接快照同步分离 |
| broker registry | 内存 registry，Broker 间通过 Gossip 做最终一致同步 |

本地只想单进程调试时，可以关闭 `im.bolt.client.enabled`、`im.bolt.server.enabled`，或只启动单个 broker 和单个 gateway。

`im-ws-gateway` 启动后会先向 broker 注册自身 Bolt 地址，然后通过独立心跳刷新网关活跃时间，并定时同步本机当前活跃用户列表；broker 根据该快照清理 stale connection，避免 gateway 异常断开后连接索引长期残留。

多机部署时需要显式配置 `IM_GATEWAY_HOST`，该地址会写入 `im.gateway.ws.host` 并作为 gateway 注册给 broker 的 Bolt 可访问地址；本地默认值为 `127.0.0.1`。

`im-ws-gateway` 还提供基础长连接治理：Netty pipeline 会处理 WebSocket Ping/Pong，超过 `im.gateway.ws.idle.reader-idle-seconds` 未收到客户端帧时主动关闭连接，连接关闭后触发 broker 注销用户到当前 gateway 的连接索引。WebSocket 单帧大小由 `im.gateway.ws.max-frame-payload-length` 控制，服务停止时会主动通知 broker 注销当前 gateway，并关闭本机活跃连接，避免下线过程遗留本地连接。

`im-http-gateway` 会拒绝 `/internal/**` 外部访问，并为缺少 `X-Trace-Id` 的请求自动生成 traceId 后透传到下游服务，同时把最终 traceId 写回响应头，方便前端与服务端日志对齐。当前 HTTP 对外路由仍指向已有 message service 的 `/user/**`、`/friend/**`、`/im/**` 接口；account/social 服务当前还没有独立 HTTP Controller，后续拆分出 HTTP API 后再增加对应路由。

Broker 内部按五类 Bolt service 拆分调用边界：

| Service | 职责 |
|---------|------|
| `broker.broker` | Broker 实例注册、注销、心跳、列表查询 |
| `broker.gossip` | Broker 间 digest/delta 同步 |
| `broker.gateway` | WS gateway 注册、注销、心跳 |
| `broker.connection` | 用户到 gateway 的连接索引注册、注销、同步、迁移 |
| `broker.frame` | 上行帧转交 message 服务，下行帧写入目标 gateway |

连接索引当前使用内存 registry。每个用户连接按照 `userId` 在当前 Broker 列表中选择归属 broker；gateway 或 message 请求可以落到任意 broker，非归属 broker 会转发到归属 broker 处理。Broker 列表变化时，当前实例会定期检查本地连接索引，把不再归属自己的用户映射迁移到新的归属 broker。Broker/Gateway/Connection 三类 registry 变化会写入 `BrokerStateStore`，再通过 `im-gossip` 在 Broker 集群内最终一致同步。

主要启动类：

```text
com.co.kc.imchat.gateway.http.ImHttpGatewayApplication
com.co.kc.imchat.gateway.ws.ImWsGatewayApplication
com.co.kc.imchat.broker.ImBrokerApplication
com.co.kc.imchat.service.message.ImChatApplication
com.co.kc.imchat.service.account.ImAccountApplication
com.co.kc.imchat.service.social.ImSocialApplication
```

## 目录

```text
.
├── im-common
├── im-gateway
│   ├── im-http-gateway
│   └── im-ws-gateway
├── im-broker
├── im-service
│   ├── im-account
│   ├── im-message
│   └── im-social
├── im-plugin
├── sql
│   └── ddl.sql
└── pom.xml
```

## 开发约定

1. 新增服务间调用能力时先定义 `*-facade`，server 只依赖 facade 契约。
2. 新增消息业务规则时优先放在 `im-service/im-message/im-message-server` 内部领域包。
3. 新增 HTTP 入口时优先放在对应业务服务，统一由 `im-http-gateway` 做外部路由。
4. 新增 WebSocket 协议入口时放在 `im-ws-gateway-server`，业务处理通过 broker 转发到服务 facade。
5. 新增通用工具、通用异常、跨模块基础类型或跨服务实时帧基础类型时放在 `im-common`。
6. server 内部仍保持 DDD 边界，领域层不反向依赖应用层、接口层或基础设施层。
7. 应用层命令、查询和通知模型优先使用 `record` 表达不可变输入输出模型。

### 提交信息约定

提交信息沿用项目现有的中文标题和编号正文格式：

```text
<中文动词开头的简洁标题>

1. <第一组完整改动，说明新增或调整的核心能力>

2. <第二组完整改动，说明模块结构、调用边界或配置变化>

3. <第三组完整改动，说明兼容处理、测试和文档更新>
```

具体要求：

1. 标题使用“新增、优化、调整、修复、拆分、清理”等动词开头，概括提交目的，不罗列文件名，结尾不加标点。
2. 标题与正文之间保留一个空行；正文使用连续数字编号，不使用无序列表。
3. 每条正文描述一个完整改动维度，优先按照“核心能力、架构与配置、验证与清理”的顺序组织。
4. 涉及跨模块重构时应说明新的职责边界、调用链路和数据所有权，不能只写“调整代码结构”。
5. 涉及运行行为时应说明默认配置、兼容策略和失败处理；涉及测试时应说明覆盖的关键场景。
6. 单次提交应保持主题一致。仅格式化、重命名或机械生成的内容应与对应功能改动一起说明，避免正文失去审查价值。

示例：

```text
优化仓储缓存与配置管理

1. 新增仓储缓存代理与 JetCache 远程缓存配置，补充缓存名称常量和领域缓存值序列化支持

2. 拆分基础设施 Bean 配置，整理启动与仓储缓存集成测试包结构，抽取 SpringBootTest 测试注解

3. 清理未使用的 CQRS 模型和仓储依赖，补充缓存代理单元测试与启动集成测试
```

## 开源协议

本项目基于 `GNU General Public License v3.0 only` 开源，详情见 [LICENSE](LICENSE)。
