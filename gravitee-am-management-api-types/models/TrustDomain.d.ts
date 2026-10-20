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
import type { JWKSet } from './JWKSet';
import type { UserBindingCriterion } from './UserBindingCriterion';
import type { TrustDomainKeyMaterial } from './TrustDomainKeyMaterial';
/**
 *
 * @export
 * @interface TrustDomain
 */
export interface TrustDomain {
    /**
     *
     * @type {Array<string>}
     * @memberof TrustDomain
     */
    allowedAlgorithms?: Array<string>;
    /**
     * Use keyMaterial.source instead. Null when the key material is a PEM certificate.
     * @type {string}
     * @memberof TrustDomain
     * @deprecated
     */
    bundleSource?: TrustDomainBundleSourceEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof TrustDomain
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof TrustDomain
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof TrustDomain
     */
    id?: string;
    /**
     * Expected value of the "iss" claim in an external JWT. Required to accept tokens during an RFC 8693 exchange.
     * @type {string}
     * @memberof TrustDomain
     */
    issuer?: string;
    /**
     * Use keyMaterial.jwksUrl instead.
     * @type {string}
     * @memberof TrustDomain
     * @deprecated
     */
    jwksUrl?: string;
    /**
     *
     * @type {TrustDomainKeyMaterial}
     * @memberof TrustDomain
     */
    keyMaterial?: TrustDomainKeyMaterial;
    /**
     * Label the trusted domain is known by. Unique within the security domain.
     * @type {string}
     * @memberof TrustDomain
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof TrustDomain
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof TrustDomain
     */
    referenceType?: TrustDomainReferenceTypeEnum;
    /**
     *
     * @type {number}
     * @memberof TrustDomain
     */
    refreshIntervalSeconds?: number;
    /**
     * One-to-one mapping from external scope to domain scope. Unmapped issuer scopes are dropped (fail-closed). Applies to tokens matched by "issuer".
     * @type {{ [key: string]: string; }}
     * @memberof TrustDomain
     */
    scopeMappings?: {
        [key: string]: string;
    };
    /**
     * SPIFFE trust domain matched against the "sub" of a JWT-SVID, without the "spiffe://" scheme. Required to accept SPIFFE client assertions.
     * @type {string}
     * @memberof TrustDomain
     */
    spiffeTrustDomain?: string;
    /**
     *
     * @type {JWKSet}
     * @memberof TrustDomain
     */
    staticJwks?: JWKSet;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof TrustDomain
     */
    updatedAt?: number;
    /**
     * Criteria used to locate a domain user when user binding is enabled. All criteria are combined with AND.
     * @type {Array<UserBindingCriterion>}
     * @memberof TrustDomain
     */
    userBindingCriteria?: Array<UserBindingCriterion>;
    /**
     * Whether the external JWT subject is resolved to a single domain user using the user binding criteria. When false, a virtual user is built from the token claims only.
     * @type {boolean}
     * @memberof TrustDomain
     */
    userBindingEnabled?: boolean;
}
/**
 * @export
 */
export declare const TrustDomainBundleSourceEnum: {
    readonly JwksUrl: "JWKS_URL";
    readonly StaticJwks: "STATIC_JWKS";
};
export type TrustDomainBundleSourceEnum = typeof TrustDomainBundleSourceEnum[keyof typeof TrustDomainBundleSourceEnum];
/**
 * @export
 */
export declare const TrustDomainReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type TrustDomainReferenceTypeEnum = typeof TrustDomainReferenceTypeEnum[keyof typeof TrustDomainReferenceTypeEnum];
/**
 * Check if a given object implements the TrustDomain interface.
 */
export declare function instanceOfTrustDomain(value: object): value is TrustDomain;
export declare function TrustDomainFromJSON(json: any): TrustDomain;
export declare function TrustDomainFromJSONTyped(json: any, ignoreDiscriminator: boolean): TrustDomain;
export declare function TrustDomainToJSON(json: any): TrustDomain;
export declare function TrustDomainToJSONTyped(value?: TrustDomain | null, ignoreDiscriminator?: boolean): any;
