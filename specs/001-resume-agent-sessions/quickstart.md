# Quickstart: Validate Terminal Agent Session Restore

## Prerequisites

- IntelliJ IDEA with plugin build installed.
- Restore mode set to auto (default).
- Local agent tools available for Codex, Claude, and OpenCode.

## Scenario A: Happy Path (Mixed Agents)

1. Open one project workspace.
2. Open five terminal tabs.
3. Start active sessions: 2x Codex, 2x Claude, 1x OpenCode.
4. Close IDE normally.
5. Reopen IDE and project.
6. Verify:
   - All five terminal tabs reappear.
   - Each terminal resumes the same agent type and session reference.
   - Restore summary reports success for resumed terminals.

Expected outcome:
- >=95% successful resume across repeated runs.
- Working context available within 60 seconds from startup completion.

## Scenario B: Partial Failure + Retry

1. Prepare same mixed setup as Scenario A.
2. Invalidate one saved session reference before restart.
3. Restart IDE.
4. Verify:
   - Other terminals continue restoring successfully.
   - Failed terminal shows clear reason.
   - One automatic retry occurs for failed terminal.
5. Trigger manual retry for the failed terminal.

Expected outcome:
- Failed terminal reaches final status after one auto retry.
- Manual retry action is available without rerunning successful terminals.

## Scenario C: Manual Restore Mode

1. Enable manual restore mode in settings.
2. Restart IDE with previously saved active sessions.
3. Verify no automatic restore starts.
4. Trigger manual restore command.
5. Verify project-scoped candidates are restored.

Expected outcome:
- Startup auto-restore is skipped in manual mode.
- Manual restore performs scoped recovery correctly.

## Scenario D: Privacy and Contract Compliance

1. Run at least one restore cycle.
2. Inspect persisted snapshot data artifact.
3. Verify snapshot contains only minimal metadata.
4. Verify prohibited data is absent.

Expected outcome:
- No auth tokens, command history, or terminal output persisted.

## Scenario E: Timeout Behavior

1. Simulate stalled agent resume command for one terminal.
2. Start restore.
3. Verify timeout status is returned within 30 seconds per attempt.
4. Verify one retry is attempted and then terminal is marked failed.

Expected outcome:
- 100% of attempts end with success or timeout/failed status within bounded time.

## Implementation Validation Log (2026-03-27)

Automated validation executed in this repository:
- Command: `./gradlew test`
- Result: `BUILD SUCCESSFUL`
- Coverage added:
  - Resolver normalization and invalid ID rejection.
  - `/proc/<pid>/environ` parser with missing-file behavior.
  - Copilot workspace state mapping by cwd and timestamp.
  - Ambiguity classifier and chooser selected/canceled outcomes.
  - Exact restore + fallback command mix across providers.
  - Ambiguity selection and ambiguity-cancel fallback integration paths.
  - v1/v2 -> v3 snapshot migration compatibility assertions.

Manual IDE validation status:
- Real IntelliJ runtime checks for persisted `.idea/agent-session-saver/<scope>-snapshots.json` content and startup chooser UX are still pending in a live IDE session.
