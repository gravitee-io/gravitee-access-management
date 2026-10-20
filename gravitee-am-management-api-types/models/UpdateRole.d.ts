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
 * @interface UpdateRole
 */
export interface UpdateRole {
    /**
     *
     * @type {string}
     * @memberof UpdateRole
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateRole
     */
    name: string;
    /**
     *
     * @type {Array<string>}
     * @memberof UpdateRole
     */
    oauthScopes?: Array<string>;
    /**
     *
     * @type {Array<string>}
     * @memberof UpdateRole
     */
    permissions?: Array<string>;
}
/**
 * Check if a given object implements the UpdateRole interface.
 */
export declare function instanceOfUpdateRole(value: object): value is UpdateRole;
export declare function UpdateRoleFromJSON(json: any): UpdateRole;
export declare function UpdateRoleFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateRole;
export declare function UpdateRoleToJSON(json: any): UpdateRole;
export declare function UpdateRoleToJSONTyped(value?: UpdateRole | null, ignoreDiscriminator?: boolean): any;
