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
import { getDomainFlows, updateDomainFlows } from '@management-commands/domain-management-commands';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { FlowEntityTypeEnum } from '../../../api/management/models';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { getBase64BasicAuth } from '@gateway-commands/utils';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { jira } from '@specs-utils/jira';
import { setup } from '../../test-fixture';
import { FlowPolicyFixture, setupFlowPolicyFixture } from './fixture/flow-policy-fixture';

setup(200000);

const JWT_FORMAT = /^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$/;

describe('Flow Policy Execution', () => {
  let fixture: FlowPolicyFixture;

  beforeAll(async () => {
    fixture = await setupFlowPolicyFixture();
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  it(jira`should execute a single policy in a login flow ${'AM-2199'}`, async () => {
    // Add Groovy policy to LOGIN flow pre-step
    const flows = await getDomainFlows(fixture.domainId, fixture.accessToken);
    lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'pre', [
      {
        name: 'Groovy',
        policy: 'groovy',
        description: 'Set groovy attribute on user',
        enabled: true,
        configuration: JSON.stringify({
          onRequestScript: "context.setAttribute('groovy-test', 'policy-executed');",
        }),
      },
    ]);
    lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'post', []);

    await waitForSyncAfter(fixture.domainId, () => updateDomainFlows(fixture.domainId, fixture.accessToken, flows));

    // Login — policy did not block login, valid JWT returned
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=password&username=${encodeURIComponent(fixture.user.username)}&password=${encodeURIComponent(fixture.user.password)}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
      },
    ).expect(200);

    expect(response.body.access_token).toMatch(JWT_FORMAT);
    expect(response.body.token_type).toEqual('bearer');
  });

  it(jira`should execute multiple policies in a login flow ${'AM-2198'}`, async () => {
    // Add TWO Groovy policies in sequence — both must execute without error
    const flows = await getDomainFlows(fixture.domainId, fixture.accessToken);
    lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'pre', [
      {
        name: 'Groovy First',
        policy: 'groovy',
        description: 'Set first attribute',
        enabled: true,
        configuration: JSON.stringify({
          onRequestScript: "context.setAttribute('groovy-first', 'ran');",
        }),
      },
      {
        name: 'Groovy Second',
        policy: 'groovy',
        description: 'Set second attribute using first',
        enabled: true,
        configuration: JSON.stringify({
          onRequestScript: "context.setAttribute('groovy-second', 'also-ran');",
        }),
      },
    ]);
    lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'post', []);

    await waitForSyncAfter(fixture.domainId, () => updateDomainFlows(fixture.domainId, fixture.accessToken, flows));

    // Login succeeds => both policies ran without error
    const response = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=password&username=${encodeURIComponent(fixture.user.username)}&password=${encodeURIComponent(fixture.user.password)}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + getBase64BasicAuth(fixture.clientId, fixture.clientSecret),
      },
    ).expect(200);

    expect(response.body.access_token).toMatch(JWT_FORMAT);
    expect(response.body.token_type).toEqual('bearer');
  });
});
