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

import { MIGRATION_SEEDS } from './seeds';
import { MigrationSeed, SeedVariant, toMinorVersion, variantFor } from './version-range';

export { toMinorVersion };

/** The data sets `version` seeds, each with the variant that covers it, in registration order. */
export function selectSeeds(seeds: MigrationSeed<any>[], version: string): { seed: MigrationSeed<any>; variant: SeedVariant<any> }[] {
  return seeds.flatMap((seed) => {
    const variant = variantFor(seed, version);
    return variant ? [{ seed, variant }] : [];
  });
}

export function applyMissingSeedEnvironmentDefaults(): void {
  process.env.AM_MANAGEMENT_URL ||= 'http://localhost:8093';
  process.env.AM_MANAGEMENT_ENDPOINT ||= `${process.env.AM_MANAGEMENT_URL}/management`;
  process.env.AM_GATEWAY_URL ||= 'http://localhost:8091';
  process.env.AM_DOMAIN_DATA_PLANE_ID ||= 'dp1';
  process.env.AM_DEF_ORG_ID ||= 'DEFAULT';
  process.env.AM_DEF_ENV_ID ||= 'DEFAULT';
  process.env.AM_ADMIN_USERNAME ||= 'admin';
  process.env.AM_ADMIN_PASSWORD ||= 'adminadmin';
}

/**
 * Seed, under a channel label, every data set the given version covers.
 * @param version  The AM version whose data shape to seed, e.g. "4.12" or "4.13.0-alpha.4". Compared on major.minor.
 * @param label    The channel/instance label used to name the seeded entities, e.g. "alpha" (baseline) or "beta" (newline).
 */
export async function runSeed(version: string, label: string): Promise<void> {
  applyMissingSeedEnvironmentDefaults();
  const minorVersion = toMinorVersion(version);
  const selected = selectSeeds(MIGRATION_SEEDS, minorVersion);
  if (selected.length === 0) {
    throw new Error(`No migration seed covers version ${minorVersion}`);
  }
  for (const { seed, variant } of selected) {
    console.log(`🌱 Seeding "${seed.name}" for ${minorVersion} under label ${label}`);
    await seed.seed(label, variant.options);
  }
}

function readArg(name: string): string | undefined {
  const index = process.argv.indexOf(name);
  return index >= 0 ? process.argv[index + 1] : undefined;
}

if (require.main === module) {
  const version = readArg('--version');
  const label = readArg('--label');
  if (!version) {
    throw new Error('--version is required');
  }
  if (!label) {
    throw new Error('--label is required');
  }
  runSeed(version, label).catch((error) => {
    console.error(error);
    process.exit(1);
  });
}
