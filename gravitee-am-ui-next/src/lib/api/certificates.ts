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
import { type DomainRef, domainPath } from './paths';
import type { Plugin } from './plugins';

export interface CertificateUsage {
    readonly id: string;
    readonly name: string;
}

export type CertificateStatus = 'valid' | 'will_expire' | 'expired' | 'renewed';

export interface Certificate {
    readonly id: string;
    readonly name: string;
    readonly type: string;
    /** `enc`, `sig` or `mtls`. */
    readonly usage?: readonly string[];
    readonly system?: boolean;
    readonly status?: CertificateStatus;
    /** Epoch milliseconds. */
    readonly expiresAt?: number;
    readonly applications?: readonly CertificateUsage[];
    readonly identityProviders?: readonly CertificateUsage[];
}

export interface CertificateDetail {
    readonly id: string;
    readonly name: string;
    readonly type: string;
    readonly system?: boolean;
    /** Plugin configuration, serialized as a JSON string. */
    readonly configuration: string;
}

export interface CertificateKey {
    readonly fmt: string;
    readonly payload: string;
}

export interface CertificateBody {
    readonly name: string;
    readonly type: string;
    /** Plugin configuration, serialized as a JSON string. */
    readonly configuration: string;
}

export function listCertificates(ref: DomainRef): Promise<Certificate[]> {
    return http<Certificate[]>(`${domainPath(ref)}/certificates`);
}

export function getCertificate(ref: DomainRef, certificateId: string): Promise<CertificateDetail> {
    return http<CertificateDetail>(`${domainPath(ref)}/certificates/${certificateId}`);
}

export function createCertificate(ref: DomainRef, certificate: CertificateBody): Promise<CertificateDetail> {
    return http<CertificateDetail>(`${domainPath(ref)}/certificates`, { method: 'POST', body: JSON.stringify(certificate) });
}

export function updateCertificate(ref: DomainRef, certificateId: string, certificate: CertificateBody): Promise<CertificateDetail> {
    const { name, type, configuration } = certificate;
    return http<CertificateDetail>(`${domainPath(ref)}/certificates/${certificateId}`, {
        method: 'PUT',
        body: JSON.stringify({ name, type, configuration }),
    });
}

export function deleteCertificate(ref: DomainRef, certificateId: string): Promise<void> {
    return http<void>(`${domainPath(ref)}/certificates/${certificateId}`, { method: 'DELETE' });
}

export function listCertificateKeys(ref: DomainRef, certificateId: string): Promise<CertificateKey[]> {
    return http<CertificateKey[]>(`${domainPath(ref)}/certificates/${certificateId}/keys`);
}

export function rotateCertificate(ref: DomainRef): Promise<Certificate> {
    return http<Certificate>(`${domainPath(ref)}/certificates/rotate`, { method: 'POST' });
}

export function listCertificatePlugins(): Promise<Plugin[]> {
    return http<Plugin[]>('/platform/plugins/certificates');
}

export function getCertificateSchema(type: string): Promise<Record<string, unknown>> {
    return http<Record<string, unknown>>(`/platform/plugins/certificates/${type}/schema`);
}
