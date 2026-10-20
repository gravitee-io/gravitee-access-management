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
 * @interface ModifiedCertificateEntity
 */
export interface ModifiedCertificateEntity {
    /**
     *
     * @type {string}
     * @memberof ModifiedCertificateEntity
     */
    configuration?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ModifiedCertificateEntity
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof ModifiedCertificateEntity
     */
    domain?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ModifiedCertificateEntity
     */
    expiresAt?: number;
    /**
     *
     * @type {string}
     * @memberof ModifiedCertificateEntity
     */
    id?: string;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof ModifiedCertificateEntity
     */
    metadata?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof ModifiedCertificateEntity
     */
    name?: string;
    /**
     *
     * @type {boolean}
     * @memberof ModifiedCertificateEntity
     */
    system?: boolean;
    /**
     *
     * @type {string}
     * @memberof ModifiedCertificateEntity
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof ModifiedCertificateEntity
     */
    updatedAt?: number;
}
/**
 * Check if a given object implements the ModifiedCertificateEntity interface.
 */
export declare function instanceOfModifiedCertificateEntity(value: object): value is ModifiedCertificateEntity;
export declare function ModifiedCertificateEntityFromJSON(json: any): ModifiedCertificateEntity;
export declare function ModifiedCertificateEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): ModifiedCertificateEntity;
export declare function ModifiedCertificateEntityToJSON(json: any): ModifiedCertificateEntity;
export declare function ModifiedCertificateEntityToJSONTyped(value?: ModifiedCertificateEntity | null, ignoreDiscriminator?: boolean): any;
