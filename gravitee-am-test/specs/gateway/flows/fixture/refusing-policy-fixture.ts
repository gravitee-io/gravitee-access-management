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
import { Domain } from '@management-models/Domain';
import {
  createDomain,
  DomainOidcConfig,
  getDomainFlows,
  safeDeleteDomain,
  startDomain,
  updateDomainFlows,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createUser, listUsers } from '@management-commands/user-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';
import { FlowEntityTypeEnum } from '../../../../api/management/models';

export const REDIRECT_URI = 'https://callback';

export const REFUSING_USER = {
  username: 'refusing-policy-user',
  password: 'SomeP@ssw0rd',
  firstName: 'Refusing',
  lastName: 'Policy',
  email: 'refusing-policy@example.com',
  preRegistration: false,
};

/**
 * A Validate Request rule that cannot be satisfied: it requires a request parameter that the
 * sign-in never sends, so the policy refuses every request reaching this flow step.
 */
export const refusingPolicy = (status = '401') => ({
  name: 'Validate Request',
  policy: 'policy-request-validation',
  description: '',
  condition: '',
  enabled: true,
  configuration: JSON.stringify({
    status,
    rules: [
      {
        input: "{#request.params['a-parameter-never-sent']}",
        isRequired: true,
        constraint: { type: 'NOT_NULL' },
      },
    ],
  }),
});

/**
 * An Enrich User Profile policy writing a fixed value onto the signing-in user.
 *
 * Its effect is stored against the user rather than only reaching the token, so whether it ran
 * can still be established after a sign-in that was refused and issued no token.
 */
export const enrichProfilePolicy = (key: string, value: string) => ({
  name: 'Enrich User Profile',
  policy: 'policy-am-enrich-profile',
  description: '',
  condition: '',
  enabled: true,
  configuration: JSON.stringify({
    exitOnError: false,
    properties: [{ claim: key, claimValue: value }],
  }),
});

/** A Groovy policy whose script throws, for the runtime-failure case. */
export const throwingPolicy = () => ({
  name: 'Groovy',
  policy: 'groovy',
  description: '',
  condition: '',
  enabled: true,
  configuration: JSON.stringify({
    onRequestScript: 'def nothing = null; nothing.somethingThatDoesNotExist()',
  }),
});

export interface RefusingPolicyFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  openIdConfiguration: DomainOidcConfig;
  application: any;
  user: typeof REFUSING_USER;
  /** Replace the policies on the domain's Login flow. Pass [] to clear them. */
  setLoginPolicies: (scope: 'pre' | 'post', policies: any[]) => Promise<void>;
  /** The signing-in user's stored profile, for reading back what a policy wrote. */
  readUserProfile: () => Promise<Record<string, any>>;
}

export const setupRefusingPolicyFixture = async (): Promise<RefusingPolicyFixture> => {
  const accessToken = await requestAdminAccessToken();

  const domain = await createDomain(accessToken, uniqueName('refusing-policy', true), 'AM-2199 a policy that refuses a sign-in');

  const idpSet = await getAllIdps(domain.id, accessToken);
  const defaultIdp = idpSet.values().next().value;

  const appName = uniqueName('refusing-policy-app', true);
  const application = await createApplication(domain.id, accessToken, {
    name: appName,
    type: 'WEB',
    clientId: appName,
    clientSecret: uniqueName('refusing-secret', true),
    redirectUris: [REDIRECT_URI],
  }).then((app) =>
    updateApplication(
      domain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: [REDIRECT_URI],
            grantTypes: ['authorization_code'],
            scopeSettings: [{ scope: 'openid', defaultScope: true }],
          },
          advanced: { skipConsent: true },
        },
        identityProviders: [{ identity: defaultIdp.id, priority: -1 }],
      },
      app.id,
    ).then((updated) => {
      updated.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updated;
    }),
  );

  await createUser(domain.id, accessToken, REFUSING_USER);

  await startDomain(domain.id, accessToken);
  const started = await waitForDomainStart(domain);

  const setLoginPolicies = async (scope: 'pre' | 'post', policies: any[]) => {
    const flows = await getDomainFlows(domain.id, accessToken);
    lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, scope, policies);
    await waitForSyncAfter(domain.id, () => updateDomainFlows(domain.id, accessToken, flows));
  };

  const readUserProfile = async (): Promise<Record<string, any>> => {
    const page = await listUsers(domain.id, accessToken, REFUSING_USER.username);
    if (!page.totalCount || page.data.length === 0) {
      throw new Error(`user ${REFUSING_USER.username} not found`);
    }
    return page.data[0].additionalInformation ?? {};
  };

  return {
    accessToken,
    domain: started.domain,
    openIdConfiguration: started.oidcConfig,
    application,
    user: REFUSING_USER,
    setLoginPolicies,
    readUserProfile,
    cleanUp: async () => {
      await safeDeleteDomain(domain.id, accessToken);
    },
  };
};
