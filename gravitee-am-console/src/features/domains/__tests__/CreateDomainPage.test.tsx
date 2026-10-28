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
import { LayoutSlotsProvider, TooltipProvider } from '@gravitee/graphene-core';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { listDataPlanes } from '../../../lib/api/data-planes';
import { createDomain } from '../../../lib/api/domains';
import { HttpError } from '../../../lib/api/http';
import { CreateDomainPage } from '../CreateDomainPage';

vi.mock('../../../lib/api/data-planes', () => ({ listDataPlanes: vi.fn() }));
vi.mock('../../../lib/api/domains', () => ({ createDomain: vi.fn() }));
vi.mock('../../../lib/session/session', () => ({ useSession: () => ({ user: { org: 'DEFAULT' } }) }));
vi.mock('../../../lib/session/environment', () => ({
    useCurrentEnvironment: () => ({ id: 'env-1', name: 'Development', hrids: ['dev'] }),
}));

function renderPage() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <MemoryRouter initialEntries={['/environments/dev/domains/new']}>
                <LayoutSlotsProvider>
                    <TooltipProvider>
                        <Routes>
                            <Route path="/environments/dev/domains/new" element={<CreateDomainPage />} />
                            <Route path="/environments/dev/domains/:hrid" element={<p>Domain page</p>} />
                        </Routes>
                    </TooltipProvider>
                </LayoutSlotsProvider>
            </MemoryRouter>
        </QueryClientProvider>,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(createDomain).mockResolvedValue({ id: 'd9', hrid: 'customer-identity', name: 'Customer Identity', enabled: false });
});

describe('CreateDomainPage', () => {
    it('creates the domain on the single data plane with the Angular fields, then opens it', async () => {
        vi.mocked(listDataPlanes).mockResolvedValue([{ id: 'default', name: 'Default' }]);
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Customer Identity' } });
        await waitFor(() => expect(screen.getByRole('button', { name: 'Create domain' })).toBeEnabled());
        expect(screen.queryByText('Data plane')).not.toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Create domain' }));

        await waitFor(() =>
            expect(createDomain).toHaveBeenCalledWith('DEFAULT', 'env-1', {
                name: 'Customer Identity',
                description: undefined,
                dataPlaneId: 'default',
            }),
        );
        expect(await screen.findByText('Domain page')).toBeInTheDocument();
        expect(listDataPlanes).toHaveBeenCalledWith('DEFAULT', 'env-1');
    });

    it('sends the description once typed', async () => {
        vi.mocked(listDataPlanes).mockResolvedValue([{ id: 'default', name: 'Default' }]);
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Acme' } });
        fireEvent.change(screen.getByRole('textbox', { name: 'Description' }), { target: { value: 'Customer realm' } });
        await waitFor(() => expect(screen.getByRole('button', { name: 'Create domain' })).toBeEnabled());
        fireEvent.click(screen.getByRole('button', { name: 'Create domain' }));

        await waitFor(() =>
            expect(createDomain).toHaveBeenCalledWith('DEFAULT', 'env-1', {
                name: 'Acme',
                description: 'Customer realm',
                dataPlaneId: 'default',
            }),
        );
    });

    it('requires a data plane when there are several', async () => {
        vi.mocked(listDataPlanes).mockResolvedValue([
            { id: 'eu', name: 'Europe' },
            { id: 'us', name: 'America' },
        ]);
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Acme' } });

        expect(await screen.findByRole('combobox', { name: 'Data plane' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Create domain' })).toBeDisabled();
    });

    it('keeps the form when the creation fails', async () => {
        vi.mocked(listDataPlanes).mockResolvedValue([{ id: 'default', name: 'Default' }]);
        vi.mocked(createDomain).mockRejectedValueOnce(new HttpError(400, 'Domain with name [Acme] already exists'));
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Acme' } });
        await waitFor(() => expect(screen.getByRole('button', { name: 'Create domain' })).toBeEnabled());
        fireEvent.click(screen.getByRole('button', { name: 'Create domain' }));

        await waitFor(() => expect(createDomain).toHaveBeenCalled());
        expect(await screen.findByRole('button', { name: 'Create domain' })).toBeEnabled();
        expect(screen.queryByText('Domain page')).not.toBeInTheDocument();
    });

    it('shows the error panel when the data planes do not load', async () => {
        vi.mocked(listDataPlanes).mockRejectedValue(new HttpError(500, 'Service unavailable'));
        renderPage();

        expect(await screen.findByText('Could not load the data planes, so a domain cannot be created.')).toBeInTheDocument();
    });
});
