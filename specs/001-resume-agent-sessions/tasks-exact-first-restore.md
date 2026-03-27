# Tasks: Exact-First Session Restore (With Fallback + Ambiguity Prompt)

**Input**: Existing implementation in `src/main/kotlin/plugin/` and tests in `tests/`  
**Goal**: Restore the current session per terminal tab when exact mapping is available; fallback to provider continue/last when exact mapping is unavailable.  
**Decisions Locked**:
- Plugin-only capture (no shell hooks)
- Ambiguous mapping => ask on startup
- Chooser cancel => fallback continue/last for that tab

## Format
- `[ID] [P?] Description`
- `[P]` means task can run in parallel
- Each task includes concrete target files

---

## Phase 1: Restore Flow Stabilization

- [X] R001 Fix restore candidate filtering so snapshots are not blocked by duplicated `hadActiveAgentSession` checks.  
  Files: `src/main/kotlin/plugin/runtime/RestoreRuntimeFactory.kt`, `src/main/kotlin/plugin/restore/RestoreCoordinator.kt`
- [X] R002 Align startup success notification with actual dispatched restore attempts (no false-positive "restored N").  
  Files: `src/main/kotlin/plugin/restore/StartupRestorePostStartupActivity.kt`, `src/main/kotlin/plugin/runtime/RestoreRuntimeFactory.kt`
- [X] R003 Add structured restore summary counters: `exactRestored`, `chooserResolved`, `fallbackRestored`, `skipped`.  
  Files: `src/main/kotlin/plugin/runtime/RestoreRuntimeFactory.kt`, `src/main/kotlin/plugin/restore/ui/RestoreSummaryNotifier.kt`

---

## Phase 2: Exact Session Resolution Core

- [X] R004 Create resolver contract and models (`ResolvedSessionReference`, confidence/source enum, candidate list).  
  Files: `src/main/kotlin/plugin/capture/resolution/SessionReferenceResolver.kt`, `src/main/kotlin/plugin/capture/resolution/ResolutionModels.kt`
- [X] R005 Add process metadata extraction (PID + command) from terminal connector in capture path.  
  Files: `src/main/kotlin/plugin/capture/TerminalSnapshotCapture.kt`
- [X] R006 Implement resolver chain orchestration with ordered sources (command -> env -> terminal text -> provider state).  
  Files: `src/main/kotlin/plugin/capture/resolution/ResolverChain.kt`
- [X] R007 Integrate resolver chain into snapshot capture; persist resolved ID and ambiguity candidates.  
  Files: `src/main/kotlin/plugin/capture/TerminalSnapshotCapture.kt`, `src/main/kotlin/plugin/persistence/SessionSnapshotCaptureService.kt`

---

## Phase 3: Source Resolvers

- [X] R008 Implement command-line resolver (explicit resume/session flags per provider).  
  Files: `src/main/kotlin/plugin/capture/resolution/sources/CommandLineResolver.kt`
- [X] R009 Implement process-environment resolver using `/proc/<pid>/environ` for session/thread-like vars.  
  Files: `src/main/kotlin/plugin/capture/resolution/sources/ProcessEnvResolver.kt`
- [X] R010 Implement terminal-text resolver for exit hints like `codex resume <id>` and similar patterns.  
  Files: `src/main/kotlin/plugin/capture/resolution/sources/TerminalTextResolver.kt`
- [X] R011 Implement Copilot local-state resolver using `~/.copilot/session-state/*/workspace.yaml` (+ timestamps).  
  Files: `src/main/kotlin/plugin/capture/resolution/sources/CopilotStateResolver.kt`
- [X] R012 [P] Add provider resolver registry with per-agent source priority configuration.  
  Files: `src/main/kotlin/plugin/capture/resolution/ResolverRegistry.kt`

---

## Phase 4: Snapshot Schema v3

- [X] R013 Extend `TerminalSessionSnapshot` with v3 fields:
  - `sessionReferenceSource`
  - `sessionReferenceConfidence`
  - `sessionCandidates`
  - optional process metadata for diagnostics  
  Files: `src/main/kotlin/plugin/persistence/model/TerminalSessionSnapshot.kt`
- [X] R014 Bump serializer/schema handling to v3 and ensure compatibility reads for v1/v2 fixtures.  
  Files: `src/main/kotlin/plugin/persistence/SnapshotSerializer.kt`, `src/main/kotlin/plugin/persistence/SnapshotMigrationService.kt`
- [X] R015 Update snapshot validity rules for v3 (exact ID optional only when fallback path is allowed).  
  Files: `src/main/kotlin/plugin/persistence/model/TerminalSessionSnapshot.kt`

---

## Phase 5: Ambiguity Prompt + Fallback Behavior

- [X] R016 Build startup ambiguity classifier (`0`, `1`, `many` exact candidates by tab).  
  Files: `src/main/kotlin/plugin/runtime/RestoreRuntimeFactory.kt`
- [X] R017 Implement startup chooser UI for ambiguous tabs (select exact ID or cancel).  
  Files: `src/main/kotlin/plugin/restore/ui/StartupSessionChooser.kt`, `src/main/kotlin/plugin/runtime/RestoreRuntimeFactory.kt`
- [X] R018 Wire chooser result into restore dispatch (selected exact ID path).  
  Files: `src/main/kotlin/plugin/runtime/RestoreRuntimeFactory.kt`
- [X] R019 Implement cancel behavior => provider fallback command for that tab (`continue/last`).  
  Files: `src/main/kotlin/plugin/runtime/RestoreRuntimeFactory.kt`, `src/main/kotlin/plugin/adapters/providers/*.kt`
- [X] R020 Ensure provider adapters use explicit ID when present; fallback command when empty.  
  Files: `src/main/kotlin/plugin/adapters/providers/CodexAgentAdapter.kt`, `src/main/kotlin/plugin/adapters/providers/ClaudeAgentAdapter.kt`, `src/main/kotlin/plugin/adapters/providers/OpenCodeAgentAdapter.kt`, `src/main/kotlin/plugin/adapters/providers/CopilotAgentAdapter.kt`

---

## Phase 6: Tests

- [X] R021 Add unit tests for resolver normalization and invalid-ID rejection (version-like tokens, synthetic IDs).  
  Files: `tests/unit/capture/AgentSessionDetectorTest.kt`, `tests/unit/capture/SessionReferenceResolverTest.kt`
- [X] R022 Add unit tests for process-env parsing and missing `/proc` behavior.  
  Files: `tests/unit/capture/ProcessEnvResolverTest.kt`
- [X] R023 Add unit tests for Copilot state resolver mapping by cwd/timestamp.  
  Files: `tests/unit/capture/CopilotStateResolverTest.kt`
- [X] R024 Add unit tests for ambiguity classifier and chooser outcomes (selected/canceled).  
  Files: `tests/unit/restore/StartupAmbiguityResolutionTest.kt`
- [X] R025 Update adapter command tests to current expected fallback commands.  
  Files: `tests/unit/adapters/AgentAdapterCommandTest.kt`
- [X] R026 Add integration test: startup restore with exact IDs + fallback mix.  
  Files: `tests/integration/restore/StartupRestoreHappyPathTest.kt`, `tests/integration/restore/MixedAgentMappingTest.kt`
- [X] R027 Add integration test: ambiguous tab => chooser selection path.  
  Files: `tests/integration/restore/StartupRestoreAmbiguityChooserTest.kt`
- [X] R028 Add integration test: ambiguous tab => chooser cancel => fallback path.  
  Files: `tests/integration/restore/StartupRestoreAmbiguityFallbackTest.kt`
- [X] R029 Update migration compatibility tests for v3 fields.  
  Files: `tests/unit/persistence/SnapshotMigrationCompatibilityTest.kt`

---

## Phase 7: Manual Validation + Handoff

- [ ] R030 Validate capture output in a real project (`.idea/agent-session-saver/<scope>-snapshots.json`) includes resolver metadata and candidates.  
  Evidence: append results to `specs/001-resume-agent-sessions/quickstart.md`
- [ ] R031 Validate startup behavior with 4 tabs (Codex/OpenCode/Codex/Copilot): exact restore where possible, chooser for ambiguous, fallback on cancel.  
  Evidence: append results to `specs/001-resume-agent-sessions/quickstart.md`
- [ ] R032 Validate no IDE startup crash regressions and stable notifications.  
  Evidence: append results to `specs/001-resume-agent-sessions/quickstart.md`
- [X] R033 Update implementation notes in `specs/001-resume-agent-sessions/plan.md` with final resolver order and fallback policy.

---

## Execution Order

1. Phase 1 -> Phase 2 -> Phase 3 -> Phase 4 -> Phase 5  
2. Then run Phase 6 tests  
3. Finish with Phase 7 manual validation and docs

## Parallelizable Work

- Phase 3: `R011` and `R012` can run in parallel after `R006`
- Phase 6: `R022`, `R023`, `R024`, `R025` can run in parallel
- Phase 7: `R030` and `R032` can run in parallel
