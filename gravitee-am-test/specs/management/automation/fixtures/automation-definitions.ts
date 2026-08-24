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

import { buildCertificate } from '@api-fixtures/certificates';
import { buildKafkaReporterConfigJson } from '../../reporters/fixtures/kafka-reporter-config-helper';

export interface AutomationDomainDef {
  key: string;
  name?: string;
  description?: string;
  path?: string;
  enabled?: boolean;
  dataPlaneId?: string;
  certificates?: object[];
  accountSettings?: Record<string, unknown>;
  saml?: object | null;
  [extra: string]: unknown;
}

const defaultDataPlaneId = (): string => process.env.AM_DOMAIN_DATA_PLANE_ID || 'default';

/**
 * Build an Automation API domain definition with sensible defaults for path,
 * name and dataPlaneId. Overrides win — pass `path` or `name` to set them
 * explicitly, or include extra top-level keys (e.g. `saml`, `certificates`).
 */
export const buildAutomationDomainDef = (overrides: AutomationDomainDef): AutomationDomainDef => ({
  name: `Automation Domain ${overrides.key}`,
  description: 'Created via Automation API',
  path: `/${overrides.key}`,
  dataPlaneId: defaultDataPlaneId(),
  ...overrides,
});

export interface InlineUser {
  username: string;
  password: string;
  firstname?: string;
  lastname?: string;
}

export interface AutomationIdpDef {
  key: string;
  name: string;
  type: string;
  configuration: string;
}

/**
 * Build an inline-am-idp definition. `users` is serialized into the
 * `configuration` string field expected by the Automation API.
 */
export const buildInlineIdpDef = (overrides: {
  key: string;
  name?: string;
  system?: boolean;
  users: InlineUser[];
}): AutomationIdpDef => ({
  key: overrides.key,
  name: overrides.name ?? `Automation IDP ${overrides.key}`,
  type: 'inline-am-idp',
  ...(overrides.system !== undefined ? { system: overrides.system } : {}),
  configuration: JSON.stringify({
    users: overrides.users.map((u) => ({
      firstname: u.firstname ?? 'Test',
      lastname: u.lastname ?? 'User',
      username: u.username,
      password: u.password,
    })),
  }),
});

/**
 * Build an Automation API certificate definition keyed by `key`, reusing a known-good
 * java-keystore certificate configuration. Pass `system: true` to mark it the domain's
 * system certificate — in which case only `key` is meaningful and the certificate material
 * is built from `domains.certificates.default.*` settings in {@code gravitee.yaml}.
 */
export const buildAutomationCertificateDef = (overrides: {
  key: string;
  name?: string;
  system?: boolean;
}): object => ({
  ...buildCertificate(0),
  key: overrides.key,
  name: overrides.name ?? `Automation cert ${overrides.key}`,
  ...(overrides.system !== undefined ? { system: overrides.system } : {}),
});

export interface AutomationReporterAttributeMapping {
  expression: string;
  exportedName: string;
}

/** The mappings a reporter definition declares when it does not say otherwise. */
export const DEFAULT_REPORTER_ATTRIBUTE_MAPPINGS: AutomationReporterAttributeMapping[] = [
  { expression: "{#context.attributes['user'].additionalInformation['sub']}", exportedName: 'user_sub' },
  { expression: "{#context.attributes['client'].clientId}", exportedName: 'client_id' },
];

export interface AutomationReporterDefOverrides {
  key?: string;
  name?: string;
  system?: boolean;
  configuration?: string;
  /**
   * Omit to take the builder's default.
   */
  attributeMappings?: AutomationReporterAttributeMapping[] | null;
}

/**
 * Resolves the `attributeMappings` field. `undefined` falls back and `null` leaves the field off the payload.
 */
const attributeMappingsField = (
  supplied: AutomationReporterAttributeMapping[] | null | undefined,
  fallback: AutomationReporterAttributeMapping[] | null,
): object => {
  const resolved = supplied === undefined ? fallback : supplied;
  return resolved === null ? {} : { attributeMappings: resolved };
};

/**
 * Build an Automation API reporter definition keyed by `key`, using a schema-valid Kafka
 * reporter configuration. Pass `system: true` to mark it the domain's system reporter — in
 * which case only `key` is meaningful and the reporter is built from
 * `domains.reporters.default.*` and repository settings in {@code gravitee.yaml}.
 */
export const buildAutomationReporterDef = (overrides: AutomationReporterDefOverrides & { key: string }): object => ({
  key: overrides.key,
  name: overrides.name ?? `Automation reporter ${overrides.key}`,
  type: 'reporter-am-kafka',
  configuration: overrides.configuration ?? buildKafkaReporterConfigJson(),
  ...attributeMappingsField(overrides.attributeMappings, DEFAULT_REPORTER_ATTRIBUTE_MAPPINGS),
  ...(overrides.system !== undefined ? { system: overrides.system } : {}),
});

export const buildSystemAutomationDef = (key: string): object => ({
  key,
  system: true,
});

/**
 * A system reporter definition — minimal by design, so nothing is defaulted onto it. Mappings appear only
 * when a test declares them explicitly.
 */
export const buildSystemAutomationReporterDef = (overrides: AutomationReporterDefOverrides & { key: string }): object => ({
  ...buildSystemAutomationDef(overrides.key),
  ...attributeMappingsField(overrides.attributeMappings, null),
});
