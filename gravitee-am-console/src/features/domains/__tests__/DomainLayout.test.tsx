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
import { LayoutSlotsProvider, toast, useLayoutSlots } from '@gravitee/graphene-core';
import { renderWithGraphene } from '@gravitee/graphene-core/testing';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { type Domain, enableDomain } from '../../../lib/api/domains';
import { useCurrentDomain } from '../../../lib/session/domain';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainLayout } from '../DomainLayout';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/domains', () => ({ enableDomain: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: vi.fn() }));

const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };

function Banner() {
    return <>{useLayoutSlots().slots.banner}</>;
}

function renderLayout(domain: Domain, permissions: string[]) {
    vi.mocked(useCurrentDomain).mockReturnValue({
        domain,
        ref,
        basePath: '/environments/dev/domains/acme',
        domainsPath: '/environments/dev/domains',
    });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return renderWithGraphene(
        <QueryClientProvider client={client}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter>
                    <LayoutSlotsProvider>
                        <Banner />
                        <DomainLayout />
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

describe('DomainLayout', () => {
    it('enables a disabled domain from the banner', async () => {
        vi.mocked(enableDomain).mockResolvedValue({ id: 'd1', hrid: 'acme', name: 'Acme', enabled: true });

        renderLayout({ id: 'd1', hrid: 'acme', name: 'Acme', enabled: false }, ['domain_settings_update']);
        expect(screen.getByRole('status', { name: 'Domain status' })).toHaveTextContent('Acme domain is disabled.');
        await userEvent.click(screen.getByRole('button', { name: 'Enable domain' }));

        expect(enableDomain).toHaveBeenCalledWith(ref);
        await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Domain Acme enabled'));
    });

    it('shows no banner without domain_settings_update', () => {
        renderLayout({ id: 'd1', hrid: 'acme', name: 'Acme', enabled: false }, []);

        expect(screen.queryByRole('button', { name: 'Enable domain' })).not.toBeInTheDocument();
    });

    it('shows no banner for an enabled domain', () => {
        renderLayout({ id: 'd1', hrid: 'acme', name: 'Acme', enabled: true }, ['domain_settings_update']);

        expect(screen.queryByRole('status', { name: 'Domain status' })).not.toBeInTheDocument();
    });
});
