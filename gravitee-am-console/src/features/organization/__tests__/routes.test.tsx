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
import { listOrganizationEntrypoints, getOrganizationEntrypoint } from '../../../lib/api/entrypoints';
import { getOrganizationForm } from '../../../lib/api/forms';
import { getInstallationConfiguration } from '../../../lib/api/management-api';
import { getInstallation, getOrganizationSettings } from '../../../lib/api/organization';
import { getTag, listTags } from '../../../lib/api/tags';
import { organizationRoutes } from '../routes';
import { currentEnvironment, organizationPath, renderRoutes, session } from './render';

vi.mock('../../../lib/api/tags', () => ({ listTags: vi.fn(), getTag: vi.fn() }));
vi.mock('../../../lib/api/entrypoints', () => ({ listOrganizationEntrypoints: vi.fn(), getOrganizationEntrypoint: vi.fn() }));
vi.mock('../../../lib/api/forms', () => ({ getOrganizationForm: vi.fn() }));
vi.mock('../../../lib/api/organization', () => ({
    getOrganizationSettings: vi.fn(),
    listOrganizationIdentityProviders: vi.fn(),
    getInstallation: vi.fn(),
}));
vi.mock('../../../lib/api/management-api', () => ({ getInstallationConfiguration: vi.fn() }));
vi.mock('../../../lib/session/session', () => ({ useSession: () => session }));
vi.mock('../../../lib/session/environment', () => ({ useCurrentEnvironment: () => currentEnvironment }));
// The sidebar is a layout slot, so the routes render without it.
vi.mock('../../../lib/layout/useSectionSidebar', () => ({ useSectionSidebar: vi.fn() }));

const SETTINGS = 'organization_settings_read';

function renderAt(path: string, permissions: string[]) {
    return renderRoutes(path, <Route path="/environments/dev">{organizationRoutes}</Route>, permissions);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getInstallationConfiguration).mockResolvedValue({ type: 'standalone' });
});

describe('organizationRoutes', () => {
    it('shows the not allowed panel for the whole section without organization_settings_read', async () => {
        renderAt(`${organizationPath}/tags`, ['organization_tag_list']);

        expect(await screen.findByText('Not allowed')).toBeInTheDocument();
        expect(listTags).not.toHaveBeenCalled();
    });

    it.each([
        ['tags', [SETTINGS], listTags],
        ['tags/new', [SETTINGS, 'organization_tag_list'], listTags],
        ['tags/eu', [SETTINGS, 'organization_tag_list'], getTag],
        ['entrypoints', [SETTINGS], listOrganizationEntrypoints],
        ['entrypoints/new', [SETTINGS, 'organization_entrypoint_list'], listTags],
        ['entrypoints/e1', [SETTINGS, 'organization_entrypoint_list'], getOrganizationEntrypoint],
        ['forms', [SETTINGS], getOrganizationForm],
        ['forms/form?template=LOGIN', [SETTINGS, 'organization_form_list'], getOrganizationForm],
        ['cockpit', [SETTINGS], getInstallation],
    ])('shows the not allowed panel in place of %s without its permission, and loads nothing', async (path, permissions, loader) => {
        renderAt(
            `${organizationPath}/${path}`,
            [SETTINGS, ...permissions].filter(permission => permission !== SETTINGS || path !== 'general'),
        );

        expect(await screen.findByText('Not allowed')).toBeInTheDocument();
        expect(loader).not.toHaveBeenCalled();
    });

    it('leaves the Cockpit page for the first page of the section in cloud mode', async () => {
        vi.mocked(getInstallationConfiguration).mockResolvedValue({ type: 'managed' });
        vi.mocked(getOrganizationSettings).mockResolvedValue({ identities: [] });

        renderAt(`${organizationPath}/cockpit`, [SETTINGS, 'installation_read']);

        expect(await screen.findByRole('heading', { name: 'Authentication' })).toBeInTheDocument();
        expect(getInstallation).not.toHaveBeenCalled();
    });
});
