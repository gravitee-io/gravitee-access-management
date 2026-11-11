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
import { getApplication } from '../../../lib/api/applications';
import { listApplicationPermissions } from '../../../lib/api/permissions';
import { applicationRoutes } from '../routes';
import { applicationsPath, currentDomain, domainBasePath, renderRoutes, webApplication } from './render';

vi.mock('../../../lib/api/applications', async importOriginal => ({ ...(await importOriginal<object>()), getApplication: vi.fn() }));
vi.mock('../../../lib/api/permissions', () => ({ listApplicationPermissions: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));
vi.mock('../ApplicationsPage', () => ({ ApplicationsPage: () => <p>Applications page</p> }));
vi.mock('../NewApplicationPage', () => ({ NewApplicationPage: () => <p>Create page</p> }));
vi.mock('../ApplicationOverviewPage', () => ({ ApplicationOverviewPage: () => <p>Overview page</p> }));
vi.mock('../ApplicationIdentityProvidersPage', () => ({ ApplicationIdentityProvidersPage: () => <p>Identity providers page</p> }));
vi.mock('../ApplicationAnalyticsPage', () => ({ ApplicationAnalyticsPage: () => <p>Analytics page</p> }));
vi.mock('../ApplicationGeneralPage', () => ({ ApplicationGeneralPage: () => <p>General page</p> }));
vi.mock('../ApplicationMetadataPage', () => ({ ApplicationMetadataPage: () => <p>Metadata page</p> }));
vi.mock('../ApplicationMembersPage', () => ({ ApplicationMembersPage: () => <p>Members page</p> }));

const READ = ['application_read'];

function renderAt(section: string, permissions: string[]) {
    return renderRoutes(`${applicationsPath}${section}`, <Route path={domainBasePath}>{applicationRoutes}</Route>, permissions);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getApplication).mockResolvedValue(webApplication);
    vi.mocked(listApplicationPermissions).mockResolvedValue([]);
});

describe('application routes', () => {
    it.each([
        ['the list', '', ['application_list'], 'Applications page'],
        ['the creation', '/new', ['application_create'], 'Create page'],
        ['the identity providers', '/app-1/idp', [...READ, 'application_identity_provider_list'], 'Identity providers page'],
        ['the analytics', '/app-1/analytics', [...READ, 'application_analytics_list'], 'Analytics page'],
        ['the general settings', '/app-1/settings/general', [...READ, 'application_settings_read'], 'General page'],
        ['the metadata', '/app-1/settings/metadata', [...READ, 'application_settings_read'], 'Metadata page'],
        ['the members', '/app-1/settings/members', [...READ, 'application_member_list'], 'Members page'],
        ['a section a later story ports', '/app-1/design/forms', [...READ, 'application_form_list'], 'Forms'],
    ])('opens %s with its permissions', async (_name, section, permissions, page) => {
        renderAt(section, permissions);

        expect(await screen.findByText(page)).toBeInTheDocument();
    });

    it.each([
        ['the list', '', [], 'Applications page'],
        ['the creation', '/new', ['application_list'], 'Create page'],
        ['the application', '/app-1/overview', [], 'Overview page'],
        ['the identity providers', '/app-1/idp', READ, 'Identity providers page'],
        ['the analytics', '/app-1/analytics', READ, 'Analytics page'],
        ['the general settings', '/app-1/settings/general', [...READ, 'application_openid_read'], 'General page'],
        ['the metadata', '/app-1/settings/metadata', [...READ, 'application_openid_read'], 'Metadata page'],
        ['the members', '/app-1/settings/members', [...READ, 'application_settings_read'], 'Members page'],
        ['a section a later story ports', '/app-1/design/forms', [...READ, 'application_flow_list'], 'Forms'],
    ])('shows Not allowed on %s without its permission', async (_name, section, permissions, page) => {
        renderAt(section, permissions);

        expect(await screen.findByText('Not allowed')).toBeInTheDocument();
        expect(screen.queryByText(page)).not.toBeInTheDocument();
    });

    it('opens an application for a member of it alone', async () => {
        vi.mocked(listApplicationPermissions).mockResolvedValue(['application_read']);

        renderAt('/app-1/overview', []);

        expect(await screen.findByText('Overview page')).toBeInTheDocument();
    });

    it.each([
        ['SAML 2.0 while the domain has SAML off', '/app-1/settings/saml2', 'application_saml_read', 'SAML 2.0'],
        [
            'the resources of an application that is not a resource server',
            '/app-1/settings/resources',
            'application_resource_list',
            'Resources',
        ],
    ])('opens the overview for %s', async (_name, section, permission, title) => {
        renderAt(section, [...READ, permission]);

        expect(await screen.findByText('Overview page')).toBeInTheDocument();
        expect(screen.queryByRole('heading', { name: title })).not.toBeInTheDocument();
    });

    it('opens a section without the permission of its menu group, as Angular does', async () => {
        renderAt('/app-1/settings/factors', [...READ, 'application_factor_list']);

        expect(await screen.findByRole('heading', { name: 'Multifactor Auth' })).toBeInTheDocument();
    });

    it('takes the permissions of an application member into account', async () => {
        vi.mocked(listApplicationPermissions).mockResolvedValue(['application_read', 'application_analytics_list']);

        renderAt('/app-1/analytics', []);

        expect(await screen.findByText('Analytics page')).toBeInTheDocument();
    });
});
