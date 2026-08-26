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

import { Domain } from '@management-models/Domain';
import {
  createDomain,
  DomainOidcConfig,
  patchDomain,
  safeDeleteDomain,
  startDomain,
  waitForDomainStart,
} from '@management-commands/domain-management-commands';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { createIdp, deleteIdp } from '@management-commands/idp-management-commands';
import { createApplication, updateApplication } from '@management-commands/application-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Fixture } from '../../../test-fixture';

export interface AuthMethodApp {
  clientId: string;
  clientSecret: string;
}

export interface ArbitraryAuthMethodFixture extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  /** No token endpoint authentication method set — the arbitrary case. */
  unsetApp: AuthMethodApp;
  /** Fixed to client_secret_basic, as the contrast. */
  fixedApp: AuthMethodApp;
  /** The method actually stored on the unset application, read back after creation. */
  unsetAppStoredMethod: string | undefined;
}

/**
 * Both applications live in one domain so the comparison is like for like: same gateway, same
 * identity provider, differing only in whether a token endpoint authentication method is set.
 */
export const setupArbitraryAuthMethodFixture = async (): Promise<ArbitraryAuthMethodFixture> => {
  const accessToken = await requestAdminAccessToken();
  let domain: Domain | null = null;

  try {
    domain = await createDomain(accessToken, uniqueName('arbitrary-auth', true), 'AM-2232 arbitrary client authentication method');

    await patchDomain(domain.id, accessToken, {
      oidc: {
        clientRegistrationSettings: { allowLocalhostRedirectUri: true, allowHttpSchemeRedirectUri: true },
      },
    });

    await deleteIdp(domain.id, accessToken, 'default-idp-' + domain.id);
    const idp = await createIdp(domain.id, accessToken, {
      external: false,
      type: 'inline-am-idp',
      domainWhitelist: [],
      configuration: JSON.stringify({
        users: [{ firstname: 'Token', lastname: 'User', username: uniqueName('arb-user', true), password: '#CoMpL3X-P@SsW0Rd' }],
      }),
      name: 'arbitrary-auth-idp',
    });

    const buildApp = async (label: string, tokenEndpointAuthMethod?: string) => {
      const created = await createApplication(domain!.id, accessToken, {
        name: uniqueName(label, true),
        type: 'WEB',
        redirectUris: ['http://localhost:4000/'],
      });

      const oauth: Record<string, any> = {
        redirectUris: ['http://localhost:4000/'],
        grantTypes: ['client_credentials'],
      };
      // Sent as an empty string for the arbitrary case. Omitting the field entirely is not the
      // same thing: the management API then stores client_secret_basic, and the application ends
      // up fixed to one method rather than accepting any.
      oauth.tokenEndpointAuthMethod = tokenEndpointAuthMethod ?? '';

      const updated = await updateApplication(
        domain!.id,
        accessToken,
        { settings: { oauth }, identityProviders: [{ identity: idp.id, priority: 0 }] },
        created.id,
      );

      return {
        clientId: updated.settings.oauth.clientId,
        clientSecret: created.settings.oauth.clientSecret,
        storedMethod: updated.settings.oauth.tokenEndpointAuthMethod,
      };
    };

    const unset = await buildApp('arb-unset-app');
    const fixed = await buildApp('arb-fixed-app', 'client_secret_basic');

    const startedDomain = await startDomain(domain.id, accessToken);
    const started = await waitForDomainStart(startedDomain);

    return {
      accessToken,
      domain,
      oidc: started.oidcConfig,
      unsetApp: { clientId: unset.clientId, clientSecret: unset.clientSecret },
      fixedApp: { clientId: fixed.clientId, clientSecret: fixed.clientSecret },
      unsetAppStoredMethod: unset.storedMethod,
      cleanUp: async () => {
        if (domain?.id) {
          await safeDeleteDomain(domain.id, accessToken);
        }
      },
    };
  } catch (error) {
    if (domain?.id) {
      try {
        await safeDeleteDomain(domain.id, accessToken);
      } catch (e) {
        console.error('Cleanup failed:', e);
      }
    }
    throw error;
  }
};
