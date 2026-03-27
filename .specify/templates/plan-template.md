# Implementation Plan: [FEATURE]

**Branch**: `[###-feature-name]` | **Date**: [DATE] | **Spec**: [link]
**Input**: Feature specification from `/specs/[###-feature-name]/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

[Extract from feature spec: primary requirement + technical approach from research]

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: [e.g., Kotlin 1.9, Java 17 or NEEDS CLARIFICATION]  
**Primary Dependencies**: [e.g., IntelliJ Platform SDK, Gradle IntelliJ Plugin or NEEDS CLARIFICATION]  
**Storage**: [e.g., IDE local files, plugin state, or N/A]  
**Testing**: [e.g., JUnit, IntelliJ test framework, integration UI checks or NEEDS CLARIFICATION]  
**Target Platform**: [e.g., IntelliJ IDEA 2024.3+ or NEEDS CLARIFICATION]  
**Project Type**: [IntelliJ plugin / desktop extension]  
**Performance Goals**: [e.g., session restore under X seconds, non-blocking UI response]  
**Constraints**: [e.g., no data loss on upgrade, no EDT blocking for slow operations]  
**Scale/Scope**: [e.g., session count, project size bounds, supported IDE builds]

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- [ ] User stories are independently deliverable with explicit P1/P2/P3 priority and
      independent validation paths.
- [ ] Plugin contracts are explicit: actions, settings, extension points, persisted schema.
- [ ] Persistence safety is documented: compatibility strategy for schema/state changes.
- [ ] Threading/performance plan avoids EDT blocking and defines measurable limits.
- [ ] Verification plan includes automated checks plus manual IntelliJ validation scenarios.
- [ ] Any exception to constitution principles is documented with owner, rationale, and expiry.

## Project Structure

### Documentation (this feature)

```text
specs/[###-feature]/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
# [REMOVE IF UNUSED] Option 1: Single project (DEFAULT)
src/
├── models/
├── services/
├── cli/
└── lib/

tests/
├── contract/
├── integration/
└── unit/

# [REMOVE IF UNUSED] Option 2: Web application (when "frontend" + "backend" detected)
backend/
├── src/
│   ├── models/
│   ├── services/
│   └── api/
└── tests/

frontend/
├── src/
│   ├── components/
│   ├── pages/
│   └── services/
└── tests/

# [REMOVE IF UNUSED] Option 3: Mobile + API (when "iOS/Android" detected)
api/
└── [same as backend above]

ios/ or android/
└── [platform-specific structure: feature modules, UI flows, platform tests]
```

**Structure Decision**: [Document the selected structure and reference the real
directories captured above]

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
