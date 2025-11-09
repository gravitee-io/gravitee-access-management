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
import fetch from 'cross-fetch';

globalThis.fetch = fetch;

import { jest, afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  safeDeleteDomain,
  DomainOidcConfig,
  getDomainFlows,
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
import { logoutUser, requestToken, signInUser } from '@gateway-commands/oauth-oidc-commands';
import { uniqueName } from '@utils-commands/misc';

import { Domain, FlowEntityTypeEnum } from '../../api/management/models';
import { assertGeneratedTokenAndGet } from '@gateway-commands/utils';
import { decodeJwt } from '@utils-commands/jwt';
import { clearEmails, getLastEmail, hasEmail } from '@utils-commands/email-commands';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';

let domain: Domain;
let managementApiAccessToken: string;
let openIdConfiguration: DomainOidcConfig;
let application;
let user = {
  username: 'FlowUser',
  password: 'SomeP@ssw0rd',
  firstName: 'Flow',
  lastName: 'User',
  email: 'flowuser@acme.fr',
  preRegistration: false,
};

jest.setTimeout(200000);

beforeAll(async () => {
  managementApiAccessToken = await requestAdminAccessToken();
  expect(managementApiAccessToken).toBeDefined();

  const createdDomain = await createDomain(managementApiAccessToken, uniqueName('flow-execution', true), 'test end-user logout');
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();
  domain = createdDomain;

  const domainStarted = await startDomain(domain.id, managementApiAccessToken);
  // do the rest of the setup while the domain is starting
  // Create the application
  const idpSet = await getAllIdps(domain.id, managementApiAccessToken);
  const appClientId = uniqueName('flow-app', true);
  const appClientSecret = uniqueName('flow-app', true);
  const appName = uniqueName('my-client', true);
  application = await createApplication(domain.id, managementApiAccessToken, {
    name: appName,
    type: 'WEB',
    clientId: appClientId,
    clientSecret: appClientSecret,
    redirectUris: ['https://callback'],
  }).then((app) =>
    updateApplication(
      domain.id,
      managementApiAccessToken,
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
      // restore the clientSecret coming from the create order
      updatedApp.settings.oauth.clientSecret = app.settings.oauth.clientSecret;
      return updatedApp;
    }),
  );
  expect(application).toBeDefined();

  // Wait for application to sync to gateway
  await waitForDomainSync(domain.id, managementApiAccessToken);

  // Create a User
  await createUser(domain.id, managementApiAccessToken, user);

  await waitForDomainStart(domainStarted).then((result) => {
    domain = result.domain;
    openIdConfiguration = result.oidcConfig;
  });

  // Clear emails for this specific recipient at the start to avoid interference from other tests
  await clearEmails(user.email);
});

describe('Flows Execution - authorization_code flow', () => {
  describe('Only Domain Flows', () => {
    it('Define Domain flows', async () => {
      const flows = await getDomainFlows(domain.id, managementApiAccessToken);
      // Define Groovy policy set attribute into the context on ALL flow
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Root, 'pre', [
        {
          name: 'Groovy',
          policy: 'groovy',
          description: '',
          condition: '',
          enabled: true,
          configuration: JSON.stringify({ onRequestScript: 'context.setAttribute("groovy-domain-all","domainRootInfo");' }),
        },
      ]);
      // Define PRE LOGIN flow - Enrich Authorization Context
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'pre', [
        {
          name: 'Enrich Authentication Flow',
          policy: 'policy-am-enrich-auth-flow',
          description: '',
          configuration: JSON.stringify({
            properties: [
              {
                key: 'groovy-domain-all',
                value: "{#context.attributes['groovy-domain-all']}",
              },
            ],
          }),
          enabled: true,
          condition: '',
        },
      ]);
      // Define POST LOGIN flow - Enrich User Profile
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'post', [
        {
          name: 'Enrich User Profile',
          policy: 'policy-am-enrich-profile',
          description: '',
          configuration: JSON.stringify({
            exitOnError: false,
            properties: [{ claim: 'claimFromGroovy', claimValue: "{#context.attributes['authFlow']['groovy-domain-all']}" }],
          }),
          enabled: true,
          condition: '',
        },
      ]);
      await updateDomainFlows(domain.id, managementApiAccessToken, flows);
    });

    it('Update App to define access_token custom claims', async () => {
      application.settings.oauth.tokenCustomClaims = [
        {
          tokenType: 'access_token',
          claimName: 'domain-groovy-from-profile',
          claimValue: "{#context.attributes['user']['additionalInformation']['claimFromGroovy']}",
        },
        {
          tokenType: 'access_token',
          claimName: 'domain-groovy-from-authflow',
          claimValue: "{#context.attributes['authFlow']['groovy-domain-all']}",
        },
      ];
      await patchApplication(domain.id, managementApiAccessToken, application, application.id);
      await waitForDomainSync(domain.id, managementApiAccessToken);
    });

    it('After LOGIN, flow has been executed', async () => {
      const postLoginRedirect = await signInUser(domain, application, user, openIdConfiguration);

      const tokenResponse = await requestToken(application, openIdConfiguration, postLoginRedirect);
      const accessToken = assertGeneratedTokenAndGet(tokenResponse.body);
      const JWT = decodeJwt(accessToken);
      expect(JWT['domain-groovy-from-profile']).toBeDefined();
      expect(JWT['domain-groovy-from-profile']).toEqual('domainRootInfo');
      expect(JWT['domain-groovy-from-authflow']).toBeDefined();
      expect(JWT['domain-groovy-from-authflow']).toEqual('domainRootInfo');

      await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
    });

    it('Update domain policies', async () => {
      const flows = await getDomainFlows(domain.id, managementApiAccessToken);
      // Update information set by Groovy
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Root, 'pre', [
        {
          name: 'Groovy',
          policy: 'groovy',
          description: '',
          condition: '',
          enabled: true,
          configuration: JSON.stringify({ onRequestScript: 'context.setAttribute("groovy-domain-all","domainRootInfoUpdated");' }),
        },
      ]);
      // Set condition in Enrich Authorization Context
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'pre', [
        {
          name: 'Enrich Authentication Flow',
          policy: 'policy-am-enrich-auth-flow',
          description: '',
          configuration: JSON.stringify({
            properties: [
              {
                key: 'groovy-domain-all',
                value: "{#context.attributes['groovy-domain-all']}",
              },
            ],
          }),
          enabled: true,
          condition: 'false',
        },
      ]);
      // As EnrichAuth flow is not executed, we will use directly the groovy value as groovy policy is exepcuted for all request
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'post', [
        {
          name: 'Enrich User Profile',
          policy: 'policy-am-enrich-profile',
          description: '',
          configuration: JSON.stringify({
            exitOnError: false,
            properties: [{ claim: 'claimFromGroovy', claimValue: "{#context.attributes['groovy-domain-all']}" }],
          }),
          enabled: true,
          condition: '',
        },
      ]);
      await updateDomainFlows(domain.id, managementApiAccessToken, flows);
      await waitForDomainSync(domain.id, managementApiAccessToken);
    });

    it('After LOGIN, flow has been executed', async () => {
      const postLoginRedirect = await signInUser(domain, application, user, openIdConfiguration);

      const tokenResponse = await requestToken(application, openIdConfiguration, postLoginRedirect);
      const accessToken = assertGeneratedTokenAndGet(tokenResponse.body);
      const JWT = decodeJwt(accessToken);
      expect(JWT['domain-groovy-from-profile']).toBeDefined();
      expect(JWT['domain-groovy-from-profile']).toEqual('domainRootInfoUpdated');
      expect(JWT['domain-groovy-from-authflow']).not.toBeDefined();

      await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
    });
  });

  describe('App Flows', () => {
    it('Define ALL flow - ', async () => {
      const flows = await getApplicationFlows(domain.id, managementApiAccessToken, application.id);
      // Define Groovy policy set attribute into the context on ALL flow
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Root, 'pre', [
        {
          name: 'Groovy',
          policy: 'groovy',
          description: '',
          condition: '',
          enabled: true,
          configuration: JSON.stringify({ onRequestScript: 'context.setAttribute("groovy-app-all","appRootInfo");' }),
        },
      ]);
      // Define PRE LOGIN flow - Enrich Authorization Context
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'pre', [
        {
          name: 'Enrich Authentication Flow',
          policy: 'policy-am-enrich-auth-flow',
          description: '',
          configuration: JSON.stringify({
            properties: [
              {
                key: 'groovy-app-all',
                value: "{#context.attributes['groovy-app-all']}",
              },
            ],
          }),
          enabled: true,
          condition: '',
        },
      ]);
      // Define POST LOGIN flow - Enrich User Profile
      lookupFlowAndResetPolicies(flows, FlowEntityTypeEnum.Login, 'post', [
        {
          name: 'Enrich User Profile',
          policy: 'policy-am-enrich-profile',
          description: '',
          configuration: JSON.stringify({
            exitOnError: false,
            properties: [{ claim: 'claimFromAppGroovy', claimValue: "{#context.attributes['authFlow']['groovy-app-all']}" }],
          }),
          enabled: true,
          condition: '',
        },
      ]);
      await updateApplicationFlows(domain.id, managementApiAccessToken, application.id, flows);
    });

    it('Update App to define access_token custom claims', async () => {
      application.settings.oauth.tokenCustomClaims = [
        {
          tokenType: 'access_token',
          claimName: 'domain-groovy-from-profile',
          claimValue: "{#context.attributes['user']['additionalInformation']['claimFromGroovy']}",
        },
        {
          tokenType: 'access_token',
          claimName: 'domain-groovy-from-authflow',
          claimValue: "{#context.attributes['authFlow']['groovy-domain-all']}",
        },
      ];
      await patchApplication(domain.id, managementApiAccessToken, application, application.id);
    });

    it('Update App to define access_token custom claims', async () => {
      application.settings.oauth.tokenCustomClaims = [
        {
          tokenType: 'access_token',
          claimName: 'domain-groovy-from-profile',
          claimValue: "{#context.attributes['user']['additionalInformation']['claimFromGroovy']}",
        },
        {
          tokenType: 'access_token',
          claimName: 'domain-groovy-from-authflow',
          claimValue: "{#context.attributes['authFlow']['groovy-domain-all']}",
        },
        // new claims to test app flow
        {
          tokenType: 'access_token',
          claimName: 'app-groovy-from-profile',
          claimValue: "{#context.attributes['user']['additionalInformation']['claimFromAppGroovy']}",
        },
        {
          tokenType: 'access_token',
          claimName: 'app-groovy-from-authflow',
          claimValue: "{#context.attributes['authFlow']['groovy-app-all']}",
        },
      ];
      await patchApplication(domain.id, managementApiAccessToken, application, application.id);
      await waitForDomainSync(domain.id, managementApiAccessToken);
    });

    it('After LOGIN, App & domain flows has been executed', async () => {
      const postLoginRedirect = await signInUser(domain, application, user, openIdConfiguration);

      const tokenResponse = await requestToken(application, openIdConfiguration, postLoginRedirect);
      const accessToken = assertGeneratedTokenAndGet(tokenResponse.body);

      const JWT = decodeJwt(accessToken);
      expect(JWT['domain-groovy-from-profile']).toBeDefined();
      expect(JWT['domain-groovy-from-profile']).toEqual('domainRootInfoUpdated');
      expect(JWT['domain-groovy-from-authflow']).not.toBeDefined();
      expect(JWT['app-groovy-from-profile']).toBeDefined();
      expect(JWT['app-groovy-from-profile']).toEqual('appRootInfo');
      expect(JWT['app-groovy-from-authflow']).toBeDefined();
      expect(JWT['app-groovy-from-authflow']).toEqual('appRootInfo');

      await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
    });
  });

  describe('App Flows with Domain disabled', () => {
    it('Define ALL flow - ', async () => {
      const domainFlows = await getDomainFlows(domain.id, managementApiAccessToken);
      // Update information set by Groovy
      lookupFlowAndResetPolicies(domainFlows, FlowEntityTypeEnum.Root, 'pre', [
        {
          name: 'Groovy',
          policy: 'groovy',
          description: '',
          condition: '',
          enabled: true,
          configuration: JSON.stringify({
            onRequestScript: 'context.setAttribute("groovy-domain-all","domainRootInfoUpdatedAfterDisabled");',
          }),
        },
      ]);

      // Define Groovy policy set attribute into the context on ALL flow
      application.settings.advanced.flowsInherited = false;
      await patchApplication(domain.id, managementApiAccessToken, application, application.id);
      await waitForDomainSync(domain.id, managementApiAccessToken);
    });

    it('After LOGIN, Only App has been executed', async () => {
      const postLoginRedirect = await signInUser(domain, application, user, openIdConfiguration);

      const tokenResponse = await requestToken(application, openIdConfiguration, postLoginRedirect);
      const accessToken = assertGeneratedTokenAndGet(tokenResponse.body);
      const JWT = decodeJwt(accessToken);
      expect(JWT['domain-groovy-from-profile']).toBeDefined();
      // value is present because persisted in the user profile but the value is the one before the "domainRootInfoUpdatedAfterDisabled" update
      expect(JWT['domain-groovy-from-profile']).toEqual('domainRootInfoUpdated');
      expect(JWT['domain-groovy-from-authflow']).not.toBeDefined();
      expect(JWT['app-groovy-from-profile']).toBeDefined();
      expect(JWT['app-groovy-from-profile']).toEqual('appRootInfo');
      expect(JWT['app-groovy-from-authflow']).toBeDefined();
      expect(JWT['app-groovy-from-authflow']).toEqual('appRootInfo');

      await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);
    });
  });

  describe('App Flows with New Conditional Flow', () => {
    const EMAIL_SUBJECT = 'Email Send Under Condition';

    it('Define new LOGIN flow with condition - ', async () => {
      const appFlows = await getApplicationFlows(domain.id, managementApiAccessToken, application.id);

      appFlows.push({
        name: 'Conditionnal Login',
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
              url: `${openIdConfiguration.issuer}/.well-known/openid-configuration`,
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
              subject: EMAIL_SUBJECT,
              content: '<a href="${jwks_uri_from_callout}">jwks_uri</a>',
            }),
          },
        ],
        type: FlowEntityTypeEnum.Login,
        condition: "{#request.params['callout'] != null && #request.params['callout'][0].equals('true') }",
      });

      await updateApplicationFlows(domain.id, managementApiAccessToken, application.id, appFlows);
      await waitForDomainSync(domain.id, managementApiAccessToken);
    });

    it("After LOGIN without the callout parameter, email isn't received ", async () => {
      await clearEmails(user.email);

      const postLoginRedirect = await signInUser(domain, application, user, openIdConfiguration);
      await new Promise((r) => setTimeout(r, 1000));
      await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);

      const emailReceived = await hasEmail(1000, user.email);
      expect(emailReceived).toBeFalsy();
    });

    it('After LOGIN with the callout parameter, email is received ', async () => {
      await clearEmails(user.email);
      const postLoginRedirect = await signInUser(domain, application, user, openIdConfiguration, 'callout=true');
      await new Promise((r) => setTimeout(r, 1000));
      await logoutUser(openIdConfiguration.end_session_endpoint, postLoginRedirect);

      const emailReceived = await hasEmail(1000, user.email);
      expect(emailReceived).toBeTruthy();

      const email = await getLastEmail(1000, user.email);
      expect(email.subject).toBeDefined();
      expect(email.subject).toContain(EMAIL_SUBJECT);
      const jwks_uri = email.extractLink();
      expect(jwks_uri).toEqual(openIdConfiguration.jwks_uri);
    });
  });
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, managementApiAccessToken);
  }
});
