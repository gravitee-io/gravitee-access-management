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
import {
  attemptBindingADomainTo,
  deleteProvisionedDataPlanes,
  provisionConnectableDataPlane,
  provisionUnreachableDataPlane,
  provisionWrongCredentialsDataPlane,
} from './fixtures/dataplane-provisioning-fixture';
import { setup } from '../../test-fixture';
import { uniqueName } from '@utils-commands/misc';

// each rejected bind waits out the verification timeout before it answers
setup(60000);

const UNVERIFIED_DATA_PLANE = 'did not answer with the settings it was provisioned with';

describe('Domain creation against a data plane that cannot be used', () => {
  afterEach(deleteProvisionedDataPlanes);

  it('rejects a data plane whose credentials the store refuses', async () => {
    const dataPlaneId = uniqueName('dp-e2e-badcreds', true);
    await provisionWrongCredentialsDataPlane(dataPlaneId);

    const attempt = await attemptBindingADomainTo(dataPlaneId);

    expect(attempt).toMatchObject({ bound: false, status: 400 });
    expect(attempt).toHaveProperty('body', expect.stringContaining(UNVERIFIED_DATA_PLANE));
  });

  it('rejects a data plane whose store cannot be reached', async () => {
    const dataPlaneId = uniqueName('dp-e2e-unreachable-bind', true);
    await provisionUnreachableDataPlane(dataPlaneId);

    const attempt = await attemptBindingADomainTo(dataPlaneId);

    expect(attempt).toMatchObject({ bound: false, status: 400 });
    expect(attempt).toHaveProperty('body', expect.stringContaining(UNVERIFIED_DATA_PLANE));
  });

  it('accepts the same id once it is re-provisioned with settings that work', async () => {
    const dataPlaneId = uniqueName('dp-e2e-corrected', true);
    await provisionWrongCredentialsDataPlane(dataPlaneId);
    expect(await attemptBindingADomainTo(dataPlaneId)).toMatchObject({ bound: false, status: 400 });

    expect((await deleteDataPlane(dataPlaneId)).status).toBe(204);
    await provisionConnectableDataPlane(dataPlaneId);

    // a rejection has to be forgotten with the definition that caused it, or correcting the settings
    // leaves the id unusable until the node is restarted
    expect(await attemptBindingADomainTo(dataPlaneId)).toMatchObject({ bound: true });
  });

  it('still accepts a data plane the store answers for', async () => {
    const dataPlaneId = uniqueName('dp-e2e-verified', true);
    await provisionConnectableDataPlane(dataPlaneId);

    expect(await attemptBindingADomainTo(dataPlaneId)).toMatchObject({ bound: true });
  });

  it('does not check the data plane the gravitee.yml declares', async () => {
    // the node's own configuration is not provisioned, so it is served without a check
    expect(await attemptBindingADomainTo('default')).toMatchObject({ bound: true });
  });
});
