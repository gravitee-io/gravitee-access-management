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
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route } from 'react-router-dom';
import type * as Domains from '../../../lib/api/domains';
import { patchDomainKeyRetrievalSettings } from '../../../lib/api/domains';
import { TrustDomainKeyRetrievalPage } from '../TrustDomainKeyRetrievalPage';
import { admin, currentDomain, ref, renderRoutes, trustDomainsPath } from './render';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/domains', async importOriginal => ({
    ...(await importOriginal<typeof Domains>()),
    patchDomainKeyRetrievalSettings: vi.fn(),
}));
vi.mock('../../../lib/session/domain', async () => {
    const { currentDomain } = await import('./render');
    return { useCurrentDomain: () => currentDomain };
});

const path = `${trustDomainsPath}/key-retrieval`;

function renderPage(permissions = admin) {
    return renderRoutes(path, <Route path={path} element={<TrustDomainKeyRetrievalPage />} />, permissions);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(patchDomainKeyRetrievalSettings).mockResolvedValue(currentDomain.domain);
});

describe('TrustDomainKeyRetrievalPage', () => {
    it('saves the key retrieval settings of the domain', async () => {
        renderPage();

        fireEvent.click(screen.getByRole('switch', { name: 'Allow unsecured HTTP JWKS URLs' }));
        fireEvent.change(screen.getByRole('spinbutton', { name: 'Cache max entries' }), { target: { value: '80' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainKeyRetrievalSettings).toHaveBeenCalledWith(ref, {
                allowUnsecuredHttpUri: true,
                allowPrivateIpAddress: false,
                fetchTimeoutMs: 5000,
                maxResponseSizeKb: 32,
                cacheTtlSeconds: 300,
                cacheMaxEntries: 80,
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('Key retrieval settings updated');
    });

    it('blocks the save while a value is under its minimum', () => {
        renderPage();

        fireEvent.change(screen.getByRole('spinbutton', { name: 'Fetch timeout (ms)' }), { target: { value: '50' } });

        expect(screen.getByText('Enter 100 or more.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('shows the settings read-only without domain_settings_update', () => {
        renderPage(['domain_settings_read', 'domain_trust_domain_list']);

        expect(screen.getByRole('switch', { name: 'Allow unsecured HTTP JWKS URLs' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });
});
