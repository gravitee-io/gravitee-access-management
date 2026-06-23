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

export type SeedModule = {
  seed(label: string): Promise<void>;
};

export function toMinorVersion(version: string): string {
  const match = version.match(/^(\d+)\.(\d+)/);
  if (!match) {
    throw new Error(`Invalid AM version: ${version}`);
  }
  return `${match[1]}.${match[2]}`;
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
 * Seed one version's data set under a channel label.
 * @param version  Which seed module to run (the data shape), e.g. "4.10". Normalized to major.minor.
 * @param label    The channel/instance label used to name the seeded entities, e.g. "alpha" (baseline) or "beta" (newline).
 */
export async function runSeed(version: string, label: string): Promise<void> {
  applyMissingSeedEnvironmentDefaults();
  const minorVersion = toMinorVersion(version);
  const seedModule = (await import(`./versions/${minorVersion}/seed`)) as SeedModule;
  await seedModule.seed(label);
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
