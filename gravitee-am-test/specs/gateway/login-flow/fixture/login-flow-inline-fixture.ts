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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { setupDomainForTest, safeDeleteDomain, DomainOidcConfig } from '@management-commands/domain-management-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { createIdp } from '@management-commands/idp-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { Fixture } from '../../../test-fixture';
import { IdentityProvider } from '@management-models/IdentityProvider';

export const INLINE_USER = {
  username: 'user',
  password: '#CoMpL3X-P@SsW0Rd',
};

export const REDIRECT_URI = 'https://auth-nightly.gravitee.io/myApp/callback';

const INLINE_IDP_OAUTH_SETTINGS = {
  redirectUris: [REDIRECT_URI],
  grantTypes: ['authorization_code', 'client_credentials', 'password', 'refresh_token'],
  scopeSettings: [{ scope: 'openid' }],
};

export interface LoginFlowInlineFixture extends Fixture {
  domain: Domain;
  accessToken: string;
  openIdConfiguration: DomainOidcConfig;
  inmemoryIdp1: IdentityProvider;
  inmemoryIdp2: IdentityProvider;
  defaultIdpId: string;
  appSso1: Application;
  appSso2: Application;
  appSelectionRule: Application;
  appSelectionRuleTwoMatch: Application;
  appSelectionRuleOneFallback: Application;
  appSelectionRuleNoneMatch: Application;
  appIdentifierFirst: Application;
  appIdpPriority: Application;
  appAccountTests: Application;
}

export const setupInlineFixture = async (): Promise<LoginFlowInlineFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    const { domain: d, oidcConfig } = await setupDomainForTest(uniqueName('login-flow-inline', true), {
      accessToken,
      waitForStart: true,
    });
    domain = d;
    expect(domain.id).toBeDefined();
    const openIdConfiguration = oidcConfig;

    const defaultIdpId = `default-idp-${domain.id}`;

    const inmemoryIdp1 = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({
        users: [{ firstname: 'my-user', lastname: 'my-user-lastname', username: 'user', password: '#CoMpL3X-P@SsW0Rd' }],
      }),
      name: uniqueName('inmemory1', true),
    });
    expect(inmemoryIdp1.id).toBeDefined();

    const inmemoryIdp2 = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({
        users: [{ firstname: 'my-user-2', lastname: 'my-user-lastname-2', username: 'user', password: '#CoMpL3X-P@SsW0Rd' }],
      }),
      name: uniqueName('inmemory2', true),
    });
    expect(inmemoryIdp2.id).toBeDefined();

    const appSso1 = await createTestApp(uniqueName('app-sso1', true), domain, accessToken, 'WEB', {
      identityProviders: new Set([{ identity: inmemoryIdp1.id, priority: -1 }]),
      settings: {
        oauth: INLINE_IDP_OAUTH_SETTINGS,
        advanced: { skipConsent: true },
      },
    });
    expect(appSso1.id).toBeDefined();

    const appSso2 = await createTestApp(uniqueName('app-sso2', true), domain, accessToken, 'WEB', {
      identityProviders: new Set([{ identity: defaultIdpId, priority: -1 }]),
      settings: {
        oauth: INLINE_IDP_OAUTH_SETTINGS,
        advanced: { skipConsent: true },
      },
    });
    expect(appSso2.id).toBeDefined();

    const appSelectionRule = await createTestApp(uniqueName('app-selection-rule', true), domain, accessToken, 'WEB', {
      identityProviders: new Set([
        { identity: inmemoryIdp1.id, selectionRule: "{#request.params['username'] matches 'user' }", priority: -1 },
      ]),
      settings: {
        oauth: INLINE_IDP_OAUTH_SETTINGS,
        advanced: { skipConsent: true },
      },
    });
    expect(appSelectionRule.id).toBeDefined();

    // Two IDPs, both with rules; first matches username "user" → authenticates via inmemoryIdp1
    const appSelectionRuleTwoMatch = await createTestApp(uniqueName('app-selection-rule-two-match', true), domain, accessToken, 'WEB', {
      identityProviders: new Set([
        { identity: inmemoryIdp1.id, selectionRule: "{#request.params['username'] matches 'user' }", priority: -1 },
        { identity: inmemoryIdp2.id, selectionRule: "{#request.params['username'] matches 'something-else' }", priority: -1 },
      ]),
      settings: {
        oauth: INLINE_IDP_OAUTH_SETTINGS,
        advanced: { skipConsent: true },
      },
    });
    expect(appSelectionRuleTwoMatch.id).toBeDefined();

    // Two IDPs; first has a non-matching rule, second has no rule (fallback) → authenticates via inmemoryIdp2
    const appSelectionRuleOneFallback = await createTestApp(uniqueName('app-selection-rule-one-fallback', true), domain, accessToken, 'WEB', {
      identityProviders: new Set([
        { identity: inmemoryIdp1.id, selectionRule: "{#request.params['username'] matches 'something-else' }", priority: -1 },
        { identity: inmemoryIdp2.id, priority: -1 },
      ]),
      settings: {
        oauth: INLINE_IDP_OAUTH_SETTINGS,
        advanced: { skipConsent: true },
      },
    });
    expect(appSelectionRuleOneFallback.id).toBeDefined();

    // Two IDPs, both with non-matching rules → no IDP selected → login fails
    const appSelectionRuleNoneMatch = await createTestApp(uniqueName('app-selection-rule-none-match', true), domain, accessToken, 'WEB', {
      identityProviders: new Set([
        { identity: inmemoryIdp1.id, selectionRule: "{#request.params['username'] matches 'something-else' }", priority: -1 },
        { identity: inmemoryIdp2.id, selectionRule: "{#request.params['username'] matches 'something-else' }", priority: -1 },
      ]),
      settings: {
        oauth: INLINE_IDP_OAUTH_SETTINGS,
        advanced: { skipConsent: true },
      },
    });
    expect(appSelectionRuleNoneMatch.id).toBeDefined();

    const appIdentifierFirst = await createTestApp(uniqueName('app-identifier-first', true), domain, accessToken, 'WEB', {
      identityProviders: new Set([{ identity: inmemoryIdp1.id, priority: -1 }]),
      settings: {
        login: { identifierFirstEnabled: true, inherited: false },
        oauth: INLINE_IDP_OAUTH_SETTINGS,
        advanced: { skipConsent: true },
      },
    });
    expect(appIdentifierFirst.id).toBeDefined();

    const appIdpPriority = await createTestApp(uniqueName('app-idp-priority', true), domain, accessToken, 'WEB', {
      identityProviders: new Set([
        { identity: inmemoryIdp1.id, priority: 2 },
        { identity: inmemoryIdp2.id, priority: 1 },
      ]),
      settings: {
        oauth: {
          redirectUris: [REDIRECT_URI],
          grantTypes: ['authorization_code', 'client_credentials', 'password', 'refresh_token'],
          scopeSettings: [{ scope: 'openid' }, { scope: 'profile' }, { scope: 'email' }],
        },
        advanced: { skipConsent: true },
      },
    });
    expect(appIdpPriority.id).toBeDefined();

    // Wrap the last app creation in waitForSyncAfter so the gateway picks up all apps created since domain start.
    const appAccountTests = await waitForSyncAfter(domain.id, () =>
      createTestApp(uniqueName('app-account-tests', true), domain, accessToken, 'WEB', {
        identityProviders: new Set([{ identity: defaultIdpId, priority: -1 }]),
        settings: {
          oauth: INLINE_IDP_OAUTH_SETTINGS,
          advanced: { skipConsent: true },
        },
      }),
    );
    expect(appAccountTests.id).toBeDefined();

    return {
      domain,
      accessToken,
      openIdConfiguration,
      inmemoryIdp1,
      inmemoryIdp2,
      defaultIdpId,
      appSso1,
      appSso2,
      appSelectionRule,
      appSelectionRuleTwoMatch,
      appSelectionRuleOneFallback,
      appSelectionRuleNoneMatch,
      appIdentifierFirst,
      appIdpPriority,
      appAccountTests,
      cleanUp: async () => {
        if (domain?.id && accessToken) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup domain after fixture setup failure:', cleanupError);
      }
    }
    throw error;
  }
};
