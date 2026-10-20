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
 * @interface PatchApplicationSAMLSettings
 */
export interface PatchApplicationSAMLSettings {
    /**
     *
     * @type {Array<SAMLAssertionAttribute>}
     * @memberof PatchApplicationSAMLSettings
     */
    assertionAttributes?: Array<SAMLAssertionAttribute>;
    /**
     *
     * @type {number}
     * @memberof PatchApplicationSAMLSettings
     */
    assertionValiditySeconds?: number;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationSAMLSettings
     */
    attributeConsumeServiceUrl?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof PatchApplicationSAMLSettings
     */
    audiences?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationSAMLSettings
     */
    certificate?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationSAMLSettings
     */
    dataEncryptionAlgorithm?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationSAMLSettings
     */
    entityId?: string;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationSAMLSettings
     */
    includeAssertionConditions?: boolean;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationSAMLSettings
     */
    keyTransportEncryptionAlgorithm?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationSAMLSettings
     */
    nameIdMapping?: string;
    /**
     *
     * @type {number}
     * @memberof PatchApplicationSAMLSettings
     */
    notBeforeTimeSkewSeconds?: number;
    /**
     *
     * @type {number}
     * @memberof PatchApplicationSAMLSettings
     */
    notOnOrAfterTimeSkewSeconds?: number;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationSAMLSettings
     */
    responseBinding?: string;
    /**
     *
     * @type {string}
     * @memberof PatchApplicationSAMLSettings
     */
    singleLogoutServiceUrl?: string;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationSAMLSettings
     */
    wantAssertionsEncrypted?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationSAMLSettings
     */
    wantAssertionsSigned?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof PatchApplicationSAMLSettings
     */
    wantResponseSigned?: boolean;
}
/**
 * Check if a given object implements the PatchApplicationSAMLSettings interface.
 */
export declare function instanceOfPatchApplicationSAMLSettings(value: object): value is PatchApplicationSAMLSettings;
export declare function PatchApplicationSAMLSettingsFromJSON(json: any): PatchApplicationSAMLSettings;
export declare function PatchApplicationSAMLSettingsFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchApplicationSAMLSettings;
export declare function PatchApplicationSAMLSettingsToJSON(json: any): PatchApplicationSAMLSettings;
export declare function PatchApplicationSAMLSettingsToJSONTyped(value?: PatchApplicationSAMLSettings | null, ignoreDiscriminator?: boolean): any;
