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
import type * as Graphene from '@gravitee/graphene-core';
import { toast } from '@gravitee/graphene-core';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { listAllScopes } from '../../../lib/api/scopes';
import {
    createTrustedDomain,
    deleteTrustedDomain,
    getTrustedDomain,
    type TrustedDomain,
    updateTrustedDomain,
} from '../../../lib/api/trust-domains';
import { NewTrustDomainPage } from '../NewTrustDomainPage';
import { TrustDomainPage } from '../TrustDomainPage';
import { admin, ref, renderRoutes, trustDomainsPath } from './render';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/trust-domains', () => ({
    createTrustedDomain: vi.fn(),
    deleteTrustedDomain: vi.fn(),
    getTrustedDomain: vi.fn(),
    updateTrustedDomain: vi.fn(),
}));
vi.mock('../../../lib/api/scopes', () => ({ listAllScopes: vi.fn() }));
vi.mock('../../../lib/session/domain', async () => {
    const { currentDomain } = await import('./render');
    return { useCurrentDomain: () => currentDomain };
});

const listPath = `${trustDomainsPath}/domains`;

const stored: TrustedDomain = {
    id: 'td-1',
    name: 'acme',
    domainIdentifier: 'https://sso.acme.com',
    keyMaterial: { source: 'pem', certificate: 'cert', refreshIntervalSeconds: 300 },
    crossAppAccess: { enabled: true, resourceServers: [{ id: 'rs-1', name: 'Calendar', resource: 'https://calendar.acme.com' }] },
    updatedAt: 1,
};

function renderPage(path: string, permissions = admin) {
    return renderRoutes(
        path,
        <>
            <Route path={`${listPath}/new`} element={<NewTrustDomainPage />} />
            <Route path={`${listPath}/:trustDomainId`} element={<TrustDomainPage />} />
            <Route path={listPath} element={<h1>Trusted domains list</h1>} />
        </>,
        permissions,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listAllScopes).mockResolvedValue([
        { id: 's1', key: 'openid', name: 'openid', description: '', system: true, discovery: true, parameterized: false },
    ]);
    vi.mocked(getTrustedDomain).mockResolvedValue(stored);
});

describe('NewTrustDomainPage', () => {
    it('creates a token exchange issuer named after its issuer, then opens it', async () => {
        vi.mocked(createTrustedDomain).mockResolvedValue({ ...stored, id: 'td-new', name: 'https-sso.acme.com' });
        renderPage(`${listPath}/new`);

        expect(screen.getByRole('checkbox', { name: 'Token exchange' })).toBeChecked();
        fireEvent.change(screen.getByRole('textbox', { name: 'Issuer URL' }), { target: { value: 'https://sso.acme.com' } });
        expect(screen.getByRole('textbox', { name: 'Name' })).toHaveValue('https-sso.acme.com');
        expect(screen.getByText('JWKS URL is required when the key source is JWKS URL.')).toBeInTheDocument();
        fireEvent.change(screen.getByRole('textbox', { name: 'JWKS URL' }), { target: { value: 'https://sso.acme.com/jwks' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() =>
            expect(createTrustedDomain).toHaveBeenCalledWith(ref, {
                name: 'https-sso.acme.com',
                description: undefined,
                keyMaterial: { source: 'JWKS_URL', jwksUrl: 'https://sso.acme.com/jwks', refreshIntervalSeconds: 300 },
                domainIdentifier: 'https://sso.acme.com',
                spiffe: { spiffeTrustDomain: '', allowedAlgorithms: [] },
                tokenExchange: { enabled: true, scopeMappings: {}, userBindingEnabled: false, userBindingCriteria: [] },
                crossAppAccess: { enabled: false },
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('Trusted domain created');
        expect(await screen.findByRole('heading', { level: 1, name: 'https-sso.acme.com' })).toBeInTheDocument();
    });

    it('shows the server error and keeps the form when the create fails', async () => {
        vi.mocked(createTrustedDomain).mockRejectedValue(new Error('JWKS URL host sso.acme.com could not be resolved'));
        renderPage(`${listPath}/new`);

        fireEvent.change(screen.getByRole('textbox', { name: 'Issuer URL' }), { target: { value: 'https://sso.acme.com' } });
        fireEvent.change(screen.getByRole('textbox', { name: 'JWKS URL' }), { target: { value: 'https://sso.acme.com/jwks' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('JWKS URL host sso.acme.com could not be resolved'));
        expect(screen.getByRole('textbox', { name: 'Issuer URL' })).toHaveValue('https://sso.acme.com');
    });
});

describe('TrustDomainPage', () => {
    it('renames a resource server in its row and saves it with its id', async () => {
        vi.mocked(updateTrustedDomain).mockResolvedValue({ ...stored, updatedAt: 2 });
        renderPage(`${listPath}/td-1`);

        fireEvent.click(await screen.findByRole('button', { name: 'Edit Calendar' }));
        const name = screen.getByRole('textbox', { name: 'Edited resource server name' });
        fireEvent.change(name, { target: { value: '' } });
        expect(screen.getByText('Finish editing the resource server: its name and resource URI are both required.')).toBeInTheDocument();
        fireEvent.change(name, { target: { value: 'Agenda' } });
        fireEvent.keyDown(name, { key: 'Enter' });
        expect(screen.queryByText(/Finish editing the resource server/)).not.toBeInTheDocument();
        expect(screen.getByRole('row', { name: /Agenda/ })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(updateTrustedDomain).toHaveBeenCalled());
        expect(vi.mocked(updateTrustedDomain).mock.calls[0]).toEqual([
            ref,
            'td-1',
            {
                name: 'acme',
                description: undefined,
                keyMaterial: { source: 'PEM', certificate: 'cert', refreshIntervalSeconds: 300 },
                domainIdentifier: 'https://sso.acme.com',
                spiffe: { spiffeTrustDomain: '', allowedAlgorithms: [] },
                tokenExchange: { enabled: false, scopeMappings: {}, userBindingEnabled: false, userBindingCriteria: [] },
                crossAppAccess: {
                    enabled: true,
                    resourceServers: [{ id: 'rs-1', name: 'Agenda', resource: 'https://calendar.acme.com' }],
                    audSubMapping: undefined,
                    scopeMappings: undefined,
                },
            },
        ]);
        expect(toast.success).toHaveBeenCalledWith('Trusted domain updated');
    });

    it('deletes the trusted domain from the danger zone and goes back to the list', async () => {
        vi.mocked(deleteTrustedDomain).mockResolvedValue(undefined);
        renderPage(`${listPath}/td-1`);

        fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
        const dialog = await screen.findByRole('dialog', { name: 'Delete Trusted Domain' });
        fireEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(deleteTrustedDomain).toHaveBeenCalledWith(ref, 'td-1'));
        expect(toast.success).toHaveBeenCalledWith('Trusted domain deleted');
        expect(await screen.findByRole('heading', { name: 'Trusted domains list' })).toBeInTheDocument();
    });

    it('shows the form read-only without the update and delete permissions', async () => {
        renderPage(`${listPath}/td-1`, ['domain_trust_domain_list', 'domain_trust_domain_read']);

        expect(await screen.findByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Edit Calendar' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });
});
