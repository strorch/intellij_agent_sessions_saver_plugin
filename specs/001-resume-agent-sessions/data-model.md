# Data Model: Restore Agent Sessions in Terminals

## Entity: TerminalSessionSnapshot

Purpose: Canonical persisted record for one terminal tab at capture time.

Fields:
- `snapshotId` (string, UUID): unique identifier for snapshot row.
- `schemaVersion` (integer): versioned contract for migration safety.
- `projectScopeId` (string): deterministic project identity key.
- `terminalTabId` (string): IDE terminal tab identity.
- `terminalDisplayName` (string): user-facing tab title at capture time.
- `agentType` (enum: `codex`, `claude`, `opencode`, `unknown`): mapped provider identity.
- `sessionReference` (string): minimal provider-specific resume reference.
- `hadActiveAgentSession` (boolean): whether session restore should run for this tab.
- `capturedAt` (datetime): snapshot capture timestamp.
- `lastKnownStatus` (enum: `ready`, `failed`, `skipped`): previous restore outcome.

Validation rules:
- Unique key: (`projectScopeId`, `terminalTabId`).
- `sessionReference` is required when `hadActiveAgentSession=true`.
- Must not contain tokens, terminal output, or command history fields.

## Entity: RestorePolicy

Purpose: Global/project behavior controls for restore execution.

Fields:
- `autoRestoreEnabled` (boolean, default `true`): enables startup restore flow.
- `manualRestoreMode` (boolean, default `false`): disables automatic startup restore.
- `autoRetryCount` (integer, fixed `1`): startup retries per failed terminal.
- `attemptTimeoutSeconds` (integer, fixed `30`): timeout per resume attempt.
- `scopeBoundary` (enum: `project-only`): restore scope guard.

Validation rules:
- `autoRetryCount` must equal `1` for this feature scope.
- `attemptTimeoutSeconds` must equal `30` for this feature scope.
- `manualRestoreMode=true` implies no startup auto-restore tasks are queued.

## Entity: RestoreRun

Purpose: One execution of the restore coordinator.

Fields:
- `restoreRunId` (string, UUID): unique run identifier.
- `projectScopeId` (string): project identity.
- `triggerType` (enum: `startup`, `manual`): run origin.
- `startedAt` (datetime): run start timestamp.
- `finishedAt` (datetime): run end timestamp.
- `totalCandidates` (integer): terminals evaluated.
- `totalResumed` (integer): successful restores.
- `totalFailed` (integer): failures after retry policy.
- `totalSkipped` (integer): terminals intentionally skipped.

Validation rules:
- `finishedAt >= startedAt`.
- `totalCandidates = totalResumed + totalFailed + totalSkipped`.

## Entity: RestoreAttemptResult

Purpose: Per-terminal attempt outcomes used for logs and user feedback.

Fields:
- `attemptId` (string, UUID): unique attempt identifier.
- `restoreRunId` (string, UUID): parent run reference.
- `terminalTabId` (string): target terminal.
- `attemptIndex` (integer: `1` or `2`): first try or one retry.
- `startedAt` (datetime): attempt start timestamp.
- `endedAt` (datetime): attempt end timestamp.
- `status` (enum: `success`, `timeout`, `failed`, `skipped`, `unsupported`).
- `failureCode` (string, optional): normalized reason code.
- `failureMessage` (string, optional): concise user-safe message.

Validation rules:
- `attemptIndex` max value is `2`.
- `status=timeout` when duration exceeds 30s bound.
- `failureMessage` required when `status in {failed, timeout, unsupported}`.

## Entity Relationships

- `RestoreRun` 1 -> N `RestoreAttemptResult`.
- `TerminalSessionSnapshot` is matched to runtime terminal tabs by (`projectScopeId`, `terminalTabId`).
- `RestorePolicy` governs `RestoreRun` scheduling and attempt limits.

## State Transitions

### TerminalSessionSnapshot
- `captured` -> `ready` when metadata validates.
- `ready` -> `failed` when restore run ends in terminal failure.
- `ready` -> `skipped` when no active agent session or manual mode prevents startup restore.

### RestoreAttemptResult
- `queued` -> `running` -> (`success` | `timeout` | `failed` | `unsupported`).
- First terminal failure (`failed` or `timeout`) may transition to retry queue when `attemptIndex=1`.
- Retry completion transitions terminal to final status with no further retries.
