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
 * AM-7235 — Cockpit ORGANIZATION command mechanics.
 * Verifies that sending ORGANIZATION commands via the cockpit mock correctly
 * updates the org license stored in the management API.
 *
 * Requires: --cloud stack (local-stack.sh up --cloud).
 * Tests are order-dependent; run with ci:cloud (--runInBand).
 */

import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { sendCockpitCommand, sendOrgCommand, waitForCockpitReply } from '@cloud-commands/cockpit-commands';
import { getOrgLicense, waitForOrgLicenseScope } from '@management-commands/license-management-commands';
import { setup, retryImmediatelyForThisFile } from '../test-fixture';
import { jira } from '@specs-utils/jira';
import { OrgLicenseFixture, setupOrgLicenseFixture } from './fixtures/org-license-fixture';

setup(300000);
retryImmediatelyForThisFile();

let fixture: OrgLicenseFixture;

beforeAll(async () => {
  fixture = await setupOrgLicenseFixture();
  // Start from a clean no-license state so tests are predictable.
  await fixture.clearOrgLicense();
});

afterAll(async () => {
  await fixture.cleanup();
});

describe('Cockpit ORGANIZATION command — license set', () => {
  it(jira`should return SUCCEEDED and expose scope ORGANIZATION with tier universe ${'AM-7235'}`, async () => {
    const id = await sendOrgCommand(fixture.orgId, fixture.universeLicense);
    const reply = await waitForCockpitReply(id, { timeoutMillis: 15000 });

    expect(reply.commandStatus).toBe('SUCCEEDED');

    await waitForOrgLicenseScope(fixture.accessToken, 'ORGANIZATION');
    const license = await getOrgLicense(fixture.accessToken);
    expect(license.scope).toBe('ORGANIZATION');
    expect(license.tier).toBe('universe');
  });
});

describe('Cockpit ORGANIZATION command — license clear', () => {
  it(jira`should return SUCCEEDED and fall back to PLATFORM scope when license field is omitted ${'AM-7235'}`, async () => {
    const id = await sendOrgCommand(fixture.orgId);
    const reply = await waitForCockpitReply(id, { timeoutMillis: 15000 });

    expect(reply.commandStatus).toBe('SUCCEEDED');

    await waitForOrgLicenseScope(fixture.accessToken, 'PLATFORM');
    const license = await getOrgLicense(fixture.accessToken);
    expect(license.scope).toBe('PLATFORM');
  });
});

describe('Cockpit ORGANIZATION command — idempotency', () => {
  it(jira`should return SUCCEEDED on both sends when the same license is sent twice ${'AM-7235'}`, async () => {
    const id1 = await sendOrgCommand(fixture.orgId, fixture.universeLicense);
    const reply1 = await waitForCockpitReply(id1, { timeoutMillis: 15000 });
    expect(reply1.commandStatus).toBe('SUCCEEDED');

    await waitForOrgLicenseScope(fixture.accessToken, 'ORGANIZATION');

    const id2 = await sendOrgCommand(fixture.orgId, fixture.universeLicense);
    const reply2 = await waitForCockpitReply(id2, { timeoutMillis: 15000 });
    expect(reply2.commandStatus).toBe('SUCCEEDED');
  });

  afterAll(async () => {
    await fixture.clearOrgLicense();
  });
});

describe('Cockpit ORGANIZATION command — fake base64 payload', () => {
  it(jira`should return SUCCEEDED and assign oss tier for a valid-base64 but non-license payload ${'AM-7235'}`, async () => {
    const fakeLicense = Buffer.from('not-a-real-license-payload').toString('base64');
    const id = await sendOrgCommand(fixture.orgId, fakeLicense);
    const reply = await waitForCockpitReply(id, { timeoutMillis: 15000 });

    expect(reply.commandStatus).toBe('SUCCEEDED');

    await waitForOrgLicenseScope(fixture.accessToken, 'ORGANIZATION');
    const license = await getOrgLicense(fixture.accessToken);
    expect(license.tier).toBe('oss');
  });

  afterAll(async () => {
    await fixture.clearOrgLicense();
  });
});

describe('Cockpit ORGANIZATION command — blank license', () => {
  it(jira`should return ERROR with the non-blank-base64 message for a blank license value ${'AM-7235'}`, async () => {
    const id = await sendOrgCommand(fixture.orgId, '');
    const reply = await waitForCockpitReply(id, { timeoutMillis: 15000 });

    expect(reply.commandStatus).toBe('ERROR');
    expect(reply.errorDetails).toBe('License must be a non-blank base64-encoded value');
  });
});

describe('Cockpit ORGANIZATION command — non-base64 license', () => {
  it(jira`should return ERROR with the invalid-base64 message for a non-base64 license value ${'AM-7235'}`, async () => {
    const id = await sendOrgCommand(fixture.orgId, 'not-valid-base64!!!');
    const reply = await waitForCockpitReply(id, { timeoutMillis: 15000 });

    expect(reply.commandStatus).toBe('ERROR');
    expect(reply.errorDetails).toBe('License is not a valid base64-encoded value');
  });
});

describe('Cockpit ORGANIZATION command — missing id field', () => {
  it(jira`should return ERROR naming the missing id field when the payload omits it ${'AM-7235'}`, async () => {
    const id = await sendCockpitCommand({ type: 'ORGANIZATION', payload: { name: 'Unknown', hrids: ['unknown'] } });
    const reply = await waitForCockpitReply(id, { timeoutMillis: 15000 });

    expect(reply.commandStatus).toBe('ERROR');
    expect(reply.errorDetails).toContain('[id]');
  });
});

describe('Cockpit ORGANIZATION command — unknown org', () => {
  it(jira`should return SUCCEEDED for an org that does not yet exist ${'AM-7235'}`, async () => {
    const unknownOrgId = 'unknown-org-am-7235';
    const id = await sendOrgCommand(unknownOrgId, fixture.universeLicense);
    const reply = await waitForCockpitReply(id, { timeoutMillis: 15000 });

    expect(reply.commandStatus).toBe('SUCCEEDED');
  });
});
