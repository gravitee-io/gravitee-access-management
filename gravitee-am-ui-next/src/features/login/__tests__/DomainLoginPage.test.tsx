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
import { type Domain, patchDomainLoginSettings } from '../../../lib/api/domains';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainLoginPage } from '../DomainLoginPage';

const domain: Domain = {
    id: 'd1',
    hrid: 'acme',
    name: 'Acme',
    enabled: true,
    loginSettings: { inherited: false, forgotPasswordEnabled: true, passwordlessEnabled: true, identifierFirstEnabled: false },
};
const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
const session = vi.hoisted(() => ({ domain: {} as Domain }));

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/domains', () => ({ patchDomainLoginSettings: vi.fn() }));
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
                            <DomainLoginPage />
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

const editor = ['domain_settings_read', 'domain_settings_update'];

beforeEach(() => vi.clearAllMocks());

describe('DomainLoginPage', () => {
    it('saves the stored login settings with the change and inherited false', async () => {
        vi.mocked(patchDomainLoginSettings).mockResolvedValueOnce(domain);
        renderPage(editor);

        expect(screen.getByRole('switch', { name: 'Forgot password' })).toBeChecked();
        fireEvent.click(screen.getByRole('switch', { name: 'User registration' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainLoginSettings).toHaveBeenCalledWith(ref, {
                inherited: false,
                forgotPasswordEnabled: true,
                passwordlessEnabled: true,
                identifierFirstEnabled: false,
                registerEnabled: true,
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('Login configuration updated');
    });

    it('turns off and locks the hidden login form while identifier-first login is on', () => {
        renderPage(editor, { ...domain, loginSettings: { hideForm: true } });

        fireEvent.click(screen.getByRole('switch', { name: 'Identifier-first login' }));

        expect(screen.getByRole('switch', { name: 'Hide login form' })).not.toBeChecked();
        expect(screen.getByRole('switch', { name: 'Hide login form' })).toBeDisabled();
    });

    it('locks the device options until passwordless is on', () => {
        renderPage(editor, { ...domain, loginSettings: {} });

        expect(screen.getByRole('switch', { name: 'Passwordless device recognition' })).toBeDisabled();
        expect(screen.getByRole('switch', { name: 'Enforce password usage' })).toBeDisabled();
        fireEvent.click(screen.getByRole('switch', { name: 'Enable passwordless' }));
        expect(screen.getByRole('switch', { name: 'Passwordless device recognition' })).toBeEnabled();
        expect(screen.getByRole('switch', { name: 'Enforce password usage' })).toBeEnabled();
    });

    it('requires an HTTPS base URL when certificate-based authentication is on', () => {
        renderPage(editor);

        fireEvent.click(screen.getByRole('switch', { name: 'Passwordless certificate-based authentication (CBA)' }));
        expect(screen.getByText('Certificate based authentication URL is required when CBA is enabled.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

        fireEvent.change(screen.getByLabelText('Certificate-based authentication base URL'), {
            target: { value: 'http://cba.example.com' },
        });
        expect(screen.getByText('Provide a valid HTTPS URL (for example, https://cba.example.com/).')).toBeInTheDocument();

        fireEvent.change(screen.getByLabelText('Certificate-based authentication base URL'), {
            target: { value: 'https://cba.example.com' },
        });
        expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    });

    it('requires the max passwordless usage when password usage is enforced', () => {
        renderPage(editor);

        fireEvent.click(screen.getByRole('switch', { name: 'Enforce password usage' }));
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

        fireEvent.change(screen.getByRole('spinbutton', { name: 'Max passwordless usage (seconds)' }), { target: { value: '3600' } });
        expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    });

    it('shows a read-only form without domain_settings_update', () => {
        renderPage(['domain_settings_read']);

        expect(screen.getByRole('switch', { name: 'User registration' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });
});
