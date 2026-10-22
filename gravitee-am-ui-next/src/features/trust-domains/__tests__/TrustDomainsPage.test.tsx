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
import { deleteTrustedDomain, listTrustedDomains } from '../../../lib/api/trust-domains';
import { TrustDomainsPage } from '../TrustDomainsPage';
import { admin, ref, renderRoutes, trustDomainsPath } from './render';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/trust-domains', () => ({ deleteTrustedDomain: vi.fn(), listTrustedDomains: vi.fn() }));
vi.mock('../../../lib/session/domain', async () => {
    const { currentDomain } = await import('./render');
    return { useCurrentDomain: () => currentDomain };
});

const listPath = `${trustDomainsPath}/domains`;

function renderPage(permissions = admin) {
    return renderRoutes(listPath, <Route path={listPath} element={<TrustDomainsPage />} />, permissions);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listTrustedDomains).mockResolvedValue([
        {
            id: 'td-1',
            name: 'acme',
            domainIdentifier: 'https://sso.acme.com',
            keyMaterial: { source: 'jwk_set' },
            tokenExchange: { enabled: true },
            crossAppAccess: { enabled: true },
        },
        { id: 'td-2', name: 'workloads', spiffe: { spiffeTrustDomain: 'prod.example' }, keyMaterial: { source: 'pem' } },
    ]);
    vi.mocked(deleteTrustedDomain).mockResolvedValue(undefined);
});

describe('TrustDomainsPage', () => {
    it('lists the trusted domains with their usages and key sources', async () => {
        renderPage();

        const acme = await screen.findByRole('row', { name: /acme/ });
        expect(listTrustedDomains).toHaveBeenCalledWith(ref);
        expect(within(acme).getByText('2 usages')).toBeInTheDocument();
        expect(within(acme).getByText('JWK Set')).toBeInTheDocument();
        const workloads = screen.getByRole('row', { name: /workloads/ });
        expect(within(workloads).getByText('SPIFFE')).toBeInTheDocument();
        expect(within(workloads).getByText('PEM Certificate')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'acme' })).toHaveAttribute('href', `${listPath}/td-1`);
        expect(screen.getByRole('link', { name: 'Add trusted domain' })).toHaveAttribute('href', `${listPath}/new`);
        expect(screen.getByRole('tab', { name: 'Key retrieval' })).toBeInTheDocument();
    });

    it('shows the first-use state when the domain trusts no authority', async () => {
        vi.mocked(listTrustedDomains).mockResolvedValue([]);
        renderPage();

        expect(await screen.findByText('No trusted domains yet')).toBeInTheDocument();
        expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });

    it('deletes a trusted domain after the confirmation', async () => {
        renderPage();

        fireEvent.pointerDown(await screen.findByRole('button', { name: 'Actions for acme' }), {
            button: 0,
            ctrlKey: false,
            pointerType: 'mouse',
        });
        fireEvent.click(await screen.findByRole('menuitem', { name: 'Delete' }));
        const dialog = await screen.findByRole('dialog', { name: 'Delete Trusted Domain' });
        expect(within(dialog).getByText('Are you sure you want to delete "acme"?')).toBeInTheDocument();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(deleteTrustedDomain).toHaveBeenCalledWith(ref, 'td-1'));
        expect(toast.success).toHaveBeenCalledWith('Trusted domain deleted');
        expect(listTrustedDomains).toHaveBeenCalledTimes(2);
    });

    it('hides the create, open and delete actions and the key retrieval tab without the permissions', async () => {
        renderPage(['domain_trust_domain_list']);

        await screen.findByRole('row', { name: /acme/ });
        expect(screen.queryByRole('link', { name: 'Add trusted domain' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'acme' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Actions for acme' })).not.toBeInTheDocument();
        expect(screen.queryByRole('tab', { name: 'Key retrieval' })).not.toBeInTheDocument();
    });
});
