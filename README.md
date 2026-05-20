# ImChat

## 概述

`ImChat` 是一个基于 `Spring Boot` 的即时通讯服务，主要覆盖用户、好友、群组、会话、私聊消息与群聊消息等业务场景。项目按照 `DDD`（`Domain-Driven Design`，领域驱动设计）进行建模和模块拆分，将领域模型、用例编排、接口协议和基础设施实现分离，降低业务逻辑与技术细节之间的耦合。

当前项目采用 `Maven` 多模块组织，根项目仍命名为 `im-chat`，业务启动模块为 `im-bootstrap`。

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
| `ImPrivateMessageReceivedEvent` | 消息上下文 | 私聊消息已接收 |
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
| `im-application` | 应用层 | 应用服务、用例编排、事务边界、应用 DTO、应用事件发布接口、锁和通知抽象 |
| `im-infrastructure` | 基础设施层 | MySQL 仓储实现、MyBatis 实体和 Mapper、Redis 缓存、JetCache、Redisson、JWT、BCrypt、Spring 事件发布 |
| `im-interfaces` | 接口层 | HTTP Controller、WebSocket Controller、事件 Listener、IO 模型、接口转换器、Web 配置 |
| `im-bootstrap` | 启动层 | `ImChatApplication`、`application.yml`、日志配置、启动测试 |

## 接口

### HTTP

| 资源 | 路径前缀 | 说明 |
|------|----------|------|
| 用户 | `/user` | 注册、登录、登出、用户详情 |
| 好友 | `/friend` | 好友列表、详情、搜索、添加、删除、拉黑、备注 |
| 聊天会话 | `/im/chat` | 会话列表、打开私聊、打开群聊、退出和隐藏会话 |
| 群组 | `/im/group` | 群组创建、成员管理、群设置、群详情、群消息查询 |
| 私聊消息 | `/im/private` | 私聊消息详情和历史查询 |

### WebSocket

WebSocket 连接入口为 `/ws`，应用消息前缀为 `/chat`，订阅代理前缀为 `/topic` 和 `/queue`。

| 目标地址 | 说明 |
|----------|------|
| `/chat/message/private/send` | 发送私聊消息 |
| `/chat/message/private/receive` | 接收私聊消息 |
| `/chat/message/private/read` | 已读私聊消息 |
| `/chat/message/private/revoke` | 撤回私聊消息 |
| `/chat/message/group/send` | 发送群聊消息 |
| `/chat/message/group/receive` | 接收群聊消息 |
| `/chat/message/group/read` | 已读群聊消息 |
| `/chat/message/group/revoke` | 撤回群聊消息 |

## 技术栈

| 分类 | 技术 |
|------|------|
| 基础框架 | `Spring Boot 2.7.18` |
| 构建工具 | `Maven` |
| 语言版本 | `Java 8` |
| 持久化 | `MyBatis`、`MyBatis-Plus`、`MySQL` |
| 数据源 | `Druid` |
| 缓存 | `Redis`、`JetCache`、`Caffeine` |
| Redis 客户端 | `Redisson`、`Spring Data Redis` |
| 接口协议 | `Spring MVC`、`Spring WebSocket`、`STOMP` |
| 对象转换 | `MapStruct` |
| 认证与安全 | `JWT`、`BCrypt` |
| 测试 | `JUnit 5`、`Mockito`、`H2` |

## 运行

### 环境准备

1. 安装 `JDK 8+`。
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
