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
import * as cheerio from 'cheerio';
import request from 'supertest';
// Types only: '@management-models' is mapped in tsconfig but not in the jest moduleNameMapper,
// so a runtime value import from here fails to resolve.
import type { Domain } from '@management-models/Domain';
import type { NewRoleAssignableTypeEnum } from '@management-models/NewRole';
import { performGet, performFormPost } from '@gateway-commands/oauth-oidc-commands';
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
import {
  createCustomOrganizationRole,
  deleteOrganizationRole,
  findOrganizationRoleByName,
} from '@management-commands/role-management-commands';
import { deleteOrganisationUser, searchOrganisationUsers } from '@management-commands/organisation-user-commands';
import { getDefaultApi, getIdpApi } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { retryUntil } from '@utils-commands/retry';
import { Fixture } from '../../../test-fixture';
import {
  cookieHeaderFromSetCookie,
  mergeCookieStrings,
  extractSocialUrlFromManagementLoginHtml,
  extractXsrfAndActionFromSocialLoginHtml,
  getLoginForm,
  parseLocation,
} from '../../api-management/management-auth-helper';

const MANAGEMENT_URL = process.env.AM_MANAGEMENT_URL!;
const ORG_ID = process.env.AM_DEF_ORG_ID!;
const REDIRECT_URI = 'https://nowhere.com';

/** Password shared by every inline user created on the identity-provider domains. */
const IDP_USER_PASSWORD = 'test';

/** Value of NewRoleAssignableTypeEnum.Organization, inlined because the enum is type-only here. */
const ASSIGNABLE_TYPE_ORGANIZATION = 'ORGANIZATION' as NewRoleAssignableTypeEnum;

export interface OrgProvider {
  /** Organization-level identity provider id. */
  id: string;
  /** Name, which is also how its button is identified on the Console login page. */
  name: string;
  /** Role the mapper on this provider assigns. */
  mappedRoleId: string;
  mappedRoleName: string;
  /** Username on the backing domain whose claims satisfy the mapper rule. */
  matchingUsername: string;
  /** Username on the backing domain whose claims do NOT satisfy the mapper rule. */
  nonMatchingUsername: string;
  /** OAuth client id of the application on the backing domain. */
  clientId: string;
}

export interface OrgIdpRoleMapperFixture extends Fixture {
  accessToken: string;
  gatewayUrl: string;
  internalGatewayUrl: string;
  /** Role every Console user falls back to when no mapper rule matches. */
  defaultRoleId: string;
  defaultRoleName: string;
  providerA: OrgProvider;
  providerB: OrgProvider;
}

/**
 * Sign in to the Console through a named organization identity provider, following the
 * redirect chain to the configured redirect address.
 *
 * Modelled on `runLoginFlowWithCookieJar` in the UC-AM3 fixture, but selects the provider by
 * name so that a stack with more than one registered provider is unambiguous.
 */
export async function signInThroughOrgProvider(f: OrgIdpRoleMapperFixture, provider: OrgProvider, username: string): Promise<void> {
  const managementOrigin = new URL(MANAGEMENT_URL).origin;
  const gatewayOrigin = new URL(f.gatewayUrl).origin;
  const jar: Record<string, string> = {};

  const initiateRes = await performGet(MANAGEMENT_URL, `/management/auth/authorize?redirect_uri=${encodeURIComponent(REDIRECT_URI)}`);
  const initCookie = cookieHeaderFromSetCookie(initiateRes.headers['set-cookie']);
  expect(initCookie).toBeDefined();
  jar[managementOrigin] = mergeCookieStrings(jar[managementOrigin], initCookie);

  const initLocation = initiateRes.headers.location;
  expect(initLocation).toBeDefined();

  const { origin, pathAndSearch } = parseLocation(initLocation!, MANAGEMENT_URL);
  const loginFormRes = await performGet(origin, pathAndSearch, { Cookie: jar[origin]! });
  const loginCookie = cookieHeaderFromSetCookie(loginFormRes.headers['set-cookie']);
  if (loginCookie) {
    jar[origin] = mergeCookieStrings(jar[origin], loginCookie);
  }

  const socialUrl = extractSocialUrlFromManagementLoginHtml(loginFormRes.text, f.internalGatewayUrl, f.gatewayUrl, provider.name);
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

  const { origin: o2, pathAndSearch: p2 } = parseLocation(socialResLocation!, f.gatewayUrl);
  const socialFormRes = await performGet(o2, p2, { Cookie: jar[o2]! });
  const formCookie = cookieHeaderFromSetCookie(socialFormRes.headers['set-cookie']);
  expect(formCookie).toBeDefined();
  jar[o2] = mergeCookieStrings(jar[o2], formCookie);

  const { xsrf, action } = extractXsrfAndActionFromSocialLoginHtml(socialFormRes.text);
  const actionUrl = new URL(action);
  expect(jar[actionUrl.origin]).toBeDefined();

  const postRes = await performFormPost(
    actionUrl.origin,
    actionUrl.pathname + actionUrl.search,
    {
      'X-XSRF-TOKEN': xsrf,
      username,
      password: IDP_USER_PASSWORD,
      client_id: provider.clientId,
    },
    {
      'Content-Type': 'application/x-www-form-urlencoded',
      Cookie: jar[actionUrl.origin]!,
    },
  );
  const postResCookie = cookieHeaderFromSetCookie(postRes.headers['set-cookie']);
  expect(postResCookie).toBeDefined();
  jar[actionUrl.origin] = mergeCookieStrings(jar[actionUrl.origin], postResCookie);

  // Follow the chain back to the Console. The organization user, and the membership carrying
  // the mapped role, are provisioned during these redirects.
  let loc = postRes.headers.location;
  expect(loc).toBeDefined();
  for (let i = 0; i < 32; i++) {
    const base = loc!.includes(MANAGEMENT_URL) || loc!.startsWith('/management') ? MANAGEMENT_URL : f.gatewayUrl;
    const { origin: o, pathAndSearch: p } = parseLocation(loc!, base);
    expect(jar[o]).toBeDefined();

    const next = await performGet(o, p, { Cookie: jar[o]! });
    const c = cookieHeaderFromSetCookie(next.headers['set-cookie']);
    jar[o] = mergeCookieStrings(jar[o], c);
    expect([200, 302]).toContain(next.status);

    loc = next.headers.location;
    if (!loc) {
      return;
    }
    if (loc.includes('nowhere.com')) {
      return;
    }
  }
  throw new Error(`Sign-in through "${provider.name}" did not settle within 32 redirects; last location: ${loc}`);
}

export interface OrgMembershipView {
  /** Id of the organization user the sign-in provisioned. */
  userId: string;
  roleId: string;
  roleName: string;
  /** True when the role was assigned by an identity provider's role mapper. */
  fromRoleMapper: boolean;
}

/**
 * Resolve the organization membership of the account created by signing in as `username`.
 *
 * The mapped role is not held on the user: `AuthenticationServiceImpl` records it as an
 * organization membership, so that is what has to be read back.
 */
export async function getOrgMembershipForUsername(accessToken: string, username: string): Promise<OrgMembershipView> {
  const page = await searchOrganisationUsers(accessToken, `userName eq "${username}"`);
  if (!page.totalCount || page.data.length === 0) {
    throw new Error(`No organization user named "${username}" — the Console sign-in did not provision an account`);
  }
  const userId = page.data[0].id!;

  const res = await request(MANAGEMENT_URL)
    .get(`/management/organizations/${ORG_ID}/members`)
    .set('Authorization', `Bearer ${accessToken}`)
    .expect(200);

  const membership = (res.body.memberships ?? []).find((m: any) => m.memberId === userId);
  if (!membership) {
    throw new Error(`Organization user "${username}" has no organization membership`);
  }

  return {
    userId,
    roleId: membership.roleId,
    roleName: res.body.metadata?.roles?.[membership.roleId]?.name ?? '<unknown role>',
    fromRoleMapper: Boolean(membership.fromRoleMapper),
  };
}

/** Wait until every named provider has a sign-in link on the Console login page. */
async function waitForProvidersOnLoginPage(names: string[], timeoutMillis = 30000): Promise<void> {
  await retryUntil(
    async () => {
      try {
        const { loginFormRes } = await getLoginForm(MANAGEMENT_URL, REDIRECT_URI);
        return loginFormRes.text;
      } catch {
        return '';
      }
    },
    (html) => {
      const $ = cheerio.load(html);
      const testIds = $('[data-testid^="social-provider-"]')
        .map((_, el) => `${$(el).attr('data-testid')} ${$(el).text()}`)
        .get()
        .join(' ');
      return names.every((n) => testIds.includes(n));
    },
    { timeoutMillis, intervalMillis: 500 },
  );
}

/**
 * Build a security domain that acts as an external identity provider: an inline provider
 * holding the given users, and an application the organization provider authenticates against.
 *
 * `profile` is requested alongside `openid` so that userInfo carries `preferred_username`,
 * which is the claim the role mapper rules match on.
 */
async function createIdpDomain(
  accessToken: string,
  label: string,
  usernames: string[],
): Promise<{ domain: Domain; clientId: string; clientSecret: string }> {
  const domain = await createDomain(accessToken, uniqueName(label, true), 'AM-2219 organization role mapper');
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

  const inlineIdp = await createIdp(domain.id, accessToken, {
    external: false,
    type: 'inline-am-idp',
    domainWhitelist: [],
    configuration: JSON.stringify({
      users: usernames.map((username) => ({
        firstname: username,
        lastname: 'mapper-test',
        username,
        password: IDP_USER_PASSWORD,
      })),
    }),
    name: 'inmemory',
  });

  const appName = uniqueName(`${label}-client`, true);
  const clientSecret = uniqueName('client-secret', true);
  const app = await createApplication(domain.id, accessToken, {
    name: appName,
    type: 'WEB' as any,
    clientId: appName,
    clientSecret,
    redirectUris: [REDIRECT_URI],
  });

  await updateApplication(
    domain.id,
    accessToken,
    {
      identityProviders: new Set([{ identity: inlineIdp.id!, priority: -1 }]),
      settings: {
        oauth: {
          redirectUris: [REDIRECT_URI, `${MANAGEMENT_URL}/management/auth/login/callback`],
          grantTypes: ['authorization_code', 'client_credentials', 'password', 'refresh_token'],
          tokenEndpointAuthMethod: 'client_secret_post',
          scopeSettings: [
            { scope: 'openid', defaultScope: true },
            { scope: 'profile', defaultScope: true },
          ],
        },
        advanced: { skipConsent: true },
      },
    } as any,
    app.id!,
  );

  await startDomain(domain.id, accessToken);
  const started = await waitForDomainStart(domain);
  await waitForDomainSync(started.domain.id);

  return {
    domain: started.domain,
    clientId: app.settings?.oauth?.clientId ?? appName,
    clientSecret,
  };
}

/**
 * Register an organization-level identity provider backed by `domain`, then attach a role mapper.
 *
 * Two calls are required: `NewIdentityProvider` carries no `roleMapper` field and the API rejects
 * one outright ("Property [roleMapper] is not recognized as a valid property"), so the mapper has
 * to be applied by a follow-up update. That update also requires `type`, which is otherwise easy
 * to omit since it is unchanged.
 */
async function createOrgProvider(
  accessToken: string,
  name: string,
  domainHrid: string,
  clientId: string,
  clientSecret: string,
  internalGatewayUrl: string,
  roleMapper: Record<string, string[]>,
): Promise<string> {
  const configuration = JSON.stringify({
    clientId,
    clientAuthenticationMethod: 'client_secret_post',
    clientSecret,
    wellKnownUri: `${internalGatewayUrl}/${domainHrid}/oidc/.well-known/openid-configuration`,
    responseType: 'code',
    responseMode: 'default',
    encodeRedirectUri: false,
    useIdTokenForUserInfo: false,
    signature: 'RSA_RS256',
    publicKeyResolver: 'GIVEN_KEY',
    connectTimeout: 10000,
    maxPoolSize: 200,
    scopes: ['openid', 'profile'],
  });

  const createRes = await request(MANAGEMENT_URL)
    .post(`/management/organizations/${ORG_ID}/identities`)
    .set('Authorization', `Bearer ${accessToken}`)
    .set('Content-Type', 'application/json')
    .send({ external: true, type: 'oauth2-generic-am-idp', domainWhitelist: [], configuration, name })
    .expect(201);

  const idpId = createRes.body.id;
  if (!idpId) {
    throw new Error(`Create organization identity provider "${name}" did not return an id`);
  }

  await request(MANAGEMENT_URL)
    .put(`/management/organizations/${ORG_ID}/identities/${idpId}`)
    .set('Authorization', `Bearer ${accessToken}`)
    .set('Content-Type', 'application/json')
    .send({ name, type: 'oauth2-generic-am-idp', configuration, domainWhitelist: [], roleMapper })
    .expect(200);

  return idpId;
}

export const setupOrgIdpRoleMapperFixture = async (): Promise<OrgIdpRoleMapperFixture> => {
  const accessToken = await requestAdminAccessToken();

  // Everything created below is recorded as it happens. The organization is shared across the
  // whole installation, so a setup that throws part-way — waitForProvidersOnLoginPage timing out,
  // for instance — must not leave providers or roles registered against it for the rest of the run.
  const createdOrgIdpIds: string[] = [];
  const createdRoleIds: string[] = [];
  const createdDomainIds: string[] = [];
  let identitiesWerePatched = false;

  const undoPartialSetup = async () => {
    if (identitiesWerePatched) {
      try {
        const current = await getDefaultApi(accessToken).getOrganizationSettings({ organizationId: ORG_ID });
        const remaining = (current.identities ? Array.from(current.identities) : []).filter((id) => !createdOrgIdpIds.includes(id));
        await getDefaultApi(accessToken).patchOrganizationSettings({
          organizationId: ORG_ID,
          patchOrganization: { identities: remaining },
        });
      } catch (e) {
        console.error('Partial cleanup: could not restore organization settings:', e);
      }
    }
    for (const idpId of createdOrgIdpIds) {
      try {
        await getIdpApi(accessToken).deleteIdentityProvider1({ organizationId: ORG_ID, identity: idpId });
      } catch {
        /* best effort */
      }
    }
    for (const domainId of createdDomainIds) {
      try {
        await safeDeleteDomain(domainId, accessToken);
      } catch {
        /* best effort */
      }
    }
    for (const roleId of createdRoleIds) {
      try {
        await deleteOrganizationRole(accessToken, roleId);
      } catch {
        /* best effort */
      }
    }
  };

  try {
    const gatewayUrl = process.env.AM_GATEWAY_URL!;
    const internalGatewayUrl = process.env.AM_INTERNAL_GATEWAY_URL || process.env.AM_GATEWAY_URL!;
    const defaultApi = getDefaultApi(accessToken);

    const settings = await defaultApi.getOrganizationSettings({ organizationId: ORG_ID });
    const existingIdentities = settings.identities ? Array.from(settings.identities) : [];
    if (existingIdentities.length === 0) {
      throw new Error('Organization has no identity provider');
    }

    // ORGANIZATION_USER is what AuthenticationServiceImpl falls back to when no rule matches.
    const defaultRole = await findOrganizationRoleByName(accessToken, 'ORGANIZATION_USER');

    const roleAName = uniqueName('am2219-role-a', true);
    const roleBName = uniqueName('am2219-role-b', true);
    const roleA = await createCustomOrganizationRole(accessToken, roleAName, ASSIGNABLE_TYPE_ORGANIZATION, ['domain_read']);
    createdRoleIds.push(roleA.id!);
    const roleB = await createCustomOrganizationRole(accessToken, roleBName, ASSIGNABLE_TYPE_ORGANIZATION, ['domain_read']);
    createdRoleIds.push(roleB.id!);

    const matchingA = uniqueName('am2219-match-a', true);
    const nonMatchingA = uniqueName('am2219-nomatch-a', true);
    const matchingB = uniqueName('am2219-match-b', true);

    const domainA = await createIdpDomain(accessToken, 'am2219-idp-a', [matchingA, nonMatchingA]);
    createdDomainIds.push(domainA.domain.id!);
    const domainB = await createIdpDomain(accessToken, 'am2219-idp-b', [matchingB]);
    createdDomainIds.push(domainB.domain.id!);

    const providerAName = uniqueName('am2219-provider-a', true);
    const providerBName = uniqueName('am2219-provider-b', true);

    // Only the matching username is named in each rule, so the other user reaches the default role.
    const providerAId = await createOrgProvider(
      accessToken,
      providerAName,
      domainA.domain.hrid!,
      domainA.clientId,
      domainA.clientSecret,
      internalGatewayUrl,
      { [roleA.id!]: [`preferred_username=${matchingA}`] },
    );
    createdOrgIdpIds.push(providerAId);
    const providerBId = await createOrgProvider(
      accessToken,
      providerBName,
      domainB.domain.hrid!,
      domainB.clientId,
      domainB.clientSecret,
      internalGatewayUrl,
      { [roleB.id!]: [`preferred_username=${matchingB}`] },
    );
    createdOrgIdpIds.push(providerBId);

    await defaultApi.patchOrganizationSettings({
      organizationId: ORG_ID,
      patchOrganization: { identities: [...existingIdentities, providerAId, providerBId] },
    });
    identitiesWerePatched = true;

    await waitForProvidersOnLoginPage([providerAName, providerBName]);

    const cleanUp = async () => {
      // Signing in provisions an organization user per account, so remove them rather than
      // leaving the shared organization to accumulate one set per run.
      for (const username of [matchingA, nonMatchingA, matchingB]) {
        try {
          const page = await searchOrganisationUsers(accessToken, `userName eq "${username}"`);
          for (const user of page.data ?? []) {
            await deleteOrganisationUser(accessToken, user.id);
          }
        } catch {
          // best effort — a user that was never provisioned is not an error
        }
      }

      // Re-read rather than restoring the snapshot taken during setup: other spec files patch the
      // same organization settings, and writing back a stale list would drop their providers.
      // Only the two ids this fixture added are removed.
      const current = await defaultApi.getOrganizationSettings({ organizationId: ORG_ID });
      const remaining = (current.identities ? Array.from(current.identities) : []).filter((id) => id !== providerAId && id !== providerBId);
      await defaultApi.patchOrganizationSettings({
        organizationId: ORG_ID,
        patchOrganization: { identities: remaining },
      });

      for (const idpId of [providerAId, providerBId]) {
        try {
          await getIdpApi(accessToken).deleteIdentityProvider1({ organizationId: ORG_ID, identity: idpId });
        } catch (e: any) {
          if (e?.response?.status !== 404) {
            throw e;
          }
        }
      }

      await safeDeleteDomain(domainA.domain.id!, accessToken);
      await safeDeleteDomain(domainB.domain.id!, accessToken);

      for (const roleId of [roleA.id!, roleB.id!]) {
        try {
          await deleteOrganizationRole(accessToken, roleId);
        } catch (e: any) {
          if (e?.response?.status !== 404) {
            throw e;
          }
        }
      }
    };

    return {
      accessToken,
      gatewayUrl,
      internalGatewayUrl,
      defaultRoleId: defaultRole.id!,
      defaultRoleName: defaultRole.name!,
      providerA: {
        id: providerAId,
        name: providerAName,
        mappedRoleId: roleA.id!,
        mappedRoleName: roleAName,
        matchingUsername: matchingA,
        nonMatchingUsername: nonMatchingA,
        clientId: domainA.clientId,
      },
      providerB: {
        id: providerBId,
        name: providerBName,
        mappedRoleId: roleB.id!,
        mappedRoleName: roleBName,
        matchingUsername: matchingB,
        nonMatchingUsername: '',
        clientId: domainB.clientId,
      },
      cleanUp,
    };
  } catch (error) {
    await undoPartialSetup();
    throw error;
  }
};
