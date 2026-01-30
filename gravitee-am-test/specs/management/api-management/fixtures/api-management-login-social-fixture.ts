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
import request from 'supertest';
import { Domain } from '@management-models/Domain';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createIdp } from '@management-commands/idp-management-commands';
import { getDefaultApi, getIdpApi } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export interface ApiManagementLoginSocialFixture extends Fixture {
  accessToken: string;
  domain: Domain;
  domainId: string;
  domainHrid: string;
  currentIdp: string;
  newIdp: string;
  username: string;
  appId: string;
  clientId: string;
  clientSecret: string;
  gatewayUrl: string;
  internalGatewayUrl: string;
}

const ORG_ID = process.env.AM_DEF_ORG_ID!;
const SYNC_DELAY_MS = 5000;

function delay(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

export const setupApiManagementLoginSocialFixture = async (): Promise<ApiManagementLoginSocialFixture> => {
  const accessToken = await requestAdminAccessToken();
  const gatewayUrl = process.env.AM_GATEWAY_URL!;
  const internalGatewayUrl = process.env.AM_INTERNAL_GATEWAY_URL || process.env.AM_GATEWAY_URL!;

  const domain = await createDomain(
    accessToken,
    uniqueName('social', true),
    'test management login with social provider',
  );
  if (!domain.id || !domain.hrid) {
    throw new Error('Domain create did not return id/hrid');
  }

  await patchDomain(domain.id, accessToken, {
    oidc: {
      clientRegistrationSettings: {
        allowLocalhostRedirectUri: true,
        allowHttpSchemeRedirectUri: true,
        allowWildCardRedirectUri: true,
        isDynamicClientRegistrationEnabled: false,
        isOpenDynamicClientRegistrationEnabled: false,
        isAllowedScopesEnabled: false,
        isClientTemplateEnabled: false,
      },
    },
  });

  const username = uniqueName('social-user', true);
  const appName = uniqueName('social-client', true);
  const clientSecret = uniqueName('client-secret', true);

  const inMemoryIdp = await createIdp(domain.id, accessToken, {
    external: false,
    type: 'inline-am-idp',
    domainWhitelist: [],
    configuration: JSON.stringify({
      users: [
        {
          firstname: 'my-user',
          lastname: 'my-user-lastname',
          username,
          password: 'test',
        },
      ],
    }),
    name: 'inmemory',
  });
  const idpInmemoryId = inMemoryIdp.id!;

  const app = await createApplication(domain.id, accessToken, {
    name: appName,
    type: 'WEB' as any,
    clientId: appName,
    clientSecret,
    redirectUris: ['https://nowhere.com'],
  });
  const appId = app.id!;
  const clientId = app.settings?.oauth?.clientId ?? appName;

  await updateApplication(domain.id, accessToken, {
    identityProviders: new Set([{ identity: idpInmemoryId, priority: -1 }]),
    settings: {
      oauth: {
        redirectUris: ['https://nowhere.com', 'http://localhost:8093/management/auth/login/callback'],
        grantTypes: ['authorization_code', 'client_credentials', 'password', 'refresh_token'],
        tokenEndpointAuthMethod: 'client_secret_post',
        scopeSettings: [{ scope: 'openid', defaultScope: true }],
      },
      advanced: {
        skipConsent: true,
      },
    },
  } as any, appId);

  await startDomain(domain.id, accessToken);
  const started = await waitForDomainStart(domain);
  await waitForDomainSync(started.domain.id, accessToken);
  await delay(SYNC_DELAY_MS);

  const defaultApi = getDefaultApi(accessToken);
  const settings = await defaultApi.getOrganizationSettings({ organizationId: ORG_ID });
  const identities = settings.identities ? Array.from(settings.identities) : [];
  const currentIdp = identities[0];
  if (!currentIdp) {
    throw new Error('Organization has no identity provider');
  }

  const wellKnownUri = `${internalGatewayUrl}/${started.domain.hrid}/oidc/.well-known/openid-configuration`;

  const socialIdpBody = {
    external: true,
    type: 'oauth2-generic-am-idp',
    domainWhitelist: [] as string[],
    configuration: JSON.stringify({
      clientId,
      clientAuthenticationMethod: 'client_secret_post',
      clientSecret,
      wellKnownUri,
      responseType: 'code',
      responseMode: 'default',
      encodeRedirectUri: false,
      useIdTokenForUserInfo: false,
      signature: 'RSA_RS256',
      publicKeyResolver: 'GIVEN_KEY',
      connectTimeout: 10000,
      maxPoolSize: 200,
    }),
    name: 'Social',
  };

  const createRes = await request(process.env.AM_MANAGEMENT_URL)
    .post(`/management/organizations/${ORG_ID}/identities`)
    .set('Authorization', `Bearer ${accessToken}`)
    .set('Content-Type', 'application/json')
    .send(socialIdpBody)
    .expect(201);
  const newIdp = createRes.body.id;
  if (!newIdp) {
    throw new Error('Create identity did not return id');
  }

  await defaultApi.patchOrganizationSettings({
    organizationId: ORG_ID,
    patchOrganization: { identities: [currentIdp, newIdp] },
  });
  await delay(SYNC_DELAY_MS);

  const cleanUp = async () => {
    await safeDeleteDomain(domain.id, accessToken);
    await defaultApi.patchOrganizationSettings({
      organizationId: ORG_ID,
      patchOrganization: { identities: [currentIdp] },
    });
    try {
      await getIdpApi(accessToken).deleteIdentityProvider1({ organizationId: ORG_ID, identity: newIdp });
    } catch (e: any) {
      if (e?.response?.status !== 404) throw e;
    }
  };

  return {
    accessToken,
    domain: started.domain,
    domainId: started.domain.id!,
    domainHrid: started.domain.hrid!,
    currentIdp,
    newIdp,
    username,
    appId,
    clientId,
    clientSecret,
    gatewayUrl,
    internalGatewayUrl,
    cleanUp,
  };
};
