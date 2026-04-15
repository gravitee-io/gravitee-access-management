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
import { Domain } from '@management-models/Domain';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

const ORG_ID = process.env.AM_DEF_ORG_ID;
const ENV_ID = process.env.AM_DEF_ENV_ID;

export interface BlueprintFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  cleanUp: () => Promise<void>;
  createBlueprintApp: (agentType: string, name?: string, redirectUri?: string) => Promise<any>;
  createRawApp: (body: any) => Promise<Response>;
  getApp: (appId: string) => Promise<any>;
  deleteApp: (appId: string) => Promise<void>;
  addAgentKey: (appId: string, jwk: any) => Promise<Response>;
  removeAgentKey: (appId: string, kid: string) => Promise<Response>;
  listAgentKeys: (appId: string) => Promise<any>;
}

function managementUrl(path: string): string {
  return `${process.env.AM_MANAGEMENT_URL}/management/organizations/${ORG_ID}/environments/${ENV_ID}${path}`;
}

export const setupBlueprintFixture = async (): Promise<BlueprintFixture> => {
  let domain: Domain | null = null;
  const createdAppIds: string[] = [];
  const accessToken = await requestAdminAccessToken();

  try {
    const domainResult = await setupDomainForTest(uniqueName('blueprint', true), { accessToken });
    domain = domainResult.domain;

    const createRawApp = async (body: any): Promise<Response> => {
      return fetch(managementUrl(`/domains/${domain.id}/applications`), {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(body),
      });
    };

    const createBlueprintApp = async (agentType: string, name?: string, redirectUri?: string): Promise<any> => {
      const appName = name || uniqueName(`agent-${agentType.toLowerCase()}`, true);
      const body: any = {
        name: appName,
        type: 'AGENT',
        agentSettings: { agentType },
      };
      if (redirectUri || agentType !== 'AUTONOMOUS') {
        body.redirectUris = [redirectUri || 'https://agent.example.com/callback'];
      }

      const response = await createRawApp(body);
      if (!response.ok) {
        const error = await response.text();
        throw new Error(`Failed to create blueprint app: ${response.status} ${error}`);
      }
      const app = await response.json();
      createdAppIds.push(app.id);
      return app;
    };

    const getApp = async (appId: string): Promise<any> => {
      const response = await fetch(managementUrl(`/domains/${domain.id}/applications/${appId}`), {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      return response.json();
    };

    const deleteApp = async (appId: string): Promise<void> => {
      await fetch(managementUrl(`/domains/${domain.id}/applications/${appId}`), {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${accessToken}` },
      });
    };

    const addAgentKey = async (appId: string, jwk: any): Promise<Response> => {
      return fetch(managementUrl(`/domains/${domain.id}/applications/${appId}/agent/keys`), {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(jwk),
      });
    };

    const removeAgentKey = async (appId: string, kid: string): Promise<Response> => {
      return fetch(managementUrl(`/domains/${domain.id}/applications/${appId}/agent/keys/${kid}`), {
        method: 'DELETE',
        headers: { Authorization: `Bearer ${accessToken}` },
      });
    };

    const listAgentKeys = async (appId: string): Promise<any> => {
      const response = await fetch(managementUrl(`/domains/${domain.id}/applications/${appId}/agent/keys`), {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      return response.json();
    };

    const cleanUp = async () => {
      for (const appId of createdAppIds) {
        try {
          await deleteApp(appId);
        } catch (e) {
          // best effort
        }
      }
      if (domain?.id) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };

    return {
      accessToken,
      domain,
      cleanUp,
      createBlueprintApp,
      createRawApp,
      getApp,
      deleteApp,
      addAgentKey,
      removeAgentKey,
      listAgentKeys,
    };
  } catch (error) {
    if (domain?.id) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (e) {
        console.error('Failed to cleanup after setup failure:', e);
      }
    }
    throw error;
  }
};
