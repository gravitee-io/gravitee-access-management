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
 * @interface NewScope
 */
export interface NewScope {
    /**
     *
     * @type {string}
     * @memberof NewScope
     */
    description: string;
    /**
     *
     * @type {boolean}
     * @memberof NewScope
     */
    discovery?: boolean;
    /**
     *
     * @type {number}
     * @memberof NewScope
     */
    expiresIn?: number;
    /**
     *
     * @type {string}
     * @memberof NewScope
     */
    iconUri?: string;
    /**
     *
     * @type {string}
     * @memberof NewScope
     */
    key: string;
    /**
     *
     * @type {string}
     * @memberof NewScope
     */
    name: string;
    /**
     *
     * @type {boolean}
     * @memberof NewScope
     */
    parameterized?: boolean;
}
/**
 * Check if a given object implements the NewScope interface.
 */
export declare function instanceOfNewScope(value: object): value is NewScope;
export declare function NewScopeFromJSON(json: any): NewScope;
export declare function NewScopeFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewScope;
export declare function NewScopeToJSON(json: any): NewScope;
export declare function NewScopeToJSONTyped(value?: NewScope | null, ignoreDiscriminator?: boolean): any;
