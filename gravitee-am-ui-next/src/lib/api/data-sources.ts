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
import type { DataSource as DataSourceModel } from '@am-models/DataSource';
import { http } from './http';
import type { ApiModel } from './model';
import { environmentPath } from './paths';

export type DataSource = ApiModel<DataSourceModel, 'id' | 'name'>;

export function listDataSources(organizationId: string, environmentId: string): Promise<DataSource[]> {
    return http<DataSource[]>(`${environmentPath(organizationId, environmentId)}/data-sources`);
}
