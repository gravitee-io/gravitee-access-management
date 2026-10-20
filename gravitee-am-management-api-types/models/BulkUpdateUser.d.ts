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
import type { UpdateUserWithId } from './UpdateUserWithId';
/**
 *
 * @export
 * @interface BulkUpdateUser
 */
export interface BulkUpdateUser {
    /**
     *
     * @type {string}
     * @memberof BulkUpdateUser
     */
    action: BulkUpdateUserActionEnum;
    /**
     *
     * @type {number}
     * @memberof BulkUpdateUser
     */
    failOnErrors?: number;
    /**
     *
     * @type {Array<UpdateUserWithId>}
     * @memberof BulkUpdateUser
     */
    items: Array<UpdateUserWithId>;
}
/**
 * @export
 */
export declare const BulkUpdateUserActionEnum: {
    readonly Create: "CREATE";
    readonly Update: "UPDATE";
    readonly Delete: "DELETE";
};
export type BulkUpdateUserActionEnum = typeof BulkUpdateUserActionEnum[keyof typeof BulkUpdateUserActionEnum];
/**
 * Check if a given object implements the BulkUpdateUser interface.
 */
export declare function instanceOfBulkUpdateUser(value: object): value is BulkUpdateUser;
export declare function BulkUpdateUserFromJSON(json: any): BulkUpdateUser;
export declare function BulkUpdateUserFromJSONTyped(json: any, ignoreDiscriminator: boolean): BulkUpdateUser;
export declare function BulkUpdateUserToJSON(json: any): BulkUpdateUser;
export declare function BulkUpdateUserToJSONTyped(value?: BulkUpdateUser | null, ignoreDiscriminator?: boolean): any;
