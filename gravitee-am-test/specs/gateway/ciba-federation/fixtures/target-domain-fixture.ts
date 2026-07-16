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
import { expect } from '@jest/globals';
import {
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  waitForOidcReady,
} from '@management-commands/domain-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createUser } from '@management-commands/user-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { performPost } from '@gateway-commands/oauth-oidc-commands';
import { waitForSyncAfter } from '@gateway-commands/monitoring-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Fixture } from '../../../test-fixture';

const CIBA_GRANT_TYPE = 'urn:openid:params:grant-type:ciba';

export const TARGET_USER = {
  username: uniqueName('ciba-fed-target-user', true),
  password: 'CibaFederationP@ssw0rd123!',
  firstName: 'CibaFederation',
  lastName: 'TargetUser',
  email: 'ciba.federation.target.user@test.com',
};

export interface TargetDomainFixture extends Fixture {
  domain: Domain;
  oidcConfig: any;
  user: any;
  resourceApp: { clientId: string; clientSecret: string };
  delegatedServiceApp: { clientId: string; clientSecret: string };
  switchNotifier: (target: 'accept-all' | 'reject-all') => Promise<void>;
}

async function createNotifier(
  managementUrl: string,
  domainId: string,
  accessToken: string,
  internalCibaUrl: string,
  endpointSuffix: 'accept-all' | 'reject-all',
): Promise<string> {
  const response = await performPost(
    `${managementUrl}${domainId}/auth-device-notifiers`,
    '',
    {
      type: 'http-am-authdevice-notifier',
      configuration: `{"endpoint":"${internalCibaUrl}/notify/${endpointSuffix}","headerName":"Authorization","connectTimeout":5000,"idleTimeout":10000,"maxPoolSize":10}`,
      name: `${endpointSuffix} notifier`,
    },
    { 'Content-type': 'application/json', Authorization: `Bearer ${accessToken}` },
  );
  expect(response.status).toBe(201);
  return response.body.id;
}

async function registerWithDelegatedService(
  cibaUrl: string,
  accessToken: string,
  domainId: string,
  gatewayCallback: string,
  clientId: string,
  clientSecret: string,
): Promise<void> {
  const res = await performPost(
    `${cibaUrl}/domains`,
    '',
    { domainId, domainCallback: gatewayCallback, clientId: encodeURIComponent(clientId), clientSecret },
    { 'Content-type': 'application/json', Authorization: `Bearer ${accessToken}` },
  );
  expect(res.status).toBe(200);
}

async function createResourceApplication(domainId: string, accessToken: string): Promise<{ clientId: string; clientSecret: string }> {
  const created = await createApplication(domainId, accessToken, {
    name: uniqueName('ciba-fed-resource', true),
    type: 'WEB',
    redirectUris: ['https://resource.example.com/callback'],
  });
  const clientSecret: string = created.settings.oauth.clientSecret;
  const updated = await updateApplication(
    domainId,
    accessToken,
    {
      settings: {
        oauth: {
          redirectUris: ['https://resource.example.com/callback'],
          grantTypes: ['authorization_code', CIBA_GRANT_TYPE],
          responseTypes: ['code'],
          tokenEndpointAuthMethod: 'client_secret_post',
          scopeSettings: [{ scope: 'openid', defaultScope: true }],
        },
      },
    },
    created.id,
  );
  return { clientId: updated.settings.oauth.clientId, clientSecret };
}

async function createDelegatedServiceApplication(
  domainId: string,
  accessToken: string,
): Promise<{ clientId: string; clientSecret: string }> {
  const created = await createApplication(domainId, accessToken, {
    name: uniqueName('ciba-fed-delegated-service', true),
    type: 'WEB',
    redirectUris: ['https://delegated-service.example.com/callback'],
  });
  const clientSecret: string = created.settings.oauth.clientSecret;
  const updated = await updateApplication(
    domainId,
    accessToken,
    {
      settings: {
        oauth: {
          redirectUris: ['https://delegated-service.example.com/callback'],
          // The delegated-service mock authenticates its accept-all/reject-all callback with HTTP Basic auth.
          tokenEndpointAuthMethod: 'client_secret_basic',
        },
      },
    },
    created.id,
  );
  return { clientId: updated.settings.oauth.clientId, clientSecret };
}

export const setupTargetDomain = async (accessToken: string): Promise<TargetDomainFixture> => {
  let domain: Domain | null = null;

  try {
    domain = await createDomain(accessToken, uniqueName('ciba-fed-target', true), 'CIBA federation target domain');
    expect(domain.id).toBeDefined();

    const idpSet = await getAllIdps(domain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    expect(defaultIdp).toBeDefined();

    const user = await createUser(domain.id, accessToken, {
      firstName: TARGET_USER.firstName,
      lastName: TARGET_USER.lastName,
      email: TARGET_USER.email,
      username: TARGET_USER.username,
      password: TARGET_USER.password,
      source: defaultIdp.id,
      preRegistration: false,
    });
    expect(user).toBeDefined();

    const managementUrl = `${process.env.AM_MANAGEMENT_URL}/management/organizations/DEFAULT/environments/DEFAULT/domains/`;
    const internalCibaUrl = process.env.AM_INTERNAL_CIBA_NOTIFIER_URL;
    const cibaUrl = process.env.AM_CIBA_NOTIFIER_URL;

    const acceptNotifierId = await createNotifier(managementUrl, domain.id, accessToken, internalCibaUrl, 'accept-all');
    const rejectNotifierId = await createNotifier(managementUrl, domain.id, accessToken, internalCibaUrl, 'reject-all');

    await patchDomain(domain.id, accessToken, {
      oidc: {
        cibaSettings: {
          enabled: true,
          authReqExpiry: 600,
          tokenReqInterval: 5,
          bindingMessageLength: 256,
          deviceNotifiers: [{ id: acceptNotifierId }],
        },
      },
    });

    const resourceApp = await createResourceApplication(domain.id, accessToken);
    const delegatedServiceApp = await createDelegatedServiceApplication(domain.id, accessToken);

    await startDomain(domain.id, accessToken);
    const startedDomain = await waitForDomainStart(domain);

    const gatewayCallback = `${process.env.AM_INTERNAL_GATEWAY_URL}/${startedDomain.domain.hrid}/oidc/ciba/authenticate/callback`;
    await registerWithDelegatedService(
      cibaUrl,
      accessToken,
      domain.id,
      gatewayCallback,
      delegatedServiceApp.clientId,
      delegatedServiceApp.clientSecret,
    );

    const switchNotifier = async (target: 'accept-all' | 'reject-all'): Promise<void> => {
      const id = target === 'accept-all' ? acceptNotifierId : rejectNotifierId;
      await waitForSyncAfter(domain!.id, () =>
        patchDomain(domain!.id, accessToken, {
          oidc: {
            cibaSettings: {
              enabled: true,
              authReqExpiry: 600,
              tokenReqInterval: 5,
              bindingMessageLength: 256,
              deviceNotifiers: [{ id }],
            },
          },
        }),
      );
      await waitForOidcReady(startedDomain.domain.hrid, { timeoutMs: 10000, intervalMs: 200 });
    };

    return {
      domain: startedDomain.domain,
      oidcConfig: startedDomain.oidcConfig,
      user,
      resourceApp,
      delegatedServiceApp,
      switchNotifier,
      cleanUp: async () => {
        await safeDeleteDomain(domain?.id, accessToken);
      },
    };
  } catch (error) {
    if (domain?.id) {
      await safeDeleteDomain(domain.id, accessToken);
    }
    throw error;
  }
};
