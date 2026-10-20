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
 * @interface PatchScope
 */
export interface PatchScope {
    /**
     *
     * @type {string}
     * @memberof PatchScope
     */
    description?: string;
    /**
     *
     * @type {boolean}
     * @memberof PatchScope
     */
    discovery?: boolean;
    /**
     *
     * @type {number}
     * @memberof PatchScope
     */
    expiresIn?: number;
    /**
     *
     * @type {string}
     * @memberof PatchScope
     */
    iconUri?: string;
    /**
     *
     * @type {string}
     * @memberof PatchScope
     */
    name?: string;
    /**
     *
     * @type {boolean}
     * @memberof PatchScope
     */
    parameterized?: boolean;
}
/**
 * Check if a given object implements the PatchScope interface.
 */
export declare function instanceOfPatchScope(value: object): value is PatchScope;
export declare function PatchScopeFromJSON(json: any): PatchScope;
export declare function PatchScopeFromJSONTyped(json: any, ignoreDiscriminator: boolean): PatchScope;
export declare function PatchScopeToJSON(json: any): PatchScope;
export declare function PatchScopeToJSONTyped(value?: PatchScope | null, ignoreDiscriminator?: boolean): any;
