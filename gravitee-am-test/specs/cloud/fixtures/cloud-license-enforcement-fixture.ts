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

import { getDomainApi } from '@management-commands/service/utils';
import { safeDeleteDomain } from '@management-commands/domain-management-commands';
import { getDomainState, waitForDomainReady } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { DomainState } from '../../../api/gateway/apis/MonitoringApi';
import { CloudLicenseFixture, setupCloudLicenseFixture } from './cloud-license-fixture';

const DATA_PLANE_ID = process.env.AM_DOMAIN_DATA_PLANE_ID || 'default';

export const GATED_PLUGIN = 'magiclink-am-authenticator';

/** The gate's own wording, as DomainPluginLicenseGate records it on the domain state. */
export const UNLICENSED_MESSAGE = /is not included in your organization's license/;

export interface CloudLicenseEnforcementFixture extends CloudLicenseFixture {
  /**
   * Deploy a fresh domain and return the state the gateway recorded for it.
   */
  deployDomain: () => Promise<DomainState>;
  cleanup: () => Promise<void>;
}

/**
 * Adds domain deployment to the license fixture, so the gateway's decisions can be read off domain state.
 */
export const setupCloudLicenseEnforcementFixture = async (accessToken: string): Promise<CloudLicenseEnforcementFixture> => {
  const licenseFixture = await setupCloudLicenseFixture(accessToken);
  const organizationId = process.env.AM_DEF_ORG_ID!;
  const environmentId = process.env.AM_DEF_ENV_ID!;
  const domainApi = getDomainApi(accessToken);
  const domainIds: string[] = [];

  const deployDomain = async (): Promise<DomainState> => {
    const domain = await domainApi.createDomain({
      organizationId,
      environmentId,
      newDomain: { name: uniqueName('lic-gate', true), dataPlaneId: DATA_PLANE_ID },
    });
    domainIds.push(domain.id!);
    await domainApi.patchDomain({ organizationId, environmentId, domain: domain.id!, patchDomain: { enabled: true } });
    await waitForDomainReady(domain.id!);
    return getDomainState(domain.id!);
  };

  return {
    ...licenseFixture,
    deployDomain,
    cleanup: async () => {
      await Promise.all(domainIds.map((id) => safeDeleteDomain(id, accessToken)));
      await licenseFixture.cleanup();
    },
  };
};
