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
import { toMembers } from '../memberships';

describe('toMembers', () => {
    it('resolves the role and member names from the metadata, with the Angular fallbacks', () => {
        const members = toMembers({
            memberships: [
                { id: 'm1', memberId: 'u1', memberType: 'user', roleId: 'r1' },
                { id: 'm2', memberId: 'g1', memberType: 'group', roleId: 'gone' },
                { id: 'm3', memberId: 'u2', memberType: 'user', roleId: 'r1' },
            ],
            metadata: {
                roles: { r1: { name: 'DOMAIN_OWNER' } },
                users: { u1: { displayName: 'Ada' } },
                groups: { g1: { displayName: 'Admins' } },
            },
        });

        expect(members.map(member => [member.name, member.roleName])).toEqual([
            ['Ada', 'DOMAIN_OWNER'],
            ['Admins', 'Unknown role'],
            ['Unknown user', 'DOMAIN_OWNER'],
        ]);
    });
});
