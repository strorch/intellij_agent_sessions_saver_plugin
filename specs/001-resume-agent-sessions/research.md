# Phase 0 Research: Restore Agent Sessions in Terminals

## Decision 1: Plugin Baseline and Language

- Decision: Use Kotlin 1.9 on Java 17 with IntelliJ Platform 2024.3+ compatibility.
- Rationale: Kotlin is the default ecosystem for modern IntelliJ plugin development, reducing boilerplate
  and improving readability for state and contract handling.
- Alternatives considered:
  - Java-only implementation: valid but higher verbosity for data-model serialization.
  - Newer Java baseline: increases compatibility risk with older supported IDE builds.

## Decision 2: Persistence Format and Migration Strategy

- Decision: Persist restore state as versioned JSON metadata with explicit `schemaVersion` and
  `projectScopeId`.
- Rationale: JSON is auditable and straightforward to migrate; schema versioning enforces backward-safe
  reads and future migration paths.
- Alternatives considered:
  - Binary serialization: compact but opaque and harder to safely migrate.
  - Unversioned key-value state: simpler initially but fragile for future schema changes.

## Decision 3: Metadata Minimization and Privacy Boundary

- Decision: Persist only minimal resume metadata (terminal identity, agent type, session reference,
  timestamps, status); never persist tokens, terminal output, or command history.
- Rationale: Reduces security/privacy risk while preserving required restore behavior.
- Alternatives considered:
  - Persisting output snippets for diagnostics: increases risk surface without core user value.
  - Persisting full terminal history: conflicts with constitution and privacy constraints.

## Decision 4: Restore Scope and Trigger Policy

- Decision: Restore only within the same project workspace; auto-restore enabled by default with
  optional manual mode.
- Rationale: Project scoping prevents cross-project session contamination and keeps mapping deterministic.
- Alternatives considered:
  - Cross-project restore in same IDE process: higher ambiguity and mismatch risk.
  - Global cross-IDE restore: exceeds current scope and increases conflict complexity.

## Decision 5: Failure Handling, Retry, and Timeout

- Decision: Use one automatic retry per failed terminal and enforce a 30-second timeout per attempt.
- Rationale: Balances transient-failure recovery with predictable startup completion.
- Alternatives considered:
  - No automatic retry: lower resilience for temporary failures.
  - Multiple retries: can prolong startup and reduce responsiveness.

## Decision 6: Restore Orchestration Pattern

- Decision: Implement a background restore coordinator with per-terminal task isolation and aggregated
  result reporting.
- Rationale: Avoids EDT blocking, contains failures per terminal, and provides clear progress/status
  to users.
- Alternatives considered:
  - Sequential blocking restore on startup: violates responsiveness constraints.
  - Fully unmanaged parallel restore: harder to provide deterministic status summary.

## Decision 7: Agent Integration Contract

- Decision: Define adapter contracts per agent family (Codex, Claude, OpenCode) with canonical
  `resume(sessionReference)` behavior and normalized result statuses.
- Rationale: Supports mixed-agent terminals while preventing provider-specific logic from leaking into
  core restore orchestration.
- Alternatives considered:
  - Single generic command template only: weak validation and inconsistent failure semantics.
  - Hardcoded monolithic logic: poor extensibility and harder testing.

## Decision 8: Verification Evidence Strategy

- Decision: Combine automated unit/integration validation with manual IntelliJ restart scenarios,
  including mixed-agent and partial-failure flows.
- Rationale: Startup terminal restoration is user-facing and environment-dependent, requiring both
  deterministic automated checks and real IDE behavior verification.
- Alternatives considered:
  - Automated checks only: insufficient confidence in real startup behavior.
  - Manual checks only: weak regression protection for persistence and orchestration logic.

## Resolved Unknowns Summary

All planning unknowns are resolved for this feature:
- No unresolved `NEEDS CLARIFICATION` items remain.
- Constitution gates are satisfied for persistence safety, contract explicitness, performance discipline,
  and verification requirements.

## Implementation Alignment Notes

- One-retry and 30-second timeout policy are implemented in `RetryPolicyEngine` and
  `RestoreAttemptRunner`.
- Project-scoped restore filtering is implemented in `ProjectScopeGuard`.
- Structured telemetry events are emitted for restore start, terminal attempts, summary, and end.
