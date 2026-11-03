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
import { beforeAll, expect } from '@jest/globals';
import {
  createAuthorizationDetailType,
  deleteAuthorizationDetailTypeRaw,
  getAuthorizationDetailType,
  listAuthorizationDetailTypes,
  patchAuthorizationDetailType,
} from '@management-commands/authorization-detail-type-management-commands';
import { getDataPlaneIds, getInstanceLabel } from '../../migration-seeding/seed';
import {
  createRarPermissionsMigrationFixture,
  RAR_PERMISSIONS_REQUIREMENTS,
  RarPermissionsMigrationFixture,
} from './fixture/rar-permissions-fixture';
import { currentChannel, describeWhen, itWhen } from './fixture/migration-versions';
import { setup } from '../test-fixture';
import { jira } from '@specs-utils/jira';

setup(120000);

const channelLabel = currentChannel();
const requires = RAR_PERMISSIONS_REQUIREMENTS;

// The alpha channel's owner and user were given their roles by a version that knew nothing of
// authorization detail types: only the upgrade can have granted them the permission.
describeWhen(requires.seeded, channelLabel).each(getDataPlaneIds())('migration RAR permissions [data plane %s]', (dataPlaneId) => {
  const label = getInstanceLabel(channelLabel, dataPlaneId);
  let fixture: RarPermissionsMigrationFixture;

  beforeAll(async () => {
    fixture = await createRarPermissionsMigrationFixture(label);
  });

  itWhen(requires.managementApiServesTypes)(jira`lets a domain owner from before the upgrade manage types ${'AM-8028'}`, async () => {
    // Every verification stage runs this again, so each run creates and removes its own type.
    const type = `migration_${Date.now()}`;

    const created = await createAuthorizationDetailType(fixture.domainId, fixture.ownerToken, { type, name: type });
    expect(created.type).toEqual(type);
    expect((await getAuthorizationDetailType(fixture.domainId, fixture.ownerToken, created.id)).name).toEqual(type);
    const page = await listAuthorizationDetailTypes(fixture.domainId, fixture.ownerToken, { q: type });
    expect(page.data.map((listed) => listed.type)).toEqual([type]);
    expect((await patchAuthorizationDetailType(fixture.domainId, fixture.ownerToken, created.id, { name: 'Renamed' })).name).toEqual(
      'Renamed',
    );
    expect((await deleteAuthorizationDetailTypeRaw(fixture.domainId, fixture.ownerToken, created.id)).raw.status).toEqual(204);
  });

  itWhen(requires.managementApiServesTypes)(jira`lets a domain user from before the upgrade list types only ${'AM-8028'}`, async () => {
    const type = `migration_${Date.now()}`;
    const created = await createAuthorizationDetailType(fixture.domainId, fixture.ownerToken, { type, name: type });
    try {
      const page = await listAuthorizationDetailTypes(fixture.domainId, fixture.userToken, { q: type });
      expect(page.data.map((listed) => listed.type)).toEqual([type]);

      // Guards the listing above against passing because the upgrade granted every ACL to everyone.
      await expect(
        createAuthorizationDetailType(fixture.domainId, fixture.userToken, { type: `${type}_refused`, name: 'refused' }),
      ).rejects.toMatchObject({ response: { status: 403 } });
    } finally {
      await deleteAuthorizationDetailTypeRaw(fixture.domainId, fixture.ownerToken, created.id);
    }
  });
});
