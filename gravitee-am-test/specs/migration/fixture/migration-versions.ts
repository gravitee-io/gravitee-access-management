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
 * The versions the migration pipeline is currently exercising, as published by the migration tool
 * (scripts/migration-tool/lib/Orchestrator.mjs). A spec reads these to skip a data set the channel
 * is too old to hold, or an assertion the deployed component versions cannot satisfy.
 *
 * They are absent when the suite is run outside the migration tool. Every helper here then answers
 * "assert everything", so a plain `npm run test -- specs/migration` against a current stack keeps
 * full coverage.
 */

type MinorVersion = { major: number; minor: number };

/** Token exchange (RFC 8693) appeared in 4.11: neither component knows it before. */
export const TOKEN_EXCHANGE_VERSION: MinorVersion = { major: 4, minor: 11 };

/** Trusted issuers became trusted-domain entities of their own in 4.13. */
export const TRUSTED_DOMAIN_ENTITY_VERSION: MinorVersion = { major: 4, minor: 13 };

/**
 * The first version the trusted-issuer data set is seeded from. 4.11 already had trusted issuers
 * but no way to widen the key-retrieval policy, so a 4.11-seeded issuer whose JWKS sits on a
 * loopback address could not be resolved after the upgrade.
 */
export const TOKEN_EXCHANGE_SEED_VERSION: MinorVersion = { major: 4, minor: 12 };

/** Parse the leading major.minor of an AM tag ("4.13.0-alpha.4" → 4.13). Null when unparseable. */
export function parseMinorVersion(version: string | undefined): MinorVersion | null {
  const match = String(version ?? '').match(/^(\d+)\.(\d+)/);
  return match ? { major: Number(match[1]), minor: Number(match[2]) } : null;
}

/** True when `version` is at least `floor`, and when `version` is unknown. */
export function isAtLeast(version: string | undefined, floor: MinorVersion): boolean {
  const parsed = parseMinorVersion(version);
  if (!parsed) {
    return true;
  }
  return parsed.major !== floor.major ? parsed.major > floor.major : parsed.minor >= floor.minor;
}

/**
 * The version that seeded a channel: the alpha channel carries the from-tag's data set, the beta
 * channel the to-tag's.
 */
export function seedVersionOf(channelLabel: string): string | undefined {
  return channelLabel === 'beta' ? process.env.AM_MIGRATION_TO_VERSION : process.env.AM_MIGRATION_FROM_VERSION;
}

/** Whether this channel was seeded by a version that knows the trusted-issuer data set. */
export function channelHoldsTokenExchangeSeed(channelLabel: string): boolean {
  return isAtLeast(seedVersionOf(channelLabel), TOKEN_EXCHANGE_SEED_VERSION);
}

/**
 * Whether the deployed Management API is at least `floor`. A channel seeded by a recent version
 * is still asserted after a downgrade, so a feature the downgraded component does not know has
 * nothing to be asserted against.
 */
export function managementApiSupports(floor: MinorVersion): boolean {
  return isAtLeast(process.env.AM_MIGRATION_MAPI_VERSION, floor);
}

/** Whether the deployed gateways are at least `floor`. See {@link managementApiSupports}. */
export function gatewaySupports(floor: MinorVersion): boolean {
  return isAtLeast(process.env.AM_MIGRATION_GW_VERSION, floor);
}

/**
 * Whether the deployed Management API serves the `trusted-domains` endpoint, which 4.13 introduced
 * alongside the entities. Unlike the inline list — which every version must keep serving — this
 * endpoint simply does not exist before then, so there is nothing to assert against.
 */
export function managementApiHasTrustedDomainsEndpoint(): boolean {
  return managementApiSupports(TRUSTED_DOMAIN_ENTITY_VERSION);
}
