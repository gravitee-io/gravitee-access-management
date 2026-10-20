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
 * @interface NewMembership
 */
export interface NewMembership {
    /**
     *
     * @type {string}
     * @memberof NewMembership
     */
    memberId: string;
    /**
     *
     * @type {string}
     * @memberof NewMembership
     */
    memberType: NewMembershipMemberTypeEnum;
    /**
     *
     * @type {string}
     * @memberof NewMembership
     */
    role: string;
}
/**
 * @export
 */
export declare const NewMembershipMemberTypeEnum: {
    readonly User: "USER";
    readonly Group: "GROUP";
};
export type NewMembershipMemberTypeEnum = typeof NewMembershipMemberTypeEnum[keyof typeof NewMembershipMemberTypeEnum];
/**
 * Check if a given object implements the NewMembership interface.
 */
export declare function instanceOfNewMembership(value: object): value is NewMembership;
export declare function NewMembershipFromJSON(json: any): NewMembership;
export declare function NewMembershipFromJSONTyped(json: any, ignoreDiscriminator: boolean): NewMembership;
export declare function NewMembershipToJSON(json: any): NewMembership;
export declare function NewMembershipToJSONTyped(value?: NewMembership | null, ignoreDiscriminator?: boolean): any;
