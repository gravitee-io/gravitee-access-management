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
 * @interface UpdateCertificate
 */
export interface UpdateCertificate {
    /**
     *
     * @type {string}
     * @memberof UpdateCertificate
     */
    configuration: string;
    /**
     *
     * @type {string}
     * @memberof UpdateCertificate
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof UpdateCertificate
     */
    type: string;
}
/**
 * Check if a given object implements the UpdateCertificate interface.
 */
export declare function instanceOfUpdateCertificate(value: object): value is UpdateCertificate;
export declare function UpdateCertificateFromJSON(json: any): UpdateCertificate;
export declare function UpdateCertificateFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateCertificate;
export declare function UpdateCertificateToJSON(json: any): UpdateCertificate;
export declare function UpdateCertificateToJSONTyped(value?: UpdateCertificate | null, ignoreDiscriminator?: boolean): any;
