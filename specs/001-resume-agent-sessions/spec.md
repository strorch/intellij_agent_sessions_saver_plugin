# Feature Specification: Restore Agent Sessions in Terminals

**Feature Branch**: `001-resume-agent-sessions`  
**Created**: 2026-03-26  
**Status**: Draft  
**Input**: User description: "i want to create a jetbrains ide plugin which will remember all opened agent session in terminal for example i have 5 opened terminals 2 codex 1 claude, 1 opencode and 1 claude with their own sessions then iam restarting ide after restart it should reopen all terminals(its already existing in ide) but also in each of terminals reopen agents and resume their sessions"

## Clarifications

### Session 2026-03-26

- Q: What session metadata retention policy should be enforced for restore? → A: Persist only minimal resume metadata and never persist auth tokens, full command history, or terminal output.
- Q: What is the restore scope boundary? → A: Restore only terminals and agent sessions belonging to the same project workspace.
- Q: What should automatic retry behavior be for failed startup resume? → A: One automatic retry per failed terminal, then mark as failed.
- Q: Which restore trigger mode should be supported? → A: Auto-restore by default, with a user setting to switch to manual restore only.
- Q: What should be the per-terminal resume timeout? → A: 30 seconds per resume attempt.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Auto-Resume Existing Agent Sessions (Priority: P1)

As an IDE user, I want all previously active agent terminals to resume their prior sessions after
IDE restart so I can continue work without manually reconnecting each terminal.

**Why this priority**: This is the core value of the feature and removes the biggest workflow
interruption after restart.

**Independent Test**: Start with multiple terminals running different agent sessions, restart the IDE,
and verify each terminal returns to the same agent session context as before restart.

**Acceptance Scenarios**:

1. **Given** five terminals are open with active agent sessions before IDE shutdown, **When** the IDE
   restarts, **Then** each terminal is reopened and each associated agent session is resumed.
2. **Given** a terminal has no active agent session before shutdown, **When** the IDE restarts,
   **Then** the terminal is reopened without starting an agent session automatically.

---

### User Story 2 - Recover Gracefully from Resume Failures (Priority: P2)

As an IDE user, I want clear recovery behavior when some sessions cannot be resumed so I can quickly
fix only failed terminals and keep working in successful ones.

**Why this priority**: Multi-terminal recovery is unreliable unless partial failures are isolated and
visible.

**Independent Test**: Make one session intentionally non-resumable, restart the IDE, and verify the
plugin resumes what it can and shows actionable failure information for the rest.

**Acceptance Scenarios**:

1. **Given** one saved agent session is no longer resumable, **When** restart recovery runs,
   **Then** resumable sessions continue restoring and the failed terminal is marked with a clear
   failure reason.
2. **Given** a failed resume attempt, **When** the user chooses retry for that terminal,
   **Then** the plugin retries only that terminal without restarting successful ones.

---

### User Story 3 - Preserve Terminal-to-Agent Mapping Across Mixed Agents (Priority: P3)

As an IDE user, I want each terminal to restore the correct agent type and session identity so mixed
terminal layouts remain consistent across restarts.

**Why this priority**: Users often run multiple agent tools simultaneously, and wrong mapping creates
confusion and accidental context switching.

**Independent Test**: Run mixed terminals (for example Codex, Claude, OpenCode), restart IDE, and
verify each terminal restores to its original agent type and corresponding session.

**Acceptance Scenarios**:

1. **Given** terminals contain sessions from multiple agent products, **When** recovery executes,
   **Then** each terminal restores the same agent type it had before restart.
2. **Given** multiple terminals use the same agent product with different sessions, **When** recovery
   executes, **Then** each terminal resumes its own prior session and does not swap contexts.

### Edge Cases

- IDE restarts after an unclean shutdown while saved terminal-session metadata is incomplete.
- A previously available agent command is no longer available at restore time.
- A saved session identifier is expired or invalid at restore time.
- Terminal count changed between save and restore due to user/project changes.
- User closes a terminal while restore is in progress.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUST capture a restorable snapshot of each terminal that has an active agent
  session before IDE shutdown.
- **FR-002**: System MUST store enough session metadata to restore terminal-to-agent-session mapping
  after IDE restart, while limiting persisted data to minimal resume metadata only.
- **FR-003**: System MUST automatically attempt recovery on IDE startup for terminals that had active
  agent sessions in the last saved state.
- **FR-004**: System MUST restore each terminal with the same agent product identity recorded before
  shutdown.
- **FR-005**: System MUST attempt session resumption per terminal using the saved session identity and
  track whether each attempt succeeds or fails.
- **FR-006**: System MUST continue restoring remaining terminals when one terminal fails to resume.
- **FR-007**: System MUST provide a user-visible summary of restore results, including per-terminal
  status and failure reasons.
- **FR-008**: Users MUST be able to retry resume for an individual failed terminal without re-running
  successful terminals.
- **FR-008a**: System MUST perform one automatic retry for a failed terminal during startup restore
  before marking the terminal as failed.
- **FR-009**: System MUST avoid auto-starting agent sessions in terminals that had no active agent
  session at last save.
- **FR-010**: System MUST preserve saved session data across IDE restart and plugin upgrade without
  data loss.
- **FR-011**: System MUST enable auto-restore by default and allow users to switch to manual restore
  mode globally.
- **FR-012**: System MUST log restore lifecycle events for troubleshooting.
- **FR-013**: System MUST NOT persist authentication tokens, full command history, or terminal output
  as part of session restore metadata.
- **FR-014**: System MUST restore terminal-agent sessions only for the same project workspace where
  the snapshot was captured.
- **FR-015**: System MUST enforce a 30-second timeout for each terminal resume attempt, including
  automatic retry attempts.

### Constitution Alignment *(mandatory)*

- **CA-001 (Independent Value)**: P1 delivers full end-to-end auto-resume value independently; P2 and
  P3 add resilience and mixed-agent fidelity without blocking P1.
- **CA-002 (Plugin Contract)**: The feature defines explicit user-visible behavior for auto-restore,
  retry actions, status reporting, and opt-out controls.
- **CA-003 (Persistence Safety)**: Saved terminal-session metadata remains recoverable after restart
  and upgrade, with failure isolation to avoid full-state corruption.
- **CA-004 (Threading & Performance)**: Restore behavior must not block normal IDE usability during
  startup and must provide progress transparency for users.
- **CA-005 (Verification Evidence)**: Acceptance requires repeatable multi-terminal restart validation,
  partial-failure validation, and mixed-agent mapping validation.

### Key Entities *(include if feature involves data)*

- **Terminal Session Snapshot**: Saved record of a terminal tab that includes terminal identity,
  whether an agent session was active, and recoverable minimal session metadata only.
- **Agent Session Reference**: Logical reference containing agent product identity and session identity
  needed to resume that session.
- **Restore Attempt Result**: Per-terminal outcome record including status, timestamp, and failure
  reason when restore is unsuccessful.
- **Restore Policy**: User-configurable behavior for default auto-restore, manual restore mode, and
  retry handling.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: In validation runs with at least five mixed-agent terminals, at least 95% of previously
  active sessions resume successfully after IDE restart.
- **SC-002**: In successful restore cases, users regain working session context in under 60 seconds
  from IDE startup completion.
- **SC-003**: In scenarios with partial failures, 100% of failed terminals provide clear reason and
  retry option without affecting already restored terminals.
- **SC-004**: In user acceptance testing, at least 90% of users complete post-restart workflow
  continuation without manual re-creation of terminal sessions.
- **SC-005**: In restart validation runs, 100% of terminal resume attempts either succeed or return a
  timeout failure status within 30 seconds per attempt.

## Assumptions

- IDE terminal tabs are restored by the IDE itself; this feature focuses on restoring agent session
  context inside those terminals.
- Users have valid local access to the same agent tools they used before restart.
- Session restoration is scoped to the same user environment and machine profile.
- This feature is project-scoped and excludes cross-project, cross-IDE-instance, and remote team
  session sharing.
