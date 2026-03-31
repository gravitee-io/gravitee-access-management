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
import { CallFactorFixture, CALL_MOCK_CODE, setupCallFactorFixture } from './fixtures/call-factor-fixture';
import { enrollCallFactor, verifyFactor } from './fixtures/mfa-flow-helpers';

setup(200000);

let fixture: CallFactorFixture;

beforeAll(async () => {
  fixture = await setupCallFactorFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Call MFA Factor (AM-5514)', () => {
  it(jira`should redirect to MFA enroll page after login ${'AM-5514'}`, async () => {
    const user = await buildCreateAndTestUser(fixture.domain.id, fixture.accessToken, 301);
    const clientId = fixture.application.settings.oauth.clientId;
    const authorize = await loginUserNameAndPassword(clientId, user, user.password, false, fixture.oidc, fixture.domain);
    expect(authorize.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/mfa/enroll`);
  });

  it(jira`should complete call factor MFA flow: enrol with phone -> challenge -> verify ${'AM-5514'}`, async () => {
    const user = await buildCreateAndTestUser(fixture.domain.id, fixture.accessToken, 302);
    const clientId = fixture.application.settings.oauth.clientId;
    const authorize = await loginUserNameAndPassword(clientId, user, user.password, false, fixture.oidc, fixture.domain);

    const enrolled = await enrollCallFactor(authorize, fixture.callFactor);

    const authorizeRedirect = await performGet(enrolled.headers['location'], '', {
      Cookie: enrolled.headers['set-cookie'],
    }).expect(302);
    expect(authorizeRedirect.headers['location']).toContain(`${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/mfa/challenge`);

    const challengeResult = await verifyFactor(authorizeRedirect, CALL_MOCK_CODE, fixture.callFactor);
    expect(challengeResult.headers['location']).toContain('/oauth/authorize');

    await logoutUser(fixture.oidc.end_session_endpoint, challengeResult);
  });
});
