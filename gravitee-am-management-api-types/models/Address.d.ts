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
 * @interface Address
 */
export interface Address {
    /**
     *
     * @type {string}
     * @memberof Address
     */
    country?: string;
    /**
     *
     * @type {string}
     * @memberof Address
     */
    formatted?: string;
    /**
     *
     * @type {string}
     * @memberof Address
     */
    locality?: string;
    /**
     *
     * @type {string}
     * @memberof Address
     */
    postalCode?: string;
    /**
     *
     * @type {boolean}
     * @memberof Address
     */
    primary?: boolean;
    /**
     *
     * @type {string}
     * @memberof Address
     */
    region?: string;
    /**
     *
     * @type {string}
     * @memberof Address
     */
    streetAddress?: string;
    /**
     *
     * @type {string}
     * @memberof Address
     */
    type?: string;
}
/**
 * Check if a given object implements the Address interface.
 */
export declare function instanceOfAddress(value: object): value is Address;
export declare function AddressFromJSON(json: any): Address;
export declare function AddressFromJSONTyped(json: any, ignoreDiscriminator: boolean): Address;
export declare function AddressToJSON(json: any): Address;
export declare function AddressToJSONTyped(value?: Address | null, ignoreDiscriminator?: boolean): any;
