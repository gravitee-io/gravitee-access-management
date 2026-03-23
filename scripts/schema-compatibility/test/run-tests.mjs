#!/usr/bin/env node
/**
 * Test runner for check-schema-compatibility.mjs
 *
 * For each fixture pair under scripts/schema-compatibility/test/fixtures/:
 *   breaking/*   → expects exit code 1 (breaking changes detected)
 *   non-breaking/* → expects exit code 0 (no breaking changes, or warnings only)
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

/**
 * Run one test case.
 * @param {string} category   'breaking' or 'non-breaking'
 * @param {string} caseName   fixture directory name
 * @param {number} expectedExit
 */
function runTest(category, caseName, expectedExit) {
  const dir = join(FIXTURES_DIR, category, caseName);
  const oldJson = join(dir, 'old.json');
  const newJson = join(dir, 'new.json');

  if (!existsSync(oldJson)) {
    console.log(`  SKIP  [${category}/${caseName}] — no old.json (new plugin)`);
    skipped++;
    return;
  }

  if (!existsSync(newJson)) {
    console.log(`  FAIL  [${category}/${caseName}] — no new.json found`);
    failed++;
    return;
  }

  const result = spawnSync(
    process.execPath,
    [SCRIPT, '--old', oldJson, '--new', newJson, '--plugin', caseName],
    { encoding: 'utf8' }
  );

  const actual = result.status ?? 1;
  const label = `[${category}/${caseName}]`;

  if (actual === expectedExit) {
    console.log(`  PASS  ${label}`);
    passed++;
  } else {
    console.log(`  FAIL  ${label} — expected exit ${expectedExit}, got ${actual}`);
    if (result.stdout) process.stdout.write('        stdout: ' + result.stdout.split('\n').join('\n        ') + '\n');
    if (result.stderr) process.stderr.write('        stderr: ' + result.stderr.split('\n').join('\n        ') + '\n');
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
  runTest('breaking', c, 1);
}

// ---------------------------------------------------------------------------
// Run non-breaking fixtures — expect exit 0
// ---------------------------------------------------------------------------
console.log('\nNon-breaking fixtures (expect exit 0):');
for (const c of listSubdirs(join(FIXTURES_DIR, 'non-breaking'))) {
  runTest('non-breaking', c, 0);
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
