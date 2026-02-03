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
import { afterAll, afterEach, beforeAll, expect } from '@jest/globals';
import { logoutUser } from '@gateway-commands/oauth-oidc-commands';
import { setup } from '../../test-fixture';
import { clearEmails, getLastEmail, hasEmail } from '@utils-commands/email-commands';
import { FlowExecutionFixture, setupFixture } from './fixture/flow-execution-fixture';
import { TokenClaimTokenTypeEnum } from '../../../api/management/models';

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

afterEach(async () => {
  // Reset flows after each test to ensure isolation
  await fixture.resetFlows();
});

describe('Flow Execution - Domain Flows', () => {
  it('should populate JWT claims from domain Groovy and Enrich policies', async () => {
    // Configure domain flows: Groovy sets attribute, Enrich Auth Flow passes it, Enrich Profile stores it
    await fixture.configureDomainFlows({
      rootPreScript: 'context.setAttribute("groovy-domain-all","domainRootInfo");',
      loginPreEnrichProperties: [{ key: 'groovy-domain-all', value: "{#context.attributes['groovy-domain-all']}" }],
      loginPostEnrichProperties: [{ claim: 'claimFromGroovy', claimValue: "{#context.attributes['authFlow']['groovy-domain-all']}" }],
    });

    // Configure token claims to expose the values
    await fixture.setAppTokenClaims([
      {
        tokenType: TokenClaimTokenTypeEnum.AccessToken,
        claimName: 'domain-groovy-from-profile',
        claimValue: "{#context.attributes['user']['additionalInformation']['claimFromGroovy']}",
      },
      {
        tokenType: TokenClaimTokenTypeEnum.AccessToken,
        claimName: 'domain-groovy-from-authflow',
        claimValue: "{#context.attributes['authFlow']['groovy-domain-all']}",
      },
    ]);

    // Login and verify JWT claims
    const { jwt, postLoginRedirect } = await fixture.loginAndGetJwt();

    expect(jwt['domain-groovy-from-profile']).toBeDefined();
    expect(jwt['domain-groovy-from-profile']).toEqual('domainRootInfo');
    expect(jwt['domain-groovy-from-authflow']).toBeDefined();
    expect(jwt['domain-groovy-from-authflow']).toEqual('domainRootInfo');

    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);
  });

  it('should skip Enrich Auth Flow when condition is false', async () => {
    // Configure domain flows with condition=false on Enrich Auth Flow
    await fixture.configureDomainFlows({
      rootPreScript: 'context.setAttribute("groovy-domain-all","domainRootInfoUpdated");',
      loginPreEnrichProperties: [{ key: 'groovy-domain-all', value: "{#context.attributes['groovy-domain-all']}" }],
      loginPreCondition: 'false',
      loginPostEnrichProperties: [{ claim: 'claimFromGroovy', claimValue: "{#context.attributes['groovy-domain-all']}" }],
    });

    await fixture.setAppTokenClaims([
      {
        tokenType: TokenClaimTokenTypeEnum.AccessToken,
        claimName: 'domain-groovy-from-profile',
        claimValue: "{#context.attributes['user']['additionalInformation']['claimFromGroovy']}",
      },
      {
        tokenType: TokenClaimTokenTypeEnum.AccessToken,
        claimName: 'domain-groovy-from-authflow',
        claimValue: "{#context.attributes['authFlow']['groovy-domain-all']}",
      },
    ]);

    const { jwt, postLoginRedirect } = await fixture.loginAndGetJwt();

    // Profile claim should be set (from Groovy directly, since condition skipped Enrich Auth)
    expect(jwt['domain-groovy-from-profile']).toBeDefined();
    expect(jwt['domain-groovy-from-profile']).toEqual('domainRootInfoUpdated');
    // Auth flow claim should NOT be set (Enrich Auth was skipped)
    expect(jwt['domain-groovy-from-authflow']).not.toBeDefined();

    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);
  });
});

describe('Flow Execution - App Flows', () => {
  it('should execute both domain and app flows when inheritance is enabled', async () => {
    // Configure domain flows
    await fixture.configureDomainFlows({
      rootPreScript: 'context.setAttribute("groovy-domain-all","domainRootInfo");',
      loginPreEnrichProperties: [{ key: 'groovy-domain-all', value: "{#context.attributes['groovy-domain-all']}" }],
      loginPreCondition: 'false', // Skip so authFlow doesn't have domain value
      loginPostEnrichProperties: [{ claim: 'claimFromGroovy', claimValue: "{#context.attributes['groovy-domain-all']}" }],
    });

    // Configure app flows
    await fixture.configureAppFlows({
      rootPreScript: 'context.setAttribute("groovy-app-all","appRootInfo");',
      loginPreEnrichProperties: [{ key: 'groovy-app-all', value: "{#context.attributes['groovy-app-all']}" }],
      loginPostEnrichProperties: [{ claim: 'claimFromAppGroovy', claimValue: "{#context.attributes['authFlow']['groovy-app-all']}" }],
    });

    await fixture.setAppTokenClaims([
      {
        tokenType: TokenClaimTokenTypeEnum.AccessToken,
        claimName: 'domain-groovy-from-profile',
        claimValue: "{#context.attributes['user']['additionalInformation']['claimFromGroovy']}",
      },
      {
        tokenType: TokenClaimTokenTypeEnum.AccessToken,
        claimName: 'app-groovy-from-profile',
        claimValue: "{#context.attributes['user']['additionalInformation']['claimFromAppGroovy']}",
      },
      {
        tokenType: TokenClaimTokenTypeEnum.AccessToken,
        claimName: 'app-groovy-from-authflow',
        claimValue: "{#context.attributes['authFlow']['groovy-app-all']}",
      },
    ]);

    const { jwt, postLoginRedirect } = await fixture.loginAndGetJwt();

    // Domain flow results
    expect(jwt['domain-groovy-from-profile']).toBeDefined();
    expect(jwt['domain-groovy-from-profile']).toEqual('domainRootInfo');

    // App flow results
    expect(jwt['app-groovy-from-profile']).toBeDefined();
    expect(jwt['app-groovy-from-profile']).toEqual('appRootInfo');
    expect(jwt['app-groovy-from-authflow']).toBeDefined();
    expect(jwt['app-groovy-from-authflow']).toEqual('appRootInfo');

    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);
  });

  it('should execute only app flows when domain flow inheritance is disabled', async () => {
    // Configure domain flows (these should NOT execute)
    await fixture.configureDomainFlows({
      rootPreScript: 'context.setAttribute("groovy-domain-all","shouldNotAppear");',
      loginPostEnrichProperties: [{ claim: 'claimFromDomain', claimValue: "{#context.attributes['groovy-domain-all']}" }],
    });

    // Configure app flows
    await fixture.configureAppFlows({
      rootPreScript: 'context.setAttribute("groovy-app-all","appRootInfo");',
      loginPreEnrichProperties: [{ key: 'groovy-app-all', value: "{#context.attributes['groovy-app-all']}" }],
      loginPostEnrichProperties: [{ claim: 'claimFromAppGroovy', claimValue: "{#context.attributes['authFlow']['groovy-app-all']}" }],
    });

    // Disable domain flow inheritance
    await fixture.setFlowsInherited(false);

    await fixture.setAppTokenClaims([
      {
        tokenType: TokenClaimTokenTypeEnum.AccessToken,
        claimName: 'domain-claim',
        claimValue: "{#context.attributes['user']['additionalInformation']['claimFromDomain']}",
      },
      {
        tokenType: TokenClaimTokenTypeEnum.AccessToken,
        claimName: 'app-groovy-from-profile',
        claimValue: "{#context.attributes['user']['additionalInformation']['claimFromAppGroovy']}",
      },
      {
        tokenType: TokenClaimTokenTypeEnum.AccessToken,
        claimName: 'app-groovy-from-authflow',
        claimValue: "{#context.attributes['authFlow']['groovy-app-all']}",
      },
    ]);

    const { jwt, postLoginRedirect } = await fixture.loginAndGetJwt();

    // Domain flow should NOT have executed (inheritance disabled)
    expect(jwt['domain-claim']).not.toBeDefined();

    // App flow should have executed
    expect(jwt['app-groovy-from-profile']).toBeDefined();
    expect(jwt['app-groovy-from-profile']).toEqual('appRootInfo');
    expect(jwt['app-groovy-from-authflow']).toBeDefined();
    expect(jwt['app-groovy-from-authflow']).toEqual('appRootInfo');

    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);
  });
});

describe('Flow Execution - Conditional Flows', () => {
  const EMAIL_SUBJECT = 'Email Send Under Condition';

  it('should not execute conditional flow when condition is not met', async () => {
    await clearEmails(fixture.user.email);

    await fixture.configureAppFlows({
      conditionalFlow: {
        condition: "{#request.params['callout'] != null && #request.params['callout'][0].equals('true') }",
        httpCalloutUrl: `${fixture.openIdConfiguration.issuer}/.well-known/openid-configuration`,
        emailSubject: EMAIL_SUBJECT,
        emailContent: '<a href="${jwks_uri_from_callout}">jwks_uri</a>',
      },
    });

    // Login WITHOUT the callout parameter
    const { postLoginRedirect } = await fixture.loginAndGetJwt();
    await new Promise((r) => setTimeout(r, 1000));
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);

    // Email should NOT have been sent
    const emailReceived = await hasEmail(1000, fixture.user.email);
    expect(emailReceived).toBeFalsy();
  });

  it('should execute conditional flow and send email when condition is met', async () => {
    await clearEmails(fixture.user.email);

    await fixture.configureAppFlows({
      conditionalFlow: {
        condition: "{#request.params['callout'] != null && #request.params['callout'][0].equals('true') }",
        httpCalloutUrl: `${fixture.openIdConfiguration.issuer}/.well-known/openid-configuration`,
        emailSubject: EMAIL_SUBJECT,
        emailContent: '<a href="${jwks_uri_from_callout}">jwks_uri</a>',
      },
    });

    // Login WITH the callout parameter
    const { postLoginRedirect } = await fixture.loginAndGetJwt('callout=true');
    await new Promise((r) => setTimeout(r, 1000));
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, postLoginRedirect);

    // Email SHOULD have been sent
    const emailReceived = await hasEmail(1000, fixture.user.email);
    expect(emailReceived).toBeTruthy();

    const email = await getLastEmail(1000, fixture.user.email);
    expect(email.subject).toBeDefined();
    expect(email.subject).toContain(EMAIL_SUBJECT);
    const jwks_uri = email.extractLink();
    expect(jwks_uri).toEqual(fixture.openIdConfiguration.jwks_uri);
  });
});
