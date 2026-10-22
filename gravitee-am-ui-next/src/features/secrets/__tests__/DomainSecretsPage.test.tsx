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
import { LayoutSlotsProvider, TooltipProvider, toast } from '@gravitee/graphene-core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { type Domain, patchDomainSecretSettings } from '../../../lib/api/domains';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainSecretsPage } from '../DomainSecretsPage';

const domain: Domain = {
    id: 'd1',
    hrid: 'acme',
    name: 'Acme',
    enabled: true,
};
const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
const session = vi.hoisted(() => ({ domain: {} as Domain }));

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/domains', () => ({ patchDomainSecretSettings: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({
    useCurrentDomain: () => ({
        domain: session.domain,
        ref: { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' },
        basePath: '/environments/dev/domains/acme',
        domainsPath: '/environments/dev/domains',
    }),
}));

function renderPage(permissions: string[], current: Domain = domain) {
    session.domain = current;
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter>
                    <LayoutSlotsProvider>
                        <TooltipProvider>
                            <DomainSecretsPage />
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

const editor = ['domain_settings_read', 'domain_settings_update'];
const stored: Domain = { ...domain, secretExpirationSettings: { enabled: true, expiryTimeSeconds: 7_776_000 } };

beforeEach(() => vi.clearAllMocks());

describe('DomainSecretsPage', () => {
    it('enables the expiry with the 90 days default and saves it in seconds', async () => {
        vi.mocked(patchDomainSecretSettings).mockResolvedValueOnce(stored);
        renderPage(editor);

        expect(screen.queryByRole('spinbutton', { name: 'Expiry time' })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('switch', { name: 'Enable client secret expiry' }));
        expect(screen.getByRole('spinbutton', { name: 'Expiry time' })).toHaveValue(90);
        expect(screen.getByRole('combobox', { name: 'Expiry time unit' })).toHaveTextContent('Days');
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(patchDomainSecretSettings).toHaveBeenCalledWith(ref, { enabled: true, expiryTimeSeconds: 7_776_000 }));
        expect(toast.success).toHaveBeenCalledWith('Secrets configuration updated');
    });

    it('converts a duration in months as moment does', async () => {
        vi.mocked(patchDomainSecretSettings).mockResolvedValueOnce(stored);
        renderPage(editor, stored);

        fireEvent.click(screen.getByRole('combobox', { name: 'Expiry time unit' }));
        fireEvent.click(await screen.findByRole('option', { name: 'Months' }));
        fireEvent.change(screen.getByRole('spinbutton', { name: 'Expiry time' }), { target: { value: '2' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(patchDomainSecretSettings).toHaveBeenCalledWith(ref, { enabled: true, expiryTimeSeconds: 5_270_400 }));
    });

    it('blocks the save while the duration is empty or below 1', () => {
        renderPage(editor, stored);
        const time = screen.getByRole('spinbutton', { name: 'Expiry time' });

        fireEvent.change(time, { target: { value: '' } });
        expect(screen.getByText('Duration is required.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

        fireEvent.change(time, { target: { value: '0' } });
        expect(screen.getByText('Duration must be greater than 0.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('shows a read-only form without domain_settings_update', () => {
        renderPage(['domain_settings_read'], stored);

        expect(screen.getByRole('switch', { name: 'Enable client secret expiry' })).toBeDisabled();
        expect(screen.getByRole('spinbutton', { name: 'Expiry time' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });
});
