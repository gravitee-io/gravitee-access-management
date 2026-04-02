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

import { DomainApi } from '@management-apis/DomainApi';
import { IdentityProviderApi } from '@management-apis/IdentityProviderApi';
import { ApplicationApi } from '@management-apis/ApplicationApi';
import { UserApi } from '@management-apis/UserApi';
import { RoleApi } from '@management-apis/RoleApi';
import { NewApplicationTypeEnum } from '@management-models/NewApplication';
import { NewRoleAssignableTypeEnum } from '@management-models/NewRole';
import { apiConfig } from '../../utils/api';
import { seedName } from '../../utils/seed-id';

const VERSION = '4.11';

const orgEnv = {
  organizationId: process.env.AM_DEF_ORG_ID,
  environmentId: process.env.AM_DEF_ENV_ID,
};

export async function seed(accessToken: string): Promise<void> {
  const cfg = apiConfig(accessToken);

  const domain = await new DomainApi(cfg).createDomain({
    ...orgEnv,
    newDomain: {
      name: seedName('domain', VERSION, 1),
      description: 'AM migration seed domain for 4.11',
      dataPlaneId: process.env.AM_DOMAIN_DATA_PLANE_ID || 'default',
    },
  });

  await new DomainApi(cfg).patchDomain({
    ...orgEnv,
    domain: domain.id,
    patchDomain: { enabled: true },
  });

  await new IdentityProviderApi(cfg).createIdentityProvider({
    ...orgEnv,
    domain: domain.id,
    newIdentityProvider: {
      name: seedName('idp', VERSION, 1),
      type: 'inline-am-idp',
      external: false,
      domainWhitelist: [],
      configuration: JSON.stringify({ users: [] }),
    },
  });

  await new ApplicationApi(cfg).createApplication({
    ...orgEnv,
    domain: domain.id,
    newApplication: {
      name: seedName('app', VERSION, 1),
      type: NewApplicationTypeEnum.Web,
      redirectUris: ['https://am-seed.example.com/callback'],
    },
  });

  await new UserApi(cfg).createUser({
    ...orgEnv,
    domain: domain.id,
    newUser: {
      username: seedName('user', VERSION, 1),
      email: `${seedName('user', VERSION, 1)}@example.com`,
      firstName: 'Seed',
      lastName: 'Migration',
      password: process.env.AM_SEED_USER_PASSWORD,
      enabled: true,
    },
  });

  await new RoleApi(cfg).createRole({
    ...orgEnv,
    domain: domain.id,
    newRole: {
      name: seedName('role', VERSION, 1),
      description: 'AM migration seed role for 4.11',
      assignableType: NewRoleAssignableTypeEnum.Domain,
    },
  });
}
