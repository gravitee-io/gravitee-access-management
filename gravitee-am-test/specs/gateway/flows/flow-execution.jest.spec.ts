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
import { afterAll, beforeAll, expect } from '@jest/globals';
import { getDomainFlows, updateDomainFlows, waitForDomainSync } from '@management-commands/domain-management-commands';
import { getApplicationFlows, patchApplication, updateApplicationFlows } from '@management-commands/application-management-commands';
import { logoutUser, requestToken, signInUser } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';

import { FlowEntityTypeEnum } from '../../../api/management/models';
import { assertGeneratedTokenAndGet } from '@gateway-commands/utils';
import { decodeJwt } from '@utils-commands/jwt';
import { clearEmails, getLastEmail, hasEmail } from '@utils-commands/email-commands';
import { lookupFlowAndResetPolicies } from '@management-commands/flow-management-commands';
import { syncApplication } from '@gateway-commands/application-sync-commands';
import { FlowExecutionFixture, setupFixture } from './fixture/flow-execution-fixture';

let fixture: FlowExecutionFixture;

setup(200000);

beforeAll(async () => {
  fixture = await setupFixture();
  expect(fixture.openIdConfiguration).toBeDefined();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Flows Execution - authorization_code flow', () => {
  describe('Only Domain Flows', () => {
    it('Define Domain flows', async () => {
      const flows = await getDomainFlows(fixture.domain.id, fixture.accessToken);
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
      await updateDomainFlows(fixture.domain.id, fixture.accessToken, flows);
    });

    it('Update App to define access_token custom claims', async () => {
      fixture.application.settings.oauth.tokenCustomClaims = [
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
      const patch = patchApplication(fixture.domain.id, fixture.accessToken, fixture.application, fixture.application.id);
      await syncApplication(fixture.domain.id, fixture.application.id, patch);
    });

    it('After LOGIN, flow has been executed', async () => {
      const postLoginRedirect = await signInUser(fixture.domain, fixture.application, fixture.user, fixture.openIdConfiguration);

      const tokenResponse = await requestToken(fixture.application, fixture.openIdConfiguration, postLoginRedirect);
      const accessToken = assertGeneratedTokenAndGet(tokenResponse.body);
      const JWT = decodeJwt(accessToken);
      expect(JWT['domain-groovy-from-profile']).toBeDefined();
      expect(JWT['domain-groovy-from-profile']).toEqual('domainRootInfo');
      expect(JWT['domain-groovy-from-authflow']).toBeDefined();
      expect(JWT['domain-groovy-from-authflow']).toEqual('domainRootInfo');

      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);
    });

    it('Update domain policies', async () => {
      const flows = await getDomainFlows(fixture.domain.id, fixture.accessToken);
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
      await updateDomainFlows(fixture.domain.id, fixture.accessToken, flows);
      await waitForDomainSync(fixture.domain.id, fixture.accessToken);
    });

    it('After LOGIN, flow has been executed', async () => {
      const postLoginRedirect = await signInUser(fixture.domain, fixture.application, fixture.user, fixture.openIdConfiguration);

      const tokenResponse = await requestToken(fixture.application, fixture.openIdConfiguration, postLoginRedirect);
      const accessToken = assertGeneratedTokenAndGet(tokenResponse.body);
      const JWT = decodeJwt(accessToken);
      expect(JWT['domain-groovy-from-profile']).toBeDefined();
      expect(JWT['domain-groovy-from-profile']).toEqual('domainRootInfoUpdated');
      expect(JWT['domain-groovy-from-authflow']).not.toBeDefined();

      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);
    });
  });

  describe('App Flows', () => {
    it('Define ALL flow - ', async () => {
      const flows = await getApplicationFlows(fixture.domain.id, fixture.accessToken, fixture.application.id);
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
      await updateApplicationFlows(fixture.domain.id, fixture.accessToken, fixture.application.id, flows);
    });

    it('Update App to define access_token custom claims', async () => {
      fixture.application.settings.oauth.tokenCustomClaims = [
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
      const patch = patchApplication(fixture.domain.id, fixture.accessToken, fixture.application, fixture.application.id);
      await syncApplication(fixture.domain.id, fixture.application.id, patch);
    });

    it('Update App to define access_token custom claims', async () => {
      fixture.application.settings.oauth.tokenCustomClaims = [
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
      const patch = patchApplication(fixture.domain.id, fixture.accessToken, fixture.application, fixture.application.id);
      await syncApplication(fixture.domain.id, fixture.application.id, patch);
      await waitForDomainSync(fixture.domain.id, fixture.accessToken);
    });

    it('After LOGIN, App & domain flows has been executed', async () => {
      const postLoginRedirect = await signInUser(fixture.domain, fixture.application, fixture.user, fixture.openIdConfiguration);

      const tokenResponse = await requestToken(fixture.application, fixture.openIdConfiguration, postLoginRedirect);
      const accessToken = assertGeneratedTokenAndGet(tokenResponse.body);

      const JWT = decodeJwt(accessToken);
      expect(JWT['domain-groovy-from-profile']).toBeDefined();
      expect(JWT['domain-groovy-from-profile']).toEqual('domainRootInfoUpdated');
      expect(JWT['domain-groovy-from-authflow']).not.toBeDefined();
      expect(JWT['app-groovy-from-profile']).toBeDefined();
      expect(JWT['app-groovy-from-profile']).toEqual('appRootInfo');
      expect(JWT['app-groovy-from-authflow']).toBeDefined();
      expect(JWT['app-groovy-from-authflow']).toEqual('appRootInfo');

      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);
    });
  });

  describe('App Flows with Domain disabled', () => {
    it('Define ALL flow - ', async () => {
      const domainFlows = await getDomainFlows(fixture.domain.id, fixture.accessToken);
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
      fixture.application.settings.advanced.flowsInherited = false;
      const patch = patchApplication(fixture.domain.id, fixture.accessToken, fixture.application, fixture.application.id);
      await syncApplication(fixture.domain.id, fixture.application.id, patch);
      await waitForDomainSync(fixture.domain.id, fixture.accessToken);
    });

    it('After LOGIN, Only App has been executed', async () => {
      const postLoginRedirect = await signInUser(fixture.domain, fixture.application, fixture.user, fixture.openIdConfiguration);

      const tokenResponse = await requestToken(fixture.application, fixture.openIdConfiguration, postLoginRedirect);
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

      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);
    });
  });

  describe('App Flows with New Conditional Flow', () => {
    const EMAIL_SUBJECT = 'Email Send Under Condition';

    it('Define new LOGIN flow with condition - ', async () => {
      const appFlows = await getApplicationFlows(fixture.domain.id, fixture.accessToken, fixture.application.id);

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
              url: `${fixture.openIdConfiguration.issuer}/.well-known/openid-configuration`,
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

      await updateApplicationFlows(fixture.domain.id, fixture.accessToken, fixture.application.id, appFlows);
      await waitForDomainSync(fixture.domain.id, fixture.accessToken);
    });

    it("After LOGIN without the callout parameter, email isn't received ", async () => {
      await clearEmails(fixture.user.email);

      const postLoginRedirect = await signInUser(fixture.domain, fixture.application, fixture.user, fixture.openIdConfiguration);
      await new Promise((r) => setTimeout(r, 1000));
      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);

      const emailReceived = await hasEmail(1000, fixture.user.email);
      expect(emailReceived).toBeFalsy();
    });

    it('After LOGIN with the callout parameter, email is received ', async () => {
      await clearEmails(fixture.user.email);
      const postLoginRedirect = await signInUser(
        fixture.domain,
        fixture.application,
        fixture.user,
        fixture.openIdConfiguration,
        'callout=true',
      );
      await new Promise((r) => setTimeout(r, 1000));
      await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);

      const emailReceived = await hasEmail(1000, fixture.user.email);
      expect(emailReceived).toBeTruthy();

      const email = await getLastEmail(1000, fixture.user.email);
      expect(email.subject).toBeDefined();
      expect(email.subject).toContain(EMAIL_SUBJECT);
      const jwks_uri = email.extractLink();
      expect(jwks_uri).toEqual(fixture.openIdConfiguration.jwks_uri);
    });
  });
});
