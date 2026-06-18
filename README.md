# Agent Sessions Saver (IntelliJ Plugin)

Persist and restore terminal AI agent sessions across IDE restarts.

This plugin captures minimal session metadata per terminal tab and restores the same tab-to-session mapping on startup.

## Why This Exists

If you run multiple terminal agents in parallel (for example 2x Codex, 1x Claude, 1x OpenCode, 1x Copilot), IDE restart usually breaks flow.

Agent Sessions Saver restores context fast by:

- remembering which terminal had which agent session
- restoring exact session ID when available
- falling back to provider `continue/last` mode when exact ID is unavailable

## What It Supports

- IntelliJ IDEA 2024.3+ (`since-build=243`, open-ended upper bound)
- Terminal tool window integration
- Providers:
  - Codex
  - Claude
  - OpenCode
  - Copilot

## Restore Strategy (Exact-First)

On startup, each saved terminal snapshot is restored using this order:

1. Use exact `sessionReference` if valid.
2. If no exact ID and there are multiple candidates, show chooser.
3. If chooser is canceled (or exact is unavailable), use provider fallback command.

Provider commands:

| Provider | Exact restore command | Fallback command |
|---|---|---|
| Codex | `codex resume <id>` | `codex resume --last` |
| Claude | `claude --resume <id>` | `claude --continue` |
| OpenCode | `opencode --session <id>` | `opencode --continue` |
| Copilot | `copilot --resume=<id>` | `copilot --continue` |

## Snapshot Storage

Snapshots are saved per project scope in:

```text
.idea/agent-session-saver/<projectScopeId>-snapshots.json
```

Example snapshot entry:

```json
{
  "terminalTabId": "terminal-2-37c8be57",
  "terminalDisplayName": "Tab 2",
  "agentType": "codex",
  "sessionReference": "d20de65d-40ef-4145-9f5f-85a8d5faad5c",
  "sessionReferenceSource": "PROVIDER_STATE",
  "sessionReferenceConfidence": "HIGH",
  "sessionCandidates": [
    "d20de65d-40ef-4145-9f5f-85a8d5faad5c",
    "af0af8a7-1c2e-42f6-8b9c-9e9244a2b9dc"
  ],
  "hadActiveAgentSession": true
}
```

## Examples

### Example 1: Mixed Agents, Exact Restore

Saved tabs:

- `Tab 1`: Codex with `sessionReference=codex-123`
- `Tab 2`: Claude with `sessionReference=claude-555`
- `Tab 3`: OpenCode with `sessionReference=oc-42`

Startup dispatch:

```bash
codex resume codex-123
claude --resume claude-555
opencode --session oc-42
```

### Example 2: Ambiguous Candidates, But Exact Exists

Saved snapshot:

- `sessionReference=uuid-a`
- `sessionCandidates=[uuid-a, uuid-b, uuid-c]`

Behavior:

- chooser is skipped
- `uuid-a` is used directly

### Example 3: Ambiguous Candidates, No Exact

Saved snapshot:

- `sessionReference=null`
- `sessionCandidates=[id-1, id-2]`

Behavior:

- chooser is shown
- selected ID is used for restore
- cancel triggers provider fallback command

## IntelliJ Actions

From `Tools -> Agent Sessions Saver`:

- `Capture Agent Sessions Now`
- `Restore Agent Sessions`
- `Retry Failed Agent Session(s)`

Automatic capture is also executed on project close.

## Build and Run

### Run tests

```bash
./gradlew test
```

### Launch sandbox IDE

```bash
./gradlew runIde
```

### Build installable plugin ZIP

```bash
./gradlew buildPlugin
```

Artifact:

```text
build/distributions/
```

## Troubleshooting

### Gradle reports missing `JAVA_COMPILER`

Use a full JDK (not JRE) for Gradle. In this repo:

```properties
org.gradle.java.home=/usr/lib/jvm/java-17-openjdk-amd64
```

### Session ID exists but chooser still appears

Make sure snapshot has valid:

- `sessionReference`
- `sessionReferenceSource` (`COMMAND_LINE`, `PROVIDER_STATE`, or `CHOOSER`)

If snapshots are old, capture again with `Capture Agent Sessions Now`.

### Resume returns "session not found"

The provider-side session may be expired/removed. Capture a fresh state and retry restore.

## Privacy

Persisted data is intentionally minimal.

The plugin does **not** persist:

- auth tokens
- full command history
- terminal output

## Project Layout

```text
src/
tests/
specs/001-resume-agent-sessions/
```
