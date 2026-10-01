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

import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import {
  DomainOidcConfig,
  getDomainFlows,
  safeDeleteDomain,
  setupDomainForTest,
  updateDomainFlows,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';
import { FlowEntityTypeEnum } from '../../../../api/management/models';

export type LatencyTimeUnit = 'MILLISECONDS' | 'SECONDS';

/** The request parameter the request validation rule looks at. */
export const OPTIONAL_PARAMETER = 'optional-param';

/** A Latency policy holding the request for the given duration. */
export const latencyPolicy = (time: number, timeUnit: LatencyTimeUnit) => ({
  name: 'Latency',
  policy: 'latency',
  description: '',
  condition: '',
  enabled: true,
  configuration: JSON.stringify({ time, timeUnit }),
});

/**
 * A Validate Request rule on the parameter: the value has to be made of lowercase letters.
 *
 * When the parameter is not required the rule is checked only if the parameter is present in the
 * request, a missing parameter being null. When it is required, a missing parameter is refused.
 */
export const parameterValidationPolicy = (status: string, isRequired: boolean) => ({
  name: 'Validate Request',
  policy: 'policy-request-validation',
  description: '',
  condition: '',
  enabled: true,
  configuration: JSON.stringify({
    status,
    rules: [
      {
        input: `{#request.params['${OPTIONAL_PARAMETER}']}`,
        isRequired,
        constraint: { type: 'PATTERN', parameters: ['^[a-z]+$'] },
      },
    ],
  }),
});

export interface TimedTokenResponse {
  status: number;
  body: any;
  /** Time between sending the token request and receiving its response. */
  elapsedMs: number;
}

export interface TokenFlowPoliciesFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  openIdConfiguration: DomainOidcConfig;
  /** Backend to backend application, authenticating with the client_credentials grant. */
  application: Application;
  /** Replace the policies on the domain's Token flow pre step. Pass [] to clear them. */
  setTokenPolicies: (policies: any[]) => Promise<void>;
  /** Send a client_credentials token request, with the given query string parameters. */
  requestToken: (queryParameters?: Record<string, string>) => Promise<TimedTokenResponse>;
}

export const setupTokenFlowPoliciesFixture = async (): Promise<TokenFlowPoliciesFixture> => {
  const accessToken = await requestAdminAccessToken();
  const { domain, oidcConfig } = await setupDomainForTest(uniqueName('token-flow-policies', true), { accessToken, waitForStart: true });

  try {
    const application = await waitForSyncAfter(domain.id, () =>
      createTestApp(uniqueName('token-flow-b2b', true), domain, accessToken, 'service', {
        settings: { oauth: { grantTypes: ['client_credentials'] } },
      }),
    );

    const setTokenPolicies = async (policies: any[]) => {
      const flows = await getDomainFlows(domain.id, accessToken);
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Token, 'pre', policies);
      await waitForSyncAfter(domain.id, () => updateDomainFlows(domain.id, accessToken, flows));
    };

    const requestToken = async (queryParameters: Record<string, string> = {}): Promise<TimedTokenResponse> => {
      const query = new URLSearchParams(queryParameters).toString();
      const start = performance.now();
      const response = await performPost(oidcConfig.token_endpoint, query ? `?${query}` : '', 'grant_type=client_credentials', {
        'Content-Type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(application),
      });
      return { status: response.status, body: response.body, elapsedMs: performance.now() - start };
    };

    return {
      accessToken,
      domain,
      openIdConfiguration: oidcConfig,
      application,
      setTokenPolicies,
      requestToken,
      cleanUp: async () => {
        await safeDeleteDomain(domain.id, accessToken);
      },
    };
  } catch (error) {
    await safeDeleteDomain(domain.id, accessToken);
    throw error;
  }
};
