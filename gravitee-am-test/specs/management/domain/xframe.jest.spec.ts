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
  const createdDomain = await createDomain(accessToken, uniqueName('xframe-test', true), faker.company.catchPhraseDescriptor());
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

const patchXFrame = (xframe: Record<string, unknown>) =>
  patchDomain(domain.id, accessToken, {
    path: `${domain.path}`,
    vhostMode: false,
    vhosts: [],
    webProtectionSettings: {
      xframe: {
        inherited: false,
        enabled: true,
        ...xframe,
      },
    },
  });

const expectXFrameRejected = async (xframe: Record<string, unknown>): Promise<string> => {
  let rejection: any;
  try {
    await patchXFrame(xframe);
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

describe('X-Frame-Options action validation', () => {
  describe('accepted actions', () => {
    it('should accept DENY', async () => {
      const patched = await patchXFrame({ action: 'DENY' });

      expect(patched.webProtectionSettings.xframe.action).toBe('DENY');
    });

    it('should accept SAMEORIGIN', async () => {
      const patched = await patchXFrame({ action: 'SAMEORIGIN' });

      expect(patched.webProtectionSettings.xframe.action).toBe('SAMEORIGIN');
    });

    it('should persist mixed case verbatim', async () => {
      const patched = await patchXFrame({ action: 'deny' });

      expect(patched.webProtectionSettings.xframe.action).toBe('deny');
    });

    it('should persist surrounding whitespace verbatim', async () => {
      const patched = await patchXFrame({ action: ' SAMEORIGIN ' });

      expect(patched.webProtectionSettings.xframe.action).toBe(' SAMEORIGIN ');
    });

    it('should accept an omitted action', async () => {
      const patched = await patchXFrame({});

      expect(patched.webProtectionSettings.xframe.enabled).toBe(true);
      expect(patched.webProtectionSettings.xframe.action ?? null).toBeNull();
    });

    it('should accept a blank action', async () => {
      const patched = await patchXFrame({ action: '   ' });

      expect(patched.webProtectionSettings.xframe.action).toBe('   ');
    });

    it('should not validate the action when X-Frame-Options is inherited', async () => {
      const patched = await patchDomain(domain.id, accessToken, {
        path: `${domain.path}`,
        vhostMode: false,
        vhosts: [],
        webProtectionSettings: {
          xframe: { inherited: true, enabled: false, action: 'BOGUS' },
        },
      });

      expect(patched.webProtectionSettings.xframe.inherited).toBe(true);
      expect(patched.webProtectionSettings.xframe.action).toBe('BOGUS');
    });

    it('should not validate the action when X-Frame-Options is disabled', async () => {
      const patched = await patchDomain(domain.id, accessToken, {
        path: `${domain.path}`,
        vhostMode: false,
        vhosts: [],
        webProtectionSettings: {
          xframe: { inherited: false, enabled: false, action: 'BOGUS' },
        },
      });

      expect(patched.webProtectionSettings.xframe.enabled).toBe(false);
      expect(patched.webProtectionSettings.xframe.action).toBe('BOGUS');
    });
  });

  describe('rejected actions', () => {
    it('should reject ALLOW-FROM', async () => {
      const message = await expectXFrameRejected({ action: 'ALLOW-FROM' });

      expect(message).toContain('ALLOW-FROM');
    });

    it('should reject ALLOW-FROM with a url', async () => {
      const message = await expectXFrameRejected({ action: 'ALLOW-FROM https://example.com' });

      expect(message).toContain('ALLOW-FROM https://example.com');
    });

    it('should reject any other token', async () => {
      const message = await expectXFrameRejected({ action: 'BOGUS' });

      expect(message).toContain('BOGUS');
    });
  });
});
