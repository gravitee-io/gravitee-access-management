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
import { listUsers } from '@management-commands/user-management-commands';
import { setup, retryImmediatelyForThisFile } from '../../test-fixture';
import { jira } from '@specs-utils/jira';
import {
  LdapFixture,
  setupLdapFixture,
  LDAP_ADMIN,
  LDAP_JDOE,
  LDAP_NOGROUP,
  GRAVITEE_ADMINS_DN,
  REDIRECT_URI,
} from './fixtures/ldap-fixture';

setup(200000);

/**
 * Config is applied in describe-level beforeAll blocks, so a deferred retry would
 * run against whichever configuration a LATER describe had installed.
 * Retrying in place also absorbs the window where the gateway has reported the
 * domain synced but has not yet rebuilt the identity provider.
 */
retryImmediatelyForThisFile();

let fixture: LdapFixture;

/**
 * Role mapper conditions are parsed by IdentityProviderArrayPropertyMapper as
 * `attribute=value` via split("=", 2). The DN form therefore survives intact —
 * its own '=' and ',' land in the value half.
 */
const ROLE_VIA_DN = 'LDAP_ROLE_FROM_DN';
const ROLE_VIA_CN = 'LDAP_ROLE_FROM_CN';
const MAPPER_BOTH = {
  [ROLE_VIA_DN]: [`memberOf=${GRAVITEE_ADMINS_DN}`],
  [ROLE_VIA_CN]: ['memberOf=GRAVITEE_ADMINS'],
};

beforeAll(async () => {
  fixture = await setupLdapFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

async function authenticateAndReadUser(user: { username: string; password: string }) {
  const clientId = fixture.app.settings.oauth.clientId;
  const authResponse = await performGet(
    fixture.openIdConfiguration.authorization_endpoint,
    `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&scope=openid`,
  ).expect(302);

  const postLogin = await login(authResponse, user.username, clientId, user.password);
  const loginResponse = await getHeaderLocation(postLogin);
  expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
  await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);

  const page = await listUsers(fixture.domain.id, fixture.accessToken, user.username);
  expect(page.totalCount).toBeGreaterThan(0);
  return page.data[0];
}

describe('LDAP role mapping - fetch user groups disabled', () => {
  beforeAll(async () => {
    await fixture.setMappers(MAPPER_BOTH, {}, { fetchGroups: false });
  });

  it(jira`should assign the role whose condition matches the full group DN ${'AM-7447'}`, async () => {
    const user = await authenticateAndReadUser(LDAP_ADMIN);
    expect(user.dynamicRoles).toContain(ROLE_VIA_DN);
  });

  it(jira`should NOT assign the role whose condition uses the bare group name ${'AM-7447'}`, async () => {
    const user = await authenticateAndReadUser(LDAP_ADMIN);
    expect(user.dynamicRoles).not.toContain(ROLE_VIA_CN);
  });
});

describe('LDAP role mapping - fetch user groups enabled', () => {
  beforeAll(async () => {
    await fixture.setMappers(MAPPER_BOTH, {}, { fetchGroups: true });
  });

  it(jira`should still assign the role matching the full group DN ${'AM-7447'}`, async () => {
    const user = await authenticateAndReadUser(LDAP_ADMIN);
    expect(user.dynamicRoles).toContain(ROLE_VIA_DN);
  });
});

describe('LDAP role mapping - posix-style group search', () => {
  beforeAll(async () => {
    // The POSIX pair is the one whose filter substitutes the bare uid. The
    // DN-based groupSearchFilter substitutes the user's full DN, so a
    // (memberUid={0}) filter placed there can never match.
    await fixture.setMappers(MAPPER_BOTH, {}, {
      fetchGroups: true,
      posixGroupSearchBase: 'ou=groups',
      posixGroupSearchFilter: '(memberUid={0})',
    });
  });

  it(jira`should assign the role matching the bare group name ${'AM-7447'}`, async () => {
    const user = await authenticateAndReadUser(LDAP_ADMIN);
    expect(user.dynamicRoles).toContain(ROLE_VIA_CN);
  });
});

/**
 * GroupSearchEntryHandler writes whatever the group search finds back into the
 * SAME memberOf attribute the directory itself populates, keyed by
 * groupRoleAttribute (default 'cn'). The describes below prove the fetch genuinely
 * happens, rather than only that it fails to break the directory's own memberOf.
 *
 * The DN-based pair is the default configuration and the one most directories
 * use: groupSearchFilter substitutes {0} with the user's FULL DN and matches it
 * against the group's member/uniqueMember attributes. The seeded groups carry
 * `member: uid=<uid>,ou=people,dc=gravitee,dc=io`, so this resolves. Distinct from
 * the posix pair, which matches a bare uid against memberUid.
 */
describe('LDAP group fetching - DN-based group search', () => {
  const GROUP_VIA_DN_SEARCH = 'ldap-dn-search-admins';

  it(jira`should fetch groups using the default member/uniqueMember filter ${'AM-7447'}`, async () => {
    await fixture.setMappers(MAPPER_BOTH, {}, {
      fetchGroups: true,
      groupSearchBase: 'ou=groups',
      groupSearchFilter: '(|(member={0})(uniqueMember={0}))',
    });

    const user = await authenticateAndReadUser(LDAP_ADMIN);

    // The fetched group is written into memberOf as its cn (groupRoleAttribute).
    expect(user.dynamicRoles).toContain(ROLE_VIA_CN);
  });

  it(jira`should fetch groups using an explicit member filter ${'AM-7447'}`, async () => {
    await fixture.setMappers(
      {},
      { [GROUP_VIA_DN_SEARCH]: ['memberOf=GRAVITEE_ADMINS'] },
      { fetchGroups: true, groupSearchBase: 'ou=groups', groupSearchFilter: '(member={0})' },
    );

    const user = await authenticateAndReadUser(LDAP_ADMIN);

    expect(user.dynamicGroups).toContain(GROUP_VIA_DN_SEARCH);
  });

  it(jira`should not fetch a group the user is not a member of ${'AM-7447'}`, async () => {
    // jdoe is in GRAVITEE_USERS, so a mapper keyed to GRAVITEE_ADMINS must not fire
    // even though the search itself resolves correctly.
    await fixture.setMappers(
      {},
      { [GROUP_VIA_DN_SEARCH]: ['memberOf=GRAVITEE_ADMINS'] },
      { fetchGroups: true, groupSearchBase: 'ou=groups', groupSearchFilter: '(member={0})' },
    );

    const user = await authenticateAndReadUser(LDAP_JDOE);

    expect(user.dynamicGroups).toEqual([]);
  });
});

describe('LDAP group fetching', () => {
  const GROUP_FROM_FETCH = 'ldap-fetched-admins';

  it(jira`should populate dynamic groups from a fetched posix group ${'AM-7447'}`, async () => {
    await fixture.setMappers(
      {},
      { [GROUP_FROM_FETCH]: ['memberOf=GRAVITEE_ADMINS'] },
      { fetchGroups: true, posixGroupSearchBase: 'ou=groups', posixGroupSearchFilter: '(memberUid={0})' },
    );

    const user = await authenticateAndReadUser(LDAP_ADMIN);

    expect(user.dynamicGroups).toContain(GROUP_FROM_FETCH);
  });

  it(jira`should not populate dynamic groups when group fetching is disabled ${'AM-7447'}`, async () => {
    // Same mapper, fetching off: the bare group name is never written to
    // memberOf, so the condition cannot match.
    await fixture.setMappers({}, { [GROUP_FROM_FETCH]: ['memberOf=GRAVITEE_ADMINS'] }, { fetchGroups: false });

    const user = await authenticateAndReadUser(LDAP_ADMIN);

    expect(user.dynamicGroups).toEqual([]);
  });
});

/**
 * ldap-admin is a direct member of GRAVITEE_ADMINS, which is itself a member of
 * GRAVITEE_SUPER_ADMINS. The parent group therefore only appears if the provider
 * walks the membership chain.
 */
describe('LDAP group fetching - nested groups', () => {
  const NESTED = { superAdmins: 'ldap-super-admins' };
  const nestedConfig = (recursiveGroupFetch: boolean) => ({
    fetchGroups: true,
    recursiveGroupFetch,
    groupSearchBase: 'ou=groups',
    groupSearchFilter: '(member={0})',
  });

  it(jira`should not resolve a parent group without recursive fetching ${'AM-7447'}`, async () => {
    await fixture.setMappers({}, { [NESTED.superAdmins]: ['memberOf=GRAVITEE_SUPER_ADMINS'] }, nestedConfig(false));

    const user = await authenticateAndReadUser(LDAP_ADMIN);

    expect(user.dynamicGroups).toEqual([]);
  });

  it(jira`should resolve a parent group when recursive fetching is enabled ${'AM-7447'}`, async () => {
    await fixture.setMappers({}, { [NESTED.superAdmins]: ['memberOf=GRAVITEE_SUPER_ADMINS'] }, nestedConfig(true));

    const user = await authenticateAndReadUser(LDAP_ADMIN);

    expect(user.dynamicGroups).toContain(NESTED.superAdmins);
  });
});

/**
 * groupRoleAttribute decides WHICH attribute of the matched group is written into
 * memberOf. GRAVITEE_ADMINS carries `ou: gravitee-admins-role` alongside its cn
 * purely so the non-default case can be proven.
 */
describe('LDAP group fetching - groupRoleAttribute override', () => {
  const ROLE_VIA_OU = 'LDAP_ROLE_FROM_OU';

  it(jira`should key the fetched group on a non-default attribute ${'AM-7447'}`, async () => {
    await fixture.setMappers({ [ROLE_VIA_OU]: ['memberOf=gravitee-admins-role'] }, {}, {
      fetchGroups: true,
      groupSearchBase: 'ou=groups',
      groupSearchFilter: '(member={0})',
      groupRoleAttribute: 'ou',
    });

    const user = await authenticateAndReadUser(LDAP_ADMIN);

    expect(user.dynamicRoles).toContain(ROLE_VIA_OU);
  });

  it(jira`should not match the cn once the role attribute is switched away from it ${'AM-7447'}`, async () => {
    await fixture.setMappers(MAPPER_BOTH, {}, {
      fetchGroups: true,
      groupSearchBase: 'ou=groups',
      groupSearchFilter: '(member={0})',
      groupRoleAttribute: 'ou',
    });

    const user = await authenticateAndReadUser(LDAP_ADMIN);

    // The cn is no longer written into memberOf, so the bare-CN mapper cannot fire.
    expect(user.dynamicRoles).not.toContain(ROLE_VIA_CN);
  });
});

describe('LDAP role mapping - users outside the mapped group', () => {
  beforeAll(async () => {
    await fixture.setMappers(MAPPER_BOTH, {}, { fetchGroups: false });
  });

  it(jira`should not assign the role to a user in a different group ${'AM-7447'}`, async () => {
    const user = await authenticateAndReadUser(LDAP_JDOE);
    expect(user.dynamicRoles).toEqual([]);
  });

  it(jira`should not assign the role to a user in no group at all ${'AM-7447'}`, async () => {
    const user = await authenticateAndReadUser(LDAP_NOGROUP);
    expect(user.dynamicRoles).toEqual([]);
  });
});
