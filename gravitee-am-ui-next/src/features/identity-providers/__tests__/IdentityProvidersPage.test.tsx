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

function renderPage(permissions = ALL) {
    return renderRoutes(providersPath, <Route path={providersPath} element={<IdentityProvidersPage />} />, permissions);
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
        renderPage();

        const ldap = (await screen.findByRole('link', { name: 'Corporate LDAP' })).closest('tr')!;
        expect(screen.getByRole('link', { name: 'Corporate LDAP' })).toHaveAttribute('href', `${providersPath}/idp-2`);
        expect(within(ldap).getByText('LDAP / AD')).toBeInTheDocument();
        expect(within(screen.getByRole('link', { name: 'Legacy' }).closest('tr')!).getAllByText('Custom')).toHaveLength(2);
        expect(listIdentityProviders).toHaveBeenCalledWith(ref);
    });

    it('searches the providers on the name and shows the no-results state', async () => {
        renderPage();
        await screen.findByRole('link', { name: 'Corporate LDAP' });

        fireEvent.change(screen.getByRole('textbox', { name: 'Search identity providers' }), { target: { value: 'nothing' } });

        expect(await screen.findByText('No identity providers match your search')).toBeInTheDocument();
    });

    it('shows the first-use state when the domain has no provider', async () => {
        vi.mocked(listIdentityProviders).mockResolvedValueOnce([]);

        renderPage();

        expect(await screen.findByText('No identity providers')).toBeInTheDocument();
    });

    it('shows the error panel, not the first-use state, when the list fails', async () => {
        vi.mocked(listIdentityProviders).mockRejectedValueOnce(new HttpError(500, 'Service unavailable'));

        renderPage();

        expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the identity providers.');
        expect(screen.queryByText('No identity providers')).not.toBeInTheDocument();
    });

    it('deletes a provider after the confirm dialog, then reloads the list', async () => {
        vi.mocked(deleteIdentityProvider).mockResolvedValueOnce();
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Delete Corporate LDAP' }));
        const dialog = await screen.findByRole('dialog', { name: 'Delete provider' });
        fireEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(deleteIdentityProvider).toHaveBeenCalledWith(ref, 'idp-2'));
        await waitFor(() => expect(listIdentityProviders).toHaveBeenCalledTimes(2));
    });

    it('hides create, delete and the link without the permissions', async () => {
        renderPage([]);

        expect(await screen.findByText('Corporate LDAP')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Corporate LDAP' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'New provider' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete Corporate LDAP' })).not.toBeInTheDocument();
    });
});
