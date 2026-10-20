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
    deleteCertificate,
    getCertificate,
    getCertificateSchema,
    listCertificatePlugins,
    updateCertificate,
} from '../../../lib/api/certificates';
import { HttpError } from '../../../lib/api/http';
import { CertificatePage } from '../CertificatePage';
import { certificatesPath, currentDomain, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/certificates', () => ({
    deleteCertificate: vi.fn(),
    getCertificate: vi.fn(),
    getCertificateSchema: vi.fn(),
    listCertificatePlugins: vi.fn(),
    updateCertificate: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_certificate_update', 'domain_certificate_delete'];
const certificate = {
    id: 'cert-2',
    name: 'Signing',
    type: 'javakeystore-am-certificate',
    system: false,
    configuration: JSON.stringify({ alias: 'am' }),
};

function renderPage(permissions = ALL, features: string[] = []) {
    return renderRoutes(
        `${certificatesPath}/cert-2`,
        <>
            <Route path={`${certificatesPath}/:certificateId`} element={<CertificatePage />} />
            <Route path={certificatesPath} element={<p>Certificate list</p>} />
        </>,
        permissions,
        features,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getCertificate).mockResolvedValue(certificate);
    vi.mocked(listCertificatePlugins).mockResolvedValue([
        { id: 'javakeystore-am-certificate', name: 'JavaKeyStore' },
        { id: 'aws-am-certificate', name: 'AWS', feature: 'am-certificate-aws' },
    ]);
    vi.mocked(getCertificateSchema).mockResolvedValue({
        type: 'object',
        properties: { alias: { type: 'string', title: 'Key alias' } },
    });
});

describe('CertificatePage', () => {
    it('saves the name, type and serialized configuration, as Angular does', async () => {
        vi.mocked(updateCertificate).mockResolvedValueOnce({ ...certificate, name: 'Signing key' });
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Signing key' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateCertificate).toHaveBeenCalledWith(ref, 'cert-2', {
                name: 'Signing key',
                type: 'javakeystore-am-certificate',
                configuration: JSON.stringify({ alias: 'am' }),
            }),
        );
        expect(getCertificate).toHaveBeenCalledWith(ref, 'cert-2');
    });

    it('requires a name', async () => {
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: ' ' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByText('Name is required')).toBeInTheDocument();
        expect(updateCertificate).not.toHaveBeenCalled();
    });

    it('shows the name only of a system certificate', async () => {
        vi.mocked(getCertificate).mockResolvedValueOnce({ ...certificate, system: true });
        renderPage();

        expect(await screen.findByRole('textbox', { name: 'Name' })).toBeInTheDocument();
        expect(screen.getByText('System')).toBeInTheDocument();
        expect(screen.queryByText('Configuration')).not.toBeInTheDocument();
        expect(getCertificateSchema).not.toHaveBeenCalled();
    });

    it('deletes the certificate after the confirm dialog, then opens the list', async () => {
        vi.mocked(deleteCertificate).mockResolvedValueOnce();
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
        fireEvent.click(within(await screen.findByRole('dialog', { name: 'Delete certificate' })).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(deleteCertificate).toHaveBeenCalledWith(ref, 'cert-2'));
        expect(await screen.findByText('Certificate list')).toBeInTheDocument();
    });

    it('hides save and delete without the permissions', async () => {
        renderPage([]);

        expect(await screen.findByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('opens the upgrade dialog, and does not save, when the licence lacks the plugin feature', async () => {
        vi.mocked(getCertificate).mockResolvedValue({ ...certificate, type: 'aws-am-certificate' });
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'AWS key' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByRole('dialog', { name: 'Enterprise feature' })).toBeInTheDocument();
        expect(updateCertificate).not.toHaveBeenCalled();
    });

    it('shows the not-found panel for a deleted certificate', async () => {
        vi.mocked(getCertificate).mockRejectedValueOnce(new HttpError(404, 'Certificate not found'));
        renderPage();

        expect(await screen.findByText('Certificate not found')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Back to certificates' })).toHaveAttribute('href', certificatesPath);
    });
});
