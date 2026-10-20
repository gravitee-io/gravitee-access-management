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
 * @interface ApplicationIdentityProvider
 */
export interface ApplicationIdentityProvider {
    /**
     *
     * @type {string}
     * @memberof ApplicationIdentityProvider
     */
    identity?: string;
    /**
     *
     * @type {number}
     * @memberof ApplicationIdentityProvider
     */
    priority?: number;
    /**
     *
     * @type {string}
     * @memberof ApplicationIdentityProvider
     */
    selectionRule?: string;
}
/**
 * Check if a given object implements the ApplicationIdentityProvider interface.
 */
export declare function instanceOfApplicationIdentityProvider(value: object): value is ApplicationIdentityProvider;
export declare function ApplicationIdentityProviderFromJSON(json: any): ApplicationIdentityProvider;
export declare function ApplicationIdentityProviderFromJSONTyped(json: any, ignoreDiscriminator: boolean): ApplicationIdentityProvider;
export declare function ApplicationIdentityProviderToJSON(json: any): ApplicationIdentityProvider;
export declare function ApplicationIdentityProviderToJSONTyped(value?: ApplicationIdentityProvider | null, ignoreDiscriminator?: boolean): any;
