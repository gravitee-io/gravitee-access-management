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
import { listApplications } from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { ListApplicationsTypeEnum } from '../../../api/management/apis/ApplicationApi';
import { setup } from '../../test-fixture';
import { ApplicationsFixture, setupApplicationsFixture } from './fixtures/applications-fixture';

setup(180000);

// AGENT_TYPES + NON_AGENT_TYPES together must cover every ListApplicationsTypeEnum value
// — the Java ApplicationTypeClassificationTest enforces the same invariant for the server-side
// enum. Adding a value to either set here without adding it server-side (or vice versa) is the
// failure mode this spec is designed to catch alongside that Java tripwire.
const AGENT_TYPES: ListApplicationsTypeEnum[] = [ListApplicationsTypeEnum.Agent];
const NON_AGENT_TYPES: ListApplicationsTypeEnum[] = Object.values(ListApplicationsTypeEnum).filter(
  (t) => !AGENT_TYPES.includes(t),
);

let fixture: ApplicationsFixture;
const agentIds: string[] = [];
const nonAgentIds: string[] = [];
const pagingAgentIds: string[] = [];
const pagingNonAgentIds: string[] = [];

const trackAgent = async (domainId: string, name: string, store: string[]): Promise<Application> => {
  const created = await fixture.createAndTrackApplication(domainId, { name, type: 'AGENT', kind: 'AUTONOMOUS' });
  store.push(created.id!);
  return created;
};

const trackNonAgent = async (domainId: string, name: string, store: string[], type: string = 'SERVICE'): Promise<Application> => {
  const created = await fixture.createAndTrackApplication(domainId, { name, type });
  store.push(created.id!);
  return created;
};

beforeAll(async () => {
  fixture = await setupApplicationsFixture();
});

afterAll(async () => {
  await fixture.cleanUp();
});

describe('GET /applications with multi-valued type filter', () => {
  describe('with a mix of agents and non-agents on a single page', () => {
    beforeAll(async () => {
      const domainId = fixture.primaryDomain.id!;
      const run = `${Date.now()}`;
      await trackNonAgent(domainId, uniqueName(`${run}-svc-a`, true), nonAgentIds);
      await trackNonAgent(domainId, uniqueName(`${run}-svc-b`, true), nonAgentIds);
      await trackAgent(domainId, uniqueName(`${run}-agent-a`, true), agentIds);
      await trackAgent(domainId, uniqueName(`${run}-agent-b`, true), agentIds);
      await trackAgent(domainId, uniqueName(`${run}-agent-c`, true), agentIds);
    });

    it('returns only non-agent applications when called with the non-agent type set', async () => {
      const page = await listApplications(fixture.primaryDomain.id!, fixture.accessToken, { type: NON_AGENT_TYPES, size: 50 });
      const returnedIds = page.data.map((app) => app.id!);

      expect(returnedIds.sort()).toEqual([...nonAgentIds].sort());
      expect(returnedIds.some((id) => agentIds.includes(id))).toBe(false);
      expect(page.totalCount).toEqual(nonAgentIds.length);
    });

    it('excludes agents by default when no type filter is supplied', async () => {
      const page = await listApplications(fixture.primaryDomain.id!, fixture.accessToken, { size: 50 });
      const returnedIds = page.data.map((app) => app.id!);

      expect(returnedIds.sort()).toEqual([...nonAgentIds].sort());
      expect(returnedIds.some((id) => agentIds.includes(id))).toBe(false);
      expect(page.totalCount).toEqual(nonAgentIds.length);
    });

    it('returns only agents when called with the agent type set', async () => {
      const page = await listApplications(fixture.primaryDomain.id!, fixture.accessToken, { type: AGENT_TYPES, size: 50 });
      const returnedIds = page.data.map((app) => app.id!);

      expect(returnedIds.sort()).toEqual([...agentIds].sort());
      expect(page.totalCount).toEqual(agentIds.length);
    });
  });

  describe('cross-page totalCount accuracy', () => {
    beforeAll(async () => {
      const domainId = fixture.secondaryDomain.id!;
      // Interleave creation so each small page would contain a mix under an unfiltered query.
      for (let i = 0; i < 4; i++) {
        await trackAgent(domainId, uniqueName(`p-agent-${i}`, true), pagingAgentIds);
        await trackNonAgent(domainId, uniqueName(`p-svc-${i}`, true), pagingNonAgentIds);
      }
    });

    it('reports the true non-agent totalCount on every page (server-side filter)', async () => {
      const pageSize = 2;
      const collected: string[] = [];
      let pageIndex = 0;

      while (true) {
        const page = await listApplications(fixture.secondaryDomain.id!, fixture.accessToken, {
          type: NON_AGENT_TYPES,
          page: pageIndex,
          size: pageSize,
        });
        // totalCount must equal the true non-agent count on every page — proves the filter
        // is applied at the repository layer, not as a post-fetch strip on each page.
        expect(page.totalCount).toEqual(pagingNonAgentIds.length);

        if (page.data.length === 0) break;
        collected.push(...page.data.map((a) => a.id!));
        pageIndex += 1;
        if (pageIndex > 20) throw new Error('runaway pagination');
      }

      expect(collected.sort()).toEqual([...pagingNonAgentIds].sort());
      expect(collected.some((id) => pagingAgentIds.includes(id))).toBe(false);
    });

    it('returns full pages of `size` items (no short pages from post-filtering)', async () => {
      // 4 non-agents with size=2 should yield exactly 2 full pages.
      const first = await listApplications(fixture.secondaryDomain.id!, fixture.accessToken, {
        type: NON_AGENT_TYPES,
        page: 0,
        size: 2,
      });
      const second = await listApplications(fixture.secondaryDomain.id!, fixture.accessToken, {
        type: NON_AGENT_TYPES,
        page: 1,
        size: 2,
      });

      expect(first.data).toHaveLength(2);
      expect(second.data).toHaveLength(2);
      expect(first.totalCount).toEqual(pagingNonAgentIds.length);
      expect(second.totalCount).toEqual(pagingNonAgentIds.length);
    });
  });
});
