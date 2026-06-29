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
import { uniqueName } from '@utils-commands/misc';
import {
  createProtectedResource,
  deleteProtectedResource,
  getProtectedResourceFlows,
  updateProtectedResourceFlows,
} from '@management-commands/protected-resources-management-commands';
import { Flow, ProtectedResourcePrimaryData } from '@management-models/index';

export class ProtectedResourceFlowsFixture {
  domain: any;
  accessToken: string;
  mcpResource: ProtectedResourcePrimaryData;

  async setup() {
    this.accessToken = await requestAdminAccessToken();
    const domainData = await setupDomainForTest(uniqueName('pr-flows', true), {
      accessToken: this.accessToken,
      waitForStart: true,
    });
    this.domain = domainData.domain;

    this.mcpResource = await createProtectedResource(this.domain.id, this.accessToken, {
      name: uniqueName('mcp-resource'),
      type: 'MCP_SERVER',
      resourceIdentifiers: ['https://example.com/mcp'],
    });
  }

  async cleanup() {
    if (this.mcpResource?.id) {
      await deleteProtectedResource(this.domain.id, this.accessToken, this.mcpResource.id, 'MCP_SERVER');
    }
    if (this.domain?.id) {
      await safeDeleteDomain(this.domain.id, this.accessToken);
    }
  }

  getFlows() {
    return getProtectedResourceFlows(this.domain.id, this.accessToken, this.mcpResource.id);
  }

  updateFlows(flows: Flow[]) {
    return updateProtectedResourceFlows(this.domain.id, this.accessToken, this.mcpResource.id, flows);
  }
}

export const setupProtectedResourceFlowsFixture = async (): Promise<ProtectedResourceFlowsFixture> => {
  const fixture = new ProtectedResourceFlowsFixture();
  await fixture.setup();
  return fixture;
};
