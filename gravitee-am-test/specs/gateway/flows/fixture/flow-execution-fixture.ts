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
import { DomainOidcConfig } from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  getDomainFlows,
  safeDeleteDomain,
  startDomain,
  updateDomainFlows,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import {
  createApplication,
  getApplicationFlows,
  patchApplication,
  updateApplication,
  updateApplicationFlows,
} from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { clearEmails } from '@utils-commands/email-commands';
import { Fixture } from '../../../test-fixture';
import { FlowEntityTypeEnum, TokenClaim } from '../../../../api/management/models';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { requestToken, signInUser } from '@gateway-commands/oauth-oidc-commands';
import { assertGeneratedTokenAndGet } from '@gateway-commands/utils';
import { decodeJwt } from '@utils-commands/jwt';
import { syncApplication } from '@gateway-commands/application-sync-commands';

export interface FlowExecutionFixture extends Fixture {
  domain: Domain;
  openIdConfiguration: DomainOidcConfig;
  application: any;
  user: any;
  cleanUp: () => Promise<void>;

  // Helper methods for isolated tests
  configureDomainFlows: (config: DomainFlowTestConfig) => Promise<void>;
  configureAppFlows: (config: AppFlowTestConfig) => Promise<void>;
  setAppTokenClaims: (claims: TokenClaim[]) => Promise<void>;
  setFlowsInherited: (inherited: boolean) => Promise<void>;
  loginAndGetJwt: (extraParams?: string) => Promise<{ jwt: any; postLoginRedirect: any }>;
  resetFlows: () => Promise<void>;
}

export interface DomainFlowTestConfig {
  rootPreScript?: string;
  loginPreEnrichProperties?: { key: string; value: string }[];
  loginPreCondition?: string;
  loginPostEnrichProperties?: { claim: string; claimValue: string }[];
}

export interface AppFlowTestConfig {
  rootPreScript?: string;
  loginPreEnrichProperties?: { key: string; value: string }[];
  loginPostEnrichProperties?: { claim: string; claimValue: string }[];
  conditionalFlow?: {
    condition: string;
    httpCalloutUrl: string;
    emailSubject: string;
    emailContent: string;
  };
}

export const setupFixture = async (): Promise<FlowExecutionFixture> => {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toBeDefined();

  const createdDomain = await createDomain(accessToken, uniqueName('flow-execution', true), 'test flow execution');
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const domainStarted = await startDomain(createdDomain.id, accessToken);

  const idpSet = await getAllIdps(createdDomain.id, accessToken);
  const appClientId = uniqueName('flow-app', true);
  const appClientSecret = uniqueName('flow-app', true);
  const appName = uniqueName('my-client', true);
  const application = await createApplication(createdDomain.id, accessToken, {
    name: appName,
    type: 'WEB',
    clientId: appClientId,
    clientSecret: appClientSecret,
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(
      createdDomain.id,
      accessToken,
      {
        settings: {
          oauth: {
            redirectUris: ['https://callback'],
            grantTypes: ['authorization_code'],
          },
        },
        identityProviders: [{ identity: idpSet.values().next().value.id, priority: -1 }],
      },
      app.id,
    ).then((updatedApp) => {
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
  expect(application).toBeDefined();

  await waitForDomainSync(createdDomain.id, accessToken);

  const user = {
    username: uniqueName('FlowUser', true),
    password: 'SomeP@ssw0rd',
    firstName: 'Flow',
    lastName: 'User',
    email: `${uniqueName('flowuser', true)}@acme.fr`,
    preRegistration: false,
  };
  await createUser(createdDomain.id, accessToken, user);

  const started = await waitForDomainStart(domainStarted);
  const domain = started.domain;
  const openIdConfiguration = started.oidcConfig;

  await clearEmails(user.email);

  // Helper: Configure domain flows
  const configureDomainFlows = async (config: DomainFlowTestConfig) => {
    const flows = await getDomainFlows(domain.id, accessToken);

    if (config.rootPreScript) {
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Root, 'pre', [
        {
          name: 'Groovy',
          policy: 'groovy',
          description: '',
          condition: '',
          enabled: true,
          configuration: JSON.stringify({ onRequestScript: config.rootPreScript }),
        },
      ]);
    }

    if (config.loginPreEnrichProperties) {
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'pre', [
        {
          name: 'Enrich Authentication Flow',
          policy: 'policy-am-enrich-auth-flow',
          description: '',
          configuration: JSON.stringify({ properties: config.loginPreEnrichProperties }),
          enabled: true,
          condition: config.loginPreCondition || '',
        },
      ]);
    }

    if (config.loginPostEnrichProperties) {
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'post', [
        {
          name: 'Enrich User Profile',
          policy: 'policy-am-enrich-profile',
          description: '',
          configuration: JSON.stringify({
            exitOnError: false,
            properties: config.loginPostEnrichProperties,
          }),
          enabled: true,
          condition: '',
        },
      ]);
    }

    await updateDomainFlows(domain.id, accessToken, flows);
    await waitForDomainSync(domain.id, accessToken);
  };

  // Helper: Configure app flows
  const configureAppFlows = async (config: AppFlowTestConfig) => {
    const flows = await getApplicationFlows(domain.id, accessToken, application.id);

    if (config.rootPreScript) {
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Root, 'pre', [
        {
          name: 'Groovy',
          policy: 'groovy',
          description: '',
          condition: '',
          enabled: true,
          configuration: JSON.stringify({ onRequestScript: config.rootPreScript }),
        },
      ]);
    }

    if (config.loginPreEnrichProperties) {
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'pre', [
        {
          name: 'Enrich Authentication Flow',
          policy: 'policy-am-enrich-auth-flow',
          description: '',
          configuration: JSON.stringify({ properties: config.loginPreEnrichProperties }),
          enabled: true,
          condition: '',
        },
      ]);
    }

    if (config.loginPostEnrichProperties) {
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'post', [
        {
          name: 'Enrich User Profile',
          policy: 'policy-am-enrich-profile',
          description: '',
          configuration: JSON.stringify({
            exitOnError: false,
            properties: config.loginPostEnrichProperties,
          }),
          enabled: true,
          condition: '',
        },
      ]);
    }

    if (config.conditionalFlow) {
      flows.push({
        name: 'Conditional Login',
        pre: [],
        post: [
          {
            name: 'HTTP Callout',
            policy: 'policy-http-callout',
            description: '',
            condition: '',
            enabled: true,
            configuration: JSON.stringify({
              method: 'GET',
              fireAndForget: false,
              exitOnError: false,
              errorCondition: '{#calloutResponse.status >= 400 and #calloutResponse.status <= 599}',
              errorStatusCode: 500,
              url: config.conditionalFlow.httpCalloutUrl,
              variables: [{ value: "{#jsonPath(#calloutResponse.content, '$.jwks_uri')}", name: 'jwks_uri_from_callout' }],
            }),
          },
          {
            name: 'Send email',
            policy: 'policy-am-send-email',
            description: '',
            condition: '',
            enabled: true,
            configuration: JSON.stringify({
              template: 'TEST JEST',
              from: 'no-reply@gravitee.io',
              fromName: 'Test',
              to: '${user.email}',
              subject: config.conditionalFlow.emailSubject,
              content: config.conditionalFlow.emailContent,
            }),
          },
        ],
        type: FlowEntityTypeEnum.Login,
        condition: config.conditionalFlow.condition,
      });
    }

    await updateApplicationFlows(domain.id, accessToken, application.id, flows);
    await waitForDomainSync(domain.id, accessToken);
  };

  // Helper: Set custom token claims
  const setAppTokenClaims = async (claims: TokenClaim[]) => {
    application.settings.oauth.tokenCustomClaims = claims;
    const patch = patchApplication(domain.id, accessToken, application, application.id);
    await syncApplication(domain.id, application.id, patch);
    await waitForDomainSync(domain.id, accessToken);
  };

  // Helper: Set flows inherited flag
  const setFlowsInherited = async (inherited: boolean) => {
    application.settings.advanced = application.settings.advanced || {};
    application.settings.advanced.flowsInherited = inherited;
    const patch = patchApplication(domain.id, accessToken, application, application.id);
    await syncApplication(domain.id, application.id, patch);
    await waitForDomainSync(domain.id, accessToken);
  };

  // Helper: Login and get decoded JWT
  const loginAndGetJwt = async (extraParams?: string) => {
    const postLoginRedirect = await signInUser(domain, application, user, openIdConfiguration, extraParams);
    const tokenResponse = await requestToken(application, openIdConfiguration, postLoginRedirect);
    const accessTokenStr = assertGeneratedTokenAndGet(tokenResponse.body);
    const jwt = decodeJwt(accessTokenStr);
    return { jwt, postLoginRedirect };
  };

  // Helper: Reset all flows to empty
  const resetFlows = async () => {
    const domainFlows = await getDomainFlows(domain.id, accessToken);
    for (const flow of domainFlows) {
      flow.pre = [];
      flow.post = [];
    }
    await updateDomainFlows(domain.id, accessToken, domainFlows);

    const appFlows = await getApplicationFlows(domain.id, accessToken, application.id);
    const baseAppFlows = appFlows.filter((f) => f.name !== 'Conditional Login');
    for (const flow of baseAppFlows) {
      flow.pre = [];
      flow.post = [];
    }
    await updateApplicationFlows(domain.id, accessToken, application.id, baseAppFlows);

    application.settings.oauth.tokenCustomClaims = [];
    application.settings.advanced = application.settings.advanced || {};
    application.settings.advanced.flowsInherited = true;
    const patch = patchApplication(domain.id, accessToken, application, application.id);
    await syncApplication(domain.id, application.id, patch);

    await waitForDomainSync(domain.id, accessToken);
  };

  return {
    accessToken,
    domain,
    openIdConfiguration,
    application,
    user,
    configureDomainFlows,
    configureAppFlows,
    setAppTokenClaims,
    setFlowsInherited,
    loginAndGetJwt,
    resetFlows,
    cleanUp: async () => {
      await safeDeleteDomain(domain?.id, accessToken);
    },
  };
};
