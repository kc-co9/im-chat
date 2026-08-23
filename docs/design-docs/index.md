# Design Documents

Design documents capture context, alternatives, decisions, and consequences. They describe why the system is shaped a certain way; execution status belongs in `docs/exec-plans`.

## Current Designs

- [Documentation and Harness structure](2026-08-23-documentation-harness-structure-design.md): documentation ownership, stable engineering references, Harness enforcement boundaries, and current-fact cleanup. Implementation completed.
- [Review findings remediation](2026-08-23-review-findings-remediation-design.md): canonical Session locking, authentication lifetime alignment, refresh routing, chat unread behavior, Broker owner routing, and README context restoration. Implementation completed.
- [README and message notification documentation](2026-08-23-readme-notification-documentation-design.md): README ownership, end-to-end notification and ACK flow, receipt retry semantics, and current contract-name cleanup. Implementation completed.
- [Message chat view presence](2026-08-21-message-chat-view-presence-design.md): message-owned current chat view state and unread behavior. Implementation completed.
- [Centralized session authentication](2026-08-14-centralized-session-authentication-design.md): centralized online authentication, rotating refresh tokens, and single-device session replacement. Implementation completed.
- [Broker business monitoring](2026-08-13-broker-business-monitoring-design.md): proposed governance modules and separated admin/monitor interfaces. Implementation has not started.

## Historical Designs

- [Group member and chat boundary](2026-05-11-group-member-chat-boundary-design.md)
- [Open chat lifecycle](2026-05-12-open-chat-lifecycle-design.md)
- [IM module split](2026-05-20-im-module-split-design.md)
- [Architecture upgrade](2026-06-08-architecture-upgrade-design.md)

New documents use `YYYY-MM-DD-<topic>-design.md` and must link to their execution plan when implementation begins.
