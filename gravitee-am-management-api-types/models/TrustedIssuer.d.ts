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
/**
 * An external token issuer whose JWTs are accepted as subject or actor tokens during token exchange, validated with the configured key material.
 * @export
 * @interface TrustedIssuer
 */
export interface TrustedIssuer {
    /**
     * PEM-encoded X.509 certificate. Required when keyResolutionMethod is PEM.
     * @type {string}
     * @memberof TrustedIssuer
     */
    certificate?: string;
    /**
     * Expected value of the "iss" claim in the external JWT.
     * @type {string}
     * @memberof TrustedIssuer
     */
    issuer?: string;
    /**
     * JWKS endpoint URL. Required when keyResolutionMethod is JWKS_URL.
     * @type {string}
     * @memberof TrustedIssuer
     */
    jwksUri?: string;
    /**
     * How the issuer's signing key is resolved. JWKS_URL fetches keys from a JWKS endpoint; PEM uses an inline X.509 certificate.
     * @type {string}
     * @memberof TrustedIssuer
     */
    keyResolutionMethod?: TrustedIssuerKeyResolutionMethodEnum;
    /**
     * One-to-one mapping from external scope to domain scope. Unmapped issuer scopes are dropped (fail-closed).
     * @type {{ [key: string]: string; }}
     * @memberof TrustedIssuer
     */
    scopeMappings?: {
        [key: string]: string;
    };
    /**
     * Criteria used to locate a domain user when user binding is enabled. All criteria are combined with AND.
     * @type {Array<UserBindingCriterion>}
     * @memberof TrustedIssuer
     */
    userBindingCriteria?: Array<UserBindingCriterion>;
    /**
     * Whether the external JWT subject is resolved to a single domain user using the user binding criteria. When false, a virtual user is built from the token claims only.
     * @type {boolean}
     * @memberof TrustedIssuer
     */
    userBindingEnabled?: boolean;
}
/**
 * @export
 */
export declare const TrustedIssuerKeyResolutionMethodEnum: {
    readonly JwksUrl: "JWKS_URL";
    readonly Pem: "PEM";
};
export type TrustedIssuerKeyResolutionMethodEnum = typeof TrustedIssuerKeyResolutionMethodEnum[keyof typeof TrustedIssuerKeyResolutionMethodEnum];
/**
 * Check if a given object implements the TrustedIssuer interface.
 */
export declare function instanceOfTrustedIssuer(value: object): value is TrustedIssuer;
export declare function TrustedIssuerFromJSON(json: any): TrustedIssuer;
export declare function TrustedIssuerFromJSONTyped(json: any, ignoreDiscriminator: boolean): TrustedIssuer;
export declare function TrustedIssuerToJSON(json: any): TrustedIssuer;
export declare function TrustedIssuerToJSONTyped(value?: TrustedIssuer | null, ignoreDiscriminator?: boolean): any;
