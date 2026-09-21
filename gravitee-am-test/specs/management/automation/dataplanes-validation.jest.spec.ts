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
import { setup } from '../../test-fixture';
import { AutomationDataPlaneFixture, dataPlanePayload, setupAutomationDataPlaneFixture } from './fixtures/automation-dataplane-fixture';

setup(200000);

/**
 * Field validation is covered by DataPlaneDefinitionServiceTest; what only a running stack can show
 * is that a definition the API accepts can actually be stored by the backend.
 */

let fixture: AutomationDataPlaneFixture;

// AM-7755: the JDBC columns (id 64, name 128) are narrower than the documented 255, so the accepted
// maximum only holds on MongoDB until the limits agree
const itMongo = process.env.REPOSITORY_TYPE !== 'jdbc' ? it : it.skip;

beforeAll(async () => {
  fixture = await setupAutomationDataPlaneFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('Automation API data planes - stored limits', () => {
  itMongo('should store an id of 255 characters, the documented maximum', async () => {
    // the padded id is not the one the fixture reserved, so it is deleted here
    const longest = fixture.reserveId('val-len').padEnd(255, 'x');

    try {
      expect((await fixture.client.putDataPlane(dataPlanePayload(longest))).status).toBe(200);
      expect((await fixture.client.getDataPlane(longest)).status).toBe(200);
    } finally {
      await fixture.client.deleteDataPlane(longest);
    }
  });
});
