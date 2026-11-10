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
import type { Application as ApplicationModel } from '@am-models/Application';
import type { ApplicationIdentityProvider } from '@am-models/ApplicationIdentityProvider';
import type { CimdValidationResponse } from '@am-models/CimdValidationResponse';
import type { FilteredApplication } from '@am-models/FilteredApplication';
import type { NewApplication as NewApplicationModel } from '@am-models/NewApplication';
import type { NewCimdApplication as NewCimdApplicationModel } from '@am-models/NewCimdApplication';
import type { PatchApplication } from '@am-models/PatchApplication';
import type { PatchApplicationIdentityProvider } from '@am-models/PatchApplicationIdentityProvider';
import { http } from './http';
import type { Page } from './management-api';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';

export type ApplicationSummary = ApiModel<Pick<FilteredApplication, 'id' | 'name' | 'clientId'>, 'id' | 'name'>;

/** A row of the applications list. */
export type ApplicationSummaryRow = ApiModel<Omit<FilteredApplication, 'type'> & { type: ApplicationType }, 'id' | 'name' | 'type'>;

export function applicationPath(ref: DomainRef, applicationId: string): string {
    return `${domainPath(ref)}/applications/${encodeURIComponent(applicationId)}`;
}

export function listApplications(ref: DomainRef, page: number, size: number): Promise<Page<ApplicationSummary>> {
    return http<Page<ApplicationSummary>>(`${domainPath(ref)}/applications?page=${page}&size=${size}`);
}

/** A prefix match on the name or the client id. */
export function searchApplications(ref: DomainRef, text: string): Promise<Page<ApplicationSummary>> {
    return http<Page<ApplicationSummary>>(`${domainPath(ref)}/applications?q=${encodeURIComponent(`${text}*`)}`);
}

export type ApplicationType = 'web' | 'native' | 'browser' | 'service' | 'resource_server' | 'agent';

// The wire sends the type in lower case, and the identity providers and factors as arrays.
export type Application = ApiModel<
    Omit<ApplicationModel, 'type' | 'identityProviders' | 'factors'> & {
        type: ApplicationType;
        identityProviders?: ApplicationIdentityProvider[];
        factors?: string[];
    },
    'id' | 'name' | 'type'
>;

export type NewApplication = ApiModel<NewApplicationModel>;
export type NewCimdApplication = ApiModel<NewCimdApplicationModel>;
export type CimdValidation = ApiModel<CimdValidationResponse>;
export type ApplicationPatch = ApiModel<
    Omit<PatchApplication, 'identityProviders' | 'factors'> & { identityProviders?: PatchApplicationIdentityProvider[] }
>;

/** The request types, upper case. Agents have their own area. */
export const NON_AGENT_APPLICATION_TYPES = ['WEB', 'NATIVE', 'BROWSER', 'SERVICE', 'RESOURCE_SERVER'] as const;

export interface ApplicationSort {
    readonly field: 'name' | 'updatedAt';
    readonly dir: 'asc' | 'desc';
}

export function listDomainApplications(
    ref: DomainRef,
    page: number,
    size: number,
    query: string,
    sort: ApplicationSort,
): Promise<Page<ApplicationSummaryRow>> {
    const search = query ? `&q=${encodeURIComponent(`*${query}*`)}` : '';
    const types = NON_AGENT_APPLICATION_TYPES.map(type => `&type=${type}`).join('');
    return http<Page<ApplicationSummaryRow>>(
        `${domainPath(ref)}/applications/search?page=${page}&limit=${size}&sort=${sort.field}&dir=${sort.dir}${search}${types}`,
    );
}

export function getApplication(ref: DomainRef, applicationId: string): Promise<Application> {
    return http<Application>(applicationPath(ref, applicationId));
}

export function createApplication(ref: DomainRef, application: NewApplication): Promise<Application> {
    return http<Application>(`${domainPath(ref)}/applications`, { method: 'POST', body: JSON.stringify(application) });
}

export function validateCimd(ref: DomainRef, url: string): Promise<CimdValidation> {
    return http<CimdValidation>(`${domainPath(ref)}/cimd/validate`, { method: 'POST', body: JSON.stringify({ url }) });
}

export function createCimdApplication(ref: DomainRef, application: NewCimdApplication): Promise<Application> {
    return http<Application>(`${domainPath(ref)}/cimd/applications`, { method: 'POST', body: JSON.stringify(application) });
}

export function patchApplication(ref: DomainRef, applicationId: string, patch: ApplicationPatch): Promise<Application> {
    return http<Application>(applicationPath(ref, applicationId), { method: 'PATCH', body: JSON.stringify(patch) });
}

export function updateApplicationType(ref: DomainRef, applicationId: string, type: Uppercase<ApplicationType>): Promise<Application> {
    return http<Application>(`${applicationPath(ref, applicationId)}/type`, { method: 'PUT', body: JSON.stringify({ type }) });
}

export function deleteApplication(ref: DomainRef, applicationId: string): Promise<void> {
    return http<void>(applicationPath(ref, applicationId), { method: 'DELETE' });
}
