---
trigger: always_on
---

## Coding Standards & Best Practices

When making changes to this codebase, strict adherence to the following practices is required:

### 1. Code Style & Structure
*   **Adhere to Project Conventions:** Rigorously adhere to existing project conventions. Analyze surrounding code, tests, and configuration before writing new code.
*   **Mimic Existing Style:** Match the formatting, naming conventions, and architectural patterns of the existing code.
*   **Integrate Idiomatically:** Ensure changes integrate naturally with local context (imports, functions/classes). Use idiomatic Java (Spring) and TypeScript (Angular) patterns.
*   **Verify Library Usage:** Do not assume a library is available. Check `pom.xml` or `package.json` and established usage patterns before introducing new dependencies or classes.

### 2. Reactive Programming
*   **Backend (RxJava3):** The Java backend heavily relies on RxJava3.
    *   **No Blocking Calls:** **Absolutely NO blocking calls** are permitted (e.g., `Thread.sleep`, blocking I/O, `Future.get()`).
    *   **Reactive Types:** Use `Single`, `Maybe`, `Completable`, or `Flowable` appropriately.
    *   **Error Handling:** Manage errors within the reactive stream using operators like `onErrorResumeNext`.
*   **Frontend (RxJS):** The Angular UI project extensively uses RxJS.
    *   **Observables:** Use `Observable` streams for data handling, event propagation, and state management.
    *   **Pipeable Operators:** Use pipeable operators (`map`, `switchMap`, `catchError`) for clean and composable logic.
    *   **Subscription Management:** Ensure proper subscription management (e.g., `AsyncPipe`, `takeUntil`) to prevent memory leaks.

### 3. Quality Assurance
*   **Unit Tests:** Every new feature or bug fix must be accompanied by unit tests. Treat tests as permanent artifacts.
*   **E2E Integration Tests (Jest):** Use **Jest** for end-to-end integration tests against the API (located in `gravitee-am-test/`). Ensure these tests validate the full flow of the feature or fix.
*   **Verify Build:** Always run `mvn clean install` (backend) or `yarn test` / `yarn build` (frontend) to ensure changes pass build, linting, and tests.
*   **High-Value Comments:** Add comments sparingly. Focus on *why* complex logic exists, not *what* the code is doing.

### 4. Safety & Operations
*   **Ensure Safe Operations:** Be cautious with file system modifications.
*   **Secure Code:** Never introduce code that exposes secrets or sensitive information.

### 5. Version Control
*   **Branching:** Use the pattern `fix/AM-<issue-id>-short-description` (e.g., `fix/AM-123-fix-login-bug`).
*   **Commit Messages:** Write clear, concise messages focused on the "why". Format: `fix(auth): handle null pointer in login flow`.