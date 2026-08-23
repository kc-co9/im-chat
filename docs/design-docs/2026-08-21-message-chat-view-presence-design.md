# 消息聊天查看状态设计

## 背景

前端已经区分了聊天打开、隐藏和退出状态：

- `openPrivateChat` / `openGroupChat`：进入聊天详情页；
- `hidePrivateChat` / `hideGroupChat`：关闭并从聊天列表移除会话；
- `exitChat`：前端服务层已预留，但当前没有调用，后端也尚未实现。

Account 的 `Session.chatId` 曾经被用于判断用户是否正在聊天，但当前登录流程没有可靠的写入入口。聊天页面状态属于消息域，不应继续存放在 Account Session 中。

## 决策

### 聊天接口语义

`openPrivateChat` 和 `openGroupChat`：

1. 激活当前用户的聊天记录；
2. 清理该聊天的未读状态；
3. 写入当前用户正在查看的 `chatId`。

`exitChat(userId)`：

1. 仅清理当前查看状态；
2. 不修改聊天列表可见性和未读数据。

`hidePrivateChat` 和 `hideGroupChat`：

1. 保留隐藏聊天列表项的语义；
2. 隐藏成功后清理对应的当前查看状态；
3. 不复用为普通页面退出动作。

### 查看状态模型

消息域新增最小领域对象：

```text
ImChatView(UserId userId, ImChatId chatId)
```

`chatId` 表示当前正在查看的详情页。TTL、Redis key 和序列化只存在基础设施实现中。用户没有状态或状态过期时，消息按未查看处理。

### 消息处理

消息服务不再调用 Account 的 `checkUserChatting` RPC，也不再依赖 `Session.chatId`。消息分发时由消息域查询 `ImChatView`：

- 当前查看同一 chat：消息直接按已读处理，不增加列表未读数；
- 无状态、查看其他 chat 或状态过期：写入未读消息并增加对应会话未读数。

## 前端配合

前端需要：

1. 打开详情页后继续调用 `open*`；
2. 切换到非消息 Tab、注销或离开页面时调用 `exitChat`；
3. 切换到另一聊天时直接调用新的 `open*`，不需要先调用 `exitChat`；
4. 主动关闭会话时继续调用 `hide*`。

## 验证范围

- `ImChatView` 领域行为测试；
- Redis 仓储保存、匹配清理和 TTL 测试；
- open/exit/hide 应用服务测试；
- 私聊和群聊消息在查看/未查看状态下的未读分支测试；
- Account `checkUserChatting` 和 Message `AccountAdapter.isChatting` 删除后的架构、编译和漂移检查。
