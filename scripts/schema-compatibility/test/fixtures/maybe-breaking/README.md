# maybe-breaking fixtures

Fixtures in this directory represent schema changes that the checker **cannot classify with
certainty**. Each one detects that something changed but cannot determine — without evaluating
actual data against both schemas — whether existing data would pass or fail the new schema.

The checker emits a `WARN` and exits 0 for these cases, so CI does not block. A human reviewer
should assess whether the change is safe before merging.

## Why these cases exist

Full JSON Schema compatibility requires instance-level reasoning: to know whether a change is
breaking, you need to ask "is there any document that was valid under the old schema but is now
invalid under the new one?" The checker detects most breaking changes structurally, but three
constructs cannot be classified by static analysis alone:

- **`if`-condition changes**: the checker can see the condition changed, but which instances
  satisfy the new condition (and therefore hit `then`/`else`) cannot be determined without
  evaluating data.
- **`not` content changes**: the semantics are inverted — a more-permissive `not` schema rejects
  *more* data, not less. Determining the direction of impact requires reasoning about the
  relationship between the old and new negated schemas.
- **Tuple `items` structural changes**: form/count changes (uniform ↔ tuple, count change) affect
  positional validation in ways that depend on the length and content of existing array data.

The checker warns conservatively for these cases: a change was detected, the impact is unknown,
review is required.

## Adding a fixture

- Create a subdirectory here with `old.json` and `new.json`.
- The test runner asserts **exit 0 AND at least one `WARN` in stdout**.
