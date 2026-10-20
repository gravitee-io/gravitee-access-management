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
import type { SAMLAssertionAttribute } from './SAMLAssertionAttribute';
/**
 *
 * @export
 * @interface ApplicationSAMLSettings
 */
export interface ApplicationSAMLSettings {
    /**
     *
     * @type {Array<SAMLAssertionAttribute>}
     * @memberof ApplicationSAMLSettings
     */
    assertionAttributes?: Array<SAMLAssertionAttribute>;
    /**
     *
     * @type {number}
     * @memberof ApplicationSAMLSettings
     */
    assertionValiditySeconds?: number;
    /**
     *
     * @type {string}
     * @memberof ApplicationSAMLSettings
     */
    attributeConsumeServiceUrl?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof ApplicationSAMLSettings
     */
    audiences?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof ApplicationSAMLSettings
     */
    certificate?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationSAMLSettings
     */
    dataEncryptionAlgorithm?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationSAMLSettings
     */
    entityId?: string;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationSAMLSettings
     */
    includeAssertionConditions?: boolean;
    /**
     *
     * @type {string}
     * @memberof ApplicationSAMLSettings
     */
    keyTransportEncryptionAlgorithm?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationSAMLSettings
     */
    nameIdMapping?: string;
    /**
     *
     * @type {number}
     * @memberof ApplicationSAMLSettings
     */
    notBeforeTimeSkewSeconds?: number;
    /**
     *
     * @type {number}
     * @memberof ApplicationSAMLSettings
     */
    notOnOrAfterTimeSkewSeconds?: number;
    /**
     *
     * @type {string}
     * @memberof ApplicationSAMLSettings
     */
    responseBinding?: string;
    /**
     *
     * @type {string}
     * @memberof ApplicationSAMLSettings
     */
    singleLogoutServiceUrl?: string;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationSAMLSettings
     */
    wantAssertionsEncrypted?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationSAMLSettings
     */
    wantAssertionsSigned?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof ApplicationSAMLSettings
     */
    wantResponseSigned?: boolean;
}
/**
 * Check if a given object implements the ApplicationSAMLSettings interface.
 */
export declare function instanceOfApplicationSAMLSettings(value: object): value is ApplicationSAMLSettings;
export declare function ApplicationSAMLSettingsFromJSON(json: any): ApplicationSAMLSettings;
export declare function ApplicationSAMLSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationSAMLSettings;
export declare function ApplicationSAMLSettingsToJSON(json: any): ApplicationSAMLSettings;
export declare function ApplicationSAMLSettingsToJSONTyped(value?: ApplicationSAMLSettings | null, ignoreDiscriminator?: boolean): any;
