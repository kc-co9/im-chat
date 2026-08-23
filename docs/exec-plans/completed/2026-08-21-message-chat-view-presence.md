# Message Chat View Presence Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Move current-chat viewing state into `im-message`, implement `exitChat`, and make message unread decisions independent of Account `Session.chatId`.

**Architecture:** `openPrivateChat/openGroupChat` write a message-domain `ImChatView`; `exitChat` clears the user-level presence; `hidePrivateChat/hideGroupChat` keep their hidden-list semantics and clear presence. Message fan-out queries the presence repository directly. Account `Session.chatId`, `checkUserChatting`, and the message-to-account adapter path are removed after message behavior is covered.

**Tech Stack:** Java 21, Spring Boot, MapStruct, Redis/Redisson infrastructure, MyBatis repositories, JUnit 5, Mockito, Vue/TypeScript frontend.

---

### Task 1: Add the message-domain viewing state

**Files:**
- Create: `im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/domain/chat/model/ImChatView.java`
- Create: `im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/domain/chat/repository/ImChatViewRepository.java`
- The viewing state is represented by `ImChatView` and accessed through `ImChatViewRepository`; no separate wrapper domain service is needed.

- [x] Write tests for entering a chat, clearing the current state, and matching the current chat.
- [x] Run the focused test and confirm it fails because the presence types do not exist.
- [x] Implement the immutable `ImChatView(UserId, ImChatId)` record and repository port with `save`, `clear`, and `isViewing` semantics.
- [x] Keep the viewing behavior in the repository port: `save`, `clear`, and `isViewing`.
- [x] Run the focused test and confirm it passes.

### Task 2: Implement Redis presence storage

**Files:**
- Create: `im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/infrastructure/domain/repository/RedisImChatViewRepository.java`
- Modify: `im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/infrastructure/config/RedisConfig.java` only if the existing template cannot serialize the value safely
- Test: `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/infrastructure/domain/repository/RedisImChatViewRepositoryTest.java`

- [x] Add a failing repository test for save, lookup, clear, and TTL configuration using the project’s existing Redis test pattern.
- [x] Implement a user-scoped key and a short typed value representation; keep Redis keys, TTL, and serialization out of domain classes.
- [x] Verify that missing or expired state returns `false` and that `exit` is idempotent.
- [x] Run the repository test and confirm it passes.

### Task 3: Connect open/exit/hide application behavior

**Files:**
- Modify: `im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/application/ChatAppService.java`
- Modify: `im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/interfaces/http/ChatController.java`
- Create or modify: `im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/model/cqrs/command/chat/ChatExitCmd.java`
- Modify: `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/application/PrivateChatAppServiceTest.java`
- Modify: `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/application/GroupChatAppServiceTest.java`

- [x] Add failing tests proving open private/group writes presence, exit clears it, and hide clears it while still hiding the chat.
- [x] Add `exitChat(ChatExitCmd)` as a CQRS application command with no scalar public signature.
- [x] Inject the viewing repository into `ChatAppService` and wire `open*`, `exitChat`, and `hide*` behavior.
- [x] Add the `/message/chat/exitChat` HTTP endpoint matching the frontend service contract.
- [x] Run the focused application and controller tests.

### Task 4: Replace Account-based chatting checks in message delivery

**Files:**
- Modify: `im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/application/PrivateMessageAppService.java`
- Modify: `im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/application/GroupMessageAppService.java`
- Modify: `im-service/im-message/im-message-server/src/main/java/com/co/kc/imchat/service/message/infrastructure/config/beans/AppServiceBeans.java`
- Modify: `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/application/PrivateChatAppServiceTest.java`
- Modify: `im-service/im-message/im-message-server/src/test/java/com/co/kc/imchat/service/message/application/GroupChatAppServiceTest.java`

- [x] Add failing tests for viewing the receiver’s chat versus viewing another chat/no presence, covering both private and group fan-out.
- [x] Inject `ImChatViewRepository` into message application services and use its `isViewing` query for `receiveLatestMessage` decisions.
- [x] Remove `AccountAdapter.isChatting` usage and update construction tests/test doubles.
- [x] Run message application tests and verify unread/read behavior.

### Task 5: Remove the obsolete Account Session chat-state RPC

**Files:**
- Modify: `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/application/SessionAppService.java`
- Modify/delete: `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/interfaces/rpc/SessionRpcService.java`
- Modify/delete: `im-service/im-account/im-account-facade/src/main/java/com/co/kc/imchat/service/account/facade/SessionService.java`
- Modify/delete: `im-service/im-account/im-account-facade/src/main/java/com/co/kc/imchat/service/account/facade/params/UserChattingCheckParams.java`
- Modify/delete: `im-service/im-account/im-account-facade/src/main/java/com/co/kc/imchat/service/account/facade/dto/UserChattingCheckDTO.java`
- Modify: `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/domain/session/model/Session.java`
- Modify: `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/model/cqrs/dto/SessionDTO.java`
- Modify: `im-service/im-account/im-account-server/src/main/java/com/co/kc/imchat/service/account/transformer/domain/UserDomainTransformer.java`

- [x] Confirm no remaining production caller exists after Task 4.
- [x] Remove `checkUserChatting` and the unused `Session.chatId` persistence field and builder mapping.
- [x] Remove obsolete facade contracts and account tests only after compilation proves no caller remains.
- [x] Run account module tests and architecture checks.

### Task 6: Synchronize frontend lifecycle calls

**Files:**
- Modify: `/Users/kc/Code/private/im-chat-frontend/src/composables/useChatManagement.ts`
- Modify: `/Users/kc/Code/private/im-chat-frontend/src/components/Chat.vue`
- Modify: `/Users/kc/Code/private/im-chat-frontend/src/services/chatService.ts` only if request shape changes
- Test: `/Users/kc/Code/private/im-chat-frontend/src` existing service/component tests as applicable

- [x] Call `exitChat` when leaving the message tab, logging out, or unmounting the chat view.
- [x] Keep direct `open*` when switching between conversations.
- [x] Keep `hide*` for the existing close/remove-from-list action.
- [x] Run frontend typecheck and unit tests.

### Task 7: Full verification and documentation

**Files:**
- Modify: `im-service/im-message/im-message-server/README.md` for the new presence lifecycle
- Modify: `docs/references/HARNESS_GUIDE.md` only if a stable DDD/presence boundary rule is missing

- [x] Run focused Maven tests for message and account modules.
- [x] Run frontend tests/typecheck.
- [x] Run `./scripts/verify.sh quick`, `./scripts/check-drift.sh`, and `git diff --check`.
- [x] Review the diff for unrelated changes; do not commit unless explicitly requested.
