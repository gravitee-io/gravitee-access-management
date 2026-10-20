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
import type { ManagedBy } from './ManagedBy';
/**
 *
 * @export
 * @interface IdentityProvider
 */
export interface IdentityProvider {
    /**
     *
     * @type {string}
     * @memberof IdentityProvider
     */
    configuration?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof IdentityProvider
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof IdentityProvider
     */
    dataPlaneId?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof IdentityProvider
     */
    domainWhitelist?: Array<string>;
    /**
     *
     * @type {boolean}
     * @memberof IdentityProvider
     */
    external?: boolean;
    /**
     *
     * @type {{ [key: string]: Array<string>; }}
     * @memberof IdentityProvider
     */
    groupMapper?: {
        [key: string]: Array<string>;
    };
    /**
     *
     * @type {string}
     * @memberof IdentityProvider
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof IdentityProvider
     */
    readonly key?: string;
    /**
     *
     * @type {ManagedBy}
     * @memberof IdentityProvider
     */
    managedBy?: ManagedBy;
    /**
     *
     * @type {{ [key: string]: string; }}
     * @memberof IdentityProvider
     */
    mappers?: {
        [key: string]: string;
    };
    /**
     *
     * @type {string}
     * @memberof IdentityProvider
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof IdentityProvider
     */
    passwordPolicy?: string;
    /**
     *
     * @type {string}
     * @memberof IdentityProvider
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof IdentityProvider
     */
    referenceType?: IdentityProviderReferenceTypeEnum;
    /**
     *
     * @type {{ [key: string]: Array<string>; }}
     * @memberof IdentityProvider
     */
    roleMapper?: {
        [key: string]: Array<string>;
    };
    /**
     *
     * @type {boolean}
     * @memberof IdentityProvider
     */
    system?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof IdentityProvider
     */
    systemClusterRestricted?: boolean;
    /**
     *
     * @type {string}
     * @memberof IdentityProvider
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof IdentityProvider
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const IdentityProviderReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type IdentityProviderReferenceTypeEnum = typeof IdentityProviderReferenceTypeEnum[keyof typeof IdentityProviderReferenceTypeEnum];
/**
 * Check if a given object implements the IdentityProvider interface.
 */
export declare function instanceOfIdentityProvider(value: object): value is IdentityProvider;
export declare function IdentityProviderFromJSON(json: any): IdentityProvider;
export declare function IdentityProviderFromJSONTyped(json: any, ignoreDiscriminator: boolean): IdentityProvider;
export declare function IdentityProviderToJSON(json: any): IdentityProvider;
export declare function IdentityProviderToJSONTyped(value?: Omit<IdentityProvider, 'key'> | null, ignoreDiscriminator?: boolean): any;
