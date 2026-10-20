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
import { TooltipProvider } from '@gravitee/graphene-core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { listMembershipRoles, type Member, searchOrganizationUsers } from '../../../lib/api/memberships';
import { MembershipsPanel } from '../MembershipsPanel';

vi.mock('../../../lib/api/memberships', () => ({
    listMembershipRoles: vi.fn(),
    listOrganizationGroups: vi.fn(),
    searchOrganizationUsers: vi.fn(),
}));
vi.mock('../../../lib/session/session', () => ({
    useSession: () => ({ user: { sub: 'me', org: 'DEFAULT', permissions: [] }, environments: [] }),
}));

const members: Member[] = [
    { id: 'm1', memberId: 'owner', memberType: 'user', roleId: 'r-po', roleName: 'DOMAIN_PRIMARY_OWNER', name: 'Owner' },
    { id: 'm2', memberId: 'bob', memberType: 'user', roleId: 'r-user', roleName: 'DOMAIN_USER', name: 'Bob' },
    { id: 'm3', memberId: 'me', memberType: 'user', roleId: 'r-owner', roleName: 'DOMAIN_OWNER', name: 'Me' },
];
const listMembers = vi.fn();
const saveMember = vi.fn();
const removeMember = vi.fn();

function renderPanel(rights = { canCreate: true, canUpdate: true, canDelete: true }) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <TooltipProvider>
                <MembershipsPanel
                    queryKey={['domain-members', 'd1']}
                    listMembers={listMembers}
                    saveMember={saveMember}
                    removeMember={removeMember}
                    roleType="DOMAIN"
                    resourceLabel="security domain"
                    {...rights}
                />
            </TooltipProvider>
        </QueryClientProvider>,
    );
}

function pick(trigger: HTMLElement, option: string) {
    fireEvent.pointerDown(trigger, { button: 0, ctrlKey: false, pointerType: 'mouse' });
    fireEvent.click(screen.getByRole('option', { name: option }));
}

beforeEach(() => {
    vi.clearAllMocks();
    listMembers.mockResolvedValue(members);
    saveMember.mockResolvedValue({});
    removeMember.mockResolvedValue(undefined);
    vi.mocked(listMembershipRoles).mockResolvedValue([
        { id: 'r-po', name: 'DOMAIN_PRIMARY_OWNER', system: true },
        { id: 'r-owner', name: 'DOMAIN_OWNER', system: false },
        { id: 'r-user', name: 'DOMAIN_USER', system: false },
    ]);
});

describe('MembershipsPanel', () => {
    it('locks the primary owner and the signed-in user, and lets the others change', async () => {
        renderPanel();

        const table = await screen.findByRole('table', { name: 'Members' });
        expect(within(table).getByText('DOMAIN_PRIMARY_OWNER')).toBeInTheDocument();
        expect(within(table).getByText('DOMAIN_OWNER')).toBeInTheDocument();
        expect(screen.getByRole('combobox', { name: 'Role of Bob' })).toBeEnabled();
        expect(screen.queryByRole('combobox', { name: 'Role of Owner' })).not.toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: 'Role of Me' })).not.toBeInTheDocument();
        expect(screen.getAllByRole('button', { name: /^Delete / })).toHaveLength(1);
        expect(listMembershipRoles).toHaveBeenCalledWith('DEFAULT', 'DOMAIN');
    });

    it('deletes a member after the confirmation', async () => {
        renderPanel();

        fireEvent.click(await screen.findByRole('button', { name: 'Delete Bob' }));
        fireEvent.click(within(screen.getByRole('dialog', { name: 'Delete member' })).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(removeMember).toHaveBeenCalledWith('m2'));
        await waitFor(() => expect(listMembers).toHaveBeenCalledTimes(2));
    });

    it('changes a role after the confirmation', async () => {
        renderPanel();

        pick(await screen.findByRole('combobox', { name: 'Role of Bob' }), 'DOMAIN_OWNER');
        fireEvent.click(within(screen.getByRole('dialog', { name: 'Update member' })).getByRole('button', { name: 'Update' }));

        await waitFor(() => expect(saveMember).toHaveBeenCalledWith({ memberId: 'bob', memberType: 'USER', role: 'r-owner' }));
    });

    it('adds a user found by the search, without the primary owner role on offer', async () => {
        vi.mocked(searchOrganizationUsers).mockResolvedValue([
            { id: 'bob', username: 'bob' },
            { id: 'alice', username: 'alice', displayName: 'Alice', source: 'Inline users' },
        ]);
        renderPanel();

        fireEvent.click(await screen.findByRole('button', { name: 'Add member' }));
        const dialog = screen.getByRole('dialog', { name: 'Add member' });
        fireEvent.change(within(dialog).getByRole('textbox', { name: 'User' }), { target: { value: 'al' } });
        const matches = await within(dialog).findByRole('list', { name: 'Matching users' });
        expect(within(matches).queryByText('bob')).not.toBeInTheDocument();
        fireEvent.click(within(matches).getByRole('button', { name: /Alice/ }));
        fireEvent.pointerDown(within(dialog).getByRole('combobox', { name: 'Role' }), { button: 0, ctrlKey: false, pointerType: 'mouse' });

        expect(screen.getByRole('option', { name: 'DOMAIN_PRIMARY_OWNER' })).toHaveAttribute('aria-disabled', 'true');

        fireEvent.click(screen.getByRole('option', { name: 'DOMAIN_USER' }));

        fireEvent.click(within(dialog).getByRole('button', { name: 'Add member' }));

        await waitFor(() => expect(saveMember).toHaveBeenCalledWith({ memberId: 'alice', memberType: 'USER', role: 'r-user' }));
        expect(searchOrganizationUsers).toHaveBeenCalledWith('DEFAULT', 'al');
    });

    it('is read-only without the member permissions', async () => {
        renderPanel({ canCreate: false, canUpdate: false, canDelete: false });

        expect(await screen.findByRole('combobox', { name: 'Role of Bob' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Add member' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /^Delete / })).not.toBeInTheDocument();
    });
});
