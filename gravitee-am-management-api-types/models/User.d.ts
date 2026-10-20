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
import type { EnrolledFactor } from './EnrolledFactor';
import type { UserId } from './UserId';
import type { UserIdentity } from './UserIdentity';
import type { ConsoleUserPreferences } from './ConsoleUserPreferences';
import type { Certificate } from './Certificate';
/**
 *
 * @export
 * @interface User
 */
export interface User {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof User
     */
    accountLockedAt?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof User
     */
    accountLockedUntil?: number;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    accountNonExpired?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    accountNonLocked?: boolean;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof User
     */
    additionalInformation?: {
        [key: string]: any;
    };
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof User
     */
    address?: {
        [key: string]: any;
    };
    /**
     *
     * @type {Array<Address>}
     * @memberof User
     */
    addresses?: Array<Address>;
    /**
     *
     * @type {string}
     * @memberof User
     */
    birthdate?: string;
    /**
     *
     * @type {string}
     * @memberof User
     */
    client?: string;
    /**
     *
     * @type {ConsoleUserPreferences}
     * @memberof User
     */
    consolePreferences?: ConsoleUserPreferences;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof User
     */
    createdAt?: number;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    credentialsNonExpired?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    disabled?: boolean;
    /**
     *
     * @type {string}
     * @memberof User
     */
    displayName?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof User
     */
    dynamicGroups?: Array<string>;
    /**
     *
     * @type {Array<string>}
     * @memberof User
     */
    dynamicRoles?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof User
     */
    email?: string;
    /**
     *
     * @type {Array<Attribute>}
     * @memberof User
     */
    emails?: Array<Attribute>;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    enabled?: boolean;
    /**
     *
     * @type {Array<string>}
     * @memberof User
     */
    entitlements?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof User
     */
    externalId?: string;
    /**
     *
     * @type {Array<EnrolledFactor>}
     * @memberof User
     */
    factors?: Array<EnrolledFactor>;
    /**
     *
     * @type {string}
     * @memberof User
     */
    firstName?: string;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    forceResetPassword?: boolean;
    /**
     *
     * @type {UserId}
     * @memberof User
     */
    fullId?: UserId;
    /**
     *
     * @type {Array<string>}
     * @memberof User
     */
    groups?: Array<string>;
    /**
     *
     * @type {string}
     * @memberof User
     */
    id?: string;
    /**
     *
     * @type {Array<UserIdentity>}
     * @memberof User
     */
    identities?: Array<UserIdentity>;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof User
     */
    identitiesAsMap?: {
        [key: string]: any;
    };
    /**
     *
     * @type {Array<Attribute>}
     * @memberof User
     */
    ims?: Array<Attribute>;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    inactive?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    indefinitelyLocked?: boolean;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    internal?: boolean;
    /**
     *
     * @type {{ [key: string]: any; }}
     * @memberof User
     */
    lastIdentityInformation?: {
        [key: string]: any;
    };
    /**
     *
     * @type {string}
     * @memberof User
     */
    lastIdentityUsed?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof User
     */
    lastLoginWithCredentials?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof User
     */
    lastLogoutAt?: number;
    /**
     *
     * @type {string}
     * @memberof User
     */
    lastName?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof User
     */
    lastPasswordReset?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof User
     */
    lastUsernameReset?: number;
    /**
     *
     * @type {string}
     * @memberof User
     */
    locale?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof User
     */
    loggedAt?: number;
    /**
     *
     * @type {number}
     * @memberof User
     */
    loginsCount?: number;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof User
     */
    mfaEnrollmentSkippedAt?: number;
    /**
     *
     * @type {string}
     * @memberof User
     */
    middleName?: string;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    newsletter?: boolean;
    /**
     *
     * @type {string}
     * @memberof User
     */
    nickName?: string;
    /**
     *
     * @type {string}
     * @memberof User
     */
    password?: string;
    /**
     *
     * @type {string}
     * @memberof User
     */
    phoneNumber?: string;
    /**
     *
     * @type {Array<Attribute>}
     * @memberof User
     */
    phoneNumbers?: Array<Attribute>;
    /**
     *
     * @type {Array<Attribute>}
     * @memberof User
     */
    photos?: Array<Attribute>;
    /**
     *
     * @type {string}
     * @memberof User
     */
    picture?: string;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    preRegistration?: boolean;
    /**
     *
     * @type {string}
     * @memberof User
     */
    preferredLanguage?: string;
    /**
     *
     * @type {string}
     * @memberof User
     */
    profile?: string;
    /**
     *
     * @type {string}
     * @memberof User
     */
    referenceId?: string;
    /**
     *
     * @type {string}
     * @memberof User
     */
    referenceType?: UserReferenceTypeEnum;
    /**
     *
     * @type {string}
     * @memberof User
     */
    registrationAccessToken?: string;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    registrationCompleted?: boolean;
    /**
     *
     * @type {string}
     * @memberof User
     */
    registrationUserUri?: string;
    /**
     *
     * @type {Array<string>}
     * @memberof User
     */
    roles?: Array<string>;
    /**
     *
     * @type {Array<Role>}
     * @memberof User
     */
    rolesPermissions?: Array<Role>;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    serviceAccount?: boolean;
    /**
     *
     * @type {string}
     * @memberof User
     */
    source?: string;
    /**
     *
     * @type {boolean}
     * @memberof User
     */
    temporarilyLocked?: boolean;
    /**
     *
     * @type {string}
     * @memberof User
     */
    title?: string;
    /**
     *
     * @type {string}
     * @memberof User
     */
    type?: string;
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof User
     */
    updatedAt?: number;
    /**
     *
     * @type {string}
     * @memberof User
     */
    username?: string;
    /**
     *
     * @type {string}
     * @memberof User
     */
    website?: string;
    /**
     *
     * @type {Array<Certificate>}
     * @memberof User
     */
    x509Certificates?: Array<Certificate>;
    /**
     *
     * @type {string}
     * @memberof User
     */
    zoneInfo?: string;
}
/**
 * @export
 */
export declare const UserReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type UserReferenceTypeEnum = typeof UserReferenceTypeEnum[keyof typeof UserReferenceTypeEnum];
/**
 * Check if a given object implements the User interface.
 */
export declare function instanceOfUser(value: object): value is User;
export declare function UserFromJSON(json: any): User;
export declare function UserFromJSONTyped(json: any, ignoreDiscriminator: boolean): User;
export declare function UserToJSON(json: any): User;
export declare function UserToJSONTyped(value?: User | null, ignoreDiscriminator?: boolean): any;
