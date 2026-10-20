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
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { getLicense } from '../../api/license';
import { LicenseGuard, LicenseScope, LoadLicenseScope, RequireLicense, useLicense } from '../license';
import { AmFeature } from '../license-features';

vi.mock('../../api/license', () => ({ getLicense: vi.fn() }));

function Probe() {
    const { isMissing } = useLicense();
    return (
        <p>
            sms:{String(isMissing(AmFeature.AM_MFA_SMS))} ldap:{String(isMissing(AmFeature.AM_IDP_LDAP))} oss:{String(isMissing(undefined))}
        </p>
    );
}

describe('useLicense', () => {
    it('reports a feature the licence lacks, and never an absent feature', () => {
        render(
            <LicenseScope license={{ tier: 'universe', features: [AmFeature.AM_IDP_LDAP] }}>
                <Probe />
            </LicenseScope>,
        );

        expect(screen.getByText('sms:true ldap:false oss:false')).toBeInTheDocument();
    });
});

describe('LicenseGuard', () => {
    it('swallows the click and opens the upgrade dialog when the feature is missing', () => {
        const onClick = vi.fn();
        render(
            <LicenseScope license={{ features: [] }}>
                <LicenseGuard feature={AmFeature.AM_MFA_SMS}>
                    <button onClick={onClick}>Save</button>
                </LicenseGuard>
            </LicenseScope>,
        );

        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(onClick).not.toHaveBeenCalled();
        expect(screen.getByRole('dialog', { name: 'Enterprise feature' })).toHaveTextContent(
            'The SMS factor is part of Gravitee Enterprise.',
        );
        expect(screen.getByRole('link', { name: 'Request upgrade' })).toHaveAttribute(
            'href',
            'https://gravitee.io/self-hosted-trial?utm_source=oss_am&utm_medium=am-mfa-sms&utm_campaign=oss_am_to_ee_am',
        );
    });

    it('lets the click through when the licence grants the feature, or when there is no feature', () => {
        const onClick = vi.fn();
        render(
            <LicenseScope license={{ features: [AmFeature.AM_MFA_SMS] }}>
                <LicenseGuard feature={AmFeature.AM_MFA_SMS}>
                    <button onClick={onClick}>Save</button>
                </LicenseGuard>
                <LicenseGuard feature={undefined}>
                    <button onClick={onClick}>Create</button>
                </LicenseGuard>
            </LicenseScope>,
        );

        fireEvent.click(screen.getByRole('button', { name: 'Save' }));
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        expect(onClick).toHaveBeenCalledTimes(2);
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });
});

describe('RequireLicense', () => {
    it('renders the upgrade panel instead of the page when the feature is missing', () => {
        render(
            <LicenseScope license={{ features: [] }}>
                <MemoryRouter initialEntries={['/alerts']}>
                    <Routes>
                        <Route element={<RequireLicense feature={AmFeature.ALERT_ENGINE} />}>
                            <Route path="alerts" element={<p>Alerts page</p>} />
                        </Route>
                    </Routes>
                </MemoryRouter>
            </LicenseScope>,
        );

        expect(screen.queryByText('Alerts page')).not.toBeInTheDocument();
        expect(screen.getByText('Enterprise feature')).toBeInTheDocument();
    });
});

describe('LoadLicenseScope', () => {
    it('loads the licence before it renders the children', async () => {
        vi.mocked(getLicense).mockResolvedValueOnce({ tier: 'universe', features: [AmFeature.AM_MFA_SMS] });
        const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
        render(
            <QueryClientProvider client={client}>
                <LoadLicenseScope organizationId="DEFAULT">
                    <Probe />
                </LoadLicenseScope>
            </QueryClientProvider>,
        );

        expect(await screen.findByText('sms:false ldap:true oss:false')).toBeInTheDocument();
        expect(getLicense).toHaveBeenCalledWith('DEFAULT');
    });

    it('treats a failed load as no licence', async () => {
        vi.mocked(getLicense).mockRejectedValueOnce(new Error('boom'));
        const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
        render(
            <QueryClientProvider client={client}>
                <LoadLicenseScope organizationId="DEFAULT">
                    <Probe />
                </LoadLicenseScope>
            </QueryClientProvider>,
        );

        expect(await screen.findByText('sms:true ldap:true oss:false')).toBeInTheDocument();
    });
});
