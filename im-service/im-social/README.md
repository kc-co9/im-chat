# im-social

社交服务聚合模块，承载好友与群组两个限界上下文。两个上下文共享一个部署和 MySQL Schema，
但不共享聚合、生命周期或仓储边界。

## 领域位置与上下文地图

| 子域 | 类型 | 限界上下文 | 职责 |
|---|---|---|---|
| 好友子域 | 支撑子域 | 好友上下文 | 双向好友关系、单边备注、拉黑和当前用户视角的好友资料 |
| 群组子域 | 核心域 | 群组上下文 | 群组、群主、成员、成员昵称、群公告和成员数量 |

## 统一语言

| 上下文 | 业务术语 | 类型 | 建模名称 |
|---|---|---|---|
| 好友 | 好友关系 | 聚合根 | `Friend` |
| 好友 | 好友关系标识、关系边 | 值对象 | `FriendId`、`FriendEdge` |
| 好友 | 好友备注、状态、展示名称 | 值对象 | `FriendAlias`、`FriendStatus`、`FriendDisplayName` |
| 好友 | 当前用户视角的好友资料 | 派生投影 | `FriendProfile` |
| 好友 | 好友规则 | 领域服务 | `FriendService` |
| 好友 | 好友关系变化 | 领域事件 | `FriendAddedEvent`、`FriendRemovedEvent` |
| 群组 | 群组 | 聚合根 | `Group` |
| 群组 | 群成员 | 实体 | `GroupMember` |
| 群组 | 群名称、成员标识、成员数量、群公告 | 值对象 | `GroupName`、`MemberId`、`MemberCount`、`GroupNotification` |
| 群组 | 群组规则 | 领域服务 | `GroupService` |
| 群组 | 群组生命周期变化 | 领域事件 | `GroupCreatedEvent`、`GroupDismissedEvent`、`GroupMemberJoinedEvent`、`GroupMemberRemovedEvent` |

## 关键不变量与生命周期

- 用户不能与自己建立好友关系；建立好友会补齐两个方向的 `Friend`，只有双方关系都有效时，好友关系才可供业务使用。
- 备注、拉黑和解除拉黑只改变当前方向的好友关系；好友上下文通过独立 `FriendRepository` 持久化，不进入群组聚合。
- 群组必须处于活动状态才能执行成员或设置变更；只有群主可以解散群组、转让群主、踢人或修改群公告。
- 群主不能直接退群，也不能把自己踢出；转让目标必须是现有成员。`Group` 维护群状态和成员数量。
- `GroupMember` 在源码中是实体。成员集合因规模和独立表结构通过 `GroupMemberRepository` 分离存取，但这只是持久化访问边界，不把成员提升为独立聚合；加入、离开、踢出和成员别名变更仍由 `GroupService` 协调，并受 `Group` 活跃状态、群主权限或成员资格约束。
- 好友和群组事件表达已经发生的 Social 事实；跨服务会话维护由应用层在边界外响应，不把 Message 模型并入 Social 聚合。

## 协作与状态所有权

- Social 独占 `im_chat_social` Schema，拥有 `db_friend`、`db_im_group` 和 `db_im_group_member`；服务之间不共享表。
- Account 仍拥有用户身份。Social 通过 Account Facade 得到 `UserProfile` 投影，用它组合 `FriendProfile` 和成员展示信息，不持久化 Account 用户资料。
- `FriendProfile` 是当前用户视角的派生资料；`FriendDisplayName` 依次取好友备注、Account 用户名和好友用户 ID。
- Message 通过 `im-social-facade` 获取好友关系、群成员和消息接收人投影，不接触 Social 聚合或数据库。
- 群组创建、邀请、退群、踢人和解散通过 Message Facade 维护对应群聊；Social 读取 `UserGroupChatSummary` 作为会话投影，但聊天和消息事实仍归 Message。

## 子模块与验证

- [`im-social-facade`](im-social-facade/README.md)：好友、群组、成员与消息接收人查询契约。
- [`im-social-server`](im-social-server/README.md)：好友和群组领域实现、Account/Message 适配及 Social 数据持久化。

```bash
mvn -q -pl im-service/im-social -am test
./scripts/verify.sh architecture
```
