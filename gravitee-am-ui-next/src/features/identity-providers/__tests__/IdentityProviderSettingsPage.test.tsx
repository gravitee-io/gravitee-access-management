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
import { listCertificates } from '../../../lib/api/certificates';
import { listDataSources } from '../../../lib/api/data-sources';
import { listDomainEntrypoints } from '../../../lib/api/domains';
import { HttpError } from '../../../lib/api/http';
import {
    deleteIdentityProvider,
    getIdentityProvider,
    getIdentityProviderSchema,
    type IdentityProvider,
    listIdentityProviderPlugins,
    updateIdentityProvider,
} from '../../../lib/api/identity-providers';
import { getInstallationConfiguration } from '../../../lib/api/management-api';
import { IdentityProviderLayout } from '../IdentityProviderLayout';
import { IdentityProviderSettingsPage } from '../IdentityProviderSettingsPage';
import { currentDomain, providersPath, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/identity-providers', async importOriginal => ({
    ...(await importOriginal<object>()),
    deleteIdentityProvider: vi.fn(),
    getIdentityProvider: vi.fn(),
    getIdentityProviderSchema: vi.fn(),
    listIdentityProviderPlugins: vi.fn(),
    updateIdentityProvider: vi.fn(),
}));
vi.mock('../../../lib/api/certificates', () => ({ listCertificates: vi.fn() }));
vi.mock('../../../lib/api/data-sources', () => ({ listDataSources: vi.fn() }));
vi.mock('../../../lib/api/domains', () => ({ listDomainEntrypoints: vi.fn() }));
vi.mock('../../../lib/api/management-api', () => ({ getInstallationConfiguration: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const ALL = ['domain_identity_provider_update', 'domain_identity_provider_delete'];

const google: IdentityProvider = {
    id: 'idp-1',
    name: 'Google',
    type: 'google-am-idp',
    system: false,
    external: true,
    configuration: JSON.stringify({ clientId: 'abc' }),
    domainWhitelist: ['gmail.com'],
    mappers: { email: 'mail' },
};

function renderPage(provider: IdentityProvider = google, permissions = ALL, features: string[] = []) {
    vi.mocked(getIdentityProvider).mockResolvedValue(provider);
    return renderRoutes(
        `${providersPath}/${provider.id}/settings`,
        <>
            <Route path={providersPath} element={<p>Providers list</p>} />
            <Route path={`${providersPath}/:providerId`} element={<IdentityProviderLayout />}>
                <Route path="settings" element={<IdentityProviderSettingsPage />} />
                <Route path="mappers" element={<p>Mappers page</p>} />
            </Route>
        </>,
        permissions,
        features,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getIdentityProviderSchema).mockResolvedValue({
        type: 'object',
        properties: { clientId: { type: 'string', title: 'Client ID' } },
    });
    vi.mocked(listIdentityProviderPlugins).mockResolvedValue([
        { id: 'google-am-idp', name: 'Google', external: true },
        { id: 'inline-am-idp', name: 'Inline', external: false },
        { id: 'oidc-am-idp', name: 'OpenID Connect', feature: 'am-idp-oidc', external: true },
    ]);
    vi.mocked(listCertificates).mockResolvedValue([]);
    vi.mocked(listDataSources).mockResolvedValue([]);
    vi.mocked(getInstallationConfiguration).mockResolvedValue({});
    vi.mocked(listDomainEntrypoints).mockResolvedValue([{ id: 'e1', url: 'https://auth.example.com', defaultEntrypoint: true }]);
    vi.mocked(updateIdentityProvider).mockImplementation(async (_ref, provider) => provider);
});

describe('IdentityProviderSettingsPage', () => {
    it('saves the name, the whitelist and the configuration as the Angular update does', async () => {
        renderPage();

        const name = await screen.findByRole('textbox', { name: 'Name' });
        fireEvent.change(name, { target: { value: 'Google login' } });
        fireEvent.change(screen.getByRole('textbox', { name: /Client ID/ }), { target: { value: 'xyz' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateIdentityProvider).toHaveBeenCalledWith(ref, {
                ...google,
                name: 'Google login',
                domainWhitelist: ['gmail.com'],
                configuration: JSON.stringify({ clientId: 'xyz' }),
            }),
        );
    });

    it('shows the redirect URI from the domain entrypoint and path', async () => {
        renderPage();

        expect(await screen.findByText('https://auth.example.com/acme/login/callback')).toBeInTheDocument();
    });

    it('asks before a save that drops a user of an inline provider', async () => {
        const inline: IdentityProvider = {
            id: 'idp-2',
            name: 'Inline',
            type: 'inline-am-idp',
            system: false,
            external: false,
            configuration: JSON.stringify({ users: [{ username: 'john', password: '********' }] }),
        };
        vi.mocked(getIdentityProviderSchema).mockResolvedValue({
            type: 'object',
            properties: {
                users: { type: 'array', title: 'Users', items: { type: 'object', properties: { username: { type: 'string' } } } },
            },
        });
        renderPage(inline);

        await screen.findByRole('textbox', { name: 'Name' });
        fireEvent.click(screen.getByRole('button', { name: /Remove/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        const dialog = await screen.findByRole('dialog', { name: 'Update provider: a user has been modified or deleted' });
        expect(updateIdentityProvider).not.toHaveBeenCalled();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateIdentityProvider).toHaveBeenCalledWith(ref, expect.objectContaining({ configuration: '{"users":[]}' })),
        );
    });

    it('deletes the provider after the confirm dialog, then opens the list', async () => {
        vi.mocked(deleteIdentityProvider).mockResolvedValueOnce();
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
        fireEvent.click(within(await screen.findByRole('dialog', { name: 'Delete provider' })).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(deleteIdentityProvider).toHaveBeenCalledWith(ref, 'idp-1'));
        expect(await screen.findByText('Providers list')).toBeInTheDocument();
    });

    it('hides Save and Delete without the permissions', async () => {
        renderPage(google, []);

        expect(await screen.findByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(screen.getByRole('textbox', { name: /Client ID/ })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('opens the upgrade dialog, not the save, when the licence lacks the plugin feature', async () => {
        renderPage({ ...google, type: 'oidc-am-idp' });

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'OIDC' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByRole('dialog', { name: 'Enterprise feature' })).toBeInTheDocument();
        expect(updateIdentityProvider).not.toHaveBeenCalled();
    });

    it('opens the user mappers of a system provider, and shows no Settings tab', async () => {
        renderPage({ ...google, system: true, external: false });

        expect(await screen.findByText('Mappers page')).toBeInTheDocument();
        expect(screen.queryByRole('tab', { name: 'Settings' })).not.toBeInTheDocument();
    });

    it('shows the not-found panel for an unknown provider', async () => {
        vi.mocked(getIdentityProvider).mockRejectedValue(new HttpError(404, 'Not found'));
        renderRoutes(`${providersPath}/missing`, <Route path={`${providersPath}/:providerId`} element={<IdentityProviderLayout />} />, ALL);

        expect(await screen.findByText('Identity provider not found')).toBeInTheDocument();
    });
});
