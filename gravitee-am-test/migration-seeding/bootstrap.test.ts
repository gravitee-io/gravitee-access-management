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
import { selectSeeds } from './bootstrap';
import { MIGRATION_SEEDS } from './seeds';

function seedNamesFor(version: string): string[] {
  return selectSeeds(MIGRATION_SEEDS, version).map(({ seed, variant }) => `${seed.name} ${JSON.stringify(variant.options ?? null)}`);
}

describe('migration seed bootstrap', () => {
  it('seeds nothing for a version before the first data set', () => {
    expect(seedNamesFor('4.9')).toEqual([]);
  });

  it('seeds only the Management API data before the trusted-issuer data set', () => {
    expect(seedNamesFor('4.10')).toEqual(['Management API data null']);
    expect(seedNamesFor('4.11')).toEqual(['Management API data null']);
  });

  it('seeds trusted issuers through the inline list in 4.12', () => {
    expect(seedNamesFor('4.12')).toEqual([
      'Management API data null',
      'token exchange trusted issuer {"trustedIssuerApi":"inline","keyRetrievalApi":"legacy-spiffe"}',
    ]);
  });

  it('seeds trusted issuers as trusted domains from 4.13 on, including versions not released yet', () => {
    const expected = [
      'Management API data null',
      'token exchange trusted issuer {"trustedIssuerApi":"trusted-domain","keyRetrievalApi":"key-retrieval-settings"}',
    ];
    expect(seedNamesFor('4.13')).toEqual(expected);
    expect(seedNamesFor('4.20')).toEqual(expected);
  });
});
