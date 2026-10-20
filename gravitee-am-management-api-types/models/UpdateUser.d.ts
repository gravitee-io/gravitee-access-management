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
 * @interface UpdateUser
 */
export interface UpdateUser {
    /**
     *
     * @type {boolean}
     * @memberof UpdateUser
     */
    accountNonExpired?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof UpdateUser
     */
    accountNonLocked?: boolean;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof UpdateUser
     */
    additionalInformation?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof UpdateUser
     */
    client?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UpdateUser
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof UpdateUser
     */
    credentialsNonExpired?: boolean;
    /**
     *
     * @type {string}
     * @memberof UpdateUser
     */
    displayName?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateUser
     */
    email?: string;
    /**
     *
     * @type {boolean}
     * @memberof UpdateUser
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof UpdateUser
     */
    externalId?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateUser
     */
    firstName?: string;
    /**
     *
     * @type {boolean}
     * @memberof UpdateUser
     */
    forceResetPassword?: boolean;
    /**
     *
     * @type {string}
     * @memberof UpdateUser
     */
    lastName?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UpdateUser
     */
    loggedAt?: number;
    /**
     *
     * @type {number}
     * @memberof UpdateUser
     */
    loginsCount?: number;
    /**
     *
     * @type {boolean}
     * @memberof UpdateUser
     */
    preRegistration?: boolean;
    /**
     *
     * @type {string}
     * @memberof UpdateUser
     */
    preferredLanguage?: string;
    /**
     *
     * @type {boolean}
     * @memberof UpdateUser
     */
    registrationCompleted?: boolean;
    /**
     *
     * @type {string}
     * @memberof UpdateUser
     */
    source?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UpdateUser
     */
    updatedAt?: number;
}
/**
 * Check if a given object implements the UpdateUser interface.
 */
export declare function instanceOfUpdateUser(value: object): value is UpdateUser;
export declare function UpdateUserFromJSON(json: any): UpdateUser;
export declare function UpdateUserFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateUser;
export declare function UpdateUserToJSON(json: any): UpdateUser;
export declare function UpdateUserToJSONTyped(value?: UpdateUser | null, ignoreDiscriminator?: boolean): any;
