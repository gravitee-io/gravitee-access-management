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
/**
 * 
 * @export
 * @interface Membership
 */
export interface Membership {
    /**
     * 
     * @type {string}
     * @memberof Membership
     */
    id?: string;
    /**
     * 
     * @type {string}
     * @memberof Membership
     */
    domain?: string;
    /**
     * 
     * @type {string}
     * @memberof Membership
     */
    memberId?: string;
    /**
     * 
     * @type {string}
     * @memberof Membership
     */
    memberType?: MembershipMemberTypeEnum;
    /**
     * 
     * @type {string}
     * @memberof Membership
     */
    referenceId?: string;
    /**
     * 
     * @type {string}
     * @memberof Membership
     */
    referenceType?: MembershipReferenceTypeEnum;
    /**
     * 
     * @type {string}
     * @memberof Membership
     */
    roleId?: string;
    /**
     * 
     * @type {Date}
     * @memberof Membership
     */
    createdAt?: Date;
    /**
     * 
     * @type {Date}
     * @memberof Membership
     */
    updatedAt?: Date;
}


/**
 * @export
 */
export const MembershipMemberTypeEnum = {
    User: 'USER',
    Group: 'GROUP'
} as const;
export type MembershipMemberTypeEnum = typeof MembershipMemberTypeEnum[keyof typeof MembershipMemberTypeEnum];

/**
 * @export
 */
export const MembershipReferenceTypeEnum = {
    Platform: 'PLATFORM',
    Domain: 'DOMAIN',
    Application: 'APPLICATION',
    Organization: 'ORGANIZATION',
    Environment: 'ENVIRONMENT'
} as const;
export type MembershipReferenceTypeEnum = typeof MembershipReferenceTypeEnum[keyof typeof MembershipReferenceTypeEnum];


export function MembershipFromJSON(json: any): Membership {
    return MembershipFromJSONTyped(json, false);
}

export function MembershipFromJSONTyped(json: any, ignoreDiscriminator: boolean): Membership {
    if ((json === undefined) || (json === null)) {
        return json;
    }
    return {
        
        'id': !exists(json, 'id') ? undefined : json['id'],
        'domain': !exists(json, 'domain') ? undefined : json['domain'],
        'memberId': !exists(json, 'memberId') ? undefined : json['memberId'],
        'memberType': !exists(json, 'memberType') ? undefined : json['memberType'],
        'referenceId': !exists(json, 'referenceId') ? undefined : json['referenceId'],
        'referenceType': !exists(json, 'referenceType') ? undefined : json['referenceType'],
        'roleId': !exists(json, 'roleId') ? undefined : json['roleId'],
        'createdAt': !exists(json, 'createdAt') ? undefined : (new Date(json['createdAt'])),
        'updatedAt': !exists(json, 'updatedAt') ? undefined : (new Date(json['updatedAt'])),
    };
}

export function MembershipToJSON(value?: Membership | null): any {
    if (value === undefined) {
        return undefined;
    }
    if (value === null) {
        return null;
    }
    return {
        
        'id': value.id,
        'domain': value.domain,
        'memberId': value.memberId,
        'memberType': value.memberType,
        'referenceId': value.referenceId,
        'referenceType': value.referenceType,
        'roleId': value.roleId,
        'createdAt': value.createdAt === undefined ? undefined : (value.createdAt.toISOString()),
        'updatedAt': value.updatedAt === undefined ? undefined : (value.updatedAt.toISOString()),
    };
}

