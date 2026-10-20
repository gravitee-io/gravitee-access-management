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
import { createCertificate, getCertificateSchema, listCertificatePlugins } from '../../../lib/api/certificates';
import { CreateCertificatePage } from '../CreateCertificatePage';
import { certificatesPath, currentDomain, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/certificates', () => ({
    createCertificate: vi.fn(),
    getCertificateSchema: vi.fn(),
    listCertificatePlugins: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

function renderPage() {
    return renderRoutes(
        `${certificatesPath}/new`,
        <>
            <Route path={`${certificatesPath}/new`} element={<CreateCertificatePage />} />
            <Route path={`${certificatesPath}/:certificateId`} element={<p>Detail page</p>} />
        </>,
        [],
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listCertificatePlugins).mockResolvedValue([
        { id: 'javakeystore-am-certificate', name: 'Gravitee IO - Access Management - Certificate - JavaKeyStore' },
        { id: 'aws-am-certificate', name: 'Gravitee IO - Access Management - Certificate - AWS', feature: 'am-certificate-aws' },
        { id: 'custom-am-certificate', name: 'Custom certificate' },
    ]);
    vi.mocked(getCertificateSchema).mockResolvedValue({
        type: 'object',
        properties: { alias: { type: 'string', title: 'Key alias' } },
    });
    vi.mocked(createCertificate).mockResolvedValue({
        id: 'cert-9',
        name: 'Signing',
        type: 'javakeystore-am-certificate',
        configuration: '{}',
    });
});

describe('CreateCertificatePage', () => {
    it('names the plugins as Angular does and locks the ones the licence lacks', async () => {
        renderPage();

        expect(await screen.findByRole('radio', { name: 'Java Keystore (.jks)' })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'AWS Secret Manager, Enterprise' })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'Custom certificate' })).toBeInTheDocument();
    });

    it('creates the certificate with the Angular payload, then opens it', async () => {
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'Java Keystore (.jks)' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Signing' } });
        fireEvent.change(screen.getByRole('textbox', { name: /Key alias/ }), { target: { value: 'am' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() =>
            expect(createCertificate).toHaveBeenCalledWith(ref, {
                name: 'Signing',
                type: 'javakeystore-am-certificate',
                configuration: JSON.stringify({ alias: 'am' }),
            }),
        );
        expect(getCertificateSchema).toHaveBeenCalledWith('javakeystore-am-certificate');
        expect(await screen.findByText('Detail page')).toBeInTheDocument();
    });
});
