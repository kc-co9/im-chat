# Open Chat Lifecycle Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace separate create/enter chat HTTP semantics with frontend-oriented open-chat APIs and make group chat rows lazy per user.

**Architecture:** Friend and group membership remain relationship facts. Private and group chat rows become per-user conversation entries created by open-chat flows. Creating a group creates group and members, then opens only the owner's group chat.

**Tech Stack:** Java 8, Spring Boot 2.7, MyBatis-Plus, MapStruct, JUnit 5, AssertJ.

---

## File Structure

- Modify `src/main/java/com/co/kc/imchat/application/ChatAppService.java`: replace create/enter methods with open flows; create group without creating every member's chat.
- Modify `src/main/java/com/co/kc/imchat/application/ImGroupAppService.java`: group message fan-out creates missing member chats.
- Modify `src/main/java/com/co/kc/imchat/endpoint/http/ChatController.java`: expose `openPrivateChat`, `openGroupChat`; remove old create/enter chat endpoints.
- Create/replace CQRS command and DTO classes under `src/main/java/com/co/kc/imchat/model/cqrs/command/chat/` and `src/main/java/com/co/kc/imchat/model/cqrs/dto/im/`.
- Create/replace HTTP request/response classes under `src/main/java/com/co/kc/imchat/model/io/chat/`.
- Modify `src/main/java/com/co/kc/imchat/transformer/http/ImChatHttpIoTransformer.java`: map open DTOs to responses.
- Modify tests in `src/test/java/com/co/kc/imchat/application/GroupChatAppServiceTest.java` and add private open-chat tests as needed.

## Task 1: Add Tests for the New Lifecycle

**Files:**
- Modify: `src/test/java/com/co/kc/imchat/application/GroupChatAppServiceTest.java`
- Create if useful: `src/test/java/com/co/kc/imchat/application/PrivateChatAppServiceTest.java`

- [ ] **Step 1: Update group creation test**

Change `createGroupChatPersistsGroupMembersSeparatelyFromGroupChats` so it expects:

- group is saved
- owner + initial members are saved as `ImGroupMember`
- only the owner's `ImGroupChat` is saved
- returned DTO contains `groupId` and owner's `chatId`

- [ ] **Step 2: Add open group chat test**

Add a test where current user is an existing group member but no group chat row exists. Calling `openGroupChat(groupId, userId)` should:

- create one `ImGroupChat` for current user
- enter that chat in session
- return the new `chatId`

- [ ] **Step 3: Add private open chat test**

Add or update a private chat test where current user and peer are friends. Calling `openPrivateChat(peerUserId, userId)` should:

- get or create both users' private chat rows
- enter current user's chat
- return current user's `chatId`

- [ ] **Step 4: Verify RED**

Run:

```bash
mvn -q -Dtest=GroupChatAppServiceTest test
```

Expected: compilation or test failure because new command/DTO/application methods do not exist yet.

## Task 2: Add New Command and DTO Types

**Files:**
- Create: `src/main/java/com/co/kc/imchat/model/cqrs/command/chat/ImPrivateChatOpenCmd.java`
- Create: `src/main/java/com/co/kc/imchat/model/cqrs/command/chat/ImGroupCreateCmd.java`
- Create: `src/main/java/com/co/kc/imchat/model/cqrs/command/chat/ImGroupChatOpenCmd.java`
- Create: `src/main/java/com/co/kc/imchat/model/cqrs/dto/im/ImChatOpenDTO.java`
- Create: `src/main/java/com/co/kc/imchat/model/cqrs/dto/im/ImGroupCreateDTO.java`

- [ ] **Step 1: Add command classes**

Use simple Lombok `@Data`, `@NoArgsConstructor`, `@AllArgsConstructor`.

Fields:

- `ImPrivateChatOpenCmd`: `Long userId`, `Long peerUserId`
- `ImGroupCreateCmd`: `Long ownerId`, `List<Long> memberIds`, `String groupName`
- `ImGroupChatOpenCmd`: `Long userId`, `Long groupId`

- [ ] **Step 2: Add DTOs**

Fields:

- `ImChatOpenDTO`: `Long chatId`
- `ImGroupCreateDTO`: `Long groupId`, `Long chatId`

## Task 3: Implement Application Open Flows

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/application/ChatAppService.java`
- Modify: `src/main/java/com/co/kc/imchat/domain/chat/ImGroupChatRepository.java`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/domain/MysqlImGroupChatRepository.java`
- Modify: `src/main/java/com/co/kc/imchat/infrastructure/mybatis/service/DbImGroupChatService.java`

- [ ] **Step 1: Add group chat lookup by group and user**

Add repository method:

```java
ImGroupChat find(ImGroupId groupId, UserId userId);
```

Implement it through `DbImGroupChatService#getByGroupIdAndUserId`.

- [ ] **Step 2: Implement `openPrivateChat`**

Move current `createPrivateChat` creation logic into:

```java
public ImChatOpenDTO openPrivateChat(ImPrivateChatOpenCmd command)
```

After ensuring current user's chat exists, call `imChatService.enterChat(senderChat.getId(), senderId)` and return `ImChatOpenDTO`.

- [ ] **Step 3: Implement `createGroup`**

Replace current `createGroupChat` with:

```java
public ImGroupCreateDTO createGroup(ImGroupCreateCmd command)
```

It should:

- create `ImGroup`
- create owner + initial `ImGroupMember` rows
- get or create only owner `ImGroupChat`
- call `imChatService.enterChat(ownerChat.getId(), ownerId)`
- return `groupId` and owner `chatId`

- [ ] **Step 4: Implement `openGroupChat`**

Add:

```java
public ImChatOpenDTO openGroupChat(ImGroupChatOpenCmd command)
```

It should:

- verify current user is an `ImGroupMember`
- get or create current user's `ImGroupChat`
- call `imChatService.enterChat(chatId, userId)`
- mark unread messages read, preserving existing `enterGroupChat` behavior
- return `ImChatOpenDTO`

- [ ] **Step 5: Remove or stop using old methods**

Remove or replace external usage of:

- `createPrivateChat`
- `enterPrivateChat`
- `createGroupChat`
- `enterGroupChat`

No compatibility needed.

## Task 4: Update Group Message Fan-out for Lazy Group Chats

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/application/ImGroupAppService.java`

- [ ] **Step 1: Ensure member chat rows exist**

In `sendMessage`, after loading members, get chats by group/user ids. If a member has no `ImGroupChat`, create one with:

- new `chatId`
- same `groupId`
- member `userId`
- type `GROUP`
- unread count `0`

- [ ] **Step 2: Preserve inbox/read behavior**

Use the created or existing member chats to build inbox messages and update unread/read state.

- [ ] **Step 3: Save all chats**

Call `imGroupChatRepository.saveAll(memberChatList)` so both new and updated chat rows are persisted.

## Task 5: Update HTTP API Model

**Files:**
- Modify: `src/main/java/com/co/kc/imchat/endpoint/http/ChatController.java`
- Create: `src/main/java/com/co/kc/imchat/model/io/chat/ImPrivateChatOpenRequest.java`
- Create: `src/main/java/com/co/kc/imchat/model/io/chat/ImPrivateChatOpenResponse.java`
- Create: `src/main/java/com/co/kc/imchat/model/io/chat/ImGroupCreateRequest.java`
- Create: `src/main/java/com/co/kc/imchat/model/io/chat/ImGroupCreateResponse.java`
- Create: `src/main/java/com/co/kc/imchat/model/io/chat/ImGroupChatOpenRequest.java`
- Create: `src/main/java/com/co/kc/imchat/model/io/chat/ImGroupChatOpenResponse.java`
- Modify: `src/main/java/com/co/kc/imchat/transformer/http/ImChatHttpIoTransformer.java`

- [ ] **Step 1: Replace endpoints**

Expose:

- `POST /im/chat/openPrivateChat`
- `POST /im/chat/openGroupChat`
- `POST /im/chat/createGroup`

Remove old endpoint methods from `ChatController`.

- [ ] **Step 2: Add request/response classes**

Requests:

- `ImPrivateChatOpenRequest`: `Long peerUserId`
- `ImGroupCreateRequest`: `List<Long> memberIds`, `String groupName`
- `ImGroupChatOpenRequest`: `Long groupId`

Responses:

- private/group open responses: `Long chatId`
- create group response: `Long groupId`, `Long chatId`

## Task 6: Verification

**Files:**
- Full project

- [ ] **Step 1: Focused tests**

Run:

```bash
mvn -q -Dtest=GroupChatAppServiceTest test
```

Expected: pass.

- [ ] **Step 2: Compile**

Run:

```bash
mvn -q -DskipTests compile
```

Expected: pass with no references to removed controller classes.

- [ ] **Step 3: Full tests**

Run:

```bash
mvn clean test
```

Expected: all tests pass.

- [ ] **Step 4: Stale API scan**

Run:

```bash
rg "createPrivateChat|enterPrivateChat|createGroupChat|enterGroupChat|ImPrivateChatCreate|ImPrivateChatEnter|ImGroupChatCreate|ImGroupChatEnter" src/main/java src/test/java
```

Expected: no stale external API usage remains. Existing class files may be deleted or fully unused.
