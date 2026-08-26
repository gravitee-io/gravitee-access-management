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
import { setup } from '../test-fixture';
import { jira } from '@specs-utils/jira';
import { uniqueName } from '@utils-commands/misc';
import {
  SystemClusterIdpFixture,
  setupSystemClusterIdpFixture,
  buildMongoIdpBody,
  buildMongoIdpUpdateBody,
  buildMongoAutomationIdpDef,
  createCloudIdp,
  getCloudIdp,
  updateCloudIdp,
  putAutomationIdp,
  platformDatabase,
  defaultIdpType,
  isMongoStack,
  readStorageRules,
  createOrganizationIdp,
  updateOrganizationIdp,
  deleteOrganizationIdp,
  createSecondCloudDomain,
  deleteCloudIdp,
  listCloudIdps,
  setupGatewayLoginFixture,
  OWN_DATABASE,
  OWN_USERS_COLLECTION,
} from './fixtures/system-cluster-idp-fixture';

setup(120000);

/**
 * A managed cloud installation owns the storage location of a mongo identity provider that reuses the
 * system cluster: the database comes from the repository settings and the collection is derived from
 * the provider id. Both are fixed once the provider is created.
 *
 * Managed-cloud stack only (local-stack.sh --cloud). A standalone installation leaves the provider
 * exactly as the administrator configured it, which is why this lives in the cloud suite.
 */
let scope: SystemClusterIdpFixture;

beforeAll(async () => {
  scope = await setupSystemClusterIdpFixture();
});

afterAll(async () => {
  if (scope) {
    await scope.cleanup();
  }
});

const itMongoOnly = isMongoStack ? it : it.skip;

const newMongoIdp = (useSystemCluster: boolean) => createCloudIdp(scope, buildMongoIdpBody({ useSystemCluster }));

describe('Identity provider reusing the system cluster', () => {
  it(jira`should report both storage rules to the console ${'AM-7584'}`, async () => {
    // The console shapes the mongo form from these two rules, one per pinned field.
    const rules = await readStorageRules(scope);

    expect(rules).toEqual({ pinDatabase: true, prefixUsersCollection: true });
  });

  it(jira`should pin the storage of a provider created with the system cluster ${'AM-7584'}`, async () => {
    const idp = await newMongoIdp(true);

    const configuration = JSON.parse(idp.configuration);
    expect(configuration.database).toEqual(platformDatabase());
    expect(configuration.usersCollection).toEqual(`idp_${idp.id}`);
    expect(idp.systemClusterRestricted).toEqual(true);

    // The pinned values must be what was persisted, not just what create echoed back.
    const fetched = await getCloudIdp(scope, idp.id);
    expect(JSON.parse(fetched.configuration)).toMatchObject({ usersCollection: `idp_${idp.id}` });
    expect(fetched.systemClusterRestricted).toEqual(true);
  });

  it(jira`should reject a change to the users collection of a pinned provider ${'AM-7585'}`, async () => {
    const idp = await newMongoIdp(true);

    await expect(updateCloudIdp(scope, idp.id, buildMongoIdpUpdateBody(idp, { usersCollection: 'somewhere-else' }))).rejects.toMatchObject({
      response: { status: 400 },
    });

    const unchanged = await getCloudIdp(scope, idp.id);
    expect(JSON.parse(unchanged.configuration).usersCollection).toEqual(`idp_${idp.id}`);
  });

  it(jira`should reject a change to the database of a pinned provider ${'AM-7585'}`, async () => {
    const idp = await newMongoIdp(true);

    await expect(updateCloudIdp(scope, idp.id, buildMongoIdpUpdateBody(idp, { database: 'some-other-database' }))).rejects.toMatchObject({
      response: { status: 400 },
    });
  });

  it(jira`should accept an update that leaves the storage settings alone ${'AM-7585'}`, async () => {
    const idp = await newMongoIdp(true);

    const updated = await updateCloudIdp(scope, idp.id, buildMongoIdpUpdateBody(idp, { usernameField: 'login' }));

    const configuration = JSON.parse(updated.configuration);
    expect(configuration.usernameField).toEqual('login');
    expect(configuration.usersCollection).toEqual(`idp_${idp.id}`);
    expect(updated.systemClusterRestricted).toEqual(true);
  });

  it(jira`should reject an update that turns the system cluster on ${'AM-7585'}`, async () => {
    const idp = await newMongoIdp(false);
    expect(idp.systemClusterRestricted).toBeFalsy();

    await expect(updateCloudIdp(scope, idp.id, buildMongoIdpUpdateBody(idp, { useSystemCluster: true }))).rejects.toMatchObject({
      response: { status: 400 },
    });

    // The provider keeps the storage it was created with, and stays editable.
    const unchanged = await getCloudIdp(scope, idp.id);
    expect(JSON.parse(unchanged.configuration)).toMatchObject({ useSystemCluster: false, usersCollection: OWN_USERS_COLLECTION });
    expect(unchanged.systemClusterRestricted).toBeFalsy();
  });

  it(jira`should accept the same automation manifest applied twice ${'AM-7586'}`, async () => {
    const manifest = buildMongoAutomationIdpDef(uniqueName('am7264-auto', true).toLowerCase());

    const first = await putAutomationIdp(scope, manifest);
    expect(first.status).toEqual(200);
    const pinned = JSON.parse(first.body.configuration);
    expect(pinned.database).toEqual(platformDatabase());
    expect(pinned.usersCollection).toMatch(/^idp_/);

    // The manifest still names the storage the operator wrote, which the platform overwrote.
    const second = await putAutomationIdp(scope, manifest);
    expect(second.status).toEqual(200);
    expect(JSON.parse(second.body.configuration)).toMatchObject({
      database: pinned.database,
      usersCollection: pinned.usersCollection,
    });
  });

  it(jira`should reject an automation manifest that leaves the system cluster ${'AM-7586'}`, async () => {
    const key = uniqueName('am7264-auto-move', true).toLowerCase();
    const created = await putAutomationIdp(scope, buildMongoAutomationIdpDef(key));
    expect(created.status).toEqual(200);

    const moved = await putAutomationIdp(scope, buildMongoAutomationIdpDef(key, { useSystemCluster: false }));

    expect(moved.status).toEqual(400);
    expect(moved.body.message).toEqual('Identity provider storage settings cannot be changed');
  });

  it(jira`should pin an organization identity provider like a domain one ${'AM-7580'}`, async () => {
    const idp = await createOrganizationIdp(scope, buildMongoIdpBody({ useSystemCluster: true }));

    try {
      const configuration = JSON.parse(idp.configuration);
      expect(configuration.database).toEqual(platformDatabase());
      expect(configuration.usersCollection).toEqual(`idp_${idp.id}`);
      expect(idp.systemClusterRestricted).toEqual(true);
      expect(idp.referenceType).toEqual('organization');

      await expect(
        updateOrganizationIdp(scope, idp.id, buildMongoIdpUpdateBody(idp, { usersCollection: 'somewhere-else' })),
      ).rejects.toMatchObject({ response: { status: 400 } });
    } finally {
      await deleteOrganizationIdp(scope, idp.id);
    }
  });

  it(jira`should give two providers in one domain two collections ${'AM-7583'}`, async () => {
    // Both ask for the same collection, so only the derivation from the id keeps them apart.
    const one = await createCloudIdp(scope, buildMongoIdpBody({ useSystemCluster: true, usersCollection: 'shared-users' }));
    const two = await createCloudIdp(scope, buildMongoIdpBody({ useSystemCluster: true, usersCollection: 'shared-users' }));

    expect(JSON.parse(one.configuration).usersCollection).toEqual(`idp_${one.id}`);
    expect(JSON.parse(two.configuration).usersCollection).toEqual(`idp_${two.id}`);
    expect(one.id).not.toEqual(two.id);
  });

  it(jira`should not share a collection between two domains ${'AM-7583'}`, async () => {
    const first = await createCloudIdp(scope, buildMongoIdpBody({ useSystemCluster: true, usersCollection: 'shared-users' }));
    const secondDomain = await createSecondCloudDomain(scope);

    const second = await createCloudIdp(secondDomain, buildMongoIdpBody({ useSystemCluster: true, usersCollection: 'shared-users' }));

    expect(JSON.parse(second.configuration).usersCollection).toEqual(`idp_${second.id}`);
    expect(JSON.parse(second.configuration).usersCollection).not.toEqual(JSON.parse(first.configuration).usersCollection);
  });

  it(jira`should give a recreated provider a new collection ${'AM-7583'}`, async () => {
    const original = await createCloudIdp(scope, buildMongoIdpBody({ useSystemCluster: true }));
    const originalCollection = JSON.parse(original.configuration).usersCollection;

    await deleteCloudIdp(scope, original.id);
    const recreated = await createCloudIdp(scope, { ...buildMongoIdpBody({ useSystemCluster: true }), name: original.name });

    expect(recreated.id).not.toEqual(original.id);
    expect(JSON.parse(recreated.configuration).usersCollection).not.toEqual(originalCollection);
  });

  it(jira`should give every concurrent create its own collection ${'AM-7583'}`, async () => {
    const created = await Promise.all(
      Array.from({ length: 5 }, () =>
        createCloudIdp(scope, buildMongoIdpBody({ useSystemCluster: true, usersCollection: 'shared-users' })),
      ),
    );

    const collections = created.map((idp) => JSON.parse(idp.configuration).usersCollection);
    expect(new Set(collections).size).toEqual(5);
    created.forEach((idp) => expect(JSON.parse(idp.configuration).usersCollection).toEqual(`idp_${idp.id}`));
  });

  it(jira`should never pin the domain's default identity provider ${'AM-7583'}`, async () => {
    // The list endpoint strips the flag, so the provider has to be read by id. The API withholds a
    // system provider's configuration on both, so the collection itself cannot be asserted here.
    const providers = await listCloudIdps(scope);
    const defaultIdp = providers.find((idp) => idp.system);
    expect(defaultIdp).toBeDefined();
    expect(defaultIdp.type).toEqual(defaultIdpType());

    const fetched = await getCloudIdp(scope, defaultIdp.id);
    expect(fetched.system).toEqual(true);
    expect(fetched.systemClusterRestricted).toBeFalsy();
  });

  // A mongo identity provider needs a mongo container, which only the mongo overlay starts. Every
  // other test here reads the stored configuration, so only this one needs the store to answer.
  itMongoOnly(jira`should authenticate a user held in the pinned collection ${'AM-7579'}`, async () => {
    const gateway = await setupGatewayLoginFixture(scope);

    try {
      const pinned = JSON.parse(gateway.idp.configuration);
      expect(pinned.database).toEqual(platformDatabase());
      expect(pinned.usersCollection).toEqual(`idp_${gateway.idp.id}`);

      const response = await gateway.login();

      expect(response.status).toEqual(200);
      const token = JSON.parse(response.text);
      expect(token.token_type).toEqual('bearer');
      expect(token.access_token).toBeTruthy();

      // gis is the identity provider id joined to the user's externalId, so it proves the gateway
      // read the user back out of the pinned collection rather than anywhere else.
      const claims = JSON.parse(Buffer.from(token.access_token.split('.')[1], 'base64').toString());
      expect(claims.gis).toEqual(`${gateway.idp.id}:${gateway.user.externalId}`);
    } finally {
      await gateway.cleanup();
    }
  });

  it(jira`should leave a provider that does not reuse the system cluster editable ${'AM-7584'}`, async () => {
    const idp = await newMongoIdp(false);

    const created = JSON.parse(idp.configuration);
    expect(created.database).toEqual(OWN_DATABASE);
    expect(created.usersCollection).toEqual(OWN_USERS_COLLECTION);
    expect(idp.systemClusterRestricted).toBeFalsy();

    const updated = await updateCloudIdp(
      scope,
      idp.id,
      buildMongoIdpUpdateBody(idp, { database: 'another-database', usersCollection: 'another-users' }),
    );

    expect(JSON.parse(updated.configuration)).toMatchObject({
      database: 'another-database',
      usersCollection: 'another-users',
    });
  });
});
