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
import * as faker from 'faker';
import { afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createDomain, safeDeleteDomain, patchDomain, startDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../../test-fixture';

let accessToken;
let domain;

setup(200000);

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  const createdDomain = await createDomain(accessToken, uniqueName('csp-test', true), faker.company.catchPhraseDescriptor());
  expect(createdDomain).toBeDefined();
  expect(createdDomain.id).toBeDefined();

  const startedDomain = await startDomain(createdDomain.id, accessToken);
  expect(startedDomain).toBeDefined();
  domain = startedDomain;
});

afterAll(async () => {
  if (domain?.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});

const patchCsp = (csp: Record<string, unknown>) =>
  patchDomain(domain.id, accessToken, {
    path: `${domain.path}`,
    vhostMode: false,
    vhosts: [],
    webProtectionSettings: {
      csp: {
        inherited: false,
        enabled: true,
        ...csp,
      },
    },
  });

/**
 * Patches and expects a 400, returning the server-supplied message so the test can assert it is
 * actually readable.
 */
const expectCspRejected = async (csp: Record<string, unknown>): Promise<string> => {
  let rejection: any;
  try {
    await patchCsp(csp);
  } catch (error) {
    rejection = error;
  }

  expect(rejection).toBeDefined();
  expect(rejection.response?.status).toBe(400);

  const raw = String(rejection.message ?? '');
  const bodyStart = raw.indexOf('body=');
  expect(bodyStart).toBeGreaterThanOrEqual(0);

  const body = JSON.parse(raw.slice(bodyStart + 'body='.length));
  expect(typeof body.message).toBe('string');
  return body.message;
};

describe('CSP settings validation', () => {
  describe('accepted policies', () => {
    it('should accept a simple directive list', async () => {
      const patched = await patchCsp({ directives: ["default-src 'self'", "script-src 'self' https://cdn.example.com"] });

      expect(patched.webProtectionSettings.csp.directives).toContain("default-src 'self'");
    });

    it('should accept trailing semicolons without altering validity', async () => {
      const patched = await patchCsp({ directives: ["default-src 'self';", "script-src 'self';"] });

      expect(patched.webProtectionSettings.csp.directives).toHaveLength(2);
    });

    it('should accept directives that take no value', async () => {
      const patched = await patchCsp({ directives: ["default-src 'self'", 'upgrade-insecure-requests'] });

      expect(patched.webProtectionSettings.csp.directives).toContain('upgrade-insecure-requests');
    });

    it('should accept an unknown but syntactically valid directive name', async () => {
      // The known-name list is a Console concern; the API must not block a directive CSP adds later.
      const patched = await patchCsp({ directives: ["some-future-directive 'self'"] });

      expect(patched.webProtectionSettings.csp.directives).toContain("some-future-directive 'self'");
    });

    it('should persist the operator casing verbatim', async () => {
      const patched = await patchCsp({ directives: ["Default-Src 'self'"] });

      expect(patched.webProtectionSettings.csp.directives).toContain("Default-Src 'self'");
    });

    it('should accept report-only with a report-uri', async () => {
      const patched = await patchCsp({
        reportOnly: true,
        directives: ["default-src 'self'", 'report-uri /csp-reports'],
      });

      expect(patched.webProtectionSettings.csp.reportOnly).toBe(true);
    });

    it('should accept report-only with a report-to', async () => {
      const patched = await patchCsp({
        reportOnly: true,
        directives: ["default-src 'self'", 'report-to csp-endpoint'],
      });

      expect(patched.webProtectionSettings.csp.reportOnly).toBe(true);
    });

    it('should not validate directives when CSP is inherited', async () => {
      const patched = await patchDomain(domain.id, accessToken, {
        path: `${domain.path}`,
        vhostMode: false,
        vhosts: [],
        webProtectionSettings: {
          csp: { inherited: true, enabled: false, directives: ['this is not valid'] },
        },
      });

      expect(patched.webProtectionSettings.csp.inherited).toBe(true);
    });
  });

  describe('rejected policies', () => {
    it('should reject an unknown-shaped directive name', async () => {
      const message = await expectCspRejected({ directives: ["script_src 'self'"] });

      expect(message).toContain('script_src');
      expect(message).toContain('not a valid CSP directive name');
    });

    it('should reject a directive with no value', async () => {
      const message = await expectCspRejected({ directives: ['default-src'] });

      expect(message).toContain('requires a value');
    });

    it('should reject duplicate directive names', async () => {
      const message = await expectCspRejected({ directives: ["default-src 'self'", "default-src 'none'"] });

      expect(message).toContain('more than once');
    });

    it('should reject duplicates differing only by case', async () => {
      const message = await expectCspRejected({ directives: ["script-src 'self'", "Script-Src 'none'"] });

      expect(message).toContain('more than once');
    });

    it('should reject several directives packed into one entry', async () => {
      const message = await expectCspRejected({ directives: ["default-src 'self'; script-src 'self'"] });

      expect(message).toContain(';');
    });

    it('should reject an empty directive list when CSP is enabled', async () => {
      const message = await expectCspRejected({ directives: [] });

      expect(message).toContain('At least one CSP directive');
    });

    it('should reject report-only without a report target', async () => {
      const message = await expectCspRejected({ reportOnly: true, directives: ["default-src 'self'"] });

      expect(message).toContain('report-uri');
      expect(message).toContain('report-to');
    });
  });
});
