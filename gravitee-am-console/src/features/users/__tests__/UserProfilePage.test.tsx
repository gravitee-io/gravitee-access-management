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
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route } from 'react-router-dom';
import { searchApplications } from '../../../lib/api/applications';
import { HttpError } from '../../../lib/api/http';
import { getActivePasswordPolicy } from '../../../lib/api/password-policies';
import {
    deleteUser,
    getUser,
    isEmailRequired,
    resetUserPassword,
    unlockUser,
    updateUser,
    updateUsername,
    updateUserStatus,
    type User,
} from '../../../lib/api/users';
import { UserLayout } from '../UserLayout';
import { UserProfilePage } from '../UserProfilePage';
import { currentDomain, ref, renderRoutes, usersPath } from './render';

vi.mock('../../../lib/api/users', async importOriginal => ({
    ...(await importOriginal<object>()),
    deleteUser: vi.fn(),
    getUser: vi.fn(),
    isEmailRequired: vi.fn(),
    resetUserPassword: vi.fn(),
    unlockUser: vi.fn(),
    updateUser: vi.fn(),
    updateUsername: vi.fn(),
    updateUserStatus: vi.fn(),
}));
vi.mock('../../../lib/api/applications', () => ({ searchApplications: vi.fn() }));
vi.mock('../../../lib/api/password-policies', () => ({ getActivePasswordPolicy: vi.fn(), evaluatePassword: vi.fn() }));
vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const ALL = ['domain_user_read', 'domain_user_update', 'domain_user_delete'];
const userPath = `${usersPath}/u-1`;

const alice: User = {
    id: 'u-1',
    username: 'alice',
    displayName: 'Alice Liddell',
    firstName: 'Alice',
    lastName: 'Liddell',
    email: 'alice@acme.com',
    enabled: true,
    internal: true,
    referenceType: 'domain',
    accountNonLocked: true,
    client: 'app-1',
    applicationEntity: { id: 'app-1', name: 'Portal' },
    additionalInformation: { team: 'blue', address: { city: 'Oxford' }, hidden: null },
    forceResetPassword: false,
    sourceId: 'idp-1',
    source: 'Default Identity Provider',
    loginsCount: 3,
};

function setup(user: User = alice, permissions = ALL) {
    vi.mocked(getUser).mockResolvedValue(user);
    renderRoutes(
        `${userPath}/profile`,
        <>
            <Route path={usersPath} element={<p>Users list</p>} />
            <Route path={`${usersPath}/:userId`} element={<UserLayout />}>
                <Route path="profile" element={<UserProfilePage />} />
            </Route>
        </>,
        permissions,
    );
    return {
        openAction: async (name: string) => {
            await userEvent.click(await screen.findByRole('button', { name: 'Actions' }));
            await userEvent.click(await screen.findByRole('menuitem', { name }));
        },
        confirm: async (title: string, label: string) => {
            const dialog = await screen.findByRole('dialog', { name: title });
            await userEvent.click(within(dialog).getByRole('button', { name: label }));
        },
    };
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(isEmailRequired).mockResolvedValue(true);
    vi.mocked(searchApplications).mockResolvedValue({ data: [], currentPage: 0, totalCount: 0 });
    vi.mocked(getActivePasswordPolicy).mockResolvedValue(null);
});

describe('UserLayout and UserProfilePage', () => {
    it('shows the user, its status and its activity', async () => {
        setup();

        expect(await screen.findByRole('heading', { name: 'Alice Liddell' })).toBeInTheDocument();
        expect(screen.getByText('Enabled')).toBeInTheDocument();
        expect(screen.getByText('Internal')).toBeInTheDocument();
        expect(screen.queryByText('Pre-registered')).not.toBeInTheDocument();
        expect(screen.getByText('The username of the user.')).toBeInTheDocument();
        expect(screen.getByText('Logins').nextSibling).toHaveTextContent('3');
        expect(screen.getByText('Last application').nextSibling).toHaveTextContent('Portal');
        expect(getUser).toHaveBeenCalledWith(ref, 'u-1');
    });

    it('marks an external user and a pending pre-registration', async () => {
        setup({ ...alice, internal: false, preRegistration: true, registrationCompleted: false });

        expect(await screen.findByText('External')).toBeInTheDocument();
        expect(screen.getByText('Pre-registered')).toBeInTheDocument();
    });

    it('saves the eight profile fields, keeping the stored object and null attributes', async () => {
        const updated = { ...alice, firstName: 'Alicia', displayName: 'Alicia Liddell' };
        vi.mocked(updateUser).mockResolvedValue(updated);
        setup();
        const firstName = await screen.findByLabelText('First name');

        await userEvent.clear(firstName);
        await userEvent.type(firstName, 'Alicia');
        await userEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateUser).toHaveBeenCalledWith(ref, 'u-1', {
                firstName: 'Alicia',
                lastName: 'Liddell',
                displayName: 'Alicia Liddell',
                email: 'alice@acme.com',
                enabled: true,
                client: 'app-1',
                additionalInformation: { hidden: null, team: 'blue', address: { city: 'Oxford' } },
                forceResetPassword: false,
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('User updated');
    });

    it('keeps a stored attribute name read-only and drops a removed attribute', async () => {
        vi.mocked(updateUser).mockResolvedValue(alice);
        setup();
        const names = await screen.findAllByLabelText('Attribute name');

        expect(names[0]).toBeDisabled();
        expect(screen.getByLabelText('Value of address')).toBeDisabled();
        await userEvent.click(screen.getByRole('button', { name: 'Remove team' }));
        await userEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateUser).toHaveBeenCalledWith(
                ref,
                'u-1',
                expect.objectContaining({ additionalInformation: { hidden: null, address: { city: 'Oxford' } } }),
            ),
        );
    });

    it('blocks the save while a shown field is empty', async () => {
        setup();
        await userEvent.clear(await screen.findByLabelText('Last name'));

        expect(screen.getByLabelText('Last name')).toHaveAccessibleDescription('Enter last name');
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('blocks the save until a new attribute has a name and a value', async () => {
        setup();
        await userEvent.click(await screen.findByRole('button', { name: 'Add attribute' }));
        const names = screen.getAllByLabelText('Attribute name');
        const name = names[names.length - 1];

        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
        await userEvent.type(name, 'role');
        expect(screen.getByLabelText('Value of role')).toHaveAccessibleDescription('Enter a value');
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
        await userEvent.type(screen.getByLabelText('Value of role'), 'admin');

        expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    });

    it('updates the username after a confirmation', async () => {
        vi.mocked(updateUsername).mockResolvedValue({ ...alice, username: 'alice2' });
        const harness = setup();
        const username = await screen.findByRole('textbox', { name: 'Username' });

        await userEvent.clear(username);
        await userEvent.type(username, 'alice2');
        await userEvent.click(screen.getByRole('button', { name: 'Update username' }));
        expect(await screen.findByText(/It also unlocks the account\./)).toBeInTheDocument();
        await harness.confirm('Update username', 'Update');

        await waitFor(() => expect(updateUsername).toHaveBeenCalledWith(ref, 'u-1', 'alice2'));
        expect(toast.success).toHaveBeenCalledWith('Username updated');
    });

    it('rejects a username that starts with a space', async () => {
        setup();
        const username = await screen.findByRole('textbox', { name: 'Username' });

        await userEvent.clear(username);
        await userEvent.type(username, ' alice');

        expect(username).toHaveAccessibleDescription('Username cannot start or end with whitespace');
        expect(screen.getByRole('button', { name: 'Update username' })).toBeDisabled();
    });

    it('disables the user after a confirmation, then reloads it', async () => {
        vi.mocked(updateUserStatus).mockResolvedValue({ ...alice, enabled: false });
        const harness = setup();
        await screen.findByText('Enabled');
        vi.mocked(getUser).mockResolvedValue({ ...alice, enabled: false });

        await harness.openAction('Disable user');
        await harness.confirm('Disable user', 'Disable');

        await waitFor(() => expect(updateUserStatus).toHaveBeenCalledWith(ref, 'u-1', false));
        expect(toast.success).toHaveBeenCalledWith('User disabled');
        expect(await screen.findByText('Disabled')).toBeInTheDocument();
    });

    it('keeps an unsaved edit when an action reloads the user', async () => {
        vi.mocked(updateUserStatus).mockResolvedValue({ ...alice, enabled: false });
        const harness = setup();
        const firstName = await screen.findByLabelText('First name');
        await userEvent.clear(firstName);
        await userEvent.type(firstName, 'Alicia');
        vi.mocked(getUser).mockResolvedValue({ ...alice, enabled: false });

        await harness.openAction('Disable user');
        await harness.confirm('Disable user', 'Disable');

        expect(await screen.findByText('Disabled')).toBeInTheDocument();
        expect(screen.getByLabelText('First name')).toHaveValue('Alicia');
        expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    });

    it('asks for a username when the field is emptied', async () => {
        setup();
        const username = await screen.findByRole('textbox', { name: 'Username' });

        await userEvent.clear(username);

        expect(username).toHaveAccessibleDescription('Enter username');
    });

    it('unlocks a locked user', async () => {
        vi.mocked(unlockUser).mockResolvedValue();
        const harness = setup({ ...alice, accountNonLocked: false, accountLockedUntil: Date.now() + 60_000 });

        expect(await screen.findByText('Locked')).toBeInTheDocument();
        vi.mocked(getUser).mockResolvedValue(alice);
        await harness.openAction('Unlock account');
        await harness.confirm('Unlock user', 'Unlock');

        await waitFor(() => expect(unlockUser).toHaveBeenCalledWith(ref, 'u-1'));
        expect(await screen.findByText('Enabled')).toBeInTheDocument();
    });

    it('resets the password', async () => {
        vi.mocked(resetUserPassword).mockResolvedValue();
        const harness = setup();

        await harness.openAction('Reset password');
        const dialog = await screen.findByRole('dialog', { name: 'Reset password' });
        await userEvent.type(within(dialog).getByLabelText('New password'), 'n3w-password');
        await userEvent.click(within(dialog).getByRole('button', { name: 'Reset password' }));

        await waitFor(() => expect(resetUserPassword).toHaveBeenCalledWith(ref, 'u-1', 'n3w-password'));
        expect(getActivePasswordPolicy).toHaveBeenCalledWith(ref, 'idp-1');
        expect(toast.success).toHaveBeenCalledWith('Password reset');
    });

    it('deletes the user after a confirmation, then goes back to the list', async () => {
        vi.mocked(deleteUser).mockResolvedValue();
        const harness = setup();

        await harness.openAction('Delete user');
        await harness.confirm('Delete user', 'Delete');

        await waitFor(() => expect(deleteUser).toHaveBeenCalledWith(ref, 'u-1'));
        expect(toast.success).toHaveBeenCalledWith('User alice deleted');
        expect(await screen.findByText('Users list')).toBeInTheDocument();
    });

    it('shows the profile read-only, with no action, to a reader', async () => {
        setup(alice, ['domain_user_read']);

        expect(await screen.findByLabelText('First name')).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Actions' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Update username' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Add attribute' })).not.toBeInTheDocument();
        expect(screen.queryByLabelText('Application')).not.toBeInTheDocument();
    });

    it('shows the not found panel for a deleted user', async () => {
        vi.mocked(getUser).mockRejectedValue(new HttpError(404, 'User not found'));
        renderRoutes(`${userPath}/profile`, <Route path={`${usersPath}/:userId/*`} element={<UserLayout />} />, ALL);

        expect(await screen.findByText('This user does not exist or has been deleted.')).toBeInTheDocument();
    });
});
