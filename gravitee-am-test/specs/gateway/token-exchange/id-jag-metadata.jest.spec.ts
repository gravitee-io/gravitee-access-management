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
import { waitForOidcReady } from '@management-commands/domain-management-commands';
import { setup } from '../../test-fixture';
import { ID_JAG_TOKEN_TYPE, IdJagFixture, setupIdJagFixture } from './fixtures/id-jag-fixture';

setup(300000);

const IDENTITY_CHAINING_REQUESTED_TOKEN_TYPES = 'identity_chaining_requested_token_types_supported';

let fixture: IdJagFixture;

const fetchDiscoveryDocument = async (): Promise<Record<string, unknown>> =>
  (await waitForOidcReady(fixture.domain.hrid, { timeoutMs: 30000, intervalMs: 500 })).body;

beforeAll(async () => {
  fixture = await setupIdJagFixture();
});

afterAll(async () => {
  if (fixture) {
    await fixture.cleanUp();
  }
});

describe('ID-JAG issuance - advertised in the authorization server metadata', () => {
  it('should name the ID-JAG token type while the domain permits it', async () => {
    await fixture.setDomainAllowsIdJag(true);

    expect((await fetchDiscoveryDocument())[IDENTITY_CHAINING_REQUESTED_TOKEN_TYPES]).toEqual([ID_JAG_TOKEN_TYPE]);
  });

  it('should drop only that field, rather than empty it, once the domain stops permitting it', async () => {
    await fixture.setDomainAllowsIdJag(true);
    const { [IDENTITY_CHAINING_REQUESTED_TOKEN_TYPES]: advertised, ...everythingElse } = await fetchDiscoveryDocument();

    await fixture.setDomainAllowsIdJag(false);

    expect(advertised).toEqual([ID_JAG_TOKEN_TYPE]);
    expect(await fetchDiscoveryDocument()).toEqual(everythingElse);
  });
});
