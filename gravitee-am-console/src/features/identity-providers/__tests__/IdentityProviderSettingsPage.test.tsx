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
import { buttonHarness, inputHarness } from '@gravitee/graphene-core/testing';
import { screen, waitFor } from '@testing-library/react';
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
import { GOOGLE_SCHEMA } from './google-schema';
import { currentDomain, providersPath, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/identity-providers', async importOriginal => ({
    ...(await importOriginal<object>()),
    deleteIdentityProvider: vi.fn(),
    getIdentityProvider: vi.fn(),
    getIdentityProviderSchema: vi.fn(),
    listIdentityProviderPlugins: vi.fn(),
    updateIdentityProvider: vi.fn(),
}));
vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/certificates', () => ({ listCertificates: vi.fn() }));
vi.mock('../../../lib/api/data-sources', () => ({ listDataSources: vi.fn() }));
vi.mock('../../../lib/api/domains', async importOriginal => ({ ...(await importOriginal<object>()), listDomainEntrypoints: vi.fn() }));
vi.mock('../../../lib/api/management-api', () => ({ getInstallationConfiguration: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const ALL = ['domain_identity_provider_update', 'domain_identity_provider_delete'];

const STORED = {
    clientId: 'abc',
    clientSecret: 's3cr3t',
    scopes: ['openid'],
    connectTimeout: 10000,
    idleTimeout: 10000,
    maxPoolSize: 200,
    storeOriginalTokens: false,
    encodeRedirectUri: false,
};

const google: IdentityProvider = {
    id: 'idp-1',
    name: 'Google',
    type: 'google-am-idp',
    system: false,
    external: true,
    configuration: JSON.stringify(STORED),
    domainWhitelist: ['gmail.com'],
    mappers: { email: 'mail' },
};

function setupSettingsPageHarness(provider: IdentityProvider = google, permissions = ALL, features: string[] = []) {
    vi.mocked(getIdentityProvider).mockResolvedValue(provider);
    renderRoutes(
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
    const replace = async (name: string | RegExp, text: string) => {
        await inputHarness({ name }).clear();
        if (text) {
            await inputHarness({ name }).type(text);
        }
    };

    return {
        findName: () => screen.findByRole('textbox', { name: 'Name' }),
        rename: async (text: string) => {
            await screen.findByRole('textbox', { name: 'Name' });
            await replace('Name', text);
        },
        setClientId: (text: string) => replace(/Client ID/, text),
        save: () => buttonHarness({ name: 'Save' }).click(),
        clickButton: (name: string | RegExp) => buttonHarness({ name }).click(),
        confirm: async (dialogName: string, button: string) => {
            const dialog = await screen.findByRole('dialog', { name: dialogName });
            await buttonHarness({ name: button, within: dialog }).click();
        },
        delete: async () => {
            await screen.findByRole('button', { name: 'Delete' });
            await buttonHarness({ name: 'Delete' }).click();
            const dialog = await screen.findByRole('dialog', { name: 'Delete provider' });
            await buttonHarness({ name: 'Delete', within: dialog }).click();
        },
    };
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getIdentityProviderSchema).mockResolvedValue(GOOGLE_SCHEMA);
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
        const harness = setupSettingsPageHarness();

        await harness.rename('Google login');
        await harness.setClientId('xyz');
        await harness.save();

        await waitFor(() => expect(updateIdentityProvider).toHaveBeenCalledTimes(1));
        const [calledRef, sent] = vi.mocked(updateIdentityProvider).mock.calls[0];
        expect(calledRef).toEqual(ref);
        expect({ ...sent, configuration: undefined }).toEqual({
            ...google,
            name: 'Google login',
            domainWhitelist: ['gmail.com'],
            configuration: undefined,
        });
        expect(JSON.parse(sent.configuration!)).toEqual({ ...STORED, clientId: 'xyz' });
        await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Provider updated'));
    });

    it('shows the name with no configuration fields when the plugin has no schema', async () => {
        vi.mocked(getIdentityProviderSchema).mockResolvedValue({});
        const harness = setupSettingsPageHarness();

        expect(await harness.findName()).toHaveValue('Google');
        expect(screen.queryByRole('textbox', { name: /Client ID/ })).not.toBeInTheDocument();
    });

    it('shows the plugin description, as the Angular side panel does', async () => {
        setupSettingsPageHarness();

        expect(await screen.findByText(/Google identity provider, let you register/)).toBeInTheDocument();
    });

    it('shows the redirect URI from the domain entrypoint and path', async () => {
        setupSettingsPageHarness();

        expect(await screen.findByText('https://auth.example.com/acme/login/callback')).toBeInTheDocument();
    });

    it('shows an error in place of the redirect URI when the entrypoints fail to load', async () => {
        vi.mocked(listDomainEntrypoints).mockRejectedValue(new HttpError(500, 'Service unavailable'));
        setupSettingsPageHarness();

        expect(await screen.findByText('Could not load the domain entrypoints.')).toBeInTheDocument();
    });

    it('shows the settings when the plugin list fails, which only gives the licence feature', async () => {
        vi.mocked(listIdentityProviderPlugins).mockRejectedValue(new HttpError(500, 'Service unavailable'));
        const harness = setupSettingsPageHarness();

        expect(await harness.findName()).toHaveValue('Google');
    });

    it('locks the fields while a save runs, so the reset after it loses no edit', async () => {
        vi.mocked(updateIdentityProvider).mockReturnValue(new Promise(() => undefined));
        const harness = setupSettingsPageHarness();

        await harness.rename('Google login');
        await harness.save();

        await waitFor(() => expect(screen.getByRole('textbox', { name: 'Name' })).toBeDisabled());
        expect(screen.getByRole('textbox', { name: /Client ID/ })).toBeDisabled();
    });

    it('describes the name input with its error when the name is cleared', async () => {
        const harness = setupSettingsPageHarness();

        await harness.rename('');
        await harness.setClientId('xyz');

        const name = await harness.findName();
        await waitFor(() => expect(name).toHaveAttribute('aria-invalid', 'true'));
        expect(name).toHaveAccessibleDescription('Name is required');
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
        const harness = setupSettingsPageHarness(inline);

        await harness.findName();
        await harness.clickButton(/Remove/);
        await harness.save();

        await screen.findByRole('dialog', { name: 'Update provider: a user has been modified or deleted' });
        expect(updateIdentityProvider).not.toHaveBeenCalled();
        await harness.confirm('Update provider: a user has been modified or deleted', 'Save');

        await waitFor(() =>
            expect(updateIdentityProvider).toHaveBeenCalledWith(ref, expect.objectContaining({ configuration: '{"users":[]}' })),
        );
    });

    it('sends the pinned storage fields of a restricted MongoDB provider back as stored', async () => {
        const stored = { uri: 'mongodb://old:27017', useSystemCluster: true, database: 'tenant-db', usersCollection: 'tenant_users' };
        const mongo: IdentityProvider = {
            id: 'idp-3',
            name: 'MongoDB',
            type: 'mongo-am-idp',
            system: false,
            external: false,
            systemClusterRestricted: true,
            configuration: JSON.stringify(stored),
        };
        vi.mocked(getIdentityProviderSchema).mockResolvedValue({
            type: 'object',
            properties: {
                uri: { type: 'string', title: 'MongoDB URI' },
                useSystemCluster: { type: 'boolean', title: 'Use system cluster' },
                database: { type: 'string', title: 'Database' },
                usersCollection: { type: 'string', title: 'Users collection' },
            },
        });
        vi.mocked(getInstallationConfiguration).mockResolvedValue({ systemClusterRestricted: true });
        const harness = setupSettingsPageHarness(mongo);

        await harness.findName();
        await harness.rename('MongoDB users');
        await harness.save();

        await waitFor(() => expect(updateIdentityProvider).toHaveBeenCalledTimes(1));
        const [, sent] = vi.mocked(updateIdentityProvider).mock.calls[0];
        expect(JSON.parse(sent.configuration!)).toEqual(stored);
    });

    it('deletes the provider after the confirm dialog, then opens the list', async () => {
        vi.mocked(deleteIdentityProvider).mockResolvedValueOnce();
        const harness = setupSettingsPageHarness();

        await harness.delete();

        await waitFor(() => expect(deleteIdentityProvider).toHaveBeenCalledWith(ref, 'idp-1'));
        expect(await screen.findByText('Providers list')).toBeInTheDocument();
    });

    it('hides Save and Delete without the permissions', async () => {
        const harness = setupSettingsPageHarness(google, []);

        expect(await harness.findName()).toBeDisabled();
        expect(inputHarness({ name: /Client ID/ }).isDisabled()).toBe(true);
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('opens the upgrade dialog, not the save, when the licence lacks the plugin feature', async () => {
        const harness = setupSettingsPageHarness({ ...google, type: 'oidc-am-idp' });

        await harness.rename('OIDC');
        await harness.save();

        expect(await screen.findByRole('dialog', { name: 'Enterprise feature' })).toBeInTheDocument();
        expect(updateIdentityProvider).not.toHaveBeenCalled();
    });

    it('opens the user mappers of a system provider, and shows no Settings tab', async () => {
        setupSettingsPageHarness({ ...google, system: true, external: false });

        expect(await screen.findByText('Mappers page')).toBeInTheDocument();
        expect(screen.queryByRole('tab', { name: 'Settings' })).not.toBeInTheDocument();
    });

    it('shows the not-found panel for an unknown provider', async () => {
        vi.mocked(getIdentityProvider).mockRejectedValue(new HttpError(404, 'Not found'));
        renderRoutes(`${providersPath}/missing`, <Route path={`${providersPath}/:providerId`} element={<IdentityProviderLayout />} />, ALL);

        expect(await screen.findByText('Identity provider not found')).toBeInTheDocument();
    });
});
