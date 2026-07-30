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
import { CockpitQueueEntry, sendCockpitCommand, waitForCockpitReply } from '@cloud-commands/cockpit-commands';
import { stackLicenseBase64 } from '@cloud-commands/license-key';
import { retryUntil } from '@utils-commands/retry';
import { GraviteeLicense } from '../../../api/management/models/GraviteeLicense';

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };

export const EXAMPLE_EE_FEATURES = ['am-idp-saml', 'am-mfa-fido2'];
export const OSS_TIER = 'oss';

/**
 * The default organization's own attributes, as OrganizationServiceImpl.createDefault() sets them.
 */
const DEFAULT_ORGANIZATION = {
  name: 'Default organization',
  description: 'Default organization',
  hrids: ['default'],
  accessPoints: [],
};

export interface CloudLicenseFixture {
  organizationId: string;
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
 * Scoped to the default organization by necessity.
 * Managed-cloud stack only (local-stack.sh --cloud).
 */
export const setupCloudLicenseFixture = async (accessToken: string): Promise<CloudLicenseFixture> => {
  const organizationId = process.env.AM_DEF_ORG_ID!;
  const licenseApi = getLicenseApi(accessToken);
  const licenseKey = stackLicenseBase64();

  const organizationLicense = () => licenseApi.getOrganizationLicense({ organizationId });
  const platformLicense = await licenseApi.getLicense();

  const pushOrganization = async (license?: string): Promise<CockpitQueueEntry> => {
    const commandId = await sendCockpitCommand({
      type: 'ORGANIZATION',
      payload: { id: organizationId, ...DEFAULT_ORGANIZATION, ...(license === undefined ? {} : { license }) },
    });
    return waitForCockpitReply(commandId);
  };

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
    licenseKey,
    platformLicense,
    organizationLicense,
    pushLicense,
    clearLicense,
    cleanup: () => clearLicense().then(() => undefined),
  };
};
