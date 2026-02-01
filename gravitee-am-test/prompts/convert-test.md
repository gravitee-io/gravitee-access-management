**Role:** Senior SDET / Automation Engineer
**Task:** Convert a Postman Collection JSON into a Jest/Supertest TypeScript suite.
**Goal:** Achieve 1:1 functional parity and 100% logic coverage.

**Input Files:**
1. **Source Context:** [Insert Link to Postman Collection JSON]
2. **Style Guide:** @Guidelines.md
3. **Reference Implementation:** [Insert Link to cba-settings.jest.spec.ts]
4. **Reference Patterns (auth/flows):** `gravitee-am-test/specs/management/api-management/*.jest.spec.ts`, `management-auth-helper.ts`, and fixtures in `fixtures/` — use for structure of flow helpers, fixtures, and shared auth helpers.

---

### Phase 1: Conversion Rules

**1. Structure & Scoping**
* Map Postman **Folders** to `describe()` blocks.
* Map Postman **Requests** to `it()` blocks.
* Ensure `beforeAll`/`afterAll` hooks match Collection-level scripts.
* ENFORCE test isolation, each test **MUST** be able to run independently of any other test.
* **Prepare / Cleanup folders:** When the collection has a "Prepare" (or similar) folder that runs setup requests (e.g. create org, create IDP) and a "Cleanup" (or "Teardown") folder that runs teardown requests (e.g. delete IDP, reset config), map these to **fixture setup and teardown**, not to standalone `it()` blocks. Use a fixture module that exports a `setupXxxFixture()` (or similar) returning the created resources and a `cleanUp` function; call the setup in `beforeAll` and `cleanUp` in `afterAll`. Optionally keep one or two `it()` blocks under a "Prepare" / "Cleanup" `describe` that assert the fixture state (e.g. "should create alert domain") if that matches the collection’s intent.
* **Extract repeated flow:** If the same sequence of requests (e.g. authorize → get form → POST) appears in multiple tests, extract it into a named helper (in the fixture or a shared module). Do not copy-paste the same multi-step flow across tests.

**2. Variable Handling & Chaining**
* **Environment Variables:** Convert `{{variable}}` to `process.env.VARIABLE`.
* **Chained Data:** If a Postman test sets a variable (e.g., `pm.environment.set("id", data.id)`), you MUST define a shared variable (e.g., `let sharedId;`) in the top-level `describe` block so subsequent tests can access it.
* **Dynamic Variables:** Map built-ins (e.g., `{{$randomInt}}`) to JS equivalents (e.g., `faker` or `Math.random`).

**3. Logic Conversion**
* **Pre-request Scripts:** Logic inside "Pre-request Script" tabs MUST be converted into the test setup or payload construction *before* the API call.
* **Assertions:** Convert every single `pm.test` assertion:
    * `pm.response.to.have.status(200)` -> `expect(response.status).toBe(200);`
    * `pm.expect(json.val).to.eql(1)` -> `expect(response.body.val).toEqual(1);`
* **Every test must assert:** Each `it()` MUST contain at least one `expect()` that always runs (not only inside an `if`). Prefer explicit assertions (e.g. `expect(value).toBeDefined()`, `expect(headers.location).toContain(...)`) for the behaviour under test.

---

### Phase 2: The Parity Audit (Crucial)

**You must generate a "Parity Matrix" table** at the end of your response. This is required to prove that no test case was left behind.

**Format for the Matrix:**
| Postman Element | Source Name | Mapped to Jest Block/Expect | Status | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **Folder** | *User Management* | `describe('User Management', ...)` | ✅ Mapped | - |
| **Request** | *Create User* | `it('should create user', ...)` | ✅ Mapped | - |
| **Pre-Request** | *Generate timestamp* | `const timestamp = Date.now()` | ✅ Mapped | Moved to variable def |
| **Assertion** | *Status 201* | `expect(res.status).toBe(201)` | ✅ Mapped | - |
| **Assertion** | *Check ID format* | - | ⚠️ Skipped | Regex too complex, marked TODO |

**Requirement:**
* Every request in the JSON **must** have a row.
* Every `pm.test` inside those requests **must** have a row.
* If something is skipped or changed significantly, mark it with ⚠️ and explain why in "Notes".

---

### Phase 3: Refactor & quality (single-pass output)

Apply these so the delivered code matches repo patterns without a follow-up refactor.

**1. Helpers and consolidation**
* **Shared helpers:** Pure or cross-spec helpers (e.g. `parseLocation`, `cookieHeaderFromSetCookie`, `getLoginForm`) belong in a shared module (e.g. `management-auth-helper.ts`). Flow-specific helpers that need fixture data (e.g. `runLoginFlowWithCookieJar`, `followRedirectsUntil`) belong in the fixture file that provides that data.
* **No duplication:** If the same function exists in more than one spec or fixture, move it to a shared module and import it. One source of truth per helper.
* **Fixtures:** One fixture per “setup story” (e.g. login, login-social, alert). Fixture returns setup result plus any flow helpers that depend on it. Export helpers used by the spec from the fixture or shared module.

**2. Deterministic behaviour (true path only)**
* **Assert then use:** For values the flow guarantees (e.g. redirect `location`, `Set-Cookie` after authorize), use `expect(x).toBeDefined()` (or equivalent), then use the value. Do not guard with `if (x)` and silently skip — if it’s missing, the test should fail.
* **Optional only when server varies:** Use conditionals only when the server legitimately returns different shapes (e.g. 200 vs 302). Then assert only the branch you care about (e.g. when `status === 302`, assert `headers.location`).
* **Cleanup:** In `afterAll`, use `expect(fixture).toBeDefined(); await fixture!.cleanUp();`. In fixture cleanup, assert required state (e.g. `expect(domain.id).toBeDefined()`) then run teardown; avoid `if (domain?.id && accessToken)` when the fixture guarantees both.

**3. Fixture contracts**
* Fixture interface MUST declare every field returned (e.g. `accessToken`, `domain`, `domain.id`). Specs use these without `!` where the type allows; assert at use site only when crossing a non-null boundary.
* Any value returned from setup and used by tests (e.g. `accessToken`) must be on the fixture interface and set in the returned object.

**4. Formatting and noise**
* Blank line between each `it()` and between logical groups of helpers.
* Remove redundant comments and debug logging. Keep only comments that explain non-obvious behaviour or intent.

**5. Auth / redirect flows (when the collection covers login or OAuth-like flows)**
* Use a per-origin cookie jar (e.g. `Record<origin, cookie>`) when following redirects across origins; merge `Set-Cookie` into the jar and send `Cookie` for the correct origin on each request.
* Parse `Location` (absolute or relative) with a single helper (e.g. `parseLocation(location, baseOrigin)` → `{ origin, pathAndSearch }`).
* For “follow redirects until condition” (e.g. land on redirect_uri), use a small helper that returns the final location or throws with a clear message if the condition is not met within a step limit.

---

### Phase 4: Parity verification

After delivering the Jest code, verify parity against the Parity Matrix and the source collection.

**1. Matrix completeness**
* Confirm every Postman **request** has a row in the matrix and is mapped to an `it()` or to a shared helper invoked by an `it()`.
* Confirm every **pm.test** assertion in the collection has a row and is mapped to an `expect()` (or to behaviour covered by a helper’s assertions).
* Confirm every **folder** is mapped to a `describe()`.
* For any row marked ⚠️ Skipped, ensure "Notes" explains the reason and, if applicable, a TODO or follow-up.

**2. Code vs matrix**
* Walk the delivered spec file and fixture/helper files: for each `it()`, identify the corresponding matrix row(s); for each `expect()`, identify the corresponding assertion row(s).
* Confirm no request or assertion from the collection is missing from the matrix or from the code.
* If you find a gap (e.g. an assertion in the collection with no matching expect), add the missing row and the missing assertion, or document the exception in the matrix with ⚠️ and a note.

**3. Verification statement**
* End the response with a short **Parity verification** statement: e.g. “Verified: N requests and M assertions mapped; 0 gaps” or “Verified: N requests, M assertions; 1 skipped (see matrix note).”

---

### Output Order
1.  **The Parity Matrix** (Generate this FIRST to plan the conversion).
2.  **The Jest Code** (The full `spec.ts` file, plus any new or modified fixture and shared helper files).
3.  **Parity verification** (Statement confirming matrix completeness and code vs matrix check).

**Running tests:** It is possible to run the tests **only if** the user has already started the management API and gateway for you. If those services are not running, do not run the tests; deliver the matrix and code only.