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
 * @interface NewExtensionGrant
 */
export interface NewExtensionGrant {
    /**
     *
     * @type {string}
     * @memberof NewExtensionGrant
     */
    configuration: string;
    /**
     *
     * @type {boolean}
     * @memberof NewExtensionGrant
     */
    createUser?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewExtensionGrant
     */
    grantType: string;
    /**
     *
     * @type {string}
     * @memberof NewExtensionGrant
     */
    identityProvider?: string;
    /**
     *
     * @type {string}
     * @memberof NewExtensionGrant
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof NewExtensionGrant
     */
    type: string;
    /**
     *
     * @type {boolean}
     * @memberof NewExtensionGrant
     */
    userExists?: boolean;
}
/**
 * Check if a given object implements the NewExtensionGrant interface.
 */
export declare function instanceOfNewExtensionGrant(value: object): value is NewExtensionGrant;
export declare function NewExtensionGrantFromJSON(json: any): NewExtensionGrant;
export declare function NewExtensionGrantFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewExtensionGrant;
export declare function NewExtensionGrantToJSON(json: any): NewExtensionGrant;
export declare function NewExtensionGrantToJSONTyped(value?: NewExtensionGrant | null, ignoreDiscriminator?: boolean): any;
