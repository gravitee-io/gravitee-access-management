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
import { getDomainApi } from '@management-commands/service/utils';
import { section, info, success, ICON, ansi } from '../logger';

export type CreatedDomain = { id: string; name: string; ordinal: number };

/**
 * Create and start a number of domains, returning their identifiers and ordinals.
 * @param accessToken Management API access token
 * @param orgId Organization id
 * @param envId Environment id
 * @param namePrefix Resource name prefix
 * @param runTag Unique run tag to ensure predictable uniqueness
 * @param domainCount Number of domains to create
 */
export async function createAndStartDomains(
  accessToken: string,
  orgId: string,
  envId: string,
  namePrefix: string,
  runTag: string,
  domainCount: number,
): Promise<CreatedDomain[]> {
  section('Create and start domains');
  const api = getDomainApi(accessToken);
  const created: CreatedDomain[] = [];

  for (let d = 1; d <= domainCount; d++) {
    // Short, readable domain name: <prefix>-d<idx>-<shortTag>
    const shortTag = (runTag || '').replace(/[^0-9A-Za-z]/g, '').slice(-6);
    const domainName = `${namePrefix}-d${d}-${shortTag || d}`;
    info(`Creating domain: ${domainName}`);

    const domain = await api.createDomain({
      organizationId: orgId,
      environmentId: envId,
      newDomain: { name: domainName, description: 'Provisioned by provision.ts', dataPlaneId: 'default' },
    });

    const domainId = (domain as any).id;
    info(`Starting domain: ${domainName} (${domainId})`);

    await api.patchDomain({ organizationId: orgId, environmentId: envId, domain: domainId, patchDomain: { enabled: true } });

    const started = await api.findDomain({ organizationId: orgId, environmentId: envId, domain: domainId });
    if (!(started as any).enabled) {
      throw new Error(`Domain ${domainName} (${domainId}) failed to enable`);
    }

    success(`Domain ready: ${domainName} (${domainId})`);
    created.push({ id: domainId, name: domainName, ordinal: d });
  }

  return created;
}

/**
 * Wait a fixed delay to allow newly started domains to become ready.
 * Use this when you don't need to poll a health endpoint.
 * @param ms Milliseconds to wait
 */
export async function waitForDomainsReady(ms: number) {
  section('Readiness wait');

  const { startSpinner, stopSpinner } = await import('../logger');
  const spin = startSpinner(`${ICON.hourglass} Waiting ${Math.floor(ms / 1000)}s for domains to be ready...`);
  await new Promise((r) => setTimeout(r, ms));

  stopSpinner(spin, `${ansi.green}${ICON.ok} Domains are ready${ansi.reset}`);
  success('Wait complete');
}
