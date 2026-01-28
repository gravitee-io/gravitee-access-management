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
import * as faker from 'faker';
import { afterAll, beforeAll, expect } from '@jest/globals';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { createIdp, deleteIdp, getAllIdps, getIdp, updateIdp } from '@management-commands/idp-management-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../test-fixture';

setup(200000);

let accessToken;
let domain;
let idp;

beforeAll(async () => {
  accessToken = await requestAdminAccessToken();
  domain = await setupDomainForTest(uniqueName('idp', true), { accessToken }).then((it) => it.domain);
});

function buildIdp(i: number) {
  const idpConfig = {
    users: [
      {
        firstname: 'firstname' + i,
        lastname: 'lastname' + i,
        username: 'user' + i,
        email: `firstname${i}.lastname${i}@mail.com`,
        password: '#CoMpL3X-P@SsW0Rd',
      },
    ],
  };
  const newIdp = {
    external: false,
    type: 'inline-am-idp',
    domainWhitelist: ['gmail.com', 'hotmail.com'],
    configuration: `${JSON.stringify(idpConfig)}`,
    name: 'inmemory',
  };
  return { idpConfig, newIdp };
}

describe('when creating identity providers', () => {
  for (let i = 0; i < 10; i++) {
    it('must create new idp: ' + i, async () => {
      const { idpConfig, newIdp } = buildIdp(i);

      const createdIdp = await createIdp(domain.id, accessToken, newIdp);
      expect(createdIdp).toBeDefined();

      expect(createdIdp.id).toBeDefined();
      expect(createdIdp.name).toEqual(newIdp.name);
      expect(createdIdp.type).toEqual(newIdp.type);
      expect(createdIdp.external).toEqual(newIdp.external);
      expect(createdIdp.domainWhitelist).toContain('gmail.com');
      expect(createdIdp.domainWhitelist).toContain('hotmail.com');
      expect(createdIdp.configuration).toBeDefined();

      const configuration = JSON.parse(createdIdp.configuration);
      expect(configuration.users[0].firstname).toEqual(idpConfig.users[0].firstname);
      expect(configuration.users[0].lastname).toEqual(idpConfig.users[0].lastname);
      expect(configuration.users[0].username).toEqual(idpConfig.users[0].username);
      expect(configuration.users[0].email).toEqual(idpConfig.users[0].email);
      expect(configuration.users[0].password).toEqual('********');

      idp = createdIdp;
    });
  }
});

describe('after creating identity providers', () => {
  it('must find Identity provider', async () => {
    const foundIdp = await getIdp(domain.id, accessToken, idp.id);
    expect(foundIdp).toBeDefined();
    expect(idp.id).toEqual(foundIdp.id);
  });

  it('must update Identity Provider', async () => {
    const updatedIdp = await updateIdp(
      domain.id,
      accessToken,
      {
        name: faker.commerce.productName(),
        type: idp.type,
        configuration: idp.configuration,
      },
      idp.id,
    );
    expect(updatedIdp.name === idp.name).toBeFalsy();
    idp = updatedIdp;
  });

  it('must find all Identity providers', async () => {
    const idpSet = await getAllIdps(domain.id, accessToken);

    expect(idpSet.length).toEqual(11);
  });

  it('Must delete Identity Provider', async () => {
    await deleteIdp(domain.id, accessToken, idp.id);
    const idpSet = await getAllIdps(domain.id, accessToken);

    expect(idpSet.length).toEqual(10);
  });
});

afterAll(async () => {
  if (domain && domain.id) {
    await safeDeleteDomain(domain.id, accessToken);
  }
});
