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
import { jira } from '@specs-utils/jira';
import { getApplication, patchApplication } from '@management-commands/application-management-commands';
import { retryImmediatelyForThisFile, setup } from '../../test-fixture';
import { decodeToken, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);
// The removal happens once, between the two describes below, so the tests are order-dependent.
retryImmediatelyForThisFile();

/**
 * AM-2173 / UC-AM22 — removing custom metadata from an application.
 *
 * Removing the row in the Console is covered by Playwright. What that cannot show is whether the
 * removal takes effect for a claim that referred to it: the application is held in memory by the
 * gateway, so a stale value would surface in a token requested after the change rather than in
 * the saved settings.
 */
const REMOVED_KEY = 'tier';
const REMOVED_VALUE = 'platinum';
const KEPT_KEY = 'region';
const KEPT_VALUE = 'eu-west';

const metadataExpression = (key: string) => `{#context.attributes['client'].metadata['${key}']}`;

let fixture: TokenIdentityFixture;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    applicationMetadata: { [REMOVED_KEY]: REMOVED_VALUE, [KEPT_KEY]: KEPT_VALUE },
    tokenCustomClaims: [
      { tokenType: 'ACCESS_TOKEN', claimName: 'app_tier', claimValue: metadataExpression(REMOVED_KEY) },
      { tokenType: 'ACCESS_TOKEN', claimName: 'app_region', claimValue: metadataExpression(KEPT_KEY) },
    ],
  });
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

/** A fresh token's claims, so the gateway's in-memory view is what gets inspected. */
const freshClaims = async (): Promise<Record<string, any>> => {
  const tokens = await fixture.passwordGrant('openid');
  return decodeToken(tokens.access_token).payload;
};

describe('Application metadata removal (AM-2173)', () => {
  describe('before the entry is removed', () => {
    it(jira`both metadata values are readable in claims ${'AM-2173'}`, async () => {
      const claims = await freshClaims();

      // Anchors the rest of the file: without this, a later absence would prove nothing,
      // since a claim that never worked also looks absent.
      expect(claims.app_tier).toEqual(REMOVED_VALUE);
      expect(claims.app_region).toEqual(KEPT_VALUE);
    });
  });

  describe('after the entry is removed', () => {
    beforeAll(async () => {
      // Patching metadata replaces the whole map, so removal means sending it without the key.
      await patchApplication(fixture.domain.id, fixture.accessToken, { metadata: { [KEPT_KEY]: KEPT_VALUE } } as any, fixture.app.id);

      // An application change does not reliably advance the domain's lastSync, so polling the
      // observable — a freshly issued token — is both accurate and what this test cares about.
      const deadline = Date.now() + 30_000;
      for (;;) {
        if ((await freshClaims()).app_tier === undefined) {
          return;
        }
        if (Date.now() > deadline) {
          throw new Error('gateway still issued the removed metadata value 30s after it was deleted');
        }
      }
    });

    it(jira`the entry no longer appears in the application metadata ${'AM-2173'}`, async () => {
      const app = await getApplication(fixture.domain.id, fixture.accessToken, fixture.app.id);

      expect(app.metadata).not.toHaveProperty(REMOVED_KEY);
    });

    it(jira`a newly issued token no longer carries the removed value ${'AM-2173'}`, async () => {
      const claims = await freshClaims();

      // The claim is left out entirely rather than carrying null: ExecutionContextTokenEnhancer
      // only writes a claim when its expression evaluates to a non-null value.
      expect(claims).not.toHaveProperty('app_tier');
    });

    it(jira`the remaining metadata is untouched and still readable ${'AM-2173'}`, async () => {
      const app = await getApplication(fixture.domain.id, fixture.accessToken, fixture.app.id);
      expect(app.metadata).toMatchObject({ [KEPT_KEY]: KEPT_VALUE });

      expect((await freshClaims()).app_region).toEqual(KEPT_VALUE);
    });
  });
});
