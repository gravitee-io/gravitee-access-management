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
import { LayoutSlotsProvider, toast } from '@gravitee/graphene-core';
import { renderWithGraphene } from '@gravitee/graphene-core/testing';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { type Domain, listDomainsByIds, searchDomains } from '../../../lib/api/domains';
import { HttpError } from '../../../lib/api/http';
import { getUserPreferences, updateUserPreferences } from '../../../lib/api/management-api';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainsPage } from '../DomainsPage';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/domains', () => ({ searchDomains: vi.fn(), listDomainsByIds: vi.fn() }));
vi.mock('../../../lib/api/management-api', () => ({ getUserPreferences: vi.fn(), updateUserPreferences: vi.fn() }));
vi.mock('../../../lib/session/session', () => ({ useSession: () => ({ user: { org: 'DEFAULT' } }) }));
vi.mock('../../../lib/session/environment', () => ({
    useCurrentEnvironment: () => ({ id: 'env-1', name: 'Development', hrids: ['dev'] }),
}));

const acme: Domain = { id: 'd1', hrid: 'acme', name: 'Acme', description: 'Customer realm', enabled: true, updatedAt: Date.now() };
const beta: Domain = { id: 'd2', hrid: 'beta', name: 'Beta', enabled: false };

function renderPage(permissions: string[] = []) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return renderWithGraphene(
        <QueryClientProvider client={client}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter initialEntries={['/environments/dev/domains']}>
                    <LayoutSlotsProvider>
                        <DomainsPage />
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

describe('DomainsPage', () => {
    beforeEach(() => {
        vi.mocked(getUserPreferences).mockResolvedValue({});
    });

    it('lists the domains of the current environment', async () => {
        vi.mocked(searchDomains).mockResolvedValueOnce({ data: [acme, beta], currentPage: 0, totalCount: 2 });

        renderPage();

        expect(await screen.findByRole('link', { name: 'Acme' })).toHaveAttribute('href', '/environments/dev/domains/acme');
        expect(screen.getByText('Customer realm')).toBeInTheDocument();
        expect(screen.getByText('Enabled')).toBeInTheDocument();
        expect(screen.getByText('Disabled')).toBeInTheDocument();
        expect(searchDomains).toHaveBeenCalledWith('DEFAULT', 'env-1', 0, 25, '');
    });

    it('shows the error panel, not the first-use state, when the list fails', async () => {
        vi.mocked(searchDomains).mockRejectedValueOnce(new HttpError(500, 'Service unavailable'));

        renderPage(['domain_create']);

        expect(await screen.findByText('Could not load the security domains.')).toBeInTheDocument();
        expect(screen.queryByText('No security domains yet')).not.toBeInTheDocument();
    });

    it('shows the first-use empty state with Create domain when the environment has no domain', async () => {
        vi.mocked(searchDomains).mockResolvedValueOnce({ data: [], currentPage: 0, totalCount: 0 });

        renderPage(['domain_create']);

        expect(await screen.findByText('No security domains yet')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Create domain' })).toHaveAttribute('href', '/environments/dev/new-domain');
    });

    it('searches the domains and shows the no-results state when nothing matches', async () => {
        vi.mocked(searchDomains)
            .mockResolvedValueOnce({ data: [acme], currentPage: 0, totalCount: 1 })
            .mockResolvedValue({ data: [], currentPage: 0, totalCount: 0 });

        renderPage();
        await userEvent.type(await screen.findByRole('searchbox', { name: 'Search domains' }), 'zzz');

        await waitFor(() => expect(searchDomains).toHaveBeenLastCalledWith('DEFAULT', 'env-1', 0, 25, 'zzz'));
        expect(await screen.findByText('No domains match your search')).toBeInTheDocument();
        expect(screen.queryByText('No security domains yet')).not.toBeInTheDocument();
    });

    it('offers Create domain with the domain_create permission only', async () => {
        vi.mocked(searchDomains).mockResolvedValue({ data: [acme], currentPage: 0, totalCount: 1 });

        const { unmount } = renderPage(['domain_create']);
        expect(await screen.findByRole('link', { name: 'Create domain' })).toHaveAttribute('href', '/environments/dev/new-domain');
        unmount();

        renderPage();
        await screen.findByRole('link', { name: 'Acme' });
        expect(screen.queryByRole('link', { name: 'Create domain' })).not.toBeInTheDocument();
    });

    it('lists the pinned domains only, read by id', async () => {
        vi.mocked(getUserPreferences).mockResolvedValue({ pinnedDomainIds: ['d2'] });
        vi.mocked(searchDomains).mockResolvedValue({ data: [acme, beta], currentPage: 0, totalCount: 2 });
        vi.mocked(listDomainsByIds).mockResolvedValue({ data: [beta], currentPage: 0, totalCount: 1 });

        renderPage();
        await screen.findByRole('link', { name: 'Acme' });
        await userEvent.click(screen.getByRole('switch', { name: 'Pinned only' }));

        await waitFor(() => expect(screen.queryByRole('link', { name: 'Acme' })).not.toBeInTheDocument());
        expect(screen.getByRole('link', { name: 'Beta' })).toBeInTheDocument();
        expect(listDomainsByIds).toHaveBeenCalledWith('DEFAULT', 'env-1', ['d2']);
    });

    it('pins a domain in the user preferences', async () => {
        vi.mocked(getUserPreferences).mockResolvedValue({ defaultDomainId: 'd2', pinnedDomainIds: ['d2'] });
        vi.mocked(searchDomains).mockResolvedValue({ data: [acme], currentPage: 0, totalCount: 1 });
        vi.mocked(updateUserPreferences).mockImplementation(async preferences => preferences);

        renderPage();
        await userEvent.click(await screen.findByRole('button', { name: 'Actions for Acme' }));
        await userEvent.click(screen.getByRole('menuitem', { name: 'Pin domain' }));

        await waitFor(() => expect(updateUserPreferences).toHaveBeenCalledWith({ defaultDomainId: 'd2', pinnedDomainIds: ['d2', 'd1'] }));
        expect(toast.success).toHaveBeenCalledWith('Acme pinned');
    });

    it('sets the default domain for the current environment, and unsets it', async () => {
        vi.mocked(searchDomains).mockResolvedValue({ data: [acme], currentPage: 0, totalCount: 1 });
        vi.mocked(updateUserPreferences).mockImplementation(async preferences => preferences);

        renderPage();
        await userEvent.click(await screen.findByRole('button', { name: 'Actions for Acme' }));
        await userEvent.click(screen.getByRole('menuitem', { name: 'Set as default domain' }));

        await waitFor(() => expect(updateUserPreferences).toHaveBeenCalledWith({ defaultDomainId: 'd1', defaultEnvironmentId: 'env-1' }));
        expect(await screen.findByLabelText('Default domain')).toBeInTheDocument();

        await userEvent.click(screen.getByRole('button', { name: 'Actions for Acme' }));
        await userEvent.click(screen.getByRole('menuitem', { name: 'Unset default domain' }));

        await waitFor(() => expect(updateUserPreferences).toHaveBeenLastCalledWith({ defaultDomainId: null, defaultEnvironmentId: null }));
    });
});
