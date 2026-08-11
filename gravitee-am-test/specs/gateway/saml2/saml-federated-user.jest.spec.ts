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
import { afterAll, beforeAll, beforeEach, describe, expect, it } from '@jest/globals';
import { jira } from '@specs-utils/jira';

import { SAML_LOOPBACK_TEST, SamlLoopbackFixture, setupSamlLoopbackFixture, TEST_USER } from './fixtures/saml-loopback-fixture';
import { setup } from '../../test-fixture';

/**
 * Lifecycle of the federated user that a SAML login creates in the client domain:
 * how repeat logins are matched, what happens when the NameID cannot be resolved,
 * and what happens when the NameID mapping changes after a user already exists.
 */
setup(200000);

const UUID_FORMAT = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

let fixture: SamlLoopbackFixture;

beforeAll(async () => {
  fixture = await setupSamlLoopbackFixture();
});

beforeEach(async () => {
  await fixture.clearFederatedUsers();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanup();
  }
});

describe('SAML Federated User - repeat authentication', () => {
  it(jira`should update the existing federated user on re-login rather than duplicating it ${'AM-6959'}`, async () => {
    await fixture.setSamlSettings({ nameIdMapping: SAML_LOOPBACK_TEST.EL_USERNAME, assertionAttributes: null });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);
    const afterFirstLogin = await fixture.getOnlyFederatedUser();
    expect(afterFirstLogin.loginsCount).toEqual(1);

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    // Same NameID resolves to the same externalId, so the existing record is updated.
    const afterSecondLogin = await fixture.getOnlyFederatedUser();
    expect(afterSecondLogin.id).toEqual(afterFirstLogin.id);
    expect(afterSecondLogin.loginsCount).toEqual(2);
  });
});

describe('SAML Federated User - unresolvable NameID', () => {
  it(jira`should fall back to the internal user id when the NameID mapping references a missing attribute ${'AM-6960'}`, async () => {
    // `employeeId` is not present on the inline IdP user, so the expression cannot resolve.
    await fixture.setSamlSettings({
      nameIdMapping: `{#context.attributes['user'].employeeId}`,
      assertionAttributes: null,
    });

    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    // Authentication still succeeds — the IdP degrades to the internal user id
    // rather than failing the flow.
    const federatedUser = await fixture.getOnlyFederatedUser();
    expect(federatedUser.username).toMatch(UUID_FORMAT);
    expect(federatedUser.username).not.toEqual(TEST_USER.username);
  });
});

describe('SAML Federated User - NameID mapping changed after first login', () => {
  it(jira`should orphan the original federated user when the NameID mapping changes ${'AM-6960'}`, async () => {
    await fixture.setSamlSettings({ nameIdMapping: SAML_LOOPBACK_TEST.EL_USERNAME, assertionAttributes: null });
    await fixture.authenticate(TEST_USER.username, TEST_USER.password);
    const original = await fixture.getOnlyFederatedUser();
    expect(original.username).toEqual(TEST_USER.username);

    // Change what the NameID resolves to, leaving the existing federated user in place.
    await fixture.setSamlSettings({ nameIdMapping: SAML_LOOPBACK_TEST.EL_EMAIL });
    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    // The new NameID does not match the stored externalId, so a second record is
    // created and the original is left behind rather than being migrated.
    const users = await fixture.findFederatedUsers();
    expect(users).toHaveLength(2);
    expect(users.map((user) => user.username).sort()).toEqual([TEST_USER.email, TEST_USER.username].sort());
  });

  it(jira`should create a single federated user when the old one is deleted before re-login ${'AM-6961'}`, async () => {
    await fixture.setSamlSettings({ nameIdMapping: SAML_LOOPBACK_TEST.EL_USERNAME, assertionAttributes: null });
    await fixture.authenticate(TEST_USER.username, TEST_USER.password);
    expect(await fixture.getOnlyFederatedUser()).toEqual(expect.objectContaining({ username: TEST_USER.username }));

    await fixture.setSamlSettings({ nameIdMapping: SAML_LOOPBACK_TEST.EL_EMAIL });
    await fixture.clearFederatedUsers();
    await fixture.authenticate(TEST_USER.username, TEST_USER.password);

    const federatedUser = await fixture.getOnlyFederatedUser();
    expect(federatedUser.username).toEqual(TEST_USER.email);
  });
});
