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
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { createDeviceIdentifier, getDeviceIdentifierSchema, listDeviceIdentifierPlugins } from '../../../lib/api/device-identifier';
import { CreateDeviceIdentifierPage } from '../CreateDeviceIdentifierPage';
import { PLUGINS, SCHEMAS } from './plugins';
import { currentDomain, deviceIdentifiersPath, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/device-identifier', () => ({
    createDeviceIdentifier: vi.fn(),
    getDeviceIdentifierSchema: vi.fn(),
    listDeviceIdentifierPlugins: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

function renderPage() {
    return renderRoutes(
        `${deviceIdentifiersPath}/new`,
        <>
            <Route path={`${deviceIdentifiersPath}/new`} element={<CreateDeviceIdentifierPage />} />
            <Route path={`${deviceIdentifiersPath}/:deviceIdentifierId`} element={<p>Detail page</p>} />
        </>,
        [],
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listDeviceIdentifierPlugins).mockResolvedValue(PLUGINS);
    vi.mocked(getDeviceIdentifierSchema).mockImplementation(async type => SCHEMAS[type]);
    vi.mocked(createDeviceIdentifier).mockImplementation(async (_ref, body) => ({ id: 'd-9', ...body }));
});

describe('CreateDeviceIdentifierPage', () => {
    it('names the plugins as Angular does and locks the ones the license lacks', async () => {
        renderPage();

        expect(await screen.findByRole('radio', { name: 'FingerprintJS v3 community' })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'Gravitee IO - Access Management - Device Identifier - Cookie' })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'FingerprintJS v3 Pro, Enterprise' })).toBeInTheDocument();
    });

    it('creates the device identifier with the schema defaults, then opens it', async () => {
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'FingerprintJS v3 community' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Fingerprint' } });
        expect(screen.getByRole('textbox', { name: 'Type' })).toHaveValue('FingerprintJS v3 community');
        expect(screen.getByRole('textbox', { name: 'Type' })).toBeDisabled();
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() =>
            expect(createDeviceIdentifier).toHaveBeenCalledWith(ref, {
                name: 'Fingerprint',
                type: 'fingerprintjs-v3-community-device-identifier',
                configuration: expect.any(String),
            }),
        );
        expect(JSON.parse(vi.mocked(createDeviceIdentifier).mock.calls[0][1].configuration)).toEqual({ useCookie: false });
        expect(await screen.findByText('Detail page')).toBeInTheDocument();
    });

    it('creates a plugin without settings with the configuration {}, as Angular does', async () => {
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'Gravitee IO - Access Management - Device Identifier - Cookie' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        expect(await screen.findByText('This plugin has no settings to configure.')).toBeInTheDocument();
        expect(screen.getByRole('textbox', { name: 'Type' })).toHaveValue('cookie-device-identifier');
        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Cookie' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() =>
            expect(createDeviceIdentifier).toHaveBeenCalledWith(ref, {
                name: 'Cookie',
                type: 'cookie-device-identifier',
                configuration: '{}',
            }),
        );
    });
});
