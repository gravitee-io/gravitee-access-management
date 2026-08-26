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
import { retryUntil } from '@utils-commands/retry';
import { setup } from '../../test-fixture';
import { decodeToken, setupTokenIdentityFixture, TokenIdentityFixture } from './fixtures/token-identity-fixture';

setup(200000);

const TIER_KEY = 'tier';
const TIER_VALUE = 'platinum';
const REGION_KEY = 'region';
const REGION_VALUE = 'eu-west';

/**
 * Written with a fresh value on every change so a test can wait for its own write to reach the
 * gateway without watching the entry it is about to assert on - waiting and asserting on the same
 * claim would make the assertion true by construction.
 */
const REVISION_KEY = 'revision';

const metadataExpression = (key: string) => `{#context.attributes['client'].metadata['${key}']}`;

let fixture: TokenIdentityFixture;
let revisions = 0;

beforeAll(async () => {
  fixture = await setupTokenIdentityFixture({
    applicationMetadata: { [TIER_KEY]: TIER_VALUE, [REGION_KEY]: REGION_VALUE },
    tokenCustomClaims: [
      { tokenType: 'ACCESS_TOKEN', claimName: 'app_tier', claimValue: metadataExpression(TIER_KEY) },
      { tokenType: 'ACCESS_TOKEN', claimName: 'app_region', claimValue: metadataExpression(REGION_KEY) },
      { tokenType: 'ACCESS_TOKEN', claimName: 'app_revision', claimValue: metadataExpression(REVISION_KEY) },
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

/**
 * Replaces the application's metadata, then waits until the gateway serves that exact write.
 *
 * Patching metadata replaces the whole map, so an entry is removed by sending the map without it.
 * An application change does not reliably advance the domain's lastSync, so the wait polls the
 * observable - a freshly issued token - keyed on the revision this call just wrote.
 */
const writeMetadata = async (metadata: Record<string, string>): Promise<void> => {
  const revision = `rev-${Date.now()}-${++revisions}`;

  await patchApplication(
    fixture.domain.id,
    fixture.accessToken,
    { metadata: { ...metadata, [REVISION_KEY]: revision } } as any,
    fixture.app.id,
  );

  await retryUntil(freshClaims, (claims) => claims.app_revision === revision, { timeoutMillis: 30000, intervalMillis: 500 });
};

describe('Application metadata - an entry is readable as a claim', () => {
  it(jira`each entry reaches the token through its own claim ${'AM-2173'}`, async () => {
    await writeMetadata({ [TIER_KEY]: TIER_VALUE, [REGION_KEY]: REGION_VALUE });

    const claims = await freshClaims();
    expect(claims.app_tier).toEqual(TIER_VALUE);
    expect(claims.app_region).toEqual(REGION_VALUE);

    const app = await getApplication(fixture.domain.id, fixture.accessToken, fixture.app.id);
    expect(app.metadata).toMatchObject({ [TIER_KEY]: TIER_VALUE, [REGION_KEY]: REGION_VALUE });
  });
});

describe('Application metadata - removing an entry', () => {
  it(jira`the removed entry leaves both the application and its claim, and the rest survives ${'AM-2173'}`, async () => {
    await writeMetadata({ [TIER_KEY]: TIER_VALUE, [REGION_KEY]: REGION_VALUE });

    // Anchors the assertions below: a later absence would prove nothing, since a claim that
    // never worked also looks absent.
    expect((await freshClaims()).app_tier).toEqual(TIER_VALUE);

    await writeMetadata({ [REGION_KEY]: REGION_VALUE });

    const claims = await freshClaims();
    // The claim is left out entirely rather than carrying null: ExecutionContextTokenEnhancer
    // only writes a claim when its expression evaluates to a non-null value.
    expect(claims).not.toHaveProperty('app_tier');
    expect(claims.app_region).toEqual(REGION_VALUE);

    const app = await getApplication(fixture.domain.id, fixture.accessToken, fixture.app.id);
    expect(app.metadata).not.toHaveProperty(TIER_KEY);
    expect(app.metadata).toMatchObject({ [REGION_KEY]: REGION_VALUE });
  });
});
