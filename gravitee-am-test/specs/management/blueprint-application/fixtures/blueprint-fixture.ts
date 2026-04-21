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
import {
  createDomain,
  DomainOidcConfig,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { Domain } from '@management-models/Domain';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

const ORG_ID = process.env.AM_DEF_ORG_ID;
const ENV_ID = process.env.AM_DEF_ENV_ID;

const AGENT_TYPE_TO_APPLICATION_TYPE: Record<string, string> = {
  USER_EMBEDDED: 'NATIVE',
  HOSTED_DELEGATED: 'WEB',
  AUTONOMOUS: 'SERVICE',
};

export interface BlueprintFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  /** Populated after {@link BlueprintFixture.waitForOidc} resolves. */
  oidc?: DomainOidcConfig;
  cleanUp: () => Promise<void>;
  /** Await domain start + OIDC well-known discovery; sets {@link oidc}. */
  waitForOidc: () => Promise<DomainOidcConfig>;
  createBlueprintApp: (
    agentType: string,
    name?: string,
    redirectUri?: string,
    tokenEndpointAuthMethod?: string,
  ) => Promise<any>;
  createRawApp: (body: any) => Promise<Response>;
  getApp: (appId: string) => Promise<any>;
  deleteApp: (appId: string) => Promise<void>;
  /** Registers a single JWK on the app's OAuth `jwks` field; used by tests that need to verify client assertions. */
  registerJwk: (appId: string, jwk: any) => Promise<Response>;
}

function managementUrl(path: string): string {
  return `${process.env.AM_MANAGEMENT_URL}/management/organizations/${ORG_ID}/environments/${ENV_ID}${path}`;
}

export const setupBlueprintFixture = async (): Promise<BlueprintFixture> => {
  let domain: Domain | null = null;
  const createdAppIds: string[] = [];
  const accessToken = await requestAdminAccessToken();

  try {
    // Create the domain without starting it so tests can create blueprint apps
    // first — that way the initial sync on startDomain picks them up, avoiding
    // post-start app-sync races. Tests that hit the gateway call
    // fixture.waitForOidc() after creating apps to start + wait for sync.
    domain = await createDomain(accessToken, uniqueName('blueprint', true), 'blueprint fixture domain');

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

    const createBlueprintApp = async (
      agentType: string,
      name?: string,
      redirectUri?: string,
      tokenEndpointAuthMethod?: string,
    ): Promise<any> => {
      const appName = name || uniqueName(`agent-${agentType.toLowerCase()}`, true);
      const body: any = {
        name: appName,
        type: AGENT_TYPE_TO_APPLICATION_TYPE[agentType],
        agentIdentityMode: true,
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
      let app = await response.json();
      createdAppIds.push(app.id);

      // Override the assertion-default auth method when a test exercises a
      // secret-based flow (e.g. basic auth against an AUTONOMOUS agent).
      if (tokenEndpointAuthMethod && tokenEndpointAuthMethod !== app.settings?.oauth?.tokenEndpointAuthMethod) {
        const patchResponse = await fetch(managementUrl(`/domains/${domain.id}/applications/${app.id}`), {
          method: 'PATCH',
          headers: {
            Authorization: `Bearer ${accessToken}`,
            'Content-Type': 'application/json',
          },
          body: JSON.stringify({ settings: { oauth: { tokenEndpointAuthMethod } } }),
        });
        if (!patchResponse.ok) {
          const error = await patchResponse.text();
          throw new Error(`Failed to patch tokenEndpointAuthMethod: ${patchResponse.status} ${error}`);
        }
        app = await patchResponse.json();
      }
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

    const registerJwk = async (appId: string, jwk: any): Promise<Response> => {
      return fetch(managementUrl(`/domains/${domain.id}/applications/${appId}`), {
        method: 'PATCH',
        headers: {
          Authorization: `Bearer ${accessToken}`,
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          settings: {
            oauth: { jwks: JSON.stringify({ keys: [jwk] }) },
          },
        }),
      });
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

    const fixture: BlueprintFixture = {
      accessToken,
      domain,
      cleanUp,
      createBlueprintApp,
      createRawApp,
      getApp,
      deleteApp,
      registerJwk,
      waitForOidc: async () => {
        if (fixture.oidc) {
          return fixture.oidc;
        }
        await startDomain(domain.id, accessToken);
        const started = await waitForDomainStart(domain);
        fixture.oidc = started.oidcConfig;
        return started.oidcConfig;
      },
    };
    return fixture;
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
