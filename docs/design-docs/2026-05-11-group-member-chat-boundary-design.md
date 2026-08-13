# Group Member and Chat Boundary Design

## Goal

Clarify the boundary between group membership and per-user group chat state.

## Problem

The current group chat model lets `db_im_group_chat` and `db_im_group_member` carry overlapping user-scoped fields. This makes it unclear whether a row represents membership in a group or a conversation entry in a user's chat list.

## Decisions

### Group

`db_im_group` represents the group itself.

Fields:

- `group_id`
- `owner_id`
- `name`
- `notification`

`owner_id` means the current group owner. It is not an audit-only creator field. Ownership may be transferred later.

Rules:

- `owner_id` must also exist in `db_im_group_member`.
- Ownership transfer can only target an existing group member.
- The owner cannot be removed from the group before ownership is transferred.

### Group Member

`db_im_group_member` represents group membership.

Fields:

- `group_id`
- `user_id`
- `user_alias`
- `join_time`

`user_alias` is the member's display name inside the group. It belongs to membership, not to the chat entry.

No role field is introduced for now.

### Group Chat

`db_im_group_chat` represents a user's conversation entry for a group.

Fields:

- `chat_id`
- `group_id`
- `user_id`
- `group_alias`
- `last_message_id`
- `read_message_id`
- `unread_message_count`

`group_alias` is the user's private remark for the group. It belongs to the user's chat entry.

`user_alias` must be removed from `db_im_group_chat`.

## Creation Timing

Creating a group should synchronously create:

1. `db_im_group`
2. `db_im_group_member` rows for the owner and initial members
3. `db_im_group_chat` rows for the owner and initial members

This matches the current API model, where `chat_id` is the entry point for sending, querying, and entering a group chat. Lazy chat creation can be introduced later if the product needs hidden or delayed conversation entries.

## Query and Write Boundaries

Membership checks should use `db_im_group_member`.

Per-user chat state should use `db_im_group_chat`.

Examples:

- Send message recipients come from `db_im_group_member`.
- Message fan-out updates each recipient's `db_im_group_chat`.
- History permission checks confirm membership through `db_im_group_member`.
- Chat list rendering reads `db_im_group_chat`, and joins or fetches group/member data for names.

## Future Notes

If the product later needs hiding or deleting a conversation while preserving membership, add state to `db_im_group_chat`. Do not remove `db_im_group_member` for that workflow.
