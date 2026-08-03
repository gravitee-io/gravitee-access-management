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
import { createDomain, safeDeleteDomain, startDomain, waitForDomainStart } from '@management-commands/domain-management-commands';
import { createIdp, deleteIdp, getIdp, updateIdp } from '@management-commands/idp-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup, retryImmediatelyForThisFile } from '../test-fixture';
import { jira } from '@specs-utils/jira';
import { OrgLicenseFixture, setupOrgLicenseFixture } from './fixtures/org-license-fixture';

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

beforeAll(async () => {
  fixture = await setupOrgLicenseFixture();
  // Universe license required to start the domain without EE-gate interference.
  await fixture.setUniverseLicense();
  const domain = await createDomain(fixture.accessToken, uniqueName('am7241', true), 'AM-7241 plugin license gate');
  await startDomain(domain.id, fixture.accessToken);
  const started = await waitForDomainStart(domain);
  domainId = started.domain.id;
  await fixture.clearOrgLicense();
});

afterAll(async () => {
  if (eeIdpId) {
    await deleteIdp(domainId, fixture.accessToken, eeIdpId).catch(() => null);
  }
  await fixture.cleanup();
  await safeDeleteDomain(domainId, fixture.accessToken);
});

describe('OSS identity provider — no org license', () => {
  it(jira`should allow creating an OSS (inline) identity provider without an org license ${'AM-7241'}`, async () => {
    const idp = await createIdp(domainId, fixture.accessToken, {
      name: uniqueName('inline-oss'),
      type: INLINE_TYPE,
      configuration: inlineConfig,
    });
    expect(idp.id).toEqual(expect.any(String));
    await deleteIdp(domainId, fixture.accessToken, idp.id);
  });
});

describe('EE identity provider — no org license', () => {
  it(jira`should reject creating an EE (Azure AD) identity provider with 403 ${'AM-7241'}`, async () => {
    await expect(
      createIdp(domainId, fixture.accessToken, {
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
    const idp = await createIdp(domainId, fixture.accessToken, {
      name: uniqueName('azure-ee-licensed'),
      type: AZURE_AD_TYPE,
      configuration: azureAdConfig,
    });
    expect(idp.id).toEqual(expect.any(String));
    eeIdpId = idp.id;
  });

  it(jira`should allow updating the EE identity provider while the universe license is active ${'AM-7241'}`, async () => {
    const updated = await updateIdp(
      domainId,
      fixture.accessToken,
      { name: uniqueName('azure-ee-updated'), type: AZURE_AD_TYPE, configuration: azureAdConfig },
      eeIdpId,
    );
    expect(updated.id).toBe(eeIdpId);
  });
});

describe('EE identity provider — license removed after creation', () => {
  beforeAll(async () => {
    await fixture.clearOrgLicense();
  });

  it(jira`should reject updating an EE identity provider after the org license is removed ${'AM-7241'}`, async () => {
    await expect(
      updateIdp(
        domainId,
        fixture.accessToken,
        { name: uniqueName('azure-ee-update-blocked'), type: AZURE_AD_TYPE, configuration: azureAdConfig },
        eeIdpId,
      ),
    ).rejects.toMatchObject({ response: { status: 403 } });
  });

  it(jira`should allow reading an EE identity provider without an org license ${'AM-7241'}`, async () => {
    const idp = await getIdp(domainId, fixture.accessToken, eeIdpId);
    expect(idp.id).toBe(eeIdpId);
  });

  it(jira`should allow deleting an EE identity provider without an org license ${'AM-7241'}`, async () => {
    await deleteIdp(domainId, fixture.accessToken, eeIdpId);
    eeIdpId = null;
  });
});
