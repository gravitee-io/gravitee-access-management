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
import { toFlowPayload } from '@management-commands/protected-resources-management-commands';
import { setup } from '../test-fixture';
import { ProtectedResourceFlowsFixture, setupProtectedResourceFlowsFixture } from './fixtures/protected-resource-flows-fixture';

setup(120000);

describe('Protected resource (MCP server) flows management', () => {
  let fixture: ProtectedResourceFlowsFixture;

  beforeAll(async () => {
    fixture = await setupProtectedResourceFlowsFixture();
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanup();
    }
  });

  const tokenPolicy = {
    name: 'Groovy',
    policy: 'groovy',
    description: 'token flow policy',
    condition: '',
    enabled: true,
    configuration: JSON.stringify({ onRequestScript: "context.setAttribute('pr-token-flow', 'executed');" }),
  };

  it('should expose the token flow', async () => {
    const response = await fixture.getFlows().expect(200);
    const tokenFlow = response.body.find((f) => f.type.toLowerCase() === 'token');
    expect(tokenFlow).toBeDefined();
  });

  it('should persist a policy on the token flow', async () => {
    const flows = await fixture.getFlows().then((r) => r.body);
    const tokenFlow = flows.find((f) => f.type.toLowerCase() === 'token');
    tokenFlow.pre = [tokenPolicy];

    // Only the token flow may be submitted for a protected resource.
    const updated = await fixture.updateFlows([toFlowPayload(tokenFlow)]).expect(200);
    const persistedToken = updated.body.find((f) => f.type.toLowerCase() === 'token');
    expect(persistedToken.pre).toHaveLength(1);
    expect(persistedToken.pre[0].policy).toEqual('groovy');

    // Re-read to confirm persistence
    const reread = await fixture.getFlows().then((r) => r.body);
    const rereadToken = reread.find((f) => f.type.toLowerCase() === 'token');
    expect(rereadToken.pre).toHaveLength(1);
    expect(rereadToken.id).toBeDefined();
  });

  it('should reject a non-token flow', async () => {
    const loginFlow = { type: 'login', name: 'LOGIN', pre: [tokenPolicy], post: [], enabled: true };

    const response = await fixture.updateFlows([toFlowPayload(loginFlow)]).expect(400);
    expect(JSON.stringify(response.body)).toContain('TOKEN');
  });
});
