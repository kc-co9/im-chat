# Root README Context Restoration Implementation Plan

> **For agentic workers:** Execute in the current workspace without subagents, worktrees or commits. Track each step with the checkboxes below.

**Goal:** Restore the original README's explanatory DDD and Harness content while retaining current architecture facts and concise document navigation.

**Architecture:** Keep the root README as the project-level introduction. Explain concepts and motivation there, then link detailed, enforceable rules to architecture and reference documents instead of duplicating them.

**Tech Stack:** Markdown and repository Harness scripts.

---

### Task 1: Restore DDD context

- [x] Restore the motivation for DDD and explain aggregate roots, entities, value objects, domain services, events and repositories.
- [x] Restore current subdomain, bounded-context and ubiquitous-language mappings without copying stale class names.
- [x] Retain the current dependency-direction and layer-responsibility explanation.

### Task 2: Restore Harness motivation

- [x] Restore why the repository needs Harness Engineering and its five operating principles.
- [x] Retain the current enforcement-level table and add the feedback-loop explanation.
- [x] Keep detailed rule lifecycle and examples linked to the Harness Guide.

### Task 3: Verify and archive

- [x] Run drift checks and `git diff --check`.
- [x] Review the rendered Markdown structure and links.
- [x] Move this plan to completed and update execution-plan indexes.
