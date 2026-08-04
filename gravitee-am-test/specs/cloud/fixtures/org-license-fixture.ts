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
import { drainCockpitQueue, waitForCockpitConnection } from '@cloud-commands/cockpit-commands';
import { bindSafeDeleteCloudDomain } from '@cloud-commands/domain-commands';
import { waitForOrgLicenseScope } from '@management-commands/license-management-commands';
import { stackLicenseBase64 } from '@cloud-commands/license-key';
import { setupCloudOrganizationFixture } from './cloud-organization-fixture';

export interface OrgLicenseFixture {
  /** Id of the Cockpit-provisioned organization. */
  organizationId: string;
  /** Home environment of that organization — domains must be created here. */
  environmentId: string;
  /** Cockpit id of the organization owner (USER command payload id / SSO `sub`). */
  userId: string;
  /** Token for the organization owner, obtained through Cockpit SSO. */
  accessToken: string;
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
  /** Best-effort delete of a domain in this organization's home environment. */
  deleteDomain: (domainId: string | null | undefined) => Promise<void>;
  cleanup: () => Promise<void>;
}

export const setupOrgLicenseFixture = async (organizationName = 'org-license'): Promise<OrgLicenseFixture> => {
  await waitForCockpitConnection();
  await drainCockpitQueue();

  const organization = await setupCloudOrganizationFixture(organizationName);
  const organizationId = organization.organizationId;
  const environmentId = organization.environmentId;
  const userId = organization.userId;
  const accessToken = organization.accessToken;

  const expiredLicensePath = process.env.AM_ORG_LICENSE_EXPIRED_PATH ?? null;
  const universeLicense = stackLicenseBase64();
  const expiredLicense = expiredLicensePath ? readFileSync(expiredLicensePath).toString('base64') : null;

  const setUniverseLicense = async (): Promise<void> => {
    const reply = await organization.resync({ license: universeLicense });
    if (reply.commandStatus !== 'SUCCEEDED') {
      throw new Error(`Failed to set universe license for ${organizationId}: ${reply.errorDetails}`);
    }
    await waitForOrgLicenseScope(accessToken, 'ORGANIZATION', { timeoutMillis: 30000 }, organizationId);
  };

  const clearOrgLicense = async (): Promise<void> => {
    const reply = await organization.resync();
    if (reply.commandStatus !== 'SUCCEEDED') {
      throw new Error(`Failed to clear org license for ${organizationId}: ${reply.errorDetails}`);
    }
    await waitForOrgLicenseScope(accessToken, 'PLATFORM', { timeoutMillis: 30000 }, organizationId);
  };

  const setExpiredLicense = async (): Promise<void> => {
    if (!expiredLicense) return;
    const reply = await organization.resync({ license: expiredLicense });
    if (reply.commandStatus !== 'SUCCEEDED') {
      throw new Error(`Failed to set expired license for ${organizationId}: ${reply.errorDetails}`);
    }
  };

  const deleteDomain = bindSafeDeleteCloudDomain({ accessToken, organizationId, environmentId });

  const cleanup = async (): Promise<void> => {
    try {
      await clearOrgLicense();
    } catch {
      // best-effort cleanup
    }
  };

  return {
    organizationId,
    environmentId,
    userId,
    accessToken,
    universeLicense,
    expiredLicense,
    hasExpiredLicense: expiredLicense !== null,
    setUniverseLicense,
    clearOrgLicense,
    setExpiredLicense,
    deleteDomain,
    cleanup,
  };
};
