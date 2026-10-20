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
 * @interface RoleEntity
 */
export interface RoleEntity {
    /**
     *
     * @type {string}
     * @memberof RoleEntity
     */
    assignableType?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof RoleEntity
     */
    availablePermissions?: Array<string>;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof RoleEntity
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof RoleEntity
     */
    defaultRole?: boolean;
    /**
     *
     * @type {string}
     * @memberof RoleEntity
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof RoleEntity
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof RoleEntity
     */
    name?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof RoleEntity
     */
    permissions?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof RoleEntity
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof RoleEntity
     */
    referenceType?: RoleEntityReferenceTypeEnum;
    /**
     *
     * @type {boolean}
     * @memberof RoleEntity
     */
    system?: boolean;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof RoleEntity
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const RoleEntityReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type RoleEntityReferenceTypeEnum = typeof RoleEntityReferenceTypeEnum[keyof typeof RoleEntityReferenceTypeEnum];
/**
 * Check if a given object implements the RoleEntity interface.
 */
export declare function instanceOfRoleEntity(value: object): value is RoleEntity;
export declare function RoleEntityFromJSON(json: any): RoleEntity;
export declare function RoleEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): RoleEntity;
export declare function RoleEntityToJSON(json: any): RoleEntity;
export declare function RoleEntityToJSONTyped(value?: RoleEntity | null, ignoreDiscriminator?: boolean): any;
