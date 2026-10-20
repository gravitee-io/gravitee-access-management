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
 * @interface Credential
 */
export interface Credential {
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    aaguid?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Credential
     */
    accessedAt?: number;
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    attestationStatement?: string;
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    attestationStatementFormat?: string;
    /**
     *
     * @type {number}
     * @memberof Credential
     */
    counter?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Credential
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    credentialId?: string;
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    deviceName?: string;
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    ipAddress?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Credential
     */
    lastCheckedAt?: number;
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    publicKey?: string;
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    referenceType?: CredentialReferenceTypeEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Credential
     */
    updatedAt?: number;
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    userAgent?: string;
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    userId?: string;
    /**
     *
     * @type {string}
     * @memberof Credential
     */
    username?: string;
}
/**
 * @export
 */
export declare const CredentialReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type CredentialReferenceTypeEnum = typeof CredentialReferenceTypeEnum[keyof typeof CredentialReferenceTypeEnum];
/**
 * Check if a given object implements the Credential interface.
 */
export declare function instanceOfCredential(value: object): value is Credential;
export declare function CredentialFromJSON(json: any): Credential;
export declare function CredentialFromJSONTyped(json: any, ignoreDiscriminator: boolean): Credential;
export declare function CredentialToJSON(json: any): Credential;
export declare function CredentialToJSONTyped(value?: Credential | null, ignoreDiscriminator?: boolean): any;
