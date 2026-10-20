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
 * @interface CertificateSettings
 */
export interface CertificateSettings {
    /**
     *
     * @type {string}
     * @memberof CertificateSettings
     */
    fallbackCertificate?: string;
}
/**
 * Check if a given object implements the CertificateSettings interface.
 */
export declare function instanceOfCertificateSettings(value: object): value is CertificateSettings;
export declare function CertificateSettingsFromJSON(json: any): CertificateSettings;
export declare function CertificateSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): CertificateSettings;
export declare function CertificateSettingsToJSON(json: any): CertificateSettings;
export declare function CertificateSettingsToJSONTyped(value?: CertificateSettings | null, ignoreDiscriminator?: boolean): any;
