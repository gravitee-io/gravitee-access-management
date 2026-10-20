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
import type { IdentityProvider } from './IdentityProvider';
import type { Application } from './Application';
/**
 *
 * @export
 * @interface CertificateEntity
 */
export interface CertificateEntity {
    /**
     *
     * @type {Array<Application>}
     * @memberof CertificateEntity
     */
    applications?: Array<Application>;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof CertificateEntity
     */
    createdAt?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof CertificateEntity
     */
    expiresAt?: number;
    /**
     *
     * @type {string}
     * @memberof CertificateEntity
     */
    id?: string;
    /**
     *
     * @type {Array<IdentityProvider>}
     * @memberof CertificateEntity
     */
    identityProviders?: Array<IdentityProvider>;
    /**
     *
     * @type {string}
     * @memberof CertificateEntity
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof CertificateEntity
     */
    status?: CertificateEntityStatusEnum;
    /**
     *
     * @type {boolean}
     * @memberof CertificateEntity
     */
    system?: boolean;
    /**
     *
     * @type {string}
     * @memberof CertificateEntity
     */
    type?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof CertificateEntity
     */
    usage?: Array<string>;
}
/**
 * @export
 */
export declare const CertificateEntityStatusEnum: {
    readonly Valid: "VALID";
    readonly WillExpire: "WILL_EXPIRE";
    readonly Expired: "EXPIRED";
    readonly Renewed: "RENEWED";
};
export type CertificateEntityStatusEnum = typeof CertificateEntityStatusEnum[keyof typeof CertificateEntityStatusEnum];
/**
 * Check if a given object implements the CertificateEntity interface.
 */
export declare function instanceOfCertificateEntity(value: object): value is CertificateEntity;
export declare function CertificateEntityFromJSON(json: any): CertificateEntity;
export declare function CertificateEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): CertificateEntity;
export declare function CertificateEntityToJSON(json: any): CertificateEntity;
export declare function CertificateEntityToJSONTyped(value?: CertificateEntity | null, ignoreDiscriminator?: boolean): any;
