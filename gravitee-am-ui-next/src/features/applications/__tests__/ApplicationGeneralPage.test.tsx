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
import { MemoryRouter, Outlet, Route, Routes } from 'react-router-dom';
import type { ApplicationDetail } from '../../../lib/api/applications';
import { PermissionScope } from '../../../lib/session/permissions';
import { ApplicationGeneralPage } from '../ApplicationPage';

vi.mock('../../../lib/api/applications', () => ({ getApplication: vi.fn(), patchApplication: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({
    useCurrentDomain: () => ({
        domain: { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true },
        ref: { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' },
        basePath: '/environments/dev/domains/acme',
        domainsPath: '/environments/dev/domains',
    }),
}));

const application: ApplicationDetail = { id: 'a1', name: 'Shop', type: 'web', enabled: true };

function renderPage(permissions: string[]) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter initialEntries={['/settings/general']}>
                    <LayoutSlotsProvider>
                        <TooltipProvider>
                            <Routes>
                                <Route element={<Outlet context={application} />}>
                                    <Route path="settings/general" element={<ApplicationGeneralPage />} />
                                </Route>
                            </Routes>
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

describe('ApplicationGeneralPage', () => {
    it('lets a user with application_settings_update edit and save', () => {
        renderPage(['application_settings_read', 'application_settings_update']);

        expect(screen.getByRole('textbox', { name: 'Name' })).toBeEnabled();
        expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument();
    });

    it('shows a read-only form to a user without application_settings_update', () => {
        renderPage(['application_settings_read']);

        expect(screen.getByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });
});
