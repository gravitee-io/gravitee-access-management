**Role:** Senior SDET / Automation Engineer
**Task:** Convert a Postman Collection JSON into a Jest/Supertest TypeScript suite.
**Goal:** Achieve 1:1 functional parity and 100% logic coverage.

**Input Files:**
1. **Source Context:** [Insert Link to Postman Collection JSON]
2. **Style Guide:** [Insert Link to GUIDELINES.md]
3. **Reference Implementation:** [Insert Link to cba-settings.jest.spec.ts]

---

### Phase 1: Conversion Rules

**1. Structure & Scoping**
* Map Postman **Folders** to `describe()` blocks.
* Map Postman **Requests** to `it()` blocks.
* Ensure `beforeAll`/`afterAll` hooks match Collection-level scripts.
* ENFORCE test isolation, each test **MUST** be able to run independently of any other test

**2. Variable Handling & Chaining**
* **Environment Variables:** Convert `{{variable}}` to `process.env.VARIABLE`.
* **Chained Data:** If a Postman test sets a variable (e.g., `pm.environment.set("id", data.id)`), you MUST define a shared variable (e.g., `let sharedId;`) in the top-level `describe` block so subsequent tests can access it.
* **Dynamic Variables:** Map built-ins (e.g., `{{$randomInt}}`) to JS equivalents (e.g., `faker` or `Math.random`).

**3. Logic Conversion**
* **Pre-request Scripts:** Logic inside "Pre-request Script" tabs MUST be converted into the test setup or payload construction *before* the API call.
* **Assertions:** Convert every single `pm.test` assertion:
    * `pm.response.to.have.status(200)` -> `expect(response.status).toBe(200);`
    * `pm.expect(json.val).to.eql(1)` -> `expect(response.body.val).toEqual(1);`

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

### Output Order
1.  **The Parity Matrix** (Generate this FIRST to plan the conversion).
2.  **The Jest Code** (The full `spec.ts` file).

**Constraint:** Do not run the tests.