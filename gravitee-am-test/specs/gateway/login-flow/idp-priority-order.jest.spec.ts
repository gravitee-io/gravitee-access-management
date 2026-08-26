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
import { getHeaderLocation, login } from '@gateway-commands/login-commands';
import { logoutUser, performGet, requestToken } from '@gateway-commands/oauth-oidc-commands';
import { patchApplication } from '@management-commands/application-management-commands';
import { updateIdp } from '@management-commands/idp-management-commands';
import { retryImmediatelyForThisFile, setup } from '../../test-fixture';
import { INLINE_USER, LoginFlowInlineFixture, REDIRECT_URI, setupInlineFixture } from './fixture/login-flow-inline-fixture';

setup(200000);
// The priorities are swapped once, between the two describes below, so order matters.
retryImmediatelyForThisFile();

/**
 * AM-2179 / UC-AM26 — altering the order in which an application's identity providers are tried.
 *
 * That priority decides who answers is already covered, but only for one fixed configuration.
 * Nothing changed the order and showed the outcome change with it, which is the use case here.
 *
 * This works because both inline providers hold the SAME username with a different profile:
 * whichever one answers is visible in the returned `given_name`. A test using two users held in
 * different providers cannot show anything, since both sign-ins succeed whatever the order.
 */
const IDP1_GIVEN_NAME = 'my-user';
const IDP2_GIVEN_NAME = 'my-user-2';
const IDP1_FAMILY_NAME = 'my-user-lastname';

/** A username added to the first provider only, so no ordering can decide who answers for it. */
const IDP1_ONLY_USERNAME = 'only-in-idp1';
const IDP1_ONLY_GIVEN_NAME = 'sole-holder';

let fixture: LoginFlowInlineFixture;

/** Signs in through the priority application and returns the profile the winning provider held. */
const signInAndReadProfile = async (username: string, password: string): Promise<Record<string, any>> => {
  const clientId = fixture.appIdpPriority.settings.oauth.clientId;

  const authResponse = await performGet(
    fixture.openIdConfiguration.authorization_endpoint,
    `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&scope=openid%20profile`,
  ).expect(302);

  const postLogin = await login(authResponse, username, clientId, password);
  const loginResponse = await getHeaderLocation(postLogin);
  expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);

  const tokenResponse = await requestToken(fixture.appIdpPriority, fixture.openIdConfiguration, loginResponse);
  expect(tokenResponse.status).toBe(200);

  const userInfo = await performGet(fixture.openIdConfiguration.userinfo_endpoint, '', {
    Authorization: `Bearer ${tokenResponse.body.access_token}`,
  }).expect(200);

  await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  return userInfo.body;
};

beforeAll(async () => {
  fixture = await setupInlineFixture();
  expect(fixture.openIdConfiguration).toBeDefined();

  // Add a user the second provider does not hold, for the "held in only one provider" case.
  await updateIdp(
    fixture.domain.id,
    fixture.accessToken,
    {
      name: fixture.inmemoryIdp1.name,
      // Required on update even though it is unchanged — omitting it returns "type: must not be blank".
      type: fixture.inmemoryIdp1.type,
      configuration: JSON.stringify({
        users: [
          {
            firstname: IDP1_GIVEN_NAME,
            lastname: 'my-user-lastname',
            username: INLINE_USER.username,
            password: INLINE_USER.password,
            email: INLINE_USER.email,
          },
          {
            firstname: IDP1_ONLY_GIVEN_NAME,
            lastname: 'sole-holder-lastname',
            username: IDP1_ONLY_USERNAME,
            password: INLINE_USER.password,
            email: 'only-in-idp1@example.com',
          },
        ],
      }),
    },
    fixture.inmemoryIdp1.id,
  );

  // The gateway holds the provider in memory, so wait until the added user can actually sign in.
  const deadline = Date.now() + 30_000;
  for (;;) {
    try {
      await signInAndReadProfile(IDP1_ONLY_USERNAME, INLINE_USER.password);
      break;
    } catch (e) {
      if (Date.now() > deadline) {
        throw e;
      }
    }
  }
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Identity provider processing order (AM-2179)', () => {
  describe('with the shipped order', () => {
    it(jira`the higher priority provider answers for a username both hold ${'AM-2179'}`, async () => {
      // appIdpPriority ships as inmemoryIdp1=2, inmemoryIdp2=1, and the lower number wins.
      const profile = await signInAndReadProfile(INLINE_USER.username, INLINE_USER.password);

      expect(profile.given_name).toEqual(IDP2_GIVEN_NAME);
    });

    it(jira`a user held in only one provider signs in whatever the order ${'AM-2179'}`, async () => {
      const profile = await signInAndReadProfile(IDP1_ONLY_USERNAME, INLINE_USER.password);

      expect(profile.given_name).toEqual(IDP1_ONLY_GIVEN_NAME);
    });
  });

  describe('after the priorities are swapped', () => {
    beforeAll(async () => {
      await patchApplication(
        fixture.domain.id,
        fixture.accessToken,
        {
          identityProviders: new Set([
            { identity: fixture.inmemoryIdp1.id, priority: 1 },
            { identity: fixture.inmemoryIdp2.id, priority: 2 },
          ]),
        } as any,
        fixture.appIdpPriority.id,
      );

      // An application change does not reliably advance the domain's lastSync, so poll the
      // observable — who actually answers a sign-in — rather than waiting on a sync marker.
      const deadline = Date.now() + 30_000;
      for (;;) {
        const profile = await signInAndReadProfile(INLINE_USER.username, INLINE_USER.password);
        if (profile.given_name === IDP1_GIVEN_NAME) {
          return;
        }
        if (Date.now() > deadline) {
          throw new Error('the swapped priority was not applied within 30s; the other provider still answered');
        }
      }
    });

    it(jira`swapping the order changes which provider answers ${'AM-2179'}`, async () => {
      const profile = await signInAndReadProfile(INLINE_USER.username, INLINE_USER.password);

      // The same username now resolves to the other provider's profile. The family name is
      // checked too, since the providers differ in both and one field alone could be coincidence.
      expect(profile.given_name).toEqual(IDP1_GIVEN_NAME);
      expect(profile.family_name).toEqual(IDP1_FAMILY_NAME);
    });

    it(jira`the single-provider user is unaffected by the order ${'AM-2179'}`, async () => {
      const profile = await signInAndReadProfile(IDP1_ONLY_USERNAME, INLINE_USER.password);

      expect(profile.given_name).toEqual(IDP1_ONLY_GIVEN_NAME);
    });
  });
});
