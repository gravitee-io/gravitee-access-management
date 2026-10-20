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
import { listApplications } from '../../../lib/api/applications';
import { PermissionScope } from '../../../lib/session/permissions';
import { ApplicationsPage } from '../ApplicationsPage';

vi.mock('../../../lib/api/applications', () => ({ listApplications: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({
    useCurrentDomain: () => ({
        domain: { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true },
        ref: { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' },
        basePath: '/environments/dev/domains/acme',
        domainsPath: '/environments/dev/domains',
    }),
}));

function renderPage(permissions: string[]) {
    vi.mocked(listApplications).mockResolvedValue({ data: [], currentPage: 0, totalCount: 0 });
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter initialEntries={['/environments/dev/domains/acme/applications']}>
                    <LayoutSlotsProvider>
                        <TooltipProvider>
                            <ApplicationsPage />
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

describe('ApplicationsPage', () => {
    it('links to the create page for a user with application_create', async () => {
        renderPage(['application_list', 'application_create']);

        expect(await screen.findByText('No applications')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Create application' })).toHaveAttribute(
            'href',
            '/environments/dev/domains/acme/applications/new',
        );
    });

    it('hides the create action from a user without application_create', async () => {
        renderPage(['application_list']);

        expect(await screen.findByText('No applications')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Create application' })).not.toBeInTheDocument();
    });
});
