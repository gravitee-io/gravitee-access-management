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
 * @interface ScopeEntity
 */
export interface ScopeEntity {
    /**
     *
     * @type {string}
     * @memberof ScopeEntity
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof ScopeEntity
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof ScopeEntity
     */
    key?: string;
    /**
     *
     * @type {string}
     * @memberof ScopeEntity
     */
    name?: string;
}
/**
 * Check if a given object implements the ScopeEntity interface.
 */
export declare function instanceOfScopeEntity(value: object): value is ScopeEntity;
export declare function ScopeEntityFromJSON(json: any): ScopeEntity;
export declare function ScopeEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): ScopeEntity;
export declare function ScopeEntityToJSON(json: any): ScopeEntity;
export declare function ScopeEntityToJSONTyped(value?: ScopeEntity | null, ignoreDiscriminator?: boolean): any;
