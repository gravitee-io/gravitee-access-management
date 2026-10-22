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
import { listDeviceIdentifiers } from '../../../lib/api/device-identifier';
import { DeviceIdentifiersPage } from '../DeviceIdentifiersPage';
import { currentDomain, deviceIdentifiersPath, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/device-identifier', () => ({ listDeviceIdentifiers: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_device_identifier_create', 'domain_device_identifier_read'];

function renderPage(permissions = ALL) {
    return renderRoutes(deviceIdentifiersPath, <Route path={deviceIdentifiersPath} element={<DeviceIdentifiersPage />} />, permissions);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listDeviceIdentifiers).mockResolvedValue([
        { id: 'd-1', name: 'Fingerprint', type: 'fingerprintjs-v3-community-device-identifier' },
        { id: 'd-2', name: 'Cookie', type: 'cookie-device-identifier' },
    ]);
});

describe('DeviceIdentifiersPage', () => {
    it('lists the device identifiers of the domain with the Angular type names and a link to each', async () => {
        renderPage();

        expect(await screen.findByRole('link', { name: 'Fingerprint' })).toHaveAttribute('href', `${deviceIdentifiersPath}/d-1`);
        expect(screen.getByText('FingerprintJS v3 community')).toBeInTheDocument();
        expect(screen.getByText('cookie-device-identifier')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Add device identifier' })).toHaveAttribute('href', `${deviceIdentifiersPath}/new`);
        expect(listDeviceIdentifiers).toHaveBeenCalledWith(ref);
    });

    it('shows the first-use state when the domain has no device identifier', async () => {
        vi.mocked(listDeviceIdentifiers).mockResolvedValueOnce([]);
        renderPage();

        expect(await screen.findByText('No device identifiers yet')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Add device identifier' })).toBeInTheDocument();
    });

    it('shows the error panel, not the first-use state, when the list fails', async () => {
        vi.mocked(listDeviceIdentifiers).mockRejectedValueOnce(new Error('Service unavailable'));
        renderPage();

        expect(await screen.findByText('Could not load the device identifiers.')).toBeInTheDocument();
        expect(screen.queryByText('No device identifiers yet')).not.toBeInTheDocument();
    });

    it('hides the add button and the links without the permissions', async () => {
        renderPage([]);

        expect(await screen.findByText('Fingerprint')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Fingerprint' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Add device identifier' })).not.toBeInTheDocument();
    });
});
