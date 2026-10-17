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
import { OrganizationGroupMembersFixture, setupOrganizationGroupMembersFixture } from './fixtures/organization-group-members-fixture';

global.fetch = fetch;
jest.setTimeout(200000);

let fixture: OrganizationGroupMembersFixture;

beforeAll(async () => {
  fixture = await setupOrganizationGroupMembersFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('Organization group members', () => {
  it('should list members as user entities', async () => {
    const page = await fixture.listMembers();

    expect(page.totalCount).toEqual(1);
    expect(page.data[0]).toMatchObject({
      id: fixture.member.id,
      username: fixture.member.username,
      email: fixture.member.email,
    });
    expect(page.data[0]).not.toHaveProperty('password');
  });
});

describe('Organization user bulk creation', () => {
  it('should return the created user as a user entity', async () => {
    const response = await fixture.bulkCreateMember();

    expect(response.allSuccessful).toEqual(true);
    expect(response.results[0].body).toMatchObject({ username: expect.stringMatching(/^grp-member/) });
    expect(response.results[0].body).not.toHaveProperty('password');
  });
});
