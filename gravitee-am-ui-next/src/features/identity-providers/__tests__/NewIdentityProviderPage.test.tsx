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
import { LayoutSlotsProvider, TooltipProvider } from '@gravitee/graphene-core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { createIdentityProvider, getIdentityProviderSchema, listIdentityProviderPlugins } from '../../../lib/api/identity-providers';
import { LicenseScope } from '../../../lib/session/license';
import { NewIdentityProviderPage } from '../NewIdentityProviderPage';

vi.mock('../../../lib/api/identity-providers', () => ({
    createIdentityProvider: vi.fn(),
    getIdentityProviderSchema: vi.fn(),
    listIdentityProviderPlugins: vi.fn(),
}));
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

function renderPage() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
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
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listIdentityProviderPlugins).mockResolvedValue([
        { id: 'mongo-am-idp', name: 'MongoDB', external: false },
        { id: 'ldap-am-idp', name: 'LDAP', displayName: 'LDAP / AD', feature: 'am-idp-ldap', external: false },
        { id: 'google-am-idp', name: 'Google', labels: ['Social'], external: true },
    ]);
    vi.mocked(getIdentityProviderSchema).mockResolvedValue({
        type: 'object',
        properties: { clientId: { type: 'string', title: 'Client ID' } },
    });
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
        renderPage();

        expect(await screen.findByRole('radio', { name: 'MongoDB' })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'LDAP / AD, Enterprise' })).toBeInTheDocument();

        fireEvent.change(screen.getByRole('searchbox', { name: 'Filter providers' }), { target: { value: 'social' } });

        expect(screen.getByRole('radio', { name: 'Google' })).toBeInTheDocument();
        expect(screen.queryByRole('radio', { name: 'MongoDB' })).not.toBeInTheDocument();
    });

    it('opens the upgrade dialog, and does not select, on a click on a locked plugin', async () => {
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'LDAP / AD, Enterprise' }));

        expect(await screen.findByRole('dialog', { name: 'Enterprise feature' })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'LDAP / AD, Enterprise', hidden: true })).toHaveAttribute('aria-checked', 'false');
        expect(screen.getByRole('button', { name: 'Next', hidden: true })).toBeDisabled();
    });

    it('creates an external provider with the default whitelist, then opens it', async () => {
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'Google' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'My Google' } });
        fireEvent.change(screen.getByRole('textbox', { name: /Client ID/ }), { target: { value: 'abc' } });

        expect(screen.getByText('gmail.com')).toBeInTheDocument();

        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() =>
            expect(createIdentityProvider).toHaveBeenCalledWith(ref, {
                name: 'My Google',
                type: 'google-am-idp',
                configuration: JSON.stringify({ clientId: 'abc' }),
                external: true,
                domainWhitelist: ['gmail.com'],
            }),
        );
        expect(await screen.findByText('Detail page')).toBeInTheDocument();
    });

    it('requires a name', async () => {
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'MongoDB' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        fireEvent.click(await screen.findByRole('button', { name: 'Create' }));

        expect(await screen.findByText('Name is required')).toBeInTheDocument();
        expect(createIdentityProvider).not.toHaveBeenCalled();
    });
});
