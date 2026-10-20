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
 * @interface FilteredIdentityProviderInfo
 */
export interface FilteredIdentityProviderInfo {
    /**
     *
     * @type {boolean}
     * @memberof FilteredIdentityProviderInfo
     */
    external?: boolean;
    /**
     *
     * @type {string}
     * @memberof FilteredIdentityProviderInfo
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof FilteredIdentityProviderInfo
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof FilteredIdentityProviderInfo
     */
    passwordPolicy?: string;
    /**
     *
     * @type {boolean}
     * @memberof FilteredIdentityProviderInfo
     */
    system?: boolean;
    /**
     *
     * @type {string}
     * @memberof FilteredIdentityProviderInfo
     */
    type?: string;
}
/**
 * Check if a given object implements the FilteredIdentityProviderInfo interface.
 */
export declare function instanceOfFilteredIdentityProviderInfo(value: object): value is FilteredIdentityProviderInfo;
export declare function FilteredIdentityProviderInfoFromJSON(json: any): FilteredIdentityProviderInfo;
export declare function FilteredIdentityProviderInfoFromJSONTyped(json: any, ignoreDiscriminator: boolean): FilteredIdentityProviderInfo;
export declare function FilteredIdentityProviderInfoToJSON(json: any): FilteredIdentityProviderInfo;
export declare function FilteredIdentityProviderInfoToJSONTyped(value?: FilteredIdentityProviderInfo | null, ignoreDiscriminator?: boolean): any;
