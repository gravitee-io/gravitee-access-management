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
import { type Domain, listDomainEntrypoints, patchDomainEntrypoints } from '../../../lib/api/domains';
import { getInstallationConfiguration } from '../../../lib/api/management-api';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainEntrypointsPage } from '../DomainEntrypointsPage';

const domain: Domain = { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true, path: '/acme', vhostMode: false, vhosts: [] };
const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
const session = vi.hoisted(() => ({ domain: {} as Domain, domainRestrictions: [] as string[] }));

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/domains', () => ({ listDomainEntrypoints: vi.fn(), patchDomainEntrypoints: vi.fn() }));
vi.mock('../../../lib/api/management-api', () => ({ getInstallationConfiguration: vi.fn() }));
vi.mock('../../../lib/session/environment', () => ({
    useCurrentEnvironment: () => ({ id: 'env-1', name: 'Dev', hrids: ['dev'], domainRestrictions: session.domainRestrictions }),
}));
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
                            <DomainEntrypointsPage />
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </PermissionScope>
        </QueryClientProvider>,
    );
}

const editor = ['domain_settings_read', 'domain_settings_update'];

beforeEach(() => {
    vi.clearAllMocks();
    session.domainRestrictions = [];
    vi.mocked(listDomainEntrypoints).mockResolvedValue([
        { id: 'default', url: 'https://gw.example', defaultEntrypoint: true },
        { id: 'eu', url: 'https://eu.gw.example', defaultEntrypoint: false },
    ]);
    vi.mocked(getInstallationConfiguration).mockResolvedValue({});
});

describe('DomainEntrypointsPage', () => {
    it('shows the first entrypoint that is not the default, with the context path', async () => {
        renderPage(editor);

        expect(await screen.findByRole('textbox', { name: 'Domain entrypoint URL' })).toHaveValue('https://eu.gw.example');
        expect(screen.getByRole('textbox', { name: 'Domain context path' })).toHaveValue('/acme');
        expect(screen.getByText('https://eu.gw.example/acme')).toBeInTheDocument();
        expect(listDomainEntrypoints).toHaveBeenCalledWith(ref);
    });

    it('saves the Angular patchEntrypoints fields in context-path mode', async () => {
        vi.mocked(patchDomainEntrypoints).mockResolvedValueOnce({ ...domain, path: '/partners' });
        renderPage(editor);

        fireEvent.change(await screen.findByRole('textbox', { name: 'Domain context path' }), { target: { value: '/partners' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(patchDomainEntrypoints).toHaveBeenCalledWith(ref, { path: '/partners', vhostMode: false, vhosts: [] }));
        expect(toast.success).toHaveBeenCalledWith('Domain Acme updated');
    });

    it('blocks the save while the context path is empty', async () => {
        renderPage(editor);

        fireEvent.change(await screen.findByRole('textbox', { name: 'Domain context path' }), { target: { value: '' } });

        expect(screen.getByText('A path is required.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('adds a first virtual host with the domain path as the overriding one, then saves the virtual hosts', async () => {
        vi.mocked(patchDomainEntrypoints).mockResolvedValueOnce(domain);
        renderPage(editor);

        fireEvent.click(await screen.findByRole('button', { name: 'Switch to virtual hosts mode' }));
        fireEvent.click(screen.getByRole('button', { name: 'Add virtual host' }));
        expect(screen.getByRole('textbox', { name: 'Path' })).toHaveValue('/acme');
        expect(screen.getByRole('radio', { name: /Override entrypoint/ })).toBeChecked();
        fireEvent.change(screen.getByRole('textbox', { name: 'Host' }), { target: { value: 'auth.acme.example' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainEntrypoints).toHaveBeenCalledWith(ref, {
                path: '/acme',
                vhostMode: true,
                vhosts: [{ host: 'auth.acme.example', path: '/acme', overrideEntrypoint: true }],
            }),
        );
    });

    it('rejects a host outside the environment domain restrictions', async () => {
        session.domainRestrictions = ['acme.io'];
        renderPage(editor, { ...domain, vhostMode: true, vhosts: [{ host: 'auth.acme.io', path: '/', overrideEntrypoint: true }] });

        fireEvent.change(await screen.findByRole('combobox', { name: 'Host' }), { target: { value: 'auth.other.example' } });

        expect(await screen.findByText('Enter a host under acme.io, with an optional port.')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('shows a read-only form without domain_settings_update', async () => {
        renderPage(['domain_settings_read'], { ...domain, vhostMode: true, vhosts: [{ host: 'auth.acme.example', path: '/' }] });

        expect(await screen.findByRole('textbox', { name: 'Host' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Switch to context-path mode' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Add virtual host' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: /Remove virtual host/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });

    it('is read-only in cloud mode, without the switch mode button', async () => {
        vi.mocked(getInstallationConfiguration).mockResolvedValue({ type: 'managed' });
        renderPage(editor);

        await waitFor(() => expect(screen.getByRole('textbox', { name: 'Domain context path' })).toBeDisabled());
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Switch to virtual hosts mode' })).not.toBeInTheDocument();
    });

    it('warns when the organization has no entrypoint', async () => {
        vi.mocked(listDomainEntrypoints).mockResolvedValue([]);
        renderPage(editor);

        expect(await screen.findByText('Default entrypoint is missing')).toBeInTheDocument();
        expect(screen.queryByRole('textbox', { name: 'Domain context path' })).not.toBeInTheDocument();
    });

    it('shows an error panel when the entrypoints do not load', async () => {
        vi.mocked(listDomainEntrypoints).mockRejectedValue(new Error('Gateway down'));
        renderPage(editor);

        expect(await screen.findByText("Could not load the domain's entrypoints.")).toBeInTheDocument();
    });
});
