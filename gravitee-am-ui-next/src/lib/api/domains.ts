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
import type { CorsSettings as CorsSettingsModel } from '@am-models/CorsSettings';
import type { Domain as DomainModel } from '@am-models/Domain';
import type { Entrypoint as EntrypointModel } from '@am-models/Entrypoint';
import type { LoginSettings as LoginSettingsModel } from '@am-models/LoginSettings';
import type { NewDomain as NewDomainModel } from '@am-models/NewDomain';
import type { VirtualHost as VirtualHostModel } from '@am-models/VirtualHost';
import type { WebProtectionSettings as WebProtectionSettingsModel } from '@am-models/WebProtectionSettings';
import { http } from './http';
import type { Page } from './management-api';
import type { MembershipsResponse, NewMembership } from './memberships';
import type { ApiModel } from './model';
import { type DomainRef, domainPath, environmentPath, searchParam } from './paths';

export type VirtualHost = ApiModel<VirtualHostModel, 'host' | 'path'>;

export interface CertificateSettings {
    /** The id of the certificate used when the selected one fails to load or sign. */
    readonly fallbackCertificate?: string | null;
}

export type Domain = ApiModel<Omit<DomainModel, 'vhosts'> & { vhosts?: readonly VirtualHost[] }, 'id' | 'hrid' | 'name' | 'enabled'>;

export function listDomains(organizationId: string, environmentId: string, page: number, size: number, query = ''): Promise<Page<Domain>> {
    return http<Page<Domain>>(`${environmentPath(organizationId, environmentId)}/domains?page=${page}&size=${size}${searchParam(query)}`);
}

export type NewDomain = ApiModel<NewDomainModel>;

export function createDomain(organizationId: string, environmentId: string, domain: NewDomain): Promise<Domain> {
    return http<Domain>(`${environmentPath(organizationId, environmentId)}/domains`, { method: 'POST', body: JSON.stringify(domain) });
}

export function getDomainByHrid(organizationId: string, environmentId: string, hrid: string): Promise<Domain> {
    return http<Domain>(`${environmentPath(organizationId, environmentId)}/domains/_hrid/${hrid}`);
}

export type Entrypoint = ApiModel<EntrypointModel, 'id' | 'url'>;

export function listDomainEntrypoints(ref: DomainRef): Promise<Entrypoint[]> {
    return http<Entrypoint[]>(`${domainPath(ref)}/entrypoints`);
}

/** The Angular `DomainService.getEntrypoint`: the only entrypoint, else the first one that is not the default. */
export function domainEntrypoint(entrypoints: readonly Entrypoint[]): Entrypoint | undefined {
    return entrypoints.length === 1 ? entrypoints[0] : (entrypoints.find(entrypoint => !entrypoint.defaultEntrypoint) ?? entrypoints[0]);
}

/** The Angular `EntrypointService.resolveBaseUrl`: the URL applications use to reach the domain. */
export function accessUrl(entrypointUrl: string, path: string, vhostMode: boolean, vhosts: readonly VirtualHost[]): string {
    let base = entrypointUrl;
    let resolvedPath = path;
    const vhost = vhostMode ? vhosts.find(candidate => candidate.overrideEntrypoint) : undefined;
    if (vhost) {
        base = (/^(https?:\/\/)/.exec(entrypointUrl)?.[1] ?? '') + vhost.host;
        resolvedPath = vhost.path;
    }
    return resolvedPath === '/' ? base : base + resolvedPath;
}

export function updateCertificateSettings(ref: DomainRef, certificateSettings: CertificateSettings): Promise<Domain> {
    return http<Domain>(`${domainPath(ref)}/certificate-settings`, { method: 'PUT', body: JSON.stringify(certificateSettings) });
}

/** The fields the Angular `DomainService.patchGeneralSettings` sends. */
export interface DomainGeneralSettings {
    readonly name: string;
    readonly description?: string;
    readonly path?: string;
    readonly enabled: boolean;
    readonly tags: readonly string[];
    readonly oidc: { readonly postLogoutRedirectUris: readonly string[]; readonly requestUris: readonly string[] };
    readonly master: boolean;
}

export function patchDomainGeneralSettings(ref: DomainRef, settings: DomainGeneralSettings): Promise<Domain> {
    return http<Domain>(domainPath(ref), { method: 'PATCH', body: JSON.stringify(settings) });
}

/** The fields the Angular `DomainService.patchEntrypoints` sends. */
export interface DomainEntrypointSettings {
    readonly path?: string;
    readonly vhostMode: boolean;
    readonly vhosts: readonly VirtualHost[];
}

export function patchDomainEntrypoints(ref: DomainRef, settings: DomainEntrypointSettings): Promise<Domain> {
    return http<Domain>(domainPath(ref), { method: 'PATCH', body: JSON.stringify(settings) });
}

export type LoginSettings = ApiModel<LoginSettingsModel>;

/** The Angular `DomainService.patchLoginSettings`. */
export function patchDomainLoginSettings(ref: DomainRef, loginSettings: LoginSettings): Promise<Domain> {
    return http<Domain>(domainPath(ref), { method: 'PATCH', body: JSON.stringify({ loginSettings }) });
}

export type CorsSettings = ApiModel<CorsSettingsModel>;

export type WebProtectionSettings = ApiModel<WebProtectionSettingsModel>;

/** The fields the Angular `DomainService.patchWebProtectionSettings` sends. */
export interface DomainWebProtectionSettings {
    readonly corsSettings: CorsSettings;
    readonly webProtectionSettings: WebProtectionSettings;
}

export function patchDomainWebProtectionSettings(ref: DomainRef, settings: DomainWebProtectionSettings): Promise<Domain> {
    return http<Domain>(domainPath(ref), { method: 'PATCH', body: JSON.stringify(settings) });
}

export function listDomainMembers(ref: DomainRef): Promise<MembershipsResponse> {
    return http<MembershipsResponse>(`${domainPath(ref)}/members`);
}

/** Adds a member to the domain, or changes the role of one. */
export function addDomainMember(ref: DomainRef, member: NewMembership): Promise<unknown> {
    return http<unknown>(`${domainPath(ref)}/members`, { method: 'POST', body: JSON.stringify(member) });
}

export function removeDomainMember(ref: DomainRef, membershipId: string): Promise<void> {
    return http<void>(`${domainPath(ref)}/members/${membershipId}`, { method: 'DELETE' });
}

export function deleteDomain(ref: DomainRef): Promise<void> {
    return http<void>(domainPath(ref), { method: 'DELETE' });
}
