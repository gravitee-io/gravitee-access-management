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
import { login, getHeaderLocation } from '@gateway-commands/login-commands';
import { performGet, logoutUser } from '@gateway-commands/oauth-oidc-commands';
import { setup, retryImmediatelyForThisFile } from '../../test-fixture';
import { jira } from '@specs-utils/jira';
import { LdapFixture, setupLdapFixture, LDAP_ADMIN, REDIRECT_URI } from './fixtures/ldap-fixture';

setup(200000);

/**
 * Every describe here reconfigures the provider and restores it in afterAll, so a
 * deferred retry would run after the restore, against the wrong configuration.
 * Retrying in place also absorbs the window where the gateway has reported the
 * domain synced but has not yet rebuilt the identity provider.
 */
retryImmediatelyForThisFile();

let fixture: LdapFixture;

beforeAll(async () => {
  fixture = await setupLdapFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

function authorizeUrlParams(): string {
  const clientId = fixture.app.settings.oauth.clientId;
  return `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&scope=openid`;
}

async function authenticate(user: { username: string; password: string }) {
  const clientId = fixture.app.settings.oauth.clientId;
  const authResponse = await performGet(fixture.openIdConfiguration.authorization_endpoint, authorizeUrlParams()).expect(
    302,
  );
  const postLogin = await login(authResponse, user.username, clientId, user.password);
  return { postLogin, clientId };
}

describe('LDAP authentication - custom user search filter', () => {
  afterAll(async () => {
    await fixture.reconfigureIdp({});
  });

  it(jira`should authenticate by email when the filter matches mail ${'AM-7447'}`, async () => {
    await fixture.reconfigureIdp({ userSearchFilter: '(mail={0})' });

    const { postLogin } = await authenticate({ username: 'ldap-admin@gravitee.io', password: LDAP_ADMIN.password });
    const loginResponse = await getHeaderLocation(postLogin);

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });

  it(jira`should reject the uid once the filter matches only mail ${'AM-7447'}`, async () => {
    await fixture.reconfigureIdp({ userSearchFilter: '(mail={0})' });

    const { postLogin } = await authenticate(LDAP_ADMIN);

    expect(postLogin.headers['location']).toContain('error=login_failed');
  });

  it(jira`should accept either uid or mail with a compound filter ${'AM-7447'}`, async () => {
    await fixture.reconfigureIdp({ userSearchFilter: '(|(uid={0})(mail={0}))' });

    const byUid = await authenticate(LDAP_ADMIN);
    const uidResponse = await getHeaderLocation(byUid.postLogin);
    expect(uidResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, uidResponse);

    const byMail = await authenticate({ username: 'ldap-admin@gravitee.io', password: LDAP_ADMIN.password });
    const mailResponse = await getHeaderLocation(byMail.postLogin);
    expect(mailResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, mailResponse);
  });
});

describe('LDAP authentication - directory connection failures', () => {
  afterAll(async () => {
    await fixture.reconfigureIdp({});
  });

  it(jira`should fail login when the service account credentials are wrong ${'AM-7447'}`, async () => {
    await fixture.reconfigureIdp({ contextSourcePassword: 'not-the-bind-password' });

    const { postLogin } = await authenticate(LDAP_ADMIN);

    expect(postLogin.headers['location']).toContain('error=login_failed');
  });

  it(jira`should fail login when the directory is unreachable ${'AM-7447'}`, async () => {
    await fixture.reconfigureIdp({ contextSourceUrl: 'ldap://openldap:3899' });

    const { postLogin } = await authenticate(LDAP_ADMIN);

    // Asserted deliberately: an unreachable directory is reported as
    // invalid_user / "Invalid or unknown user" — identical to a mistyped
    // username, so an outage is indistinguishable from a bad login. This pins
    // the current behaviour; if AM ever reports infrastructure failures
    // distinctly, this test should fail and be updated on purpose.
    expect(postLogin.headers['location']).toContain('error=login_failed');
    expect(postLogin.headers['location']).toContain('error_code=invalid_user');
  });

  it(jira`should recover once the directory is reachable again ${'AM-7447'}`, async () => {
    await fixture.reconfigureIdp({});

    const { postLogin } = await authenticate(LDAP_ADMIN);
    const loginResponse = await getHeaderLocation(postLogin);

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });
});

describe('LDAP authentication - user search base', () => {
  afterAll(async () => {
    // Restore the working search base for any later test in this file.
    await fixture.reconfigureIdp({});
  });

  it(jira`should reject a valid user when the search base does not contain their entry ${'AM-7447'}`, async () => {
    // The seeded entries live under ou=people. Pointing the search base at an OU
    // they are not in makes them unfindable, so a correct password still fails.
    await fixture.reconfigureIdp({ userSearchBase: 'ou=groups' });

    const { postLogin } = await authenticate(LDAP_ADMIN);

    expect(postLogin.headers['location']).toContain('error=login_failed');
  });

  it(jira`should authenticate once the search base again contains the entry ${'AM-7447'}`, async () => {
    await fixture.reconfigureIdp({ userSearchBase: 'ou=people' });

    const { postLogin } = await authenticate(LDAP_ADMIN);
    const loginResponse = await getHeaderLocation(postLogin);

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });
});
