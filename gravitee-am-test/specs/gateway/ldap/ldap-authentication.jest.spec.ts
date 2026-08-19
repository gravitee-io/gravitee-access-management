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
import { performGet, requestToken, logoutUser } from '@gateway-commands/oauth-oidc-commands';
import { listUsers } from '@management-commands/user-management-commands';
import { setup, retryImmediatelyForThisFile } from '../../test-fixture';
import { jira } from '@specs-utils/jira';
import { LdapFixture, setupLdapFixture, LDAP_ADMIN, LDAP_JDOE, LDAP_HASHED, LDAP_COMPARE, LDAP_COMPARE_3P, REDIRECT_URI } from './fixtures/ldap-fixture';

setup(200000);

/**
 * Several describes reconfigure the provider and restore it in afterAll, so a
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

describe('LDAP authentication - successful login', () => {
  it(jira`should authenticate a user that exists in the directory ${'AM-4037'}`, async () => {
    const { postLogin } = await authenticate(LDAP_ADMIN);
    const loginResponse = await getHeaderLocation(postLogin);

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);

    const token = await requestToken(fixture.app, fixture.openIdConfiguration, loginResponse);
    expect(token.body.access_token).toEqual(expect.any(String));

    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });

  it(jira`should provision the directory user into the domain on first login ${'AM-4037'}`, async () => {
    // Authenticates in-test rather than relying on the preceding case, so this
    // test passes when run on its own.
    const { postLogin } = await authenticate(LDAP_ADMIN);
    const loginResponse = await getHeaderLocation(postLogin);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);

    const page = await listUsers(fixture.domain.id, fixture.accessToken, LDAP_ADMIN.username);

    expect(page.totalCount).toBeGreaterThan(0);
    const user = page.data[0];
    expect(user.username).toEqual(LDAP_ADMIN.username);
    // `source` carries the identity provider NAME, not its id.
    expect(user.source).toEqual(fixture.idp.name);
  });

  it(jira`should authenticate a second directory user ${'AM-4037'}`, async () => {
    const { postLogin } = await authenticate(LDAP_JDOE);
    const loginResponse = await getHeaderLocation(postLogin);

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });
});

describe('LDAP authentication - rejected login', () => {
  it(jira`should reject a username that does not exist in the directory ${'AM-7447'}`, async () => {
    const { postLogin } = await authenticate({ username: 'not-in-the-directory', password: 'whatever' });

    expect(postLogin.headers['location']).toContain('error=login_failed');
  });

  it(jira`should reject a valid directory user presenting the wrong password ${'AM-7447'}`, async () => {
    const { postLogin } = await authenticate({ username: LDAP_ADMIN.username, password: 'wrong-password' });

    expect(postLogin.headers['location']).toContain('error=login_failed');
  });
});

/**
 * The user search filter is '(uid={0})' with the submitted username substituted
 * into it. If that substitution is not escaped, LDAP filter metacharacters let a
 * caller rewrite the query — '*' turning it into '(uid=*)' which matches every
 * entry, or ')(uid=*' closing the clause and injecting another.
 *
 * Every one of these must fail closed. A success here would be a serious
 * authentication bypass, so the assertions are deliberately strict.
 */
describe('LDAP authentication - search filter injection', () => {
  const INJECTION_ATTEMPTS: Array<[string, string]> = [
    ['a bare wildcard', '*'],
    ['a wildcard suffix', 'ldap-adm*'],
    ['a clause-closing injection', ')(uid=*'],
    ['an always-true injection', '*)(|(uid=*'],
    ['an escaped backslash', 'ldap-admin\\'],
  ];

  // The jira tagged template validates EVERY interpolation as a Jira key, so the
  // label cannot be interpolated into it. Build the tag once and append it.
  const TAG = jira`${'AM-7447'}`;

  INJECTION_ATTEMPTS.forEach(([label, username]) => {
    it(`should reject ${label} in the username ${TAG}`, async () => {
      const { postLogin } = await authenticate({ username, password: LDAP_ADMIN.password });

      expect(postLogin.headers['location']).toContain('error=login_failed');
      // A successful login redirects to the callback carrying an authorization
      // code. Matching on a bare 'code=' would false-positive on the
      // 'response_type=code' and 'error_code=' already in the login URL.
      expect(postLogin.headers['location']).not.toContain(`${REDIRECT_URI}?code=`);
    });
  });

  it(jira`should not provision any user from an injection attempt ${'AM-7447'}`, async () => {
    // A wildcard that matched an entry would leave a provisioned user behind even
    // if the token exchange failed, so assert the directory was never resolved.
    const page = await listUsers(fixture.domain.id, fixture.accessToken, '*');

    const usernames = page.data.map((u) => u.username);
    expect(usernames).not.toContain('*');
    expect(usernames).not.toContain(')(uid=*');
  });
});

describe('LDAP authentication - hashed credentials', () => {
  it(jira`should authenticate a user whose password is stored hashed ${'AM-7447'}`, async () => {
    // slapd verifies the {SSHA} hash during the bind, so AM never needs the
    // plaintext. A regression here would lock out every directory that stores
    // credentials hashed, which is essentially all of them.
    const { postLogin } = await authenticate(LDAP_HASHED);
    const loginResponse = await getHeaderLocation(postLogin);

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });

  it(jira`should reject a wrong password for a hashed-credential user ${'AM-7447'}`, async () => {
    const { postLogin } = await authenticate({ username: LDAP_HASHED.username, password: 'not-the-password' });

    expect(postLogin.headers['location']).toContain('error=login_failed');
  });
});

describe('LDAP authentication - compare mode password encoders', () => {
  afterAll(async () => {
    await fixture.reconfigureIdp({});
  });

  it(jira`should authenticate against a locally hashed password ${'AM-7447'}`, async () => {
    // compareuser stores a raw base64 SHA-1 digest. AM hashes the submitted
    // password the same way and compares, rather than delegating to a bind.
    await fixture.reconfigureIdp({ passwordAlgorithm: 'SHA', passwordEncoding: 'Base64' });

    const { postLogin } = await authenticate(LDAP_COMPARE);
    const loginResponse = await getHeaderLocation(postLogin);

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });

  it(jira`should authenticate a third-party-encoded password without a scheme prefix ${'AM-7447'}`, async () => {
    // compareuser3p stores the bare digest. hashEncodedByThirdParty tells AM to
    // compare without wrapping it in "{SHA}".
    await fixture.reconfigureIdp({
      passwordAlgorithm: 'SHA',
      passwordEncoding: 'Base64',
      hashEncodedByThirdParty: true,
    });

    const { postLogin } = await authenticate(LDAP_COMPARE_3P);
    const loginResponse = await getHeaderLocation(postLogin);

    expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
    await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
  });

  it(jira`should reject a wrong password in compare mode ${'AM-7447'}`, async () => {
    await fixture.reconfigureIdp({ passwordAlgorithm: 'SHA', passwordEncoding: 'Base64' });

    const { postLogin } = await authenticate({ username: LDAP_COMPARE.username, password: 'wrong-password' });

    expect(postLogin.headers['location']).toContain('error=login_failed');
  });
});
