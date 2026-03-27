<!--
Sync Impact Report
- Version change: N/A -> 1.0.0
- Modified principles:
  - N/A (initial ratification)
- Added sections:
  - Platform & Data Constraints
  - Delivery Workflow & Quality Gates
- Removed sections:
  - None
- Templates requiring updates:
  - .specify/templates/plan-template.md: ✅ updated
  - .specify/templates/spec-template.md: ✅ updated
  - .specify/templates/tasks-template.md: ✅ updated
  - .specify/templates/commands/*.md: ⚠ pending (directory not present in repository)
- Follow-up TODOs:
  - None
-->

# IntelliJ Agent Sessions Saver Plugin Constitution

## Core Principles

### I. Independent User Value
Every feature MUST be split into independently deliverable user stories with explicit
priority. Each story MUST define an independent validation path that proves user value
without requiring unfinished stories. This keeps delivery incremental and reduces
integration risk.

### II. Explicit Plugin Contracts
All plugin-visible behavior MUST be defined through explicit contracts: actions, settings,
extension points, persisted schema, and user-facing notifications. Hidden coupling through
global mutable state or undocumented side effects is prohibited because it makes IDE
behavior unpredictable and hard to debug.

### III. Safe Session Persistence
Session save/restore changes MUST preserve existing user data across plugin upgrades.
Any change to persisted format MUST include a compatibility strategy (backward-compatible
read or versioned migration) and a rollback-safe fallback. Data loss from schema changes is
never acceptable.

### IV. Threading and Performance Discipline
UI-thread blocking operations MUST be avoided for any potentially slow I/O or processing.
Long-running work MUST execute off the EDT with clear user feedback when relevant.
Performance-sensitive paths (session serialization/deserialization) MUST define measurable
limits in feature specs.

### V. Verifiable Delivery Gates
Each change MUST include reproducible verification evidence: automated checks where
applicable and documented manual IntelliJ validation steps for user-facing behavior.
Pull requests MUST state which constitution principles were validated and how.

## Platform & Data Constraints

- Implementation MUST target a declared IntelliJ Platform and Java/Kotlin baseline in the
  plan document.
- Plugin compatibility range (`since-build`/`until-build`) MUST be defined before release.
- Session data MUST remain local by default. Any network transfer requires explicit product
  requirement and security review.
- Configuration, caches, and persisted session artifacts MUST use deterministic formats and
  stable identifiers to support upgrade safety.

## Delivery Workflow & Quality Gates

- `/speckit.specify` output MUST include user stories, edge cases, measurable success
  criteria, and constitution alignment requirements.
- `/speckit.plan` MUST pass Constitution Check before research/design completion.
- `/speckit.tasks` MUST include tasks for compatibility validation, persistence safety, and
  manual IDE verification.
- Code review MUST reject changes that violate any Core Principle unless an approved
  temporary exception is documented in the plan with expiry and owner.

## Governance

This constitution overrides conflicting local workflow conventions for this repository.
Amendments require:

- a pull request that includes rationale, affected principles/sections, and migration impact;
- approval from repository maintainers before merge;
- synchronized updates to `.specify/templates` artifacts when enforcement rules change.

Versioning policy:

- MAJOR: Removes or redefines a principle in a backward-incompatible way.
- MINOR: Adds a new principle/section or materially expands governance requirements.
- PATCH: Clarifies wording without changing governance intent.

Compliance review expectations:

- Every implementation plan MUST include a Constitution Check section.
- Every pull request MUST list executed verification steps mapped to changed behavior.
- Periodic audits MAY sample merged work for constitution compliance and open corrective
  tasks for gaps.

**Version**: 1.0.0 | **Ratified**: 2026-03-26 | **Last Amended**: 2026-03-26
