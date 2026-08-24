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

import { Domain } from '@management-models/Domain';
import { requestAdminAccessToken } from '@management-commands/token-management-commands';
import { safeDeleteDomain, setupDomainForTest } from '@management-commands/domain-management-commands';
import { uniqueName } from '@utils-commands/misc';
import {
  DEFAULT_ATTRIBUTE_MAPPINGS,
  KafkaReporterConfig,
  ReporterAttributeMapping,
  buildKafkaReporterConfigJson,
} from './kafka-reporter-config-helper';
import { Reporter } from '@management-models/Reporter';
import {
  createDomainReporter,
  deleteDomainReporter,
  listDomainReporters,
  updateDomainReporter,
} from '@management-commands/reporter-management-commands';

export interface CreateReporterOptions {
  name?: string;
  enabled?: boolean;
  configuration?: string;
  /** Mappings to declare. Omit for {@link DEFAULT_ATTRIBUTE_MAPPINGS}; pass `[]` or `null` to declare none. */
  attributeMappings?: ReporterAttributeMapping[] | null;
}

export interface DomainReporterFixture {
  domain: Domain;
  accessToken: string;
  kafkaConfig(overrides?: Partial<KafkaReporterConfig>): string;
  createdReporterIds: string[];

  /** Create a Kafka reporter under the domain and track it for cleanup. */
  createReporter(options?: CreateReporterOptions): Promise<Reporter>;

  /** Re-PUT an existing reporter; unsupplied fields fall back to the reporter's current values. */
  updateReporter(reporter: Reporter, options?: CreateReporterOptions): Promise<Reporter>;

  /** The domain's built-in system reporter, which no test creates. */
  systemReporter(): Promise<Reporter>;

  cleanUp(): Promise<void>;
}

export const setupDomainReporterFixture = async (): Promise<DomainReporterFixture> => {
  const accessToken = await requestAdminAccessToken();
  const { domain } = await setupDomainForTest(uniqueName('reporter-domain', true), { accessToken, waitForStart: false });
  const createdReporterIds: string[] = [];
  const kafkaConfig = buildKafkaReporterConfigJson;
  
  const mappingsOf = (options: CreateReporterOptions) =>
    options.attributeMappings === null ? undefined : (options.attributeMappings ?? DEFAULT_ATTRIBUTE_MAPPINGS);

  const createReporter = async (options: CreateReporterOptions = {}): Promise<Reporter> => {
    const reporter = await createDomainReporter(domain.id, accessToken, {
      type: 'reporter-am-kafka',
      name: options.name ?? uniqueName('kafka-reporter', true),
      enabled: options.enabled ?? true,
      configuration: options.configuration ?? kafkaConfig(),
      attributeMappings: mappingsOf(options),
    });
    createdReporterIds.push(reporter.id);
    return reporter;
  };

  const updateReporter = (reporter: Reporter, options: CreateReporterOptions = {}): Promise<Reporter> =>
    updateDomainReporter(domain.id, accessToken, reporter.id, {
      type: reporter.type,
      name: options.name ?? reporter.name,
      enabled: options.enabled ?? reporter.enabled,
      configuration: options.configuration ?? kafkaConfig(),
      attributeMappings: mappingsOf(options),
    });

  const systemReporter = async (): Promise<Reporter> => {
    const reporters = await listDomainReporters(domain.id, accessToken);
    const system = reporters.find((r) => r.system);
    if (!system) {
      throw new Error('the domain has no system reporter');
    }
    return system;
  };

  const cleanUp = async (): Promise<void> => {
    for (const id of createdReporterIds) {
      try {
        await deleteDomainReporter(domain.id, accessToken, id);
      } catch {
        // ignore cleanup errors
      }
    }
    await safeDeleteDomain(domain.id, accessToken);
  };

  return { domain, accessToken, kafkaConfig, createdReporterIds, createReporter, updateReporter, systemReporter, cleanUp };
};
