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
import { setup } from '../../test-fixture';
import {
  getOrgMembershipForUsername,
  OrgIdpRoleMapperFixture,
  setupOrgIdpRoleMapperFixture,
  signInThroughOrgProvider,
} from './fixtures/org-idp-role-mapper-fixture';

setup(300000);

/**
 * AM-2219 / UC-AM4 — role mappers configured on an **organization** identity provider, applied
 * when signing in to the Console.
 *
 * Role mapping on a security domain is covered elsewhere and works differently: there the mapped
 * role lands in the user's `dynamicRoles`. At organization level `AuthenticationServiceImpl`
 * records it as an organization membership carrying the mapped role, flagged `fromRoleMapper`.
 *
 * The decision logic — mapped roles win, an empty set falls back to the default role — is already
 * unit-tested in `AuthenticationServiceTest`. What is only reachable end to end, and is what these
 * tests are for, is that a mapper persisted on an organization provider is loaded and its
 * `claim=value` rule evaluated against the claims the external provider actually returns.
 */
describe('Organization identity provider role mapper (AM-2219)', () => {
  let fixture: OrgIdpRoleMapperFixture;

  beforeAll(async () => {
    fixture = await setupOrgIdpRoleMapperFixture();
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  it(jira`user matching the mapper rule signs in to the Console with the mapped role ${'AM-2219'}`, async () => {
    const provider = fixture.providerA;

    await signInThroughOrgProvider(fixture, provider, provider.matchingUsername);

    const membership = await getOrgMembershipForUsername(fixture.accessToken, provider.matchingUsername);
    expect(membership.roleId).toBe(provider.mappedRoleId);
    expect(membership.roleName).toBe(provider.mappedRoleName);
    expect(membership.fromRoleMapper).toBe(true);
  });

  it(jira`user not matching the rule falls back to the default organization role ${'AM-2219'}`, async () => {
    const provider = fixture.providerA;

    await signInThroughOrgProvider(fixture, provider, provider.nonMatchingUsername);

    const membership = await getOrgMembershipForUsername(fixture.accessToken, provider.nonMatchingUsername);
    // Asserting only "does not have the mapped role" would also pass if sign-in assigned nothing
    // at all, so the specific fallback is checked instead.
    expect(membership.roleId).not.toBe(provider.mappedRoleId);
    expect(membership.roleId).toBe(fixture.defaultRoleId);
    expect(membership.roleName).toBe(fixture.defaultRoleName);
    expect(membership.fromRoleMapper).toBe(false);
  });

  it(jira`each organization provider applies its own role mapper ${'AM-2219'}`, async () => {
    const { providerA, providerB } = fixture;

    await signInThroughOrgProvider(fixture, providerA, providerA.matchingUsername);
    await signInThroughOrgProvider(fixture, providerB, providerB.matchingUsername);

    const fromA = await getOrgMembershipForUsername(fixture.accessToken, providerA.matchingUsername);
    const fromB = await getOrgMembershipForUsername(fixture.accessToken, providerB.matchingUsername);

    expect(fromA.roleId).toBe(providerA.mappedRoleId);
    expect(fromB.roleId).toBe(providerB.mappedRoleId);

    // Each account carries only the role of the provider it signed in through.
    expect(fromA.roleId).not.toBe(providerB.mappedRoleId);
    expect(fromB.roleId).not.toBe(providerA.mappedRoleId);
    expect(fromA.fromRoleMapper).toBe(true);
    expect(fromB.fromRoleMapper).toBe(true);
  });
});
