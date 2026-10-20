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
import type { Role } from './Role';
/**
 *
 * @export
 * @interface RolePage
 */
export interface RolePage {
    /**
     *
     * @type {number}
     * @memberof RolePage
     */
    currentPage?: number;
    /**
     *
     * @type {Array<Role>}
     * @memberof RolePage
     */
    data?: Array<Role>;
    /**
     *
     * @type {number}
     * @memberof RolePage
     */
    totalCount?: number;
}
/**
 * Check if a given object implements the RolePage interface.
 */
export declare function instanceOfRolePage(value: object): value is RolePage;
export declare function RolePageFromJSON(json: any): RolePage;
export declare function RolePageFromJSONTyped(json: any, ignoreDiscriminator: boolean): RolePage;
export declare function RolePageToJSON(json: any): RolePage;
export declare function RolePageToJSONTyped(value?: RolePage | null, ignoreDiscriminator?: boolean): any;
