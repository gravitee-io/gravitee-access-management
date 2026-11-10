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
import { HttpError } from '../../../lib/api/http';
import { listApplicationPermissions } from '../../../lib/api/permissions';
import { ApplicationLayout } from '../ApplicationLayout';
import { ApplicationSettingsLayout } from '../ApplicationSettingsLayout';
import { RequireApplicationType } from '../RequireApplicationType';
import { applicationsPath, currentDomain, ref, renderRoutes, webApplication } from './render';

vi.mock('../../../lib/api/applications', async importOriginal => ({ ...(await importOriginal<object>()), getApplication: vi.fn() }));
vi.mock('../../../lib/api/permissions', () => ({ listApplicationPermissions: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const appPath = `${applicationsPath}/app-1`;

function renderLayout(path: string, domainPermissions: string[], applicationPermissions: string[] = []) {
    vi.mocked(listApplicationPermissions).mockResolvedValue(applicationPermissions);
    return renderRoutes(
        path,
        <Route path={`${applicationsPath}/:appId`} element={<ApplicationLayout />}>
            <Route path="overview" element={<p>Overview page</p>} />
            <Route element={<RequireApplicationType types={['web', 'native']} />}>
                <Route path="analytics" element={<p>Analytics page</p>} />
            </Route>
            <Route path="settings" element={<ApplicationSettingsLayout />}>
                <Route path="general" element={<p>General page</p>} />
                <Route path="members" element={<p>Members page</p>} />
                <Route path="factors" element={<p>Factors page</p>} />
            </Route>
        </Route>,
        domainPermissions,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getApplication).mockResolvedValue(webApplication);
});

describe('ApplicationLayout', () => {
    it('loads the application and its permissions, then shows the section', async () => {
        renderLayout(`${appPath}/overview`, ['application_read']);

        expect(await screen.findByText('Overview page')).toBeInTheDocument();
        expect(getApplication).toHaveBeenCalledWith(ref, 'app-1');
        expect(listApplicationPermissions).toHaveBeenCalledWith(ref, 'app-1');
    });

    it('opens for a member of the application alone', async () => {
        renderLayout(`${appPath}/overview`, [], ['application_read']);

        expect(await screen.findByText('Overview page')).toBeInTheDocument();
    });

    it('shows Not allowed, without loading the application, to a user who cannot read it', async () => {
        renderLayout(`${appPath}/overview`, ['application_list']);

        expect(await screen.findByText('Not allowed')).toBeInTheDocument();
        expect(getApplication).not.toHaveBeenCalled();
    });

    it('shows a not found panel for an application that does not exist', async () => {
        vi.mocked(getApplication).mockRejectedValue(new HttpError(404, 'Application not found'));
        renderLayout(`${appPath}/overview`, ['application_read']);

        expect(await screen.findByText('Application not found')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Back to applications' })).toHaveAttribute('href', applicationsPath);
    });

    it('opens a section the application type has', async () => {
        renderLayout(`${appPath}/analytics`, ['application_read']);

        expect(await screen.findByText('Analytics page')).toBeInTheDocument();
    });

    it('opens the overview for a section the application type does not have', async () => {
        vi.mocked(getApplication).mockResolvedValue({ ...webApplication, type: 'service' });
        renderLayout(`${appPath}/analytics`, ['application_read']);

        expect(await screen.findByText('Overview page')).toBeInTheDocument();
        expect(screen.queryByText('Analytics page')).not.toBeInTheDocument();
    });

    it('opens the general settings first', async () => {
        renderLayout(`${appPath}/settings`, ['application_read', 'application_settings_read', 'application_member_list']);

        expect(await screen.findByText('General page')).toBeInTheDocument();
    });

    it('opens the first settings page the user may read', async () => {
        renderLayout(`${appPath}/settings`, ['application_read', 'application_member_list']);

        expect(await screen.findByText('Members page')).toBeInTheDocument();
    });

    it('opens the factors with application_factor_read, as Angular does', async () => {
        renderLayout(`${appPath}/settings`, ['application_read', 'application_factor_read']);

        expect(await screen.findByText('Factors page')).toBeInTheDocument();
    });
});
