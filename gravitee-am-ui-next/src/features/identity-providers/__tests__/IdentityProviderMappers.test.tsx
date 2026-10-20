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
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { listGroups } from '../../../lib/api/groups';
import { getIdentityProvider, type IdentityProvider, updateIdentityProvider } from '../../../lib/api/identity-providers';
import { listAllRoles } from '../../../lib/api/roles';
import { IdentityProviderGroupsPage } from '../IdentityProviderGroupsPage';
import { IdentityProviderLayout } from '../IdentityProviderLayout';
import { IdentityProviderMappersPage } from '../IdentityProviderMappersPage';
import { IdentityProviderRolesPage } from '../IdentityProviderRolesPage';
import { currentDomain, providersPath, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/identity-providers', () => ({
    getIdentityProvider: vi.fn(),
    listIdentityProviderPlugins: vi.fn().mockResolvedValue([]),
    updateIdentityProvider: vi.fn(),
}));
vi.mock('../../../lib/api/roles', () => ({ listAllRoles: vi.fn() }));
vi.mock('../../../lib/api/groups', () => ({ listGroups: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const UPDATE = ['domain_identity_provider_update'];

const provider: IdentityProvider = {
    id: 'idp-1',
    name: 'Corporate LDAP',
    type: 'ldap-am-idp',
    system: false,
    external: false,
    configuration: '{}',
    roleMapper: { 'role-1': ['memberOf=admins'] },
    groupMapper: { 'group-1': ['ou=sales', 'ou=ops'] },
};

function renderTab(tab: string, permissions = UPDATE, entity: IdentityProvider = provider) {
    vi.mocked(getIdentityProvider).mockResolvedValue(entity);
    return renderRoutes(
        `${providersPath}/${entity.id}/${tab}`,
        <Route path={`${providersPath}/:providerId`} element={<IdentityProviderLayout />}>
            <Route path="mappers" element={<IdentityProviderMappersPage />} />
            <Route path="roles" element={<IdentityProviderRolesPage />} />
            <Route path="groups" element={<IdentityProviderGroupsPage />} />
        </Route>,
        permissions,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(updateIdentityProvider).mockImplementation(async (_ref, updated) => updated);
    vi.mocked(listAllRoles).mockResolvedValue([
        { id: 'role-1', name: 'ADMIN' },
        { id: 'role-2', name: 'USER' },
        { id: 'role-3', name: 'DOMAIN_PRIMARY_OWNER', system: true },
    ]);
    vi.mocked(listGroups).mockResolvedValue({
        data: [
            { id: 'group-1', name: 'Sales' },
            { id: 'group-2', name: 'Support' },
        ],
        currentPage: 0,
        totalCount: 2,
    });
});

describe('IdentityProviderMappersPage', () => {
    it('shows the default mappers until the provider has its own', async () => {
        renderTab('mappers');

        const keys = await screen.findAllByRole('textbox', { name: 'User profile attribute' });
        expect(keys.map(input => (input as HTMLInputElement).value)).toEqual(['sub', 'email', 'name', 'given_name', 'family_name']);
    });

    it('saves the mappers in the provider update', async () => {
        renderTab('mappers', UPDATE, { ...provider, mappers: { email: 'mail' } });

        fireEvent.click(await screen.findByRole('button', { name: 'Add mapper' }));
        const keys = screen.getAllByRole('textbox', { name: 'User profile attribute' });
        const values = screen.getAllByRole('textbox', { name: 'Raw attribute' });
        fireEvent.change(keys[1], { target: { value: 'nickname' } });
        fireEvent.change(values[1], { target: { value: "{#profile['nick']}" } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateIdentityProvider).toHaveBeenCalledWith(ref, {
                ...provider,
                mappers: { email: 'mail', nickname: "{#profile['nick']}" },
            }),
        );
    });

    it('refuses a duplicate attribute', async () => {
        renderTab('mappers', UPDATE, { ...provider, mappers: { email: 'mail' } });

        fireEvent.click(await screen.findByRole('button', { name: 'Add mapper' }));
        fireEvent.change(screen.getAllByRole('textbox', { name: 'User profile attribute' })[1], { target: { value: 'email' } });
        fireEvent.change(screen.getAllByRole('textbox', { name: 'Raw attribute' })[1], { target: { value: 'mail2' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(updateIdentityProvider).not.toHaveBeenCalled();
    });

    it('hides the edit actions without the update permission', async () => {
        renderTab('mappers', []);

        expect((await screen.findAllByRole('textbox', { name: 'User profile attribute' }))[0]).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Add mapper' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });
});

describe('IdentityProviderRolesPage', () => {
    it('lists the mapped users under the role names', async () => {
        renderTab('roles');

        expect(await screen.findByRole('heading', { name: 'ADMIN' })).toBeInTheDocument();
        expect(screen.getByText('memberOf=admins')).toBeInTheDocument();
        expect(listAllRoles).toHaveBeenCalledWith(ref);
    });

    it('adds a user expression to roles and saves the role mapper', async () => {
        renderTab('roles');

        fireEvent.click(await screen.findByRole('button', { name: 'Add role mapping' }));
        const dialog = await screen.findByRole('dialog', { name: 'Role mappings' });
        fireEvent.change(within(dialog).getByRole('textbox', { name: 'Users' }), { target: { value: 'uid=john' } });
        fireEvent.click(within(dialog).getByRole('checkbox', { name: 'ADMIN' }));
        fireEvent.click(within(dialog).getByRole('checkbox', { name: 'USER' }));
        expect(within(dialog).getByRole('checkbox', { name: 'DOMAIN_PRIMARY_OWNER' })).toBeDisabled();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateIdentityProvider).toHaveBeenCalledWith(ref, {
                ...provider,
                roleMapper: { 'role-1': ['memberOf=admins', 'uid=john'], 'role-2': ['uid=john'] },
            }),
        );
    });

    it('removes an entry after the confirm dialog, and drops the empty role', async () => {
        renderTab('roles');

        fireEvent.click(await screen.findByRole('button', { name: 'Remove memberOf=admins from ADMIN' }));
        fireEvent.click(
            within(await screen.findByRole('dialog', { name: 'Delete entry from role' })).getByRole('button', { name: 'Remove' }),
        );

        await waitFor(() => expect(updateIdentityProvider).toHaveBeenCalledWith(ref, { ...provider, roleMapper: {} }));
    });

    it('hides add and remove without the update permission', async () => {
        renderTab('roles', []);

        expect(await screen.findByText('memberOf=admins')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Add role mapping' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Remove memberOf=admins from ADMIN' })).not.toBeInTheDocument();
    });
});

describe('IdentityProviderGroupsPage', () => {
    it('loads the first 100 groups and removes one entry of a group', async () => {
        renderTab('groups');

        expect(await screen.findByRole('heading', { name: 'Sales' })).toBeInTheDocument();
        expect(listGroups).toHaveBeenCalledWith(ref, 0, 100);

        fireEvent.click(screen.getByRole('button', { name: 'Remove ou=ops from Sales' }));
        fireEvent.click(
            within(await screen.findByRole('dialog', { name: 'Delete entry from group' })).getByRole('button', { name: 'Remove' }),
        );

        await waitFor(() =>
            expect(updateIdentityProvider).toHaveBeenCalledWith(ref, { ...provider, groupMapper: { 'group-1': ['ou=sales'] } }),
        );
    });
});
