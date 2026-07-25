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
import { reporterTypeLabel } from './reporter-type-label';

describe('reporterTypeLabel', () => {
  it.each([
    ['reporter-am-elasticsearch', 'Elasticsearch'],
    ['reporter-am-tcp', 'TCP'],
    ['reporter-am-file', 'File'],
    ['reporter-am-kafka', 'Kafka'],
    ['reporter-am-jdbc', 'JDBC'],
    ['mongodb', 'MongoDB'],
  ])('labels %s as %s', (pluginId, expected) => {
    expect(reporterTypeLabel(pluginId)).toEqual(expected);
  });

  it('falls back to the plugin id when the reporter is unknown', () => {
    expect(reporterTypeLabel('reporter-am-not-shipped-yet')).toEqual('reporter-am-not-shipped-yet');
  });
});
