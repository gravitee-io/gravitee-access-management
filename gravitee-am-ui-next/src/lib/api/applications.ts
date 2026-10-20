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
import { type DomainRef, domainPath, searchParam } from './paths';

export type ApplicationType = 'web' | 'native' | 'browser' | 'service' | 'resource_server';

export interface Application {
    readonly id: string;
    readonly name: string;
    readonly description?: string;
    readonly type: ApplicationType;
    readonly enabled: boolean;
    readonly updatedAt?: number;
}

export interface ApplicationDetail extends Application {
    readonly settings?: {
        readonly oauth?: {
            readonly clientId?: string;
            readonly grantTypes?: string[];
            readonly redirectUris?: string[];
            readonly scopeSettings?: { readonly scope: string }[];
        };
        readonly advanced?: { readonly flowsInherited?: boolean };
    };
    readonly createdAt?: number;
}

export function listApplications(ref: DomainRef, page: number, size: number, query = ''): Promise<Page<Application>> {
    return http<Page<Application>>(`${domainPath(ref)}/applications?page=${page}&size=${size}${searchParam(query)}`);
}

export function getApplication(ref: DomainRef, applicationId: string): Promise<ApplicationDetail> {
    return http<ApplicationDetail>(`${domainPath(ref)}/applications/${applicationId}`);
}

export function patchApplication(
    ref: DomainRef,
    applicationId: string,
    patch: Partial<Pick<Application, 'name' | 'description' | 'enabled'>> & {
        readonly settings?: { readonly advanced: { readonly flowsInherited: boolean } };
    },
): Promise<ApplicationDetail> {
    return http<ApplicationDetail>(`${domainPath(ref)}/applications/${applicationId}`, {
        method: 'PATCH',
        body: JSON.stringify(patch),
    });
}
