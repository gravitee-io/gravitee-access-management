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
 * @interface NewRole
 */
export interface NewRole {
    /**
     *
     * @type {string}
     * @memberof NewRole
     */
    assignableType?: NewRoleAssignableTypeEnum;
    /**
     *
     * @type {string}
     * @memberof NewRole
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof NewRole
     */
    name: string;
}
/**
 * @export
 */
export declare const NewRoleAssignableTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type NewRoleAssignableTypeEnum = typeof NewRoleAssignableTypeEnum[keyof typeof NewRoleAssignableTypeEnum];
/**
 * Check if a given object implements the NewRole interface.
 */
export declare function instanceOfNewRole(value: object): value is NewRole;
export declare function NewRoleFromJSON(json: any): NewRole;
export declare function NewRoleFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewRole;
export declare function NewRoleToJSON(json: any): NewRole;
export declare function NewRoleToJSONTyped(value?: NewRole | null, ignoreDiscriminator?: boolean): any;
