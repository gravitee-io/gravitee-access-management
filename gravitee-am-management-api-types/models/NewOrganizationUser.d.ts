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
 * @interface NewOrganizationUser
 */
export interface NewOrganizationUser {
    /**
     *
     * @type {boolean}
     * @memberof NewOrganizationUser
     */
    accountNonExpired?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof NewOrganizationUser
     */
    accountNonLocked?: boolean;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof NewOrganizationUser
     */
    additionalInformation?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof NewOrganizationUser
     */
    client?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof NewOrganizationUser
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof NewOrganizationUser
     */
    credentialsNonExpired?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewOrganizationUser
     */
    domain?: string;
    /**
     *
     * @type {string}
     * @memberof NewOrganizationUser
     */
    email?: string;
    /**
     *
     * @type {boolean}
     * @memberof NewOrganizationUser
     */
    enabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewOrganizationUser
     */
    externalId?: string;
    /**
     *
     * @type {string}
     * @memberof NewOrganizationUser
     */
    firstName?: string;
    /**
     *
     * @type {boolean}
     * @memberof NewOrganizationUser
     */
    forceResetPassword?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof NewOrganizationUser
     */
    internal?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewOrganizationUser
     */
    lastName?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof NewOrganizationUser
     */
    lastPasswordReset?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof NewOrganizationUser
     */
    loggedAt?: number;
    /**
     *
     * @type {number}
     * @memberof NewOrganizationUser
     */
    loginsCount?: number;
    /**
     *
     * @type {string}
     * @memberof NewOrganizationUser
     */
    password?: string;
    /**
     *
     * @type {boolean}
     * @memberof NewOrganizationUser
     */
    preRegistration?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewOrganizationUser
     */
    preferredLanguage?: string;
    /**
     *
     * @type {boolean}
     * @memberof NewOrganizationUser
     */
    registrationCompleted?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof NewOrganizationUser
     */
    serviceAccount?: boolean;
    /**
     *
     * @type {string}
     * @memberof NewOrganizationUser
     */
    source?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof NewOrganizationUser
     */
    updatedAt?: number;
    /**
     *
     * @type {string}
     * @memberof NewOrganizationUser
     */
    username: string;
}
/**
 * Check if a given object implements the NewOrganizationUser interface.
 */
export declare function instanceOfNewOrganizationUser(value: object): value is NewOrganizationUser;
export declare function NewOrganizationUserFromJSON(json: any): NewOrganizationUser;
export declare function NewOrganizationUserFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewOrganizationUser;
export declare function NewOrganizationUserToJSON(json: any): NewOrganizationUser;
export declare function NewOrganizationUserToJSONTyped(value?: NewOrganizationUser | null, ignoreDiscriminator?: boolean): any;
