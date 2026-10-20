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
import { HttpError, http } from './http';
import { type DomainRef, domainPath } from './paths';
import type { Plugin } from './plugins';

/** A resource of the list. The list returns the basic fields only. */
export interface Resource {
    readonly id: string;
    readonly name: string;
    readonly type: string;
}

export interface ResourcePlugin extends Plugin {
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

export function listResourcePlugins(): Promise<ResourcePlugin[]> {
    return http<ResourcePlugin[]>('/platform/plugins/resources?expand=icon');
}
