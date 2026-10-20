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
 * @interface Group
 */
export interface Group {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Group
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof Group
     */
    description?: string;
    /**
     *
     * @type {string}
     * @memberof Group
     */
    id?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof Group
     */
    members?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof Group
     */
    name?: string;
    /**
     *
     * @type {string}
     * @memberof Group
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof Group
     */
    referenceType?: GroupReferenceTypeEnum;
    /**
     *
     * @type {Array<string>}
     * @memberof Group
     */
    roles?: Array<string>;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Group
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const GroupReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type GroupReferenceTypeEnum = typeof GroupReferenceTypeEnum[keyof typeof GroupReferenceTypeEnum];
/**
 * Check if a given object implements the Group interface.
 */
export declare function instanceOfGroup(value: object): value is Group;
export declare function GroupFromJSON(json: any): Group;
export declare function GroupFromJSONTyped(json: any, ignoreDiscriminator: boolean): Group;
export declare function GroupToJSON(json: any): Group;
export declare function GroupToJSONTyped(value?: Group | null, ignoreDiscriminator?: boolean): any;
