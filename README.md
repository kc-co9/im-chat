# ImChat

## 概述

`ImChat` 是一个基于 `Spring Boot` 的即时通讯服务，主要覆盖用户、好友、群组、会话、私聊消息与群聊消息等业务场景。项目按照 `DDD`（`Domain-Driven Design`，领域驱动设计）进行建模和模块拆分，将领域模型、用例编排、接口协议和基础设施实现分离，降低业务逻辑与技术细节之间的耦合。

当前项目采用 `Java 21`、`Spring Boot 3` 和 `Maven` 多模块组织，根项目仍命名为 `im-chat`，业务启动模块为 `im-bootstrap`。

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

项目采用基于依赖倒置的分层架构。领域层位于核心位置，只表达业务模型和业务规则；应用层依赖领域层并编排用例；接口层和基础设施层位于外侧，分别负责协议适配和技术实现。

```text
                         ┌────────────────────┐
                         │    im-bootstrap    │
                         │  application entry │
                         └─────────┬──────────┘
                 ┌─────────────────┴─────────────────┐
                 │                                   │
        ┌────────▼────────┐                 ┌────────▼──────────┐
        │  im-interfaces  │                 │ im-infrastructure │
        │ interface layer │                 │ infrastructure    │
        └────────┬────────┘                 └────────┬──────────┘
                 └─────────────────┬─────────────────┘
                                   │
                         ┌─────────▼──────────┐
                         │   im-application   │
                         │ application layer  │
                         └─────────┬──────────┘
                                   │
                         ┌─────────▼──────────┐
                         │     im-domain      │
                         │    domain layer    │
                         └─────────┬──────────┘
                                   │
                         ┌─────────▼──────────┐
                         │     im-common      │
                         │ common utilities   │
                         └────────────────────┘
```

### 依赖方向

项目遵循严格依赖方向：

```text
im-bootstrap       -> im-interfaces, im-infrastructure
im-interfaces      -> im-application, im-domain, im-common
im-infrastructure  -> im-application, im-domain, im-common
im-application     -> im-domain, im-common
im-domain          -> im-common
im-common          -> no business module dependency
```

约束原则：

1. `im-domain` 不依赖 `im-application`、`im-infrastructure` 或 `im-interfaces`。
2. `im-application` 不依赖具体的接口协议和基础设施实现。
3. 仓储接口、领域服务接口和领域事件定义放在领域层。
4. 仓储实现、数据库实体、缓存对象、消息发布、分布式锁和三方组件适配放在基础设施层。
5. HTTP、WebSocket、事件监听、请求响应模型和协议转换放在接口层。
6. 启动类、运行配置和应用组装放在启动层。

## 模块

| 模块 | 职责 | 主要内容 |
|------|------|----------|
| `im-common` | 公共模块 | 通用常量、异常、基础枚举、响应模型、工具类 |
| `im-domain` | 领域层 | 聚合根、实体、值对象、领域服务、领域事件、仓储接口 |
| `im-application` | 应用层 | 应用服务、用例编排、事务边界、应用 DTO、命令查询模型、应用事件发布接口、锁和通知抽象 |
| `im-infrastructure` | 基础设施层 | MySQL 仓储实现、MyBatis 实体和 Mapper、Redis 缓存、JetCache、Redisson、JWT、BCrypt、Spring 事件发布 |
| `im-interfaces` | 接口层 | HTTP Controller、WebSocket Controller、事件 Listener、IO 模型、接口转换器、Web 配置 |
| `im-bootstrap` | 启动层 | `ImChatApplication`、`application.yml`、日志配置、启动测试 |

### 消息投递架构

项目支持多应用实例部署。客户端 WebSocket 连接只会落到某一个应用实例，但消息发送方、接收方和群成员可能连接在不同实例上。为了解耦“消息写入”和“在线投递”，项目使用 MySQL 保存消息事实，使用 Redis Pub/Sub 在所有应用实例之间广播待推送通知，再由各实例尝试向本机 WebSocket 用户队列投递。

```text
       sender client
            │
            │ STOMP /chat/message/*/send
            ▼
┌──────────────────────┐
│   app instance A     │
│ WebSocket Controller │
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
           │ 4. Redis convertAndSend(topic, notifyCmd)
           ▼
      ┌─────────┐
      │  Redis  │
      │ Pub/Sub │
      └────┬────┘
           │ 5. broadcast to every app instance
  ┌────────┴────────┬─────────────────┐
  ▼                 ▼                 ▼
┌────────────┐   ┌────────────┐   ┌────────────┐
│ instance A │   │ instance B │   │ instance C │
│ Consumer   │   │ Consumer   │   │ Consumer   │
└─────┬──────┘   └─────┬──────┘   └─────┬──────┘
      │                │                │
      │ 6. convertAndSendToUser(receiverId, /queue/...)
      ▼                ▼                ▼
 local STOMP       local STOMP       local STOMP
 sessions          sessions          sessions
      │                │                │
      └────────────── receiver clients ─┘
```

#### 投递链路

1. `ImPrivateWsController` 或 `ImGroupWsController` 接收 WebSocket 命令，调用 `PrivateMessageAppService` 或 `GroupMessageAppService`。
2. 应用服务在事务内完成权限校验、幂等校验、消息副本落库、会话状态更新，并发布领域事件。
3. `PrivateMessageListener` 或 `GroupMessageListener` 监听领域事件，判断需要通知哪些用户，再调用 `ImMessageNotifierInvoker`。
4. `ImMessageNotifierInvoker` 根据通知命令类型选择具体 notifier，例如 `PrivateSentNotifier`、`PrivateRevokedNotifier`、`GroupSentNotifier`、`GroupRevokedNotifier`。
5. notifier 继承 `AbstractRedisImMessageNotifier`，通过 `RedisPublisher` 调用 `RedisTemplate.convertAndSend`，发布到 `RedisTopic` 对应频道。
6. 每个应用实例启动时，`RedisMessageListenerContainer` 会把所有 `RedisSubscriber` 注册为 Redis 订阅者，因此同一条通知会被广播到所有实例。
7. 每个实例上的 consumer 收到通知后调用 `SimpMessagingTemplate.convertAndSendToUser`，向本机 STOMP 用户队列推送。没有目标用户连接的实例不会产生实际客户端投递。
8. 客户端订阅自己的 `/queue/message/private/sent`、`/queue/message/private/revoked`、`/queue/message/group/sent`、`/queue/message/group/revoked` 等队列接收推送。
9. 需要确认的通知会在 Redis 写入 `PENDING` 状态，并注册延迟重试任务；客户端 ACK 后写为 `CONFIRMED`，重试任务触发时会跳过已确认通知。

#### 回执重投队列

需要客户端回执的通知会同时写入确认状态和延迟重投任务。重投任务内容按 `receiptId` 保存，延迟队列里只保存 `receiptId`，避免队列元素携带过大的通知内容。

延迟重投队列按 `ReceiptType` 拆分业务类型，再按 `receiptId` 拆分分片。每个分片有独立消费任务，只拉取自己绑定的队列，避免某一类回执或某一个热点队列长期占用整体消费入口。客户端 ACK 时，服务端通过 `receiptId` 找到任务内容，再定位对应分片并清理队列中的残留任务；如果队列里后续仍弹出已确认任务，只要任务内容不存在就会直接跳过。

这种架构的核心是：消息事实以数据库为准，Redis 只负责跨实例广播在线通知。即使接收方当前不在线，消息副本仍已保存；接收方重新打开会话或查询历史时，从数据库读取消息。

#### 通知通道

| 通知场景 | Redis topic | Redis subscriber | WebSocket 用户队列 |
|----------|-------------|------------------|--------------------|
| 私聊发送 | `im:message:private:sent` | `ImPrivateMessageSentConsumer` | `/queue/message/private/sent` |
| 私聊撤回 | `im:message:private:revoked` | `ImPrivateMessageRevokedConsumer` | `/queue/message/private/revoked` |
| 群聊发送 | `im:message:group:sent` | `ImGroupMessageSentConsumer` | `/queue/message/group/sent` |
| 群聊撤回 | `im:message:group:revoked` | `ImGroupMessageRevokedConsumer` | `/queue/message/group/revoked` |

客户端需要连接 `/ws`，发送命令到 `/chat/message/...`，并订阅上表中的用户队列。`convertAndSendToUser` 使用用户 ID 字符串作为 user destination，因此 WebSocket 握手阶段需要把当前登录用户绑定为 STOMP Principal。

客户端收到需要回执的通知后，统一调用 `/chat/message/notification/ack`。`receiptType` 为发送通知时，服务端会先把消息副本置为 `RECEIVED`，再确认对应发送通知任务；`receiptType` 为撤回通知时，服务端只确认对应撤回通知任务。

#### 投递语义

1. MySQL 中的收件箱消息副本是消息事实来源；Redis Pub/Sub 只承载在线推送通知。
2. Redis Pub/Sub 不保存历史通知。实例重启、网络抖动或客户端离线时，客户端应通过历史查询和消息详情接口补齐数据。
3. 发送接口使用 `messageToken` 做幂等校验，同一会话内重复提交同一 token 会被拒绝，避免客户端重试造成重复消息。
4. 所有实例都会收到 Redis 广播。是否真正推送到客户端，取决于该实例本地是否持有目标用户的 WebSocket 会话。
5. `ImMessageConfirmable` 通知使用 Redis 保存确认状态，回执标识由 `ReceiptType + receiverId + chatId + messageId` 组成。重试任务只负责重新广播未确认通知，不改变消息已落库这一事实。
6. 客户端仍应按 `messageId` 或 `messageToken` 做幂等处理，因为网络超时、客户端 ACK 丢失或重试任务先于 ACK 到达时，仍可能看到重复通知。

### 模块协作

运行时入口集中在 `im-interfaces`。HTTP Controller 负责查询类接口和非实时命令，WebSocket Controller 负责客户端实时消息命令，Spring 事件 Listener 负责承接领域事件后的异步通知。接口层会把请求模型转换为应用层命令或查询，再调用 `im-application` 中的应用服务。

`im-application` 是用例编排层，负责事务边界、分布式锁、权限校验顺序、仓储调用顺序和领域事件发布。它不直接操作数据库、Redis 或 WebSocket 连接，而是依赖领域层仓储接口、领域服务接口和应用层抽象，例如 `DomainEventPublisher`、`ImMessageNotifierInvoker`。

`im-domain` 保存核心业务规则。消息发送、消息状态流转、群成员校验、好友关系校验、会话归属校验等规则在领域模型或领域服务中完成。领域层只声明仓储接口，不感知 MySQL、Redis、STOMP 或 Spring MVC。

`im-infrastructure` 提供技术实现，包括 MySQL 仓储、MyBatis Mapper、Redis 会话缓存、Redis 发布订阅、分布式锁、JWT、BCrypt 和 Spring 事件发布。`im-bootstrap` 负责把接口层、应用层和基础设施层装配成可运行应用。

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

WebSocket 连接入口为 `/ws`，应用消息前缀为 `/chat`，订阅代理前缀为 `/topic` 和 `/queue`。

| 目标地址 | 说明 |
|----------|------|
| `/chat/message/private/send` | 发送私聊消息 |
| `/chat/message/private/read` | 已读私聊消息 |
| `/chat/message/private/revoke` | 撤回私聊消息 |
| `/chat/message/group/send` | 发送群聊消息 |
| `/chat/message/group/read` | 已读群聊消息 |
| `/chat/message/group/revoke` | 撤回群聊消息 |
| `/chat/message/notification/ack` | 确认通知已收到，发送通知回执也会触发消息接收 |

## 消息收发核心流程

### 私聊消息发送

1. 客户端通过 WebSocket 向 `/chat/message/private/send` 发送 `ImPrivateMessageSendRequest`。
2. `ImPrivateWsController` 将请求转换为 `ImPrivateMessageSendCmd`，调用 `PrivateMessageAppService.sendMessage`。
3. 应用层按 `chatId + messageToken` 加分布式锁，避免同一会话内重复提交同一客户端消息。
4. 应用层加载发送方私聊会话，校验当前用户拥有该会话，再加载对端会话。
5. `FriendService` 校验双方好友关系正常，`ImMessageService.ensurePrivateMessageUnique` 校验消息 token 未写入。
6. 应用层生成同一个 `messageId` 的两份收件箱消息：发送方副本和接收方副本。
7. 发送方副本立即带有发送、接收、已读时间；接收方副本初始为 `SENT`。
8. 两份消息写入 `ImPrivateInboxMessageRepository`，双方会话通过 `receiveLatestMessage` 更新最近消息、未读数和活跃时间。
9. 应用层发布 `ImPrivateMessageSentEvent`。
10. `PrivateMessageListener` 监听事件后调用 `onMessageSent`；如果接收方在线，则通过 `ImMessageNotifierInvoker` 创建 Redis 确认状态并发布通知，所有实例收到广播后再尝试投递到本机 WebSocket 用户队列。

### 私聊消息接收、已读与撤回

客户端收到私聊发送通知后，通过 `/chat/message/notification/ack` 上报 `PRIVATE_MESSAGE_SEND` 回执。`NotificationAckAppService` 会通过回执策略加载当前用户消息副本并调用 `ImPrivateInboxMessage.receive`，只更新本地副本的接收状态，然后统一确认对应发送通知任务。私聊已读入口 `/chat/message/private/read` 会调用消息副本的 `read`，再更新当前用户私聊会话的 `readMessageId` 和未读数。

私聊撤回入口 `/chat/message/private/revoke` 会加载发送方和接收方两份副本。`ImMessageService.revokePrivateMessage` 先确认两份副本的原始发送人都是当前用户，再同时撤回两份副本。持久化后发布 `ImPrivateMessageRevokedEvent`，事件监听器负责推送撤回通知。客户端收到撤回通知后通过 `/chat/message/notification/ack` 确认。

### 群聊消息发送

1. 客户端通过 WebSocket 向 `/chat/message/group/send` 发送 `GroupMessageSendRequest`。
2. `ImGroupWsController` 转换为 `GroupMessageSendCmd`，调用 `GroupMessageAppService.sendMessage`。
3. 应用层按 `chatId + messageToken` 加分布式锁，加载发送方群聊会话并校验会话归属。
4. 应用层加载群组，`GroupService.ensureGroupMember` 校验发送方仍是有效群成员。
5. `ImMessageService.ensureGroupMessageUnique` 按发送方会话、用户和 token 做幂等校验。
6. `GroupService.findMessageRecipients` 找到群内所有接收者会话，并结合 `UserService.isChatting` 标记在线会话状态。
7. `ImMessageService.transmitGroupMessage` 为每个群成员生成一份 `ImGroupInboxMessage`。发送方副本直接为 `READ`，其他成员副本为 `SENT`。
8. 每个成员的群聊会话通过 `receiveLatestMessage` 更新最近消息、未读数、已读位点和活跃时间。
9. 应用层批量保存群消息副本和群聊会话，并发布 `ImGroupMessageSentEvent`。
10. `GroupMessageListener` 监听发送事件后加载群成员会话，跳过发送者和离线用户，为每个在线成员创建 Redis 确认状态并发布通知；所有实例收到广播后再尝试投递到本机 WebSocket 用户队列。

### 群聊消息接收、已读与撤回

客户端收到群聊发送通知后，通过 `/chat/message/notification/ack` 上报 `GROUP_MESSAGE_SEND` 回执。`NotificationAckAppService` 会通过回执策略校验会话归属和群成员身份，只更新当前成员消息副本的接收状态，然后统一确认对应发送通知任务。群聊已读入口 `/chat/message/group/read` 会更新当前成员的消息副本和群聊会话读位点，不影响其他成员副本。

群聊撤回入口 `/chat/message/group/revoke` 会先校验当前用户拥有发送方会话且仍是群成员，再加载同一 `groupId + messageId` 下的全部消息副本。`ImMessageService.revokeGroupMessage` 要求待撤回集合中包含当前用户的发送方副本，然后撤回全部副本。持久化后发布 `ImGroupMessageRevokedEvent`，监听器为群内所有成员会话发布 Redis 撤回通知。客户端收到撤回通知后通过 `/chat/message/notification/ack` 确认。

### 状态与投递边界

消息状态保存在每个用户自己的收件箱副本中。私聊有两份副本，群聊按群成员数量生成多份副本，因此接收、已读和撤回都可以在用户维度表达。会话上的 `lastMessageId`、`readMessageId`、`unreadMessageCount` 和 `activeTime` 用于会话列表展示，不作为消息明细的唯一事实来源。

消息写入与事件发布由应用服务在事务内编排，实时推送通过领域事件之后的监听流程完成。这样可以把核心写模型与在线通知解耦：用户离线时消息仍然落库，用户在线时再通过 Redis Pub/Sub 和 STOMP 用户队列推送通知。

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
| 接口协议 | `Spring MVC`、`Spring WebSocket`、`STOMP`、`Springdoc OpenAPI` |
| 对象转换 | `MapStruct` |
| 认证与安全 | `JWT`、`BCrypt` |
| 测试 | `JUnit 5`、`Mockito`、`H2` |

## 运行

### 环境准备

1. 安装 `JDK 21`。
2. 安装 `Maven`。
3. 准备 `MySQL`，默认数据库为 `im_chat`。
4. 准备 `Redis`，默认地址为 `127.0.0.1:6379`。
5. 执行 `sql/ddl.sql` 初始化数据库表结构。

默认运行配置位于：

```text
im-bootstrap/src/main/resources/application.yml
```

默认端口：

```text
8888
```

### 构建

```bash
mvn clean package
```

### 测试

```bash
mvn test
```

### 启动

```bash
mvn -pl im-bootstrap -am spring-boot:run
```

启动类：

```text
com.co.kc.imchat.ImChatApplication
```

## 目录

```text
.
├── im-application
├── im-bootstrap
├── im-common
├── im-domain
├── im-infrastructure
├── im-interfaces
├── sql
│   └── ddl.sql
└── pom.xml
```

## 开发约定

1. 新增核心业务规则时优先放在 `im-domain`。
2. 新增用例编排时放在 `im-application`，应用服务只组织流程，不承载领域规则。
3. 新增 HTTP、WebSocket 或事件监听入口时放在 `im-interfaces`。
4. 新增数据库、Redis、消息发布、令牌、加密、分布式锁等技术实现时放在 `im-infrastructure`。
5. 新增通用工具、通用异常或跨模块基础类型时放在 `im-common`。
6. 不允许领域层反向依赖应用层、接口层或基础设施层。
7. 应用层命令、查询和通知模型优先使用 `record` 表达不可变输入输出模型。
