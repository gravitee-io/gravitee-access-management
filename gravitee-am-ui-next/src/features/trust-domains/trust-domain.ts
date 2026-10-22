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
import type { JWKSet } from '@am-models/JWKSet';
import type { TrustedDomain, TrustedDomainRequest } from '../../lib/api/trust-domains';

/**
 * The trusted domain form model and its helpers, from the Angular `domain/settings/trust-domains/trust-domain.types.ts`.
 */
export type TrustDomainUsage = 'SPIFFE' | 'ISSUER' | 'CROSS_APP_ACCESS';

const TRUST_DOMAIN_USAGE_LABELS: Readonly<Record<TrustDomainUsage, string>> = {
    ISSUER: 'Token Exchange',
    SPIFFE: 'SPIFFE',
    CROSS_APP_ACCESS: 'Cross App Access',
};

export type KeyMaterialSource = 'JWKS_URL' | 'JWK_SET' | 'PEM';

export const KEY_MATERIAL_SOURCE_OPTIONS: readonly { value: KeyMaterialSource; label: string }[] = [
    { value: 'JWKS_URL', label: 'JWKS URL' },
    { value: 'JWK_SET', label: 'JWK Set' },
    { value: 'PEM', label: 'PEM Certificate' },
];

export const DEFAULT_KEY_MATERIAL_SOURCE: KeyMaterialSource = 'JWKS_URL';

export interface TrustDomainKeyMaterial {
    readonly source: KeyMaterialSource;
    readonly jwksUrl?: string;
    readonly jwkSet?: JWKSet;
    readonly certificate?: string;
    readonly refreshIntervalSeconds?: number;
}

export interface UserBindingCriterion {
    readonly attribute: string;
    readonly expression: string;
}

export interface CrossAppAccessResourceServer {
    /** Set by AM. Sent back on update, so a renamed resource server keeps its identity. */
    readonly id?: string;
    readonly name: string;
    readonly resource: string;
}

export interface CrossAppAccessSettings {
    readonly enabled?: boolean;
    readonly resourceServers?: readonly CrossAppAccessResourceServer[];
    readonly audSubMapping?: string;
    /** Domain scope to the name this authority knows it by. */
    readonly scopeMappings?: Readonly<Record<string, string>>;
}

export interface TrustDomain {
    readonly id?: string;
    readonly name: string;
    readonly description?: string;
    readonly spiffeTrustDomain?: string;
    readonly issuer?: string;
    readonly tokenExchangeEnabled?: boolean;
    readonly keyMaterial?: TrustDomainKeyMaterial;
    readonly refreshIntervalSeconds?: number;
    readonly allowedAlgorithms?: readonly string[];
    readonly scopeMappings?: Readonly<Record<string, string>>;
    readonly userBindingEnabled?: boolean;
    readonly userBindingCriteria?: readonly UserBindingCriterion[];
    readonly crossAppAccess?: CrossAppAccessSettings;
}

export const DEFAULT_REFRESH_INTERVAL_SECONDS = 300;

export const AUD_SUB_MAPPING_MAX_LENGTH = 512;

export const ISSUER_MAX_LENGTH = 512;

export function toTrustedDomainRequest(trustDomain: TrustDomain): TrustedDomainRequest {
    return {
        name: trustDomain.name,
        description: trustDomain.description,
        keyMaterial: trustDomain.keyMaterial
            ? { ...trustDomain.keyMaterial, refreshIntervalSeconds: trustDomain.refreshIntervalSeconds }
            : undefined,
        domainIdentifier: trustDomain.issuer ?? '',
        spiffe: {
            spiffeTrustDomain: trustDomain.spiffeTrustDomain ?? '',
            allowedAlgorithms: [...(trustDomain.allowedAlgorithms ?? [])],
        },
        tokenExchange: {
            enabled: trustDomain.tokenExchangeEnabled ?? false,
            scopeMappings: { ...trustDomain.scopeMappings },
            userBindingEnabled: trustDomain.userBindingEnabled ?? false,
            userBindingCriteria: [...(trustDomain.userBindingCriteria ?? [])],
        },
        crossAppAccess: trustDomain.crossAppAccess
            ? {
                  ...trustDomain.crossAppAccess,
                  resourceServers: trustDomain.crossAppAccess.resourceServers && [...trustDomain.crossAppAccess.resourceServers],
              }
            : { enabled: false },
    };
}

/** Flattens the API shape into the form model. Angular reads any `tokenExchange` block as token exchange on. */
export function fromTrustedDomain(trustedDomain: TrustedDomain): TrustDomain {
    const keyMaterial = trustedDomain.keyMaterial;
    return {
        id: trustedDomain.id,
        name: trustedDomain.name ?? '',
        description: trustedDomain.description,
        spiffeTrustDomain: trustedDomain.spiffe?.spiffeTrustDomain,
        issuer: trustedDomain.domainIdentifier,
        tokenExchangeEnabled: !!trustedDomain.tokenExchange,
        keyMaterial: keyMaterial ? { ...keyMaterial, source: keyMaterial.source?.toUpperCase() as KeyMaterialSource } : undefined,
        refreshIntervalSeconds: keyMaterial?.refreshIntervalSeconds ?? DEFAULT_REFRESH_INTERVAL_SECONDS,
        allowedAlgorithms: trustedDomain.spiffe?.allowedAlgorithms ?? [],
        scopeMappings: trustedDomain.tokenExchange?.scopeMappings,
        userBindingEnabled: trustedDomain.tokenExchange?.userBindingEnabled ?? false,
        userBindingCriteria: trustedDomain.tokenExchange?.userBindingCriteria?.map(criterion => ({
            attribute: criterion.attribute ?? '',
            expression: criterion.expression ?? '',
        })),
        crossAppAccess: trustedDomain.crossAppAccess && {
            ...trustedDomain.crossAppAccess,
            resourceServers: trustedDomain.crossAppAccess.resourceServers?.map(server => ({
                id: server.id,
                name: server.name ?? '',
                resource: server.resource ?? '',
            })),
        },
    };
}

export function trustDomainUsages(trustDomain: TrustDomain): TrustDomainUsage[] {
    const usages: TrustDomainUsage[] = [];
    if (trustDomain.spiffeTrustDomain) {
        usages.push('SPIFFE');
    }
    if (trustDomain.tokenExchangeEnabled) {
        usages.push('ISSUER');
    }
    if (trustDomain.crossAppAccess?.enabled) {
        usages.push('CROSS_APP_ACCESS');
    }
    return usages;
}

export function trustDomainUsagesLabel(trustDomain: TrustDomain): string {
    return trustDomainUsages(trustDomain)
        .map(usage => TRUST_DOMAIN_USAGE_LABELS[usage])
        .join(', ');
}

const ABSOLUTE_URI_PATTERN = /^[a-zA-Z][a-zA-Z0-9+.-]*:/;

/** The server rule on an audience and a resource identifier: it must carry a scheme. */
export function isAbsoluteUri(value: string | undefined): boolean {
    return ABSOLUTE_URI_PATTERN.test((value ?? '').trim());
}

const ACRONYMS = new Set(['JWKS', 'JWK', 'PEM', 'URL', 'URI', 'ID', 'SPIFFE', 'SVID', 'X509']);

/** Turns a raw enum value such as JWKS_URL into "JWKS URL". */
export function humanizeEnumValue(value: string): string {
    return value
        .split(/[_\s-]+/)
        .filter(word => word.length > 0)
        .map(word => (ACRONYMS.has(word.toUpperCase()) ? word.toUpperCase() : word.charAt(0).toUpperCase() + word.slice(1).toLowerCase()))
        .join(' ');
}

export function keyMaterialSourceLabel(source: string | undefined): string {
    const known = KEY_MATERIAL_SOURCE_OPTIONS.find(option => option.value.toLowerCase() === (source ?? '').toLowerCase());
    return known?.label ?? humanizeEnumValue(source ?? '');
}

const OUTSIDE_LABEL = /[^a-z0-9.-]+/g;
const LABEL_EDGES = /^[.-]+|[.-]+$/g;

export function deriveNameFromIssuer(issuer: string): string {
    return issuer.toLowerCase().replace(OUTSIDE_LABEL, '-').replace(LABEL_EDGES, '').slice(0, 255).replace(LABEL_EDGES, '');
}

const SPIFFE_TRUST_DOMAIN_PATTERN = /^[a-z0-9]([a-z0-9.-]*[a-z0-9])?$/;

export function isValidSpiffeTrustDomain(spiffeTrustDomain: string): boolean {
    return SPIFFE_TRUST_DOMAIN_PATTERN.test(spiffeTrustDomain);
}

export function keyMaterialErrors(keyMaterial: TrustDomainKeyMaterial | undefined): string[] {
    switch (keyMaterial?.source) {
        case 'JWKS_URL':
            return (keyMaterial.jwksUrl ?? '').trim() ? [] : ['JWKS URL is required when the key source is JWKS URL.'];
        case 'JWK_SET':
            return keyMaterial.jwkSet?.keys?.length ? [] : ['The JWK set must contain at least one key.'];
        case 'PEM':
            return (keyMaterial.certificate ?? '').trim() ? [] : ['PEM certificate is required when the key source is PEM Certificate.'];
        default:
            return ['A key source is required.'];
    }
}
