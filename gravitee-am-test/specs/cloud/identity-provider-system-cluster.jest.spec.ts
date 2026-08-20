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
import { getDomainApi } from '@management-commands/service/utils';
import { safeDeleteCloudDomain } from '@cloud-commands/domain-commands';
import { uniqueName } from '@utils-commands/misc';
import { setup } from '../test-fixture';
import { jira } from '@specs-utils/jira';
import { CloudSharedFixture, setupCloudSharedFixture } from './fixtures/cloud-shared-fixture';
import {
  CloudIdpScope,
  buildMongoIdpBody,
  buildMongoIdpUpdateBody,
  createCloudIdp,
  getCloudIdp,
  updateCloudIdp,
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
let shared: CloudSharedFixture;
let scope: CloudIdpScope;

beforeAll(async () => {
  shared = await setupCloudSharedFixture();
  const domain = await getDomainApi(shared.accessToken).createDomain({
    organizationId: shared.organizationId,
    environmentId: shared.environmentId,
    newDomain: { name: uniqueName('idp-system-cluster', true) },
  });
  scope = {
    accessToken: shared.accessToken,
    organizationId: shared.organizationId,
    environmentId: shared.environmentId,
    domainId: domain.id,
  };
});

afterAll(async () => {
  if (scope?.domainId) {
    await safeDeleteCloudDomain(scope, scope.domainId);
  }
  if (shared) {
    await shared.cleanup();
  }
});

const newMongoIdp = (useSystemCluster: boolean) => createCloudIdp(scope, buildMongoIdpBody({ useSystemCluster }));

describe('Identity provider reusing the system cluster', () => {
  it(jira`should pin the storage of a provider created with the system cluster ${'AM-7264'}`, async () => {
    const idp = await newMongoIdp(true);

    const configuration = JSON.parse(idp.configuration);
    // The platform database name depends on the deployment, so assert the administrator's choice was
    // discarded rather than pinning the spec to one environment's dbname.
    expect(configuration.database).not.toEqual('my-own-database');
    expect(configuration.database).toBeTruthy();
    expect(configuration.usersCollection).toEqual(`idp_${idp.id}`);
    expect(idp.systemClusterRestricted).toEqual(true);

    // The pinned values must be what was persisted, not just what create echoed back.
    const fetched = await getCloudIdp(scope, idp.id);
    expect(JSON.parse(fetched.configuration)).toMatchObject({ usersCollection: `idp_${idp.id}` });
    expect(fetched.systemClusterRestricted).toEqual(true);
  });

  it(jira`should reject a change to the users collection of a pinned provider ${'AM-7264'}`, async () => {
    const idp = await newMongoIdp(true);

    await expect(updateCloudIdp(scope, idp.id, buildMongoIdpUpdateBody(idp, { usersCollection: 'somewhere-else' }))).rejects.toMatchObject({
      response: { status: 400 },
    });

    const unchanged = await getCloudIdp(scope, idp.id);
    expect(JSON.parse(unchanged.configuration).usersCollection).toEqual(`idp_${idp.id}`);
  });

  it(jira`should reject a change to the database of a pinned provider ${'AM-7264'}`, async () => {
    const idp = await newMongoIdp(true);

    await expect(updateCloudIdp(scope, idp.id, buildMongoIdpUpdateBody(idp, { database: 'some-other-database' }))).rejects.toMatchObject({
      response: { status: 400 },
    });
  });

  it(jira`should accept an update that leaves the storage settings alone ${'AM-7264'}`, async () => {
    const idp = await newMongoIdp(true);

    const updated = await updateCloudIdp(scope, idp.id, buildMongoIdpUpdateBody(idp, { usernameField: 'login' }));

    const configuration = JSON.parse(updated.configuration);
    expect(configuration.usernameField).toEqual('login');
    expect(configuration.usersCollection).toEqual(`idp_${idp.id}`);
    expect(updated.systemClusterRestricted).toEqual(true);
  });

  it(jira`should pin a provider when an update turns the system cluster on ${'AM-7264'}`, async () => {
    const idp = await newMongoIdp(false);
    expect(idp.systemClusterRestricted).toBeFalsy();

    const updated = await updateCloudIdp(scope, idp.id, buildMongoIdpUpdateBody(idp, { useSystemCluster: true }));

    const configuration = JSON.parse(updated.configuration);
    expect(configuration.database).not.toEqual('my-own-database');
    expect(configuration.usersCollection).toEqual(`idp_${idp.id}`);
    expect(updated.systemClusterRestricted).toEqual(true);

    // The provider is pinned from here on, so a further move is rejected.
    await expect(
      updateCloudIdp(scope, idp.id, buildMongoIdpUpdateBody(updated, { usersCollection: 'somewhere-else' })),
    ).rejects.toMatchObject({ response: { status: 400 } });
  });

  it(jira`should leave a provider that does not reuse the system cluster editable ${'AM-7264'}`, async () => {
    const idp = await newMongoIdp(false);

    const created = JSON.parse(idp.configuration);
    expect(created.database).toEqual('my-own-database');
    expect(created.usersCollection).toEqual('my-own-users');
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
