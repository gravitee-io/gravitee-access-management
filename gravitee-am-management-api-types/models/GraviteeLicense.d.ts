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
 * @interface GraviteeLicense
 */
export interface GraviteeLicense {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof GraviteeLicense
     */
    expiresAt?: number;
    /**
     *
     * @type {Array<string>}
     * @memberof GraviteeLicense
     */
    features?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof GraviteeLicense
     */
    isExpired?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof GraviteeLicense
     */
    packs?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof GraviteeLicense
     */
    scope?: string;
    /**
     *
     * @type {string}
     * @memberof GraviteeLicense
     */
    tier?: string;
}
/**
 * Check if a given object implements the GraviteeLicense interface.
 */
export declare function instanceOfGraviteeLicense(value: object): value is GraviteeLicense;
export declare function GraviteeLicenseFromJSON(json: any): GraviteeLicense;
export declare function GraviteeLicenseFromJSONTyped(json: any, ignoreDiscriminator: boolean): GraviteeLicense;
export declare function GraviteeLicenseToJSON(json: any): GraviteeLicense;
export declare function GraviteeLicenseToJSONTyped(value?: GraviteeLicense | null, ignoreDiscriminator?: boolean): any;
