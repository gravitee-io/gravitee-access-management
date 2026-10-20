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
import type { CertificateEntity } from '@am-models/CertificateEntity';
import type { CertificateKey as CertificateKeyModel } from '@am-models/CertificateKey';
import type { ModifiedCertificateEntity } from '@am-models/ModifiedCertificateEntity';
import type { NewCertificate } from '@am-models/NewCertificate';
import { http } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';
import type { Plugin } from './plugins';

export interface CertificateUsage {
    readonly id: string;
    readonly name: string;
}

export type CertificateStatus = 'valid' | 'will_expire' | 'expired' | 'renewed';

/** `usage` holds `enc`, `sig` or `mtls`. */
export type Certificate = ApiModel<
    Omit<CertificateEntity, 'status' | 'applications' | 'identityProviders'> & {
        status?: CertificateStatus;
        applications?: readonly CertificateUsage[];
        identityProviders?: readonly CertificateUsage[];
    },
    'id' | 'name' | 'type'
>;

/**
 * `GET .../certificates/:id` returns a `ModifiedCertificateEntity`, although its `@Schema` names `CertificateEntity`.
 * `configuration` is the plugin configuration, serialized as a JSON string.
 */
export type CertificateDetail = ApiModel<ModifiedCertificateEntity, 'id' | 'name' | 'type' | 'configuration'>;

export type CertificateKey = ApiModel<CertificateKeyModel, 'fmt' | 'payload'>;

export type CertificateBody = ApiModel<NewCertificate>;

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
