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
import { deleteFactor, getFactor, getFactorSchema, listFactorPlugins, updateFactor } from '../../../lib/api/factors';
import { HttpError } from '../../../lib/api/http';
import { listResourcePlugins, listResourcesWhenPermitted } from '../../../lib/api/resources';
import { FactorPage } from '../FactorPage';
import { currentDomain, factorsPath, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/factors', () => ({
    deleteFactor: vi.fn(),
    getFactor: vi.fn(),
    getFactorSchema: vi.fn(),
    listFactorPlugins: vi.fn(),
    updateFactor: vi.fn(),
}));
vi.mock('../../../lib/api/resources', () => ({ listResourcesWhenPermitted: vi.fn(), listResourcePlugins: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_factor_update', 'domain_factor_delete'];
const factor = {
    id: 'f-1',
    name: 'Email OTP',
    type: 'email-am-factor',
    factorType: 'EMAIL',
    configuration: JSON.stringify({ returnDigits: 6 }),
};

function renderPage(permissions = ALL, features: string[] = []) {
    return renderRoutes(
        `${factorsPath}/f-1`,
        <>
            <Route path={`${factorsPath}/:factorId`} element={<FactorPage />} />
            <Route path={factorsPath} element={<p>Factor list</p>} />
        </>,
        permissions,
        features,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getFactor).mockResolvedValue(factor);
    vi.mocked(listFactorPlugins).mockResolvedValue([
        { id: 'email-am-factor', name: 'Email', category: 'email' },
        { id: 'sms-am-factor', name: 'SMS', category: 'sms', feature: 'am-factor-sms' },
        { id: 'fido2-am-factor', name: 'FIDO2', category: 'fido2' },
    ]);
    vi.mocked(listResourcesWhenPermitted).mockResolvedValue([]);
    vi.mocked(listResourcePlugins).mockResolvedValue([]);
    vi.mocked(getFactorSchema).mockResolvedValue({
        type: 'object',
        properties: {
            factorType: { default: 'EMAIL', readOnly: true },
            returnDigits: { type: 'integer', title: 'Number of digits' },
        },
    });
});

describe('FactorPage', () => {
    it('saves the name, type and serialized configuration, as Angular does', async () => {
        vi.mocked(updateFactor).mockResolvedValueOnce({ ...factor, name: 'Mail code' });
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Mail code' } });
        expect(screen.getByRole('textbox', { name: 'Factor type' })).toHaveValue('EMAIL');
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateFactor).toHaveBeenCalledWith(ref, 'f-1', {
                name: 'Mail code',
                type: 'email-am-factor',
                configuration: expect.any(String),
            }),
        );
        expect(JSON.parse(vi.mocked(updateFactor).mock.calls[0][2].configuration)).toMatchObject({ returnDigits: 6 });
        expect(getFactor).toHaveBeenCalledWith(ref, 'f-1');
    });

    it('fills the schema defaults and is clean again after a save', async () => {
        vi.mocked(getFactor).mockResolvedValue({ ...factor, type: 'otp-am-factor', factorType: 'TOTP', configuration: '{}' });
        vi.mocked(getFactorSchema).mockResolvedValue({
            type: 'object',
            properties: {
                factorType: { default: 'TOTP', readOnly: true },
                timeStep: { type: 'string', title: 'Time-step', default: '30', readonly: true },
            },
        });
        vi.mocked(updateFactor).mockImplementation(async (_ref, _id, body) => ({ ...factor, ...body }));
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Authenticator' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(updateFactor).toHaveBeenCalled());
        expect(JSON.parse(vi.mocked(updateFactor).mock.calls[0][2].configuration)).toMatchObject({ timeStep: '30' });
        await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled());
    });

    it('requires a name', async () => {
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: ' ' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByText('Name is required')).toBeInTheDocument();
        expect(updateFactor).not.toHaveBeenCalled();
    });

    it('deletes the factor after the confirm dialog, then opens the list', async () => {
        vi.mocked(deleteFactor).mockResolvedValueOnce();
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
        fireEvent.click(within(await screen.findByRole('dialog', { name: 'Delete factor' })).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(deleteFactor).toHaveBeenCalledWith(ref, 'f-1'));
        expect(await screen.findByText('Factor list')).toBeInTheDocument();
    });

    it('hides save and delete without the permissions', async () => {
        renderPage([]);

        expect(await screen.findByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('opens the upgrade dialog, and does not save, when the licence lacks the plugin feature', async () => {
        vi.mocked(getFactor).mockResolvedValue({ ...factor, type: 'sms-am-factor' });
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'SMS code' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByRole('dialog', { name: 'Enterprise feature' })).toBeInTheDocument();
        expect(updateFactor).not.toHaveBeenCalled();
    });

    it('shows the WebAuthn note for a FIDO2 factor', async () => {
        vi.mocked(getFactor).mockResolvedValue({ ...factor, type: 'fido2-am-factor', factorType: 'FIDO2' });
        renderPage();

        expect(await screen.findByText('This FIDO2 factor plugin is using existing WebAuthn configuration.')).toBeInTheDocument();
    });

    it('shows the not-found panel for a deleted factor', async () => {
        vi.mocked(getFactor).mockRejectedValueOnce(new HttpError(404, 'Factor not found'));
        renderPage();

        expect(await screen.findByText('Factor not found')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Back to factors' })).toHaveAttribute('href', factorsPath);
    });
});
