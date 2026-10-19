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
import fetch from 'cross-fetch';
import { afterAll, beforeAll, describe, expect, it, jest } from '@jest/globals';
import {
  ORG_USER_ACCESS_TEST,
  OrganisationUserAccessFixture,
  setupOrganisationUserAccessFixture,
  waitForNextSecond,
} from './fixtures/organisation-user-access-fixture';

global.fetch = fetch;
jest.setTimeout(200000);

let fixture: OrganisationUserAccessFixture;

beforeAll(async () => {
  fixture = await setupOrganisationUserAccessFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Organisation user access - disable', () => {
  it('should reject the session JWT of a disabled user', async () => {
    const user = await fixture.createUser();
    const jwt = await fixture.login(user.username, ORG_USER_ACCESS_TEST.PASSWORD);
    expect(await fixture.currentUserStatus(jwt)).toEqual(200);

    await fixture.updateStatus(user.id, false);

    expect(await fixture.currentUserStatus(jwt)).toEqual(401);
  });

  it('should revoke and reject the account tokens of a disabled user', async () => {
    const user = await fixture.createUser();
    const accountToken = await fixture.createAccountToken(user.id);
    expect(await fixture.currentUserStatus(accountToken)).toEqual(200);

    await fixture.updateStatus(user.id, false);

    expect(await fixture.currentUserStatus(accountToken)).toEqual(401);
    expect(await fixture.listAccountTokens(user.id)).toEqual([]);
  });

  it('should refuse a new login of a disabled user', async () => {
    const user = await fixture.createUser();

    await fixture.updateStatus(user.id, false);

    expect(await fixture.loginStatus(user.username, ORG_USER_ACCESS_TEST.PASSWORD)).toEqual(401);
  });

  it('should keep a disabled user disabled after a profile update', async () => {
    const user = await fixture.createUser();
    await fixture.updateStatus(user.id, false);

    await fixture.updateProfile(user.id, 'Renamed');

    const updated = await fixture.getUser(user.id);
    expect(updated.firstName).toEqual('Renamed');
    expect(updated.enabled).toEqual(false);
  });

  it('should allow a new login after re-enable and keep the old JWT invalid', async () => {
    const user = await fixture.createUser();
    const oldJwt = await fixture.login(user.username, ORG_USER_ACCESS_TEST.PASSWORD);
    await waitForNextSecond();
    await fixture.updateStatus(user.id, false);

    await fixture.updateStatus(user.id, true);
    await waitForNextSecond();

    const newJwt = await fixture.login(user.username, ORG_USER_ACCESS_TEST.PASSWORD);
    expect(await fixture.currentUserStatus(newJwt)).toEqual(200);
    expect(await fixture.currentUserStatus(oldJwt)).toEqual(401);
  });
});

describe('Organisation user access - password reset', () => {
  it('should reject a JWT issued before an admin password reset and revoke the account tokens', async () => {
    const user = await fixture.createUser();
    const jwt = await fixture.login(user.username, ORG_USER_ACCESS_TEST.PASSWORD);
    const accountToken = await fixture.createAccountToken(user.id);
    await waitForNextSecond();

    await fixture.resetPassword(user.id, ORG_USER_ACCESS_TEST.NEW_PASSWORD);

    expect(await fixture.currentUserStatus(jwt)).toEqual(401);
    expect(await fixture.currentUserStatus(accountToken)).toEqual(401);
    expect(await fixture.listAccountTokens(user.id)).toEqual([]);
    await waitForNextSecond();
    const newJwt = await fixture.login(user.username, ORG_USER_ACCESS_TEST.NEW_PASSWORD);
    expect(await fixture.currentUserStatus(newJwt)).toEqual(200);
  });
});
