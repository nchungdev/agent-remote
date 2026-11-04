# Active Plan: Chat UI Redesign (Claude / Codex / Antigravity Inspired with Personal Signature)

## Goal
1. Redesign Chat UI with a refined editorial aesthetic (inspired by Claude, Codex, and Antigravity, with `agent-remote`'s own Copper-Obsidian & Cyan-Telemetry signature).
2. Fix Model Selector in `ChatInputBar` to select the active Engine's supported models (live from catalog + accurate engine fallbacks).
3. Group sessions by Project in `ConversationDrawerContent` with full-width drawer on compact screens (`< 600dp`) and standard `360dp` width on tablets/desktop.
4. Keep Engine & Host switching inside Settings, leaving the Drawer footer clean.

## Design Specifications Agreed with User
1. **Color & Signature Identity**:
   - Canvas: Deep Obsidian Slate (`#121215`), Elevated Surface (`#1A1A1F`).
   - User Bubble: Warm Bronze-Charcoal (`#282422`) with Copper-Slate border (`#423833`), shape `RoundedCornerShape(20.dp, 20.dp, 4.dp, 20.dp)`.
   - Assistant Flow: Borderless editorial layout with generous `24.sp` line height (`15.5.sp` font size).
2. **Thinking Block (`ExecutionTraceView`)**:
   - **Collapsed by default** (`isExpanded = false`).
   - Compact bar displaying icon, status label, character count badge (`X ký tự`), and chevron.
   - Click to expand monospace thought content with left copper accent bar.
3. **Full-Featured Code Block (`MarkdownContent`)**:
   - Header with language tag, line count (`X dòng`), and 1-tap Copy button with visual confirmation.
   - Body with **line numbers column** on the left, vertical separator, and horizontally scrollable monospace code.
   - Markdown support for headings (`#`, `##`, `###`), bullet lists, bold, and inline code.
4. **Typography & Standard Controls**:
   - High-legibility typography (`15.5.sp` body, `24.sp` line height, `0.15.sp` letter spacing).
   - Standard Material 3 touch targets, New Chat action in TopAppBar, and floating Input Dock.

## Files to Modify / Create
- `app/src/commonMain/kotlin/core/domain/model/AgentType.kt`: Accurate default models per engine.
- `app/src/commonMain/kotlin/features/chat/presentation/ChatViewModel.kt`: Populate `availableModels` and `selectedModel` per engine with live catalog override.
- `app/src/commonMain/kotlin/app/App.kt`: Full-width drawer `Surface` on compact screens (`< 600.dp`).
- `app/src/commonMain/kotlin/features/chat/presentation/components/MarkdownContent.kt`: Full-featured Markdown & Code Card with line numbers, line count, and copy button.
- `app/src/commonMain/kotlin/features/chat/presentation/components/ExecutionTraceView.kt`: Collapsed-by-default Thinking bar + Tool execution trace cards.
- `app/src/commonMain/kotlin/features/chat/presentation/components/ChatMessageBubble.kt`: Signature User bubble + borderless Assistant flow.
- `app/src/commonMain/kotlin/features/chat/presentation/components/ChatInputBar.kt`: Floating dock with Engine Model selector, attachment button, and Send/Stop button.
- `app/src/commonMain/kotlin/features/chat/presentation/ChatScreen.kt`: Refined TopBar (with New Chat button + status badge) and signature Empty State with quick-start prompt pills.
