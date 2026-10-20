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
import { createFactor, getFactorSchema, listFactorPlugins } from '../../../lib/api/factors';
import { listResourcePlugins, listResourcesWhenPermitted } from '../../../lib/api/resources';
import { CreateFactorPage } from '../CreateFactorPage';
import { currentDomain, factorsPath, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/factors', () => ({
    createFactor: vi.fn(),
    getFactorSchema: vi.fn(),
    listFactorPlugins: vi.fn(),
}));
vi.mock('../../../lib/api/resources', () => ({ listResourcesWhenPermitted: vi.fn(), listResourcePlugins: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

function renderPage() {
    return renderRoutes(
        `${factorsPath}/new`,
        <>
            <Route path={`${factorsPath}/new`} element={<CreateFactorPage />} />
            <Route path={`${factorsPath}/:factorId`} element={<p>Detail page</p>} />
        </>,
        [],
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listFactorPlugins).mockResolvedValue([
        { id: 'email-am-factor', name: 'Email', category: 'email' },
        { id: 'fido2-am-factor', name: 'FIDO2', category: 'fido2' },
        { id: 'sms-am-factor', name: 'SMS', category: 'sms', feature: 'am-factor-sms' },
    ]);
    vi.mocked(listResourcesWhenPermitted).mockResolvedValue([]);
    vi.mocked(listResourcePlugins).mockResolvedValue([]);
    vi.mocked(getFactorSchema).mockResolvedValue({
        type: 'object',
        properties: {
            factorType: { default: 'EMAIL', readOnly: true },
            returnDigits: { type: 'integer', title: 'Number of digits' },
            issuer: { type: 'string', title: 'Issuer', default: 'Gravitee.io' },
        },
    });
    vi.mocked(createFactor).mockResolvedValue({
        id: 'f-9',
        name: 'Email OTP',
        type: 'email-am-factor',
        factorType: 'EMAIL',
        configuration: '{}',
    });
});

describe('CreateFactorPage', () => {
    it('names the plugins as Angular does and locks the ones the licence lacks', async () => {
        renderPage();

        expect(await screen.findByRole('radio', { name: 'Email Factor' })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'SMS Factor, Enterprise' })).toBeInTheDocument();
    });

    it('creates the factor with the factor type of the schema, then opens it', async () => {
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'Email Factor' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Email OTP' } });
        expect(screen.getByRole('textbox', { name: 'Factor type' })).toHaveValue('EMAIL');
        fireEvent.change(screen.getByRole('spinbutton', { name: /Number of digits/ }), { target: { value: '8' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() =>
            expect(createFactor).toHaveBeenCalledWith(ref, {
                name: 'Email OTP',
                type: 'email-am-factor',
                factorType: 'EMAIL',
                configuration: expect.any(String),
            }),
        );
        expect(JSON.parse(vi.mocked(createFactor).mock.calls[0][1].configuration)).toEqual({
            factorType: 'EMAIL',
            issuer: 'Gravitee.io',
            returnDigits: 8,
        });
        expect(listResourcesWhenPermitted).toHaveBeenCalledWith(ref);
        expect(getFactorSchema).toHaveBeenCalledWith('email-am-factor');
        expect(await screen.findByText('Detail page')).toBeInTheDocument();
    });

    it('shows the WebAuthn note for a FIDO2 factor', async () => {
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'FIDO2 Factor' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));

        expect(await screen.findByText(/FIDO2 factor plugin uses the existing WebAuthn configuration/)).toBeInTheDocument();
    });
});
