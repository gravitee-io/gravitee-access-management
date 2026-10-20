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
 * @interface UpdateScope
 */
export interface UpdateScope {
    /**
     *
     * @type {string}
     * @memberof UpdateScope
     */
    description: string;
    /**
     *
     * @type {boolean}
     * @memberof UpdateScope
     */
    discovery?: boolean;
    /**
     *
     * @type {number}
     * @memberof UpdateScope
     */
    expiresIn?: number;
    /**
     *
     * @type {string}
     * @memberof UpdateScope
     */
    iconUri?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateScope
     */
    name: string;
    /**
     *
     * @type {boolean}
     * @memberof UpdateScope
     */
    parameterized?: boolean;
}
/**
 * Check if a given object implements the UpdateScope interface.
 */
export declare function instanceOfUpdateScope(value: object): value is UpdateScope;
export declare function UpdateScopeFromJSON(json: any): UpdateScope;
export declare function UpdateScopeFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateScope;
export declare function UpdateScopeToJSON(json: any): UpdateScope;
export declare function UpdateScopeToJSONTyped(value?: UpdateScope | null, ignoreDiscriminator?: boolean): any;
