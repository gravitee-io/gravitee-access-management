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
import { LayoutSlotsProvider, SidebarProvider, TooltipProvider, useLayoutSlots } from '@gravitee/graphene-core';
import { renderWithGraphene } from '@gravitee/graphene-core/testing';
import { screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { PermissionScope } from '../../../lib/session/permissions';
import { domainSettingsRoutes } from '../routes';

vi.mock('../DomainGeneralPage', () => ({ DomainGeneralPage: () => <p>General page</p> }));
vi.mock('../../entrypoints/DomainEntrypointsPage', () => ({ DomainEntrypointsPage: () => <p>Entrypoints page</p> }));
vi.mock('../../../lib/session/domain', () => ({
    useCurrentDomain: () => ({
        domain: { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true },
        ref: { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' },
        basePath: '/environments/dev/domains/acme',
        domainsPath: '/environments/dev/domains',
    }),
}));

function Sidebar() {
    return <SidebarProvider>{useLayoutSlots().slots.contextSidebar}</SidebarProvider>;
}

function setupSettingsRoutesHarness(path: string, permissions: string[]) {
    renderWithGraphene(
        <PermissionScope permissions={permissions}>
            <MemoryRouter initialEntries={[`/environments/dev/domains/acme/${path}`]}>
                <LayoutSlotsProvider>
                    <TooltipProvider>
                        <Sidebar />
                        <Routes>
                            <Route path="/environments/dev/domains/acme">{domainSettingsRoutes}</Route>
                        </Routes>
                    </TooltipProvider>
                </LayoutSlotsProvider>
            </MemoryRouter>
        </PermissionScope>,
    );
}

describe('domainSettingsRoutes', () => {
    it('opens General for the settings root', async () => {
        setupSettingsRoutesHarness('settings', ['domain_settings_read']);

        expect(await screen.findByText('General page')).toBeInTheDocument();
    });

    it('mounts Entrypoints', async () => {
        setupSettingsRoutesHarness('settings/entrypoints', ['domain_settings_read']);

        expect(await screen.findByText('Entrypoints page')).toBeInTheDocument();
    });

    it('shows a placeholder for a settings page that has not moved yet', () => {
        setupSettingsRoutesHarness('settings/flows', ['domain_settings_read', 'domain_flow_list']);

        expect(screen.getByRole('heading', { name: 'Flows' })).toBeInTheDocument();
        expect(screen.getByText('Not available yet')).toBeInTheDocument();
    });

    it('refuses the settings without domain_settings_read', () => {
        setupSettingsRoutesHarness('settings/general', ['domain_flow_list']);

        expect(screen.getByText('Not allowed')).toBeInTheDocument();
        expect(screen.queryByText('General page')).not.toBeInTheDocument();
    });

    it('lists only the menu items the permissions allow', async () => {
        setupSettingsRoutesHarness('settings/general', ['domain_settings_read', 'domain_flow_list']);

        expect(await screen.findByText('General page')).toBeInTheDocument();
        expect(screen.getByText('Entrypoints')).toBeInTheDocument();
        expect(screen.getByText('Flows')).toBeInTheDocument();
        expect(screen.queryByText('Providers')).not.toBeInTheDocument();
        expect(screen.queryByText('SAML 2.0')).not.toBeInTheDocument();
    });
});
