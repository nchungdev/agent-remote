# Active Plan: Engine Catalog + Conversation Sessions

## Goal
1. Server loads, per engine, everything the engine's own app shows: projects (name + folders + order),
   sections/pins, conversations (title, project, workspace, pinned, archived, live status).
2. User picks an existing conversation (resume) or creates a new one; server routes prompts to a
   CLI process bound to `(engine, conversation_id)`.

## Verified storage (2026-11-01, this Mac)
| Engine | Projects | Conversations | Pins / sections | Titles |
|---|---|---|---|---|
| Antigravity | `~/.gemini/config/projects/*.json` (+ order in `~/Library/Application Support/Antigravity/app_storage.json` `projectsOrder`) | `~/.gemini/antigravity/conversation_summaries.db` (superset: app + CLI rows; skip `parent_conversation_id != ''`) | none found on disk | `~/.gemini/antigravity/annotations/<id>.pbtxt` `title:"..."` overrides DB |
| Codex | `~/.codex/state_5.sqlite` `projects` + `project_roots` | same db `threads` (skip `source` starting `{` = subagents) | `thread_sections` (section named `Pinned`) + `threads.is_pinned` | `threads.name` > `threads.title` |
| Claude Code | `~/.claude.json` `projects` keys (real paths) | `~/.claude/projects/<slug>/<id>.jsonl` | none | last `custom-title` line > first user text |

## agy stream-json (verified spike)
- `agy --input-format stream-json --output-format stream-json [--conversation ID]` keeps running; one stdin line per turn:
  `{"event":"user","message":{"role":"user","content":"..."}}`
- Output: `init{conversation_id}`, `step_update{step_index,state,step_type,text_delta,tool_name,tool_info}`, `result{status,response,error}` (per turn).
- Writes to `~/.gemini/antigravity-cli/brain/<id>/` and also registers in the app DB.

## Steps
- [ ] `internal/catalog`: model, antigravity, codex, claude, service + tests
- [ ] protocol: `LIST_CATALOG` → `CATALOG`; `OPEN_CONVERSATION` → `CONVERSATION_HISTORY`; `NEW_CONVERSATION`; `SEND_PROMPT{conversationId}`
- [ ] `internal/router`: shared action handling for WS gateway + WebRTC hub (removes duplication)
- [ ] `internal/runtime`: registry keyed by (engine, id); agy long-lived stream-json; claude/codex per-turn resume
- [ ] app: catalog drawer (projects → conversations, pinned first), open/new conversation
- [ ] verify: go test, build, device
