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
import { REDIRECT_URI } from './fixtures/ldap-fixture';
import {
  LdapAccountLinkFixture,
  setupLdapAccountLinkFixture,
  LDAP_JOHN,
  WORKER_ROLE,
  ENGINEER_ROLE,
  WORKER_CONDITION,
  ENGINEER_CONDITION,
} from './fixtures/ldap-account-link-fixture';

setup(200000);

/**
 * Tests are deliberately ordered — each builds on the linked state established by
 * the previous one — so retries must happen in place.
 * Retrying in place also absorbs the window where the gateway has reported the
 * domain synced but has not yet rebuilt the identity provider.
 */
retryImmediatelyForThisFile();

/**
 * Regression guard for AM-5385 — "Can't get dynamic roles for the user".
 *
 * Two providers resolve the same username. Once the account-linking policy links
 * them, UserAuthenticationServiceImpl takes the isAccountLinked() branch, which
 * skips the block that refreshes dynamicRoles/dynamicGroups. The reported symptoms
 * were roles vanishing after signing in through the second provider, and role
 * changes no longer taking effect afterwards.
 *
 * Tests run in order: each builds on the linked state established by the previous.
 */
let fixture: LdapAccountLinkFixture;

beforeAll(async () => {
  fixture = await setupLdapAccountLinkFixture();
  await fixture.setRoleMapper({ [WORKER_ROLE]: [WORKER_CONDITION] });
  await fixture.useIdp(fixture.idpWithRoles);
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

async function signIn() {
  const clientId = fixture.app.settings.oauth.clientId;
  const authResponse = await performGet(
    fixture.openIdConfiguration.authorization_endpoint,
    `?response_type=code&client_id=${clientId}&redirect_uri=${REDIRECT_URI}&scope=openid`,
  ).expect(302);

  const postLogin = await login(authResponse, LDAP_JOHN.username, clientId, LDAP_JOHN.password);
  const loginResponse = await getHeaderLocation(postLogin);
  expect(loginResponse.headers['location']).toContain(`${REDIRECT_URI}?code=`);
  await logoutUser(fixture.openIdConfiguration.end_session_endpoint, loginResponse);
}

async function johnsProfile() {
  const page = await listUsers(fixture.domain.id, fixture.accessToken, LDAP_JOHN.username);
  expect(page.totalCount).toBeGreaterThan(0);
  return page.data[0];
}

describe('LDAP account linking - dynamic roles (AM-5385)', () => {
  it(jira`should assign the mapped role on the first sign-in ${'AM-5385'}`, async () => {
    await signIn();
    const john = await johnsProfile();

    expect(john.dynamicRoles).toContain(WORKER_ROLE);
    // Nothing linked yet — this is a single-provider login.
    expect(john.identities ?? []).toHaveLength(0);
  });

  it(jira`should link the second provider without creating a second user ${'AM-5385'}`, async () => {
    await fixture.useIdp(fixture.idpWithoutRoles);
    await signIn();

    const page = await listUsers(fixture.domain.id, fixture.accessToken, LDAP_JOHN.username);
    expect(page.totalCount).toEqual(1);
    expect(page.data[0].identities ?? []).not.toHaveLength(0);
  });

  it(jira`should NOT drop the mapped role after signing in through the linked provider ${'AM-5385'}`, async () => {
    // The reported symptom: "All the roles of the user disappeared" once the second
    // provider was used. The role came from the other provider and must survive.
    const john = await johnsProfile();

    expect(john.dynamicRoles).toContain(WORKER_ROLE);
  });

  it(jira`should still apply role-mapper changes once the account is linked ${'AM-5385'}`, async () => {
    // The second symptom: role changes stopped taking effect after linking. Adding
    // a second condition must be reflected on the next sign-in via that provider.
    await fixture.setRoleMapper({
      [WORKER_ROLE]: [WORKER_CONDITION],
      [ENGINEER_ROLE]: [WORKER_CONDITION],
    });
    await fixture.useIdp(fixture.idpWithRoles);
    await signIn();

    const john = await johnsProfile();

    expect(john.dynamicRoles).toContain(WORKER_ROLE);
    expect(john.dynamicRoles).toContain(ENGINEER_ROLE);
  });

  it(jira`should remove a role whose condition no longer matches ${'AM-5385'}`, async () => {
    // engineers does not contain john, so that condition cannot match and the role
    // must be withdrawn rather than lingering from the previous sign-in.
    await fixture.setRoleMapper({
      [WORKER_ROLE]: [WORKER_CONDITION],
      [ENGINEER_ROLE]: [ENGINEER_CONDITION],
    });
    await signIn();

    const john = await johnsProfile();

    expect(john.dynamicRoles).toContain(WORKER_ROLE);
    expect(john.dynamicRoles).not.toContain(ENGINEER_ROLE);
  });
});
