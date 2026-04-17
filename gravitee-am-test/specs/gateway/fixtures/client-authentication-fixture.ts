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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import {
  deleteDomain,
  patchDomain,
  setupDomainForTest,
  waitForDomainSync,
} from '@management-commands/domain-management-commands';
import { createApplication, patchApplication } from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Application } from '@management-models/Application';

export const CLIENT_ID_WITH_PLUS_BASIC = 'my+client+id';
export const CLIENT_ID_WITH_PLUS_POST = 'my+post+client';

export interface ClientAuthenticationFixture {
  domain: Domain;
  accessToken: string;
  openIdConfiguration: any;
  // App configured for client_secret_basic, client_id contains literal '+'
  basicApp: Application;
  basicAppSecret: string;
  // App configured for client_secret_post, client_id contains literal '+'
  postApp: Application;
  postAppSecret: string;
  cleanup: () => Promise<void>;
}

export const setupClientAuthenticationFixture = async (): Promise<ClientAuthenticationFixture> => {
  let domain: Domain | null = null;
  const accessToken = await requestAdminAccessToken();

  try {
    const setupResult = await setupDomainForTest(uniqueName('client-auth', true), {
      accessToken,
      waitForStart: true,
    });
    domain = setupResult.domain;
    const openIdConfiguration = setupResult.oidcConfig;

    // Disable expiring secrets so freshly-issued secrets remain usable for the test run.
    domain = await patchDomain(domain.id, accessToken, {
      secretSettings: { enabled: false, expiryTimeSeconds: 0 },
    });

    // SERVICE apps default to client_secret_basic + client_credentials grant — no follow-up update needed,
    // which keeps the captured raw secret valid.
    const basicApp = await createApplication(domain.id, accessToken, {
      name: 'plus-client-basic',
      type: 'SERVICE',
      clientId: CLIENT_ID_WITH_PLUS_BASIC,
      redirectUris: ['https://callback'],
    });
    const basicAppSecret: string = basicApp.settings.oauth.clientSecret;

    const postApp = await createApplication(domain.id, accessToken, {
      name: 'plus-client-post',
      type: 'SERVICE',
      clientId: CLIENT_ID_WITH_PLUS_POST,
      redirectUris: ['https://callback'],
    });
    const postAppSecret: string = postApp.settings.oauth.clientSecret;

    await patchApplication(
      domain.id,
      accessToken,
      { settings: { oauth: { tokenEndpointAuthMethod: 'client_secret_post' } } },
      postApp.id,
    );

    // Single sync wait covers the domain patch and both app creations.
    await waitForDomainSync();

    const cleanup = async () => {
      if (domain?.id) {
        await deleteDomain(domain.id, accessToken);
      }
    };

    return {
      domain,
      accessToken,
      openIdConfiguration,
      basicApp,
      basicAppSecret,
      postApp,
      postAppSecret,
      cleanup,
    };
  } catch (err) {
    if (domain?.id) {
      try {
        await deleteDomain(domain.id, accessToken);
      } catch (cleanupErr) {
        console.error('Failed to cleanup domain after setup failure:', cleanupErr);
      }
    }
    throw err;
  }
};
