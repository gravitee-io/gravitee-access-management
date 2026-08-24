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

import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { deleteOrgReporter } from '@management-commands/reporter-management-commands';
import {
  DEFAULT_ATTRIBUTE_MAPPINGS,
  KafkaReporterConfig,
  ReporterAttributeMapping,
  buildKafkaReporterConfigJson,
} from './kafka-reporter-config-helper';
import { Reporter } from '@management-models/Reporter';
import { createOrgReporter, updateOrgReporter } from '@management-commands/reporter-management-commands';
import { uniqueName } from '@utils-commands/misc';

export interface CreateOrgReporterOptions {
  name?: string;
  enabled?: boolean;
  configuration?: string;
  /** Mappings to declare. Omit for {@link DEFAULT_ATTRIBUTE_MAPPINGS}; pass `[]` or `null` to declare none. */
  attributeMappings?: ReporterAttributeMapping[] | null;
}

export interface OrgReporterFixture {
  accessToken: string;
  kafkaConfig(overrides?: Partial<KafkaReporterConfig>): string;
  createdReporterIds: string[];

  /** Create a Kafka reporter under the organization and track it for cleanup. */
  createReporter(options?: CreateOrgReporterOptions): Promise<Reporter>;

  /** Re-PUT an existing reporter; unsupplied fields fall back to the reporter's current values. */
  updateReporter(reporter: Reporter, options?: CreateOrgReporterOptions): Promise<Reporter>;

  cleanUp(): Promise<void>;
}

export const setupOrgReporterFixture = async (): Promise<OrgReporterFixture> => {
  const accessToken = await requestAdminAccessToken();
  const createdReporterIds: string[] = [];
  const kafkaConfig = buildKafkaReporterConfigJson;
  
  const mappingsOf = (options: CreateOrgReporterOptions) =>
    options.attributeMappings === null ? undefined : (options.attributeMappings ?? DEFAULT_ATTRIBUTE_MAPPINGS);

  const createReporter = async (options: CreateOrgReporterOptions = {}): Promise<Reporter> => {
    const reporter = await createOrgReporter(accessToken, {
      type: 'reporter-am-kafka',
      name: options.name ?? uniqueName('org-kafka-reporter', true),
      enabled: options.enabled ?? true,
      configuration: options.configuration ?? kafkaConfig(),
      attributeMappings: mappingsOf(options),
    });
    createdReporterIds.push(reporter.id);
    return reporter;
  };

  const updateReporter = (reporter: Reporter, options: CreateOrgReporterOptions = {}): Promise<Reporter> =>
    updateOrgReporter(accessToken, reporter.id, {
      type: reporter.type,
      name: options.name ?? reporter.name,
      enabled: options.enabled ?? reporter.enabled,
      // preserved so a mapping-only update cannot silently un-inherit an organization reporter
      inherited: reporter.inherited,
      configuration: options.configuration ?? kafkaConfig(),
      attributeMappings: mappingsOf(options),
    });

  const cleanUp = async (): Promise<void> => {
    for (const id of createdReporterIds) {
      try {
        await deleteOrgReporter(accessToken, id);
      } catch {
        // ignore cleanup errors
      }
    }
  };

  return { accessToken, kafkaConfig, createdReporterIds, createReporter, updateReporter, cleanUp };
};
