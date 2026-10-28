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
import type { Domain as DomainModel } from '@am-models/Domain';
import type { NewDomain as NewDomainModel } from '@am-models/NewDomain';
import { http } from './http';
import type { Page } from './management-api';
import type { ApiModel } from './model';
import { type DomainRef, domainPath, environmentPath, searchParam } from './paths';

export type Domain = ApiModel<DomainModel, 'id' | 'hrid' | 'name' | 'enabled'>;

const SWITCHER_PAGE_SIZE = 200;

/** Every domain of the environment, read page by page. */
export async function listDomains(organizationId: string, environmentId: string): Promise<Page<Domain>> {
    const page = (index: number) =>
        http<Page<Domain>>(`${environmentPath(organizationId, environmentId)}/domains?page=${index}&size=${SWITCHER_PAGE_SIZE}`);
    const first = await page(0);
    const data = [...first.data];
    for (let index = 1; data.length < first.totalCount; index++) {
        const next = await page(index);
        if (next.data.length === 0) {
            break;
        }
        data.push(...next.data);
    }
    return { data, currentPage: 0, totalCount: first.totalCount };
}

export function getDomain(organizationId: string, environmentId: string, domainId: string): Promise<Domain> {
    return http<Domain>(`${environmentPath(organizationId, environmentId)}/domains/${domainId}`);
}

export function searchDomains(
    organizationId: string,
    environmentId: string,
    page: number,
    size: number,
    query = '',
): Promise<Page<Domain>> {
    return http<Page<Domain>>(`${environmentPath(organizationId, environmentId)}/domains?page=${page}&size=${size}${searchParam(query)}`);
}

export function listDomainsByIds(organizationId: string, environmentId: string, ids: readonly string[]): Promise<Page<Domain>> {
    const params = ids.map(id => `ids=${encodeURIComponent(id)}`).join('&');
    return http<Page<Domain>>(`${environmentPath(organizationId, environmentId)}/domains?${params}`);
}

export function getDomainByHrid(organizationId: string, environmentId: string, hrid: string): Promise<Domain> {
    return http<Domain>(`${environmentPath(organizationId, environmentId)}/domains/_hrid/${hrid}`);
}

export type NewDomain = ApiModel<NewDomainModel>;

export function createDomain(organizationId: string, environmentId: string, domain: NewDomain): Promise<Domain> {
    return http<Domain>(`${environmentPath(organizationId, environmentId)}/domains`, { method: 'POST', body: JSON.stringify(domain) });
}

export function enableDomain(ref: DomainRef): Promise<Domain> {
    return http<Domain>(domainPath(ref), { method: 'PATCH', body: JSON.stringify({ enabled: true }) });
}
