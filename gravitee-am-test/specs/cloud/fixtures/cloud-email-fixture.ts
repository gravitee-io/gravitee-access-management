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
import { getApplicationApi, getDomainApi, getEntrypointsApi, getUserApi } from '@management-commands/service/utils';
import { waitForOidcReady } from '@management-commands/domain-management-commands';
import { getDomainState, waitForDomainReady } from '@gateway-commands/monitoring-commands';
import { sendCockpitCommand } from '@cloud-commands/cockpit-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { retryUntil, withRetry } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';
import { expect } from '@jest/globals';

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };
const DATA_PLANE_ID = process.env.AM_DOMAIN_DATA_PLANE_ID || 'default';
const SCIM_CUSTOM_USER_SCHEMA = 'urn:ietf:params:scim:schemas:extension:custom:2.0:User';

export interface CloudEmailFixture {
  organizationId: string;
  environmentId: string;
  domainId: string;
  /** The single GATEWAY access point Cockpit provisioned for this environment, e.g. `gw-abc.example.com`. */
  entrypointHost: string;
  /** Create a pre-registered user through the management API; MAPI emails the confirmation link. */
  createPreRegisteredUser: (email: string) => Promise<any>;
  /** Re-send the registration confirmation for an existing pre-registered user. */
  resendRegistrationConfirmation: (userId: string) => Promise<void>;
  /** Provision a pre-registered user through SCIM; the gateway emails the confirmation link. */
  createScimUser: (email: string) => Promise<any>;
  cleanup: () => Promise<void>;
}

/**
 * A managed-cloud environment holding exactly one GATEWAY access point, plus a domain in it wired for
 * both management-API and SCIM user provisioning. One access point rather than several because these
 * specs assert the host of an emailed link, and the entrypoint tiebreak is covered by unit tests.
 *
 * Managed-cloud stack only (local-stack.sh --cloud).
 */
export const setupCloudEmailFixture = async (accessToken: string): Promise<CloudEmailFixture> => {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = uniqueName('env-email', true);
  const entrypointHost = `${uniqueName('gw', true)}.example.com`;
  const entrypointUrl = `https://${entrypointHost}`;

  // 1. Cockpit provisions the environment; its GATEWAY access point becomes the environment entrypoint.
  await sendCockpitCommand({
    type: 'ENVIRONMENT',
    payload: {
      id: environmentId,
      organizationId,
      hrids: [environmentId],
      name: 'AM7229 cloud email env',
      accessPoints: [{ target: 'GATEWAY', host: entrypointHost }],
    },
  });

  await retryUntil(
    () => getEntrypointsApi(accessToken).listEntrypoints({ organizationId }),
    (entrypoints: any[]) => entrypoints.some((e) => e.url === entrypointUrl),
    POLL,
  );

  // 2. A domain in that environment, with SCIM on and a service app able to call it. Configure before
  //    enabling the domain so the initial sync picks everything up.
  const domainApi = getDomainApi(accessToken);
  const domain = await domainApi.createDomain({
    organizationId,
    environmentId,
    newDomain: { name: uniqueName('email-entrypoint-domain', true), dataPlaneId: DATA_PLANE_ID },
  });

  await domainApi.patchDomain({
    organizationId,
    environmentId,
    domain: domain.id,
    patchDomain: { scim: { enabled: true, idpSelectionEnabled: false } },
  });

  const applicationApi = getApplicationApi(accessToken);
  const scimApp = await applicationApi.createApplication({
    organizationId,
    environmentId,
    domain: domain.id,
    newApplication: { name: uniqueName('scim-app', true), type: 'SERVICE' },
  });
  await applicationApi.updateApplication({
    organizationId,
    environmentId,
    domain: domain.id,
    application: scimApp.id,
    patchApplication: {
      settings: {
        oauth: {
          grantTypes: ['client_credentials'],
          scopeSettings: [{ scope: 'scim', defaultScope: true }],
        },
      },
    },
  });

  await domainApi.patchDomain({ organizationId, environmentId, domain: domain.id, patchDomain: { enabled: true } });
  await waitForDomainReady(domain.id);
  const oidcConfig = (await waitForOidcReady(domain.hrid)).body;

  // The entrypoint row is in the database by now, but neither plane reads it from there: each resolves
  // from its own in-memory cache, warmed a moment later by the entrypoint event. Gate on both caches or
  // the first test races them and sees the data plane fallback.
  await retryUntil(
    () => domainApi.getDomainEntrypoints({ organizationId, environmentId, domain: domain.id }),
    (entrypoints: any[]) => entrypoints.some((e) => e.url === entrypointUrl),
    POLL,
  );
  await retryUntil(
    () => getDomainState(domain.id),
    (state: any) => (state.entrypoints ?? []).some((e: any) => e.url === entrypointUrl),
    POLL,
  );

  // The service app lags the OIDC endpoint under load, so retry rather than return a token that 401s.
  const scimAccessToken = await withRetry(
    async () => {
      const response = await performPost(oidcConfig.token_endpoint, '', 'grant_type=client_credentials', {
        'Content-type': 'application/x-www-form-urlencoded',
        Authorization: 'Basic ' + applicationBase64Token(scimApp),
      }).expect(200);
      expect(response.body.access_token).toBeDefined();
      return response.body.access_token;
    },
    60,
    500,
  );

  const userApi = getUserApi(accessToken);

  const createPreRegisteredUser = (email: string) => {
    const username = uniqueName('prereg', true);
    return userApi.createUser({
      organizationId,
      environmentId,
      domain: domain.id,
      newUser: { username, firstName: 'Pre', lastName: 'Registered', email, preRegistration: true },
    });
  };

  const resendRegistrationConfirmation = async (userId: string) => {
    await userApi.sendRegistrationConfirmation({ organizationId, environmentId, domain: domain.id, user: userId });
  };

  // preRegistration is only honoured when the payload deserializes into a GraviteeUser, which needs the
  // custom schema URI both in `schemas` and as the key of the extension object (see UserMapper).
  const createScimUser = async (email: string) => {
    const username = uniqueName('scimuser', true);
    const response = await performPost(
      `${process.env.AM_GATEWAY_URL}/${domain.hrid}/scim`,
      '/Users',
      JSON.stringify({
        schemas: [SCIM_CUSTOM_USER_SCHEMA, 'urn:ietf:params:scim:schemas:core:2.0:User'],
        userName: username,
        password: null,
        name: { givenName: 'Scim', familyName: 'Provisioned' },
        emails: [{ value: email, primary: true }],
        active: true,
        [SCIM_CUSTOM_USER_SCHEMA]: { preRegistration: true },
      }),
      { Authorization: `Bearer ${scimAccessToken}`, 'Content-Type': 'application/json' },
    );
    expect(response.status).toEqual(201);
    return response.body;
  };

  // Only the domain. Entrypoints are Cockpit-owned and a managed installation rejects deleting them,
  // so attempting it only ever produces a 400 and a misleading warning.
  const cleanup = async () => {
    await domainApi
      .deleteDomain({ organizationId, environmentId, domain: domain.id })
      .catch((e) => console.warn(`cleanup: failed to delete domain ${domain.id}: ${e.message}`));
  };

  return {
    organizationId,
    environmentId,
    domainId: domain.id,
    entrypointHost,
    createPreRegisteredUser,
    resendRegistrationConfirmation,
    createScimUser,
    cleanup,
  };
};
