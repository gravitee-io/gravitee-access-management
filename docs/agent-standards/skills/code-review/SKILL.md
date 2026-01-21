---
name: code-review
description: Use when reviewing diffs before PR/review/merge. Produces a systematic review (correctness, patterns, tests, security, contracts) and a short fix list. Review-only: do not change code unless asked.
---

# Code Review

## Purpose
Perform a systematic code review of the current changeset and report actionable findings. By default this is **review-only**: do not modify code unless explicitly requested.

## When to use
Apply this skill:
- After implementing changes, **before opening a PR / requesting review / merging**
- When explicitly asked to review code
- As the "validation" step at the end of a task

**Scope:** Identify the current 'staged' changes or the diff between the current branch and the main branch before reviewing.

**Priority:** If the changeset is large, prioritize **Global Standards** and **Architecture** findings first.

## Review mode
- Review-only by default: identify issues and propose fixes; stop for confirmation before making changes.
- **Cite evidence:** For every finding, provide the **line number and a snippet** of the code in question.
- Prefer project commands from `@docs/agent-standards/commands.md` (if present); otherwise suggest likely commands and ask before adding/updating commands.md.

## Output format
Return results in this structure:

1) **Verdict**: PASS / NEEDS_CHANGES  
2) **Top risks** (0–3 bullets)  
3) **Findings**
   - **Blockers** (must fix) - Security, blocking calls, missing audit
   - **Warnings** (should fix) - Pattern violations, missing tests
   - **Nits** (optional) - Style, naming, comments
4) **Recommended validation commands** (reference `commands.md` if available)
5) **Next steps (Task List)**
   - [ ] Task 1: (e.g., Fix the `blockingGet` in `UserService.java:42`)
   - [ ] Task 2: (e.g., Run `mvn openapi-generator:generate`)
   - [ ] Task 3: (e.g., Add audit log to `deleteUser`)
   
   *Note: I can execute these tasks for you one by one if you say 'Proceed with Step X'.*

## Review checklist

### 1. Global Standards (Apply to ALL changes)

**Security & Compliance:**
- [ ] No secrets/tokens/PII in logs (prefer structured logs: key=value)
- [ ] No full request/response bodies logged
- [ ] Authorization/validation fail closed (deny by default)
- [ ] RFC compliance verified for OAuth/OIDC/IAM work (RFC 6749, 7519, 7636, 8252, 8707)
- [ ] No exposure of internal implementation details in error messages

**Diff Integrity:**
- [ ] Small, reviewable diffs; no unrelated refactors/reformatting
- [ ] Changes focused on stated goal
- [ ] No commented-out code or debug statements

**Code Quality:**
- [ ] UK English for comments, logs, user-facing text
- [ ] Method/class visibility not widened without justification
- [ ] Error handling preserves business intent (expected 4xx not wrapped into 5xx)
- [ ] No redundant null checks (prefer `Optional` or `@NotNull`)
- [ ] No log pollution (avoid logging entire DTOs/entities with PII)

### 2. Architecture & Patterns (The "How")

**Layering:**
- [ ] Respects layering: **Repository → Service → Resource**
- [ ] Resources handle HTTP + permissions; Services handle business logic
- [ ] No business logic leaking into Resource layer

**Reactive Integrity (CRITICAL):**
- [ ] **Strictly NO `blockingGet()`** or blocking calls in reactive paths
- [ ] RxJava used correctly: `Single`/`Maybe` for I/O, `Completable` for side-effects
- [ ] Reactive chains properly composed

**Audit Trail (MANDATORY for C/U/D):**
- [ ] Audit logging present for **all** CREATE/UPDATE/DELETE operations
- [ ] Audit logging implemented in **Service layer** (not Resource)
- [ ] Resources **pass authenticated User principal** to service methods
- [ ] Uses dedicated `AuditBuilder` per entity type

**Pattern Adherence:**
- [ ] Follows established patterns (cite similar implementations found)
- [ ] No invented endpoints, config keys, or behaviours
- [ ] Reuses existing utilities/helpers where applicable

### 3. Data & API Contracts (The "Mechanics")

**API Contracts:**
- [ ] OpenAPI spec updated when API behaviour/endpoints/DTOs change
- [ ] SDK regenerated when required
- [ ] Backward compatible OR breaking change explicitly approved
- [ ] Contract/API tests cover success + error paths
- [ ] HTTP status semantics match existing patterns

**Data & Persistence:**
- [ ] Repository interfaces extend `CrudRepository<T, String>` (unless established pattern differs)
- [ ] Repository methods are domain-scoped (avoid wide fetch + in-code filtering)
- [ ] **If a Repository is changed, check for corresponding implementation in BOTH `mongo/` and `jdbc/` directories**
- [ ] Both MongoDB and JDBC implementations provided when applicable
- [ ] Liquibase changesets grouped logically (table + indexes + constraints together)
- [ ] Mongo indexes follow established initials + sort-order naming convention

### 4. Testing & Validation (The "Proof")

**Test Coverage:**
- [ ] Success **and** error paths covered
- [ ] Bottom-up coverage: **Repository → Service → Resource**
- [ ] CREATE/UPDATE/DELETE tests **must verify audit logging**
- [ ] Tests actually verify audit events are emitted (not just "audit was called")

**Implementation Parity:**
- [ ] Both MongoDB and JDBC tested via shared test suite (when applicable)
- [ ] Integration tests updated where needed

**Validation Commands:**
- [ ] Validation commands referenced from `commands.md` or proposed
- [ ] Tests pass, lint is clean

**Documentation & Traceability:**
- [ ] Complex logic explained (JavaDoc or inline comments)
- [ ] Files cited that informed implementation (pattern-following)
- [ ] Breaking changes clearly called out for PR description

## Self-review prompts (quick)
1) Pattern adherence: what existing pattern was followed, where is it?
2) Search-first: what similar implementations were inspected?
3) Risk: what could break, and which edge cases exist?
4) Reviewability: is the diff focused?
5) Completeness: does it satisfy acceptance criteria?
6) Testing: do tests verify behaviour (not just exercise code)?

## Common issues to catch

**Architectural:**
- Audit logging implemented in Resource instead of Service layer
- Business logic in Resource layer
- Blocking calls in reactive paths (`blockingGet()`)

**Data:**
- Wide queries + in-code filtering instead of scoped queries
- Missing Mongo/JDBC parity where required
- Repository changes without checking both `mongo/` and `jdbc/` directories

**API:**
- Missing SDK regeneration after API changes
- Breaking changes without migration documentation

**Testing:**
- Tests that don't verify audit events were emitted
- Only testing one storage implementation (Mongo or JDBC, not both)

**Code Quality (Agent-isms):**
- Redundant null checks when using `Optional` or `@NotNull`
- Log pollution: logging entire DTOs/entities instead of IDs
- Unrelated refactors mixed into feature changes
- Commented-out code or debug statements left behind

## References (optional)
- `@docs/agent-standards/commands.md`
- `@docs/agent-standards/templates/openapi_change_checklist.template.md`
- `@docs/agent-standards/templates/migration_checklist.template.md`

## Exit criteria
- Verdict issued with evidence (line numbers and snippets)
- Findings prioritized by severity (Blockers > Warnings > Nits)
- Actionable task list provided
- Suggested validation commands included
- No code changes made unless explicitly requested
