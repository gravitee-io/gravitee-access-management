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

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { createApplication, deleteApplication } from '@management-commands/application-management-commands';
import { Application } from '@management-models/Application';
import { Domain } from '@management-models/Domain';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export type AgentType = 'AUTONOMOUS' | 'USER_EMBEDDED' | 'HOSTED_DELEGATED';

const AGENT_TYPE_TO_APP_TYPE: Record<AgentType, string> = {
  AUTONOMOUS: 'SERVICE',
  USER_EMBEDDED: 'NATIVE',
  HOSTED_DELEGATED: 'WEB',
};

export interface AgentApplicationsFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  emptyDomain: Domain;
  createdApplicationIds: Record<string, string[]>;
  createAgentApp: (domainId: string, name: string, agentType?: AgentType) => Promise<Application>;
  createRegularApp: (domainId: string, name: string) => Promise<Application>;
  cleanUp: () => Promise<void>;
}

export const setupAgentApplicationsFixture = async (): Promise<AgentApplicationsFixture> => {
  const accessToken = await requestAdminAccessToken();
  const domain = await setupDomainForTest(uniqueName('agent-apps', true), { accessToken }).then((it) => it.domain);
  const emptyDomain = await setupDomainForTest(uniqueName('agent-apps-empty', true), { accessToken }).then((it) => it.domain);

  const createdApplicationIds: Record<string, string[]> = {};

  const track = (domainId: string, app: Application) => {
    if (!app?.id) return;
    if (!createdApplicationIds[domainId]) createdApplicationIds[domainId] = [];
    createdApplicationIds[domainId].push(app.id);
  };

  const createAgentApp = async (domainId: string, name: string, agentType: AgentType = 'AUTONOMOUS'): Promise<Application> => {
    const payload: any = {
      name,
      type: AGENT_TYPE_TO_APP_TYPE[agentType],
      agentIdentityMode: true,
      agentSettings: { agentType },
    };
    if (agentType !== 'AUTONOMOUS') {
      payload.redirectUris = ['https://agent.example.com/callback'];
    }
    const created = await createApplication(domainId, accessToken, payload);
    track(domainId, created);
    return created;
  };

  const createRegularApp = async (domainId: string, name: string): Promise<Application> => {
    const payload: any = {
      name,
      type: 'SERVICE',
    };
    const created = await createApplication(domainId, accessToken, payload);
    track(domainId, created);
    return created;
  };

  const cleanUp = async () => {
    for (const domainId of Object.keys(createdApplicationIds)) {
      for (const appId of createdApplicationIds[domainId]) {
        try {
          await deleteApplication(domainId, accessToken, appId);
        } catch (err) {
          // domain delete will cascade anyway
        }
      }
    }
    if (domain?.id) {
      await safeDeleteDomain(domain.id, accessToken);
    }
    if (emptyDomain?.id) {
      await safeDeleteDomain(emptyDomain.id, accessToken);
    }
  };

  return {
    accessToken,
    domain,
    emptyDomain,
    createdApplicationIds,
    createAgentApp,
    createRegularApp,
    cleanUp,
  };
};
