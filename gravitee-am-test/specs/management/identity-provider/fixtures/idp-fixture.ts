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
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { setupDomainForTest, safeDeleteDomain } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { Domain } from '@management-models/Domain';
import { Fixture } from '../../../test-fixture';

export interface IdpFixture extends Fixture {
  domain: Domain;
  accessToken: string;
}

export const setupIdpFixture = async (): Promise<IdpFixture> => {
  const accessToken = await requestAdminAccessToken();
  const { domain } = await setupDomainForTest(uniqueName('idp', true), { accessToken, waitForStart: false });

  return {
    accessToken,
    domain,
    cleanUp: async () => {
      if (domain?.id && accessToken) {
        await safeDeleteDomain(domain.id, accessToken);
      }
    },
  };
};

export const buildInlineIdpBody = (users: object[]) => ({
  external: false,
  type: 'inline-am-idp',
  domainWhitelist: [],
  configuration: JSON.stringify({ users }),
  name: uniqueName('inline-idp', true),
});
