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
import { getEntrypointsApi } from '@management-commands/service/utils';
import { waitForCockpitConnection } from '@cloud-commands/cockpit-commands';
import { retryUntil } from '@utils-commands/retry';
import { CloudOrganizationFixture, setupCloudOrganizationFixture } from './fixtures/cloud-organization-fixture';
import { setup } from '../test-fixture';

setup(120000);

const POLL = { timeoutMillis: 30000, intervalMillis: 1000 };

// Fixed id, as the organization fixture explains, and organizations survive the run because AM cannot delete
// one. So this id must stay unused by other specs, and by any earlier AM version: createDefaults runs once at
// creation, so an organization first created before AM-7354 keeps its default entrypoint and fails here.
const ORGANIZATION_NAME = 'cloud-org-default-entrypoint';

let organization: CloudOrganizationFixture;
let entrypoints: any[];

beforeAll(async () => {
  await waitForCockpitConnection();
  organization = await setupCloudOrganizationFixture(ORGANIZATION_NAME);
  entrypoints = await retryUntil(
    () => getEntrypointsApi(organization.accessToken).listEntrypoints({ organizationId: organization.organizationId }),
    (found: any[]) => found.some((entrypoint) => entrypoint.environmentId === organization.environmentId),
    POLL,
  );
});

afterAll(async () => {
  await organization?.cleanup();
});

describe('Entrypoints of a Cockpit-created organization', () => {
  // Positive control: without it an empty list would pass the assertion below.
  it('holds the entrypoint built from the environment access point', () => {
    expect(entrypoints).toEqual(
      expect.arrayContaining([
        expect.objectContaining({
          environmentId: organization.environmentId,
          url: `https://${organization.environmentId}.example.com`,
          defaultEntrypoint: true,
        }),
      ]),
    );
  });

  it('holds no organization-level entrypoint', () => {
    expect(entrypoints.filter((entrypoint) => entrypoint.environmentId === undefined)).toEqual([]);
  });
});
