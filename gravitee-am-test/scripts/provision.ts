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
 * Provision environments from a JSON config.
 *
 * Requires environment variables:
 * - AM_MANAGEMENT_URL
 * - AM_MANAGEMENT_ENDPOINT
 * - AM_DEF_ORG_ID
 * - AM_DEF_ENV_ID
 * - AM_ADMIN_USERNAME
 * - AM_ADMIN_PASSWORD
 *
 * Usage:
 *   npm run provision -- ./scripts/provision.example.json
 */

import fs from 'fs';
import path from 'path';
import faker from 'faker';

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createMongoIdp } from '@utils-commands/idps-commands';
import { createRandomString } from '@management-commands/service/utils';
import { getDomainApi, getDomainManagerUrl, getApplicationApi, getUserApi } from '@management-commands/service/utils';
const request = require('supertest');

// ---------- pretty logging helpers ----------
const ansi = {
  reset: '\x1b[0m',
  bold: '\x1b[1m',
  dim: '\x1b[2m',
  red: '\x1b[31m',
  green: '\x1b[32m',
  yellow: '\x1b[33m',
  blue: '\x1b[34m',
  magenta: '\x1b[35m',
  cyan: '\x1b[36m',
  gray: '\x1b[90m',
};
const ICON = {
  ok: '✔',
  fail: '✖',
  warn: '⚠',
  info: 'ℹ',
  rocket: '🚀',
  broom: '🧹',
  gear: '⚙',
  sparkles: '✨',
  hourglass: '⌛',
};
function banner(title: string) {
  const line = '─'.repeat(Math.max(10, title.length + 4));
  console.log(`${ansi.cyan}${'┌' + line + '┐'}${ansi.reset}`);
  console.log(`${ansi.cyan}│${ansi.reset}  ${ansi.bold}${title}${ansi.reset}  ${ansi.cyan}│${ansi.reset}`);
  console.log(`${ansi.cyan}${'└' + line + '┘'}${ansi.reset}`);
}
function section(label: string) {
  console.log(`\n${ansi.magenta}${ICON.gear} ${label}${ansi.reset}`);
}
function info(msg: string) {
  console.log(`${ansi.blue}${ICON.info} ${msg}${ansi.reset}`);
}
function success(msg: string) {
  console.log(`${ansi.green}${ICON.ok} ${msg}${ansi.reset}`);
}
function warn(msg: string) {
  console.log(`${ansi.yellow}${ICON.warn} ${msg}${ansi.reset}`);
}
function errorLog(msg: string) {
  console.log(`${ansi.red}${ICON.fail} ${msg}${ansi.reset}`);
}
function bullet(msg: string) {
  console.log(`${ansi.gray}  • ${msg}${ansi.reset}`);
}

// ---------- spinners ----------
const frames = ['⠋','⠙','⠹','⠸','⠼','⠴','⠦','⠧','⠇','⠏'];
type Spinner = { timer?: NodeJS.Timeout; i: number; text: string; renderLen?: number };
let activeSpinner: Spinner | null = null;
function stripAnsi(s: string): string {
  return s.replace(/\x1B\[[0-9;]*m/g, '');
}
function startSpinner(text: string): Spinner {
  if (activeSpinner) {
    stopSpinner(activeSpinner);
  }
  const s: Spinner = { i: 0, text, renderLen: 0 };
  s.timer = setInterval(() => {
    const frame = frames[(s.i = (s.i + 1) % frames.length)];
    const line = `${ansi.cyan}${frame} ${ansi.reset}${s.text}${ansi.reset}   `;
    process.stdout.write(`\r${line}`);
    s.renderLen = stripAnsi(line).length;
  }, 80);
  activeSpinner = s;
  return s;
}
function updateSpinner(s: Spinner, text: string) {
  s.text = text;
}
function stopSpinner(s: Spinner, finalText?: string) {
  if (s.timer) clearInterval(s.timer);
  const len = s.renderLen || 0;
  if (len > 0) {
    process.stdout.write('\r' + ' '.repeat(len) + '\r');
  } else {
    process.stdout.write('\r');
  }
  if (finalText) {
    console.log(finalText);
  }
  if (activeSpinner === s) {
    activeSpinner = null;
  }
}

// ---------- concurrency helper ----------
async function runWithConcurrency<I, O>(items: I[], limit: number, worker: (item: I, index: number) => Promise<O>): Promise<O[]> {
  const results: O[] = new Array(items.length) as O[];
  let idx = 0;
  async function next(): Promise<void> {
    const current = idx++;
    if (current >= items.length) return;
    results[current] = await worker(items[current], current);
    return next();
  }
  const starters = Array.from({ length: Math.min(limit, items.length) }, () => next());
  await Promise.all(starters);
  return results;
}

type GrantMode = 'random' | 'code-only' | 'all';

type ProvisionConfig = {
  namePrefix?: string;
  domains: number;
  applicationsPerDomain: number;
  usersPerDomain: number;
  idp?: 'default' | 'mongo';
  features?: string[];
  grantTypes?: GrantMode;
  scopes?: string[];
};

type CreatedSummary = {
  domains: Array<{
    id: string;
    name: string;
    applications: Array<{ id: string; clientId: string; name: string }>;
    users: number;
    idp?: string;
  }>;
};

function readConfig(filePath: string): ProvisionConfig {
  const abs = path.isAbsolute(filePath) ? filePath : path.resolve(process.cwd(), filePath);
  const raw = fs.readFileSync(abs, 'utf-8');
  const cfg = JSON.parse(raw);
  return cfg as ProvisionConfig;
}

function buildName(prefix: string, parts: Array<string | number | undefined>): string {
  return [prefix, ...parts].filter(Boolean).join('-');
}

function pickGrantTypes(mode: GrantMode | undefined): string[] {
  const all = ['authorization_code', 'implicit', 'password', 'client_credentials', 'refresh_token'];
  if (mode === 'all') return all;
  if (mode === 'code-only') return ['authorization_code', 'refresh_token'];
  // random
  const choices = [...all];
  const count = Math.max(2, Math.floor(Math.random() * choices.length));
  const picked: string[] = [];
  while (picked.length < count && choices.length > 0) {
    const idx = Math.floor(Math.random() * choices.length);
    picked.push(choices.splice(idx, 1)[0]);
  }
  if (!picked.includes('authorization_code')) picked.push('authorization_code');
  if (!picked.includes('refresh_token')) picked.push('refresh_token');
  return Array.from(new Set(picked));
}

async function ensureIdp(domainId: string, accessToken: string, kind?: string) {
  if (!kind || kind === 'default') return { idp: undefined };
  if (kind === 'mongo') {
    const idp = await createMongoIdp(domainId, accessToken);
    return { idp: idp?.id || 'mongo' };
  }
  console.warn(`Unsupported idp kind "${kind}", skipping`);
  return { idp: undefined };
}

export async function provision(configPath: string, verify: boolean) {
  const startedAt = Date.now();
  const cfg = readConfig(configPath);

  // Provide sensible defaults so users can run without exporting env vars
  process.env.AM_MANAGEMENT_URL = process.env.AM_MANAGEMENT_URL || 'http://localhost:8093';
  process.env.AM_MANAGEMENT_ENDPOINT =
    process.env.AM_MANAGEMENT_ENDPOINT || `${process.env.AM_MANAGEMENT_URL}`;
  process.env.AM_DEF_ORG_ID = process.env.AM_DEF_ORG_ID || 'DEFAULT';
  process.env.AM_DEF_ENV_ID = process.env.AM_DEF_ENV_ID || 'DEFAULT';
  process.env.AM_ADMIN_USERNAME = process.env.AM_ADMIN_USERNAME || 'admin';
  process.env.AM_ADMIN_PASSWORD = process.env.AM_ADMIN_PASSWORD || 'adminadmin';

  banner(`${ICON.rocket} Provisioning`);
  section('Configuration');
  bullet(`Management URL: ${process.env.AM_MANAGEMENT_URL}`);
  bullet(`Org/Env: ${process.env.AM_DEF_ORG_ID}/${process.env.AM_DEF_ENV_ID}`);
  bullet(`Prefix: ${cfg.namePrefix || 'prov'}`);
  bullet(`Domains: ${cfg.domains}`);
  bullet(`Apps per domain: ${cfg.applicationsPerDomain}`);
  bullet(`Users per domain: ${cfg.usersPerDomain}`);
  bullet(`IDP: ${cfg.idp || 'default'}`);
  bullet(`Grant types: ${cfg.grantTypes || 'random'}`);

  section('Authentication');
  const requiredEnv = [
    'AM_MANAGEMENT_URL',
    'AM_MANAGEMENT_ENDPOINT',
    'AM_DEF_ORG_ID',
    'AM_DEF_ENV_ID',
    'AM_ADMIN_USERNAME',
    'AM_ADMIN_PASSWORD',
  ];
  for (const key of requiredEnv) {
    if (!process.env[key]) {
      throw new Error(`Missing environment variable: ${key}`);
    }
  }

  const accessToken = await requestAdminAccessToken();
  success('Admin access token acquired');
  const namePrefix = cfg.namePrefix || 'prov';
  const runTag = new Date().toISOString().replace(/[:.]/g, '');

  const summary: CreatedSummary = { domains: [] };

  // 1) Create and start all domains first
  section('Create and start domains');
  const createdDomains: Array<{ id: string; name: string; ordinal: number }> = [];
  for (let d = 1; d <= cfg.domains; d++) {
    const domainName = buildName(namePrefix, ['domain', runTag, d]);
    info(`Creating domain: ${domainName}`);
    const domain = await getDomainApi(accessToken).createDomain({
      organizationId: process.env.AM_DEF_ORG_ID!,
      environmentId: process.env.AM_DEF_ENV_ID!,
      newDomain: { name: domainName, description: 'Provisioned by provision.ts', dataPlaneId: 'default' },
    });
    const domainId = (domain as any).id;
    info(`Starting domain: ${domainName} (${domainId})`);
    await getDomainApi(accessToken).patchDomain({
      organizationId: process.env.AM_DEF_ORG_ID!,
      environmentId: process.env.AM_DEF_ENV_ID!,
      domain: domainId,
      patchDomain: { enabled: true },
    });
    const domainAfterStart = await getDomainApi(accessToken).findDomain({
      organizationId: process.env.AM_DEF_ORG_ID!,
      environmentId: process.env.AM_DEF_ENV_ID!,
      domain: domainId,
    });
    if (!(domainAfterStart as any).enabled) {
      throw new Error(`Domain ${domainName} (${domainId}) failed to enable`);
    }
    success(`Domain ready: ${domainName} (${domainId})`);
    createdDomains.push({ id: domainId, name: domainName, ordinal: d });
  }

  // 2) Wait for domains to be ready (5-10s); use 10s to be safe
  section('Readiness wait');
  const waitText = `${ICON.hourglass} Waiting 10s for domains to be ready...`;
  const waitSpin = startSpinner(waitText);
  await new Promise((r) => setTimeout(r, 10000));
  stopSpinner(waitSpin, `${ansi.green}${ICON.ok} Domains are ready${ansi.reset}`);
  success('Wait complete');

  // 3) For each domain, create IDP, applications and users
  section('Populate domains (IDP, applications, users)');
  for (const dom of createdDomains) {
    const domainId = dom.id;
    const d = dom.ordinal;
    info(`Domain: ${dom.name} (${domainId})`);
    const { idp } = await ensureIdp(domainId, accessToken, cfg.idp);
    if (idp) {
      success(`IDP created: ${idp}`);
    } else {
      warn('No IDP created (default)');
    }

    const apps: Array<{ id: string; clientId: string; name: string }> = [];
    if (cfg.applicationsPerDomain > 0) {
      const totalApps = cfg.applicationsPerDomain;
      let createdCount = 0;
      const appSpin = startSpinner(`Creating ${totalApps} application(s): 0/${totalApps}`);
      const appIdxs = Array.from({ length: totalApps }, (_, i) => i + 1);
      const createdApps = await runWithConcurrency(
        appIdxs,
        Math.min(5, totalApps),
        async (a) => {
          // Readable single-word app name; clientId matches name; simple, memorable clientSecret
          const appName = `${namePrefix}app${d}${a}`;
          const clientId = appName;
          const grantTypes = pickGrantTypes(cfg.grantTypes || 'random');
          const newAppBody = {
            name: appName,
            type: 'web',
            clientId: clientId,
            clientSecret: 'test',
            redirectUris: [`https://example.com/callback/${clientId}`],
            settings: {
              oauth: {
                grantTypes: grantTypes,
                responseTypes: ['code'],
                applicationType: 'WEB',
                tokenEndpointAuthMethod: 'client_secret_basic',
              },
            },
          } as any;
          const created = await getApplicationApi(accessToken).createApplication({
            organizationId: process.env.AM_DEF_ORG_ID!,
            environmentId: process.env.AM_DEF_ENV_ID!,
            domain: domainId,
            newApplication: newAppBody,
          });
          if (idp) {
            await getApplicationApi(accessToken).patchApplication({
              organizationId: process.env.AM_DEF_ORG_ID!,
              environmentId: process.env.AM_DEF_ENV_ID!,
              domain: domainId,
              application: (created as any).id,
              patchApplication: { identityProviders: [{ identity: idp, selectionRule: '', priority: 0 }] } as any,
            });
          }
          createdCount++;
          updateSpinner(appSpin, `Creating ${totalApps} application(s): ${createdCount}/${totalApps}`);
          return { id: (created as any).id, clientId, name: appName };
        },
      );
      stopSpinner(appSpin, `${ansi.green}${ICON.ok} Applications created: ${totalApps}/${totalApps}${ansi.reset}`);
      apps.push(...createdApps);
      for (const app of apps) {
        bullet(`App ${app.name} id=${app.id} clientId=${app.clientId}`);
      }
    }

    let usersCreated = 0;
    if (cfg.usersPerDomain > 0) {
      const total = cfg.usersPerDomain;
      const batchSize = Math.min(200, total); // safe bulk size
      const userSpin = startSpinner(`Creating ${total} user(s): 0/${total}`);
      const makeUser = (u: number) => {
        const username = buildName(namePrefix, ['user', runTag, d, u]);
        const email = `${username}@example.test`;
        const password = 'SomeP@ssw0rd';
        return {
          username,
          email,
          firstName: faker.name.firstName(),
          lastName: faker.name.lastName(),
          password,
          preRegistration: false,
          registrationCompleted: true,
          additionalInformation: {},
          source: idp, // ensure user is created in the selected IDP
        };
      };
      // Build all users then send in batches using bulk API
      const allUsers = Array.from({ length: total }, (_, idx) => makeUser(idx + 1));
      for (let start = 0; start < total; start += batchSize) {
        const batch = allUsers.slice(start, start + batchSize);
        updateSpinner(userSpin, `Creating ${total} user(s): ${Math.min(start + batch.length, total)}/${total}`);
        await getUserApi(accessToken).bulkUserOperation({
          organizationId: process.env.AM_DEF_ORG_ID!,
          environmentId: process.env.AM_DEF_ENV_ID!,
          domain: domainId,
          domainUserBulkRequest: { action: 'CREATE', items: batch, failOnErrors: 0 },
        });
        usersCreated += batch.length;
        updateSpinner(userSpin, `Creating ${total} user(s): ${usersCreated}/${total}`);
      }
      stopSpinner(userSpin, `${ansi.green}${ICON.ok} Users created: ${usersCreated}/${total}${ansi.reset}`);
    }

    if (cfg.features && cfg.features.length > 0) {
      const unsupported = cfg.features.filter((f) => !['mfa', 'ciba', 'ratelimit'].includes(f));
      if (unsupported.length > 0) {
        warn(`Some features are not supported yet and were skipped: ${unsupported.join(', ')}`);
      }
      const requested = cfg.features.filter((f) => ['mfa', 'ciba', 'ratelimit'].includes(f));
      if (requested.length > 0) {
        warn(`Feature flags requested (${requested.join(', ')}). Enabling requires additional config and was skipped in this initial version.`);
      }
    }

    summary.domains.push({
      id: domainId,
      name: dom.name,
      applications: apps,
      users: usersCreated,
      idp: idp,
    });
    success(`Completed domain: ${dom.name}`);
  }

  // Print summary
  section('Provisioning summary');
  for (const d of summary.domains) {
    console.log(`${ansi.bold}- Domain ${d.name} (${d.id})${ansi.reset}`);
    if (d.idp) bullet(`IDP: ${d.idp}`);
    bullet(`Users: ${d.users}`);
    bullet(`Applications (${d.applications.length}):`);
    for (const app of d.applications) {
      console.log(`${ansi.gray}    - ${app.name} (${app.id}) clientId=${app.clientId}${ansi.reset}`);
    }
  }
  success(`Done in ${Math.round((Date.now() - startedAt) / 1000)}s`);

  if (verify) {
    // Verify that the expected number of domains with the prefix exist
    const baseUrl = getDomainManagerUrl(null);
    let page = 0;
    const size = 50;
    let found = 0;
    while (true) {
      const res = await request(baseUrl)
        .get('')
        .query({ page, size })
        .set('Authorization', 'Bearer ' + accessToken)
        .expect(200);
      const body = res.body;
      const domains: any[] = Array.isArray(body) ? body : body?.data || body?.content || [];
      if (!domains || domains.length === 0) break;
      for (const dom of domains) {
        if ((dom.name || '').startsWith(namePrefix)) {
          found++;
        }
      }
      if (domains.length < size) break;
      page++;
    }
    info(`Verification: found ${found} domain(s) with prefix "${namePrefix}". Expected >= ${cfg.domains}.`);
    if (found < cfg.domains) {
      throw new Error(`Verification failed: expected at least ${cfg.domains} domains, found ${found}.`);
    }
    success('Verification passed');
  }
}

export async function purge(prefix: string, verify: boolean) {
  // Defaults aligned with provision()
  process.env.AM_MANAGEMENT_URL = process.env.AM_MANAGEMENT_URL || 'http://localhost:8093';
  process.env.AM_MANAGEMENT_ENDPOINT = process.env.AM_MANAGEMENT_ENDPOINT || `${process.env.AM_MANAGEMENT_URL}`;
  process.env.AM_DEF_ORG_ID = process.env.AM_DEF_ORG_ID || 'DEFAULT';
  process.env.AM_DEF_ENV_ID = process.env.AM_DEF_ENV_ID || 'DEFAULT';
  process.env.AM_ADMIN_USERNAME = process.env.AM_ADMIN_USERNAME || 'admin';
  process.env.AM_ADMIN_PASSWORD = process.env.AM_ADMIN_PASSWORD || 'adminadmin';

  const accessToken = await requestAdminAccessToken();
  const api = getDomainApi(accessToken);

  banner(`${ICON.broom} Purge`);
  section('Configuration');
  bullet(`Management URL: ${process.env.AM_MANAGEMENT_URL}`);
  bullet(`Org/Env: ${process.env.AM_DEF_ORG_ID}/${process.env.AM_DEF_ENV_ID}`);
  bullet(`Prefix: ${prefix}`);

  let page = 0;
  const size = 50;
  let deleted = 0;
  // Iterate over pages, filter by prefix, delete
  // Stop when a page returns less than 'size' items
  while (true) {
    const res = await request(getDomainManagerUrl(null))
      .get('')
      .query({ page, size })
      .set('Authorization', 'Bearer ' + accessToken)
      .expect(200);
    const body = res.body;
    const domains: any[] = Array.isArray(body) ? body : body?.data || body?.content || [];
    if (!domains || domains.length === 0) break;
    for (const d of domains) {
      const name = d.name || '';
      if (name.startsWith(prefix)) {
        info(`Deleting domain ${name} (${d.id})`);
        await api.deleteDomain({
          organizationId: process.env.AM_DEF_ORG_ID!,
          environmentId: process.env.AM_DEF_ENV_ID!,
          domain: d.id as string,
        });
        deleted++;
      }
    }
    if (domains.length < size) break;
    page++;
  }
  success(`Purged ${deleted} domain(s) with prefix "${prefix}".`);

  if (verify) {
    // Confirm no domains remain with the prefix
    let page = 0;
    const size = 50;
    let found = 0;
    while (true) {
      const res = await request(getDomainManagerUrl(null))
        .get('')
        .query({ page, size })
        .set('Authorization', 'Bearer ' + accessToken)
        .expect(200);
      const body = res.body;
      const domains: any[] = Array.isArray(body) ? body : body?.data || body?.content || [];
      if (!domains || domains.length === 0) break;
      for (const dom of domains) {
        if ((dom.name || '').startsWith(prefix)) {
          found++;
        }
      }
      if (domains.length < size) break;
      page++;
    }
    info(`Verification after purge: found ${found} domain(s) with prefix "${prefix}". Expected 0.`);
    if (found !== 0) {
      throw new Error(`Verification failed: expected 0 domains after purge, found ${found}.`);
    }
    success('Verification after purge passed');
  }
}

if (require.main === module) {
  (async () => {
    const args = process.argv.slice(2);
    const isPurge = args.includes('--purge');
    const verify = args.includes('--verify');
    try {
      if (isPurge) {
        const prefixArgIndex = args.findIndex((a) => a === '--prefix');
        const prefix = prefixArgIndex >= 0 && args[prefixArgIndex + 1] ? args[prefixArgIndex + 1] : 'prov';
        await purge(prefix, verify);
      } else {
        const configArg = args[0];
        if (!configArg) {
          console.error('Usage: npm run provision -- <config.json>');
          process.exit(1);
        }
        await provision(configArg, verify);
      }
      process.exit(0);
    } catch (err) {
      console.error('Provisioning failed:', err);
      process.exit(1);
    }
  })();
}


