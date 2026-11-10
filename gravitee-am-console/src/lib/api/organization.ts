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
import type { FilteredIdentityProviderInfo } from '@am-models/FilteredIdentityProviderInfo';
import type { InstallationEntity } from '@am-models/InstallationEntity';
import { http } from './http';
import type { ApiModel } from './model';

/** The spec types this response as `Domain`. The Management API returns the organization settings. */
export interface OrganizationSettings {
    /** The ids of the identity providers offered on the console login page. */
    readonly identities?: readonly string[];
}

export type OrganizationIdentityProvider = ApiModel<FilteredIdentityProviderInfo, 'id' | 'name' | 'external'>;

export type Installation = ApiModel<InstallationEntity, 'id'>;

export function getOrganizationSettings(organizationId: string): Promise<OrganizationSettings> {
    return http<OrganizationSettings>(`/organizations/${organizationId}/settings`);
}

export function patchOrganizationSettings(
    organizationId: string,
    settings: { readonly identities: readonly string[] },
): Promise<OrganizationSettings> {
    return http<OrganizationSettings>(`/organizations/${organizationId}/settings`, { method: 'PATCH', body: JSON.stringify(settings) });
}

export function listOrganizationIdentityProviders(organizationId: string): Promise<OrganizationIdentityProvider[]> {
    return http<OrganizationIdentityProvider[]>(`/organizations/${organizationId}/identities`);
}

export function getInstallation(): Promise<Installation> {
    return http<Installation>('/platform/installation');
}
