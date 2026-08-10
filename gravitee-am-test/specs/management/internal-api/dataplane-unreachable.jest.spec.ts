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

import { afterEach, describe, expect, it } from '@jest/globals';
import { deleteDataPlane } from '@management-commands/dataplane-provisioning-commands';
import { deleteDomain } from '@management-commands/domain-management-commands';
import {
  BoundDomain,
  createDomainOnDataPlane,
  deleteProvisionedDataPlanes,
  provisionUnreachableDataPlane,
  releaseBoundDomain,
} from './fixtures/dataplane-provisioning-fixture';
import { setup } from '../../test-fixture';
import { uniqueName } from '@utils-commands/misc';

// every read and write against the broken store waits for its own connection timeout
setup(180000);

describe('Domains bound to an unreachable data plane', () => {
  let bound: BoundDomain | undefined;

  afterEach(async () => {
    // a domain left bound blocks its data plane from being deleted, which would poison every later run
    await releaseBoundDomain(bound);
    bound = undefined;
    await deleteProvisionedDataPlanes();
  });

  it('deletes the domain anyway, so the data plane can be deleted after it', async () => {
    const dataPlaneId = uniqueName('dp-e2e-unreachable', true);
    await provisionUnreachableDataPlane(dataPlaneId);
    bound = await createDomainOnDataPlane(dataPlaneId);

    // a domain pins its data plane, so a domain that cannot be deleted wedges the pair for good
    await deleteDomain(bound.domainId, bound.accessToken);
    bound = undefined;

    expect((await deleteDataPlane(dataPlaneId)).status).toBe(204);
  });
});
