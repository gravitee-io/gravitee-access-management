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
 * @interface CertificateKey
 */
export interface CertificateKey {
    /**
     *
     * @type {string}
     * @memberof CertificateKey
     */
    fmt?: string;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof CertificateKey
     */
    metadata?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof CertificateKey
     */
    payload?: string;
}
/**
 * Check if a given object implements the CertificateKey interface.
 */
export declare function instanceOfCertificateKey(value: object): value is CertificateKey;
export declare function CertificateKeyFromJSON(json: any): CertificateKey;
export declare function CertificateKeyFromJSONTyped(json: any, ignoreDiscriminator: boolean): CertificateKey;
export declare function CertificateKeyToJSON(json: any): CertificateKey;
export declare function CertificateKeyToJSONTyped(value?: CertificateKey | null, ignoreDiscriminator?: boolean): any;
