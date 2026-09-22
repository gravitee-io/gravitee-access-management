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
import { getExtensionGrant } from '@management-commands/extension-grant-commands';
import { setup } from '../../test-fixture';
import { decodeToken, IdJagRedemptionFixture, setupIdJagRedemptionFixture } from './fixtures/id-jag-redemption-fixture';

setup(600000);

let fixture: IdJagRedemptionFixture;

const errorOf = (response: any) => response.body.error;

const subjectOf = (response: any) => decodeToken(response.body.access_token).sub;

describe('ID-JAG redemption', () => {
  beforeAll(async () => {
    fixture = await setupIdJagRedemptionFixture({ binding: true });
  });

  afterAll(async () => {
    if (fixture) {
      await fixture.cleanUp();
    }
  });

  describe('binding the assertion subject to a local user', () => {
    it('should bind the user the identity provider created for that subject', async () => {
      const assertion = await fixture.agent.assertion();

      const response = await fixture.agent.redeem(assertion).expect(200);

      expect(subjectOf(response)).toBe(fixture.boundUserSub);
    });

    it('should refuse a subject no local user matches', async () => {
      const assertion = await fixture.agent.assertion({ user: 'stranger' });

      const response = await fixture.agent.redeem(assertion).expect(400);

      expect(errorOf(response)).toBe('invalid_grant');
    });

    it('should bind through a rule on the assertion email claim', async () => {
      const assertion = await fixture.binder.assertion();

      const response = await fixture.binder.redeem(assertion).expect(200);

      expect(decodeToken(assertion).email).toBe(fixture.knownUser.email);
      expect(subjectOf(response)).toBeDefined();
    });

    it('should refuse a redemption when the binding rules match no user', async () => {
      const assertion = await fixture.binder.assertion({ user: 'stranger' });

      const response = await fixture.binder.redeem(assertion).expect(400);

      expect(errorOf(response)).toBe('invalid_grant');
    });

    it('should enable check user on a newly created cross app access grant', async () => {
      const grant = await getExtensionGrant(fixture.resourceDomain.id, fixture.accessToken, fixture.checkGrantId);

      expect(grant.userExists).toBe(true);
      expect(grant.createUser).toBe(false);
    });
  });
});
