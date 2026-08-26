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
import { jira } from '@specs-utils/jira';
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';
import {
  INLINE_USER,
  LoginFlowInlineFixture,
  REDIRECT_URI,
  setupInlineFixture,
  signInAndReadProfile,
} from './fixture/login-flow-inline-fixture';

setup(200000);

/**
 * A selection rule that cannot be evaluated as a boolean. `selectionRuleMatches` catches the
 * failure, logs a warning and returns false, so the provider is skipped rather than the sign-in
 * failing outright. A typo in a rule therefore silently excludes a provider.
 */
const MALFORMED_RULE = "{#request.params['username'] matches";

/** A well-formed rule that does not match the fixture user. */
const NON_MATCHING_RULE = "{#request.params['username'] matches 'nobody'}";

/** Profile held by inmemoryIdp2, which is how a test tells the two providers apart. */
const IDP2_GIVEN_NAME = 'my-user-2';

const APP_OAUTH_SETTINGS = {
  redirectUris: [REDIRECT_URI],
  grantTypes: ['authorization_code', 'client_credentials', 'password', 'refresh_token'],
  // profile is needed so userinfo returns given_name.
  scopeSettings: [{ scope: 'openid' }, { scope: 'profile' }],
};

let fixture: LoginFlowInlineFixture;
let appMalformedWithFallback: any;
let appMalformedOnly: any;
let appUnruledAlongsideRuled: any;

const buildApp = (name: string, identityProviders: any[]) =>
  createTestApp(uniqueName(name, true), fixture.domain, fixture.accessToken, 'WEB', {
    identityProviders: new Set(identityProviders),
    settings: { oauth: APP_OAUTH_SETTINGS, advanced: { skipConsent: true } },
  });

beforeAll(async () => {
  fixture = await setupInlineFixture();
  expect(fixture.openIdConfiguration).toBeDefined();

  appMalformedWithFallback = await buildApp('app-malformed-rule', [
    { identity: fixture.inmemoryIdp1.id, selectionRule: MALFORMED_RULE, priority: 1 },
    { identity: fixture.inmemoryIdp2.id, priority: 2 },
  ]);

  appUnruledAlongsideRuled = await buildApp('app-unruled-alongside-ruled', [
    { identity: fixture.inmemoryIdp1.id, selectionRule: NON_MATCHING_RULE, priority: 1 },
    { identity: fixture.inmemoryIdp2.id, priority: 2 },
  ]);

  // The last creation is wrapped so the gateway has picked up every application before any
  // test runs, following the same pattern as the fixture itself.
  appMalformedOnly = await waitForSyncAfter(fixture.domain.id, () =>
    buildApp('app-malformed-only', [{ identity: fixture.inmemoryIdp1.id, selectionRule: MALFORMED_RULE, priority: 1 }]),
  );
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Identity provider selection rule - a rule that cannot be evaluated', () => {
  it(jira`the provider carrying it is skipped rather than failing the sign-in ${'AM-2171'}`, async () => {
    // Getting the other provider's profile back is what shows the broken rule excluded its
    // provider — asserting only that sign-in succeeded would not.
    const profile = await signInAndReadProfile(
      appMalformedWithFallback,
      fixture.openIdConfiguration,
      INLINE_USER.username,
      INLINE_USER.password,
    );
    expect(profile.given_name).toEqual(IDP2_GIVEN_NAME);
  });

  it(jira`sign-in is refused when the broken rule leaves no provider ${'AM-2171'}`, async () => {
    const loginResponse = await loginUserNameAndPassword(
      appMalformedOnly.settings.oauth.clientId,
      INLINE_USER,
      INLINE_USER.password,
      false,
      fixture.openIdConfiguration,
      fixture.domain,
    );

    // Refused the same way as a rule that simply does not match, rather than surfacing an
    // internal error: the user exists in that provider and would sign in without the rule.
    expect(loginResponse.headers['location']).toContain('error=login_failed&error_code=invalid_user');
  });
});

describe('Identity provider selection rule - a provider carrying no rule', () => {
  it(jira`takes part when the provider that has a rule does not match ${'AM-2171'}`, async () => {
    // `selectionRuleMatches` returns true for a blank rule, which is what every provider without
    // one relies on. The unruled provider has the lower priority, so a pass here can only mean
    // the ruled provider was excluded rather than simply being beaten to it.
    const profile = await signInAndReadProfile(
      appUnruledAlongsideRuled,
      fixture.openIdConfiguration,
      INLINE_USER.username,
      INLINE_USER.password,
    );
    expect(profile.given_name).toEqual(IDP2_GIVEN_NAME);
  });
});
