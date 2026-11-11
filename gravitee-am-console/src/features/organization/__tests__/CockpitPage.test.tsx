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
import { HttpError } from '../../../lib/api/http';
import { getInstallation } from '../../../lib/api/organization';
import { CockpitPage } from '../CockpitPage';
import { currentEnvironment, organizationPath, renderRoutes } from './render';

vi.mock('../../../lib/api/organization', () => ({ getInstallation: vi.fn() }));
vi.mock('../../../lib/session/environment', () => ({ useCurrentEnvironment: () => currentEnvironment }));

function renderPage() {
    return renderRoutes(`${organizationPath}/cockpit`, <Route path={`${organizationPath}/cockpit`} element={<CockpitPage />} />, [
        'installation_read',
    ]);
}

const url = 'https://cockpit.gravitee.io';

describe('CockpitPage', () => {
    it.each([
        [undefined, 'Meet Cockpit', 'Discover Cockpit'],
        ['PENDING', 'Installation pending', 'Open Cockpit'],
        ['ACCEPTED', 'Installation connected', 'Open Cockpit'],
        ['REJECTED', 'Installation rejected', 'Open Cockpit'],
        ['DELETED', 'Installation unlinked', 'Open Cockpit'],
    ])('shows the message of the %s status', async (status, title, link) => {
        vi.mocked(getInstallation).mockResolvedValueOnce({
            id: 'install-1',
            additionalInformation: { COCKPIT_URL: url, ...(status && { COCKPIT_INSTALLATION_STATUS: status }) },
        });

        renderPage();

        expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument();
        expect(screen.getByRole('link', { name: link })).toHaveAttribute('href', url);
        expect(screen.getByText('install-1')).toBeInTheDocument();
    });

    it('shows the error panel when the installation fails to load', async () => {
        vi.mocked(getInstallation).mockRejectedValueOnce(new HttpError(500, 'Service unavailable'));

        renderPage();

        expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the installation.');
    });
});
