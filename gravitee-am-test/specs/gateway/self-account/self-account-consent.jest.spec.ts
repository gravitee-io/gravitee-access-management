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
import {
  performDelete,
  performFormPost,
  performGet,
  performPost,
  extractXsrfTokenAndActionResponse,
} from '@gateway-commands/oauth-oidc-commands';
import { createUser, listUserConsents } from '@management-commands/user-management-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { SelfAccountFixture, setupFixture } from './fixture/self-account-fixture';
import { setup } from '../../test-fixture';

setup(300000);

// A consent record only exists once a user approves scopes, so tests that need one drive the
// authorization-code flow. Tests that change state use a user of their own, so order does not matter.
const SETTINGS = {
  selfServiceAccountManagementSettings: {
    enabled: true,
    resetPassword: { oldPasswordRequired: false, tokenAge: 0 },
  },
};

let fixture: SelfAccountFixture;

const accountUrl = (path: string) => `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/account/api${path}`;

const tokenFor = async (user: { username: string; password: string }) => {
  const response = await performPost(
    fixture.oidc.token_endpoint,
    '',
    `grant_type=password&username=${user.username}&password=${user.password}`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: 'Basic ' + applicationBase64Token(fixture.application),
    },
  );
  expect(response.status).toBe(200);
  return response.body.access_token;
};

const newUser = async () => {
  const user: any = {
    username: uniqueName('consentUser', true),
    password: 'Password123!',
    firstName: 'Consent',
    lastName: 'User',
    email: 'consentuser@acme.fr',
    preRegistration: false,
  };
  const created = await createUser(fixture.domain.id, fixture.accessToken, user);
  user.id = created.id;
  return user;
};

/** Signs in and approves the consent screen — the only way a consent record is created. */
const approveConsent = async (user: { username: string; password: string }) => {
  const clientId = fixture.application.settings.oauth.clientId;
  const params = `?response_type=code&client_id=${clientId}&redirect_uri=https://callback&scope=openid%20email`;

  const start = await performGet(fixture.oidc.authorization_endpoint, params).expect(302);
  const login = await extractXsrfTokenAndActionResponse(start);
  const postLogin = await performFormPost(
    login.action,
    '',
    { 'X-XSRF-TOKEN': login.token, username: user.username, password: user.password, client_id: clientId },
    { Cookie: login.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
  ).expect(302);

  // After login the flow returns to /authorize, which then redirects to the consent screen.
  const consentLocation = await performGet(postLogin.headers['location'], '', { Cookie: postLogin.headers['set-cookie'] }).expect(302);
  expect(consentLocation.headers['location']).toContain('/oauth/consent');

  const consent = await extractXsrfTokenAndActionResponse(consentLocation);
  await performFormPost(
    consent.action,
    '',
    { 'scope.openid': true, 'scope.email': true, user_oauth_approval: true, 'X-XSRF-TOKEN': consent.token },
    { Cookie: consent.headers['set-cookie'], 'Content-type': 'application/x-www-form-urlencoded' },
  ).expect(302);
};

const listConsent = (token: string) => performGet(accountUrl('/consent'), '', { Authorization: `Bearer ${token}` });

beforeAll(async () => {
  fixture = await setupFixture(SETTINGS, 'self-account-consent');
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('SelfAccount - Consent', () => {
  it(jira`a user who has approved nothing has an empty consent list ${'AM-7652'}`, async () => {
    // The control for every test below: the list is not simply returning everything it can find.
    const user = await newUser();
    const token = await tokenFor(user);

    const response = await listConsent(token);

    expect(response.status).toBe(200);
    expect(response.body).toEqual([]);
  });

  it(jira`approving scopes puts a consent on the user's list ${'AM-7652'}`, async () => {
    const user = await newUser();
    await approveConsent(user);
    const token = await tokenFor(user);

    const response = await listConsent(token);

    expect(response.status).toBe(200);
    expect(response.body.length).toBeGreaterThan(0);
    const scopes = response.body.map((c: any) => c.scope);
    expect(scopes).toEqual(expect.arrayContaining(['openid', 'email']));
    expect(response.body[0].clientId).toBe(fixture.application.settings.oauth.clientId);
  });

  it(jira`the list holds only the caller's own consents ${'AM-7652'}`, async () => {
    const owner = await newUser();
    const other = await newUser();
    await approveConsent(owner);

    const ownerList = await listConsent(await tokenFor(owner));
    const otherList = await listConsent(await tokenFor(other));

    expect(ownerList.body.length).toBeGreaterThan(0);
    expect(otherList.body).toEqual([]);
  });

  it(jira`a user can read one of their own consents by id ${'AM-7652'}`, async () => {
    const user = await newUser();
    await approveConsent(user);
    const token = await tokenFor(user);
    const consentId = (await listConsent(token)).body[0].id;

    const response = await performGet(accountUrl(`/consent/${consentId}`), '', { Authorization: `Bearer ${token}` });

    expect(response.status).toBe(200);
    expect(response.body.id).toBe(consentId);
  });

  it(jira`an unknown consent id is not found ${'AM-7652'}`, async () => {
    const user = await newUser();
    const token = await tokenFor(user);

    const response = await performGet(accountUrl('/consent/does-not-exist'), '', { Authorization: `Bearer ${token}` });

    expect(response.status).toBe(404);
  });

  it(jira`a user can revoke their own consent, and it leaves the list ${'AM-7652'}`, async () => {
    const user = await newUser();
    await approveConsent(user);
    const token = await tokenFor(user);
    const before = await listConsent(token);
    const consentId = before.body[0].id;

    const removed = await performDelete(accountUrl(`/consent/${consentId}`), '', { Authorization: `Bearer ${token}` });
    expect(removed.status).toBe(204);

    // Revoking a consent revokes the tokens issued under it, so the check needs a fresh one.
    const after = await listConsent(await tokenFor(user));
    expect(after.body.map((c: any) => c.id)).not.toContain(consentId);
  });

  // Un-skip once AM-7654 is fixed. A delete is not scoped to the caller either: the consent is
  // found by id alone, and the caller's id is used only to build the audit record.
  it.skip(jira`revoking another user's consent is refused (AM-7654) ${'AM-7652'}`, async () => {
    const owner = await newUser();
    const other = await newUser();
    await approveConsent(owner);
    const ownerToken = await tokenFor(owner);
    const consentId = (await listConsent(ownerToken)).body[0].id;

    const response = await performDelete(accountUrl(`/consent/${consentId}`), '', { Authorization: `Bearer ${await tokenFor(other)}` });
    expect(response.status).not.toBe(204);

    // Checked through the management API rather than by signing the owner in again: a fresh
    // sign-in records a new approval and would mask whether the original survived.
    const ownerConsents: any = await listUserConsents(fixture.domain.id, fixture.accessToken, owner.id);
    expect(ownerConsents.map((c: any) => c.id)).toContain(consentId);
  });

  it(jira`consent endpoints refuse a request with no token ${'AM-7652'}`, async () => {
    const response = await performGet(accountUrl('/consent'), '');

    expect(response.status).toBe(401);
  });

  // Un-skip once AM-7654 is fixed. Reading a consent by id is currently not scoped to the caller,
  // so another user's record comes back with 200 — verified against a running gateway.
  it.skip(jira`reading another user's consent by id is refused (AM-7654) ${'AM-7652'}`, async () => {
    const owner = await newUser();
    const other = await newUser();
    await approveConsent(owner);
    const consentId = (await listConsent(await tokenFor(owner))).body[0].id;

    const response = await performGet(accountUrl(`/consent/${consentId}`), '', { Authorization: `Bearer ${await tokenFor(other)}` });

    expect(response.status).not.toBe(200);
  });
});
