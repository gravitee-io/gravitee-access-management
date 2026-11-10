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
import type { Entrypoint as EntrypointModel } from '@am-models/Entrypoint';
import type { NewEntrypoint } from '@am-models/NewEntrypoint';
import { http } from './http';
import type { ApiModel } from './model';

/** An entrypoint of the organization. */
export type OrganizationEntrypoint = ApiModel<EntrypointModel, 'id' | 'name' | 'url'>;

export type OrganizationEntrypointInput = Pick<NewEntrypoint, 'name' | 'description' | 'url' | 'tags'>;

export function listOrganizationEntrypoints(organizationId: string): Promise<OrganizationEntrypoint[]> {
    return http<OrganizationEntrypoint[]>(`/organizations/${organizationId}/entrypoints`);
}

export function getOrganizationEntrypoint(organizationId: string, entrypointId: string): Promise<OrganizationEntrypoint> {
    return http<OrganizationEntrypoint>(`/organizations/${organizationId}/entrypoints/${encodeURIComponent(entrypointId)}`);
}

export function createOrganizationEntrypoint(
    organizationId: string,
    entrypoint: OrganizationEntrypointInput,
): Promise<OrganizationEntrypoint> {
    return http<OrganizationEntrypoint>(`/organizations/${organizationId}/entrypoints`, {
        method: 'POST',
        body: JSON.stringify(entrypoint),
    });
}

export function updateOrganizationEntrypoint(
    organizationId: string,
    entrypointId: string,
    entrypoint: OrganizationEntrypointInput,
): Promise<OrganizationEntrypoint> {
    return http<OrganizationEntrypoint>(`/organizations/${organizationId}/entrypoints/${encodeURIComponent(entrypointId)}`, {
        method: 'PUT',
        body: JSON.stringify({ name: entrypoint.name, description: entrypoint.description, url: entrypoint.url, tags: entrypoint.tags }),
    });
}

export function deleteOrganizationEntrypoint(organizationId: string, entrypointId: string): Promise<void> {
    return http<void>(`/organizations/${organizationId}/entrypoints/${encodeURIComponent(entrypointId)}`, { method: 'DELETE' });
}
