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
 * AM-7236 — Organization license endpoint responses.
 * Verifies that GET /organizations/{orgId}/license returns the correct license
 * data depending on the org's current license state.
 *
 * Requires: --cloud stack (local-stack.sh up --cloud).
 * Tests are order-dependent; run with ci:cloud (--runInBand).
 */

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { sendOrgCommand, waitForCockpitReply } from '@cloud-commands/cockpit-commands';
import {
  getOrgLicense,
  getOrgLicenseRaw,
  getPlatformLicense,
  waitForOrgLicenseScope,
} from '@management-commands/license-management-commands';
import { setup, retryImmediatelyForThisFile } from '../test-fixture';
import { jira } from '@specs-utils/jira';
import { OrgLicenseFixture, setupOrgLicenseFixture } from './fixtures/org-license-fixture';

setup(300000);
retryImmediatelyForThisFile();

let fixture: OrgLicenseFixture;

beforeAll(async () => {
  fixture = await setupOrgLicenseFixture('org-license-endpoint');
  await fixture.clearOrgLicense();
});

afterAll(async () => {
  await fixture?.cleanup();
});

describe('GET /organizations/{orgId}/license — with universe org license', () => {
  beforeAll(async () => {
    await fixture.setUniverseLicense();
  });

  afterAll(async () => {
    await fixture.clearOrgLicense();
  });

  it(jira`should return scope ORGANIZATION and tier universe ${'AM-7236'}`, async () => {
    const license = await getOrgLicense(fixture.accessToken, fixture.organizationId);
    expect(license.scope).toBe('ORGANIZATION');
    expect(license.tier).toBe('universe');
  });

  it(jira`should include a non-null expiresAt ${'AM-7236'}`, async () => {
    const license = await getOrgLicense(fixture.accessToken, fixture.organizationId);
    expect(license.expiresAt).not.toBeNull();
    expect(typeof license.expiresAt).toBe('number');
  });

  it(jira`should always include the scope property in the response ${'AM-7236'}`, async () => {
    const license = await getOrgLicense(fixture.accessToken, fixture.organizationId);
    expect(Object.prototype.hasOwnProperty.call(license, 'scope')).toBe(true);
  });
});

describe('GET /organizations/{orgId}/license — no org license', () => {
  it(jira`should fall back to PLATFORM scope when no org license is set ${'AM-7236'}`, async () => {
    const license = await getOrgLicense(fixture.accessToken, fixture.organizationId);
    expect(license.scope).toBe('PLATFORM');
  });
});

describe('GET /organizations/{orgId}/license — fake base64 org license', () => {
  beforeAll(async () => {
    const fakeLicense = Buffer.from('not-a-real-license').toString('base64');
    const id = await sendOrgCommand(fixture.organizationId, fakeLicense);
    await waitForCockpitReply(id, { timeoutMillis: 15000 });
    await waitForOrgLicenseScope(fixture.accessToken, 'ORGANIZATION', undefined, fixture.organizationId);
  });

  afterAll(async () => {
    await fixture.clearOrgLicense();
  });

  it(jira`should return scope ORGANIZATION and tier oss for a fake license payload ${'AM-7236'}`, async () => {
    const license = await getOrgLicense(fixture.accessToken, fixture.organizationId);
    expect(license.scope).toBe('ORGANIZATION');
    expect(license.tier).toBe('oss');
  });
});

describe('GET /platform/license — platform license endpoint', () => {
  it(jira`should return scope PLATFORM regardless of org license state ${'AM-7236'}`, async () => {
    const license = await getPlatformLicense(fixture.accessToken);
    expect(license.scope).toBe('PLATFORM');
  });
});

describe('GET /organizations/{orgId}/license — authentication', () => {
  it(jira`should return 401 when no token is provided ${'AM-7236'}`, async () => {
    const response = await getOrgLicenseRaw(null, fixture.organizationId);
    expect(response.status).toBe(401);
  });
});
