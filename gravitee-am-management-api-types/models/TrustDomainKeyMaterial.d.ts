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
/**
 * Where the trusted domain's signing keys come from. Exactly one source applies.
 * @export
 * @interface TrustDomainKeyMaterial
 */
export interface TrustDomainKeyMaterial {
    /**
     * PEM-encoded X.509 certificate. Required when source is PEM.
     * @type {string}
     * @memberof TrustDomainKeyMaterial
     */
    certificate?: string;
    /**
     *
     * @type {JWKSet}
     * @memberof TrustDomainKeyMaterial
     */
    jwkSet?: JWKSet;
    /**
     * JWKS endpoint URL. Required when source is JWKS_URL.
     * @type {string}
     * @memberof TrustDomainKeyMaterial
     */
    jwksUrl?: string;
    /**
     * Which of the three key sources this trusted domain uses.
     * @type {string}
     * @memberof TrustDomainKeyMaterial
     */
    source?: TrustDomainKeyMaterialSourceEnum;
}
/**
 * @export
 */
export declare const TrustDomainKeyMaterialSourceEnum: {
    readonly JwksUrl: "JWKS_URL";
    readonly JwkSet: "JWK_SET";
    readonly Pem: "PEM";
};
export type TrustDomainKeyMaterialSourceEnum = typeof TrustDomainKeyMaterialSourceEnum[keyof typeof TrustDomainKeyMaterialSourceEnum];
/**
 * Check if a given object implements the TrustDomainKeyMaterial interface.
 */
export declare function instanceOfTrustDomainKeyMaterial(value: object): value is TrustDomainKeyMaterial;
export declare function TrustDomainKeyMaterialFromJSON(json: any): TrustDomainKeyMaterial;
export declare function TrustDomainKeyMaterialFromJSONTyped(json: any, ignoreDiscriminator: boolean): TrustDomainKeyMaterial;
export declare function TrustDomainKeyMaterialToJSON(json: any): TrustDomainKeyMaterial;
export declare function TrustDomainKeyMaterialToJSONTyped(value?: TrustDomainKeyMaterial | null, ignoreDiscriminator?: boolean): any;
