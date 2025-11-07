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
import * as faker from 'faker';
import { afterAll, afterEach, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, setupDomainForTest, startDomain } from '@management-commands/domain-management-commands';
import {
  createPasswordPolicy,
  deletePasswordPolicy,
  getAllPasswordPolicies,
  getAllPasswordPoliciesRaw,
  getPasswordPolicy,
  setPasswordPolicyDefault,
  updatePasswordPolicy,
} from '@management-commands/password-policy-management-commands';
import { uniqueName } from '@utils-commands/misc';

global.fetch = fetch;

jest.setTimeout(200000);

let accessToken;
let domain;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await setupDomainForTest(uniqueName('password-policy-m', true), { accessToken, waitForStart: false }).then((it) => it.domain);
});

describe('password policy management', () => {
  it('must return no password policy', async () => {
    const passwordPolicies = await getAllPasswordPoliciesRaw(domain.id, accessToken);
    expect(passwordPolicies.raw.status).toEqual(204);
  });

  it('must create password policy', async () => {
    const name = 'default';
    const passwordPolicy = await createPasswordPolicy(domain.id, accessToken, {
      name: name,
    });
    expect(passwordPolicy).toBeDefined();
    expect(passwordPolicy.id).toEqual(passwordPolicy.id);
    expect(passwordPolicy.defaultPolicy).toBeTruthy();

    const policy = await getPasswordPolicy(domain.id, accessToken, passwordPolicy.id);
    expect(policy).toBeDefined();
    expect(passwordPolicy.name).toEqual(name);
    expect(policy.defaultPolicy).toBeTruthy();
  });

  it('must update password policy', async () => {
    const oldPasswordPolicy = await createPasswordPolicy(domain.id, accessToken, {
      name: 'new',
    });
    const updatedName = 'updated-new';
    const updatedPasswordPolicy = await updatePasswordPolicy(domain.id, accessToken, oldPasswordPolicy.id, {
      name: updatedName,
      defaultPolicy: true,
    });
    expect(updatedPasswordPolicy).toBeDefined();
    expect(updatedPasswordPolicy.name).toEqual(updatedName);
    expect(updatedPasswordPolicy.defaultPolicy).toBeTruthy();

    const policy = await getPasswordPolicy(domain.id, accessToken, updatedPasswordPolicy.id);
    expect(policy).toBeDefined();
    expect(updatedPasswordPolicy.name).toEqual(updatedName);
    expect(policy.defaultPolicy).toBeTruthy();
  });

  it('must find all password policies', async () => {
    await createPasswordPolicy(domain.id, accessToken, {
      name: 'create-one-policy',
    });
    const passwordPolicies = await getAllPasswordPolicies(domain.id, accessToken);
    expect(passwordPolicies.length).toEqual(1);
  });

  it('must not delete password policy when not found', async () => {
    await expect(async () => {
      const response = await deletePasswordPolicy('not-found-id', accessToken, 'not-found-id');
      expect(response.raw.status).toEqual(204);
    }).rejects.toThrow();
  });

  it('must set default password policy', async () => {
    const oldPolicy = await createPasswordPolicy(domain.id, accessToken, {
      name: 'old-to-assign',
    });
    expect(oldPolicy).toBeDefined();
    expect(oldPolicy.defaultPolicy).toBeTruthy();

    const newPolicy = await createPasswordPolicy(domain.id, accessToken, {
      name: 'default-to-assign',
    });

    await setPasswordPolicyDefault(domain.id, accessToken, newPolicy.id);

    const updatedOldPolicy = await getPasswordPolicy(domain.id, accessToken, oldPolicy.id);
    expect(updatedOldPolicy).toBeDefined();
    expect(updatedOldPolicy.defaultPolicy).toBeFalsy();

    const updatedNewPolicy = await getPasswordPolicy(domain.id, accessToken, newPolicy.id);
    expect(updatedNewPolicy).toBeDefined();
    expect(updatedNewPolicy.defaultPolicy).toBeTruthy();
  });
});

afterEach(async () => {
  await removeAllPasswordPolicies();
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

async function removeAllPasswordPolicies(): Promise<void> {
  const passwordPolicies = await getAllPasswordPolicies(domain.id, accessToken).catch(() => []);
  for (const p of passwordPolicies) {
    await deletePasswordPolicy(domain.id, accessToken, p.id);
  }
}
