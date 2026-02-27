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
import { KafkaReporterConfig, buildKafkaReporterConfigJson } from './kafka-reporter-config-helper';

export interface OrgReporterFixture {
  accessToken: string;
  kafkaConfig(overrides?: Partial<KafkaReporterConfig>): string;
  createdReporterIds: string[];
  cleanUp(): Promise<void>;
}

export const setupOrgReporterFixture = async (): Promise<OrgReporterFixture> => {
  const accessToken = await requestAdminAccessToken();
  const createdReporterIds: string[] = [];
  const kafkaConfig = buildKafkaReporterConfigJson;
  
  const cleanUp = async (): Promise<void> => {
    for (const id of createdReporterIds) {
      try {
        await deleteOrgReporter(accessToken, id);
      } catch {
        // ignore cleanup errors
      }
    }
  };

  return { accessToken, kafkaConfig, createdReporterIds, cleanUp };
};
