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
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { listDataPlanes } from '../../../lib/api/data-planes';
import { type Domain, deleteDomain, patchDomainGeneralSettings } from '../../../lib/api/domains';
import { listTags } from '../../../lib/api/tags';
import { PermissionScope } from '../../../lib/session/permissions';
import { DomainGeneralPage } from '../DomainGeneralPage';

const domain: Domain = {
    id: 'd1',
    hrid: 'acme',
    name: 'Acme',
    description: 'Customer realm',
    enabled: true,
    path: '/acme',
    master: false,
    tags: ['eu'],
    dataPlaneId: 'default',
    oidc: { postLogoutRedirectUris: ['https://acme.example.com/bye'], requestUris: ['https://acme.example.com/req.jwt'] },
};
const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };
const session = vi.hoisted(() => ({ domain: {} as Domain }));

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/domains', () => ({ patchDomainGeneralSettings: vi.fn(), deleteDomain: vi.fn() }));
vi.mock('../../../lib/api/tags', () => ({ listTags: vi.fn() }));
vi.mock('../../../lib/api/data-planes', () => ({ listDataPlanes: vi.fn() }));
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
                <MemoryRouter initialEntries={['/environments/dev/domains/acme/settings/general']}>
                    <LayoutSlotsProvider>
                        <TooltipProvider>
                            <Routes>
                                <Route path="/environments/dev/domains/acme/settings/general" element={<DomainGeneralPage />} />
                                <Route path="/environments/dev/domains/:hrid/settings/general" element={<p>Renamed domain</p>} />
                                <Route path="/environments/dev/domains" element={<p>Domains list</p>} />
                            </Routes>
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
    vi.mocked(listTags).mockResolvedValue([{ id: 'eu', name: 'Europe' }]);
    vi.mocked(listDataPlanes).mockResolvedValue([{ id: 'default', name: 'Default' }]);
});

describe('DomainGeneralPage', () => {
    it('saves the Angular patchGeneralSettings fields, with the path unchanged', async () => {
        vi.mocked(patchDomainGeneralSettings).mockResolvedValueOnce({ ...domain, description: 'Partners' });
        renderPage(editor);

        fireEvent.change(screen.getByRole('textbox', { name: 'Description' }), { target: { value: 'Partners' } });
        fireEvent.click(screen.getByRole('switch', { name: /Master domain/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(patchDomainGeneralSettings).toHaveBeenCalledWith(ref, {
                name: 'Acme',
                description: 'Partners',
                path: '/acme',
                enabled: true,
                tags: ['eu'],
                oidc: { postLogoutRedirectUris: ['https://acme.example.com/bye'], requestUris: ['https://acme.example.com/req.jwt'] },
                master: true,
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('Domain Acme updated');
    });

    it('keeps an absent description absent when the user does not edit it', async () => {
        const { description: _description, ...withoutDescription } = domain;
        vi.mocked(patchDomainGeneralSettings).mockResolvedValueOnce(withoutDescription);
        renderPage(editor, withoutDescription);

        fireEvent.click(screen.getByRole('switch', { name: /Enable domain/ }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(patchDomainGeneralSettings).toHaveBeenCalled());
        expect(vi.mocked(patchDomainGeneralSettings).mock.calls[0][1]).toMatchObject({ description: undefined, enabled: false });
    });

    it('opens the new URL when the save gives the domain a new hrid', async () => {
        vi.mocked(patchDomainGeneralSettings).mockResolvedValueOnce({ ...domain, name: 'Acme Corp', hrid: 'acme-corp' });
        renderPage(editor);

        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Acme Corp' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByText('Renamed domain')).toBeInTheDocument();
        expect(toast.success).toHaveBeenCalledWith('Domain Acme Corp updated');
    });

    it('blocks the save while the name is empty', () => {
        renderPage(editor);

        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: '' } });

        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('adds a post-logout redirect URI and rejects a duplicate with the Angular message', () => {
        renderPage(editor);
        const input = screen.getByRole('textbox', { name: 'Add post-logout redirect URI' });

        fireEvent.change(input, { target: { value: 'https://acme.example.com/bye' } });
        fireEvent.keyDown(input, { key: 'Enter' });
        expect(toast.error).toHaveBeenCalledWith('Error : redirect URI "https://acme.example.com/bye" already exists');

        fireEvent.change(input, { target: { value: 'https://acme.example.com/later' } });
        fireEvent.keyDown(input, { key: 'Enter' });
        expect(screen.getByText('https://acme.example.com/later')).toBeInTheDocument();
    });

    it('removes a request URI only after the confirmation', async () => {
        renderPage(editor);

        fireEvent.click(screen.getByRole('button', { name: 'Remove https://acme.example.com/req.jwt' }));
        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('Are you sure you want to remove this request URI ?');
        expect(screen.getByText('https://acme.example.com/req.jwt')).toBeInTheDocument();

        fireEvent.click(within(dialog).getByRole('button', { name: 'Remove' }));

        await waitFor(() => expect(screen.queryByText('https://acme.example.com/req.jwt')).not.toBeInTheDocument());
        expect(screen.getByText('There are no request URIs.')).toBeInTheDocument();
    });

    it('shows a read-only form without domain_settings_update or domain_settings_delete', async () => {
        renderPage(['domain_settings_read']);

        expect(await screen.findByText('eu | Europe')).toBeInTheDocument();
        expect(screen.getByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('textbox', { name: 'Add request URI' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Remove https://acme.example.com/req.jwt' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('deletes the domain after the confirmation and opens the domains list', async () => {
        vi.mocked(deleteDomain).mockResolvedValueOnce(undefined);
        renderPage([...editor, 'domain_settings_delete']);

        fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
        const dialog = await screen.findByRole('dialog');
        expect(dialog).toHaveTextContent('Are you sure you want to delete this domain ?');
        fireEvent.click(within(dialog).getByRole('button', { name: 'Delete domain' }));

        await waitFor(() => expect(deleteDomain).toHaveBeenCalledWith(ref));
        expect(toast.success).toHaveBeenCalledWith('Domain Acme deleted');
        expect(await screen.findByText('Domains list')).toBeInTheDocument();
    });

    it('shows the data plane only when the environment has more than one', async () => {
        vi.mocked(listDataPlanes).mockResolvedValue([
            { id: 'default', name: 'Default' },
            { id: 'eu', name: 'Europe' },
        ]);
        renderPage(editor);

        expect(await screen.findByRole('textbox', { name: 'Data plane' })).toHaveValue('Default (default)');
    });
});
