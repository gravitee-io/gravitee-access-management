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

const LABELS = {
  'reporter-am-tcp': 'TCP',
  'reporter-am-file': 'File',
  'reporter-am-kafka': 'Kafka',
  'reporter-am-jdbc': 'JDBC',
  'reporter-am-elasticsearch': 'Elasticsearch',
  mongodb: 'MongoDB',
};

/** Display name for a reporter plugin id, shared by the reporter list and the reporter screen. */
export function reporterTypeLabel(pluginId: string): string {
  return LABELS[pluginId] ?? pluginId;
}
