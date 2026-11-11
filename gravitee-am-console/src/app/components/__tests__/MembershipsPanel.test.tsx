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
import { toast } from '@gravitee/graphene-core';
import { buttonHarness, renderWithGraphene, selectHarness } from '@gravitee/graphene-core/testing';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ReactNode } from 'react';
import { HttpError } from '../../../lib/api/http';
import {
    listMembers,
    listMembershipRoles,
    listOrganizationGroups,
    type Member,
    removeMember,
    saveMember,
    searchOrganizationUsers,
} from '../../../lib/api/memberships';
import { MembershipsPanel } from '../MembershipsPanel';

vi.mock('../../../lib/api/memberships', () => ({
    listMembers: vi.fn(),
    listMembershipRoles: vi.fn(),
    listOrganizationGroups: vi.fn(),
    removeMember: vi.fn(),
    saveMember: vi.fn(),
    searchOrganizationUsers: vi.fn(),
}));
vi.mock('../../../lib/session/session', () => ({
    useSession: () => ({ user: { sub: 'me', org: 'DEFAULT', permissions: [] }, environments: [] }),
}));
vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));

const membersPath = '/organizations/DEFAULT/environments/env-1/domains/d1/applications/app-1/members';

const members: Member[] = [
    { id: 'm4', memberId: 'g1', memberType: 'group', roleId: 'r-user', roleName: 'APPLICATION_USER', name: 'Admins' },
    { id: 'm1', memberId: 'owner', memberType: 'user', roleId: 'r-po', roleName: 'APPLICATION_PRIMARY_OWNER', name: 'Owner' },
    { id: 'm2', memberId: 'bob', memberType: 'user', roleId: 'r-user', roleName: 'APPLICATION_USER', name: 'Bob' },
    { id: 'm3', memberId: 'me', memberType: 'user', roleId: 'r-owner', roleName: 'APPLICATION_OWNER', name: 'Me' },
];

const ALL = { canCreate: true, canUpdate: true, canDelete: true };

function renderPanel(rights = ALL) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const wrapper = ({ children }: { readonly children: ReactNode }) => (
        <QueryClientProvider client={client}>{children}</QueryClientProvider>
    );
    renderWithGraphene(<MembershipsPanel membersPath={membersPath} roleType="APPLICATION" resourceLabel="application" {...rights} />, {
        wrapper,
    });
}

async function openAddDialog() {
    await userEvent.click(await screen.findByRole('button', { name: 'Add member' }));
    return screen.getByRole('dialog', { name: 'Add member' });
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listMembers).mockResolvedValue(members);
    vi.mocked(saveMember).mockResolvedValue({});
    vi.mocked(removeMember).mockResolvedValue(undefined);
    vi.mocked(listMembershipRoles).mockResolvedValue([
        { id: 'r-po', name: 'APPLICATION_PRIMARY_OWNER', system: true },
        { id: 'r-owner', name: 'APPLICATION_OWNER', system: false },
        { id: 'r-user', name: 'APPLICATION_USER', system: false },
    ]);
    vi.mocked(listOrganizationGroups).mockResolvedValue([
        { id: 'g1', name: 'Admins' },
        { id: 'g2', name: 'Auditors' },
    ]);
});

describe('MembershipsPanel', () => {
    it('lists the users before the groups, with the roles of the resource type', async () => {
        renderPanel();

        const table = await screen.findByRole('table');
        const names = within(table)
            .getAllByRole('row')
            .slice(1)
            .map(row => within(row).getAllByRole('cell')[0]?.textContent);
        expect(names).toEqual(['Owner', 'Bob', 'Me', 'Admins']);
        expect(listMembers).toHaveBeenCalledWith(membersPath);
        expect(listMembershipRoles).toHaveBeenCalledWith('DEFAULT', 'APPLICATION');
    });

    it('keeps the primary owner and the signed-in user read-only', async () => {
        renderPanel();

        await screen.findByRole('table');
        expect(screen.getByText('APPLICATION_PRIMARY_OWNER')).toBeInTheDocument();
        expect(screen.getByText('APPLICATION_OWNER')).toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: 'Role of Owner' })).not.toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: 'Role of Me' })).not.toBeInTheDocument();
        expect(screen.getByRole('combobox', { name: 'Role of Bob' })).toBeEnabled();
        expect(screen.getAllByRole('button', { name: /^Remove / }).map(button => button.getAttribute('aria-label'))).toEqual([
            'Remove Bob',
            'Remove Admins',
        ]);
    });

    it('shows the empty state without members', async () => {
        vi.mocked(listMembers).mockResolvedValue([]);
        renderPanel();

        expect(await screen.findByText('No members yet.')).toBeInTheDocument();
    });

    it('shows an error when the members do not load', async () => {
        vi.mocked(listMembers).mockRejectedValue(new HttpError(500, 'boom'));
        renderPanel();

        expect(await screen.findByText('Could not load the members.')).toBeInTheDocument();
    });

    it('lists the members with read-only roles when the roles do not load', async () => {
        vi.mocked(listMembershipRoles).mockRejectedValue(new HttpError(403, 'Forbidden'));
        renderPanel();

        await screen.findByRole('table');
        expect(screen.queryByRole('combobox', { name: 'Role of Bob' })).not.toBeInTheDocument();
        expect(screen.getAllByText('APPLICATION_USER')).toHaveLength(2);
    });

    it('removes a member after the confirmation, then reloads the list', async () => {
        renderPanel();

        await userEvent.click(await screen.findByRole('button', { name: 'Remove Bob' }));
        await userEvent.click(within(screen.getByRole('dialog', { name: 'Remove member' })).getByRole('button', { name: 'Remove' }));

        await waitFor(() => expect(removeMember).toHaveBeenCalledWith(membersPath, 'm2'));
        expect(toast.success).toHaveBeenCalledWith('Member deleted');
        await waitFor(() => expect(listMembers).toHaveBeenCalledTimes(2));
    });

    it('changes a role after the confirmation', async () => {
        renderPanel();

        await screen.findByRole('table');
        await selectHarness({ name: 'Role of Admins' }).selectOption('APPLICATION_OWNER');
        await userEvent.click(within(screen.getByRole('dialog', { name: 'Update member' })).getByRole('button', { name: 'Update' }));

        await waitFor(() => expect(saveMember).toHaveBeenCalledWith(membersPath, { memberId: 'g1', memberType: 'GROUP', role: 'r-owner' }));
        expect(toast.success).toHaveBeenCalledWith('Member updated');
    });

    it('keeps the role when the change is cancelled', async () => {
        renderPanel();

        await screen.findByRole('table');
        await selectHarness({ name: 'Role of Bob' }).selectOption('APPLICATION_OWNER');
        await userEvent.click(within(screen.getByRole('dialog', { name: 'Update member' })).getByRole('button', { name: 'Cancel' }));

        expect(saveMember).not.toHaveBeenCalled();
        expect(selectHarness({ name: 'Role of Bob' }).getValue()).toBe('APPLICATION_USER');
    });

    it('adds a user found by the search, without the members already listed or the primary owner role', async () => {
        vi.mocked(searchOrganizationUsers).mockResolvedValue([
            { id: 'bob', username: 'bob' },
            { id: 'alice', username: 'alice', displayName: 'Alice', source: 'Inline users' },
        ]);
        renderPanel();
        const dialog = await openAddDialog();

        await userEvent.type(within(dialog).getByRole('textbox', { name: 'User' }), 'al');
        const matches = await within(dialog).findByRole('list', { name: 'Matching users' });
        expect(
            within(matches)
                .getAllByRole('button')
                .map(button => button.textContent),
        ).toEqual(['Alicealice · Inline users']);
        await userEvent.click(within(matches).getByRole('button', { name: /Alice/ }));
        await selectHarness({ name: 'Role' }).open();
        expect(screen.getByRole('option', { name: 'APPLICATION_PRIMARY_OWNER' })).toHaveAttribute('aria-disabled', 'true');
        await userEvent.click(screen.getByRole('option', { name: 'APPLICATION_USER' }));
        await buttonHarness({ name: 'Add member', within: dialog }).click();

        await waitFor(() =>
            expect(saveMember).toHaveBeenCalledWith(membersPath, { memberId: 'alice', memberType: 'USER', role: 'r-user' }),
        );
        expect(searchOrganizationUsers).toHaveBeenLastCalledWith('DEFAULT', 'al');
        expect(toast.success).toHaveBeenCalledWith('Member added');
    });

    it('adds a group that is not a member yet', async () => {
        renderPanel();
        const dialog = await openAddDialog();

        await selectHarness({ name: 'Type' }).selectOption('Group');
        await selectHarness({ name: 'Group' }).open();
        expect(screen.getAllByRole('option').map(option => option.textContent)).toEqual(['Auditors']);
        await userEvent.click(screen.getByRole('option', { name: 'Auditors' }));
        await selectHarness({ name: 'Role' }).selectOption('APPLICATION_OWNER');
        await buttonHarness({ name: 'Add member', within: dialog }).click();

        await waitFor(() => expect(saveMember).toHaveBeenCalledWith(membersPath, { memberId: 'g2', memberType: 'GROUP', role: 'r-owner' }));
    });

    it.each(['Type', 'Group', 'Role'])('opens the %s list below its field in the add dialog', async name => {
        renderPanel();
        await openAddDialog();
        if (name === 'Group') {
            await selectHarness({ name: 'Type' }).selectOption('Group');
        }

        await selectHarness({ name }).open();

        expect(screen.getByRole('listbox').closest('[data-side]')).toHaveAttribute('data-side', 'bottom');
    });

    it('keeps the browser autofill off the user search', async () => {
        renderPanel();
        const dialog = await openAddDialog();
        const search = within(dialog).getByRole('textbox', { name: 'User' });

        expect(search).toHaveAttribute('autocomplete', 'off');
        expect(search.getAttribute('placeholder')).not.toMatch(/name/i);
    });

    it('shows an error when the user search fails', async () => {
        vi.mocked(searchOrganizationUsers).mockRejectedValue(new HttpError(403, 'Forbidden'));
        renderPanel();
        const dialog = await openAddDialog();

        await userEvent.type(within(dialog).getByRole('textbox', { name: 'User' }), 'al');

        expect(await within(dialog).findByRole('alert')).toHaveTextContent('Could not search the users. Forbidden');
        expect(within(dialog).queryByRole('list', { name: 'Matching users' })).not.toBeInTheDocument();
    });

    it('shows an error when the groups do not load', async () => {
        vi.mocked(listOrganizationGroups).mockRejectedValue(new HttpError(403, 'Forbidden'));
        renderPanel();
        const dialog = await openAddDialog();

        await selectHarness({ name: 'Type' }).selectOption('Group');

        expect(await within(dialog).findByRole('alert')).toHaveTextContent('Could not load the groups. Forbidden');
    });

    it('reports a failed save with the server message', async () => {
        vi.mocked(saveMember).mockRejectedValue(new HttpError(400, 'Role not allowed'));
        renderPanel();

        await screen.findByRole('table');
        await selectHarness({ name: 'Role of Bob' }).selectOption('APPLICATION_OWNER');
        await userEvent.click(within(screen.getByRole('dialog', { name: 'Update member' })).getByRole('button', { name: 'Update' }));

        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Could not save the member. Role not allowed'));
    });

    it('is read-only without the member permissions', async () => {
        renderPanel({ canCreate: false, canUpdate: false, canDelete: false });

        await screen.findByRole('table');
        expect(selectHarness({ name: 'Role of Bob' }).isDisabled()).toBe(true);
        expect(screen.queryByRole('button', { name: 'Add member' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /^Remove / })).not.toBeInTheDocument();
    });

    it('applies each member permission on its own', async () => {
        renderPanel({ canCreate: false, canUpdate: false, canDelete: true });

        await screen.findByRole('table');
        expect(screen.queryByRole('button', { name: 'Add member' })).not.toBeInTheDocument();
        expect(selectHarness({ name: 'Role of Bob' }).isDisabled()).toBe(true);
        expect(screen.getByRole('button', { name: 'Remove Bob' })).toBeInTheDocument();
    });
});
