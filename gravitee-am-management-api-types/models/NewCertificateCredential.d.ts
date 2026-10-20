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
 * @interface NewCertificateCredential
 */
export interface NewCertificateCredential {
    /**
     * The certificate in PEM format
     * @type {string}
     * @memberof NewCertificateCredential
     */
    certificatePem: string;
}
/**
 * Check if a given object implements the NewCertificateCredential interface.
 */
export declare function instanceOfNewCertificateCredential(value: object): value is NewCertificateCredential;
export declare function NewCertificateCredentialFromJSON(json: any): NewCertificateCredential;
export declare function NewCertificateCredentialFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewCertificateCredential;
export declare function NewCertificateCredentialToJSON(json: any): NewCertificateCredential;
export declare function NewCertificateCredentialToJSONTyped(value?: NewCertificateCredential | null, ignoreDiscriminator?: boolean): any;
