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

import { readFileSync } from 'fs';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { drainCockpitQueue, sendOrgCommand, waitForCockpitConnection, waitForCockpitReply } from '@cloud-commands/cockpit-commands';
import { waitForOrgLicenseScope } from '@management-commands/license-management-commands';
import { stackLicenseBase64 } from '@cloud-commands/license-key';

export interface OrgLicenseFixture {
  accessToken: string;
  orgId: string;
  /** Base64-encoded universe license key. */
  universeLicense: string;
  /** Base64-encoded expired license key — only present when AM_ORG_LICENSE_EXPIRED_PATH is set. */
  expiredLicense: string | null;
  hasExpiredLicense: boolean;
  /** Send universe license and wait for scope to become ORGANIZATION. */
  setUniverseLicense: () => Promise<void>;
  /** Clear the org license and wait for scope to fall back to PLATFORM. */
  clearOrgLicense: () => Promise<void>;
  /** Send expired license via cockpit (noop when no expired license path is configured). */
  setExpiredLicense: () => Promise<void>;
  cleanup: () => Promise<void>;
}

export const setupOrgLicenseFixture = async (): Promise<OrgLicenseFixture> => {
  const accessToken = await requestAdminAccessToken();
  await waitForCockpitConnection();
  await drainCockpitQueue();

  const orgId = process.env.AM_DEF_ORG_ID;
  const expiredLicensePath = process.env.AM_ORG_LICENSE_EXPIRED_PATH ?? null;

  const universeLicense = stackLicenseBase64();
  const expiredLicense = expiredLicensePath ? readFileSync(expiredLicensePath).toString('base64') : null;

  const setUniverseLicense = async (): Promise<void> => {
    const id = await sendOrgCommand(orgId, universeLicense);
    await waitForCockpitReply(id, { timeoutMillis: 15000 });
    await waitForOrgLicenseScope(accessToken, 'ORGANIZATION', { timeoutMillis: 30000 });
  };

  const clearOrgLicense = async (): Promise<void> => {
    const id = await sendOrgCommand(orgId);
    await waitForCockpitReply(id, { timeoutMillis: 15000 });
    await waitForOrgLicenseScope(accessToken, 'PLATFORM', { timeoutMillis: 30000 });
  };

  const setExpiredLicense = async (): Promise<void> => {
    if (!expiredLicense) return;
    const id = await sendOrgCommand(orgId, expiredLicense);
    await waitForCockpitReply(id, { timeoutMillis: 15000 });
  };

  const cleanup = async (): Promise<void> => {
    try {
      await clearOrgLicense();
    } catch {
      // best-effort cleanup
    }
  };

  return {
    accessToken,
    orgId,
    universeLicense,
    expiredLicense,
    hasExpiredLicense: expiredLicense !== null,
    setUniverseLicense,
    clearOrgLicense,
    setExpiredLicense,
    cleanup,
  };
};
