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
/**
 *
 * @export
 * @interface CertificateCredential
 */
export interface CertificateCredential {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof CertificateCredential
     */
    accessedAt?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof CertificateCredential
     */
    certificateExpiresAt?: number;
    /**
     *
     * @type {string}
     * @memberof CertificateCredential
     */
    certificateIssuerDN?: string;
    /**
     *
     * @type {string}
     * @memberof CertificateCredential
     */
    certificatePem?: string;
    /**
     *
     * @type {string}
     * @memberof CertificateCredential
     */
    certificateSerialNumber?: string;
    /**
     *
     * @type {string}
     * @memberof CertificateCredential
     */
    certificateSubjectDN?: string;
    /**
     *
     * @type {string}
     * @memberof CertificateCredential
     */
    certificateThumbprint?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof CertificateCredential
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof CertificateCredential
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof CertificateCredential
     */
    ipAddress?: string;
    /**
     *
     * @type {{ [key: string]: string; }}
     * @memberof CertificateCredential
     */
    metadata?: {
        [key: string]: string;
    };
    /**
     *
     * @type {string}
     * @memberof CertificateCredential
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof CertificateCredential
     */
    referenceType?: CertificateCredentialReferenceTypeEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof CertificateCredential
     */
    updatedAt?: number;
    /**
     *
     * @type {string}
     * @memberof CertificateCredential
     */
    userAgent?: string;
    /**
     *
     * @type {string}
     * @memberof CertificateCredential
     */
    userId?: string;
    /**
     *
     * @type {string}
     * @memberof CertificateCredential
     */
    username?: string;
}
/**
 * @export
 */
export declare const CertificateCredentialReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type CertificateCredentialReferenceTypeEnum = typeof CertificateCredentialReferenceTypeEnum[keyof typeof CertificateCredentialReferenceTypeEnum];
/**
 * Check if a given object implements the CertificateCredential interface.
 */
export declare function instanceOfCertificateCredential(value: object): value is CertificateCredential;
export declare function CertificateCredentialFromJSON(json: any): CertificateCredential;
export declare function CertificateCredentialFromJSONTyped(json: any, ignoreDiscriminator: boolean): CertificateCredential;
export declare function CertificateCredentialToJSON(json: any): CertificateCredential;
export declare function CertificateCredentialToJSONTyped(value?: CertificateCredential | null, ignoreDiscriminator?: boolean): any;
