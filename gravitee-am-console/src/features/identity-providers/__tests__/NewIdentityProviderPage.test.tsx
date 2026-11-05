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
import { LayoutSlotsProvider, toast, TooltipProvider } from '@gravitee/graphene-core';
import { buttonHarness, inputHarness, passwordInputHarness, radioHarness, renderWithGraphene } from '@gravitee/graphene-core/testing';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { listDataSources } from '../../../lib/api/data-sources';
import { createIdentityProvider, getIdentityProviderSchema, listIdentityProviderPlugins } from '../../../lib/api/identity-providers';
import { LicenseScope } from '../../../lib/session/license';
import { NewIdentityProviderPage } from '../NewIdentityProviderPage';
import { GOOGLE_SCHEMA } from './google-schema';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/identity-providers', async importOriginal => ({
    ...(await importOriginal<object>()),
    createIdentityProvider: vi.fn(),
    getIdentityProviderSchema: vi.fn(),
    listIdentityProviderPlugins: vi.fn(),
}));
vi.mock('../../../lib/api/certificates', () => ({ listCertificates: vi.fn().mockResolvedValue([]) }));
vi.mock('../../../lib/api/data-sources', () => ({ listDataSources: vi.fn() }));
vi.mock('../../../lib/api/management-api', () => ({ getInstallationConfiguration: vi.fn().mockResolvedValue({}) }));
vi.mock('../../../lib/session/domain', () => ({
    useCurrentDomain: () => ({
        domain: { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true },
        ref: { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' },
        basePath: '/environments/dev/domains/acme',
        domainsPath: '/environments/dev/domains',
    }),
}));

const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
const providersPath = '/environments/dev/domains/acme/settings/providers';

function setupNewProviderHarness(client = new QueryClient({ defaultOptions: { queries: { retry: false } } })) {
    const view = renderWithGraphene(
        <QueryClientProvider client={client}>
            <LicenseScope license={{ tier: 'oss', features: [] }}>
                <MemoryRouter initialEntries={[`${providersPath}/new`]}>
                    <LayoutSlotsProvider>
                        <TooltipProvider>
                            <Routes>
                                <Route path={`${providersPath}/new`} element={<NewIdentityProviderPage />} />
                                <Route path={`${providersPath}/:id`} element={<p>Detail page</p>} />
                            </Routes>
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </LicenseScope>
        </QueryClientProvider>,
    );

    return {
        unmount: view.unmount,
        findPlugin: (name: string) => screen.findByRole('radio', { name }),
        filter: (text: string) => inputHarness({ name: 'Filter providers' }).type(text),
        /** Picks the plugin, then opens the configuration step. */
        configure: async (plugin: string) => {
            await screen.findByRole('radio', { name: plugin });
            await radioHarness({ name: plugin }).select();
            await buttonHarness({ name: 'Next' }).click();
        },
        clickLockedPlugin: async (name: string) => userEvent.click(await screen.findByRole('radio', { name })),
        typeName: async (text: string) => {
            await screen.findByRole('textbox', { name: 'Name' });
            await inputHarness({ name: 'Name' }).type(text);
        },
        typeClientId: (text: string) => inputHarness({ name: /Client ID/ }).type(text),
        typeClientSecret: (text: string) => passwordInputHarness({ name: /Client Secret/ }).type(text),
        create: async () => {
            await screen.findByRole('button', { name: 'Create' });
            await buttonHarness({ name: 'Create' }).click();
        },
    };
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listDataSources).mockResolvedValue([]);
    vi.mocked(listIdentityProviderPlugins).mockResolvedValue([
        { id: 'mongo-am-idp', name: 'MongoDB', external: false },
        { id: 'ldap-am-idp', name: 'LDAP', displayName: 'LDAP / AD', feature: 'am-idp-ldap', external: false },
        { id: 'google-am-idp', name: 'Google', labels: ['Social'], external: true },
    ]);
    vi.mocked(getIdentityProviderSchema).mockResolvedValue(GOOGLE_SCHEMA);
    vi.mocked(createIdentityProvider).mockResolvedValue({
        id: 'idp-1',
        name: 'My Google',
        type: 'google-am-idp',
        system: false,
        external: true,
    });
});

describe('NewIdentityProviderPage', () => {
    it('lists the plugins, filters them on the labels, and locks the ones the licence lacks', async () => {
        const harness = setupNewProviderHarness();

        expect(await harness.findPlugin('MongoDB')).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'LDAP / AD, Enterprise' })).toBeInTheDocument();

        await harness.filter('social');

        expect(screen.getByRole('radio', { name: 'Google' })).toBeInTheDocument();
        expect(screen.queryByRole('radio', { name: 'MongoDB' })).not.toBeInTheDocument();
    });

    it('opens the upgrade dialog, and does not select, on a click on a locked plugin', async () => {
        const harness = setupNewProviderHarness();

        await harness.clickLockedPlugin('LDAP / AD, Enterprise');

        expect(await screen.findByRole('dialog', { name: 'Enterprise feature' })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'LDAP / AD, Enterprise', hidden: true })).toHaveAttribute('aria-checked', 'false');
        expect(screen.getByRole('button', { name: 'Next', hidden: true })).toBeDisabled();
    });

    it('creates an external provider with the default whitelist, then opens it', async () => {
        const harness = setupNewProviderHarness();

        await harness.configure('Google');
        await harness.typeName('My Google');
        await harness.typeClientId('abc');
        await harness.typeClientSecret('s3cr3t');

        expect(screen.getByText('gmail.com')).toBeInTheDocument();

        await harness.create();

        await waitFor(() => expect(createIdentityProvider).toHaveBeenCalledTimes(1));
        const [calledRef, sent] = vi.mocked(createIdentityProvider).mock.calls[0];
        expect(calledRef).toEqual(ref);
        expect({ ...sent, configuration: undefined }).toEqual({
            name: 'My Google',
            type: 'google-am-idp',
            configuration: undefined,
            external: true,
            domainWhitelist: ['gmail.com'],
        });
        expect(JSON.parse(sent.configuration)).toEqual({
            clientId: 'abc',
            clientSecret: 's3cr3t',
            connectTimeout: 10000,
            encodeRedirectUri: false,
            idleTimeout: 10000,
            maxPoolSize: 200,
            scopes: [],
            storeOriginalTokens: false,
        });
        expect(toast.success).toHaveBeenCalledWith('Provider My Google created');
        expect(await screen.findByText('Detail page')).toBeInTheDocument();
    });

    it('offers the data sources of the environment in a data source field', async () => {
        vi.mocked(listDataSources).mockResolvedValue([{ id: 'ds-1', name: 'Users cluster' }]);
        vi.mocked(getIdentityProviderSchema).mockResolvedValue({
            type: 'object',
            properties: { datasource: { type: 'string', title: 'Data source', widget: 'datasource' } },
        });
        const harness = setupNewProviderHarness();

        await harness.configure('MongoDB');

        expect(await screen.findByRole('combobox', { name: /Data source/ })).toBeInTheDocument();
    });

    it('offers a data source created after an earlier visit to the wizard', async () => {
        const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
        vi.mocked(getIdentityProviderSchema).mockResolvedValue({
            type: 'object',
            properties: { datasource: { type: 'string', title: 'Data source', widget: 'datasource' } },
        });
        const first = setupNewProviderHarness(client);
        await first.configure('MongoDB');
        expect(await screen.findByRole('textbox', { name: /Data source/ })).toBeDisabled();
        first.unmount();

        vi.mocked(listDataSources).mockResolvedValue([{ id: 'ds-1', name: 'Users cluster' }]);
        await setupNewProviderHarness(client).configure('MongoDB');

        expect(await screen.findByRole('combobox', { name: /Data source/ })).toBeEnabled();
    });

    it('disables Create while the name is blank', async () => {
        const harness = setupNewProviderHarness();

        await harness.configure('MongoDB');

        await waitFor(() => expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled());
    });
});
