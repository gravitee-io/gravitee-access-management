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

import { describe, expect, it, beforeAll } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getApplicationApi, getDomainApi, getFactorApi, getIdpApi, getUserApi } from '../../api/commands/management/service/utils';
import {
  getApplicationName,
  getApplicationWithCustomIdpName,
  getCustomIdpId,
  getCustomIdpName,
  getCustomIdpUserName,
  getDataPlaneIds,
  getDefaultStoreUserName,
  getDomainName,
  getFactorId,
  getInstanceLabel,
} from '../../migration-seeding/seed';
import { setup } from '../test-fixture';

setup(60000);

const channelLabel = process.env.AM_MIGRATION_TEST_LABEL || 'alpha';

// Verify the seeded MAPI data on every configured data plane's domain.
describe.each(getDataPlaneIds())('migration MAPI data [data plane %s]', (dataPlaneId) => {
  let accessToken: string;
  const label = getInstanceLabel(channelLabel, dataPlaneId);
  const domainName = getDomainName(label);
  const applicationName = getApplicationName(label);
  const applicationWithCustomIdpName = getApplicationWithCustomIdpName(label);
  const factorId = getFactorId(label);
  const customIdpId = getCustomIdpId(label);
  const defaultStoreUserName = getDefaultStoreUserName(label);
  const customIdpUserName = getCustomIdpUserName(label);

  beforeAll(async () => {
    accessToken = await requestAdminAccessToken();
  });

  it('keeps the versioned MAPI data readable through the generated SDK', async () => {
    const domainApi = getDomainApi(accessToken);
    const applicationApi = getApplicationApi(accessToken);
    const factorApi = getFactorApi(accessToken);
    const idpApi = getIdpApi(accessToken);
    const organizationId = process.env.AM_DEF_ORG_ID;
    const environmentId = process.env.AM_DEF_ENV_ID;

    const domains = await domainApi.listDomains({ organizationId, environmentId, q: domainName });
    const domain = domains.data?.find((candidate) => candidate.name === domainName);

    expect(domain).toBeDefined();
    expect(domain.id).toBeDefined();

    const applications = await applicationApi.listApplications({
      organizationId,
      environmentId,
      domain: domain.id
    });
    const application = applications.data?.find((candidate) => candidate.name === applicationName);

    expect(application).toBeDefined();
    expect(application.name).toBe(applicationName);

    const applicationCustomIdp = applications.data?.find((candidate) => candidate.name === applicationWithCustomIdpName);

    expect(applicationCustomIdp).toBeDefined();
    expect(applicationCustomIdp.name).toBe(applicationWithCustomIdpName);

    const factor = await factorApi.getFactor({ organizationId, environmentId, domain: domain.id, factor: factorId });
    expect(factor.name).toBe(`Migration TOTP factor ${label}`);

    const idp = await idpApi.findIdentityProvider({ organizationId, environmentId, domain: domain.id, identity: customIdpId });
    expect(idp.name).toBe(getCustomIdpName(label));

    const applicationDetails = await applicationApi.findApplication({
      organizationId,
      environmentId,
      domain: domain.id,
      application: application.id,
    });
    const applicationFactors = applicationDetails.settings?.mfa?.factor?.applicationFactors || [];
    expect(applicationDetails.settings?.mfa?.factor?.defaultFactorId).toBe(factorId);
    expect(applicationFactors).toEqual(expect.arrayContaining([expect.objectContaining({ id: factorId })]));
  });
});
