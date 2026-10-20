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
 * @interface Certificate
 */
export interface Certificate {
    /**
     *
     * @type {string}
     * @memberof Certificate
     */
    value?: string;
}
/**
 * Check if a given object implements the Certificate interface.
 */
export declare function instanceOfCertificate(value: object): value is Certificate;
export declare function CertificateFromJSON(json: any): Certificate;
export declare function CertificateFromJSONTyped(json: any, ignoreDiscriminator: boolean): Certificate;
export declare function CertificateToJSON(json: any): Certificate;
export declare function CertificateToJSONTyped(value?: Certificate | null, ignoreDiscriminator?: boolean): any;
