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
import type { NewMembership as NewMembershipModel } from '@am-models/NewMembership';
import type { RoleEntity } from '@am-models/RoleEntity';
import { http } from './http';
import type { Page } from './management-api';
import type { ApiModel } from './model';

export type MemberType = 'user' | 'group';

export interface Member {
    readonly id: string;
    readonly memberId: string;
    readonly memberType: MemberType;
    readonly roleId: string;
    readonly roleName: string;
    readonly name: string;
}

// The generated MembershipListItem types the metadata as an untyped map, and the member type is lowercase on the wire.
interface MembershipsResponse {
    readonly memberships: readonly { id: string; memberId: string; memberType: MemberType; roleId: string }[];
    readonly metadata: {
        readonly roles: Readonly<Record<string, { name: string }>>;
        readonly users: Readonly<Record<string, { displayName?: string }>>;
        readonly groups: Readonly<Record<string, { displayName?: string }>>;
    };
}

/** A POST for a member who already has a membership changes its role. */
export type NewMembership = ApiModel<NewMembershipModel>;

export type MembershipRole = ApiModel<RoleEntity, 'id' | 'name' | 'system'>;

export type MembershipRoleType = 'ORGANIZATION' | 'DOMAIN' | 'APPLICATION' | 'PROTECTED_RESOURCE';

// The generated `User` model does not compile, as `users.ts` explains. These are the fields the search reads.
export interface OrganizationUser {
    readonly id: string;
    readonly username: string;
    readonly displayName?: string;
    readonly source?: string;
}

export type OrganizationGroup = ApiModel<Group, 'id' | 'name'>;

function toMembers({ memberships, metadata }: MembershipsResponse): Member[] {
    return memberships.map(membership => ({
        id: membership.id,
        memberId: membership.memberId,
        memberType: membership.memberType,
        roleId: membership.roleId,
        roleName: metadata.roles[membership.roleId]?.name ?? 'Unknown role',
        name:
            membership.memberType === 'user'
                ? (metadata.users[membership.memberId]?.displayName ?? 'Unknown user')
                : (metadata.groups[membership.memberId]?.displayName ?? 'Unknown group'),
    }));
}

/** `membersPath` is the members endpoint of the resource, for example `.../applications/:id/members`. */
export async function listMembers(membersPath: string): Promise<Member[]> {
    return toMembers(await http<MembershipsResponse>(membersPath));
}

export function saveMember(membersPath: string, member: NewMembership): Promise<unknown> {
    return http<unknown>(membersPath, { method: 'POST', body: JSON.stringify(member) });
}

export function removeMember(membersPath: string, membershipId: string): Promise<void> {
    return http<void>(`${membersPath}/${encodeURIComponent(membershipId)}`, { method: 'DELETE' });
}

export function listMembershipRoles(organizationId: string, type: MembershipRoleType): Promise<MembershipRole[]> {
    return http<MembershipRole[]>(`/organizations/${organizationId}/roles?type=${type}`);
}

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
