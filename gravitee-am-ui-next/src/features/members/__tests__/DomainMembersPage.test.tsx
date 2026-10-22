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
import type * as Graphene from '@gravitee/graphene-core';
import { LayoutSlotsProvider, TooltipProvider } from '@gravitee/graphene-core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { addDomainMember, type Domain, listDomainMembers, removeDomainMember } from '../../../lib/api/domains';
import type * as Memberships from '../../../lib/api/memberships';
import { listMembershipRoles } from '../../../lib/api/memberships';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainMembersPage } from '../DomainMembersPage';

const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/domains', () => ({ listDomainMembers: vi.fn(), addDomainMember: vi.fn(), removeDomainMember: vi.fn() }));
vi.mock('../../../lib/api/memberships', async importOriginal => ({
    ...(await importOriginal<typeof Memberships>()),
    listMembershipRoles: vi.fn(),
}));
vi.mock('../../../lib/session/session', () => ({
    useSession: () => ({ user: { sub: 'me', org: 'DEFAULT', permissions: [] }, environments: [] }),
}));
vi.mock('../../../lib/session/domain', () => ({
    useCurrentDomain: () => ({
        domain: { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true } satisfies Domain,
        ref: { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' },
        basePath: '/environments/dev/domains/acme',
        domainsPath: '/environments/dev/domains',
    }),
}));

function renderPage(permissions: string[]) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter>
                    <LayoutSlotsProvider>
                        <TooltipProvider>
                            <DomainMembersPage />
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

const all = ['domain_member_list', 'domain_member_create', 'domain_member_update', 'domain_member_delete'];

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listDomainMembers).mockResolvedValue({
        memberships: [
            { id: 'm1', memberId: 'bob', memberType: 'user', roleId: 'r-user' },
            { id: 'm2', memberId: 'g1', memberType: 'group', roleId: 'r-owner' },
        ],
        metadata: {
            roles: { 'r-user': { name: 'DOMAIN_USER' }, 'r-owner': { name: 'DOMAIN_OWNER' } },
            users: { bob: { displayName: 'Bob' } },
            groups: { g1: { name: 'Admins' } },
        },
    });
    vi.mocked(listMembershipRoles).mockResolvedValue([
        { id: 'r-owner', name: 'DOMAIN_OWNER', system: false },
        { id: 'r-user', name: 'DOMAIN_USER', system: false },
    ]);
    vi.mocked(addDomainMember).mockResolvedValue({});
    vi.mocked(removeDomainMember).mockResolvedValue(undefined);
});

describe('DomainMembersPage', () => {
    it('lists the domain members with their names and roles', async () => {
        renderPage(all);

        const table = await screen.findByRole('table', { name: 'Members' });
        expect(within(table).getByText('Bob')).toBeInTheDocument();
        expect(within(table).getByText('Admins')).toBeInTheDocument();
        expect(listDomainMembers).toHaveBeenCalledWith(ref);
        expect(listMembershipRoles).toHaveBeenCalledWith('DEFAULT', 'DOMAIN');
    });

    it('changes a role with the Angular addMember payload', async () => {
        renderPage(all);

        const trigger = await screen.findByRole('combobox', { name: 'Role of Bob' });
        fireEvent.pointerDown(trigger, { button: 0, ctrlKey: false, pointerType: 'mouse' });
        fireEvent.click(screen.getByRole('option', { name: 'DOMAIN_OWNER' }));
        fireEvent.click(within(screen.getByRole('dialog', { name: 'Update member' })).getByRole('button', { name: 'Update' }));

        await waitFor(() => expect(addDomainMember).toHaveBeenCalledWith(ref, { memberId: 'bob', memberType: 'USER', role: 'r-owner' }));
    });

    it('removes a member after the confirmation', async () => {
        renderPage(all);

        fireEvent.click(await screen.findByRole('button', { name: 'Delete Bob' }));
        fireEvent.click(within(screen.getByRole('dialog', { name: 'Delete member' })).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(removeDomainMember).toHaveBeenCalledWith(ref, 'm1'));
    });

    it('hides the add and delete actions without domain_member_create and domain_member_delete', async () => {
        renderPage(['domain_member_list']);

        expect(await screen.findByRole('table', { name: 'Members' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Add member' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete Bob' })).not.toBeInTheDocument();
        expect(screen.getByRole('combobox', { name: 'Role of Bob' })).toBeDisabled();
    });
});
