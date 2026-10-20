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
 * @interface UpdateUserWithId
 */
export interface UpdateUserWithId {
    /**
     *
     * @type {boolean}
     * @memberof UpdateUserWithId
     */
    accountNonExpired?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof UpdateUserWithId
     */
    accountNonLocked?: boolean;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof UpdateUserWithId
     */
    additionalInformation?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof UpdateUserWithId
     */
    client?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UpdateUserWithId
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof UpdateUserWithId
     */
    credentialsNonExpired?: boolean;
    /**
     *
     * @type {string}
     * @memberof UpdateUserWithId
     */
    displayName?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateUserWithId
     */
    email?: string;
    /**
     *
     * @type {boolean}
     * @memberof UpdateUserWithId
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof UpdateUserWithId
     */
    externalId?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateUserWithId
     */
    firstName?: string;
    /**
     *
     * @type {boolean}
     * @memberof UpdateUserWithId
     */
    forceResetPassword?: boolean;
    /**
     *
     * @type {string}
     * @memberof UpdateUserWithId
     */
    id?: string;
    /**
     *
     * @type {string}
     * @memberof UpdateUserWithId
     */
    lastName?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UpdateUserWithId
     */
    loggedAt?: number;
    /**
     *
     * @type {number}
     * @memberof UpdateUserWithId
     */
    loginsCount?: number;
    /**
     *
     * @type {boolean}
     * @memberof UpdateUserWithId
     */
    preRegistration?: boolean;
    /**
     *
     * @type {string}
     * @memberof UpdateUserWithId
     */
    preferredLanguage?: string;
    /**
     *
     * @type {boolean}
     * @memberof UpdateUserWithId
     */
    registrationCompleted?: boolean;
    /**
     *
     * @type {string}
     * @memberof UpdateUserWithId
     */
    source?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UpdateUserWithId
     */
    updatedAt?: number;
}
/**
 * Check if a given object implements the UpdateUserWithId interface.
 */
export declare function instanceOfUpdateUserWithId(value: object): value is UpdateUserWithId;
export declare function UpdateUserWithIdFromJSON(json: any): UpdateUserWithId;
export declare function UpdateUserWithIdFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateUserWithId;
export declare function UpdateUserWithIdToJSON(json: any): UpdateUserWithId;
export declare function UpdateUserWithIdToJSONTyped(value?: UpdateUserWithId | null, ignoreDiscriminator?: boolean): any;
