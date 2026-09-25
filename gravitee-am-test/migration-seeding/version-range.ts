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

/**
 * Version ranges shared by the migration seeds (which data set a version seeds, and how) and the
 * migration specs (which assertions a channel and the deployed components can satisfy).
 *
 * Versions are compared on their major.minor only: "4.13.0-alpha.4" is 4.13.
 */

/** A major.minor version, e.g. "4.12". */
export type MinorVersion = `${number}.${number}`;

/**
 * A half-open range of major.minor versions: `from` is inclusive, `until` exclusive. Either bound may
 * be left out, so `{}` covers every version, `{ from: '4.12' }` 4.12 onwards and
 * `{ until: '4.13' }` everything before 4.13.
 */
export type VersionRange = { from?: MinorVersion; until?: MinorVersion };

type ParsedVersion = { major: number; minor: number };

/** The leading major.minor of an AM tag or version ("4.13.0-alpha.4" → "4.13"). Throws when unparseable. */
export function toMinorVersion(version: string): MinorVersion {
  const parsed = parseVersion(version);
  if (!parsed) {
    throw new Error(`Invalid AM version: ${version}`);
  }
  return `${parsed.major}.${parsed.minor}`;
}

function parseVersion(version: string | undefined): ParsedVersion | null {
  const match = String(version ?? '').match(/^(\d+)\.(\d+)/);
  return match ? { major: Number(match[1]), minor: Number(match[2]) } : null;
}

function compare(left: ParsedVersion, right: ParsedVersion): number {
  return left.major !== right.major ? left.major - right.major : left.minor - right.minor;
}

/**
 * Whether `version` falls in `range`. An unknown or unparseable version is in every range: outside
 * the migration tool nothing tells which version is running, and the specs then assert everything.
 */
export function inRange(version: string | undefined, range: VersionRange): boolean {
  const parsed = parseVersion(version);
  if (!parsed) {
    return true;
  }
  if (range.from && compare(parsed, parseVersion(range.from)!) < 0) {
    return false;
  }
  return !(range.until && compare(parsed, parseVersion(range.until)!) >= 0);
}

/** Whether two ranges share at least one version. */
function overlaps(left: VersionRange, right: VersionRange): boolean {
  const leftFrom = left.from ? parseVersion(left.from) : null;
  const rightFrom = right.from ? parseVersion(right.from) : null;
  const leftUntil = left.until ? parseVersion(left.until) : null;
  const rightUntil = right.until ? parseVersion(right.until) : null;
  const leftEndsBeforeRight = leftUntil !== null && rightFrom !== null && compare(leftUntil, rightFrom) <= 0;
  const rightEndsBeforeLeft = rightUntil !== null && leftFrom !== null && compare(rightUntil, leftFrom) <= 0;
  return !leftEndsBeforeRight && !rightEndsBeforeLeft;
}

/** How a data set is seeded over a range of versions: the options the seed function receives there. */
export interface SeedVariant<O> {
  range: VersionRange;
  options: O;
}

/**
 * A migration data set: the function that seeds it, and one variant per range of versions whose
 * Management API needs a different write path. A version no variant covers does not seed it.
 */
export interface MigrationSeed<O = void> {
  name: string;
  variants: SeedVariant<O>[];
  seed(channelLabel: string, options: O): Promise<void>;
}

/** Declare a migration seed. Throws when two variants cover the same version. */
export function migrationSeed<O>(definition: MigrationSeed<O>): MigrationSeed<O> {
  definition.variants.forEach((variant, index) => {
    definition.variants.slice(index + 1).forEach((other) => {
      if (overlaps(variant.range, other.range)) {
        throw new Error(
          `Migration seed "${definition.name}" declares overlapping variants ${describeRange(variant.range)} and ${describeRange(
            other.range,
          )}`,
        );
      }
    });
  });
  return definition;
}

/** The variant of `seed` that covers `version`, if any. */
export function variantFor<O>(seed: MigrationSeed<O>, version: string): SeedVariant<O> | undefined {
  return seed.variants.find((variant) => inRange(version, variant.range));
}

/**
 * Whether a channel seeded by `version` holds the data set of `seed`. An unknown version holds
 * everything, like {@link inRange}.
 */
export function seededBy(seed: MigrationSeed<any>, version: string | undefined): boolean {
  return !parseVersion(version) || variantFor(seed, version) !== undefined;
}

export function describeRange(range: VersionRange): string {
  return `[${range.from ?? '*'}, ${range.until ?? '*'})`;
}

/**
 * What a migration assertion needs to be meaningful: the channel must have been seeded with `seed`,
 * and the component it talks to must run a version in the given range. Every field is optional.
 */
export interface MigrationRequirement {
  seed?: MigrationSeed<any>;
  managementApi?: VersionRange;
  gateway?: VersionRange;
}

/** Declare a spec's named requirements, keeping their names typed. */
export function migrationRequirements<T extends Record<string, MigrationRequirement>>(requirements: T): T {
  return requirements;
}

/** The versions a requirement is checked against; any of them may be unknown. */
export interface MigrationVersions {
  seededWith?: string;
  managementApi?: string;
  gateway?: string;
}

/** Whether `requirement` holds for `versions`. Unknown versions satisfy every requirement. */
export function isSatisfied(requirement: MigrationRequirement, versions: MigrationVersions): boolean {
  return (
    (!requirement.seed || seededBy(requirement.seed, versions.seededWith)) &&
    (!requirement.managementApi || inRange(versions.managementApi, requirement.managementApi)) &&
    (!requirement.gateway || inRange(versions.gateway, requirement.gateway))
  );
}
