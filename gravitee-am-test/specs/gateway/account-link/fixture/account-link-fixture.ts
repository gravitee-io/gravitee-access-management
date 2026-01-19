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
import { DomainOidcConfig } from '@management-commands/domain-management-commands';
import { Application } from '@management-models/Application';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setupDownstreamDomain } from './downstream-domain-fixture';
import { setupUpstreamDomain } from './upstream-domain-fixture';
import { IdentityProvider } from '@management-models/IdentityProvider';
import { Fixture } from '../../../test-fixture';

export interface AccountLinkFixture extends Fixture {
  downstreamDomain: DownstreamDomain;
  upstreamDomain: UpstreamDomain;
}

export interface DownstreamDomain extends Fixture {
  oidcDomain: Domain;
  oidc: DomainOidcConfig;
  oidcApplication: Application;
  oidcClientId: string;
  oidcClientSecret: string;
  user: UserData;
}

export interface UpstreamDomain extends Fixture {
  domain: Domain;
  oidc: DomainOidcConfig;
  user: UserData;
  oidcIdp: IdentityProvider;
  localIdp: IdentityProvider;
  oidcApp: Application;
  localIdpApp: Application;
}

export interface UserData {
  username: string;
  password: string;
  email: string;
}

export const USER_EMAIL = 'test@test.fr';

export const setupFixture = async (): Promise<AccountLinkFixture> => {
  const accessToken = await requestAdminAccessToken();

  const upstreamDomainName = uniqueName('account-link-upstream', true).toLowerCase();
  const downstreamDomainName = uniqueName('account-link-downstream', true).toLowerCase();

  const userDataDownstream = userData('downstream-user', USER_EMAIL);
  const userDataUpstream = userData('upstream-user', USER_EMAIL);

  const downstreamDomain = await setupDownstreamDomain(
    accessToken,
    downstreamDomainName,
    userDataDownstream,
    `http://localhost:8092/${upstreamDomainName}/login/callback`,
  );
  const upstreamDomain = await setupUpstreamDomain(accessToken, upstreamDomainName, downstreamDomain, userDataUpstream);

  return {
    accessToken: accessToken,
    downstreamDomain: downstreamDomain,
    upstreamDomain: upstreamDomain,
    cleanUp: async () => {
      if (upstreamDomain) {
        await upstreamDomain.cleanUp();
      }

      if (downstreamDomain) {
        await downstreamDomain.cleanUp();
      }
    },
  };
};

const userData = (username: string, email: string) => {
  return {
    username: username,
    password: 'Test1234567!',
    firstName: username,
    lastName: username,
    email: email,
  };
};
