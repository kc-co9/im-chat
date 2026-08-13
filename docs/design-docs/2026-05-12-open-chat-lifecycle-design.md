# Open Chat Lifecycle Design

## Goal

Unify private and group chat lifecycle around frontend-friendly "open chat" interactions.

## Problem

Private chat currently has a clear split:

- Adding a friend creates the relationship.
- Creating a private chat creates the conversation entry.
- Entering a private chat marks the current session.

Group chat is less consistent:

- Creating a group creates the group, membership, and group chat rows at the same time.
- This makes `group_member` and `group_chat` lifecycles less readable than the private-chat flow.

## Decision

The frontend should not need to call separate "create chat" and "enter chat" APIs.

The frontend should call one "open chat" API when the user wants to enter a conversation. The backend should get or create the current user's chat row, mark the session as entering that chat, then return the chat id and display data.

## Frontend Interaction Model

### Private Chat

User action:

- User opens a friend profile or friend list and taps "send message".

API:

- `POST /im/chat/openPrivateChat`

Input:

- `peerUserId`

Backend behavior:

1. Validate both users are friends.
2. Get or create both users' private chat rows.
3. Mark current user as entering their private chat.
4. Return current user's `chatId` and display data.

### Group Creation

User action:

- User selects members, enters group name, and taps "create".

API:

- `POST /im/group/createGroup`

Input:

- `memberIds`
- `groupName`

Backend behavior:

1. Create `ImGroup`.
2. Create `ImGroupMember` rows for owner and initial members.
3. Create or get only the owner's `ImGroupChat`.
4. Mark owner as entering the group chat.
5. Return `groupId` and owner's `chatId`.

The API should not create `ImGroupChat` rows for every member at group creation time.

### Opening a Group Chat

User action:

- User taps a group from a group list, invite, notification, search result, or after app refresh.

API:

- `POST /im/chat/openGroupChat`

Input:

- `groupId`

Backend behavior:

1. Validate current user is an `ImGroupMember`.
2. Get or create current user's `ImGroupChat`.
3. Mark current user as entering the group chat.
4. Return current user's `chatId` and group display data.

## Backend Model

### Relationship Facts

- Private relationship: `Friend`
- Group relationship: `ImGroupMember`

These should be created independently from chat entry creation.

### Chat Entries

- Private chat entry: `ImPrivateChat`
- Group chat entry: `ImGroupChat`

These represent a user's conversation entry and read/unread state.

## Message Fan-out

Group message recipients should come from `ImGroupMember`.

When sending a group message, if a member does not have an `ImGroupChat` row yet, the backend should create it before writing the member's inbox message and updating unread state.

This keeps lazy chat creation safe while still allowing messages to appear in each member's chat list after the first message.

## API Cleanup

No compatibility is required because the system is not live.

Remove or replace the old external APIs:

- Replace `createPrivateChat` + `enterPrivateChat` with `openPrivateChat`.
- Replace `createGroupChat` + `enterGroupChat` with `createGroup` + `openGroupChat`.

Internal application methods can still be decomposed, but external HTTP semantics should match frontend user actions.

## Naming

Recommended command/response names:

- `ImPrivateChatOpenCmd`
- `ImGroupCreateCmd`
- `ImGroupChatOpenCmd`
- `ImChatOpenDTO`
- `ImGroupCreateDTO`

Recommended request/response names:

- `ImPrivateChatOpenRequest`
- `ImPrivateChatOpenResponse`
- `ImGroupCreateRequest`
- `ImGroupCreateResponse`
- `ImGroupChatOpenRequest`
- `ImGroupChatOpenResponse`
