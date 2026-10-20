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
 * @interface Role
 */
export interface Role {
    /**
     *
     * @type {string}
     * @memberof Role
     */
    assignableType?: RoleAssignableTypeEnum;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Role
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof Role
     */
    defaultRole?: boolean;
    /**
     *
     * @type {string}
     * @memberof Role
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof Role
     */
    id?: string;
    /**
     *
     * @type {boolean}
     * @memberof Role
     */
    internalOnly?: boolean;
    /**
     *
     * @type {string}
     * @memberof Role
     */
    name?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof Role
     */
    oauthScopes?: Array<string>;
    /**
     *
     * @type {{ [key: string]: Array<string>; }}
     * @memberof Role
     */
    permissionAcls?: {
        [key: string]: Array<InnerEnum>;
    };
    /**
     *
     * @type {string}
     * @memberof Role
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof Role
     */
    referenceType?: RoleReferenceTypeEnum;
    /**
     *
     * @type {boolean}
     * @memberof Role
     */
    system?: boolean;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Role
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const RoleAssignableTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type RoleAssignableTypeEnum = typeof RoleAssignableTypeEnum[keyof typeof RoleAssignableTypeEnum];
/**
 * @export
 */
export declare const RolePermissionAclsEnum: {
    readonly Create: "CREATE";
    readonly Read: "READ";
    readonly List: "LIST";
    readonly Update: "UPDATE";
    readonly Delete: "DELETE";
};
export type RolePermissionAclsEnum = typeof RolePermissionAclsEnum[keyof typeof RolePermissionAclsEnum];
/**
 * @export
 */
export declare const RoleReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type RoleReferenceTypeEnum = typeof RoleReferenceTypeEnum[keyof typeof RoleReferenceTypeEnum];
/**
 * Check if a given object implements the Role interface.
 */
export declare function instanceOfRole(value: object): value is Role;
export declare function RoleFromJSON(json: any): Role;
export declare function RoleFromJSONTyped(json: any, ignoreDiscriminator: boolean): Role;
export declare function RoleToJSON(json: any): Role;
export declare function RoleToJSONTyped(value?: Role | null, ignoreDiscriminator?: boolean): any;
