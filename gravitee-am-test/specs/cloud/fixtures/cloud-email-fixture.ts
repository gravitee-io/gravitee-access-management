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
import { extractXsrfTokenAndActionResponse, performFormPost, performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { retryUntil, withRetry } from '@utils-commands/retry';
import { uniqueName } from '@utils-commands/misc';
import { expect } from '@jest/globals';

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };
const DATA_PLANE_ID = process.env.AM_DOMAIN_DATA_PLANE_ID || 'default';
const SCIM_CUSTOM_USER_SCHEMA = 'urn:ietf:params:scim:schemas:extension:custom:2.0:User';
const RESET_REDIRECT_URI = 'http://localhost:4000';

export interface CloudEmailFixture {
  organizationId: string;
  environmentId: string;
  domainId: string;
  /** The access point Cockpit generated. Reachable, but not what request-less flows resolve to. */
  entrypointHost: string;
  /** The customer's overriding access point, so what request-less flows resolve to. */
  overridingHost: string;
  /** Create a pre-registered user through the management API; MAPI emails the confirmation link. */
  createPreRegisteredUser: (email: string) => Promise<any>;
  /** Re-send the registration confirmation for an existing pre-registered user. */
  resendRegistrationConfirmation: (userId: string) => Promise<void>;
  /** Provision a pre-registered user through SCIM; the gateway emails the confirmation link. */
  createScimUser: (email: string) => Promise<any>;
  /** The address of the user the forgot-password flow is driven for. */
  resetPasswordUserEmail: string;
  /** Ask the gateway to email a reset link, as if the user reached it on `forwardedHost`. */
  requestForgotPassword: (forwardedHost: string) => Promise<void>;
  cleanup: () => Promise<void>;
}

/**
 * A managed-cloud environment with two GATEWAY access points: the one Cockpit generated, and the
 * customer's overriding one. Two rather than one so the specs can tell the two resolutions apart,
 * request-less flows resolve to the overriding host while a request-bearing flow follows the host the
 * user actually reached us on.
 *
 * Managed-cloud stack only (local-stack.sh --cloud).
 */
export const setupCloudEmailFixture = async (accessToken: string): Promise<CloudEmailFixture> => {
  const organizationId = process.env.AM_DEF_ORG_ID;
  const environmentId = uniqueName('env-email', true);
  const entrypointHost = `${uniqueName('gw', true)}.example.com`;
  const overridingHost = `${uniqueName('custom', true)}.example.com`;
  const entrypointUrl = `https://${entrypointHost}`;
  const overridingUrl = `https://${overridingHost}`;

  // 1. Cockpit provisions the environment. The access point it generates itself is the environment's
  //    default entrypoint; the customer's overriding one is what resolution prefers.
  await sendCockpitCommand({
    type: 'ENVIRONMENT',
    payload: {
      id: environmentId,
      organizationId,
      hrids: [environmentId],
      name: 'AM7229 cloud email env',
      accessPoints: [
        { target: 'GATEWAY', host: entrypointHost },
        { target: 'GATEWAY', host: overridingHost, overriding: true },
      ],
    },
  });

  await retryUntil(
    () => getEntrypointsApi(accessToken).listEntrypoints({ organizationId }),
    (entrypoints: any[]) => entrypoints.some((e) => e.url === entrypointUrl) && entrypoints.some((e) => e.url === overridingUrl),
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
    patchDomain: {
      scim: { enabled: true, idpSelectionEnabled: false },
      loginSettings: { forgotPasswordEnabled: true },
      // The reset app redirects to localhost, which the domain rejects by default.
      oidc: {
        clientRegistrationSettings: {
          allowLocalhostRedirectUri: true,
          allowHttpSchemeRedirectUri: true,
          allowWildCardRedirectUri: true,
        },
      },
    },
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

  // A web app and a real user, so the forgot-password form can be driven end to end.
  const webApp = await applicationApi.createApplication({
    organizationId,
    environmentId,
    domain: domain.id,
    newApplication: {
      name: uniqueName('reset-app', true),
      type: 'WEB',
      clientId: uniqueName('reset-app', true),
      redirectUris: [RESET_REDIRECT_URI],
    },
  });
  await applicationApi.updateApplication({
    organizationId,
    environmentId,
    domain: domain.id,
    application: webApp.id,
    patchApplication: {
      settings: {
        oauth: {
          redirectUris: [RESET_REDIRECT_URI],
          grantTypes: ['implicit', 'authorization_code', 'password', 'refresh_token'],
          scopeSettings: [{ scope: 'openid', defaultScope: true }],
        },
      },
      identityProviders: new Set([{ identity: `default-idp-${domain.id}`, priority: 0 }]),
    },
  });
  const resetClientId = webApp.settings.oauth.clientId;

  // Created before the domain starts, so the initial sync picks it up with no extra wait.
  const resetPasswordUserEmail = `reset-${uniqueName('user', true)}@acme.fr`;
  await getUserApi(accessToken).createUser({
    organizationId,
    environmentId,
    domain: domain.id,
    newUser: {
      username: uniqueName('resetuser', true),
      firstName: 'Reset',
      lastName: 'Me',
      email: resetPasswordUserEmail,
      password: 'Password123!',
      preRegistration: false,
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
    (entrypoints: any[]) => entrypoints.some((e) => e.url === overridingUrl),
    POLL,
  );
  // The gateway endpoint reports the raw cache, so both access points show up here.
  await retryUntil(
    () => getDomainState(domain.id),
    (state: any) => {
      const urls = (state.entrypoints ?? []).map((e: any) => e.url);
      return urls.includes(entrypointUrl) && urls.includes(overridingUrl);
    },
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

  // X-Forwarded-Host is how the gateway learns the public host behind a proxy, so it is what decides
  // the origin here. The request itself still goes to the local gateway.
  const requestForgotPassword = async (forwardedHost: string) => {
    const authParams = `?response_type=token&client_id=${resetClientId}&redirect_uri=${RESET_REDIRECT_URI}`;
    const authResponse = await performGet(oidcConfig.authorization_endpoint, authParams).expect(302);
    const { headers, token } = await extractXsrfTokenAndActionResponse(authResponse);

    await performFormPost(
      process.env.AM_GATEWAY_URL,
      `/${domain.hrid}/forgotPassword${authParams}`,
      {
        'X-XSRF-TOKEN': token,
        email: resetPasswordUserEmail,
        client_id: resetClientId,
      },
      {
        Cookie: headers['set-cookie'],
        'Content-type': 'application/x-www-form-urlencoded',
        'X-Forwarded-Host': forwardedHost,
        'X-Forwarded-Proto': 'https',
      },
    ).expect(302);
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
    overridingHost,
    createPreRegisteredUser,
    resendRegistrationConfirmation,
    createScimUser,
    resetPasswordUserEmail,
    requestForgotPassword,
    cleanup,
  };
};
