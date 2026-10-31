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
import { LayoutSlotsProvider, toast, TooltipProvider, useLayoutSlots } from '@gravitee/graphene-core';
import { renderWithGraphene } from '@gravitee/graphene-core/testing';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, screen, waitFor } from '@testing-library/react';
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
    ...(await importOriginal<Record<string, unknown>>()),
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

function Banner() {
    return <>{useLayoutSlots().slots.banner}</>;
}

function renderPage(permissions: string[], current: Domain = domain) {
    session.domain = current;
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return renderWithGraphene(
        <QueryClientProvider client={client}>
            <PermissionScope permissions={permissions}>
                <MemoryRouter>
                    <LayoutSlotsProvider>
                        <Banner />
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

    it('saves inherited false for a domain with no login settings', async () => {
        vi.mocked(patchDomainLoginSettings).mockResolvedValueOnce(domain);
        renderPage(editor, { ...domain, loginSettings: undefined });

        fireEvent.click(screen.getByRole('switch', { name: 'Passwordless magic link authentication' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(patchDomainLoginSettings).toHaveBeenCalledWith(ref, { magicLinkAuthEnabled: true, inherited: false }));
    });

    it('shows the error toast when the save fails', async () => {
        vi.mocked(patchDomainLoginSettings).mockRejectedValueOnce(new Error('Boom'));
        renderPage(editor);

        fireEvent.click(screen.getByRole('switch', { name: 'User registration' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Could not update the login configuration. Boom'));
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
        const url = screen.getByRole('textbox', { name: 'Certificate-based authentication base URL' });
        expect(url).toHaveAccessibleDescription('Certificate based authentication URL is required when CBA is enabled.');
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

        fireEvent.change(url, { target: { value: 'http://cba.example.com' } });
        expect(url).toHaveAccessibleDescription('Provide a valid HTTPS URL (for example, https://cba.example.com/).');

        fireEvent.change(url, { target: { value: 'https://cba.example.com' } });
        expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    });

    it('requires the max passwordless usage when password usage is enforced', () => {
        renderPage(editor);

        fireEvent.click(screen.getByRole('switch', { name: 'Enforce password usage' }));
        const maxAge = screen.getByRole('spinbutton', { name: 'Max passwordless usage (seconds)' });
        expect(maxAge).not.toHaveAccessibleDescription('Enter a number of seconds, 0 or more.');
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

        fireEvent.blur(maxAge);
        expect(maxAge).toHaveAccessibleDescription('Enter a number of seconds, 0 or more.');

        fireEvent.change(maxAge, { target: { value: '3600' } });
        expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    });

    it('turns password usage off once neither passwordless nor CBA is on', () => {
        renderPage(editor, { ...domain, loginSettings: { passwordlessEnabled: true } });

        fireEvent.click(screen.getByRole('switch', { name: 'Enforce password usage' }));
        fireEvent.blur(screen.getByRole('spinbutton', { name: 'Max passwordless usage (seconds)' }));
        fireEvent.click(screen.getByRole('switch', { name: 'Enable passwordless' }));

        expect(screen.getByRole('switch', { name: 'Enforce password usage' })).not.toBeChecked();
        const maxAge = screen.getByRole('spinbutton', { name: 'Max passwordless usage (seconds)' });
        expect(maxAge).toBeDisabled();
        expect(maxAge).not.toHaveAccessibleDescription('Enter a number of seconds, 0 or more.');
        expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
    });

    it('shows a read-only form without domain_settings_update', () => {
        renderPage(['domain_settings_read']);

        expect(screen.getByRole('switch', { name: 'User registration' })).toBeDisabled();
        fireEvent.click(screen.getByRole('switch', { name: 'Enable passwordless' }));
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });
});
