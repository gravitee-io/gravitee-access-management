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
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { listDomains } from '../../../lib/api/management-api';
import { DomainsPage } from '../DomainsPage';

vi.mock('../../../lib/api/management-api', () => ({ listDomains: vi.fn() }));
vi.mock('../../../lib/session/session', () => ({ useSession: () => ({ user: { org: 'DEFAULT' } }) }));
vi.mock('../../../lib/session/environment', () => ({
    useCurrentEnvironment: () => ({ id: 'env-1', name: 'Development', hrids: ['dev'] }),
}));

function renderPage() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <MemoryRouter>
                <LayoutSlotsProvider>
                    <TooltipProvider>
                        <DomainsPage />
                    </TooltipProvider>
                </LayoutSlotsProvider>
            </MemoryRouter>
        </QueryClientProvider>,
    );
}

describe('DomainsPage', () => {
    it('lists the domains of the current environment', async () => {
        vi.mocked(listDomains).mockResolvedValueOnce({
            data: [{ id: 'd1', hrid: 'acme', name: 'Acme', description: 'Customer realm', enabled: true, updatedAt: Date.now() }],
            currentPage: 0,
            totalCount: 1,
        });

        renderPage();

        expect(await screen.findByRole('link', { name: 'Acme' })).toHaveAttribute('href', '/acme');
        expect(screen.getByText('Customer realm')).toBeInTheDocument();
        expect(screen.getByText('Enabled')).toBeInTheDocument();
        expect(listDomains).toHaveBeenCalledWith('DEFAULT', 'env-1', 0, 25);
    });

    it('shows the first-use empty state when the environment has no domain', async () => {
        vi.mocked(listDomains).mockResolvedValueOnce({ data: [], currentPage: 0, totalCount: 0 });

        renderPage();

        expect(await screen.findByText('No security domains')).toBeInTheDocument();
    });
});
