# Implementation Plan: Restore Agent Sessions in Terminals

**Branch**: `001-resume-agent-sessions` | **Date**: 2026-03-26 | **Spec**: [/home/mstorchak/prj/intellij_agent_sessions_saver_plugin/specs/001-resume-agent-sessions/spec.md](/home/mstorchak/prj/intellij_agent_sessions_saver_plugin/specs/001-resume-agent-sessions/spec.md)
**Input**: Feature specification from `/specs/001-resume-agent-sessions/spec.md`

**Note**: This plan covers Phase 0 (research) and Phase 1 (design/contracts) and is ready for `/speckit.tasks`.

## Summary

Build an IntelliJ IDEA plugin capability that restores agent sessions per terminal after IDE restart,
with project-scoped session mapping, minimal persisted metadata, one automatic retry, explicit
per-terminal timeout, and clear user-visible restore outcomes.

## Technical Context

**Language/Version**: Kotlin 1.9, Java 17 runtime  
**Primary Dependencies**: IntelliJ Platform SDK, bundled Terminal tool window APIs, Kotlinx Serialization, JUnit 5, IntelliJ Platform test framework  
**Storage**: Versioned JSON snapshot in plugin persistent state (project scope) plus IDE-managed terminal tabs  
**Testing**: Unit tests for persistence and mapping, integration tests for restore orchestration, manual IntelliJ validation scenarios  
**Target Platform**: IntelliJ IDEA 2024.3+ (plugin compatibility range declared before release)  
**Project Type**: IntelliJ plugin / desktop extension  
**Performance Goals**: No EDT blocking during restore; each resume attempt times out at 30s; mixed 5-terminal restore completes within 60s in normal conditions  
**Constraints**: Minimal metadata only; no tokens/history/output persistence; project-scoped restore; one automatic retry per failed terminal; deterministic status reporting  
**Scale/Scope**: Up to 20 terminals per project, mixed agent types (Codex/Claude/OpenCode), single-user local machine scope

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [x] User stories are independently deliverable with explicit P1/P2/P3 priority and independent validation paths.
- [x] Plugin contracts are explicit: actions, settings, extension points, persisted schema.
- [x] Persistence safety is documented: compatibility strategy for schema/state changes.
- [x] Threading/performance plan avoids EDT blocking and defines measurable limits.
- [x] Verification plan includes automated checks plus manual IntelliJ validation scenarios.
- [x] Any exception to constitution principles is documented with owner, rationale, and expiry.

**Gate Status**: PASS (pre-research)  
**Post-Design Re-check**: PASS (research + data model + contracts + quickstart satisfy all gates)

## Project Structure

### Documentation (this feature)

```text
specs/001-resume-agent-sessions/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
│   └── session-restore-contract.md
└── tasks.md
```

### Source Code (repository root)

```text
src/
└── main/
    ├── kotlin/
    │   └── plugin/
    │       ├── restore/
    │       ├── persistence/
    │       ├── adapters/
    │       ├── settings/
    │       └── telemetry/
    └── resources/
        └── META-INF/
            └── plugin.xml

tests/
├── unit/
│   ├── persistence/
│   └── restore/
├── integration/
│   └── restore/
└── fixtures/
    └── snapshots/
```

**Structure Decision**: Use a single-module IntelliJ plugin layout. Keep restore orchestration,
persistence, adapter mapping, and user settings in separate packages to preserve independent
story delivery and make compatibility tests isolated.

## Complexity Tracking

No constitutional violations identified; no complexity waiver required.

## Implementation Notes

- Core restore orchestration is implemented through `RestoreCoordinator` + `RetryPolicyEngine`.
- Provider adapters are isolated in `plugin/adapters/providers/` with a registry-based resolver.
- Persistence uses versioned JSON envelopes through `SnapshotSerializer` and
  `SnapshotMigrationService`.
- Final resolver order for exact ID detection is:
  1. command line
  2. process environment (`/proc/<pid>/environ`)
  3. terminal text hints
  4. provider local state (Copilot workspace files)
- Restore dispatch policy is exact-first:
  - explicit ID => exact resume command
  - ambiguous IDs => startup chooser prompt
  - chooser cancel or missing explicit ID => provider fallback command (`continue/last`)
