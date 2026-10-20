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
import { type DomainRef, domainPath, environmentPath, searchParam } from './paths';

export interface VirtualHost {
    readonly host: string;
    readonly path: string;
    readonly overrideEntrypoint?: boolean;
}

export interface CertificateSettings {
    /** The id of the certificate used when the selected one fails to load or sign. */
    readonly fallbackCertificate?: string | null;
}

export interface Domain {
    readonly id: string;
    readonly hrid: string;
    readonly name: string;
    readonly description?: string;
    readonly enabled: boolean;
    readonly path?: string;
    readonly vhostMode?: boolean;
    readonly vhosts?: readonly VirtualHost[];
    readonly createdAt?: number;
    readonly updatedAt?: number;
    readonly certificateSettings?: CertificateSettings;
}

export function listDomains(organizationId: string, environmentId: string, page: number, size: number, query = ''): Promise<Page<Domain>> {
    return http<Page<Domain>>(`${environmentPath(organizationId, environmentId)}/domains?page=${page}&size=${size}${searchParam(query)}`);
}

export function getDomainByHrid(organizationId: string, environmentId: string, hrid: string): Promise<Domain> {
    return http<Domain>(`${environmentPath(organizationId, environmentId)}/domains/_hrid/${hrid}`);
}

export interface Entrypoint {
    readonly id: string;
    readonly url: string;
    readonly defaultEntrypoint?: boolean;
}

export function listDomainEntrypoints(ref: DomainRef): Promise<Entrypoint[]> {
    return http<Entrypoint[]>(`${domainPath(ref)}/entrypoints`);
}

export function updateCertificateSettings(ref: DomainRef, certificateSettings: CertificateSettings): Promise<Domain> {
    return http<Domain>(`${domainPath(ref)}/certificate-settings`, { method: 'PUT', body: JSON.stringify(certificateSettings) });
}
