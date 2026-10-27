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
import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import type { Domain } from '../../lib/api/domains';
import { useEnvironmentDomains } from '../../lib/session/domains';
import { Shell } from '../Shell';

vi.mock('../../lib/session/session', () => ({
    useSession: () => ({
        user: { sub: 'admin', org: 'DEFAULT', permissions: [] },
        environments: [{ id: 'env-1', name: 'Production', hrids: ['prod'] }],
    }),
}));
vi.mock('../../lib/session/environment', () => ({
    useCurrentEnvironment: () => ({ id: 'env-1', name: 'Production', hrids: ['prod'] }),
}));
vi.mock('../../lib/session/preferences', () => ({ useUserPreferences: () => ({ data: {} }) }));
vi.mock('../../lib/session/domains', () => ({ useEnvironmentDomains: vi.fn() }));

const domains: Domain[] = [{ id: 'd1', hrid: 'one', name: 'One', enabled: true }];

function renderShellAt(path: string) {
    return render(
        <TooltipProvider>
            <LayoutSlotsProvider>
                <MemoryRouter initialEntries={[path]}>
                    <Routes>
                        <Route path="/environments/:envHrid" element={<Shell />}>
                            <Route path="domains/:domainHrid/dashboard" element={<p>Dashboard page</p>} />
                        </Route>
                    </Routes>
                </MemoryRouter>
            </LayoutSlotsProvider>
        </TooltipProvider>,
    );
}

describe('Shell', () => {
    it('renders the page of a domain in the environment', () => {
        vi.mocked(useEnvironmentDomains).mockReturnValue({
            data: { data: domains, currentPage: 0, totalCount: 1 },
            isPending: false,
        } as never);

        renderShellAt('/environments/prod/domains/one/dashboard');

        expect(screen.getByText('Dashboard page')).toBeInTheDocument();
    });

    it('shows the not found panel for a domain that is not in the environment', () => {
        vi.mocked(useEnvironmentDomains).mockReturnValue({
            data: { data: domains, currentPage: 0, totalCount: 1 },
            isPending: false,
        } as never);

        renderShellAt('/environments/prod/domains/gone/dashboard');

        expect(screen.getByText('Domain not found')).toBeInTheDocument();
        expect(screen.queryByText('Dashboard page')).not.toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Go to the security domains' })).toHaveAttribute('href', '/environments/prod/domains');
    });

    it('waits for the domain list before it decides', () => {
        vi.mocked(useEnvironmentDomains).mockReturnValue({ data: undefined, isPending: true } as never);

        renderShellAt('/environments/prod/domains/one/dashboard');

        expect(screen.getByText('Dashboard page')).toBeInTheDocument();
        expect(screen.queryByText('Domain not found')).not.toBeInTheDocument();
    });
});
