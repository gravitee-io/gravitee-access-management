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
/* Gravitee.io - Access Management API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

/* tslint:disable */
/* eslint-disable */
import { exists, mapValues } from '../runtime';
/**
 * 
 * @export
 * @interface UpdateUser
 */
export interface UpdateUser {
    /**
     * 
     * @type {string}
     * @memberof UpdateUser
     */
    email?: string;
    /**
     * 
     * @type {string}
     * @memberof UpdateUser
     */
    firstName?: string;
    /**
     * 
     * @type {string}
     * @memberof UpdateUser
     */
    lastName?: string;
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
    externalId?: string;
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
     * @type {boolean}
     * @memberof UpdateUser
     */
    credentialsNonExpired?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof UpdateUser
     */
    enabled?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof UpdateUser
     */
    preRegistration?: boolean;
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
     * 
     * @type {string}
     * @memberof UpdateUser
     */
    client?: string;
    /**
     * 
     * @type {number}
     * @memberof UpdateUser
     */
    loginsCount?: number;
    /**
     * 
     * @type {Date}
     * @memberof UpdateUser
     */
    loggedAt?: Date;
    /**
     * 
     * @type {string}
     * @memberof UpdateUser
     */
    preferredLanguage?: string;
    /**
     * 
     * @type {{ [key: string]: any; }}
     * @memberof UpdateUser
     */
    additionalInformation?: { [key: string]: any; };
    /**
     * 
     * @type {Date}
     * @memberof UpdateUser
     */
    createdAt?: Date;
    /**
     * 
     * @type {Date}
     * @memberof UpdateUser
     */
    updatedAt?: Date;
    /**
     * 
     * @type {boolean}
     * @memberof UpdateUser
     */
    forceResetPassword?: boolean;
}

export function UpdateUserFromJSON(json: any): UpdateUser {
    return UpdateUserFromJSONTyped(json, false);
}

export function UpdateUserFromJSONTyped(json: any, ignoreDiscriminator: boolean): UpdateUser {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'email': !exists(json, 'email') ? undefined : json['email'],
        'firstName': !exists(json, 'firstName') ? undefined : json['firstName'],
        'lastName': !exists(json, 'lastName') ? undefined : json['lastName'],
        'displayName': !exists(json, 'displayName') ? undefined : json['displayName'],
        'externalId': !exists(json, 'externalId') ? undefined : json['externalId'],
        'accountNonExpired': !exists(json, 'accountNonExpired') ? undefined : json['accountNonExpired'],
        'accountNonLocked': !exists(json, 'accountNonLocked') ? undefined : json['accountNonLocked'],
        'credentialsNonExpired': !exists(json, 'credentialsNonExpired') ? undefined : json['credentialsNonExpired'],
        'enabled': !exists(json, 'enabled') ? undefined : json['enabled'],
        'preRegistration': !exists(json, 'preRegistration') ? undefined : json['preRegistration'],
        'registrationCompleted': !exists(json, 'registrationCompleted') ? undefined : json['registrationCompleted'],
        'source': !exists(json, 'source') ? undefined : json['source'],
        'client': !exists(json, 'client') ? undefined : json['client'],
        'loginsCount': !exists(json, 'loginsCount') ? undefined : json['loginsCount'],
        'loggedAt': !exists(json, 'loggedAt') ? undefined : (new Date(json['loggedAt'])),
        'preferredLanguage': !exists(json, 'preferredLanguage') ? undefined : json['preferredLanguage'],
        'additionalInformation': !exists(json, 'additionalInformation') ? undefined : json['additionalInformation'],
        'createdAt': !exists(json, 'createdAt') ? undefined : (new Date(json['createdAt'])),
        'updatedAt': !exists(json, 'updatedAt') ? undefined : (new Date(json['updatedAt'])),
        'forceResetPassword': !exists(json, 'forceResetPassword') ? undefined : json['forceResetPassword'],
    };
}

export function UpdateUserToJSON(value?: UpdateUser | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'email': value.email,
        'firstName': value.firstName,
        'lastName': value.lastName,
        'displayName': value.displayName,
        'externalId': value.externalId,
        'accountNonExpired': value.accountNonExpired,
        'accountNonLocked': value.accountNonLocked,
        'credentialsNonExpired': value.credentialsNonExpired,
        'enabled': value.enabled,
        'preRegistration': value.preRegistration,
        'registrationCompleted': value.registrationCompleted,
        'source': value.source,
        'client': value.client,
        'loginsCount': value.loginsCount,
        'loggedAt': value.loggedAt === undefined ? undefined : (value.loggedAt.toISOString()),
        'preferredLanguage': value.preferredLanguage,
        'additionalInformation': value.additionalInformation,
        'createdAt': value.createdAt === undefined ? undefined : (value.createdAt.toISOString()),
        'updatedAt': value.updatedAt === undefined ? undefined : (value.updatedAt.toISOString()),
        'forceResetPassword': value.forceResetPassword,
    };
}

