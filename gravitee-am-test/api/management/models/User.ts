/* tslint:disable */
/* eslint-disable */
/**
 * Gravitee.io - Access Management API
 * No description provided (generated by Openapi Generator https://github.com/openapitools/openapi-generator)
 *
 * 
 *
 * NOTE: This class is auto generated by OpenAPI Generator (https://openapi-generator.tech).
 * https://openapi-generator.tech
 * Do not edit the class manually.
 */

import { exists, mapValues } from '../runtime';
import {
    Address,
    AddressFromJSON,
    AddressFromJSONTyped,
    AddressToJSON,
} from './Address';
import {
    Attribute,
    AttributeFromJSON,
    AttributeFromJSONTyped,
    AttributeToJSON,
} from './Attribute';
import {
    Certificate,
    CertificateFromJSON,
    CertificateFromJSONTyped,
    CertificateToJSON,
} from './Certificate';
import {
    EnrolledFactor,
    EnrolledFactorFromJSON,
    EnrolledFactorFromJSONTyped,
    EnrolledFactorToJSON,
} from './EnrolledFactor';
import {
    Role,
    RoleFromJSON,
    RoleFromJSONTyped,
    RoleToJSON,
} from './Role';

/**
 * 
 * @export
 * @interface User
 */
export interface User {
    /**
     * 
     * @type {string}
     * @memberof User
     */
    id?: string;
    /**
     * 
     * @type {string}
     * @memberof User
     */
    externalId?: string;
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
    password?: string;
    /**
     * 
     * @type {string}
     * @memberof User
     */
    email?: string;
    /**
     * 
     * @type {string}
     * @memberof User
     */
    displayName?: string;
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
    firstName?: string;
    /**
     * 
     * @type {string}
     * @memberof User
     */
    lastName?: string;
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
    picture?: string;
    /**
     * 
     * @type {Array<Attribute>}
     * @memberof User
     */
    emails?: Array<Attribute>;
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
    ims?: Array<Attribute>;
    /**
     * 
     * @type {Array<Attribute>}
     * @memberof User
     */
    photos?: Array<Attribute>;
    /**
     * 
     * @type {Array<string>}
     * @memberof User
     */
    entitlements?: Array<string>;
    /**
     * 
     * @type {Array<Address>}
     * @memberof User
     */
    addresses?: Array<Address>;
    /**
     * 
     * @type {Array<string>}
     * @memberof User
     */
    roles?: Array<string>;
    /**
     * 
     * @type {Array<string>}
     * @memberof User
     */
    dynamicRoles?: Array<string>;
    /**
     * 
     * @type {Set<Role>}
     * @memberof User
     */
    rolesPermissions?: Set<Role>;
    /**
     * 
     * @type {Array<string>}
     * @memberof User
     */
    groups?: Array<string>;
    /**
     * 
     * @type {Array<Certificate>}
     * @memberof User
     */
    x509Certificates?: Array<Certificate>;
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
     * @type {Date}
     * @memberof User
     */
    accountLockedAt?: Date;
    /**
     * 
     * @type {Date}
     * @memberof User
     */
    accountLockedUntil?: Date;
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
    enabled?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof User
     */
    internal?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof User
     */
    preRegistration?: boolean;
    /**
     * 
     * @type {boolean}
     * @memberof User
     */
    registrationCompleted?: boolean;
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
    registrationUserUri?: string;
    /**
     * 
     * @type {string}
     * @memberof User
     */
    registrationAccessToken?: string;
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
    referenceId?: string;
    /**
     * 
     * @type {string}
     * @memberof User
     */
    source?: string;
    /**
     * 
     * @type {string}
     * @memberof User
     */
    client?: string;
    /**
     * 
     * @type {number}
     * @memberof User
     */
    loginsCount?: number;
    /**
     * 
     * @type {Array<EnrolledFactor>}
     * @memberof User
     */
    factors?: Array<EnrolledFactor>;
    /**
     * 
     * @type {{ [key: string]: any; }}
     * @memberof User
     */
    additionalInformation?: { [key: string]: any; };
    /**
     * 
     * @type {Date}
     * @memberof User
     */
    loggedAt?: Date;
    /**
     * 
     * @type {Date}
     * @memberof User
     */
    lastPasswordReset?: Date;
    /**
     * 
     * @type {Date}
     * @memberof User
     */
    lastLogoutAt?: Date;
    /**
     * 
     * @type {Date}
     * @memberof User
     */
    mfaEnrollmentSkippedAt?: Date;
    /**
     * 
     * @type {Date}
     * @memberof User
     */
    createdAt?: Date;
    /**
     * 
     * @type {Date}
     * @memberof User
     */
    updatedAt?: Date;
    /**
     * 
     * @type {{ [key: string]: any; }}
     * @memberof User
     */
    address?: { [key: string]: any; };
    /**
     * 
     * @type {string}
     * @memberof User
     */
    locale?: string;
    /**
     * 
     * @type {string}
     * @memberof User
     */
    zoneInfo?: string;
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
    inactive?: boolean;
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
    website?: string;
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
    phoneNumber?: string;
}


/**
 * @export
 */
export const UserReferenceTypeEnum = {
    Platform: 'PLATFORM',
    Domain: 'DOMAIN',
    Application: 'APPLICATION',
    Organization: 'ORGANIZATION',
    Environment: 'ENVIRONMENT'
} as const;
export type UserReferenceTypeEnum = typeof UserReferenceTypeEnum[keyof typeof UserReferenceTypeEnum];


export function UserFromJSON(json: any): User {
    return UserFromJSONTyped(json, false);
}

export function UserFromJSONTyped(json: any, ignoreDiscriminator: boolean): User {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'id': !exists(json, 'id') ? undefined : json['id'],
        'externalId': !exists(json, 'externalId') ? undefined : json['externalId'],
        'username': !exists(json, 'username') ? undefined : json['username'],
        'password': !exists(json, 'password') ? undefined : json['password'],
        'email': !exists(json, 'email') ? undefined : json['email'],
        'displayName': !exists(json, 'displayName') ? undefined : json['displayName'],
        'nickName': !exists(json, 'nickName') ? undefined : json['nickName'],
        'firstName': !exists(json, 'firstName') ? undefined : json['firstName'],
        'lastName': !exists(json, 'lastName') ? undefined : json['lastName'],
        'title': !exists(json, 'title') ? undefined : json['title'],
        'type': !exists(json, 'type') ? undefined : json['type'],
        'preferredLanguage': !exists(json, 'preferredLanguage') ? undefined : json['preferredLanguage'],
        'picture': !exists(json, 'picture') ? undefined : json['picture'],
        'emails': !exists(json, 'emails') ? undefined : ((json['emails'] as Array<any>).map(AttributeFromJSON)),
        'phoneNumbers': !exists(json, 'phoneNumbers') ? undefined : ((json['phoneNumbers'] as Array<any>).map(AttributeFromJSON)),
        'ims': !exists(json, 'ims') ? undefined : ((json['ims'] as Array<any>).map(AttributeFromJSON)),
        'photos': !exists(json, 'photos') ? undefined : ((json['photos'] as Array<any>).map(AttributeFromJSON)),
        'entitlements': !exists(json, 'entitlements') ? undefined : json['entitlements'],
        'addresses': !exists(json, 'addresses') ? undefined : ((json['addresses'] as Array<any>).map(AddressFromJSON)),
        'roles': !exists(json, 'roles') ? undefined : json['roles'],
        'dynamicRoles': !exists(json, 'dynamicRoles') ? undefined : json['dynamicRoles'],
        'rolesPermissions': !exists(json, 'rolesPermissions') ? undefined : (new Set((json['rolesPermissions'] as Array<any>).map(RoleFromJSON))),
        'groups': !exists(json, 'groups') ? undefined : json['groups'],
        'x509Certificates': !exists(json, 'x509Certificates') ? undefined : ((json['x509Certificates'] as Array<any>).map(CertificateFromJSON)),
        'accountNonExpired': !exists(json, 'accountNonExpired') ? undefined : json['accountNonExpired'],
        'accountNonLocked': !exists(json, 'accountNonLocked') ? undefined : json['accountNonLocked'],
        'accountLockedAt': !exists(json, 'accountLockedAt') ? undefined : (new Date(json['accountLockedAt'])),
        'accountLockedUntil': !exists(json, 'accountLockedUntil') ? undefined : (new Date(json['accountLockedUntil'])),
        'credentialsNonExpired': !exists(json, 'credentialsNonExpired') ? undefined : json['credentialsNonExpired'],
        'enabled': !exists(json, 'enabled') ? undefined : json['enabled'],
        'internal': !exists(json, 'internal') ? undefined : json['internal'],
        'preRegistration': !exists(json, 'preRegistration') ? undefined : json['preRegistration'],
        'registrationCompleted': !exists(json, 'registrationCompleted') ? undefined : json['registrationCompleted'],
        'newsletter': !exists(json, 'newsletter') ? undefined : json['newsletter'],
        'registrationUserUri': !exists(json, 'registrationUserUri') ? undefined : json['registrationUserUri'],
        'registrationAccessToken': !exists(json, 'registrationAccessToken') ? undefined : json['registrationAccessToken'],
        'referenceType': !exists(json, 'referenceType') ? undefined : json['referenceType'],
        'referenceId': !exists(json, 'referenceId') ? undefined : json['referenceId'],
        'source': !exists(json, 'source') ? undefined : json['source'],
        'client': !exists(json, 'client') ? undefined : json['client'],
        'loginsCount': !exists(json, 'loginsCount') ? undefined : json['loginsCount'],
        'factors': !exists(json, 'factors') ? undefined : ((json['factors'] as Array<any>).map(EnrolledFactorFromJSON)),
        'additionalInformation': !exists(json, 'additionalInformation') ? undefined : json['additionalInformation'],
        'loggedAt': !exists(json, 'loggedAt') ? undefined : (new Date(json['loggedAt'])),
        'lastPasswordReset': !exists(json, 'lastPasswordReset') ? undefined : (new Date(json['lastPasswordReset'])),
        'lastLogoutAt': !exists(json, 'lastLogoutAt') ? undefined : (new Date(json['lastLogoutAt'])),
        'mfaEnrollmentSkippedAt': !exists(json, 'mfaEnrollmentSkippedAt') ? undefined : (new Date(json['mfaEnrollmentSkippedAt'])),
        'createdAt': !exists(json, 'createdAt') ? undefined : (new Date(json['createdAt'])),
        'updatedAt': !exists(json, 'updatedAt') ? undefined : (new Date(json['updatedAt'])),
        'address': !exists(json, 'address') ? undefined : json['address'],
        'locale': !exists(json, 'locale') ? undefined : json['locale'],
        'zoneInfo': !exists(json, 'zoneInfo') ? undefined : json['zoneInfo'],
        'middleName': !exists(json, 'middleName') ? undefined : json['middleName'],
        'inactive': !exists(json, 'inactive') ? undefined : json['inactive'],
        'profile': !exists(json, 'profile') ? undefined : json['profile'],
        'website': !exists(json, 'website') ? undefined : json['website'],
        'birthdate': !exists(json, 'birthdate') ? undefined : json['birthdate'],
        'phoneNumber': !exists(json, 'phoneNumber') ? undefined : json['phoneNumber'],
    };
}

export function UserToJSON(value?: User | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'id': value.id,
        'externalId': value.externalId,
        'username': value.username,
        'password': value.password,
        'email': value.email,
        'displayName': value.displayName,
        'nickName': value.nickName,
        'firstName': value.firstName,
        'lastName': value.lastName,
        'title': value.title,
        'type': value.type,
        'preferredLanguage': value.preferredLanguage,
        'picture': value.picture,
        'emails': value.emails === undefined ? undefined : ((value.emails as Array<any>).map(AttributeToJSON)),
        'phoneNumbers': value.phoneNumbers === undefined ? undefined : ((value.phoneNumbers as Array<any>).map(AttributeToJSON)),
        'ims': value.ims === undefined ? undefined : ((value.ims as Array<any>).map(AttributeToJSON)),
        'photos': value.photos === undefined ? undefined : ((value.photos as Array<any>).map(AttributeToJSON)),
        'entitlements': value.entitlements,
        'addresses': value.addresses === undefined ? undefined : ((value.addresses as Array<any>).map(AddressToJSON)),
        'roles': value.roles,
        'dynamicRoles': value.dynamicRoles,
        'rolesPermissions': value.rolesPermissions === undefined ? undefined : (Array.from(value.rolesPermissions as Set<any>).map(RoleToJSON)),
        'groups': value.groups,
        'x509Certificates': value.x509Certificates === undefined ? undefined : ((value.x509Certificates as Array<any>).map(CertificateToJSON)),
        'accountNonExpired': value.accountNonExpired,
        'accountNonLocked': value.accountNonLocked,
        'accountLockedAt': value.accountLockedAt === undefined ? undefined : (value.accountLockedAt.toISOString()),
        'accountLockedUntil': value.accountLockedUntil === undefined ? undefined : (value.accountLockedUntil.toISOString()),
        'credentialsNonExpired': value.credentialsNonExpired,
        'enabled': value.enabled,
        'internal': value.internal,
        'preRegistration': value.preRegistration,
        'registrationCompleted': value.registrationCompleted,
        'newsletter': value.newsletter,
        'registrationUserUri': value.registrationUserUri,
        'registrationAccessToken': value.registrationAccessToken,
        'referenceType': value.referenceType,
        'referenceId': value.referenceId,
        'source': value.source,
        'client': value.client,
        'loginsCount': value.loginsCount,
        'factors': value.factors === undefined ? undefined : ((value.factors as Array<any>).map(EnrolledFactorToJSON)),
        'additionalInformation': value.additionalInformation,
        'loggedAt': value.loggedAt === undefined ? undefined : (value.loggedAt.toISOString()),
        'lastPasswordReset': value.lastPasswordReset === undefined ? undefined : (value.lastPasswordReset.toISOString()),
        'lastLogoutAt': value.lastLogoutAt === undefined ? undefined : (value.lastLogoutAt.toISOString()),
        'mfaEnrollmentSkippedAt': value.mfaEnrollmentSkippedAt === undefined ? undefined : (value.mfaEnrollmentSkippedAt.toISOString()),
        'createdAt': value.createdAt === undefined ? undefined : (value.createdAt.toISOString()),
        'updatedAt': value.updatedAt === undefined ? undefined : (value.updatedAt.toISOString()),
        'address': value.address,
        'locale': value.locale,
        'zoneInfo': value.zoneInfo,
        'middleName': value.middleName,
        'inactive': value.inactive,
        'profile': value.profile,
        'website': value.website,
        'birthdate': value.birthdate,
        'phoneNumber': value.phoneNumber,
    };
}

