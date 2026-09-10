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
import { performGet, performPatch, performPost } from '@gateway-commands/oauth-oidc-commands';
import { createUser } from '@management-commands/user-management-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { SelfAccountFixture, setupFixture } from './fixture/self-account-fixture';
import { setup } from '../../test-fixture';

setup(300000);

// Error messages are asserted alongside the status: a 400 alone would not distinguish a rejected
// username from a malformed request. Each test that changes a username uses a user of its own.
const SETTINGS = {
  selfServiceAccountManagementSettings: {
    enabled: true,
    resetPassword: { oldPasswordRequired: false, tokenAge: 0 },
  },
};

let fixture: SelfAccountFixture;

const accountUrl = (path: string) => `${process.env.AM_GATEWAY_URL}/${fixture.domain.hrid}/account/api${path}`;

const newUser = () => ({
  username: uniqueName('usernameUser', true),
  password: 'Password123!',
  firstName: 'Username',
  lastName: 'User',
  email: 'usernameuser@acme.fr',
  preRegistration: false,
});

/** Creates a user and returns them along with an access token of their own. */
const userWithToken = async () => {
  const user = newUser();
  await createUser(fixture.domain.id, fixture.accessToken, user);
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
  return { user, token: response.body.access_token };
};

const patchUsername = (token: string, body: any) =>
  performPatch(accountUrl('/profile/username'), '', body === null ? undefined : JSON.stringify(body), {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
  });

beforeAll(async () => {
  fixture = await setupFixture(SETTINGS, 'self-account-username');
});

afterAll(async () => {
  if (fixture) await fixture.cleanUp();
});

describe('SelfAccount - Update username - accepted', () => {
  it(jira`a username of digits only is accepted ${'AM-7652'}`, async () => {
    const { token } = await userWithToken();
    const digits = `${Date.now()}`.slice(-10);

    const response = await patchUsername(token, { username: digits });

    expect(response.status).toBe(200);
    expect(response.body.status).toBe('OK');
  });

  it(jira`the user can sign in with their new username ${'AM-7652'}`, async () => {
    const { user, token } = await userWithToken();
    const changed = uniqueName('renamed', true);

    await patchUsername(token, { username: changed }).expect(200);

    // The old username is gone and the new one works, which is what makes the change real
    // rather than merely reported.
    const withNew = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=password&username=${changed}&password=${user.password}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(fixture.application),
      },
    );
    expect(withNew.status).toBe(200);

    const withOld = await performPost(
      fixture.oidc.token_endpoint,
      '',
      `grant_type=password&username=${user.username}&password=${user.password}`,
      {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(fixture.application),
      },
    );
    expect(withOld.status).not.toBe(200);
  });
});

describe('SelfAccount - Update username - rejected', () => {
  it(jira`a request with no body is rejected ${'AM-7652'}`, async () => {
    const { token } = await userWithToken();

    const response = await patchUsername(token, null);

    expect(response.status).toBe(400);
    expect(response.body.status).toBe('KO');
    expect(response.body.errorMessage).toContain('Username is required');
  });

  it(jira`a body with no username value is rejected ${'AM-7652'}`, async () => {
    const { token } = await userWithToken();

    const response = await patchUsername(token, {});

    expect(response.status).toBe(400);
    expect(response.body.status).toBe('KO');
    expect(response.body.errorMessage).toContain('Username is required');
  });

  it(jira`a username of spaces only is rejected ${'AM-7652'}`, async () => {
    const { token } = await userWithToken();

    const response = await patchUsername(token, { username: '   ' });

    expect(response.status).toBe(400);
  });

  it(jira`a username mixing valid and invalid characters is rejected ${'AM-7652'}`, async () => {
    const { token } = await userWithToken();
    const mixed = 'updatedusername1?^';

    const response = await patchUsername(token, { username: mixed });

    expect(response.status).toBe(400);
    expect(response.body.status).toBe('KO');
    expect(response.body.errorMessage).toContain(`Username [${mixed}] is not a valid value`);
  });

  it(jira`a username of only special characters is rejected ${'AM-7652'}`, async () => {
    const { token } = await userWithToken();
    const special = '!%^%!&^%';

    const response = await patchUsername(token, { username: special });

    expect(response.status).toBe(400);
    expect(response.body.status).toBe('KO');
    expect(response.body.errorMessage).toContain(`Username [${special}] is not a valid value`);
  });

  it(jira`a username already held by another user is rejected ${'AM-7652'}`, async () => {
    const { user: existing } = await userWithToken();
    const { token } = await userWithToken();

    const response = await patchUsername(token, { username: existing.username });

    expect(response.status).toBe(400);
    expect(response.body.status).toBe('KO');
    expect(response.body.errorMessage).toContain(`User with username [${existing.username}]`);
    expect(response.body.errorMessage).toContain('already exists');
  });

  it(jira`the user's own current username is rejected as already taken ${'AM-7652'}`, async () => {
    // The edge of the case above: the clash is with themselves rather than somebody else.
    const { user, token } = await userWithToken();

    const response = await patchUsername(token, { username: user.username });

    expect(response.status).toBe(400);
    expect(response.body.errorMessage).toContain('already exists');
  });
});

describe('SelfAccount - Update username - authentication', () => {
  it(jira`a request with no token is refused ${'AM-7652'}`, async () => {
    const response = await performPatch(accountUrl('/profile/username'), '', JSON.stringify({ username: uniqueName('nope', true) }), {
      'Content-Type': 'application/json',
    });

    expect(response.status).toBe(401);
  });

  it(jira`a request with a malformed token is refused ${'AM-7652'}`, async () => {
    const response = await patchUsername('not-a-real-token', { username: uniqueName('nope', true) });

    expect(response.status).toBe(401);
    expect(JSON.stringify(response.body)).toContain('invalid');
  });
});
