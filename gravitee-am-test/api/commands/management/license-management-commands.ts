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

import { GraviteeLicense } from '@management-models/GraviteeLicense';
import { getLicenseApi } from './service/utils';
import { retryUntil } from '@utils-commands/retry';

export const getOrgLicense = (accessToken: string, orgId = process.env.AM_DEF_ORG_ID): Promise<GraviteeLicense> =>
  getLicenseApi(accessToken).getOrganizationLicense({ organizationId: orgId });

export const getPlatformLicense = (accessToken: string): Promise<GraviteeLicense> => getLicenseApi(accessToken).getLicense();

/** Returns the raw HTTP Response for the org license endpoint — useful for asserting 401/403 status codes. */
export const getOrgLicenseRaw = (accessToken: string | null, orgId = process.env.AM_DEF_ORG_ID): Promise<Response> => {
  const headers: Record<string, string> = { 'Content-Type': 'application/json' };
  if (accessToken) {
    headers['Authorization'] = `Bearer ${accessToken}`;
  }
  return fetch(`${process.env.AM_MANAGEMENT_URL}/management/organizations/${orgId}/license`, { headers });
};

/** Poll the org license endpoint until `scope` matches. */
export const waitForOrgLicenseScope = (
  accessToken: string,
  scope: string,
  options?: { timeoutMillis?: number; intervalMillis?: number },
): Promise<GraviteeLicense> =>
  retryUntil(
    () => getOrgLicense(accessToken).catch(() => null),
    (license) => license?.scope === scope,
    {
      timeoutMillis: options?.timeoutMillis ?? 30000,
      intervalMillis: options?.intervalMillis ?? 1000,
    },
  ) as Promise<GraviteeLicense>;
