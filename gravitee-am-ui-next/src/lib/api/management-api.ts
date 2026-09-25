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

/** Claims of the console JWT plus the flattened platform and organization permissions, as `GET /user` returns them. */
export interface CurrentUser {
    readonly sub: string;
    readonly org: string;
    readonly preferred_username?: string;
    readonly name?: string;
    readonly given_name?: string;
    readonly family_name?: string;
    readonly email?: string;
    readonly permissions: string[];
}

export interface Environment {
    readonly id: string;
    readonly name: string;
    readonly hrids: string[];
}

export interface Domain {
    readonly id: string;
    readonly hrid: string;
    readonly name: string;
    readonly description?: string;
    readonly enabled: boolean;
    readonly createdAt?: number;
    readonly updatedAt?: number;
}

export interface Page<T> {
    readonly data: T[];
    readonly currentPage: number;
    readonly totalCount: number;
}

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
    };
    readonly createdAt?: number;
}

export interface IdentityProviderSummary {
    readonly id: string;
    readonly name: string;
    readonly type: string;
    readonly system: boolean;
    readonly external: boolean;
}

export interface IdentityProvider extends IdentityProviderSummary {
    /** Plugin configuration, serialized as a JSON string. System providers do not return it. */
    readonly configuration?: string;
    readonly domainWhitelist?: string[];
    readonly mappers?: Record<string, unknown>;
    readonly roleMapper?: Record<string, unknown>;
    readonly groupMapper?: Record<string, unknown>;
}

export interface Form {
    /** Absent while the domain still uses the default template. */
    readonly id?: string;
    readonly template: string;
    readonly enabled: boolean;
    readonly content: string;
}

function domainPath(organizationId: string, environmentId: string, domainId: string): string {
    return `/organizations/${organizationId}/environments/${environmentId}/domains/${domainId}`;
}

export function getCurrentUser(): Promise<CurrentUser> {
    return http<CurrentUser>('/user');
}

export function listEnvironments(organizationId: string): Promise<Environment[]> {
    return http<Environment[]>(`/organizations/${organizationId}/environments`);
}

export function listDomains(organizationId: string, environmentId: string, page: number, size: number): Promise<Page<Domain>> {
    return http<Page<Domain>>(`/organizations/${organizationId}/environments/${environmentId}/domains?page=${page}&size=${size}`);
}

export function getDomainByHrid(organizationId: string, environmentId: string, hrid: string): Promise<Domain> {
    return http<Domain>(`/organizations/${organizationId}/environments/${environmentId}/domains/_hrid/${hrid}`);
}

export interface DomainRef {
    readonly organizationId: string;
    readonly environmentId: string;
    readonly domainId: string;
}

export function listApplications(ref: DomainRef, page: number, size: number): Promise<Page<Application>> {
    return http<Page<Application>>(
        `${domainPath(ref.organizationId, ref.environmentId, ref.domainId)}/applications?page=${page}&size=${size}`,
    );
}

export function getApplication(ref: DomainRef, applicationId: string): Promise<ApplicationDetail> {
    return http<ApplicationDetail>(`${domainPath(ref.organizationId, ref.environmentId, ref.domainId)}/applications/${applicationId}`);
}

export function patchApplication(
    ref: DomainRef,
    applicationId: string,
    patch: Pick<Application, 'name' | 'description' | 'enabled'>,
): Promise<ApplicationDetail> {
    return http<ApplicationDetail>(`${domainPath(ref.organizationId, ref.environmentId, ref.domainId)}/applications/${applicationId}`, {
        method: 'PATCH',
        body: JSON.stringify(patch),
    });
}

export function listIdentityProviders(ref: DomainRef): Promise<IdentityProviderSummary[]> {
    return http<IdentityProviderSummary[]>(`${domainPath(ref.organizationId, ref.environmentId, ref.domainId)}/identities`);
}

export function getIdentityProvider(ref: DomainRef, identityProviderId: string): Promise<IdentityProvider> {
    return http<IdentityProvider>(`${domainPath(ref.organizationId, ref.environmentId, ref.domainId)}/identities/${identityProviderId}`);
}

export function updateIdentityProvider(ref: DomainRef, provider: IdentityProvider): Promise<IdentityProvider> {
    const { name, type, configuration, domainWhitelist, mappers, roleMapper, groupMapper } = provider;
    return http<IdentityProvider>(`${domainPath(ref.organizationId, ref.environmentId, ref.domainId)}/identities/${provider.id}`, {
        method: 'PUT',
        body: JSON.stringify({ name, type, configuration, domainWhitelist, mappers, roleMapper, groupMapper }),
    });
}

export function getIdentityProviderSchema(type: string): Promise<Record<string, unknown>> {
    return http<Record<string, unknown>>(`/platform/plugins/identities/${type}/schema`);
}

export function getForm(ref: DomainRef, template: string): Promise<Form> {
    return http<Form>(`${domainPath(ref.organizationId, ref.environmentId, ref.domainId)}/forms?template=${template}`);
}

/** Creates the domain's own form the first time, then updates it. */
export function saveForm(ref: DomainRef, form: Form): Promise<Form> {
    const forms = `${domainPath(ref.organizationId, ref.environmentId, ref.domainId)}/forms`;
    return form.id
        ? http<Form>(`${forms}/${form.id}`, { method: 'PUT', body: JSON.stringify({ enabled: form.enabled, content: form.content }) })
        : http<Form>(forms, {
              method: 'POST',
              body: JSON.stringify({ template: form.template, enabled: form.enabled, content: form.content }),
          });
}
