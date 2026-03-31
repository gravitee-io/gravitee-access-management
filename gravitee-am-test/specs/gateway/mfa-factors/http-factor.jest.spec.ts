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
import { loginUserNameAndPassword } from '@gateway-commands/login-commands';
import { logoutUser, performGet } from '@gateway-commands/oauth-oidc-commands';
import { buildCreateAndTestUser } from '@management-commands/user-management-commands';
import { setup } from '../../test-fixture';
import { jira } from '@specs-utils/jira';
import { HttpFactorFixture, setupHttpFactorFixture } from './fixtures/http-factor-fixture';
import { enrollHttpFactor, verifyFactor } from './fixtures/mfa-flow-helpers';

setup(200000);

let fixture: HttpFactorFixture;

beforeAll(async () => {
  fixture = await setupHttpFactorFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('HTTP MFA Factor (AM-5514)', () => {
  it(jira`should redirect to MFA enroll page after login ${'AM-5514'}`, async () => {
    const user = await buildCreateAndTestUser(fixture.domain.id, fixture.accessToken, 101);
    const clientId = fixture.application.settings.oauth.clientId;
    const authorize = await loginUserNameAndPassword(clientId, user, user.password, false, fixture.oidc, fixture.domain);
    expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/mfa/enroll`);
  });

  it(jira`should enrol in HTTP factor then reach challenge after authorize redirect ${'AM-5514'}`, async () => {
    const user = await buildCreateAndTestUser(fixture.domain.id, fixture.accessToken, 102);
    const clientId = fixture.application.settings.oauth.clientId;
    const authorize = await loginUserNameAndPassword(clientId, user, user.password, false, fixture.oidc, fixture.domain);

    const enrolled = await enrollHttpFactor(authorize, fixture.httpFactor);
    expect(enrolled.headers['location']).toContain('/oauth/authorize');

    const authorizeRedirect = await performGet(enrolled.headers['location'], '', {
      Cookie: enrolled.headers['set-cookie'],
    }).expect(302);
    expect(authorizeRedirect.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/mfa/challenge`);
  });

  it(jira`should complete full MFA flow: login -> enrol -> challenge -> verify ${'AM-5514'}`, async () => {
    const user = await buildCreateAndTestUser(fixture.domain.id, fixture.accessToken, 103);
    const clientId = fixture.application.settings.oauth.clientId;
    const authorize = await loginUserNameAndPassword(clientId, user, user.password, false, fixture.oidc, fixture.domain);

    const enrolled = await enrollHttpFactor(authorize, fixture.httpFactor);

    const authorizeRedirect = await performGet(enrolled.headers['location'], '', {
      Cookie: enrolled.headers['set-cookie'],
    }).expect(302);

    // WireMock echoes back success for any code
    const challengeResult = await verifyFactor(authorizeRedirect, '123456', fixture.httpFactor);
    expect(challengeResult.headers['location']).toContain('/oauth/authorize');

    await logoutUser(fixture.oidc.end_session_endpoint, challengeResult);
  });
});
