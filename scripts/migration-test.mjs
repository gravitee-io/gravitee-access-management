#!/usr/bin/env node

import { spawn } from 'child_process';
import { fileURLToPath } from 'url';
import { dirname, join } from 'path';

/**
 * Migration Test Tool Launcher
 * ---------------------------
 * This wrapper ensures the tool runs via 'npx zx' to avoid global dependency issues.
 */

const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);
const toolEntry = join(__dirname, 'migration-tool', 'index.mjs');

// Prefer local zx if available to avoid npm cache permission issues
const zxBinary = join(__dirname, 'migration-tool', 'node_modules', '.bin', 'zx');
const useLocal = await import('fs').then(fs => fs.promises.access(zxBinary).then(() => true).catch(() => false));

const cmd = useLocal ? zxBinary : 'npx';
const args = useLocal ? [toolEntry, ...process.argv.slice(2)] : ['-y', 'zx', toolEntry, ...process.argv.slice(2)];

// Use shell: false so args are passed as an array; script then receives them in process.argv
const child = spawn(cmd, args, {
    stdio: 'inherit',
    shell: false
});

child.on('exit', (code) => {
    process.exit(code || 0);
});

child.on('error', (err) => {
    console.error('❌ Failed to start the migration tool:', err.message);
    process.exit(1);
});
