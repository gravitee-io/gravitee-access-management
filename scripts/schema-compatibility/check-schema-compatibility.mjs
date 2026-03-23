#!/usr/bin/env node
/**
 * Schema Backwards-Compatibility Checker
 *
 * Usage:
 *   node check-schema-compatibility.mjs --old <path> --new <path> [--plugin <name>] [--allow-breaking]
 *
 * Exit codes:
 *   0  No breaking changes (or --allow-breaking and only breaking/warn changes found)
 *   1  Breaking changes found (without --allow-breaking)
 *   2  Usage/parse error
 *
 * KNOWN LIMITATIONS
 * -----------------
 * This checker covers the subset of JSON Schema used by AM plugin `schema-form.json` descriptors.
 * The following are NOT checked:
 *
 *   - $ref resolution: schemas using $ref to point to definitions are not dereferenced;
 *     breaking changes inside $ref targets will be missed.
 *
 *   - allOf/anyOf/oneOf content: adding allOf entries and removing anyOf/oneOf branches is
 *     detected. When branch counts stay the same, content changes emit a WARN for manual review
 *     but the structural impact (which data is affected) is not analyzed.
 *
 *   - if/then/else: presence changes are detected (adding emits an ERROR; removing emits a WARN).
 *     When both schemas have if/then/else, content changes emit a WARN for manual review but the
 *     structural impact is not analyzed.
 *
 *   - not: presence and content changes are detected (adding emits an ERROR; removing and content
 *     changes emits a WARN). The structural impact of the negated schema is not recursed.
 *
 *   - tuple items (items as array): form changes and count changes warn; positional schemas are
 *     recursed by index. Interaction with additionalItems/minItems is not analyzed.
 */

import { readFileSync } from 'fs';

// ---------------------------------------------------------------------------
// CLI parsing
// ---------------------------------------------------------------------------

const args = process.argv.slice(2);

function getArg(flag) {
  const idx = args.indexOf(flag);
  return idx !== -1 ? args[idx + 1] : null;
}

const oldPath = getArg('--old');
const newPath = getArg('--new');
const pluginName = getArg('--plugin') ?? 'unknown';
const allowBreaking = args.includes('--allow-breaking');

if (!oldPath || !newPath) {
  console.error('Usage: node check-schema-compatibility.mjs --old <path> --new <path> [--plugin <name>] [--allow-breaking]');
  process.exit(2);
}

// ---------------------------------------------------------------------------
// Schema loading
// ---------------------------------------------------------------------------

function loadSchema(path) {
  try {
    return JSON.parse(readFileSync(path, 'utf8'));
  } catch (err) {
    console.error(`Error reading schema at ${path}: ${err.message}`);
    process.exit(2);
  }
}

const oldSchema = loadSchema(oldPath);
const newSchema = loadSchema(newPath);

// ---------------------------------------------------------------------------
// Finding collection
// ---------------------------------------------------------------------------

/** @type {Array<{severity: 'ERROR'|'WARN', path: string, message: string}>} */
const findings = [];

function error(path, message) {
  findings.push({ severity: 'ERROR', path, message });
}

function warn(path, message) {
  findings.push({ severity: 'WARN', path, message });
}

// ---------------------------------------------------------------------------
// Schema comparison helpers
// ---------------------------------------------------------------------------

/**
 * Deep equality check with key-order normalisation, for comparing schema branches.
 */
function sortKeys(val) {
  if (Array.isArray(val)) return val.map(sortKeys);
  if (val !== null && typeof val === 'object') {
    return Object.fromEntries(Object.keys(val).sort().map(k => [k, sortKeys(val[k])]));
  }
  return val;
}
function deepEqual(a, b) {
  return JSON.stringify(sortKeys(a)) === JSON.stringify(sortKeys(b));
}

function getRequired(schema) {
  return new Set(Array.isArray(schema.required) ? schema.required : []);
}

function getProperties(schema) {
  return schema.properties ?? {};
}

/**
 * Recursively compare two sub-schemas at a given JSON-path prefix.
 * @param {object} oldSub
 * @param {object} newSub
 * @param {string} prefix  e.g. "" for root, "properties.foo" for nested
 */
function compareSchemas(oldSub, newSub, prefix) {
  const path = (suffix) => prefix ? `${prefix}.${suffix}` : suffix;

  // --- type ---
  if (oldSub.type !== undefined && newSub.type !== undefined && oldSub.type !== newSub.type) {
    error(path('type'), `type changed from "${oldSub.type}" to "${newSub.type}"`);
  }

  // --- const ---
  if (oldSub.const === undefined && newSub.const !== undefined) {
    error(path('const'), `const constraint added (${JSON.stringify(newSub.const)}) — field now accepts only this single value`);
  } else if (oldSub.const !== undefined && newSub.const !== undefined && oldSub.const !== newSub.const) {
    error(path('const'), `const value changed from ${JSON.stringify(oldSub.const)} to ${JSON.stringify(newSub.const)} — existing data with the old value will fail`);
  }

  // --- enum ---
  if (oldSub.enum !== undefined) {
    if (newSub.enum === undefined) {
      // enum was completely removed — field is now more permissive (non-breaking)
    } else {
      const oldVals = new Set(oldSub.enum);
      const newVals = new Set(newSub.enum);
      for (const v of oldVals) {
        if (!newVals.has(v)) {
          error(path('enum'), `enum value removed: "${v}" — existing data using this value will fail validation`);
        }
      }
    }
  } else if (newSub.enum !== undefined) {
    error(path('enum'), `enum constraint added — previously free-form field now restricted to: [${newSub.enum.join(', ')}]`);
  }

  // --- additionalProperties ---
  const oldAP = oldSub.additionalProperties;
  const newAP = newSub.additionalProperties;
  const oldStrict = oldAP === false;
  const newStrict = newAP === false;
  if (!oldStrict && newStrict) {
    // nothing/true/schema → false: now fully closed
    error(prefix || '(root)', `additionalProperties: false added — existing data with extra fields will be rejected`);
  } else if (typeof newAP === 'object' && newAP !== null) {
    if (oldAP === undefined || oldAP === true) {
      // unrestricted → schema: additional properties are now type-constrained
      error(prefix || '(root)', `additionalProperties schema added — additional properties are now restricted`);
    } else if (typeof oldAP === 'object' && oldAP !== null) {
      // schema → schema: recurse for internal breaking changes
      compareSchemas(oldAP, newAP, path('additionalProperties'));
    }
    // false → schema: more permissive, non-breaking — no error
  }

  // --- string constraints ---
  compareNumericConstraint(oldSub.minLength, newSub.minLength, path('minLength'), 'minLength', 'increased');
  compareNumericConstraintMax(oldSub.maxLength, newSub.maxLength, path('maxLength'), 'maxLength', 'decreased');

  // --- pattern / format added ---
  if (oldSub.pattern === undefined && newSub.pattern !== undefined) {
    error(path('pattern'), `pattern constraint added: "${newSub.pattern}" — existing data may fail the new pattern`);
  }
  if (oldSub.format === undefined && newSub.format !== undefined) {
    error(path('format'), `format constraint added: "${newSub.format}" — existing data may fail the new format`);
  }

  // --- numeric bounds ---
  compareNumericConstraint(oldSub.minimum, newSub.minimum, path('minimum'), 'minimum', 'increased');
  compareNumericConstraintMax(oldSub.maximum, newSub.maximum, path('maximum'), 'maximum', 'decreased');
  compareNumericConstraint(oldSub.exclusiveMinimum, newSub.exclusiveMinimum, path('exclusiveMinimum'), 'exclusiveMinimum', 'increased');
  compareNumericConstraintMax(oldSub.exclusiveMaximum, newSub.exclusiveMaximum, path('exclusiveMaximum'), 'exclusiveMaximum', 'decreased');

  // --- array constraints ---
  compareNumericConstraint(oldSub.minItems, newSub.minItems, path('minItems'), 'minItems', 'increased');
  compareNumericConstraintMax(oldSub.maxItems, newSub.maxItems, path('maxItems'), 'maxItems', 'decreased');
  if (!oldSub.uniqueItems && newSub.uniqueItems) {
    error(path('uniqueItems'), `uniqueItems: true added — existing data with duplicates will fail`);
  }

  // --- items (array element schema) ---
  if (oldSub.items !== undefined && newSub.items !== undefined) {
    const oldIsArray = Array.isArray(oldSub.items);
    const newIsArray = Array.isArray(newSub.items);
    if (!oldIsArray && !newIsArray) {
      // Both uniform form — recurse
      compareSchemas(oldSub.items, newSub.items, path('items'));
    } else if (oldIsArray && newIsArray) {
      // Both tuple form — warn on count change, recurse into matching positions
      if (newSub.items.length !== oldSub.items.length) {
        warn(path('items'), `tuple items changed from ${oldSub.items.length} to ${newSub.items.length} positional schemas — verify intent; interaction with additionalItems/minItems not analysed`);
      }
      const compareLen = Math.min(oldSub.items.length, newSub.items.length);
      for (let i = 0; i < compareLen; i++) {
        compareSchemas(oldSub.items[i], newSub.items[i], path(`items[${i}]`));
      }
    } else {
      // Form switched between uniform and tuple
      warn(path('items'), `items validation form changed (${oldIsArray ? 'tuple' : 'uniform'} → ${newIsArray ? 'tuple' : 'uniform'}) — impact cannot be fully analysed; review manually`);
    }
  }

  // --- combiners ---
  // allOf: adding an entry is breaking; changed branch content warns (content is not fully analysed)
  const oldAllOf = oldSub.allOf ?? [];
  const newAllOf = newSub.allOf ?? [];
  if (newAllOf.length > oldAllOf.length) {
    error(path('allOf'), `allOf gained ${newAllOf.length - oldAllOf.length} new constraint(s) — existing data must now satisfy additional schemas`);
  } else {
    const compareLen = Math.min(oldAllOf.length, newAllOf.length);
    for (let i = 0; i < compareLen; i++) {
      if (!deepEqual(oldAllOf[i], newAllOf[i])) {
        warn(path(`allOf[${i}]`), `allOf branch ${i} content changed — impact cannot be fully analysed; review manually`);
      }
    }
  }

  // anyOf/oneOf: removing a branch is breaking; changed branch content warns
  compareCombiners(oldSub.anyOf, newSub.anyOf, path('anyOf'), 'anyOf');
  compareCombiners(oldSub.oneOf, newSub.oneOf, path('oneOf'), 'oneOf');

  // --- not ---
  // Adding a not constraint is breaking; removing warns; content changes warn
  const oldNot = oldSub.not;
  const newNot = newSub.not;
  if (oldNot === undefined && newNot !== undefined) {
    error(path('not'), `not constraint added — data that previously matched may now be rejected`);
  } else if (oldNot !== undefined && newNot === undefined) {
    warn(path('not'), `not constraint removed — previously rejected data may now be accepted; verify intent`);
  } else if (oldNot !== undefined && newNot !== undefined && !deepEqual(oldNot, newNot)) {
    warn(path('not'), `not constraint content changed — impact cannot be fully analysed; review manually`);
  }

  // --- if/then/else ---
  // Adding a conditional is breaking; removing warns; content changes warn (full impact not analysed)
  const oldHasIf = oldSub.if !== undefined;
  const newHasIf = newSub.if !== undefined;
  if (!oldHasIf && newHasIf) {
    error(path('if'), `conditional (if/then/else) added — data satisfying the condition must now meet additional constraints`);
  } else if (oldHasIf && !newHasIf) {
    warn(path('if'), `conditional (if/then/else) removed — previously enforced constraints no longer apply; verify intent`);
  } else if (oldHasIf && newHasIf) {
    if (!deepEqual(oldSub.if, newSub.if) || !deepEqual(oldSub.then, newSub.then) || !deepEqual(oldSub.else, newSub.else)) {
      warn(path('if'), `conditional (if/then/else) content changed — impact cannot be fully analysed; review manually`);
    }
  }

  // --- properties ---
  const oldProps = getProperties(oldSub);
  const newProps = getProperties(newSub);
  const oldRequired = getRequired(oldSub);
  const newRequired = getRequired(newSub);

  // Fields added to required
  for (const key of newRequired) {
    if (!oldRequired.has(key)) {
      if (oldProps[key] !== undefined) {
        // Field existed as optional, now required
        error(path(`required[${key}]`), `field "${key}" added to required — existing data without this field will fail`);
      } else {
        // Brand new required field
        error(path(`required[${key}]`), `new required field "${key}" — existing data without this field will fail`);
      }
    }
  }

  // Fields removed from required (demoted to optional) — WARN only
  for (const key of oldRequired) {
    if (!newRequired.has(key)) {
      warn(path(`required[${key}]`), `field "${key}" removed from required (demoted to optional) — generally safe but verify intent`);
    }
  }

  // Property key removed
  for (const key of Object.keys(oldProps)) {
    if (!(key in newProps)) {
      error(path(`properties.${key}`), `property "${key}" removed — existing data referencing this field will lose its value`);
    }
  }

  // Property key added (only required ones are already caught above)
  // Non-breaking: adding optional properties is fine.

  // Recurse into existing properties
  for (const key of Object.keys(oldProps)) {
    if (key in newProps) {
      compareSchemas(oldProps[key], newProps[key], path(`properties.${key}`));
    }
  }

  // --- definitions / $defs ---
  // Walk each keyword independently; cross-keyword renames are not detected.
  for (const defsKey of ['definitions', '$defs']) {
    const oldDefs = oldSub[defsKey] ?? {};
    const newDefs = newSub[defsKey] ?? {};
    for (const key of Object.keys(oldDefs)) {
      if (!(key in newDefs)) {
        error(path(`${defsKey}.${key}`), `definition "${key}" removed from ${defsKey} — any $ref targeting it will break`);
      } else {
        compareSchemas(oldDefs[key], newDefs[key], path(`${defsKey}.${key}`));
      }
    }
  }
}

/**
 * A "min-style" constraint: adding it or increasing it is breaking.
 */
function compareNumericConstraint(oldVal, newVal, pathStr, constraintName, direction) {
  if (newVal === undefined) return;
  if (oldVal === undefined) {
    error(pathStr, `${constraintName} added (${newVal}) — existing data below this threshold will fail`);
  } else if (newVal > oldVal) {
    error(pathStr, `${constraintName} ${direction} from ${oldVal} to ${newVal} — existing data may fail`);
  }
}

/**
 * A "max-style" constraint: adding it or decreasing it is breaking.
 */
function compareNumericConstraintMax(oldVal, newVal, pathStr, constraintName, direction) {
  if (newVal === undefined) return;
  if (oldVal === undefined) {
    error(pathStr, `${constraintName} added (${newVal}) — existing data above this threshold will fail`);
  } else if (newVal < oldVal) {
    error(pathStr, `${constraintName} ${direction} from ${oldVal} to ${newVal} — existing data may fail`);
  }
}

/**
 * anyOf/oneOf: removing a branch is breaking; content changes in same-count branches warn.
 */
function compareCombiners(oldList, newList, pathStr, keyword) {
  if (!oldList || oldList.length === 0) return;
  const newLen = newList ? newList.length : 0;
  if (newLen < oldList.length) {
    error(pathStr, `${keyword} branch removed (${oldList.length} → ${newLen}) — data matching the removed branch(es) will fail`);
  } else if (newLen === oldList.length) {
    for (let i = 0; i < oldList.length; i++) {
      if (!deepEqual(oldList[i], newList[i])) {
        warn(`${pathStr}[${i}]`, `${keyword} branch ${i} content changed — impact cannot be fully analysed; review manually`);
      }
    }
  }
  // newLen > oldList.length: adding branches is non-breaking (more permissive)
}

// ---------------------------------------------------------------------------
// Run comparison
// ---------------------------------------------------------------------------

compareSchemas(oldSchema, newSchema, '');

// ---------------------------------------------------------------------------
// Report
// ---------------------------------------------------------------------------

const errors = findings.filter(f => f.severity === 'ERROR');
const warns = findings.filter(f => f.severity === 'WARN');

if (findings.length === 0) {
  console.log(`✅  [${pluginName}] No breaking changes detected.`);
  process.exit(0);
}

console.log(`\n── Schema compatibility report: ${pluginName} ──`);

if (errors.length > 0) {
  console.log(`\n🔴  BREAKING CHANGES (${errors.length}):`);
  for (const f of errors) {
    console.log(`  ERROR  ${f.path}`);
    console.log(`         ${f.message}`);
  }
}

if (warns.length > 0) {
  console.log(`\n🟡  WARNINGS (${warns.length}):`);
  for (const f of warns) {
    console.log(`  WARN   ${f.path}`);
    console.log(`         ${f.message}`);
  }
}

console.log('');

if (errors.length > 0) {
  if (allowBreaking) {
    console.log(`⚠️   Breaking changes found but --allow-breaking is set. Exiting 0.`);
    process.exit(0);
  } else {
    console.log(`❌  Breaking changes found. If this is intentional, ensure pom.xml minor version (y in x.y.z) is incremented.`);
    process.exit(1);
  }
}

// Only warnings — always exit 0
console.log(`✅  [${pluginName}] No breaking changes (${warns.length} warning(s)).`);
process.exit(0);
