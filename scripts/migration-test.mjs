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

// Forward all arguments to the actual tool via npx -y zx
// -y ensures it doesn't prompt for installation
const child = spawn('npx', ['-y', 'zx', toolEntry, ...process.argv.slice(2)], {
    stdio: 'inherit',
    shell: true
});

child.on('exit', (code) => {
    process.exit(code || 0);
});

child.on('error', (err) => {
    console.error('❌ Failed to start the migration tool:', err.message);
    process.exit(1);
});
