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
import { createDomain, deleteDomain } from '@management-commands/domain-management-commands';
import { createIdp } from '@management-commands/idp-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { JWT_FORMAT } from '@specs-utils/jwt-format';
import { Fixture } from '../../../test-fixture';
import { AutomationClient } from './automation-client';

/**
 * A domain and identity provider created through the legacy management API — i.e. NOT through the
 * Automation API — so both land with {@code managedBy = NONE} and a random internal UUID and no
 * automation key. They are the "brownfield" resources the {@code id:<uuid>} addressing convention
 * exists to reach.
 */
export interface BrownfieldFixture extends Fixture {
  accessToken: string;
  client: AutomationClient;
  /** Internal UUID of the brownfield domain (addressable as {@code id:<domainId>}). */
  domainId: string;
  /** Internal UUID of the brownfield identity provider (addressable as {@code id:<idpId>}). */
  idpId: string;
}

const inlineIdpBody = (name: string) => ({
  name,
  type: 'inline-am-idp',
  configuration: JSON.stringify({
    users: [{ firstname: 'Test', lastname: 'User', username: 'brownfield-user', password: 'Password1!' }],
  }),
});

export const setupBrownfieldFixture = async (): Promise<BrownfieldFixture> => {
  const accessToken = await requestAdminAccessToken();
  expect(accessToken).toMatch(JWT_FORMAT);
  const client = new AutomationClient(accessToken);

  const domain = await createDomain(accessToken, uniqueName('brownfield', true).toLowerCase(), 'Brownfield domain');
  const idp = await createIdp(domain.id, accessToken, inlineIdpBody('Brownfield LDAP'));

  return {
    accessToken,
    client,
    domainId: domain.id,
    idpId: idp.id,
    cleanUp: async () => {
      // deleting the domain cascades to its identity providers
      await deleteDomain(domain.id, accessToken);
    },
  };
};
