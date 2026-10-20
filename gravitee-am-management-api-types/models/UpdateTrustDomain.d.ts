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
 * @interface UpdateTrustDomain
 */
export interface UpdateTrustDomain {
    /**
     *
     * @type {Array<string>}
     * @memberof UpdateTrustDomain
     */
    allowedAlgorithms?: Array<string>;
    /**
     * Use keyMaterial.source instead.
     * @type {string}
     * @memberof UpdateTrustDomain
     * @deprecated
     */
    bundleSource?: UpdateTrustDomainBundleSourceEnum;
    /**
     *
     * @type {string}
     * @memberof UpdateTrustDomain
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateTrustDomain
     */
    issuer?: string;
    /**
     * Use keyMaterial.jwksUrl instead.
     * @type {string}
     * @memberof UpdateTrustDomain
     * @deprecated
     */
    jwksUrl?: string;
    /**
     *
     * @type {TrustDomainKeyMaterial}
     * @memberof UpdateTrustDomain
     */
    keyMaterial?: TrustDomainKeyMaterial;
    /**
     * New label for the trusted domain. Left unchanged when absent.
     * @type {string}
     * @memberof UpdateTrustDomain
     */
    name?: string;
    /**
     *
     * @type {number}
     * @memberof UpdateTrustDomain
     */
    refreshIntervalSeconds?: number;
    /**
     *
     * @type {{ [key: string]: string; }}
     * @memberof UpdateTrustDomain
     */
    scopeMappings?: {
        [key: string]: string;
    };
    /**
     * SPIFFE trust domain matched against the "sub" of a JWT-SVID. Supplying either matcher replaces both; supplying neither leaves them unchanged.
     * @type {string}
     * @memberof UpdateTrustDomain
     */
    spiffeTrustDomain?: string;
    /**
     *
     * @type {Array<UserBindingCriterion>}
     * @memberof UpdateTrustDomain
     */
    userBindingCriteria?: Array<UserBindingCriterion>;
    /**
     *
     * @type {boolean}
     * @memberof UpdateTrustDomain
     */
    userBindingEnabled?: boolean;
}
/**
 * @export
 */
export declare const UpdateTrustDomainBundleSourceEnum: {
    readonly JwksUrl: "JWKS_URL";
    readonly StaticJwks: "STATIC_JWKS";
};
export type UpdateTrustDomainBundleSourceEnum = typeof UpdateTrustDomainBundleSourceEnum[keyof typeof UpdateTrustDomainBundleSourceEnum];
/**
 * Check if a given object implements the UpdateTrustDomain interface.
 */
export declare function instanceOfUpdateTrustDomain(value: object): value is UpdateTrustDomain;
export declare function UpdateTrustDomainFromJSON(json: any): UpdateTrustDomain;
export declare function UpdateTrustDomainFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateTrustDomain;
export declare function UpdateTrustDomainToJSON(json: any): UpdateTrustDomain;
export declare function UpdateTrustDomainToJSONTyped(value?: UpdateTrustDomain | null, ignoreDiscriminator?: boolean): any;
