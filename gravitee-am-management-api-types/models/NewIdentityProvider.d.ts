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
 * @interface NewIdentityProvider
 */
export interface NewIdentityProvider {
    /**
     *
     * @type {string}
     * @memberof NewIdentityProvider
     */
    configuration: string;
    /**
     *
     * @type {Array<string>}
     * @memberof NewIdentityProvider
     */
    domainWhitelist?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof NewIdentityProvider
     */
    external?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewIdentityProvider
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof NewIdentityProvider
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof NewIdentityProvider
     */
    type: string;
}
/**
 * Check if a given object implements the NewIdentityProvider interface.
 */
export declare function instanceOfNewIdentityProvider(value: object): value is NewIdentityProvider;
export declare function NewIdentityProviderFromJSON(json: any): NewIdentityProvider;
export declare function NewIdentityProviderFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewIdentityProvider;
export declare function NewIdentityProviderToJSON(json: any): NewIdentityProvider;
export declare function NewIdentityProviderToJSONTyped(value?: NewIdentityProvider | null, ignoreDiscriminator?: boolean): any;
