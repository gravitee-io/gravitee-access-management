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
import { screen } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { listResourcePlugins, listResources } from '../../../lib/api/resources';
import { ServicesPage } from '../ServicesPage';
import { currentDomain, ref, renderRoutes, resourcesPath } from './render';

vi.mock('../../../lib/api/resources', () => ({ listResources: vi.fn(), listResourcePlugins: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_resource_create', 'domain_resource_read'];

function renderPage(permissions = ALL) {
    return renderRoutes(resourcesPath, <Route path={resourcesPath} element={<ServicesPage />} />, permissions);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listResources).mockResolvedValue([
        { id: 'r-1', name: 'Mail server', type: 'smtp-am-resource' },
        { id: 'r-2', name: 'Custom', type: 'custom-am-resource' },
    ]);
    vi.mocked(listResourcePlugins).mockResolvedValue([{ id: 'smtp-am-resource', name: 'SMTP Resource' }]);
});

describe('ServicesPage', () => {
    it('lists the resources of the domain with the Angular type names and a link to each', async () => {
        renderPage();

        expect(await screen.findByRole('link', { name: 'Mail server' })).toHaveAttribute('href', `${resourcesPath}/r-1`);
        expect(screen.getByText('SMTP')).toBeInTheDocument();
        expect(screen.getByText('custom-am-resource')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Add service' })).toHaveAttribute('href', `${resourcesPath}/new`);
        expect(listResources).toHaveBeenCalledWith(ref);
    });

    it('shows the first-use state when the domain has no resource', async () => {
        vi.mocked(listResources).mockResolvedValueOnce([]);
        renderPage();

        expect(await screen.findByText('No services yet')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Add service' })).toBeInTheDocument();
    });

    it('shows the error panel, not the first-use state, when the list fails', async () => {
        vi.mocked(listResources).mockRejectedValueOnce(new Error('Service unavailable'));
        renderPage();

        expect(await screen.findByText('Could not load the services.')).toBeInTheDocument();
        expect(screen.queryByText('No services yet')).not.toBeInTheDocument();
    });

    it('hides the add button without the create permission', async () => {
        renderPage([]);

        expect(await screen.findByRole('link', { name: 'Mail server' })).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Add service' })).not.toBeInTheDocument();
    });

    it('lists the resources without icons when the plugin list fails', async () => {
        vi.mocked(listResourcePlugins).mockRejectedValueOnce(new Error('Service unavailable'));
        renderPage();

        expect(await screen.findByRole('link', { name: 'Mail server' })).toBeInTheDocument();
        expect(screen.queryByText('Could not load the services.')).not.toBeInTheDocument();
    });
});
