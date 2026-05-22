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

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { Application } from '@management-models/Application';
import { patchApplication } from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import { AgentApplicationsFixture, setupAgentApplicationsFixture } from './fixtures/agent-applications-fixture';

setup(180000);

const RUN = `${Date.now()}`;
const AUTONOMOUS_PREFIX = `${RUN}-autonomous`;
const USER_EMBEDDED_PREFIX = `${RUN}-user-embedded`;
const REGULAR_PREFIX = `${RUN}-regular`;
const SEARCH_PREFIX = `${RUN}-alpha`;

let fixture: AgentApplicationsFixture;
let autonomousAgent: Application;
let userEmbeddedAgent: Application;
let regularApp: Application;
let searchableAgent: Application;

beforeAll(async () => {
  fixture = await setupAgentApplicationsFixture();

  autonomousAgent = await fixture.createAgentApp(
    fixture.domain.id!,
    uniqueName(AUTONOMOUS_PREFIX, true),
    'AUTONOMOUS',
  );
  userEmbeddedAgent = await fixture.createAgentApp(
    fixture.domain.id!,
    uniqueName(USER_EMBEDDED_PREFIX, true),
    'USER_EMBEDDED',
  );
  searchableAgent = await fixture.createAgentApp(
    fixture.domain.id!,
    `${SEARCH_PREFIX}-${uniqueName('agent', true)}`,
    'AUTONOMOUS',
  );
  regularApp = await fixture.createRegularApp(
    fixture.domain.id!,
    uniqueName(REGULAR_PREFIX, true),
  );
});

afterAll(async () => {
  await fixture.cleanUp();
});

describe('GET /applications?type=AGENT', () => {
  it('returns only applications typed as AGENT', async () => {
    const page = await fixture.listAgents(fixture.domain.id!, { size: 100 });

    expect(page.totalCount).toEqual(3);
    expect(page.data).toHaveLength(3);

    const ids = page.data.map((app) => app.id).sort();
    expect(ids).toEqual([autonomousAgent.id!, userEmbeddedAgent.id!, searchableAgent.id!].sort());
    expect(ids).not.toContain(regularApp.id);
  });

  it('exposes filtered projection (id/name/type/enabled) for each agent', async () => {
    const page = await fixture.listAgents(fixture.domain.id!, { size: 100 });
    const item = page.data.find((app) => app.id === autonomousAgent.id);

    expect(item).toMatchObject({
      id: autonomousAgent.id,
      name: autonomousAgent.name,
      type: 'agent',
      enabled: true,
    });
  });

  it('filters by name via the q parameter and still excludes non-agents', async () => {
    const page = await fixture.listAgents(fixture.domain.id!, { q: `${SEARCH_PREFIX}*`, size: 50 });

    expect(page.data).toHaveLength(1);
    expect(page.data[0].id).toEqual(searchableAgent.id);
    expect(page.data[0].type).toEqual('agent');
  });

  it('returns an empty page when the domain has no agents', async () => {
    const page = await fixture.listAgents(fixture.emptyDomain.id!, { size: 50 });

    expect(page.totalCount).toEqual(0);
    expect(page.data).toEqual([]);
  });

  it('honours pagination (size=1 returns one item per page)', async () => {
    const firstPage = await fixture.listAgents(fixture.domain.id!, { page: 0, size: 1 });
    const secondPage = await fixture.listAgents(fixture.domain.id!, { page: 1, size: 1 });

    expect(firstPage.data).toHaveLength(1);
    expect(secondPage.data).toHaveLength(1);
    expect(firstPage.totalCount).toEqual(3);
    expect(secondPage.totalCount).toEqual(3);
    expect(firstPage.data[0].id).not.toEqual(secondPage.data[0].id);
  });

  it('rejects requests without a bearer token with 401', async () => {
    const response = await fixture.listAgentsRaw(fixture.domain.id!, '', { size: 1 });
    expect(response.status).toEqual(401);
  });
});

describe('PATCH /applications/:id template flag', () => {
  it('allows an agent application to be marked as a template', async () => {
    const agent = await fixture.createAgentApp(
      fixture.domain.id!,
      uniqueName(`${RUN}-template-toggle`, true),
      'AUTONOMOUS',
    );
    expect(agent.template).toBeFalsy();

    const patched = await patchApplication(
      fixture.domain.id!,
      fixture.accessToken,
      { template: true } as any,
      agent.id!,
    );

    expect(patched.template).toEqual(true);
  });
});
