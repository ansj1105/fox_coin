---
name: fox-coin-change-guardrails
description: Apply strict change guardrails for fox_coin tasks. Use when handling user feedback that narrows scope (client vs admin, specific page only), adds notice event requirements, requires order-number persistence checks (withdraw, token deposit, swap), or requires encoding-safe Java/OpenAPI edits before commit and push.
---

# Fox Coin Change Guardrails

## Lock Scope First
- Read the latest user clarification and freeze scope before editing.
- Keep admin behavior unchanged when user says client-only.
- Edit only the named page or module when user limits target (for example, mining-history only).
- Avoid global replacements for `login_id` to nickname unless user explicitly requests all screens.

## Notice Wiring Checklist
- Confirm `NotificationType` enum contains requested notice types.
- Confirm OpenAPI enum is updated with the same values.
- Wire notifications only on successful business events:
  - level up
  - daily mining limit reached
  - deposit completed
  - inquiry submitted/answered
  - daily mission completed
  - exchange/swap/withdraw completed
- Use dedupe-safe creation path:
  - `createNotificationIfAbsentByRelatedId(...)` for entity-based events
  - `createNotificationIfAbsentByDate(...)` for date-based events

## Order Number Checklist
- Verify withdraw flow generates and persists `order_number`.
- Verify swap flow generates and persists `order_number`.
- Verify token deposit flow persists `order_number`; if missing from inbound payload, generate fallback with `OrderNumberUtils.generateOrderNumber()`.
- Verify response DTO includes `orderNumber` where applicable.

## Encoding Safety Rules
- Prefer `apply_patch` for Java/YAML edits.
- Avoid broad file rewrites with shell commands on Korean source files.
- If shell editing is unavoidable, immediately verify:
  - no broken unicode escapes
  - no malformed string literals
  - UTF-8 readable text in user-facing messages
- Keep user-facing Korean text as unicode escapes if terminal encoding is unstable.

## Validation Before Handoff
- Run `./gradlew.bat compileJava testClasses`.
- If tests fail due environment/file-lock issues, report exact failure and continue with compile-success evidence.
- Summarize:
  - exact files changed
  - whether scope constraints were preserved
  - whether order-number persistence is guaranteed