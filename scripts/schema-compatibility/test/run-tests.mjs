#!/usr/bin/env node
/**
 * Test runner for check-schema-compatibility.mjs
 *
 * For each fixture pair under scripts/schema-compatibility/test/fixtures/:
 *   breaking/*      → expects exit code 1 (breaking changes detected)
 *   non-breaking/*  → expects exit code 0 with no WARN output (clean pass)
 *   maybe-breaking/ → expects exit code 0 with at least one WARN in stdout
 *
 * Special case: if no old.json exists (new plugin), the test is skipped.
 *
 * Usage: node scripts/schema-compatibility/test/run-tests.mjs
 */

import { spawnSync } from 'child_process';
import { readdirSync, existsSync } from 'fs';
import { fileURLToPath } from 'url';
import { dirname, join, resolve } from 'path';

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

const SCRIPT = resolve(__dirname, '..', 'check-schema-compatibility.mjs');
const FIXTURES_DIR = join(__dirname, 'fixtures');

let passed = 0;
let failed = 0;
let skipped = 0;

function invoke(category, caseName) {
  const dir = join(FIXTURES_DIR, category, caseName);
  const oldJson = join(dir, 'old.json');
  const newJson = join(dir, 'new.json');
  const label = `[${category}/${caseName}]`;

  if (!existsSync(oldJson)) {
    console.log(`  SKIP  ${label} — no old.json (new plugin)`);
    skipped++;
    return null;
  }
  if (!existsSync(newJson)) {
    console.log(`  FAIL  ${label} — no new.json found`);
    failed++;
    return null;
  }
  return {
    label,
    result: spawnSync(process.execPath, [SCRIPT, '--old', oldJson, '--new', newJson, '--plugin', caseName], { encoding: 'utf8' }),
  };
}

function printFailDetail(result) {
  if (result.stdout) process.stdout.write('        stdout: ' + result.stdout.split('\n').join('\n        ') + '\n');
  if (result.stderr) process.stderr.write('        stderr: ' + result.stderr.split('\n').join('\n        ') + '\n');
}

/** If spawnSync did not start the child, log and return true. */
function handleSpawnFailure(label, result) {
  if (!result.error) return false;
  console.log(`  FAIL  ${label} — failed to spawn checker: ${result.error.message}`);
  printFailDetail(result);
  failed++;
  return true;
}

/** breaking/* — expect exit 1 */
function runBreaking(caseName) {
  const run = invoke('breaking', caseName);
  if (!run) return;
  const { label, result } = run;
  if (handleSpawnFailure(label, result)) return;
  if (result.status === 1) {
    console.log(`  PASS  ${label}`);
    passed++;
  } else {
    console.log(`  FAIL  ${label} — expected exit 1, got ${result.status}`);
    printFailDetail(result);
    failed++;
  }
}

/** non-breaking/* — expect exit 0 with no WARN output */
function runNonBreaking(caseName) {
  const run = invoke('non-breaking', caseName);
  if (!run) return;
  const { label, result } = run;
  if (handleSpawnFailure(label, result)) return;
  const exit = result.status;
  const hasWarn = result.stdout.includes('WARN');
  if (exit === 0 && !hasWarn) {
    console.log(`  PASS  ${label}`);
    passed++;
  } else if (exit !== 0) {
    console.log(`  FAIL  ${label} — expected exit 0, got ${exit}`);
    printFailDetail(result);
    failed++;
  } else {
    console.log(`  FAIL  ${label} — expected no WARN output, but warnings were emitted`);
    printFailDetail(result);
    failed++;
  }
}

/** maybe-breaking/* — expect exit 0 with at least one WARN in stdout */
function runMaybeBreaking(caseName) {
  const run = invoke('maybe-breaking', caseName);
  if (!run) return;
  const { label, result } = run;
  if (handleSpawnFailure(label, result)) return;
  const exit = result.status;
  const hasWarn = result.stdout.includes('WARN');
  if (exit === 0 && hasWarn) {
    console.log(`  PASS  ${label}`);
    passed++;
  } else if (exit !== 0) {
    console.log(`  FAIL  ${label} — expected exit 0, got ${exit}`);
    printFailDetail(result);
    failed++;
  } else {
    console.log(`  FAIL  ${label} — expected WARN output, but none was emitted`);
    printFailDetail(result);
    failed++;
  }
}

function listSubdirs(dir) {
  if (!existsSync(dir)) return [];
  return readdirSync(dir, { withFileTypes: true })
    .filter(e => e.isDirectory())
    .map(e => e.name)
    .sort();
}

// ---------------------------------------------------------------------------
// Run breaking fixtures — expect exit 1
// ---------------------------------------------------------------------------
console.log('\nBreaking change fixtures (expect exit 1):');
for (const c of listSubdirs(join(FIXTURES_DIR, 'breaking'))) {
  runBreaking(c);
}

// ---------------------------------------------------------------------------
// Run non-breaking fixtures — expect exit 0, no WARN output
// ---------------------------------------------------------------------------
console.log('\nNon-breaking fixtures (expect exit 0, no warnings):');
for (const c of listSubdirs(join(FIXTURES_DIR, 'non-breaking'))) {
  runNonBreaking(c);
}

// ---------------------------------------------------------------------------
// Run maybe-breaking fixtures — expect exit 0 with WARN output
// ---------------------------------------------------------------------------
console.log('\nMaybe-breaking fixtures (expect exit 0 with warnings):');
for (const c of listSubdirs(join(FIXTURES_DIR, 'maybe-breaking'))) {
  runMaybeBreaking(c);
}

// ---------------------------------------------------------------------------
// Summary
// ---------------------------------------------------------------------------
console.log(`\n── Results ──`);
console.log(`  Passed:  ${passed}`);
console.log(`  Failed:  ${failed}`);
console.log(`  Skipped: ${skipped}`);

if (failed > 0) {
  console.log(`\n❌  ${failed} test(s) failed.`);
  process.exit(1);
} else {
  console.log(`\n✅  All tests passed.`);
  process.exit(0);
}
