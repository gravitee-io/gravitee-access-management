# maybe-breaking fixtures

Fixtures in this directory represent schema changes that the checker **cannot classify with
certainty**. Each one detects that something changed but cannot determine — without evaluating
actual data against both schemas — whether existing data would pass or fail the new schema.

The checker emits a `WARN` and exits 0 for these cases, so CI does not block. A human reviewer
should assess whether the change is safe before merging.

## Why these cases exist

Full JSON Schema compatibility requires instance-level reasoning: to know whether a change is
breaking, you need to ask "is there any document that was valid under the old schema but is now
invalid under the new one?" For certain constructs — conditional subschemas (`if`/`then`/`else`),
negation (`not`), combiner branch content (`allOf`/`anyOf`/`oneOf`) — answering that question
requires evaluating the schema against data, which is beyond static analysis.

The checker therefore warns conservatively: a change was detected, the impact is unknown, review
is required.

## Adding a fixture

- Create a subdirectory here with `old.json` and `new.json`.
- The test runner asserts **exit 0 AND at least one `WARN` in stdout**.
