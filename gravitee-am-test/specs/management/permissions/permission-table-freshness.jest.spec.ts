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

import { describe, expect, it } from '@jest/globals';
import fs from 'fs';
import { setup } from '../../test-fixture';
import { PERMISSION_ENDPOINTS, PermissionEndpoint } from './fixtures/permission-endpoints.generated';

// The generator is CommonJS and importing it must stay side-effect free — it only regenerates when
// run as a script, so requiring it here reads the spec without writing anything.
// eslint-disable-next-line @typescript-eslint/no-var-requires
const { collect, SPEC } = require('../../../scripts/generate-permission-endpoints');

setup(200000);

/**
 * `permission-endpoints.generated.ts` is produced by hand-running the generator, and both sweeps
 * that drive it (endpoint-permission-sweep, permission-sufficiency) size themselves from whatever
 * it happens to contain. That makes staleness invisible in the worst way: adding a guarded endpoint
 * to the OpenAPI spec without regenerating leaves every existing test green while the sweep quietly
 * covers less than it claims, and the missing endpoint is exactly the untested one.
 *
 * Comparing against a fresh generation closes that. It needs no running stack — the check is
 * `docs/mapi/openapi.yaml` against a committed file — so it rides the jest jobs that already run on
 * every pull request rather than requiring a CI step of its own.
 */
describe('Permission table freshness - the committed table still matches the OpenAPI spec', () => {
  it('should find the OpenAPI spec the table is generated from', () => {
    // Asserted separately so a moved or missing spec reports as itself rather than as drift.
    expect(fs.existsSync(SPEC)).toBe(true);
  });

  it('should match a fresh generation from the OpenAPI spec', () => {
    const regenerated: PermissionEndpoint[] = collect();

    // Compared as data rather than as file text: formatting is prettier's business, and a diff here
    // should only ever mean the endpoints themselves have changed.
    expect(PERMISSION_ENDPOINTS).toEqual(regenerated);
  });

  it('should carry every endpoint the sweeps rely on being present', () => {
    // Guards the degenerate pass: an empty table would satisfy the comparison above if the
    // generator silently stopped matching anything, and both sweeps would then assert nothing.
    expect(PERMISSION_ENDPOINTS.length).toBeGreaterThan(50);
    expect(PERMISSION_ENDPOINTS.every((endpoint) => endpoint.route && endpoint.permission)).toBe(true);
  });
});
