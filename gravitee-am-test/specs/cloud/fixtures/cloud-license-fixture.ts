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

import { getLicenseApi } from '@management-commands/service/utils';
import { CockpitQueueEntry } from '@cloud-commands/cockpit-commands';
import { stackLicenseBase64 } from '@cloud-commands/license-key';
import { retryUntil } from '@utils-commands/retry';
import { GraviteeLicense } from '../../../api/management/models/GraviteeLicense';
import { CloudOrganizationFixture } from './cloud-organization-fixture';

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };

export const EXAMPLE_EE_FEATURES = ['am-idp-saml', 'am-mfa-fido2'];
export const OSS_TIER = 'oss';

export interface CloudLicenseFixture {
  organizationId: string;
  /** Token scoped to this organization. */
  accessToken: string;
  /** The license the running stack itself holds, base64-encoded as Cockpit would send it. */
  licenseKey: string;
  /** The node's own license. In managed cloud this is expected to be the OSS license. */
  platformLicense: GraviteeLicense;
  /** The organization license the settings API currently serves. */
  organizationLicense: () => Promise<GraviteeLicense>;
  /** Push the stack's license through Cockpit and wait until it is registered for the organization. */
  pushLicense: () => Promise<CockpitQueueEntry>;
  /** Re-issue the organization command with no license and wait until the paid entitlements are gone. */
  clearLicense: () => Promise<CockpitQueueEntry>;
  cleanup: () => Promise<void>;
}

/**
 * Drives an organization's license the way Cockpit does, and reads it back through the settings API.
 *
 * Managed-cloud stack only (local-stack.sh --cloud).
 */
export const setupCloudLicenseFixture = async (organization: CloudOrganizationFixture): Promise<CloudLicenseFixture> => {
  const organizationId = organization.organizationId;
  const licenseApi = getLicenseApi(organization.accessToken);
  const licenseKey = stackLicenseBase64();

  const organizationLicense = () => licenseApi.getOrganizationLicense({ organizationId });
  const platformLicense = await licenseApi.getLicense();

  const pushOrganization = (license?: string): Promise<CockpitQueueEntry> => organization.resync(license === undefined ? {} : { license });

  // Registration is asynchronous: the license is persisted, then a LICENSE event drives
  // OrganizationLicenseManager to register it with the node's license manager.
  const awaitScope = (scope: string) => retryUntil(organizationLicense, (license) => license.scope === scope, POLL);

  const pushLicense = async () => {
    const reply = await pushOrganization(licenseKey);
    await awaitScope('ORGANIZATION');
    return reply;
  };

  const clearLicense = async () => {
    const reply = await pushOrganization();
    await awaitScope('PLATFORM');
    return reply;
  };

  return {
    organizationId,
    accessToken: organization.accessToken,
    licenseKey,
    platformLicense,
    organizationLicense,
    pushLicense,
    clearLicense,
    cleanup: () => clearLicense().then(() => undefined),
  };
};
