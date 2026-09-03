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
import { afterAll, beforeAll, describe, expect, it } from '@jest/globals';
import { createIdp, getIdp, updateIdp } from '@management-commands/idp-management-commands';
import { setup } from '../../test-fixture';
import {
  buildMongoIdpBody,
  buildMongoIdpUpdateBody,
  isMongoStack,
  OWN_DATABASE,
  OWN_USERS_COLLECTION,
  setupSystemClusterFixture,
  SystemClusterFixture,
} from './fixtures/system-cluster-fixture';

setup();

let fixture: SystemClusterFixture;

beforeAll(async () => {
  fixture = await setupSystemClusterFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

const itMongoOnly = isMongoStack ? it : it.skip;

const newMongoIdp = (useSystemCluster: boolean) =>
  createIdp(fixture.domain.id, fixture.accessToken, buildMongoIdpBody({ useSystemCluster }));

const update = (idp, configurationOverrides: object) =>
  updateIdp(fixture.domain.id, fixture.accessToken, buildMongoIdpUpdateBody(idp, configurationOverrides), idp.id);

describe('Identity provider reusing the system cluster outside the storage rule', () => {
  itMongoOnly('should leave the storage of a new provider to the administrator', async () => {
    const idp = await newMongoIdp(true);

    const configuration = JSON.parse(idp.configuration);
    expect(configuration.database).toEqual(OWN_DATABASE);
    expect(configuration.usersCollection).toEqual(OWN_USERS_COLLECTION);
    expect(idp.systemClusterRestricted).toBeFalsy();
  });

  itMongoOnly('should accept an update that turns the system cluster off', async () => {
    const idp = await newMongoIdp(true);

    const updated = await update(idp, { useSystemCluster: false });

    expect(JSON.parse(updated.configuration).useSystemCluster).toEqual(false);
  });

  itMongoOnly('should accept an update that turns the system cluster on', async () => {
    const idp = await newMongoIdp(false);

    const updated = await update(idp, { useSystemCluster: true });

    expect(JSON.parse(updated.configuration).useSystemCluster).toEqual(true);
    expect(updated.systemClusterRestricted).toBeFalsy();
  });

  itMongoOnly('should accept the system cluster turned on and off again', async () => {
    const idp = await newMongoIdp(false);
    const joined = await update(idp, { useSystemCluster: true });

    const left = await update(joined, { useSystemCluster: false });

    expect(JSON.parse(left.configuration).useSystemCluster).toEqual(false);
  });

  itMongoOnly('should leave the database and the collection editable', async () => {
    const idp = await newMongoIdp(true);

    const updated = await update(idp, { database: 'another-database', usersCollection: 'another-collection' });

    const configuration = JSON.parse(updated.configuration);
    expect(configuration.database).toEqual('another-database');
    expect(configuration.usersCollection).toEqual('another-collection');
  });
});
