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
 * Optional environment variables (defaults shown):
 * - AM_MANAGEMENT_URL (default: http://localhost:8093)
 * - AM_MANAGEMENT_ENDPOINT (default: AM_MANAGEMENT_URL)
 * - AM_DEF_ORG_ID (default: DEFAULT)
 * - AM_DEF_ENV_ID (default: DEFAULT)
 * - AM_ADMIN_USERNAME (default: admin)
 * - AM_ADMIN_PASSWORD (default: adminadmin)
 *
 * Usage:
 *   npm run provision -- ./scripts/provision.example.json
 */

import fs from 'fs';
import path from 'path';

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getDomainApi, getDomainManagerUrl } from '@management-commands/service/utils';
import 'cross-fetch/polyfill';
import request from 'supertest';
import { banner, section, info, success, warn, bullet, ICON, ansi } from './provisioning/logger';
import { createAndStartDomains, waitForDomainsReady } from './provisioning/provisioners/domain-provisioner';
import { ensureIdp } from './provisioning/provisioners/idp-provisioner';
import { createAppsForDomain, CreatedApp } from './provisioning/provisioners/app-provisioner';
import { createUsersForDomain } from './provisioning/provisioners/user-provisioner';

function setupEnvDefaults() {
  process.env.AM_MANAGEMENT_URL ||= 'http://localhost:8093';
  process.env.AM_MANAGEMENT_ENDPOINT ||= process.env.AM_MANAGEMENT_URL;
  process.env.AM_DEF_ORG_ID ||= 'DEFAULT';
  process.env.AM_DEF_ENV_ID ||= 'DEFAULT';
  process.env.AM_ADMIN_USERNAME ||= 'admin';
  process.env.AM_ADMIN_PASSWORD ||= 'adminadmin';
}

async function* iterateDomains(accessToken: string, prefix: string, size = 50) {
  let page = 0;
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
        yield dom;
      }
    }
    if (domains.length < size) break;
    page++;
  }
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
    applications: CreatedApp[];
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

export async function provision(configPath: string, verify: boolean) {
  const startedAt = Date.now();
  const cfg = readConfig(configPath);

  // Provide sensible defaults so users can run without exporting env vars
  setupEnvDefaults();

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
  const createdDomains = await createAndStartDomains(
    accessToken,
    process.env.AM_DEF_ORG_ID!,
    process.env.AM_DEF_ENV_ID!,
    namePrefix,
    runTag,
    cfg.domains,
  );

  // 2) Wait for domains to be ready (5-10s); use 10s to be safe
  await waitForDomainsReady(10000);

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

    // Create applications
    const apps = await createAppsForDomain(
      accessToken,
      process.env.AM_DEF_ORG_ID!,
      process.env.AM_DEF_ENV_ID!,
      domainId,
      d,
      namePrefix,
      runTag,
      cfg.applicationsPerDomain,
      cfg.grantTypes,
      idp,
    );

    // Create users
    const usersCreated = await createUsersForDomain(
      accessToken,
      process.env.AM_DEF_ORG_ID!,
      process.env.AM_DEF_ENV_ID!,
      domainId,
      d,
      namePrefix,
      runTag,
      cfg.usersPerDomain,
      idp,
    );

    if (cfg.features && cfg.features.length > 0) {
      const unsupported = cfg.features.filter((f) => !['mfa', 'ciba', 'ratelimit'].includes(f));
      if (unsupported.length > 0) {
        warn(`Some features are not supported yet and were skipped: ${unsupported.join(', ')}`);
      }
      const requested = cfg.features.filter((f) => ['mfa', 'ciba', 'ratelimit'].includes(f));
      if (requested.length > 0) {
        warn(
          `Feature flags requested (${requested.join(', ')}). Enabling requires additional config and was skipped in this initial version.`,
        );
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
    let found = 0;
    for await (const _ of iterateDomains(accessToken, namePrefix)) {
      found++;
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
  setupEnvDefaults();

  const accessToken = await requestAdminAccessToken();
  const api = getDomainApi(accessToken);

  banner(`${ICON.broom} Purge`);
  section('Configuration');
  bullet(`Management URL: ${process.env.AM_MANAGEMENT_URL}`);
  bullet(`Org/Env: ${process.env.AM_DEF_ORG_ID}/${process.env.AM_DEF_ENV_ID}`);
  bullet(`Prefix: ${prefix}`);

  let deleted = 0;
  // Iterate over pages, filter by prefix, delete
  // Stop when a page returns less than 'size' items
  for await (const d of iterateDomains(accessToken, prefix)) {
    info(`Deleting domain ${d.name} (${d.id})`);
    await api.deleteDomain({
      organizationId: process.env.AM_DEF_ORG_ID!,
      environmentId: process.env.AM_DEF_ENV_ID!,
      domain: d.id as string,
    });
    deleted++;
  }
  success(`Purged ${deleted} domain(s) with prefix "${prefix}".`);

  if (verify) {
    // Confirm no domains remain with the prefix
    let found = 0;
    for await (const _ of iterateDomains(accessToken, prefix)) {
      found++;
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
