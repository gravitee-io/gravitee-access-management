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

  // --- additionalProperties: false added ---
  const oldStrict = oldSub.additionalProperties === false;
  const newStrict = newSub.additionalProperties === false;
  if (!oldStrict && newStrict) {
    error(prefix || '(root)', `additionalProperties: false added — existing data with extra fields will be rejected`);
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

  // --- combiners ---
  // allOf: adding an entry is breaking
  const oldAllOf = oldSub.allOf ?? [];
  const newAllOf = newSub.allOf ?? [];
  if (newAllOf.length > oldAllOf.length) {
    error(path('allOf'), `allOf gained ${newAllOf.length - oldAllOf.length} new constraint(s) — existing data must now satisfy additional schemas`);
  }

  // anyOf/oneOf: removing a branch is breaking
  compareCombiners(oldSub.anyOf, newSub.anyOf, path('anyOf'), 'anyOf');
  compareCombiners(oldSub.oneOf, newSub.oneOf, path('oneOf'), 'oneOf');

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
 * anyOf/oneOf: if new has fewer branches, that is breaking.
 */
function compareCombiners(oldList, newList, pathStr, keyword) {
  if (!oldList || oldList.length === 0) return;
  const newLen = newList ? newList.length : 0;
  if (newLen < oldList.length) {
    error(pathStr, `${keyword} branch removed (${oldList.length} → ${newLen}) — data matching the removed branch(es) will fail`);
  }
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
    console.log(`⚠️   Breaking changes found but --allow-breaking is set (major version bump detected). Exiting 0.`);
    process.exit(0);
  } else {
    console.log(`❌  Breaking changes found. If this is intentional (major version bump), ensure pom.xml major version is incremented.`);
    process.exit(1);
  }
}

// Only warnings — always exit 0
console.log(`✅  [${pluginName}] No breaking changes (${warns.length} warning(s)).`);
process.exit(0);
