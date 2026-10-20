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
import type { Role } from './Role';
import type { Address } from './Address';
import type { Attribute } from './Attribute';
import type { ApplicationEntity } from './ApplicationEntity';
import type { EnrolledFactor } from './EnrolledFactor';
import type { UserId } from './UserId';
import type { UserIdentity } from './UserIdentity';
import type { ConsoleUserPreferences } from './ConsoleUserPreferences';
import type { Certificate } from './Certificate';
/**
 *
 * @export
 * @interface UserEntity
 */
export interface UserEntity {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserEntity
     */
    accountLockedAt?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserEntity
     */
    accountLockedUntil?: number;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    accountNonExpired?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    accountNonLocked?: boolean;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof UserEntity
     */
    additionalInformation?: {
        [key: string]: any;
    };
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof UserEntity
     */
    address?: {
        [key: string]: any;
    };
    /**
     *
     * @type {Array<Address>}
     * @memberof UserEntity
     */
    addresses?: Array<Address>;
    /**
     *
     * @type {ApplicationEntity}
     * @memberof UserEntity
     */
    applicationEntity?: ApplicationEntity;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    birthdate?: string;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    client?: string;
    /**
     *
     * @type {ConsoleUserPreferences}
     * @memberof UserEntity
     */
    consolePreferences?: ConsoleUserPreferences;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserEntity
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    credentialsNonExpired?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    disabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    displayName?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof UserEntity
     */
    dynamicGroups?: Array<string>;
    /**
     *
     * @type {Array<string>}
     * @memberof UserEntity
     */
    dynamicRoles?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    email?: string;
    /**
     *
     * @type {Array<Attribute>}
     * @memberof UserEntity
     */
    emails?: Array<Attribute>;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    enabled?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof UserEntity
     */
    entitlements?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    externalId?: string;
    /**
     *
     * @type {Array<EnrolledFactor>}
     * @memberof UserEntity
     */
    factors?: Array<EnrolledFactor>;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    firstName?: string;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    forceResetPassword?: boolean;
    /**
     *
     * @type {UserId}
     * @memberof UserEntity
     */
    fullId?: UserId;
    /**
     *
     * @type {Array<string>}
     * @memberof UserEntity
     */
    groups?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    id?: string;
    /**
     *
     * @type {Array<UserIdentity>}
     * @memberof UserEntity
     */
    identities?: Array<UserIdentity>;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof UserEntity
     */
    identitiesAsMap?: {
        [key: string]: any;
    };
    /**
     *
     * @type {Array<Attribute>}
     * @memberof UserEntity
     */
    ims?: Array<Attribute>;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    inactive?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    indefinitelyLocked?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    internal?: boolean;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof UserEntity
     */
    lastIdentityInformation?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    lastIdentityUsed?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserEntity
     */
    lastLoginWithCredentials?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserEntity
     */
    lastLogoutAt?: number;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    lastName?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserEntity
     */
    lastPasswordReset?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserEntity
     */
    lastUsernameReset?: number;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    locale?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserEntity
     */
    loggedAt?: number;
    /**
     *
     * @type {number}
     * @memberof UserEntity
     */
    loginsCount?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserEntity
     */
    mfaEnrollmentSkippedAt?: number;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    middleName?: string;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    newsletter?: boolean;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    nickName?: string;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    password?: string;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    phoneNumber?: string;
    /**
     *
     * @type {Array<Attribute>}
     * @memberof UserEntity
     */
    phoneNumbers?: Array<Attribute>;
    /**
     *
     * @type {Array<Attribute>}
     * @memberof UserEntity
     */
    photos?: Array<Attribute>;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    picture?: string;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    preRegistration?: boolean;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    preferredLanguage?: string;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    profile?: string;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    referenceType?: UserEntityReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    registrationAccessToken?: string;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    registrationCompleted?: boolean;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    registrationUserUri?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof UserEntity
     */
    roles?: Array<string>;
    /**
     *
     * @type {Array<Role>}
     * @memberof UserEntity
     */
    rolesPermissions?: Array<Role>;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    serviceAccount?: boolean;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    source?: string;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    sourceId?: string;
    /**
     *
     * @type {boolean}
     * @memberof UserEntity
     */
    temporarilyLocked?: boolean;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    title?: string;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof UserEntity
     */
    updatedAt?: number;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    username?: string;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    website?: string;
    /**
     *
     * @type {Array<Certificate>}
     * @memberof UserEntity
     */
    x509Certificates?: Array<Certificate>;
    /**
     *
     * @type {string}
     * @memberof UserEntity
     */
    zoneInfo?: string;
}
/**
 * @export
 */
export declare const UserEntityReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type UserEntityReferenceTypeEnum = typeof UserEntityReferenceTypeEnum[keyof typeof UserEntityReferenceTypeEnum];
/**
 * Check if a given object implements the UserEntity interface.
 */
export declare function instanceOfUserEntity(value: object): value is UserEntity;
export declare function UserEntityFromJSON(json: any): UserEntity;
export declare function UserEntityFromJSONTyped(json: any, ignoreDiscriminator: boolean): UserEntity;
export declare function UserEntityToJSON(json: any): UserEntity;
export declare function UserEntityToJSONTyped(value?: UserEntity | null, ignoreDiscriminator?: boolean): any;
