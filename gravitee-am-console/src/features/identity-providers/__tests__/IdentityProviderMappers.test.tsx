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
import { buttonHarness, checkboxHarness, inputHarness } from '@gravitee/graphene-core/testing';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
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
vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
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

function setupMapperTabHarness(tab: string, permissions = UPDATE, entity: IdentityProvider = provider) {
    vi.mocked(getIdentityProvider).mockResolvedValue(entity);
    renderRoutes(
        `${providersPath}/${entity.id}/${tab}`,
        <Route path={`${providersPath}/:providerId`} element={<IdentityProviderLayout />}>
            <Route path="mappers" element={<IdentityProviderMappersPage />} />
            <Route path="roles" element={<IdentityProviderRolesPage />} />
            <Route path="groups" element={<IdentityProviderGroupsPage />} />
        </Route>,
        permissions,
    );
    const cell = (label: string, row: number) => screen.getAllByRole('textbox', { name: label })[row];

    return {
        findKeys: () => screen.findAllByRole('textbox', { name: 'User profile attribute' }),
        key: (row: number) => cell('User profile attribute', row),
        value: (row: number) => cell('Raw attribute', row),
        addMapper: async () => {
            await screen.findByRole('button', { name: 'Add mapper' });
            await buttonHarness({ name: 'Add mapper' }).click();
        },
        typeKey: (row: number, text: string) => userEvent.type(cell('User profile attribute', row), text),
        /** `userEvent.type` syntax: `{{` and `[[` type a literal brace or bracket. */
        typeValue: (row: number, text: string) => userEvent.type(cell('Raw attribute', row), text),
        leaveValue: async (row: number) => {
            await userEvent.click(cell('Raw attribute', row));
            await userEvent.tab();
        },
        save: () => buttonHarness({ name: 'Save' }).click(),
        isSaveDisabled: () => buttonHarness({ name: 'Save' }).isDisabled(),
        addMapping: async (noun: string, user: string, targets: string[]) => {
            await screen.findByRole('button', { name: `Add ${noun} mapping` });
            await buttonHarness({ name: `Add ${noun} mapping` }).click();
            const dialog = await screen.findByRole('dialog', { name: `${noun[0].toUpperCase()}${noun.slice(1)} mappings` });
            await inputHarness({ name: 'Users', within: dialog }).type(user);
            for (const target of targets) {
                await checkboxHarness({ name: target, within: dialog }).check();
            }
            return {
                isTargetDisabled: (target: string) => checkboxHarness({ name: target, within: dialog }).isDisabled(),
                save: () => buttonHarness({ name: 'Save', within: dialog }).click(),
            };
        },
        removeEntry: async (noun: string, label: string) => {
            await screen.findByRole('button', { name: label });
            await buttonHarness({ name: label }).click();
            const dialog = await screen.findByRole('dialog', { name: `Delete entry from ${noun}` });
            await buttonHarness({ name: 'Remove', within: dialog }).click();
        },
    };
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
        const harness = setupMapperTabHarness('mappers');

        const keys = await harness.findKeys();
        expect(keys.map(input => (input as HTMLInputElement).value)).toEqual(['sub', 'email', 'name', 'given_name', 'family_name']);
    });

    it('saves the mappers in the provider update', async () => {
        const harness = setupMapperTabHarness('mappers', UPDATE, { ...provider, mappers: { email: 'mail' } });

        await harness.addMapper();
        await harness.typeKey(1, 'nickname');
        await harness.typeValue(1, "{{#profile[['nick']}");
        await harness.save();

        await waitFor(() =>
            expect(updateIdentityProvider).toHaveBeenCalledWith(ref, {
                ...provider,
                mappers: { email: 'mail', nickname: "{#profile['nick']}" },
            }),
        );
    });

    it('marks a duplicate attribute on its row and blocks the save', async () => {
        const harness = setupMapperTabHarness('mappers', UPDATE, { ...provider, mappers: { email: 'mail' } });

        await harness.addMapper();
        await harness.typeKey(1, 'email');
        await harness.typeValue(1, 'mail2');

        expect(harness.key(1)).toHaveAttribute('aria-invalid', 'true');
        expect(harness.key(1)).toHaveAccessibleDescription('This attribute is already mapped.');
        expect(harness.isSaveDisabled()).toBe(true);
        expect(updateIdentityProvider).not.toHaveBeenCalled();
    });

    it('marks an empty cell as required once the user leaves it', async () => {
        const harness = setupMapperTabHarness('mappers', UPDATE, { ...provider, mappers: { email: 'mail' } });

        await harness.addMapper();
        await harness.typeKey(1, 'nickname');
        await harness.leaveValue(1);

        expect(harness.value(1)).toHaveAttribute('aria-invalid', 'true');
        expect(harness.value(1)).toHaveAccessibleDescription('Required.');
        expect(harness.key(1)).toHaveAttribute('aria-invalid', 'false');
        expect(harness.isSaveDisabled()).toBe(true);
    });

    it('hides the edit actions without the update permission', async () => {
        const harness = setupMapperTabHarness('mappers', []);

        expect((await harness.findKeys())[0]).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Add mapper' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });
});

describe('IdentityProviderRolesPage', () => {
    it('lists the mapped users under the role names', async () => {
        setupMapperTabHarness('roles');

        expect(await screen.findByRole('heading', { name: 'ADMIN' })).toBeInTheDocument();
        expect(screen.getByText('memberOf=admins')).toBeInTheDocument();
        expect(listAllRoles).toHaveBeenCalledWith(ref);
    });

    it('adds a user expression to roles and saves the role mapper', async () => {
        const harness = setupMapperTabHarness('roles');

        const dialog = await harness.addMapping('role', 'uid=john', ['ADMIN', 'USER']);
        expect(dialog.isTargetDisabled('DOMAIN_PRIMARY_OWNER')).toBe(true);
        await dialog.save();

        await waitFor(() =>
            expect(updateIdentityProvider).toHaveBeenCalledWith(ref, {
                ...provider,
                roleMapper: { 'role-1': ['memberOf=admins', 'uid=john'], 'role-2': ['uid=john'] },
            }),
        );
    });

    it('removes an entry after the confirm dialog, and drops the empty role', async () => {
        const harness = setupMapperTabHarness('roles');

        await harness.removeEntry('role', 'Remove memberOf=admins from ADMIN');

        await waitFor(() => expect(updateIdentityProvider).toHaveBeenCalledWith(ref, { ...provider, roleMapper: {} }));
    });

    it('hides add and remove without the update permission', async () => {
        setupMapperTabHarness('roles', []);

        expect(await screen.findByText('memberOf=admins')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Add role mapping' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Remove memberOf=admins from ADMIN' })).not.toBeInTheDocument();
    });
});

describe('IdentityProviderGroupsPage', () => {
    it('names the group in the toast when the user is already mapped onto it', async () => {
        const harness = setupMapperTabHarness('groups');

        const dialog = await harness.addMapping('group', 'ou=sales', ['Sales']);
        await dialog.save();

        expect(toast.error).toHaveBeenCalledWith('"ou=sales" already has the group Sales.');
        expect(updateIdentityProvider).not.toHaveBeenCalled();
    });

    it('loads the first 100 groups and removes one entry of a group', async () => {
        const harness = setupMapperTabHarness('groups');

        expect(await screen.findByRole('heading', { name: 'Sales' })).toBeInTheDocument();
        expect(listGroups).toHaveBeenCalledWith(ref, 0, 100);

        await harness.removeEntry('group', 'Remove ou=ops from Sales');

        await waitFor(() =>
            expect(updateIdentityProvider).toHaveBeenCalledWith(ref, { ...provider, groupMapper: { 'group-1': ['ou=sales'] } }),
        );
    });
});
