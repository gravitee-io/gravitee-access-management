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
import { createIdp, deleteIdp, getIdp, updateIdp } from '@management-commands/idp-management-commands';
import { setup } from '../../test-fixture';
import { IdpFixture, setupIdpFixture, buildInlineIdpBody } from './fixtures/idp-fixture';

setup(200000);

let fixture: IdpFixture;

beforeAll(async () => {
  fixture = await setupIdpFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const BASE_USER = {
  firstname: 'John',
  lastname: 'Doe',
  username: 'john',
  email: 'john@example.com',
  password: '#CoMpL3X-P@SsW0Rd',
};

describe('User mapper (mappers)', () => {
  it('should store mappers via update and return them via GET', async () => {
    const mappers = { given_name: 'firstname', family_name: 'lastname' };
    const created = await createIdp(fixture.domain.id, fixture.accessToken, buildInlineIdpBody([BASE_USER]));
    try {
      await updateIdp(
        fixture.domain.id,
        fixture.accessToken,
        { name: created.name, type: created.type, configuration: created.configuration, mappers },
        created.id,
      );
      const fetched = await getIdp(fixture.domain.id, fixture.accessToken, created.id);
      expect(fetched.mappers).toEqual(mappers);
    } finally {
      await deleteIdp(fixture.domain.id, fixture.accessToken, created.id);
    }
  });

  it('should reflect added mapper entry after update', async () => {
    const created = await createIdp(fixture.domain.id, fixture.accessToken, {
      ...buildInlineIdpBody([BASE_USER]),
      mappers: { given_name: 'firstname' },
    });
    try {
      const updated = await updateIdp(
        fixture.domain.id,
        fixture.accessToken,
        { name: created.name, type: created.type, configuration: created.configuration, mappers: { given_name: 'firstname', family_name: 'lastname' } },
        created.id,
      );
      expect(updated.mappers).toEqual({ given_name: 'firstname', family_name: 'lastname' });
      const fetched = await getIdp(fixture.domain.id, fixture.accessToken, created.id);
      expect(fetched.mappers).toEqual({ given_name: 'firstname', family_name: 'lastname' });
    } finally {
      await deleteIdp(fixture.domain.id, fixture.accessToken, created.id);
    }
  });

  it('should return empty mappers after clearing to {}', async () => {
    const created = await createIdp(fixture.domain.id, fixture.accessToken, {
      ...buildInlineIdpBody([BASE_USER]),
      mappers: { given_name: 'firstname' },
    });
    try {
      await updateIdp(
        fixture.domain.id,
        fixture.accessToken,
        { name: created.name, type: created.type, configuration: created.configuration, mappers: {} },
        created.id,
      );
      const fetched = await getIdp(fixture.domain.id, fixture.accessToken, created.id);
      expect(fetched.mappers).toEqual({});
    } finally {
      await deleteIdp(fixture.domain.id, fixture.accessToken, created.id);
    }
  });
});

describe('Role mapper (roleMapper)', () => {
  it('should store roleMapper via update and return it via GET', async () => {
    const roleMapper = { ADMIN: ['firstname=John'] };
    const created = await createIdp(fixture.domain.id, fixture.accessToken, buildInlineIdpBody([BASE_USER]));
    try {
      await updateIdp(
        fixture.domain.id,
        fixture.accessToken,
        { name: created.name, type: created.type, configuration: created.configuration, roleMapper },
        created.id,
      );
      const fetched = await getIdp(fixture.domain.id, fixture.accessToken, created.id);
      expect(fetched.roleMapper).toEqual(roleMapper);
    } finally {
      await deleteIdp(fixture.domain.id, fixture.accessToken, created.id);
    }
  });

  it('should replace roleMapper on update', async () => {
    const created = await createIdp(fixture.domain.id, fixture.accessToken, {
      ...buildInlineIdpBody([BASE_USER]),
      roleMapper: { ADMIN: ['firstname=John'] },
    });
    try {
      const updated = await updateIdp(
        fixture.domain.id,
        fixture.accessToken,
        { name: created.name, type: created.type, configuration: created.configuration, roleMapper: { VIEWER: ['username=john'] } },
        created.id,
      );
      expect(updated.roleMapper).toEqual({ VIEWER: ['username=john'] });
      const fetched = await getIdp(fixture.domain.id, fixture.accessToken, created.id);
      expect(fetched.roleMapper).toEqual({ VIEWER: ['username=john'] });
    } finally {
      await deleteIdp(fixture.domain.id, fixture.accessToken, created.id);
    }
  });

  it('should return empty roleMapper after clearing to {}', async () => {
    const created = await createIdp(fixture.domain.id, fixture.accessToken, {
      ...buildInlineIdpBody([BASE_USER]),
      roleMapper: { ADMIN: ['firstname=John'] },
    });
    try {
      await updateIdp(
        fixture.domain.id,
        fixture.accessToken,
        { name: created.name, type: created.type, configuration: created.configuration, roleMapper: {} },
        created.id,
      );
      const fetched = await getIdp(fixture.domain.id, fixture.accessToken, created.id);
      expect(fetched.roleMapper).toEqual({});
    } finally {
      await deleteIdp(fixture.domain.id, fixture.accessToken, created.id);
    }
  });
});

describe('Group mapper (groupMapper)', () => {
  it('should store groupMapper via update and return it via GET', async () => {
    const groupMapper = { engineering: ['firstname=John'] };
    const created = await createIdp(fixture.domain.id, fixture.accessToken, buildInlineIdpBody([BASE_USER]));
    try {
      await updateIdp(
        fixture.domain.id,
        fixture.accessToken,
        { name: created.name, type: created.type, configuration: created.configuration, groupMapper },
        created.id,
      );
      const fetched = await getIdp(fixture.domain.id, fixture.accessToken, created.id);
      expect(fetched.groupMapper).toEqual(groupMapper);
    } finally {
      await deleteIdp(fixture.domain.id, fixture.accessToken, created.id);
    }
  });

  it('should store EL expression condition in groupMapper', async () => {
    const groupMapper = { 'all-users': ["{#profile.email.endsWith('example.com')}"] };
    const created = await createIdp(fixture.domain.id, fixture.accessToken, buildInlineIdpBody([BASE_USER]));
    try {
      await updateIdp(
        fixture.domain.id,
        fixture.accessToken,
        { name: created.name, type: created.type, configuration: created.configuration, groupMapper },
        created.id,
      );
      const fetched = await getIdp(fixture.domain.id, fixture.accessToken, created.id);
      expect(fetched.groupMapper).toEqual(groupMapper);
    } finally {
      await deleteIdp(fixture.domain.id, fixture.accessToken, created.id);
    }
  });

  it('should return empty groupMapper after clearing to {}', async () => {
    const created = await createIdp(fixture.domain.id, fixture.accessToken, {
      ...buildInlineIdpBody([BASE_USER]),
      groupMapper: { engineering: ['firstname=John'] },
    });
    try {
      await updateIdp(
        fixture.domain.id,
        fixture.accessToken,
        { name: created.name, type: created.type, configuration: created.configuration, groupMapper: {} },
        created.id,
      );
      const fetched = await getIdp(fixture.domain.id, fixture.accessToken, created.id);
      expect(fetched.groupMapper).toEqual({});
    } finally {
      await deleteIdp(fixture.domain.id, fixture.accessToken, created.id);
    }
  });
});

describe('All mapper types together', () => {
  it('should store all three mapper types via update and return all via GET', async () => {
    const mappers = { given_name: 'firstname', family_name: 'lastname' };
    const roleMapper = { ADMIN: ['firstname=John'] };
    const groupMapper = { engineering: ['firstname=John'] };
    const created = await createIdp(fixture.domain.id, fixture.accessToken, buildInlineIdpBody([BASE_USER]));
    try {
      await updateIdp(
        fixture.domain.id,
        fixture.accessToken,
        { name: created.name, type: created.type, configuration: created.configuration, mappers, roleMapper, groupMapper },
        created.id,
      );
      const fetched = await getIdp(fixture.domain.id, fixture.accessToken, created.id);
      expect(fetched.mappers).toEqual(mappers);
      expect(fetched.roleMapper).toEqual(roleMapper);
      expect(fetched.groupMapper).toEqual(groupMapper);
    } finally {
      await deleteIdp(fixture.domain.id, fixture.accessToken, created.id);
    }
  });

  it('should update one mapper type while preserving others included in the payload', async () => {
    const mappers = { given_name: 'firstname' };
    const roleMapper = { ADMIN: ['firstname=John'] };
    const groupMapper = { engineering: ['firstname=John'] };
    const created = await createIdp(fixture.domain.id, fixture.accessToken, {
      ...buildInlineIdpBody([BASE_USER]),
      mappers,
      roleMapper,
      groupMapper,
    });
    try {
      // Update mappers while preserving roleMapper and groupMapper
      await updateIdp(
        fixture.domain.id,
        fixture.accessToken,
        {
          name: created.name,
          type: created.type,
          configuration: created.configuration,
          mappers: { given_name: 'firstname', family_name: 'lastname' },
          roleMapper,
          groupMapper,
        },
        created.id,
      );
      const fetched = await getIdp(fixture.domain.id, fixture.accessToken, created.id);
      expect(fetched.mappers).toEqual({ given_name: 'firstname', family_name: 'lastname' });
      expect(fetched.roleMapper).toEqual(roleMapper);
      expect(fetched.groupMapper).toEqual(groupMapper);
    } finally {
      await deleteIdp(fixture.domain.id, fixture.accessToken, created.id);
    }
  });
});
