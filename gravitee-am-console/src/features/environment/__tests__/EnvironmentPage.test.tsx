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
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { listDomains } from '../../../lib/api/domains';
import { HttpError } from '../../../lib/api/http';
import { PermissionScope } from '../../../lib/session/permissions';
import { EnvironmentPage } from '../EnvironmentPage';

vi.mock('../../../lib/api/domains', () => ({ listDomains: vi.fn() }));
vi.mock('../../../lib/session/session', () => ({ useSession: () => ({ user: { sub: 'admin', org: 'DEFAULT', permissions: [] } }) }));
vi.mock('../../../lib/session/environment', () => ({
    useCurrentEnvironment: () => ({ id: 'env-1', name: 'Production', hrids: ['prod'] }),
}));

function Location() {
    return <p>at {useLocation().pathname}</p>;
}

function renderPage(permissions: string[] = []) {
    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={queryClient}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter initialEntries={['/environments/prod']}>
                    <Routes>
                        <Route path="/environments/:envHrid" element={<EnvironmentPage />} />
                        <Route path="*" element={<Location />} />
                    </Routes>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

describe('EnvironmentPage', () => {
    it('opens the first security domain of the environment', async () => {
        vi.mocked(listDomains).mockResolvedValue({
            data: [
                { id: 'd1', hrid: 'one', name: 'One', enabled: true },
                { id: 'd2', hrid: 'two', name: 'Two', enabled: true },
            ],
            currentPage: 0,
            totalCount: 2,
        });

        renderPage();

        expect(await screen.findByText('at /environments/prod/domains/one')).toBeInTheDocument();
        expect(listDomains).toHaveBeenCalledWith('DEFAULT', 'env-1');
    });

    it('offers to create a security domain to a user with domain_create', async () => {
        vi.mocked(listDomains).mockResolvedValue({ data: [], currentPage: 0, totalCount: 0 });

        renderPage(['domain_create']);

        const create = await screen.findByRole('link', { name: 'Create a Security Domain' });
        expect(create).toHaveAttribute('href', '/environments/prod/domains/new');
    });

    it('disables the creation for a user without domain_create', async () => {
        vi.mocked(listDomains).mockResolvedValue({ data: [], currentPage: 0, totalCount: 0 });

        renderPage();

        expect(await screen.findByRole('button', { name: 'Create a Security Domain' })).toBeDisabled();
        expect(screen.queryByRole('link', { name: 'Create a Security Domain' })).not.toBeInTheDocument();
        expect(screen.getByText(/There is not enough permission/)).toBeInTheDocument();
    });

    it('shows the error when the domains cannot be loaded', async () => {
        vi.mocked(listDomains).mockRejectedValue(new HttpError(500, 'Boom'));

        renderPage(['domain_create']);

        expect(await screen.findByText('Could not load the security domains.')).toBeInTheDocument();
    });
});
