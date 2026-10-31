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
import { type Domain, patchDomainWebProtectionSettings } from '../../../lib/api/domains';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainWebProtectionPage } from '../DomainWebProtectionPage';

const domain: Domain = { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true };
const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
const session = vi.hoisted(() => ({ domain: {} as Domain }));

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/domains', () => ({ patchDomainWebProtectionSettings: vi.fn() }));
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
                            <DomainWebProtectionPage />
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

const editor = ['domain_settings_read', 'domain_settings_update'];
const corsOn = { inherited: false, enabled: true, allowedOrigins: ['https://a.example'], allowedMethods: ['GET'], allowedHeaders: [] };

beforeEach(() => vi.clearAllMocks());

describe('DomainWebProtectionPage', () => {
    it('saves the Angular defaults for a domain with no settings, with CORS overridden', async () => {
        vi.mocked(patchDomainWebProtectionSettings).mockResolvedValueOnce(domain);
        renderPage(editor);

        fireEvent.click(screen.getByRole('switch', { name: 'Inherit platform settings', description: /cross-origin settings/ }));
        fireEvent.click(screen.getByRole('switch', { name: 'Enable CORS' }));
        const origin = screen.getByRole('combobox', { name: 'Allow-Origin' });
        fireEvent.change(origin, { target: { value: '*' } });
        fireEvent.keyDown(origin, { key: 'Enter' });
        fireEvent.click(screen.getByRole('checkbox', { name: 'POST' }));
        fireEvent.click(screen.getByRole('checkbox', { name: 'GET' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainWebProtectionSettings).toHaveBeenCalledWith(ref, {
                corsSettings: {
                    inherited: false,
                    enabled: true,
                    allowedOrigins: ['*'],
                    allowedMethods: ['GET', 'POST'],
                    allowedHeaders: [],
                    allowCredentials: false,
                    maxAge: 0,
                },
                webProtectionSettings: {
                    csp: { inherited: true, enabled: false, reportOnly: false, scriptInlineNonce: true, directives: [] },
                    xframe: { inherited: true, enabled: false, action: 'DENY' },
                    xss: { inherited: true, enabled: false, action: '1; mode=block' },
                },
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('Web protection settings for Acme updated');
    });

    it('treats a section saved without inherited as inherited unless it was enabled', () => {
        renderPage(editor, { ...domain, webProtectionSettings: { xframe: { enabled: true, action: 'SAMEORIGIN' } } });

        expect(screen.getByRole('switch', { name: 'Enable X-Frame-Options' })).toBeChecked();
        expect(screen.getByRole('switch', { name: 'Inherit platform settings', description: /X-Frame-Options/ })).not.toBeChecked();
    });

    it('rejects a duplicate origin and a duplicate header with the Angular messages', () => {
        renderPage(editor, { ...domain, corsSettings: { ...corsOn, allowedHeaders: ['Content-Type'] } });
        const origin = screen.getByRole('combobox', { name: 'Allow-Origin' });
        const header = screen.getByRole('combobox', { name: 'Allow-Headers' });

        fireEvent.change(origin, { target: { value: 'https://a.example' } });
        fireEvent.keyDown(origin, { key: 'Enter' });
        fireEvent.change(header, { target: { value: 'Content-Type' } });
        fireEvent.keyDown(header, { key: 'Enter' });

        expect(toast.error).toHaveBeenCalledWith('Error : Origin "https://a.example" already exists');
        expect(toast.error).toHaveBeenCalledWith('Error : Header "Content-Type" already exists');
    });

    it('asks for an origin once the empty Allow-Origin field loses focus', () => {
        renderPage(editor, { ...domain, corsSettings: { ...corsOn, allowedOrigins: [] } });
        const origin = screen.getByRole('combobox', { name: 'Allow-Origin' });
        expect(origin).not.toHaveAccessibleDescription('Add at least one origin.');

        fireEvent.blur(origin);

        expect(origin).toHaveAccessibleDescription('Add at least one origin.');
        fireEvent.click(screen.getByRole('checkbox', { name: 'POST' }));
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('blocks the save on a negative CORS max age', () => {
        renderPage(editor, { ...domain, corsSettings: corsOn });
        const maxAge = screen.getByRole('spinbutton', { name: 'Max age' });

        fireEvent.change(maxAge, { target: { value: '-1' } });

        expect(maxAge).toHaveAccessibleDescription('Enter a duration of 0 or more.');
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('shows a stored max age of 0 as empty', () => {
        renderPage(editor, { ...domain, corsSettings: { ...corsOn, maxAge: 0 } });

        expect(screen.getByRole('spinbutton', { name: 'Max age' })).toHaveValue(null);
    });

    it('shows the max age in its largest exact unit and saves it in seconds', async () => {
        vi.mocked(patchDomainWebProtectionSettings).mockResolvedValueOnce(domain);
        renderPage(editor, { ...domain, corsSettings: { ...corsOn, maxAge: 86_400 } });
        const maxAge = screen.getByRole('spinbutton', { name: 'Max age' });
        expect(maxAge).toHaveValue(1);
        expect(screen.getByRole('combobox', { name: 'Max age unit' })).toHaveTextContent('Days');

        fireEvent.change(maxAge, { target: { value: '2' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(vi.mocked(patchDomainWebProtectionSettings).mock.calls[0][1].corsSettings).toMatchObject({ maxAge: 172_800 }),
        );
    });

    it('serializes the CSP directives and blocks a duplicate directive', async () => {
        vi.mocked(patchDomainWebProtectionSettings).mockResolvedValueOnce(domain);
        renderPage(editor, {
            ...domain,
            webProtectionSettings: { csp: { inherited: false, enabled: true, directives: ["default-src 'self'"] } },
        });

        fireEvent.click(screen.getByRole('button', { name: 'Add directive' }));
        fireEvent.change(screen.getAllByRole('combobox', { name: 'Name' })[1], { target: { value: 'default-src' } });
        expect(await screen.findByText('This directive is already configured')).toBeInTheDocument();
        expect(screen.getAllByRole('combobox', { name: 'Name' })[1]).toHaveAccessibleDescription('This directive is already configured');
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

        fireEvent.change(screen.getAllByRole('combobox', { name: 'Name' })[1], { target: { value: 'upgrade-insecure-requests' } });
        await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled());
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(patchDomainWebProtectionSettings).toHaveBeenCalled());
        expect(vi.mocked(patchDomainWebProtectionSettings).mock.calls[0][1].webProtectionSettings.csp).toEqual({
            inherited: false,
            enabled: true,
            directives: ["default-src 'self'", 'upgrade-insecure-requests'],
        });
    });

    it('keeps a typed directive name when the suggestion list closes', async () => {
        renderPage(editor, { ...domain, webProtectionSettings: { csp: { inherited: false, enabled: true, directives: [] } } });

        fireEvent.click(screen.getByRole('button', { name: 'Add directive' }));
        const name = screen.getByRole('combobox', { name: 'Name' });
        fireEvent.change(name, { target: { value: 'default-sr' } });
        fireEvent.keyDown(name, { key: 'Escape' });

        await waitFor(() => expect(screen.getByRole('combobox', { name: /^Name/ })).toHaveValue('default-sr'));
    });

    it('shows a read-only form without domain_settings_update', () => {
        renderPage(['domain_settings_read'], {
            ...domain,
            webProtectionSettings: { csp: { inherited: false, enabled: true, directives: ["default-src 'self'"] } },
        });

        expect(screen.getByRole('switch', { name: 'Enable CSP' })).toBeDisabled();
        expect(screen.getByRole('combobox', { name: 'Name' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Add directive' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Remove directive/ })).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('switch', { name: 'Enable CSP' }));
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });
});
