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
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getAllIdps } from '@management-commands/idp-management-commands';
import { createTestApp } from '@utils-commands/application-commands';
import { performGet, performPost } from '@gateway-commands/oauth-oidc-commands';
import { applicationBase64Token } from '@gateway-commands/utils';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';
import { IdentityProvider } from '@management-models/IdentityProvider';

export interface UmaConfiguration {
  resource_registration_endpoint: string;
  permission_endpoint: string;
  token_endpoint: string;
  introspection_endpoint: string;
  grant_types_supported: string[];
}

export interface UmaFixture {
  domain: Domain;
  rsApplication: Application;
  rqpApplication: Application;
  defaultIdp: IdentityProvider;
  umaConfig: UmaConfiguration;
  accessToken: string;
  cleanup: () => Promise<void>;
}

export const UMA_TEST = {
  DOMAIN_NAME_PREFIX: 'uma-grant',
  DOMAIN_DESCRIPTION: 'UMA grant domain',
  RS_CLIENT_NAME: 'uma-rs-client',
  RQP_CLIENT_NAME: 'uma-rqp-client',
  UMA_GRANT: 'urn:ietf:params:oauth:grant-type:uma-ticket',
  UMA_PROTECTION_SCOPE: 'uma_protection',
  RESOURCE_SCOPE: 'profile',
  RESOURCE_SCOPE_ALT: 'email',
};

async function enableUma(domainId: string, accessToken: string) {
  await patchDomain(domainId, accessToken, {
    uma: {
      enabled: true,
    },
  });
}

export const setupUmaFixture = async (): Promise<UmaFixture> => {
  let domain: Domain | null = null;
  let accessToken: string | null = null;

  try {
    accessToken = await requestAdminAccessToken();
    expect(accessToken).toBeDefined();

    const createdDomain = await createDomain(
      accessToken,
      uniqueName(UMA_TEST.DOMAIN_NAME_PREFIX, true),
      UMA_TEST.DOMAIN_DESCRIPTION,
    );
    expect(createdDomain).toBeDefined();
    expect(createdDomain.id).toBeDefined();
    domain = createdDomain;

    await enableUma(createdDomain.id, accessToken);

    const startedDomain = await startDomain(createdDomain.id, accessToken);
    const domainReady = await waitForDomainStart(startedDomain);
    await waitForDomainSync(domainReady.domain.id, accessToken);
    domain = domainReady.domain;

    const idpSet = await getAllIdps(domain.id, accessToken);
    const defaultIdp = idpSet.values().next().value;
    expect(defaultIdp).toBeDefined();

    const rsApplication = await createTestApp(uniqueName(UMA_TEST.RS_CLIENT_NAME, true), domain, accessToken, 'service', {
      settings: {
        oauth: {
          grantTypes: ['client_credentials'],
          scopeSettings: [{ scope: UMA_TEST.UMA_PROTECTION_SCOPE, defaultScope: false }],
        },
      },
    });

    const rqpApplication = await createTestApp(uniqueName(UMA_TEST.RQP_CLIENT_NAME, true), domain, accessToken, 'web', {
      settings: {
        oauth: {
          grantTypes: [UMA_TEST.UMA_GRANT],
          scopeSettings: [
            { scope: UMA_TEST.RESOURCE_SCOPE, defaultScope: false },
            { scope: UMA_TEST.RESOURCE_SCOPE_ALT, defaultScope: false },
          ],
        },
      },
      identityProviders: new Set([{ identity: defaultIdp.id, priority: 0 }]),
    });

    const umaResponse = await performGet(
      process.env.AM_GATEWAY_URL,
      `/${domain.hrid}/uma/.well-known/uma2-configuration`,
    ).expect(200);

    const cleanup = async () => {
      await safeDeleteDomain(domain?.id ?? null, accessToken);
    };

    return {
      domain,
      rsApplication,
      rqpApplication,
      defaultIdp,
      umaConfig: umaResponse.body,
      accessToken,
      cleanup,
    };
  } catch (error) {
    if (domain?.id && accessToken) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (cleanupError) {
        console.error('Failed to cleanup after setup failure:', cleanupError);
      }
    }
    throw error;
  }
};

export async function requestProtectionApiToken(fixture: UmaFixture) {
  const basicAuth = applicationBase64Token(fixture.rsApplication);
  const response = await performPost(
    fixture.umaConfig.token_endpoint,
    '',
    `grant_type=client_credentials&scope=${UMA_TEST.UMA_PROTECTION_SCOPE}`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${basicAuth}`,
    },
  ).expect(200);

  expect(response.body.access_token).toBeDefined();
  expect(response.body.scope).toBe(UMA_TEST.UMA_PROTECTION_SCOPE);
  return response.body.access_token as string;
}

export async function registerUmaResource(fixture: UmaFixture, protectionApiToken: string) {
  const response = await performPost(
    fixture.umaConfig.resource_registration_endpoint,
    '',
    {
      resource_scopes: [UMA_TEST.RESOURCE_SCOPE, UMA_TEST.RESOURCE_SCOPE_ALT],
      name: 'Profile access',
      description: 'Profile access resource',
    },
    {
      'Content-type': 'application/json',
      Authorization: `Bearer ${protectionApiToken}`,
    },
  ).expect(201);

  expect(response.body._id).toBeDefined();
  return response.body._id as string;
}

export async function requestPermissionTicket(
  fixture: UmaFixture,
  protectionApiToken: string,
  resourceId: string,
  scopes: string[] = [UMA_TEST.RESOURCE_SCOPE],
) {
  const response = await performPost(
    fixture.umaConfig.permission_endpoint,
    '',
    {
      resource_id: resourceId,
      resource_scopes: scopes,
    },
    {
      'Content-type': 'application/json',
      Authorization: `Bearer ${protectionApiToken}`,
    },
  ).expect(201);

  expect(response.body.ticket).toBeDefined();
  return response.body.ticket as string;
}

export async function requestRptToken(fixture: UmaFixture, ticket: string, scope?: string) {
  const basicAuth = applicationBase64Token(fixture.rqpApplication);
  const scopeParam = scope ? `&scope=${encodeURIComponent(scope)}` : '';
  return performPost(
    fixture.umaConfig.token_endpoint,
    '',
    `grant_type=${UMA_TEST.UMA_GRANT}&ticket=${ticket}${scopeParam}`,
    {
      'Content-type': 'application/x-www-form-urlencoded',
      Authorization: `Basic ${basicAuth}`,
    },
  );
}
