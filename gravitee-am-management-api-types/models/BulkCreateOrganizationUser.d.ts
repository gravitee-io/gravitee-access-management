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
import type { NewOrganizationUser } from './NewOrganizationUser';
/**
 *
 * @export
 * @interface BulkCreateOrganizationUser
 */
export interface BulkCreateOrganizationUser {
    /**
     *
     * @type {string}
     * @memberof BulkCreateOrganizationUser
     */
    action: BulkCreateOrganizationUserActionEnum;
    /**
     *
     * @type {number}
     * @memberof BulkCreateOrganizationUser
     */
    failOnErrors?: number;
    /**
     *
     * @type {Array<NewOrganizationUser>}
     * @memberof BulkCreateOrganizationUser
     */
    items: Array<NewOrganizationUser>;
}
/**
 * @export
 */
export declare const BulkCreateOrganizationUserActionEnum: {
    readonly Create: "CREATE";
    readonly Update: "UPDATE";
    readonly Delete: "DELETE";
};
export type BulkCreateOrganizationUserActionEnum = typeof BulkCreateOrganizationUserActionEnum[keyof typeof BulkCreateOrganizationUserActionEnum];
/**
 * Check if a given object implements the BulkCreateOrganizationUser interface.
 */
export declare function instanceOfBulkCreateOrganizationUser(value: object): value is BulkCreateOrganizationUser;
export declare function BulkCreateOrganizationUserFromJSON(json: any): BulkCreateOrganizationUser;
export declare function BulkCreateOrganizationUserFromJSONTyped(json: any, ignoreDiscriminator: boolean): BulkCreateOrganizationUser;
export declare function BulkCreateOrganizationUserToJSON(json: any): BulkCreateOrganizationUser;
export declare function BulkCreateOrganizationUserToJSONTyped(value?: BulkCreateOrganizationUser | null, ignoreDiscriminator?: boolean): any;
