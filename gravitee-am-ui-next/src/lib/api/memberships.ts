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
import type { Group } from '@am-models/Group';
import type { Membership } from '@am-models/Membership';
import type { NewMembership as NewMembershipModel } from '@am-models/NewMembership';
import type { RoleEntity } from '@am-models/RoleEntity';
import type { User } from '@am-models/User';
import { http } from './http';
import type { Page } from './management-api';
import type { ApiModel } from './model';

export type MemberType = 'user' | 'group';

/** A membership with the role and member names resolved from the response metadata, as the Angular services map it. */
export interface Member {
    readonly id: string;
    readonly memberId: string;
    readonly memberType: MemberType;
    readonly roleId: string;
    readonly roleName: string;
    readonly name: string;
}

/** The body of `GET .../members` for a domain, an application, a protected resource or the organization. */
export interface MembershipsResponse {
    readonly memberships: readonly ApiModel<Omit<Membership, 'memberType'> & { memberType: MemberType }, 'id' | 'memberId' | 'roleId'>[];
    readonly metadata: {
        readonly roles: Record<string, { name: string }>;
        readonly users: Record<string, { displayName?: string }>;
        readonly groups: Record<string, { displayName?: string; name?: string }>;
    };
}

/** The body of `POST .../members`. A POST for a member who exists changes the role. */
export type NewMembership = ApiModel<NewMembershipModel>;

export type MembershipRole = ApiModel<RoleEntity, 'id' | 'name' | 'system'>;

export type MembershipRoleType = 'ORGANIZATION' | 'DOMAIN' | 'APPLICATION' | 'PROTECTED_RESOURCE';

export type OrganizationUser = ApiModel<User, 'id' | 'username'>;

export type OrganizationGroup = ApiModel<Group, 'id' | 'name'>;

export function toMembers(response: MembershipsResponse): Member[] {
    const { roles, users, groups } = response.metadata;
    return response.memberships.map(membership => ({
        id: membership.id,
        memberId: membership.memberId,
        memberType: membership.memberType,
        roleId: membership.roleId,
        roleName: roles[membership.roleId]?.name ?? 'Unknown role',
        name:
            membership.memberType === 'user'
                ? (users[membership.memberId]?.displayName ?? 'Unknown user')
                : (groups[membership.memberId]?.displayName ?? groups[membership.memberId]?.name ?? 'Unknown group'),
    }));
}

export function listMembershipRoles(organizationId: string, type: MembershipRoleType): Promise<MembershipRole[]> {
    return http<MembershipRole[]>(`/organizations/${organizationId}/roles?type=${type}`);
}

/** The Angular member search: `q=<text>*`, first page of 30. */
export async function searchOrganizationUsers(organizationId: string, text: string): Promise<OrganizationUser[]> {
    const page = await http<Page<OrganizationUser>>(
        `/organizations/${organizationId}/users?q=${encodeURIComponent(`${text}*`)}&page=0&size=30`,
    );
    return page.data;
}

export async function listOrganizationGroups(organizationId: string): Promise<OrganizationGroup[]> {
    const page = await http<Page<OrganizationGroup>>(`/organizations/${organizationId}/groups`);
    return page.data;
}
