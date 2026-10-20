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
import type { Group as GroupModel } from '@am-models/Group';
import { http } from './http';
import type { Page } from './management-api';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';

export type Group = ApiModel<GroupModel, 'id' | 'name'>;

export function listGroups(ref: DomainRef, page: number, size: number): Promise<Page<Group>> {
    return http<Page<Group>>(`${domainPath(ref)}/groups?page=${page}&size=${size}`);
}
