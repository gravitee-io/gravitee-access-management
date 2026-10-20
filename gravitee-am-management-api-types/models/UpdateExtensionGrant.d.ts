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
 * @interface UpdateExtensionGrant
 */
export interface UpdateExtensionGrant {
    /**
     *
     * @type {string}
     * @memberof UpdateExtensionGrant
     */
    configuration: string;
    /**
     *
     * @type {boolean}
     * @memberof UpdateExtensionGrant
     */
    createUser?: boolean;
    /**
     *
     * @type {string}
     * @memberof UpdateExtensionGrant
     */
    grantType?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateExtensionGrant
     */
    identityProvider?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateExtensionGrant
     */
    name: string;
    /**
     *
     * @type {string}
     * @memberof UpdateExtensionGrant
     */
    type: string;
    /**
     *
     * @type {boolean}
     * @memberof UpdateExtensionGrant
     */
    userExists?: boolean;
}
/**
 * Check if a given object implements the UpdateExtensionGrant interface.
 */
export declare function instanceOfUpdateExtensionGrant(value: object): value is UpdateExtensionGrant;
export declare function UpdateExtensionGrantFromJSON(json: any): UpdateExtensionGrant;
export declare function UpdateExtensionGrantFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateExtensionGrant;
export declare function UpdateExtensionGrantToJSON(json: any): UpdateExtensionGrant;
export declare function UpdateExtensionGrantToJSONTyped(value?: UpdateExtensionGrant | null, ignoreDiscriminator?: boolean): any;
