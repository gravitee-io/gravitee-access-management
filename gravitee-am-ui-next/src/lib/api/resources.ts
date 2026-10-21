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
import type { NewServiceResource } from '@am-models/NewServiceResource';
import type { ServiceResource } from '@am-models/ServiceResource';
import { HttpError, http } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';
import type { Plugin } from './plugins';

/** A resource of the list. The list returns the basic fields only. */
export type Resource = ApiModel<ServiceResource, 'id' | 'name' | 'type'>;

/** A resource as the detail returns it. `configuration` is the plugin configuration, serialized as a JSON string. */
export type ResourceDetail = ApiModel<ServiceResource, 'id' | 'name' | 'type' | 'configuration'>;

export type NewResource = ApiModel<NewServiceResource>;

export interface ResourcePlugin extends Plugin {
    /** A data URI, when the request expands the icon. */
    readonly icon?: string;
    readonly categories?: readonly string[];
}

export function listResources(ref: DomainRef): Promise<Resource[]> {
    return http<Resource[]>(`${domainPath(ref)}/resources`);
}

/** The Angular `findByDomainWhenPermitted`: a failed list counts as no resources. A 401 still reaches the login chain. */
export function listResourcesWhenPermitted(ref: DomainRef): Promise<Resource[]> {
    return listResources(ref).catch((error: unknown) => {
        if (error instanceof HttpError && error.status === 401) {
            throw error;
        }
        return [];
    });
}

export function getResource(ref: DomainRef, resourceId: string): Promise<ResourceDetail> {
    return http<ResourceDetail>(`${domainPath(ref)}/resources/${resourceId}`);
}

export function createResource(ref: DomainRef, resource: NewResource): Promise<ResourceDetail> {
    return http<ResourceDetail>(`${domainPath(ref)}/resources`, { method: 'POST', body: JSON.stringify(resource) });
}

export function updateResource(
    ref: DomainRef,
    resourceId: string,
    resource: Pick<ResourceDetail, 'name' | 'type' | 'configuration'>,
): Promise<ResourceDetail> {
    const { name, type, configuration } = resource;
    return http<ResourceDetail>(`${domainPath(ref)}/resources/${resourceId}`, {
        method: 'PUT',
        body: JSON.stringify({ name, type, configuration }),
    });
}

export function deleteResource(ref: DomainRef, resourceId: string): Promise<void> {
    return http<void>(`${domainPath(ref)}/resources/${resourceId}`, { method: 'DELETE' });
}

export function listResourcePlugins(): Promise<ResourcePlugin[]> {
    return http<ResourcePlugin[]>('/platform/plugins/resources?expand=icon');
}

// A plugin that is not installed has no schema: the endpoint answers 204, and the form then has no configuration fields.
export async function getResourceSchema(type: string): Promise<Record<string, unknown>> {
    return (await http<Record<string, unknown> | undefined>(`/platform/plugins/resources/${type}/schema`)) ?? {};
}
