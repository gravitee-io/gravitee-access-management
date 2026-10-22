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
import type { NewScope } from '@am-models/NewScope';
import type { Scope as ScopeModel } from '@am-models/Scope';
import type { UpdateScope } from '@am-models/UpdateScope';
import { http } from './http';
import type { Page } from './management-api';
import type { ApiModel } from './model';
import { type DomainRef, domainPath, searchParam } from './paths';

export type Scope = ApiModel<ScopeModel, 'id' | 'key' | 'name' | 'description' | 'system' | 'discovery' | 'parameterized'>;

export type CreateScopeBody = Pick<NewScope, 'key' | 'name' | 'description' | 'discovery' | 'parameterized' | 'expiresIn'>;

/** The body of the Angular `ScopeService.update`. `expiresIn` is null once the consent period is cleared. */
export type UpdateScopeBody = Pick<UpdateScope, 'name' | 'description' | 'discovery' | 'parameterized'> & {
    readonly expiresIn?: number | null;
};

export function listScopes(ref: DomainRef, page: number, size: number, query = ''): Promise<Page<Scope>> {
    return http<Page<Scope>>(`${domainPath(ref)}/scopes?page=${page}&size=${size}${searchParam(query)}`);
}

/** The Angular `ScopeService.findAllByDomain`: every scope of the domain, read 50 at a time. */
export async function listAllScopes(ref: DomainRef): Promise<Scope[]> {
    const size = 50;
    const first = await listScopes(ref, 0, size);
    const rest = await Promise.all(
        Array.from({ length: Math.ceil(first.totalCount / size) - 1 }, (_, index) => listScopes(ref, index + 1, size)),
    );
    return [first, ...rest].flatMap(page => page.data);
}

export function getScope(ref: DomainRef, scopeId: string): Promise<Scope> {
    return http<Scope>(`${domainPath(ref)}/scopes/${scopeId}`);
}

export function createScope(ref: DomainRef, scope: CreateScopeBody): Promise<Scope> {
    return http<Scope>(`${domainPath(ref)}/scopes`, { method: 'POST', body: JSON.stringify(scope) });
}

export function updateScope(ref: DomainRef, scopeId: string, scope: UpdateScopeBody): Promise<Scope> {
    return http<Scope>(`${domainPath(ref)}/scopes/${scopeId}`, { method: 'PUT', body: JSON.stringify(scope) });
}

/** The list toggles: the Angular `patchDiscovery` and `patchParameterized` each send one field. */
export function patchScope(
    ref: DomainRef,
    scopeId: string,
    patch: { readonly discovery: boolean } | { readonly parameterized: boolean },
): Promise<Scope> {
    return http<Scope>(`${domainPath(ref)}/scopes/${scopeId}`, { method: 'PATCH', body: JSON.stringify(patch) });
}

export function deleteScope(ref: DomainRef, scopeId: string): Promise<void> {
    return http<void>(`${domainPath(ref)}/scopes/${scopeId}`, { method: 'DELETE' });
}
