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
import { performGet, performFormPost } from '@gateway-commands/oauth-oidc-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { getDefaultApi, getIdpApi } from '@management-commands/service/utils';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';
import { waitFor } from '@management-commands/domain-management-commands';
import { getLoginForm } from '../api-management-utils';
import {
  cookieHeaderFromSetCookie,
  extractCsrfFromManagementLoginHtml,
} from '../management-auth-helper';

export { getLoginForm } from '../api-management-utils';

export interface ApiManagementLoginFixture extends Fixture {
  accessToken: string;
  currentIdp: string;
  newIdp: string;
  username: string;
}

const ORG_ID = process.env.AM_DEF_ORG_ID!;

export const setupApiManagementLoginFixture = async (): Promise<ApiManagementLoginFixture> => {
  const accessToken = await requestAdminAccessToken();
  const defaultApi = getDefaultApi(accessToken);
  const idpApi = getIdpApi(accessToken);

  const settings = await defaultApi.getOrganizationSettings({ organizationId: ORG_ID });
  const identities = settings.identities ? Array.from(settings.identities) : [];
  const currentIdp = identities[0];
  if (!currentIdp) {
    throw new Error('Organization has no identity provider; cannot run login fixture');
  }

  const username = uniqueName('inline-user', true);
  const newIdpBody = {
    external: false,
    type: 'inline-am-idp',
    domainWhitelist: [] as string[],
    configuration: JSON.stringify({
      users: [
        {
          firstname: 'test',
          lastname: 'test',
          username,
          email: 'test@test.com',
          password: 'test',
        },
      ],
    }),
    name: 'Inline Users 2',
  };

  const createRes = await request(process.env.AM_MANAGEMENT_URL)
    .post(`/management/organizations/${ORG_ID}/identities`)
    .set('Authorization', `Bearer ${accessToken}`)
    .set('Content-Type', 'application/json')
    .send(newIdpBody)
    .expect(201);
  const newIdp = createRes.body.id;
  if (!newIdp) {
    throw new Error('Create identity provider did not return id');
  }

  await defaultApi.patchOrganizationSettings({
    organizationId: ORG_ID,
    patchOrganization: { identities: [currentIdp, newIdp] },
  });
  await waitFor(5000);

  const cleanUp = async () => {
    await defaultApi.patchOrganizationSettings({
      organizationId: ORG_ID,
      patchOrganization: { identities: [currentIdp] },
    });
    try {
      await idpApi.deleteIdentityProvider1({ organizationId: ORG_ID, identity: newIdp });
    } catch (e: any) {
      if (e?.response?.status !== 404) throw e;
    }
  };

  return {
    accessToken,
    currentIdp,
    newIdp,
    username,
    cleanUp,
  };
};

/**
 * Run simple (non-social) login flow: authorize -> get login form -> POST credentials.
 * Returns post response and session cookie for follow-up requests.
 */
export async function runSimpleLoginFlow(
  managementUrl: string,
  redirectUri: string,
  username: string,
  password: string,
): Promise<{ postRes: Awaited<ReturnType<typeof performFormPost>>; cookie: string }> {
  const { loginFormRes, cookie } = await getLoginForm(managementUrl, redirectUri);
  const csrf = extractCsrfFromManagementLoginHtml(loginFormRes.text);
  const loginCookie = cookieHeaderFromSetCookie(loginFormRes.headers['set-cookie']);
  const postRes = await performFormPost(
    managementUrl,
    '/management/auth/login',
    { _csrf: csrf, username, password },
    {
      'Content-Type': 'application/x-www-form-urlencoded',
      Cookie: loginCookie ?? cookie,
    },
  );
  const postCookie = cookieHeaderFromSetCookie(postRes.headers['set-cookie']);
  return { postRes, cookie: postCookie ?? loginCookie ?? cookie };
}
