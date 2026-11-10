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
import { http } from '../http';
import {
    listMembers,
    listMembershipRoles,
    listOrganizationGroups,
    removeMember,
    saveMember,
    searchOrganizationUsers,
} from '../memberships';

vi.mock('../http', () => ({ http: vi.fn() }));

const membersPath = '/organizations/DEFAULT/environments/env-1/domains/d1/applications/app-1/members';

describe('memberships api', () => {
    beforeEach(() => {
        vi.mocked(http).mockReset();
    });

    it('resolves the role and member names from the metadata, with the fallbacks for a missing entry', async () => {
        vi.mocked(http).mockResolvedValueOnce({
            memberships: [
                { id: 'm1', memberId: 'u1', memberType: 'user', roleId: 'r1' },
                { id: 'm2', memberId: 'g1', memberType: 'group', roleId: 'gone' },
                { id: 'm3', memberId: 'u2', memberType: 'user', roleId: 'r1' },
                { id: 'm4', memberId: 'g2', memberType: 'group', roleId: 'r1' },
            ],
            metadata: {
                roles: { r1: { name: 'APPLICATION_OWNER' } },
                users: { u1: { displayName: 'Ada' } },
                groups: { g1: { displayName: 'Admins' } },
            },
        });

        const members = await listMembers(membersPath);

        expect(http).toHaveBeenCalledWith(membersPath);
        expect(members).toEqual([
            { id: 'm1', memberId: 'u1', memberType: 'user', roleId: 'r1', roleName: 'APPLICATION_OWNER', name: 'Ada' },
            { id: 'm2', memberId: 'g1', memberType: 'group', roleId: 'gone', roleName: 'Unknown role', name: 'Admins' },
            { id: 'm3', memberId: 'u2', memberType: 'user', roleId: 'r1', roleName: 'APPLICATION_OWNER', name: 'Unknown user' },
            { id: 'm4', memberId: 'g2', memberType: 'group', roleId: 'r1', roleName: 'APPLICATION_OWNER', name: 'Unknown group' },
        ]);
    });

    it('posts the member id, the member type and the role', async () => {
        vi.mocked(http).mockResolvedValueOnce({});

        await saveMember(membersPath, { memberId: 'u1', memberType: 'USER', role: 'r1' });

        const [path, init] = vi.mocked(http).mock.calls[0];
        expect(path).toBe(membersPath);
        expect(init?.method).toBe('POST');
        expect(JSON.parse(init?.body as string)).toEqual({ memberId: 'u1', memberType: 'USER', role: 'r1' });
    });

    it('deletes a membership by its id', async () => {
        vi.mocked(http).mockResolvedValueOnce(undefined);

        await removeMember(membersPath, 'm/1');

        expect(http).toHaveBeenCalledWith(`${membersPath}/m%2F1`, { method: 'DELETE' });
    });

    it('lists the organization roles of a type', async () => {
        vi.mocked(http).mockResolvedValueOnce([]);

        await listMembershipRoles('DEFAULT', 'APPLICATION');

        expect(http).toHaveBeenCalledWith('/organizations/DEFAULT/roles?type=APPLICATION');
    });

    it('searches the organization users with a prefix match on the first 30', async () => {
        vi.mocked(http).mockResolvedValueOnce({ data: [{ id: 'u1', username: 'ada' }], currentPage: 0, totalCount: 1 });

        const users = await searchOrganizationUsers('DEFAULT', 'ad a');

        expect(http).toHaveBeenCalledWith('/organizations/DEFAULT/users?q=ad%20a*&page=0&size=30');
        expect(users).toEqual([{ id: 'u1', username: 'ada' }]);
    });

    it('lists the organization groups', async () => {
        vi.mocked(http).mockResolvedValueOnce({ data: [{ id: 'g1', name: 'Admins' }], currentPage: 0, totalCount: 1 });

        const groups = await listOrganizationGroups('DEFAULT');

        expect(http).toHaveBeenCalledWith('/organizations/DEFAULT/groups');
        expect(groups).toEqual([{ id: 'g1', name: 'Admins' }]);
    });
});
