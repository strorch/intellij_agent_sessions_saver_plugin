# Tasks: Restore Agent Sessions in Terminals

**Input**: Design documents from `/specs/001-resume-agent-sessions/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Automated tests are included because the specification and constitution require verifiable restore behavior, compatibility safety, and bounded failure handling.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Every task includes an exact file path

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Initialize plugin module, package layout, and baseline compatibility configuration

- [X] T001 Create IntelliJ plugin Gradle baseline in `build.gradle.kts`, `settings.gradle.kts`, and `gradle.properties`
- [X] T002 Create plugin source/test directory structure in `src/main/kotlin/plugin/`, `tests/unit/`, `tests/integration/`, and `tests/fixtures/`
- [X] T003 [P] Declare plugin extensions and action placeholders in `src/main/resources/META-INF/plugin.xml`
- [X] T004 [P] Add Kotlin/JVM and test runtime configuration in `build.gradle.kts`
- [X] T005 Define IntelliJ compatibility range (`since-build`/`until-build`) in `build.gradle.kts` and `src/main/resources/META-INF/plugin.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core restore architecture required before user story delivery

**⚠️ CRITICAL**: No user story work begins until this phase is complete

- [X] T006 Implement snapshot domain entities from data model in `src/main/kotlin/plugin/persistence/model/TerminalSessionSnapshot.kt`, `src/main/kotlin/plugin/persistence/model/RestorePolicy.kt`, `src/main/kotlin/plugin/persistence/model/RestoreRun.kt`, and `src/main/kotlin/plugin/persistence/model/RestoreAttemptResult.kt`
- [X] T007 [P] Implement versioned snapshot serialization and migration reader in `src/main/kotlin/plugin/persistence/SnapshotSerializer.kt` and `SnapshotMigrationService.kt`
- [X] T008 [P] Implement persistent settings store for restore policy in `src/main/kotlin/plugin/settings/RestorePolicySettingsService.kt`
- [X] T009 [P] Implement restore telemetry event contract in `src/main/kotlin/plugin/telemetry/RestoreTelemetryLogger.kt` and `RestoreTelemetryEvent.kt`
- [X] T010 Implement background restore coordinator skeleton (non-EDT) in `src/main/kotlin/plugin/restore/RestoreCoordinator.kt`
- [X] T011 Implement agent adapter interface and normalized status types in `src/main/kotlin/plugin/adapters/AgentAdapter.kt` and `src/main/kotlin/plugin/adapters/AgentResumeStatus.kt`
- [X] T012 Implement project-scope candidate filter utility in `src/main/kotlin/plugin/restore/ProjectScopeGuard.kt`
- [X] T013 [P] Add persistence fixtures for schema/version compatibility in `tests/fixtures/snapshots/v1-minimal.json` and `tests/fixtures/snapshots/v2-current.json`
- [X] T014 Add shared restore test fixture builders in `tests/unit/restore/RestoreFixtureFactory.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Auto-Resume Existing Agent Sessions (Priority: P1) 🎯 MVP

**Goal**: Automatically restore previously active agent sessions per terminal after IDE restart

**Independent Test**: With five mixed-agent terminals active before restart, IDE startup restores the same terminal-to-session mapping for active sessions and leaves non-agent terminals untouched.

### Tests for User Story 1

- [X] T015 [P] [US1] Add unit tests for active-session snapshot capture and non-agent filtering in `tests/unit/persistence/TerminalSessionSnapshotStoreTest.kt`
- [X] T016 [P] [US1] Add integration test for startup auto-restore happy path in `tests/integration/restore/StartupRestoreHappyPathTest.kt`

### Implementation for User Story 1

- [X] T017 [US1] Implement terminal snapshot capture on project shutdown in `src/main/kotlin/plugin/persistence/SessionSnapshotCaptureService.kt`
- [X] T018 [P] [US1] Implement minimal metadata persistence store in `src/main/kotlin/plugin/persistence/TerminalSessionSnapshotStore.kt`
- [X] T019 [P] [US1] Implement terminal tab mapping and identity binding in `src/main/kotlin/plugin/adapters/TerminalTabMapper.kt`
- [X] T020 [US1] Implement startup restore trigger in `src/main/kotlin/plugin/restore/StartupRestoreActivity.kt`
- [X] T021 [US1] Implement core startup restore flow for successful sessions in `src/main/kotlin/plugin/restore/RestoreCoordinator.kt`
- [X] T022 [US1] Implement success summary notification in `src/main/kotlin/plugin/restore/ui/RestoreSummaryNotifier.kt`
- [ ] T023 [US1] Execute manual validation for Quickstart Scenario A and record evidence in `specs/001-resume-agent-sessions/quickstart.md`

**Checkpoint**: User Story 1 is fully functional and independently testable

---

## Phase 4: User Story 2 - Recover Gracefully from Resume Failures (Priority: P2)

**Goal**: Isolate failed terminals, apply one automatic retry, and provide targeted manual retry

**Independent Test**: With one invalid session reference, restore still completes for other terminals, failed terminal shows reason, exactly one auto retry occurs, and manual retry reruns only that terminal.

### Tests for User Story 2

- [X] T024 [P] [US2] Add unit tests for retry and 30-second timeout policy in `tests/unit/restore/RetryAndTimeoutPolicyTest.kt`
- [X] T025 [P] [US2] Add integration test for partial failure isolation and retry in `tests/integration/restore/PartialFailureRetryTest.kt`

### Implementation for User Story 2

- [X] T026 [US2] Implement per-attempt timeout enforcement in `src/main/kotlin/plugin/restore/RestoreAttemptRunner.kt`
- [X] T027 [US2] Implement one-retry orchestration logic in `src/main/kotlin/plugin/restore/RetryPolicyEngine.kt`
- [X] T028 [P] [US2] Implement failure reason normalization for expired/unsupported sessions in `src/main/kotlin/plugin/adapters/AgentFailureMapper.kt`
- [X] T029 [US2] Extend restore summary UI with per-terminal failure details and retry action in `src/main/kotlin/plugin/restore/ui/RestoreSummaryNotifier.kt`
- [X] T030 [US2] Implement single-terminal manual retry action in `src/main/kotlin/plugin/restore/actions/RetryFailedTerminalAction.kt`
- [ ] T031 [US2] Execute manual validation for Quickstart Scenarios B and E and record evidence in `specs/001-resume-agent-sessions/quickstart.md`

**Checkpoint**: User Stories 1 and 2 both work independently with bounded failure behavior

---

## Phase 5: User Story 3 - Preserve Terminal-to-Agent Mapping Across Mixed Agents (Priority: P3)

**Goal**: Guarantee correct agent/session mapping per terminal with project-scoped restore and manual mode

**Independent Test**: Mixed-agent terminals restore with exact prior mappings; same-agent multiple sessions are not swapped; manual mode disables startup restore but allows project-scoped manual restore.

### Tests for User Story 3

- [X] T032 [P] [US3] Add unit tests for project-scope filtering and terminal-session uniqueness in `tests/unit/restore/ProjectScopeMappingTest.kt`
- [X] T033 [P] [US3] Add integration test for mixed-agent mapping fidelity in `tests/integration/restore/MixedAgentMappingTest.kt`

### Implementation for User Story 3

- [X] T034 [P] [US3] Implement Codex adapter in `src/main/kotlin/plugin/adapters/providers/CodexAgentAdapter.kt`
- [X] T035 [P] [US3] Implement Claude adapter in `src/main/kotlin/plugin/adapters/providers/ClaudeAgentAdapter.kt`
- [X] T036 [P] [US3] Implement OpenCode adapter in `src/main/kotlin/plugin/adapters/providers/OpenCodeAgentAdapter.kt`
- [X] T037 [US3] Implement adapter registry and agent-type resolution in `src/main/kotlin/plugin/adapters/AgentAdapterRegistry.kt`
- [X] T038 [US3] Implement manual restore mode behavior in `src/main/kotlin/plugin/settings/RestorePolicySettingsService.kt` and `src/main/kotlin/plugin/restore/StartupRestoreActivity.kt`
- [X] T039 [US3] Implement project-scoped manual restore action in `src/main/kotlin/plugin/restore/actions/ManualRestoreAction.kt`
- [ ] T040 [US3] Execute manual validation for Quickstart Scenario C and mixed-agent mapping checks in `specs/001-resume-agent-sessions/quickstart.md`

**Checkpoint**: All user stories are independently functional and project-scope safe

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Final contract compliance, observability completeness, and release readiness

- [X] T041 [P] Add privacy contract tests for prohibited persisted fields in `tests/unit/persistence/SnapshotPrivacyContractTest.kt`
- [X] T042 [P] Add compatibility tests for schema migration and backward reads in `tests/unit/persistence/SnapshotMigrationCompatibilityTest.kt`
- [X] T043 Finalize structured restore telemetry coverage in `src/main/kotlin/plugin/telemetry/RestoreTelemetryLogger.kt`
- [ ] T044 Validate full quickstart flow and record final execution evidence in `specs/001-resume-agent-sessions/quickstart.md`
- [X] T045 Verify plugin compatibility/build range and descriptor consistency in `build.gradle.kts` and `src/main/resources/META-INF/plugin.xml`
- [X] T046 Update feature docs after implementation decisions in `specs/001-resume-agent-sessions/plan.md`, `research.md`, and `contracts/session-restore-contract.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies; can start immediately
- **Foundational (Phase 2)**: Depends on Setup; blocks all user stories
- **User Story Phases (Phase 3+)**: Depend on Foundational completion
- **Polish (Phase 6)**: Depends on completion of targeted user stories

### User Story Dependencies

- **US1 (P1)**: Starts immediately after Foundational; delivers MVP restore value
- **US2 (P2)**: Depends on US1 restore orchestration components but remains independently testable
- **US3 (P3)**: Depends on shared adapter and orchestrator foundations; independently testable after implementation

### Within Each User Story

- Write tests first and confirm they fail for new behavior
- Implement models/state before orchestration logic
- Implement orchestration before UI actions/notifications
- Run manual IntelliJ validation before story sign-off

### Parallel Opportunities

- Setup: `T003`, `T004` can run in parallel
- Foundational: `T007`, `T008`, `T009`, `T013` can run in parallel after `T006`
- US1: `T015` and `T016` in parallel; `T018` and `T019` in parallel
- US2: `T024` and `T025` in parallel; `T026` and `T028` in parallel
- US3: `T032` and `T033` in parallel; `T034`, `T035`, `T036` in parallel
- Polish: `T041` and `T042` in parallel

---

## Parallel Example: User Story 1

```bash
# Run US1 tests in parallel
Task: T015 tests/unit/persistence/TerminalSessionSnapshotStoreTest.kt
Task: T016 tests/integration/restore/StartupRestoreHappyPathTest.kt

# Implement independent US1 components in parallel
Task: T018 src/main/kotlin/plugin/persistence/TerminalSessionSnapshotStore.kt
Task: T019 src/main/kotlin/plugin/adapters/TerminalTabMapper.kt
```

## Parallel Example: User Story 2

```bash
# Run US2 tests in parallel
Task: T024 tests/unit/restore/RetryAndTimeoutPolicyTest.kt
Task: T025 tests/integration/restore/PartialFailureRetryTest.kt

# Implement policy + mapping components in parallel
Task: T026 src/main/kotlin/plugin/restore/RestoreAttemptRunner.kt
Task: T028 src/main/kotlin/plugin/adapters/AgentFailureMapper.kt
```

## Parallel Example: User Story 3

```bash
# Run US3 tests in parallel
Task: T032 tests/unit/restore/ProjectScopeMappingTest.kt
Task: T033 tests/integration/restore/MixedAgentMappingTest.kt

# Build provider adapters in parallel
Task: T034 src/main/kotlin/plugin/adapters/providers/CodexAgentAdapter.kt
Task: T035 src/main/kotlin/plugin/adapters/providers/ClaudeAgentAdapter.kt
Task: T036 src/main/kotlin/plugin/adapters/providers/OpenCodeAgentAdapter.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup
2. Complete Phase 2: Foundational
3. Complete Phase 3: User Story 1
4. Validate Scenario A from `quickstart.md`
5. Demo MVP restore behavior

### Incremental Delivery

1. Setup + Foundational -> baseline ready
2. Deliver US1 (auto-restore MVP)
3. Deliver US2 (failure isolation + retry)
4. Deliver US3 (mixed-agent fidelity + manual mode)
5. Complete polish and release checks

### Parallel Team Strategy

1. Team completes Setup + Foundational together
2. Then parallelize by story ownership:
   - Engineer A: US1 core restore flow
   - Engineer B: US2 failure handling and retry
   - Engineer C: US3 adapters and mapping consistency
3. Merge with shared contract and telemetry checks in Phase 6

---

## Notes

- [P] tasks are isolated to separate files and can be worked concurrently
- [US#] labels map every story task to its originating user story for traceability
- Each story phase includes automated tests and manual IntelliJ validation evidence
- Suggested MVP scope: Phase 1 + Phase 2 + Phase 3 (through T023)
