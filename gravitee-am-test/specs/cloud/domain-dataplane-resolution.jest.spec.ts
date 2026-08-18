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

import { afterEach, beforeAll, describe, expect, it } from '@jest/globals';
import { getDomainApi } from '@management-commands/service/utils';
import { safeDeleteCloudDomains } from '@cloud-commands/domain-commands';
import { uniqueName } from '@utils-commands/misc';
import { jira } from '@specs-utils/jira';
import { setup } from '../test-fixture';
import { CloudSharedFixture, setupCloudSharedFixture } from './fixtures/cloud-shared-fixture';

setup(120000);

/**
 * Cloud resolves a new domain's data plane from the ones linked to its environment, rather than from
 * gravitee.yml. The shared fixture links exactly one, which is the shape the resolution is built for.
 *
 * Managed-cloud stack only (local-stack.sh --cloud).
 */
describe('AM - Cloud - data plane resolution on domain creation', () => {
  let shared: CloudSharedFixture;
  const createdDomainIds: Array<string | null | undefined> = [];

  beforeAll(async () => {
    shared = await setupCloudSharedFixture();
  });

  afterEach(async () => {
    await safeDeleteCloudDomains(shared, createdDomainIds.splice(0));
  });

  it(jira`should resolve the environment's data plane when dataPlaneId is omitted ${'AM-7262'}`, async () => {
    const domain = await getDomainApi(shared.accessToken).createDomain({
      organizationId: shared.organizationId,
      environmentId: shared.environmentId,
      newDomain: { name: uniqueName('cloud-dp-omitted', true), description: 'AM-7262' } as any,
    });
    createdDomainIds.push(domain.id);

    expect(domain.dataPlaneId).toEqual(shared.dataPlaneId);
  });

  it(jira`should accept the environment's data plane when supplied explicitly ${'AM-7262'}`, async () => {
    const domain = await getDomainApi(shared.accessToken).createDomain({
      organizationId: shared.organizationId,
      environmentId: shared.environmentId,
      newDomain: { name: uniqueName('cloud-dp-supplied', true), description: 'AM-7262', dataPlaneId: shared.dataPlaneId },
    });
    createdDomainIds.push(domain.id);

    expect(domain.dataPlaneId).toEqual(shared.dataPlaneId);
  });

  it(jira`should reject a data plane that is not linked to the environment ${'AM-7262'}`, async () => {
    const created = getDomainApi(shared.accessToken).createDomain({
      organizationId: shared.organizationId,
      environmentId: shared.environmentId,
      newDomain: { name: uniqueName('cloud-dp-unlinked', true), description: 'AM-7262', dataPlaneId: 'not-linked-dp' },
    });

    await expect(created).rejects.toMatchObject({ response: { status: 400 } });
  });
});
