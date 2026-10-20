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
import type { NewUser } from './NewUser';
/**
 *
 * @export
 * @interface BulkCreateUser
 */
export interface BulkCreateUser {
    /**
     *
     * @type {string}
     * @memberof BulkCreateUser
     */
    action: BulkCreateUserActionEnum;
    /**
     *
     * @type {number}
     * @memberof BulkCreateUser
     */
    failOnErrors?: number;
    /**
     *
     * @type {Array<NewUser>}
     * @memberof BulkCreateUser
     */
    items: Array<NewUser>;
}
/**
 * @export
 */
export declare const BulkCreateUserActionEnum: {
    readonly Create: "CREATE";
    readonly Update: "UPDATE";
    readonly Delete: "DELETE";
};
export type BulkCreateUserActionEnum = typeof BulkCreateUserActionEnum[keyof typeof BulkCreateUserActionEnum];
/**
 * Check if a given object implements the BulkCreateUser interface.
 */
export declare function instanceOfBulkCreateUser(value: object): value is BulkCreateUser;
export declare function BulkCreateUserFromJSON(json: any): BulkCreateUser;
export declare function BulkCreateUserFromJSONTyped(json: any, ignoreDiscriminator: boolean): BulkCreateUser;
export declare function BulkCreateUserToJSON(json: any): BulkCreateUser;
export declare function BulkCreateUserToJSONTyped(value?: BulkCreateUser | null, ignoreDiscriminator?: boolean): any;
