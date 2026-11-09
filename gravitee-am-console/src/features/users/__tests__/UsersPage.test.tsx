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
import { inputHarness } from '@gravitee/graphene-core/testing';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route } from 'react-router-dom';
import { listApplications } from '../../../lib/api/applications';
import { HttpError } from '../../../lib/api/http';
import { listIdentityProviders } from '../../../lib/api/identity-providers';
import { listUsers, type User } from '../../../lib/api/users';
import { UsersPage } from '../UsersPage';
import { currentDomain, ref, renderRoutes, usersPath } from './render';

vi.mock('../../../lib/api/users', async importOriginal => ({ ...(await importOriginal<object>()), listUsers: vi.fn() }));
vi.mock('../../../lib/api/applications', () => ({ listApplications: vi.fn() }));
vi.mock('../../../lib/api/identity-providers', () => ({ listIdentityProviders: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const ALL = ['domain_user_list', 'domain_user_read', 'domain_user_create'];

const alice: User = {
    id: 'u-1',
    username: 'alice',
    displayName: 'Alice Liddell',
    enabled: true,
    accountNonLocked: true,
    source: 'Default Identity Provider',
    loggedAt: 1_700_000_000_000,
};
const bob: User = { id: 'u-2', username: 'bob', displayName: 'Bob', enabled: false, accountNonLocked: true, source: 'LDAP' };

function setup(permissions = ALL) {
    renderRoutes(usersPath, <Route path={usersPath} element={<UsersPage />} />, permissions);
    return {
        search: async (text: string) => {
            await screen.findByRole('searchbox', { name: 'Search users' });
            await inputHarness({ name: 'Search users' }).type(text);
        },
        pickFilter: async (title: string, option: string) => {
            await userEvent.click(screen.getByRole('button', { name: new RegExp(title) }));
            await userEvent.click(await screen.findByRole('option', { name: new RegExp(option) }));
            await userEvent.keyboard('{Escape}');
        },
    };
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listUsers).mockResolvedValue({ data: [alice, bob], currentPage: 0, totalCount: 2 });
    vi.mocked(listIdentityProviders).mockResolvedValue([
        { id: 'idp-1', name: 'Default Identity Provider', type: 'mongo-am-idp', system: true, external: false },
    ]);
    vi.mocked(listApplications).mockResolvedValue({ data: [{ id: 'app-1', name: 'Portal' }], currentPage: 0, totalCount: 1 });
});

describe('UsersPage', () => {
    it('lists the first 25 users with their status and identity provider', async () => {
        setup();

        expect(await screen.findByRole('link', { name: 'Alice Liddell' })).toHaveAttribute('href', `${usersPath}/u-1`);
        expect(screen.getByText('Disabled')).toBeInTheDocument();
        expect(screen.getByText('LDAP')).toBeInTheDocument();
        expect(screen.getByText('Never logged in')).toBeInTheDocument();
        expect(listUsers).toHaveBeenCalledWith(ref, { text: '' }, 0, 25);
        expect(listIdentityProviders).toHaveBeenCalledWith(ref);
        expect(listApplications).toHaveBeenCalledWith(ref, 0, 50);
    });

    it('searches with the typed text and shows the no-results state', async () => {
        const harness = setup();
        await screen.findByRole('link', { name: 'Alice Liddell' });
        vi.mocked(listUsers).mockResolvedValue({ data: [], currentPage: 0, totalCount: 0 });

        await harness.search('zed');

        await waitFor(() => expect(listUsers).toHaveBeenLastCalledWith(ref, { text: 'zed' }, 0, 25));
        expect(await screen.findByText('No users match your search')).toBeInTheDocument();
    });

    it('searches with the SCIM filter of the advanced filters, joined with or and and', async () => {
        const harness = setup();
        await screen.findByRole('link', { name: 'Alice Liddell' });

        await harness.pickFilter('Application', 'Portal');
        await harness.pickFilter('Status', 'Disabled');

        await waitFor(() => expect(listUsers).toHaveBeenLastCalledWith(ref, { filter: 'client eq "app-1" and enabled eq false' }, 0, 25));
        expect(screen.getByRole('searchbox', { name: 'Search users' })).toHaveValue('client eq "app-1" and enabled eq false');
    });

    it('keeps the search field when the API rejects the search', async () => {
        const harness = setup();
        await screen.findByRole('link', { name: 'Alice Liddell' });
        vi.mocked(listUsers).mockRejectedValue(new HttpError(400, 'Invalid filter'));

        await harness.search('enabled eq');

        expect(await screen.findByText('Could not run this search. Check the syntax of the SCIM filter.')).toBeInTheDocument();
        expect(screen.getByRole('searchbox', { name: 'Search users' })).toHaveValue('enabled eq');
        vi.mocked(listUsers).mockResolvedValue({ data: [alice], currentPage: 0, totalCount: 1 });
        await harness.search(' true');
        expect(await screen.findByRole('link', { name: 'Alice Liddell' })).toBeInTheDocument();
    });

    it('gives the SCIM syntax hint only when the API rejects the search as invalid', async () => {
        const harness = setup();
        await screen.findByRole('link', { name: 'Alice Liddell' });
        vi.mocked(listUsers).mockRejectedValue(new HttpError(500, 'Service unavailable'));

        await harness.search('zed');

        expect(await screen.findByText('Could not run this search.')).toBeInTheDocument();
    });

    it('explains the SCIM search syntax', async () => {
        setup();
        await screen.findByRole('link', { name: 'Alice Liddell' });

        await userEvent.click(screen.getByRole('button', { name: 'Search syntax' }));

        expect(await screen.findByText('enabled eq false')).toBeInTheDocument();
    });

    it('shows the first-use state when the domain has no user', async () => {
        vi.mocked(listUsers).mockResolvedValue({ data: [], currentPage: 0, totalCount: 0 });

        setup();

        expect(await screen.findByText('No users yet')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Create user' })).toHaveAttribute('href', `${usersPath}/new`);
    });

    it('shows the error panel, not the first-use state, when the list fails', async () => {
        vi.mocked(listUsers).mockRejectedValue(new HttpError(500, 'Service unavailable'));

        setup();

        expect(await screen.findByText('Could not load the users.')).toBeInTheDocument();
        expect(screen.queryByText('No users yet')).not.toBeInTheDocument();
    });

    it('hides Create user without the create permission, and the user link and read columns without the read permission', async () => {
        setup(['domain_user_list']);

        expect(await screen.findByText('Alice Liddell')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Alice Liddell' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Create user' })).not.toBeInTheDocument();
        expect(screen.queryByRole('columnheader', { name: 'Identity provider' })).not.toBeInTheDocument();
        expect(screen.queryByRole('columnheader', { name: 'Last login' })).not.toBeInTheDocument();
    });
});
