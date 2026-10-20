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
import { http } from './http';
import type { Page } from './management-api';
import { environmentPath, searchParam } from './paths';

export interface Domain {
    readonly id: string;
    readonly hrid: string;
    readonly name: string;
    readonly description?: string;
    readonly enabled: boolean;
    readonly createdAt?: number;
    readonly updatedAt?: number;
}

export function listDomains(organizationId: string, environmentId: string, page: number, size: number, query = ''): Promise<Page<Domain>> {
    return http<Page<Domain>>(`${environmentPath(organizationId, environmentId)}/domains?page=${page}&size=${size}${searchParam(query)}`);
}

export function getDomainByHrid(organizationId: string, environmentId: string, hrid: string): Promise<Domain> {
    return http<Domain>(`${environmentPath(organizationId, environmentId)}/domains/_hrid/${hrid}`);
}
