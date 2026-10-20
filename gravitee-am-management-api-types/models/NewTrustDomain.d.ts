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
import type { UserBindingCriterion } from './UserBindingCriterion';
import type { TrustDomainKeyMaterial } from './TrustDomainKeyMaterial';
/**
 *
 * @export
 * @interface NewTrustDomain
 */
export interface NewTrustDomain {
    /**
     *
     * @type {Array<string>}
     * @memberof NewTrustDomain
     */
    allowedAlgorithms?: Array<string>;
    /**
     * Use keyMaterial.source instead.
     * @type {string}
     * @memberof NewTrustDomain
     * @deprecated
     */
    bundleSource?: NewTrustDomainBundleSourceEnum;
    /**
     *
     * @type {string}
     * @memberof NewTrustDomain
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof NewTrustDomain
     */
    issuer?: string;
    /**
     * Use keyMaterial.jwksUrl instead.
     * @type {string}
     * @memberof NewTrustDomain
     * @deprecated
     */
    jwksUrl?: string;
    /**
     *
     * @type {TrustDomainKeyMaterial}
     * @memberof NewTrustDomain
     */
    keyMaterial?: TrustDomainKeyMaterial;
    /**
     *
     * @type {string}
     * @memberof NewTrustDomain
     */
    name?: string;
    /**
     *
     * @type {number}
     * @memberof NewTrustDomain
     */
    refreshIntervalSeconds?: number;
    /**
     *
     * @type {{ [key: string]: string; }}
     * @memberof NewTrustDomain
     */
    scopeMappings?: {
        [key: string]: string;
    };
    /**
     * SPIFFE trust domain matched against the "sub" of a JWT-SVID. Defaults to the name when no matcher is supplied.
     * @type {string}
     * @memberof NewTrustDomain
     */
    spiffeTrustDomain?: string;
    /**
     *
     * @type {Array<UserBindingCriterion>}
     * @memberof NewTrustDomain
     */
    userBindingCriteria?: Array<UserBindingCriterion>;
    /**
     *
     * @type {boolean}
     * @memberof NewTrustDomain
     */
    userBindingEnabled?: boolean;
}
/**
 * @export
 */
export declare const NewTrustDomainBundleSourceEnum: {
    readonly JwksUrl: "JWKS_URL";
    readonly StaticJwks: "STATIC_JWKS";
};
export type NewTrustDomainBundleSourceEnum = typeof NewTrustDomainBundleSourceEnum[keyof typeof NewTrustDomainBundleSourceEnum];
/**
 * Check if a given object implements the NewTrustDomain interface.
 */
export declare function instanceOfNewTrustDomain(value: object): value is NewTrustDomain;
export declare function NewTrustDomainFromJSON(json: any): NewTrustDomain;
export declare function NewTrustDomainFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewTrustDomain;
export declare function NewTrustDomainToJSON(json: any): NewTrustDomain;
export declare function NewTrustDomainToJSONTyped(value?: NewTrustDomain | null, ignoreDiscriminator?: boolean): any;
