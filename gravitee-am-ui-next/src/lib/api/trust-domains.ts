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
import type { NewTrustedDomain } from '@am-models/NewTrustedDomain';
import type { TrustDomainKeyMaterial } from '@am-models/TrustDomainKeyMaterial';
import type { TrustedDomain as TrustedDomainModel } from '@am-models/TrustedDomain';
import { http } from './http';
import type { ApiModel } from './model';
import { type DomainRef, domainPath } from './paths';

/** The key source is lowercase on the wire, for example `jwk_set`, where the spec has `JWK_SET`. */
export type TrustedDomainKeyMaterial = ApiModel<Omit<TrustDomainKeyMaterial, 'source'> & { readonly source?: string }>;

export type TrustedDomain = ApiModel<Omit<TrustedDomainModel, 'keyMaterial'> & { readonly keyMaterial?: TrustedDomainKeyMaterial }, 'id'>;

/** The body of a create and of an update, built by the Angular `toTrustedDomainRequest`. */
export type TrustedDomainRequest = ApiModel<NewTrustedDomain>;

function trustedDomainsPath(ref: DomainRef): string {
    return `${domainPath(ref)}/trusted-domains`;
}

export function listTrustedDomains(ref: DomainRef): Promise<TrustedDomain[]> {
    return http<TrustedDomain[]>(trustedDomainsPath(ref));
}

export function getTrustedDomain(ref: DomainRef, id: string): Promise<TrustedDomain> {
    return http<TrustedDomain>(`${trustedDomainsPath(ref)}/${id}`);
}

export function createTrustedDomain(ref: DomainRef, trustedDomain: TrustedDomainRequest): Promise<TrustedDomain> {
    return http<TrustedDomain>(trustedDomainsPath(ref), { method: 'POST', body: JSON.stringify(trustedDomain) });
}

export function updateTrustedDomain(ref: DomainRef, id: string, trustedDomain: TrustedDomainRequest): Promise<TrustedDomain> {
    return http<TrustedDomain>(`${trustedDomainsPath(ref)}/${id}`, { method: 'PUT', body: JSON.stringify(trustedDomain) });
}

export function deleteTrustedDomain(ref: DomainRef, id: string): Promise<void> {
    return http<void>(`${trustedDomainsPath(ref)}/${id}`, { method: 'DELETE' });
}
