# Breaking Changes — Full Classification

The authoritative source is the fixture suite under
`scripts/schema-compatibility/test/fixtures/breaking/` and `non-breaking/` — each subdirectory
is a concrete example the checker is tested against.

## ERROR — blocks CI (exit 1)

| Category | Notes |
|---|---|
| Field added to `required` | Existing data may lack this field |
| New required field (didn't exist before) | |
| Property key removed | Existing data referencing this field loses its value |
| Property key renamed (= remove + add) | |
| `type` value changed | |
| `const` added or changed | |
| Enum value removed | Existing values become invalid |
| `enum` added to previously free-form field | |
| `additionalProperties: false` added | |
| `additionalProperties` schema added (e.g., `{type:"string"}`) | |
| `additionalProperties` schema type changed | |
| `pattern` or `format` added | |
| `minLength` increased or added | |
| `maxLength` decreased or added | |
| `minimum`/`maximum` tightened or added | |
| `minItems` increased or added | |
| `maxItems` decreased or added | |
| `uniqueItems: true` added | |
| `allOf` entry added | |
| `anyOf`/`oneOf` added from scratch | |
| `anyOf`/`oneOf` branch removed | |
| `if`/`then`/`else` added | |
| `not` added | |
| `allOf`/`anyOf`/`oneOf` branch content changed | |
| `then`/`else` content changed | |
| `then`/`else` branch added to existing conditional | |
| Required field added to `items` schema | |
| Property removed from `items` schema | |
| Definition removed from `definitions`/`$defs` | |
| Breaking change inside a definition | Recursively checked |

## WARN — does not block CI (exit 0)

| Category | Notes |
|---|---|
| `then`/`else` branch removed from existing conditional | Generally safe but verify intent |
| `if` condition changed (then/else unchanged) | Impact depends on runtime data |
| `not` content changed or removed | Inverted semantics; direction unreliable statically |
| Tuple `items` form or count changed | Impact depends on existing array content |
| Tuple `items` positional schema changed | ERROR or WARN (recursed) |
| Field removed from `required` (demoted to optional) | Generally safe |

## Non-breaking — no finding emitted

| Category |
|---|
| Optional field added |
| Optional field added to `items` schema |
| Definition added to `definitions`/`$defs` |
| `const` removed (field becomes free-form) |
| `description`/`title` added or changed |
| `maxLength` increased |
| `minimum`/`maximum` loosened |
| Enum value added |
| `additionalProperties` schema or `false` removed |
