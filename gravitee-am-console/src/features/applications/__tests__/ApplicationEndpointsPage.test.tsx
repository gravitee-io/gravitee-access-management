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
import { toast } from '@gravitee/graphene-core';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { listDomainEntrypoints } from '../../../lib/api/domains';
import { ApplicationContext } from '../ApplicationContext';
import { ApplicationEndpointsPage } from '../ApplicationEndpointsPage';
import { applicationsPath, currentDomain, ref, renderRoutes, webApplication } from './render';

const session = vi.hoisted(() => ({ domain: {} as Record<string, unknown> }));

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/domains', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    listDomainEntrypoints: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => ({ ...currentDomain, domain: session.domain }) }));

const appPath = `${applicationsPath}/app-1`;

function renderPage(domain: Record<string, unknown> = {}) {
    session.domain = { ...currentDomain.domain, ...domain };
    return renderRoutes(
        `${appPath}/endpoints`,
        <Route
            path={`${appPath}/endpoints`}
            element={
                <ApplicationContext value={{ application: webApplication, applicationPath: appPath, listPath: applicationsPath }}>
                    <ApplicationEndpointsPage />
                </ApplicationContext>
            }
        />,
        [],
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listDomainEntrypoints).mockResolvedValue([
        { id: 'default', url: 'https://default.gw', defaultEntrypoint: true },
        { id: 'public', url: 'https://auth.acme.com', defaultEntrypoint: false },
    ]);
});

describe('ApplicationEndpointsPage', () => {
    it('builds every endpoint on the entrypoint that is not the default, with the domain path', async () => {
        renderPage();

        expect(await screen.findByLabelText('Token endpoint')).toHaveValue('https://auth.acme.com/acme/oauth/token');
        expect(screen.getByLabelText('JWKS endpoint')).toHaveValue('https://auth.acme.com/acme/oidc/.well-known/jwks.json');
        expect(listDomainEntrypoints).toHaveBeenCalledWith(ref);
        expect(screen.queryByLabelText('SCIM configuration endpoint')).not.toBeInTheDocument();
    });

    it('uses the virtual host that overrides the entrypoint', async () => {
        renderPage({ vhostMode: true, vhosts: [{ host: 'login.acme.com', path: '/', overrideEntrypoint: true }] });

        expect(await screen.findByLabelText('Token endpoint')).toHaveValue('https://login.acme.com/oauth/token');
    });

    it('shows the endpoints of the protocols the domain has on', async () => {
        renderPage({ scim: { enabled: true }, saml: { enabled: true }, uma: { enabled: true }, oidc: { cibaSettings: { enabled: true } } });

        expect(await screen.findByLabelText('SCIM configuration endpoint')).toHaveValue(
            'https://auth.acme.com/acme/scim/ServiceProviderConfig',
        );
        expect(screen.getByLabelText('SAML 2.0 IdP metadata')).toBeInTheDocument();
        expect(screen.getByLabelText('UMA 2.0 configuration endpoint')).toBeInTheDocument();
        expect(screen.getByLabelText('Backchannel authentication endpoint')).toBeInTheDocument();
    });

    it('copies an endpoint and confirms with a toast', async () => {
        const writeText = vi.fn().mockResolvedValue(undefined);
        Object.assign(navigator, { clipboard: { writeText } });
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Copy Token endpoint' }));

        expect(writeText).toHaveBeenCalledWith('https://auth.acme.com/acme/oauth/token');
        await waitFor(() => expect(toast.success).toHaveBeenCalledWith('Copied to clipboard'));
    });

    it('shows the error panel when the entrypoints cannot be read', async () => {
        vi.mocked(listDomainEntrypoints).mockRejectedValue(new Error('Service unavailable'));
        renderPage();

        expect(await screen.findByText('Service unavailable')).toBeInTheDocument();
    });
});
