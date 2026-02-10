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
import { expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { setupDomainForTest, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { createApplication } from '@management-commands/application-management-commands';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export interface AgentApplicationFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  createAgentApp: (name?: string) => Promise<Application>;
  cleanup: () => Promise<void>;
}

export const AGENT_APP_TEST = {
  DOMAIN_NAME_PREFIX: 'agent-app',
  APP_TYPE: 'AGENT' as const,
  APP_DESCRIPTION: 'Test agent application',
  REDIRECT_URI: 'https://callback',
} as const;

export const setupAgentApplicationFixture = async (): Promise<AgentApplicationFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    const domainResult = await setupDomainForTest(uniqueName(AGENT_APP_TEST.DOMAIN_NAME_PREFIX, true), { accessToken });
    domain = domainResult.domain;
    expect(domain).toBeDefined();
    expect(domain.id).toBeDefined();

    const createAgentApp = async (name?: string): Promise<Application> => {
      const app = await createApplication(domain!.id, accessToken!, {
        name: name ?? uniqueName('agent-app', true),
        type: AGENT_APP_TEST.APP_TYPE,
        description: AGENT_APP_TEST.APP_DESCRIPTION,
        redirectUris: [AGENT_APP_TEST.REDIRECT_URI],
      });
      expect(app).toBeDefined();
      return app;
    };

    const cleanup = async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    };

    return {
      domain,
      accessToken,
      createAgentApp,
      cleanup,
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
