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
 * @interface NewUser
 */
export interface NewUser {
    /**
     *
     * @type {boolean}
     * @memberof NewUser
     */
    accountNonExpired?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof NewUser
     */
    accountNonLocked?: boolean;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof NewUser
     */
    additionalInformation?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof NewUser
     */
    client?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof NewUser
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof NewUser
     */
    credentialsNonExpired?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewUser
     */
    domain?: string;
    /**
     *
     * @type {string}
     * @memberof NewUser
     */
    email?: string;
    /**
     *
     * @type {boolean}
     * @memberof NewUser
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewUser
     */
    externalId?: string;
    /**
     *
     * @type {string}
     * @memberof NewUser
     */
    firstName?: string;
    /**
     *
     * @type {boolean}
     * @memberof NewUser
     */
    forceResetPassword?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof NewUser
     */
    internal?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewUser
     */
    lastName?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof NewUser
     */
    lastPasswordReset?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof NewUser
     */
    loggedAt?: number;
    /**
     *
     * @type {number}
     * @memberof NewUser
     */
    loginsCount?: number;
    /**
     *
     * @type {string}
     * @memberof NewUser
     */
    password?: string;
    /**
     *
     * @type {boolean}
     * @memberof NewUser
     */
    preRegistration?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewUser
     */
    preferredLanguage?: string;
    /**
     *
     * @type {boolean}
     * @memberof NewUser
     */
    registrationCompleted?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewUser
     */
    source?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof NewUser
     */
    updatedAt?: number;
    /**
     *
     * @type {string}
     * @memberof NewUser
     */
    username: string;
}
/**
 * Check if a given object implements the NewUser interface.
 */
export declare function instanceOfNewUser(value: object): value is NewUser;
export declare function NewUserFromJSON(json: any): NewUser;
export declare function NewUserFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewUser;
export declare function NewUserToJSON(json: any): NewUser;
export declare function NewUserToJSONTyped(value?: NewUser | null, ignoreDiscriminator?: boolean): any;
