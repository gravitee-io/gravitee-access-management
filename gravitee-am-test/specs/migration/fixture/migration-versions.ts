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
import { describe, it } from '@jest/globals';
import { isSatisfied, MigrationRequirement, MigrationVersions } from '../../../migration-seeding/version-range';

/**
 * The versions the migration pipeline is currently exercising, as published by the migration tool
 * (scripts/migration-tool/lib/Orchestrator.mjs), and the helpers that turn a spec's
 * {@link MigrationRequirement} into a running or a skipped test.
 *
 * The versions are absent when the suite is run outside the migration tool. Every requirement then
 * holds, so a plain `npm run test -- specs/migration` against a current stack keeps full coverage.
 */

/** The seeded channel the verify stage asserts: `alpha` carries the from-tag's data, `beta` the to-tag's. */
export function currentChannel(): string {
  return process.env.AM_MIGRATION_TEST_LABEL || 'alpha';
}

/** The versions a requirement is checked against for `channelLabel`. */
export function currentVersions(channelLabel: string = currentChannel()): MigrationVersions {
  return {
    seededWith: channelLabel === 'beta' ? process.env.AM_MIGRATION_TO_VERSION : process.env.AM_MIGRATION_FROM_VERSION,
    managementApi: process.env.AM_MIGRATION_MAPI_VERSION,
    gateway: process.env.AM_MIGRATION_GW_VERSION,
  };
}

/** `describe` when the requirement holds for the current channel and deployment, `describe.skip` otherwise. */
export function describeWhen(requirement: MigrationRequirement, channelLabel: string = currentChannel()) {
  return isSatisfied(requirement, currentVersions(channelLabel)) ? describe : describe.skip;
}

/** `it` when the requirement holds for the current channel and deployment, `it.skip` otherwise. */
export function itWhen(requirement: MigrationRequirement, channelLabel: string = currentChannel()) {
  return isSatisfied(requirement, currentVersions(channelLabel)) ? it : it.skip;
}
