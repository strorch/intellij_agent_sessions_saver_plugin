# Contract: Session Restore Behavior

## 1. Persisted Snapshot Contract

Snapshot format is versioned and project-scoped.

Required fields per terminal snapshot:
- `schemaVersion` (integer)
- `projectScopeId` (string)
- `terminalTabId` (string)
- `agentType` (`codex` | `claude` | `opencode` | `unknown`)
- `sessionReference` (string, required only when active session exists)
- `hadActiveAgentSession` (boolean)
- `capturedAt` (datetime)

Prohibited fields:
- Authentication tokens
- Full command history
- Terminal output content

Compatibility rules:
- Readers MUST accept older supported `schemaVersion` values.
- Writers MUST emit the current `schemaVersion`.
- Unknown future fields MUST be ignored by older readers when safe.

## 2. Agent Adapter Contract

Each supported agent provider implements a canonical adapter behavior.

Adapter input contract:
- `projectScopeId`
- `terminalTabId`
- `agentType`
- `sessionReference`
- `attemptTimeoutSeconds` (30)

Adapter output contract:
- `status` (`success` | `timeout` | `failed` | `unsupported`)
- `failureCode` (optional)
- `failureMessage` (optional)
- `durationMs`

Behavior rules:
- Adapter MUST not block UI thread.
- Adapter MUST stop and return `timeout` when 30-second limit is exceeded.
- Adapter MUST produce deterministic status for unsupported agent/provider conditions.

## 3. Restore Orchestrator Contract

Startup behavior contract:
- Trigger startup restore when auto mode is enabled.
- Process only snapshots where `projectScopeId` matches active project.
- Attempt restore for active-session terminals only.
- On failure, perform exactly one retry per terminal.
- Continue processing remaining terminals regardless of individual failure.

Manual restore behavior contract:
- Manual trigger MUST process only failed/skipped candidates in current project scope unless
  explicitly configured otherwise in a future feature.

## 4. User Feedback Contract

User-visible restore summary MUST include per-terminal:
- terminal identity
- agent type
- final status
- failure reason when not successful
- availability of manual retry action for failed terminals

Logging contract:
- Emit structured events for run start/end, attempt start/end, timeout, retry, and final summary.
- Log payloads MUST omit sensitive session content and tokens.

Implementation note:
- Current implementation emits `restore.start`, `restore.terminal`, `restore.summary`,
  and `restore.end` events through `RestoreTelemetryLogger`.
