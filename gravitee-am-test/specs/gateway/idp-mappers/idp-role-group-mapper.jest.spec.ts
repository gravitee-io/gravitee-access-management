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
import { setup } from '../../test-fixture';
import { IdpMapperFixture, setupIdpMapperFixture, JOHN, JANE, REDIRECT_URI } from './fixture/idp-mapper-fixture';

setup(200000);

let fixture: IdpMapperFixture;

beforeAll(async () => {
  fixture = await setupIdpMapperFixture();
  expect(fixture.openIdConfiguration).toBeDefined();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

async function authenticateUser(user: { username: string; password: string }): Promise<void> {
  const clientId = fixture.app.settings.oauth.clientId;
  const authResponse = await performGet(
    fixture.openIdConfiguration.authorization_endpoint,
    `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&scope=openid`,
  ).expect(302);

  const postLogin = await login(authResponse, user.username, clientId, user.password);
  const loginResponse = await getHeaderLocation(postLogin);
  expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
  await requestToken(fixture.app, fixture.openIdConfiguration, loginResponse);
  await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
}

async function findUser(username: string) {
  const page = await listUsers(fixture.domain.id, fixture.accessToken, username);
  expect(page.totalCount).toBeGreaterThan(0);
  return page.data[0];
}

describe('Role mapper — attribute match', () => {
  beforeAll(async () => {
    await fixture.updateIdpMappers({}, { ADMIN: ['firstname=John'] }, {});
  });

  it('should assign role to user whose attribute matches', async () => {
    await authenticateUser(JOHN);
    const john = await findUser(JOHN.username);
    expect(john.dynamicRoles).toContain('ADMIN');
  });

  it('should NOT assign role to user whose attribute does not match', async () => {
    await authenticateUser(JANE);
    const jane = await findUser(JANE.username);
    expect(jane.dynamicRoles).toHaveLength(0);
  });
});

describe('Role mapper — EL expression', () => {
  beforeAll(async () => {
    await fixture.updateIdpMappers({}, { VIEWER: ["{#profile.email.endsWith('example.com')}"] }, {});
  });

  it('should assign role to user with matching EL condition', async () => {
    await authenticateUser(JOHN);
    const john = await findUser(JOHN.username);
    expect(john.dynamicRoles).toContain('VIEWER');
  });
});

describe('Role mapper — no match', () => {
  beforeAll(async () => {
    await fixture.updateIdpMappers({}, { SUPERADMIN: ['firstname=Nobody'] }, {});
  });

  it('should not assign role when condition never matches', async () => {
    await authenticateUser(JOHN);
    const john = await findUser(JOHN.username);
    expect(john.dynamicRoles).toHaveLength(0);
  });
});

describe('Group mapper — attribute match', () => {
  beforeAll(async () => {
    await fixture.updateIdpMappers({}, {}, { engineering: ['firstname=John'] });
  });

  it('should assign group to user whose attribute matches', async () => {
    await authenticateUser(JOHN);
    const john = await findUser(JOHN.username);
    expect(john.dynamicGroups).toContain('engineering');
  });

  it('should NOT assign group to user whose attribute does not match', async () => {
    await authenticateUser(JANE);
    const jane = await findUser(JANE.username);
    expect(jane.dynamicGroups).toHaveLength(0);
  });
});

describe('Group mapper — EL expression', () => {
  beforeAll(async () => {
    await fixture.updateIdpMappers({}, {}, { 'all-users': ["{#profile.email.endsWith('example.com')}"] });
  });

  it('should assign group to user matching EL boolean condition', async () => {
    await authenticateUser(JOHN);
    const john = await findUser(JOHN.username);
    expect(john.dynamicGroups).toContain('all-users');
  });
});

describe('Combined role + group mapping', () => {
  beforeAll(async () => {
    await fixture.updateIdpMappers({}, { ADMIN: ['firstname=John'] }, { engineering: ['firstname=John'] });
  });

  it('should assign both role and group in a single authentication', async () => {
    await authenticateUser(JOHN);
    const john = await findUser(JOHN.username);
    expect(john.dynamicRoles).toContain('ADMIN');
    expect(john.dynamicGroups).toContain('engineering');
  });
});

describe('Clearing mappers removes runtime effect', () => {
  it('should remove dynamic roles and groups after mappers are cleared', async () => {
    await fixture.updateIdpMappers({}, { ADMIN: ['firstname=John'] }, { engineering: ['firstname=John'] });
    await authenticateUser(JOHN);
    const johnBefore = await findUser(JOHN.username);
    expect(johnBefore.dynamicRoles).toContain('ADMIN');
    expect(johnBefore.dynamicGroups).toContain('engineering');

    await fixture.updateIdpMappers({}, {}, {});
    await authenticateUser(JOHN);
    const johnAfter = await findUser(JOHN.username);
    expect(johnAfter.dynamicRoles).toHaveLength(0);
    expect(johnAfter.dynamicGroups).toHaveLength(0);
  });
});
