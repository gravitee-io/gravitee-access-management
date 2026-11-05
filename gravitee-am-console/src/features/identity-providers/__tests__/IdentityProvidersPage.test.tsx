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
import { buttonHarness, inputHarness } from '@gravitee/graphene-core/testing';
import { screen, waitFor, within } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { HttpError } from '../../../lib/api/http';
import { deleteIdentityProvider, listIdentityProviderPlugins, listIdentityProviders } from '../../../lib/api/identity-providers';
import { IdentityProvidersPage } from '../IdentityProvidersPage';
import { currentDomain, providersPath, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/identity-providers', () => ({
    deleteIdentityProvider: vi.fn(),
    listIdentityProviderPlugins: vi.fn(),
    listIdentityProviders: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const ALL = ['domain_identity_provider_create', 'domain_identity_provider_read', 'domain_identity_provider_delete'];

function setupIdentityProvidersPageHarness(permissions = ALL) {
    renderRoutes(providersPath, <Route path={providersPath} element={<IdentityProvidersPage />} />, permissions);

    return {
        findProviderLink: (name: string) => screen.findByRole('link', { name }),
        rowOf: (name: string) => screen.getByRole('link', { name }).closest('tr')!,
        search: async (text: string) => {
            await screen.findByRole('searchbox', { name: 'Search identity providers' });
            await inputHarness({ name: 'Search identity providers' }).type(text);
        },
        nextPage: () => buttonHarness({ name: 'Next page' }).click(),
        delete: async (name: string) => {
            await screen.findByRole('button', { name: `Delete ${name}` });
            await buttonHarness({ name: `Delete ${name}` }).click();
            const dialog = await screen.findByRole('dialog', { name: 'Delete provider' });
            await buttonHarness({ name: 'Delete', within: dialog }).click();
        },
    };
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listIdentityProviders).mockResolvedValue([
        { id: 'default-idp-d1', name: 'Default Identity Provider', type: 'mongo-am-idp', system: true, external: false },
        { id: 'idp-2', name: 'Corporate LDAP', type: 'ldap-am-idp', system: false, external: false },
        { id: 'idp-3', name: 'Legacy', type: 'unknown-am-idp', system: false, external: false },
    ]);
    vi.mocked(listIdentityProviderPlugins).mockResolvedValue([
        { id: 'mongo-am-idp', name: 'MongoDB', external: false },
        { id: 'ldap-am-idp', name: 'LDAP', displayName: 'LDAP / AD', external: false },
    ]);
});

describe('IdentityProvidersPage', () => {
    it('lists the providers of the domain with the plugin name, or Custom for an unknown plugin', async () => {
        const harness = setupIdentityProvidersPageHarness();

        expect(await harness.findProviderLink('Corporate LDAP')).toHaveAttribute('href', `${providersPath}/idp-2`);
        expect(within(harness.rowOf('Corporate LDAP')).getByText('LDAP / AD')).toBeInTheDocument();
        expect(within(harness.rowOf('Legacy')).getAllByText('Custom')).toHaveLength(2);
        expect(listIdentityProviders).toHaveBeenCalledWith(ref);
    });

    it('searches the providers on the name and shows the no-results state', async () => {
        const harness = setupIdentityProvidersPageHarness();
        await harness.findProviderLink('Corporate LDAP');

        await harness.search('nothing');

        expect(await screen.findByText('No identity providers match your search')).toBeInTheDocument();
    });

    it('shows one page of 10 providers at a time', async () => {
        vi.mocked(listIdentityProviders).mockResolvedValueOnce(
            Array.from({ length: 30 }, (_, index) => ({
                id: `idp-${index + 1}`,
                name: `Provider ${index + 1}`,
                type: 'ldap-am-idp',
                system: false,
                external: false,
            })),
        );

        const harness = setupIdentityProvidersPageHarness();

        expect(await harness.findProviderLink('Provider 10')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Provider 11' })).not.toBeInTheDocument();
    });

    it('goes back a page when a delete empties the last page', async () => {
        const providers = Array.from({ length: 11 }, (_, index) => ({
            id: `idp-${index + 1}`,
            name: `Provider ${index + 1}`,
            type: 'ldap-am-idp',
            system: false,
            external: false,
        }));
        vi.mocked(listIdentityProviders).mockResolvedValueOnce(providers).mockResolvedValueOnce(providers.slice(0, 10));
        vi.mocked(deleteIdentityProvider).mockResolvedValueOnce();
        const harness = setupIdentityProvidersPageHarness();
        await harness.findProviderLink('Provider 1');

        await harness.nextPage();
        await harness.delete('Provider 11');

        expect(await harness.findProviderLink('Provider 10')).toBeInTheDocument();
        expect(screen.queryByText('No identity providers match your search')).not.toBeInTheDocument();
    });

    it('shows the first-use state when the domain has no provider', async () => {
        vi.mocked(listIdentityProviders).mockResolvedValueOnce([]);

        setupIdentityProvidersPageHarness();

        expect(await screen.findByText('No identity providers yet')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Add identity provider' })).toHaveAttribute('href', `${providersPath}/new`);
    });

    it('shows the error panel, not the first-use state, when the list fails', async () => {
        vi.mocked(listIdentityProviders).mockRejectedValueOnce(new HttpError(500, 'Service unavailable'));

        setupIdentityProvidersPageHarness();

        expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the identity providers.');
        expect(screen.queryByText('No identity providers yet')).not.toBeInTheDocument();
    });

    it('shows the error panel when the plugin list fails, not Custom on every row', async () => {
        vi.mocked(listIdentityProviderPlugins).mockRejectedValueOnce(new HttpError(500, 'Service unavailable'));

        setupIdentityProvidersPageHarness();

        expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the identity providers.');
        expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });

    it('deletes a provider after the confirm dialog, then reloads the list', async () => {
        vi.mocked(deleteIdentityProvider).mockResolvedValueOnce();
        const harness = setupIdentityProvidersPageHarness();

        await harness.delete('Corporate LDAP');

        await waitFor(() => expect(deleteIdentityProvider).toHaveBeenCalledWith(ref, 'idp-2'));
        await waitFor(() => expect(listIdentityProviders).toHaveBeenCalledTimes(2));
    });

    it('hides create, delete and the link without the permissions', async () => {
        setupIdentityProvidersPageHarness([]);

        expect(await screen.findByText('Corporate LDAP')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Corporate LDAP' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Add identity provider' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete Corporate LDAP' })).not.toBeInTheDocument();
    });
});
