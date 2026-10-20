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
import { deleteCertificate, listCertificateKeys, listCertificates, rotateCertificate } from '../../../lib/api/certificates';
import { updateCertificateSettings } from '../../../lib/api/domains';
import { HttpError } from '../../../lib/api/http';
import { CertificatesPage, usageLabel } from '../CertificatesPage';
import { certificateTypeName } from '../certificate-types';
import { certificatesPath, ref, renderRoutes } from './render';

const domain = vi.hoisted(() => ({ fallback: null as string | null }));

vi.mock('../../../lib/api/certificates', () => ({
    deleteCertificate: vi.fn(),
    listCertificateKeys: vi.fn(),
    listCertificates: vi.fn(),
    rotateCertificate: vi.fn(),
}));
vi.mock('../../../lib/api/domains', () => ({ updateCertificateSettings: vi.fn() }));
vi.mock('../../../lib/session/domain', async () => {
    const { currentDomain: build } = await import('./render');
    return { useCurrentDomain: () => build(domain.fallback) };
});

const ALL = ['domain_certificate_create', 'domain_certificate_read', 'domain_certificate_delete'];

function renderPage(permissions = ALL) {
    return renderRoutes(certificatesPath, <Route path={certificatesPath} element={<CertificatesPage />} />, permissions);
}

function openActions(name: string) {
    fireEvent.pointerDown(screen.getByRole('button', { name: `Actions for ${name}` }), { button: 0, ctrlKey: false, pointerType: 'mouse' });
}

beforeEach(() => {
    vi.clearAllMocks();
    domain.fallback = null;
    vi.mocked(listCertificates).mockResolvedValue([
        {
            id: 'cert-1',
            name: 'Default',
            type: 'pkcs12-am-certificate',
            system: true,
            status: 'valid',
            usage: [],
            expiresAt: Date.UTC(2027, 8, 26),
            applications: [{ id: 'app-1', name: 'Portal' }],
            identityProviders: [],
        },
        {
            id: 'cert-2',
            name: 'Signing',
            type: 'javakeystore-am-certificate',
            system: false,
            status: 'will_expire',
            usage: ['sig', 'enc'],
            expiresAt: Date.now() + 3 * 24 * 3600 * 1000,
            applications: [],
            identityProviders: [{ id: 'idp-1', name: 'LDAP' }],
        },
    ]);
});

describe('CertificatesPage', () => {
    it('lists the certificates of the domain with their type, origin, usage, expiry and consumers', async () => {
        renderPage();

        const signing = (await screen.findByRole('link', { name: 'Signing' })).closest('tr')!;
        expect(screen.getByRole('link', { name: 'Signing' })).toHaveAttribute('href', `${certificatesPath}/cert-2`);
        expect(within(signing).getByText('Java Keystore (.jks)')).toBeInTheDocument();
        expect(within(signing).getByText('Own')).toBeInTheDocument();
        expect(within(signing).getByText('2 usages')).toBeInTheDocument();
        expect(within(signing).getByLabelText('Expiring in 3 days')).toBeInTheDocument();
        expect(within(signing).getByText('1 provider')).toBeInTheDocument();
        const system = screen.getByRole('link', { name: 'Default' }).closest('tr')!;
        expect(within(system).getByText('System')).toBeInTheDocument();
        expect(within(system).getByText('1 app')).toBeInTheDocument();
        expect(listCertificates).toHaveBeenCalledWith(ref);
    });

    it('marks the fallback certificate and blocks its delete', async () => {
        domain.fallback = 'cert-2';
        renderPage();

        const signing = (await screen.findByRole('link', { name: 'Signing' })).closest('tr')!;
        expect(within(signing).getByText('Fallback')).toBeInTheDocument();
        openActions('Signing');
        expect(screen.getByRole('menuitem', { name: 'Delete' })).toHaveAttribute('aria-disabled', 'true');
    });

    it('shows the first-use state when the domain has no certificate', async () => {
        vi.mocked(listCertificates).mockResolvedValueOnce([]);

        renderPage();

        expect(await screen.findByText('No certificates yet')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Rotate system key' })).not.toBeInTheDocument();
    });

    it('shows the error panel, not the first-use state, when the list fails', async () => {
        vi.mocked(listCertificates).mockRejectedValueOnce(new HttpError(500, 'Service unavailable'));

        renderPage();

        expect(await screen.findByRole('alert')).toHaveTextContent('Could not load the certificates.');
        expect(screen.queryByText('No certificates yet')).not.toBeInTheDocument();
    });

    it('deletes a certificate after the confirm dialog, then reloads the list', async () => {
        vi.mocked(deleteCertificate).mockResolvedValueOnce();
        renderPage();
        await screen.findByRole('link', { name: 'Signing' });

        openActions('Signing');
        fireEvent.click(screen.getByRole('menuitem', { name: 'Delete' }));
        const dialog = await screen.findByRole('dialog', { name: 'Delete certificate' });
        fireEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(deleteCertificate).toHaveBeenCalledWith(ref, 'cert-2'));
        await waitFor(() => expect(listCertificates).toHaveBeenCalledTimes(2));
    });

    it('shows the public keys of a certificate', async () => {
        vi.mocked(listCertificateKeys).mockResolvedValueOnce([{ fmt: 'PEM', payload: '-----BEGIN PUBLIC KEY-----' }]);
        renderPage();
        await screen.findByRole('link', { name: 'Signing' });

        openActions('Signing');
        fireEvent.click(screen.getByRole('menuitem', { name: 'Get public key' }));

        const dialog = await screen.findByRole('dialog', { name: 'Public certificate key' });
        expect(await within(dialog).findByRole('textbox', { name: 'PEM' })).toHaveValue('-----BEGIN PUBLIC KEY-----');
        expect(listCertificateKeys).toHaveBeenCalledWith(ref, 'cert-2');
    });

    it('rotates the system key after the confirm dialog, then reloads the list', async () => {
        vi.mocked(rotateCertificate).mockResolvedValueOnce({ id: 'cert-3', name: 'Default', type: 'pkcs12-am-certificate' });
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Rotate system key' }));
        fireEvent.click(within(await screen.findByRole('dialog', { name: 'Rotate key' })).getByRole('button', { name: 'Rotate' }));

        await waitFor(() => expect(rotateCertificate).toHaveBeenCalledWith(ref));
        await waitFor(() => expect(listCertificates).toHaveBeenCalledTimes(2));
    });

    it('saves the selected fallback certificate in the certificate settings', async () => {
        vi.mocked(updateCertificateSettings).mockResolvedValueOnce({ id: 'd1', hrid: 'acme', name: 'Acme', enabled: true });
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Settings' }));
        const dialog = await screen.findByRole('dialog', { name: 'Certificate settings' });
        fireEvent.pointerDown(within(dialog).getByRole('combobox', { name: 'Fallback certificate' }), {
            button: 0,
            ctrlKey: false,
            pointerType: 'mouse',
        });
        fireEvent.click(screen.getByRole('option', { name: 'Signing' }));
        fireEvent.click(within(dialog).getByRole('button', { name: 'Confirm' }));

        await waitFor(() => expect(updateCertificateSettings).toHaveBeenCalledWith(ref, { fallbackCertificate: 'cert-2' }));
    });

    it('saves no fallback after Clear', async () => {
        domain.fallback = 'cert-2';
        vi.mocked(updateCertificateSettings).mockResolvedValueOnce({ id: 'd1', hrid: 'acme', name: 'Acme', enabled: true });
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Settings' }));
        const dialog = await screen.findByRole('dialog', { name: 'Certificate settings' });
        fireEvent.click(within(dialog).getByRole('button', { name: 'Clear' }));

        expect(updateCertificateSettings).not.toHaveBeenCalled();

        fireEvent.click(within(dialog).getByRole('button', { name: 'Confirm' }));

        await waitFor(() => expect(updateCertificateSettings).toHaveBeenCalledWith(ref, { fallbackCertificate: null }));
    });

    it('hides create, rotate, settings, delete and the link without the permissions', async () => {
        renderPage([]);

        expect(await screen.findByText('Signing')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Signing' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Add certificate' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Rotate system key' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Settings' })).not.toBeInTheDocument();
        openActions('Signing');
        expect(screen.getByRole('menuitem', { name: 'Get public key' })).toBeInTheDocument();
        expect(screen.queryByRole('menuitem', { name: 'Delete' })).not.toBeInTheDocument();
    });
});

describe('usageLabel', () => {
    it('names one usage, counts several, and reads Undefined for none', () => {
        expect(usageLabel(['mtls'])).toBe('mTLS');
        expect(usageLabel(['sig', 'enc'])).toBe('2 usages');
        expect(usageLabel([])).toBe('Undefined');
    });
});

describe('certificateTypeName', () => {
    it('names a built-in plugin, else returns the fallback', () => {
        expect(certificateTypeName('aws-hsm-am-certificate')).toBe('AWS CloudHSM');
        expect(certificateTypeName('custom-am-certificate')).toBe('custom-am-certificate');
        expect(certificateTypeName('custom-am-certificate', 'Custom plugin')).toBe('Custom plugin');
    });
});
