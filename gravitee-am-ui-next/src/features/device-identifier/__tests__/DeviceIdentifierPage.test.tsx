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
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { Route } from 'react-router-dom';
import {
    deleteDeviceIdentifier,
    getDeviceIdentifier,
    getDeviceIdentifierSchema,
    listDeviceIdentifierPlugins,
    updateDeviceIdentifier,
} from '../../../lib/api/device-identifier';
import { HttpError } from '../../../lib/api/http';
import { DeviceIdentifierPage } from '../DeviceIdentifierPage';
import { PLUGINS, SCHEMAS } from './plugins';
import { currentDomain, deviceIdentifiersPath, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/device-identifier', () => ({
    deleteDeviceIdentifier: vi.fn(),
    getDeviceIdentifier: vi.fn(),
    getDeviceIdentifierSchema: vi.fn(),
    listDeviceIdentifierPlugins: vi.fn(),
    updateDeviceIdentifier: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_device_identifier_update', 'domain_device_identifier_delete'];
const deviceIdentifier = {
    id: 'd-1',
    name: 'Fingerprint device',
    type: 'fingerprintjs-v3-community-device-identifier',
    configuration: JSON.stringify({ useCookie: true }),
};

function renderPage(permissions = ALL) {
    return renderRoutes(
        `${deviceIdentifiersPath}/d-1`,
        <>
            <Route path={`${deviceIdentifiersPath}/:deviceIdentifierId`} element={<DeviceIdentifierPage />} />
            <Route path={deviceIdentifiersPath} element={<p>Device identifier list</p>} />
        </>,
        permissions,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getDeviceIdentifier).mockResolvedValue(deviceIdentifier);
    vi.mocked(listDeviceIdentifierPlugins).mockResolvedValue(PLUGINS);
    vi.mocked(getDeviceIdentifierSchema).mockImplementation(async type => SCHEMAS[type]);
});

describe('DeviceIdentifierPage', () => {
    it('saves the name, type and serialized configuration, as Angular does', async () => {
        vi.mocked(updateDeviceIdentifier).mockImplementation(async (_ref, _id, body) => ({ ...deviceIdentifier, ...body }));
        renderPage();

        expect(await screen.findByRole('textbox', { name: 'Type' })).toHaveValue('FingerprintJS v3 community');
        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Fingerprint' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateDeviceIdentifier).toHaveBeenCalledWith(ref, 'd-1', {
                name: 'Fingerprint',
                type: 'fingerprintjs-v3-community-device-identifier',
                configuration: expect.any(String),
            }),
        );
        expect(JSON.parse(vi.mocked(updateDeviceIdentifier).mock.calls[0][2].configuration)).toEqual({ useCookie: true });
        expect(getDeviceIdentifier).toHaveBeenCalledWith(ref, 'd-1');
        await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled());
    });

    it('requires a name', async () => {
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: ' ' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByText('Name is required')).toBeInTheDocument();
        expect(updateDeviceIdentifier).not.toHaveBeenCalled();
    });

    it('says a plugin without settings has none, and saves the configuration {}', async () => {
        vi.mocked(getDeviceIdentifier).mockResolvedValue({ ...deviceIdentifier, type: 'cookie-device-identifier', configuration: '{}' });
        vi.mocked(updateDeviceIdentifier).mockImplementation(async (_ref, _id, body) => ({ ...deviceIdentifier, ...body }));
        renderPage();

        expect(await screen.findByText('This plugin has no settings to configure.')).toBeInTheDocument();
        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Cookie' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateDeviceIdentifier).toHaveBeenCalledWith(ref, 'd-1', {
                name: 'Cookie',
                type: 'cookie-device-identifier',
                configuration: '{}',
            }),
        );
    });

    it('keeps the name, Save and Delete when the plugin is not deployed and has no schema', async () => {
        vi.mocked(getDeviceIdentifier).mockResolvedValue({ ...deviceIdentifier, type: 'removed-device-identifier' });
        vi.mocked(getDeviceIdentifierSchema).mockResolvedValue({});
        renderPage();

        expect(await screen.findByRole('textbox', { name: 'Name' })).toHaveValue('Fingerprint device');
        expect(screen.getByText('This plugin has no settings to configure.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument();
    });

    it('deletes the device identifier after the confirm dialog, then opens the list', async () => {
        vi.mocked(deleteDeviceIdentifier).mockResolvedValueOnce();
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
        fireEvent.click(
            within(await screen.findByRole('dialog', { name: 'Delete device identifier' })).getByRole('button', { name: 'Delete' }),
        );

        await waitFor(() => expect(deleteDeviceIdentifier).toHaveBeenCalledWith(ref, 'd-1'));
        expect(await screen.findByText('Device identifier list')).toBeInTheDocument();
    });

    it('hides save and delete without the permissions', async () => {
        renderPage([]);

        expect(await screen.findByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('opens the upgrade dialog, and does not save, when the license lacks the plugin feature', async () => {
        vi.mocked(getDeviceIdentifier).mockResolvedValue({
            ...deviceIdentifier,
            type: 'fingerprintjs-v3-pro-device-identifier',
            configuration: JSON.stringify({ browserToken: 'token' }),
        });
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Pro' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByRole('dialog', { name: 'Enterprise feature' })).toBeInTheDocument();
        expect(updateDeviceIdentifier).not.toHaveBeenCalled();
    });

    it('shows the not-found panel for a deleted device identifier', async () => {
        vi.mocked(getDeviceIdentifier).mockRejectedValueOnce(new HttpError(404, 'Device identifier not found'));
        renderPage();

        expect(await screen.findByText('Device identifier not found')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Back to device identifiers' })).toHaveAttribute('href', deviceIdentifiersPath);
    });
});
