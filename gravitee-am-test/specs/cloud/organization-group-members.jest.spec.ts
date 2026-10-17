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
import { setup } from '../test-fixture';
import { CloudOrgGroupMembersFixture, setupCloudOrgGroupMembersFixture } from './fixtures/cloud-org-group-members-fixture';

setup(300000);

let fixture: CloudOrgGroupMembersFixture;

beforeAll(async () => {
  fixture = await setupCloudOrgGroupMembersFixture();
});

afterAll(async () => {
  await fixture?.cleanup();
});

describe('Organization group members in managed cloud', () => {
  it('should keep only organization users as group members', async () => {
    const group = await fixture.createHomeGroup([fixture.homeOwnerId, fixture.foreignOwnerId]);

    expect(group.members).toEqual([fixture.homeOwnerId]);
  });

  it('should list only organization users', async () => {
    const group = await fixture.createHomeGroup([fixture.homeOwnerId, fixture.foreignOwnerId]);

    const page = await fixture.listHomeGroupMembers(group.id);

    expect(page.data.map((member) => member.id)).toEqual([fixture.homeOwnerId]);
  });
});
