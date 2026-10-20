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
 * @interface Membership
 */
export interface Membership {
    /**
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Membership
     */
    createdAt?: number;
    /**
     *
     * @type {string}
     * @memberof Membership
     */
    domain?: string;
    /**
     *
     * @type {boolean}
     * @memberof Membership
     */
    fromRoleMapper?: boolean;
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
     * Epoch timestamp in milliseconds.
     * @type {number}
     * @memberof Membership
     */
    updatedAt?: number;
}
/**
 * @export
 */
export declare const MembershipMemberTypeEnum: {
    readonly User: "USER";
    readonly Group: "GROUP";
};
export type MembershipMemberTypeEnum = typeof MembershipMemberTypeEnum[keyof typeof MembershipMemberTypeEnum];
/**
 * @export
 */
export declare const MembershipReferenceTypeEnum: {
    readonly Platform: "PLATFORM";
    readonly Domain: "DOMAIN";
    readonly Application: "APPLICATION";
    readonly Organization: "ORGANIZATION";
    readonly Environment: "ENVIRONMENT";
    readonly ProtectedResource: "PROTECTED_RESOURCE";
};
export type MembershipReferenceTypeEnum = typeof MembershipReferenceTypeEnum[keyof typeof MembershipReferenceTypeEnum];
/**
 * Check if a given object implements the Membership interface.
 */
export declare function instanceOfMembership(value: object): value is Membership;
export declare function MembershipFromJSON(json: any): Membership;
export declare function MembershipFromJSONTyped(json: any, ignoreDiscriminator: boolean): Membership;
export declare function MembershipToJSON(json: any): Membership;
export declare function MembershipToJSONTyped(value?: Membership | null, ignoreDiscriminator?: boolean): any;
