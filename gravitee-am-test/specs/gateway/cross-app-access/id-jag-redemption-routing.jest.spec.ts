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
import {
  audiencesOf,
  decodeToken,
  IdJagRedemptionFixture,
  setupIdJagRedemptionFixture,
  THIRD_PARTY_JWT,
} from './fixtures/id-jag-redemption-fixture';

setup(600000);

let fixture: IdJagRedemptionFixture;

const errorOf = (response: any) => response.body.error;

describe('ID-JAG redemption', () => {
  beforeAll(async () => {
    fixture = await setupIdJagRedemptionFixture({ coexistence: true });
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  describe('coexistence with the jwt-bearer grant', () => {
    it('should route an ID-JAG to the cross app access grant when both grants are deployed', async () => {
      const assertion = await fixture.legacy.assertion();

      const response = await fixture.legacy.redeem(assertion).expect(200);

      expect(audiencesOf(decodeToken(response.body.access_token))).toContain(fixture.mcpResource);
    });

    it('should keep routing a plain assertion to the jwt-bearer grant', async () => {
      const response = await fixture.legacy.redeem(THIRD_PARTY_JWT).expect(200);

      expect(response.body.access_token).toBeDefined();
    });

    it('should refuse a plain assertion when only cross app access is authorised', async () => {
      const response = await fixture.agent.redeem(THIRD_PARTY_JWT).expect(400);

      expect(errorOf(response)).toBe('unsupported_grant_type');
    });

    it('should refuse a missing assertion when only cross app access is authorised', async () => {
      const response = await fixture.agent.redeem('').expect(400);

      expect(errorOf(response)).toBe('unsupported_grant_type');
    });

    it('should treat an unparsable header as a plain assertion', async () => {
      const strict = await fixture.agent.redeem('not-a-jwt').expect(400);
      const both = await fixture.legacy.redeem('not-a-jwt').expect(400);

      expect(errorOf(strict)).toBe('unsupported_grant_type');
      expect(errorOf(both)).toBe('invalid_grant');
    });
  });
});
