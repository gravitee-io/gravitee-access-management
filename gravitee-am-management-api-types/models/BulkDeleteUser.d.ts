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
 * @interface BulkDeleteUser
 */
export interface BulkDeleteUser {
    /**
     *
     * @type {string}
     * @memberof BulkDeleteUser
     */
    action: BulkDeleteUserActionEnum;
    /**
     *
     * @type {number}
     * @memberof BulkDeleteUser
     */
    failOnErrors?: number;
    /**
     *
     * @type {Array<string>}
     * @memberof BulkDeleteUser
     */
    items: Array<string>;
}
/**
 * @export
 */
export declare const BulkDeleteUserActionEnum: {
    readonly Create: "CREATE";
    readonly Update: "UPDATE";
    readonly Delete: "DELETE";
};
export type BulkDeleteUserActionEnum = typeof BulkDeleteUserActionEnum[keyof typeof BulkDeleteUserActionEnum];
/**
 * Check if a given object implements the BulkDeleteUser interface.
 */
export declare function instanceOfBulkDeleteUser(value: object): value is BulkDeleteUser;
export declare function BulkDeleteUserFromJSON(json: any): BulkDeleteUser;
export declare function BulkDeleteUserFromJSONTyped(json: any, ignoreDiscriminator: boolean): BulkDeleteUser;
export declare function BulkDeleteUserToJSON(json: any): BulkDeleteUser;
export declare function BulkDeleteUserToJSONTyped(value?: BulkDeleteUser | null, ignoreDiscriminator?: boolean): any;
