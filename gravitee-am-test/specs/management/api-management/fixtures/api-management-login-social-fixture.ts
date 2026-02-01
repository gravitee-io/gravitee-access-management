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
import request from 'supertest';
import { performGet, performFormPost } from '@gateway-commands/oauth-oidc-commands';
import { Domain } from '@management-models/Domain';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  createDomain,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitFor,
  waitForDomainStart,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { createIdp } from '@management-commands/idp-management-commands';
import { getDefaultApi, getIdpApi } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';
import {
  cookieHeaderFromSetCookie,
  mergeCookieStrings,
  extractSocialUrlFromManagementLoginHtml,
  extractXsrfAndActionFromSocialLoginHtml,
} from '../management-auth-helper';
import { getLoginForm as getLoginFormFromUtils, parseLocation } from '../api-management-utils';

export { getLoginForm } from '../api-management-utils';

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

export async function getSocialForm(
  managementUrl: string,
  redirectUri: string,
  f: ApiManagementLoginSocialFixture,
) {
  const { loginFormRes, cookie } = await getLoginFormFromUtils(managementUrl, redirectUri);
  const socialUrl = extractSocialUrlFromManagementLoginHtml(
    loginFormRes.text,
    f.internalGatewayUrl,
    f.gatewayUrl,
  );
  const socialRes = await performGet(
    new URL(socialUrl).origin,
    new URL(socialUrl).pathname + new URL(socialUrl).search,
    { Cookie: cookie },
  );
  const socialLocation = socialRes.headers.location;
  expect(socialLocation).toBeDefined();

  const { origin: o2, pathAndSearch: p2 } = parseLocation(socialLocation!, f.gatewayUrl);
  const socialCookie = cookieHeaderFromSetCookie(socialRes.headers['set-cookie']);
  expect(socialCookie).toBeDefined();
  
  const socialFormRes = await performGet(o2, p2, { Cookie: socialCookie });
  return { socialFormRes, socialCookie };
}

export async function runLoginFlowWithCookieJar(
  managementUrl: string,
  gatewayUrl: string,
  redirectUri: string,
  f: ApiManagementLoginSocialFixture,
): Promise<{
  jar: Record<string, string>;
  postRes: Awaited<ReturnType<typeof performFormPost>>;
}> {
  const managementOrigin = new URL(managementUrl).origin;
  const gatewayOrigin = new URL(gatewayUrl).origin;
  const jar: Record<string, string> = {};

  const authorizePath = `/management/auth/authorize?redirect_uri=${encodeURIComponent(redirectUri)}`;
  const initiateRes = await performGet(managementUrl, authorizePath);
  const initCookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
  expect(initCookie).toBeDefined();

  jar[managementOrigin] = mergeCookieStrings(jar[managementOrigin], initCookie);

  const initLocation = initiateRes.headers.location;
  expect(initLocation).toBeDefined();

  const { origin, pathAndSearch } = parseLocation(initLocation!, managementUrl);
  const loginFormRes = await performGet(origin, pathAndSearch, { Cookie: jar[origin]! });
  const loginCookie = cookieHeaderFromSetCookie(loginFormRes.headers['set-cookie']);
  if (loginCookie) {
    jar[origin] = mergeCookieStrings(jar[origin], loginCookie);
  }

  const socialUrl = extractSocialUrlFromManagementLoginHtml(
    loginFormRes.text,
    f.internalGatewayUrl,
    f.gatewayUrl,
  );
  const socialOrigin = new URL(socialUrl).origin;
  const socialRes = await performGet(
    socialOrigin,
    new URL(socialUrl).pathname + new URL(socialUrl).search,
    jar[socialOrigin] ? { Cookie: jar[socialOrigin]! } : undefined,
  );
  const socialResCookie = cookieHeaderFromSetCookie(socialRes.headers['set-cookie']);
  expect(socialResCookie).toBeDefined();

  jar[gatewayOrigin] = mergeCookieStrings(jar[gatewayOrigin], socialResCookie);

  const socialResLocation = socialRes.headers.location;
  expect(socialResLocation).toBeDefined();

  const { origin: o2, pathAndSearch: p2 } = parseLocation(socialResLocation!, gatewayUrl);
  const socialFormRes = await performGet(o2, p2, { Cookie: jar[o2]! });
  const formCookie = cookieHeaderFromSetCookie(socialFormRes.headers['set-cookie']);
  expect(formCookie).toBeDefined();

  jar[o2] = mergeCookieStrings(jar[o2], formCookie);

  const { xsrf, action } = extractXsrfAndActionFromSocialLoginHtml(socialFormRes.text);
  const actionUrl = new URL(action);
  expect(jar[actionUrl.origin]).toBeDefined();

  const postHeaders: Record<string, string> = {
    'Content-Type': 'application/x-www-form-urlencoded',
    Cookie: jar[actionUrl.origin]!,
  };
  const postRes = await performFormPost(
    actionUrl.origin,
    actionUrl.pathname + actionUrl.search,
    {
      'X-XSRF-TOKEN': xsrf,
      username: f.username,
      password: 'test',
      client_id: f.clientId,
    },
    postHeaders,
  );
  const postResCookie = cookieHeaderFromSetCookie(postRes.headers['set-cookie']);
  expect(postResCookie).toBeDefined();

  jar[actionUrl.origin] = mergeCookieStrings(jar[actionUrl.origin], postResCookie);

  return { jar, postRes };
}

export async function followRedirectsUntil(
  jar: Record<string, string>,
  initialLocation: string | undefined,
  managementUrl: string,
  gatewayUrl: string,
  maxSteps: number,
): Promise<string> {
  expect(initialLocation).toBeDefined();
  let loc = initialLocation!;
  for (let i = 0; i < maxSteps; i++) {
    const base = (loc.includes(managementUrl) || loc.startsWith('/management')) ? managementUrl : gatewayUrl;
    const { origin: o, pathAndSearch: p } = parseLocation(loc, base);
    expect(jar[o]).toBeDefined();

    const next = await performGet(o, p, { Cookie: jar[o]! });
    const c = cookieHeaderFromSetCookie(next.headers['set-cookie']);
    expect(c).toBeDefined();

    jar[o] = mergeCookieStrings(jar[o], c);
    expect([200, 302]).toContain(next.status);
    const nextLoc = next.headers.location;
    expect(nextLoc).toBeDefined();

    loc = nextLoc!;
    if (loc.includes('nowhere.com')) {
      return loc;
    }
  }
  throw new Error(`Did not reach redirect_uri (nowhere.com) within ${maxSteps} redirects; last location: ${loc}`);
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
  await waitFor(SYNC_DELAY_MS);

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
  await waitFor(SYNC_DELAY_MS);

  const cleanUp = async () => {
    await safeDeleteDomain(domain.id, accessToken);
    await defaultApi.patchOrganizationSettings({
      organizationId: ORG_ID,
      patchOrganization: { identities: [currentIdp] },
    });
    try {
      await getIdpApi(accessToken).deleteIdentityProvider1({ organizationId: ORG_ID, identity: newIdp });
    } catch (e: any) {
      if (e?.response?.status !== 404) 
        {
          throw e;
        }
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
