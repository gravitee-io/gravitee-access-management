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
import type { User } from './User';
/**
 *
 * @export
 * @interface UserPage
 */
export interface UserPage {
    /**
     *
     * @type {number}
     * @memberof UserPage
     */
    currentPage?: number;
    /**
     *
     * @type {Array<User>}
     * @memberof UserPage
     */
    data?: Array<User>;
    /**
     *
     * @type {number}
     * @memberof UserPage
     */
    totalCount?: number;
}
/**
 * Check if a given object implements the UserPage interface.
 */
export declare function instanceOfUserPage(value: object): value is UserPage;
export declare function UserPageFromJSON(json: any): UserPage;
export declare function UserPageFromJSONTyped(json: any, ignoreDiscriminator: boolean): UserPage;
export declare function UserPageToJSON(json: any): UserPage;
export declare function UserPageToJSONTyped(value?: UserPage | null, ignoreDiscriminator?: boolean): any;
