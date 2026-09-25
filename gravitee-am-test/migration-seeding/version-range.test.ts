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
import { inRange, isSatisfied, MigrationRequirement, migrationSeed, seededBy, toMinorVersion, variantFor } from './version-range';

const seed = migrationSeed<string>({
  name: 'sample',
  variants: [
    { range: { from: '4.12', until: '4.13' }, options: 'legacy' },
    { range: { from: '4.13' }, options: 'current' },
  ],
  seed: async () => undefined,
});

describe('toMinorVersion', () => {
  it('keeps the major.minor of a tag', () => {
    expect(toMinorVersion('4.10.0')).toBe('4.10');
    expect(toMinorVersion('4.13.0-alpha.4')).toBe('4.13');
    expect(toMinorVersion('4.12')).toBe('4.12');
  });

  it('rejects an unparseable version', () => {
    expect(() => toMinorVersion('latest')).toThrow(/Invalid AM version/);
  });
});

describe('inRange', () => {
  it('includes from and excludes until', () => {
    expect(inRange('4.12', { from: '4.12', until: '4.13' })).toBe(true);
    expect(inRange('4.13', { from: '4.12', until: '4.13' })).toBe(false);
    expect(inRange('4.11', { from: '4.12', until: '4.13' })).toBe(false);
  });

  it('treats a missing bound as open', () => {
    expect(inRange('4.99', { from: '4.12' })).toBe(true);
    expect(inRange('4.0', { until: '4.13' })).toBe(true);
    expect(inRange('5.0', {})).toBe(true);
  });

  it('compares on major.minor, whatever the patch or pre-release', () => {
    expect(inRange('4.13.0-alpha.4', { from: '4.13' })).toBe(true);
    expect(inRange('4.12.7', { until: '4.13' })).toBe(true);
    expect(inRange('4.10', { from: '4.9' })).toBe(true);
  });

  it('puts an unknown version in every range', () => {
    expect(inRange(undefined, { from: '4.13' })).toBe(true);
    expect(inRange('latest', { until: '4.10' })).toBe(true);
  });
});

describe('migrationSeed', () => {
  it('picks the variant covering the version', () => {
    expect(variantFor(seed, '4.12')?.options).toBe('legacy');
    expect(variantFor(seed, '4.14')?.options).toBe('current');
    expect(variantFor(seed, '4.11')).toBeUndefined();
  });

  it('rejects overlapping variants', () => {
    expect(() =>
      migrationSeed({
        name: 'overlapping',
        variants: [
          { range: { from: '4.12' }, options: undefined },
          { range: { from: '4.13', until: '4.14' }, options: undefined },
        ],
        seed: async () => undefined,
      }),
    ).toThrow(/overlapping variants/);
  });

  it('tells whether a channel seeded by a version holds the data set', () => {
    expect(seededBy(seed, '4.11')).toBe(false);
    expect(seededBy(seed, '4.12')).toBe(true);
    expect(seededBy(seed, undefined)).toBe(true);
  });
});

describe('isSatisfied', () => {
  const requirement: MigrationRequirement = { seed, managementApi: { from: '4.13' }, gateway: { from: '4.11' } };

  it('holds when every known version is in range', () => {
    expect(isSatisfied(requirement, { seededWith: '4.12', managementApi: '4.13.0-alpha.4', gateway: '4.12.0' })).toBe(true);
  });

  it('fails as soon as one known version is out of range', () => {
    expect(isSatisfied(requirement, { seededWith: '4.11', managementApi: '4.13.0', gateway: '4.12.0' })).toBe(false);
    expect(isSatisfied(requirement, { seededWith: '4.12', managementApi: '4.12.0', gateway: '4.12.0' })).toBe(false);
    expect(isSatisfied(requirement, { seededWith: '4.12', managementApi: '4.13.0', gateway: '4.10.0' })).toBe(false);
  });

  it('holds when the versions are unknown', () => {
    expect(isSatisfied(requirement, {})).toBe(true);
  });
});
