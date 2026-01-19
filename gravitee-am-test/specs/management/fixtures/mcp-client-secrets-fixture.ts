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
import { safeDeleteDomain, patchDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { delay, uniqueName } from '@utils-commands/misc';
import {
  createProtectedResource,
  deleteProtectedResource,
  createMcpClientSecret,
  deleteMcpClientSecret,
  listMcpClientSecrets,
  renewMcpClientSecret,
} from '@management-commands/protected-resources-management-commands';
import { ProtectedResourcePrimaryData } from '@management-models/index';

export class McpClientSecretsFixture {
  domain: any;
  accessToken: string;
  mcpResource: ProtectedResourcePrimaryData;
  openIdConfiguration: any;

  async setup() {
    this.accessToken = await requestAdminAccessToken();
    const domainData = await setupDomainForTest(uniqueName('mcp-client-secret-domain', true), {
      accessToken: this.accessToken,
      waitForStart: true,
    });
    this.domain = domainData.domain;
    this.openIdConfiguration = domainData.oidcConfig;

    // Enable DCR/Resource functionality if needed
    this.domain = await patchDomain(this.domain.id, this.accessToken, {
      oidc: {
        clientRegistrationSettings: {
          isDynamicClientRegistrationEnabled: true,
          isOpenDynamicClientRegistrationEnabled: true,
        },
      },
    });

    const mcpResourceName = uniqueName('mcp-resource');
    try {
      this.mcpResource = await createProtectedResource(this.domain.id, this.accessToken, {
        name: mcpResourceName,
        type: 'MCP_SERVER',
        resourceIdentifiers: ['https://example.com'],
      });
    } catch (e: any) {
      console.error('Failed to create fixture protected resource:', e);
      throw e;
    }
    await delay(6000); // Wait for sync
  }

  async cleanup() {
    if (this.mcpResource && this.mcpResource.id) {
      await deleteProtectedResource(this.domain.id, this.accessToken, this.mcpResource.id, 'MCP_SERVER');
    }
    if (this.domain && this.domain.id) {
      await safeDeleteDomain(this.domain.id, this.accessToken);
    }
  }

  async listSecrets() {
    return listMcpClientSecrets(this.domain.id, this.accessToken, this.mcpResource.id);
  }

  async createSecret(name: string) {
    return createMcpClientSecret(this.domain.id, this.accessToken, this.mcpResource.id, { name });
  }

  async renewSecret(secretId: string) {
    return renewMcpClientSecret(this.domain.id, this.accessToken, this.mcpResource.id, secretId);
  }

  async deleteSecret(secretId: string) {
    return deleteMcpClientSecret(this.domain.id, this.accessToken, this.mcpResource.id, secretId);
  }

  async setDomainSecretSettings(settings: any) {
    this.domain = await patchDomain(this.domain.id, this.accessToken, {
      secretSettings: settings,
    });
  }
}

export const setupMcpClientSecretsFixture = async (): Promise<McpClientSecretsFixture> => {
  const fixture = new McpClientSecretsFixture();
  await fixture.setup();
  return fixture;
};
