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

/**
 * AM-7241 — Management API plugin license gate.
 * Verifies that EE identity providers are blocked (403) without an org license
 * and allowed (201) with a universe org license, while OSS providers are always allowed.
 *
 * Requires: --cloud stack (local-stack.sh up --cloud).
 * Tests are order-dependent; run with ci:cloud (--runInBand).
 */

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { waitForDomainStart } from '@management-commands/domain-management-commands';
import { getDomainApi, getIdpApi } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { setup, retryImmediatelyForThisFile } from '../test-fixture';
import { jira } from '@specs-utils/jira';
import { OrgLicenseFixture, setupOrgLicenseFixture } from './fixtures/org-license-fixture';
import { setupCloudSharedFixture } from './fixtures/cloud-shared-fixture';

setup(300000);
retryImmediatelyForThisFile();

const AZURE_AD_TYPE = 'azure-ad-am-idp';
const INLINE_TYPE = 'inline-am-idp';

const azureAdConfig = JSON.stringify({ tenantId: 'test-tenant', clientId: 'test', clientSecret: 'test' });
const inlineConfig = JSON.stringify({
  users: [{ firstname: 'test', lastname: 'user', username: 'testuser', password: 'Test@1234!' }],
});

let fixture: OrgLicenseFixture;
let domainId: string;
let eeIdpId: string | null = null;

const orgEnv = () => ({ organizationId: fixture.organizationId, environmentId: fixture.environmentId });

const createIdp = (idp: { name: string; type: string; configuration: string }) =>
  getIdpApi(fixture.accessToken).createIdentityProvider({
    ...orgEnv(),
    domain: domainId,
    newIdentityProvider: idp,
  });

const updateIdp = (idpId: string, body: { name: string; type: string; configuration: string }) =>
  getIdpApi(fixture.accessToken).updateIdentityProvider({
    ...orgEnv(),
    domain: domainId,
    identity: idpId,
    updateIdentityProvider: body,
  });

const getIdp = (idpId: string) =>
  getIdpApi(fixture.accessToken).findIdentityProvider({
    ...orgEnv(),
    domain: domainId,
    identity: idpId,
  });

const deleteIdp = (idpId: string) =>
  getIdpApi(fixture.accessToken).deleteIdentityProvider({
    ...orgEnv(),
    domain: domainId,
    identity: idpId,
  });

beforeAll(async () => {
  const shared = await setupCloudSharedFixture();
  fixture = await setupOrgLicenseFixture(shared);
  // Universe license required to start the domain without EE-gate interference.
  await fixture.setUniverseLicense();

  const domainApi = getDomainApi(fixture.accessToken);
  const domain = await domainApi.createDomain({
    ...orgEnv(),
    newDomain: { name: uniqueName('am7241', true), description: 'AM-7241 plugin license gate', dataPlaneId: shared.dataPlaneId },
  });
  await domainApi.patchDomain({ ...orgEnv(), domain: domain.id!, patchDomain: { enabled: true } });
  const started = await waitForDomainStart(domain);
  domainId = started.domain.id!;
  await fixture.clearOrgLicense();
});

afterAll(async () => {
  if (eeIdpId) {
    await deleteIdp(eeIdpId).catch(() => null);
  }
  await fixture?.deleteDomain(domainId);
  await fixture?.cleanup();
});

describe('OSS identity provider — no org license', () => {
  it(jira`should allow creating an OSS (inline) identity provider without an org license ${'AM-7241'}`, async () => {
    const idp = await createIdp({
      name: uniqueName('inline-oss'),
      type: INLINE_TYPE,
      configuration: inlineConfig,
    });
    expect(idp.id).toEqual(expect.any(String));
    await deleteIdp(idp.id!);
  });
});

describe('EE identity provider — no org license', () => {
  it(jira`should reject creating an EE (Azure AD) identity provider with 403 ${'AM-7241'}`, async () => {
    await expect(
      createIdp({
        name: uniqueName('azure-ee-blocked'),
        type: AZURE_AD_TYPE,
        configuration: azureAdConfig,
      }),
    ).rejects.toMatchObject({ response: { status: 403 } });
  });
});

describe('EE identity provider — with universe org license', () => {
  beforeAll(async () => {
    await fixture.setUniverseLicense();
  });

  it(jira`should allow creating an EE (Azure AD) identity provider with a universe org license ${'AM-7241'}`, async () => {
    const idp = await createIdp({
      name: uniqueName('azure-ee-licensed'),
      type: AZURE_AD_TYPE,
      configuration: azureAdConfig,
    });
    expect(idp.id).toEqual(expect.any(String));
    eeIdpId = idp.id!;
  });

  it(jira`should allow updating the EE identity provider while the universe license is active ${'AM-7241'}`, async () => {
    const updated = await updateIdp(eeIdpId!, {
      name: uniqueName('azure-ee-updated'),
      type: AZURE_AD_TYPE,
      configuration: azureAdConfig,
    });
    expect(updated.id).toBe(eeIdpId);
  });
});

describe('EE identity provider — license removed after creation', () => {
  beforeAll(async () => {
    await fixture.clearOrgLicense();
  });

  it(jira`should reject updating an EE identity provider after the org license is removed ${'AM-7241'}`, async () => {
    await expect(
      updateIdp(eeIdpId!, {
        name: uniqueName('azure-ee-update-blocked'),
        type: AZURE_AD_TYPE,
        configuration: azureAdConfig,
      }),
    ).rejects.toMatchObject({ response: { status: 403 } });
  });

  it(jira`should allow reading an EE identity provider without an org license ${'AM-7241'}`, async () => {
    const idp = await getIdp(eeIdpId!);
    expect(idp.id).toBe(eeIdpId);
  });

  it(jira`should allow deleting an EE identity provider without an org license ${'AM-7241'}`, async () => {
    await deleteIdp(eeIdpId!);
    eeIdpId = null;
  });
});
