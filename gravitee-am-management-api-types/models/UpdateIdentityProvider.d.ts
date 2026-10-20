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
 * @interface UpdateIdentityProvider
 */
export interface UpdateIdentityProvider {
    /**
     *
     * @type {string}
     * @memberof UpdateIdentityProvider
     */
    configuration: string;
    /**
     *
     * @type {Array<string>}
     * @memberof UpdateIdentityProvider
     */
    domainWhitelist?: Array<string>;
    /**
     *
     * @type {{ [key: string]: Array<string>; }}
     * @memberof UpdateIdentityProvider
     */
    groupMapper?: {
        [key: string]: Array<string>;
    };
    /**
     *
     * @type {{ [key: string]: string; }}
     * @memberof UpdateIdentityProvider
     */
    mappers?: {
        [key: string]: string;
    };
    /**
     *
     * @type {string}
     * @memberof UpdateIdentityProvider
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof UpdateIdentityProvider
     */
    passwordPolicy?: string;
    /**
     *
     * @type {{ [key: string]: Array<string>; }}
     * @memberof UpdateIdentityProvider
     */
    roleMapper?: {
        [key: string]: Array<string>;
    };
    /**
     *
     * @type {string}
     * @memberof UpdateIdentityProvider
     */
    type: string;
}
/**
 * Check if a given object implements the UpdateIdentityProvider interface.
 */
export declare function instanceOfUpdateIdentityProvider(value: object): value is UpdateIdentityProvider;
export declare function UpdateIdentityProviderFromJSON(json: any): UpdateIdentityProvider;
export declare function UpdateIdentityProviderFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateIdentityProvider;
export declare function UpdateIdentityProviderToJSON(json: any): UpdateIdentityProvider;
export declare function UpdateIdentityProviderToJSONTyped(value?: UpdateIdentityProvider | null, ignoreDiscriminator?: boolean): any;
