/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/* eslint-disable no-console */

/**
 * Migration seeding bootstrap.
 *
 * Iterates versioned seed folders (migration-seeding/versions/<major.minor>/)
 * in ascending order, running each seed up to and including --to-version.
 *
 * Optional environment variables (defaults shown):
 * - AM_MANAGEMENT_URL      (default: http://localhost:8093)
 * - AM_MANAGEMENT_ENDPOINT (default: AM_MANAGEMENT_URL)
 * - AM_DEF_ORG_ID          (default: DEFAULT)
 * - AM_DEF_ENV_ID          (default: DEFAULT)
 * - AM_ADMIN_USERNAME      (default: admin)
 * - AM_ADMIN_PASSWORD      (default: adminadmin)
 * - AM_SEED_USER_PASSWORD  (default: Gravitee@12345!)
 *
 * Usage:
 *   yarn migration:seed --to-version 4.11
 *   yarn migration:seed --to-version 4.12 --from-version 4.11  (seeds only versions in (4.11, 4.12])
 */

import fs from 'fs';
import path from 'path';
import 'cross-fetch/polyfill';

function setupEnvDefaults(): void {
  process.env.AM_MANAGEMENT_URL ||= 'http://localhost:8093';
  process.env.AM_MANAGEMENT_ENDPOINT ||= `${process.env.AM_MANAGEMENT_URL}/management`;
  process.env.AM_DEF_ORG_ID ||= 'DEFAULT';
  process.env.AM_DEF_ENV_ID ||= 'DEFAULT';
  process.env.AM_ADMIN_USERNAME ||= 'admin';
  process.env.AM_ADMIN_PASSWORD ||= 'adminadmin';
  process.env.AM_SEED_USER_PASSWORD ||= 'Gravitee@12345!';
}

async function fetchAdminAccessToken(): Promise<string> {
  const username = process.env.AM_ADMIN_USERNAME;
  const password = process.env.AM_ADMIN_PASSWORD;
  const credentials = Buffer.from(`${username}:${password}`).toString('base64');
  const res = await fetch(`${process.env.AM_MANAGEMENT_URL}/management/auth/token`, {
    method: 'POST',
    headers: {
      Authorization: `Basic ${credentials}`,
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: `grant_type=password&username=${encodeURIComponent(username)}&password=${encodeURIComponent(password)}`,
  });
  if (!res.ok) {
    throw new Error(`Failed to obtain admin access token: HTTP ${res.status}`);
  }
  const body = await res.json();
  return body.access_token;
}

function parseVersion(v: string): [number, number] {
  const parts = v.split('.');
  if (parts.length < 2) {
    throw new Error(`Invalid version format "${v}": expected <major.minor>`);
  }
  const major = parseInt(parts[0], 10);
  const minor = parseInt(parts[1], 10);
  if (isNaN(major) || isNaN(minor)) {
    throw new Error(`Invalid version format "${v}": components must be integers`);
  }
  return [major, minor];
}

function compareVersions(a: [number, number], b: [number, number]): number {
  if (a[0] !== b[0]) return a[0] - b[0];
  return a[1] - b[1];
}

async function main(): Promise<void> {
  const args = process.argv.slice(2);
  const toIdx = args.indexOf('--to-version');
  const toVersionStr = toIdx >= 0 ? args[toIdx + 1] : undefined;
  const fromIdx = args.indexOf('--from-version');
  const fromVersionStr = fromIdx >= 0 ? args[fromIdx + 1] : undefined;

  if (!toVersionStr) {
    console.error('Usage: yarn migration:seed --to-version <major.minor> [--from-version <major.minor>]');
    process.exit(1);
  }

  const toVersion = parseVersion(toVersionStr);
  const fromVersion = fromVersionStr ? parseVersion(fromVersionStr) : null;
  setupEnvDefaults();

  const versionsDir = path.join(__dirname, 'versions');
  const entries = fs
    .readdirSync(versionsDir, { withFileTypes: true })
    .filter((d) => d.isDirectory() && /^\d+\.\d+$/.test(d.name))
    .map((d) => ({ folder: d.name, version: parseVersion(d.name) }))
    .filter(({ version }) => {
      if (compareVersions(version, toVersion) > 0) return false;
      if (fromVersion && compareVersions(version, fromVersion) <= 0) return false;
      return true;
    })
    .sort((a, b) => compareVersions(a.version, b.version));

  if (entries.length === 0) {
    const range = fromVersion ? `in range (${fromVersionStr}, ${toVersionStr}]` : `at or below ${toVersionStr}`;
    console.log(`No seed versions found ${range}`);
    return;
  }

  const accessToken = await fetchAdminAccessToken();

  for (const { folder } of entries) {
    console.log(`Seeding version ${folder}...`);
    // eslint-disable-next-line @typescript-eslint/no-var-requires
    const mod = require(path.join(versionsDir, folder, 'seed'));
    await mod.seed(accessToken);
    console.log(`Done seeding version ${folder}`);
  }

  console.log(`Migration seeding complete up to version ${toVersionStr}`);
}

main().catch((err) => {
  console.error('Seeding failed:', err);
  process.exit(1);
});
