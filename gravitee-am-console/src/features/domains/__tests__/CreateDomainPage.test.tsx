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
import { LayoutSlotsProvider, toast, TooltipProvider } from '@gravitee/graphene-core';
import { renderWithGraphene } from '@gravitee/graphene-core/testing';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { listDataPlanes } from '../../../lib/api/data-planes';
import { createDomain } from '../../../lib/api/domains';
import { HttpError } from '../../../lib/api/http';
import { CreateDomainPage } from '../CreateDomainPage';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/data-planes', () => ({ listDataPlanes: vi.fn() }));
vi.mock('../../../lib/api/domains', () => ({ createDomain: vi.fn() }));
vi.mock('../../../lib/session/session', () => ({ useSession: () => ({ user: { org: 'DEFAULT' } }) }));
vi.mock('../../../lib/session/environment', () => ({
    useCurrentEnvironment: () => ({ id: 'env-1', name: 'Development', hrids: ['dev'] }),
}));

function setupCreateDomainPageHarness() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    renderWithGraphene(
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
    const submitButton = () => screen.getByRole('button', { name: 'Create domain' });

    return {
        typeName: async (text: string) => userEvent.type(await screen.findByRole('textbox', { name: 'Name' }), text),
        typeDescription: (text: string) => userEvent.type(screen.getByRole('textbox', { name: 'Description' }), text),
        submitWhenEnabled: async () => {
            await waitFor(() => expect(submitButton()).toBeEnabled());
            await userEvent.click(submitButton());
        },
        submitButton,
        dataPlaneSelect: () => screen.queryByRole('combobox', { name: 'Data plane' }),
    };
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(createDomain).mockResolvedValue({ id: 'd9', hrid: 'customer-identity', name: 'Customer Identity', enabled: false });
});

describe('CreateDomainPage', () => {
    it('creates the domain on the single data plane with the Angular fields, then opens it', async () => {
        vi.mocked(listDataPlanes).mockResolvedValue([{ id: 'default', name: 'Default' }]);
        const harness = setupCreateDomainPageHarness();

        await harness.typeName('Customer Identity');
        await harness.submitWhenEnabled();

        expect(harness.dataPlaneSelect()).not.toBeInTheDocument();
        expect(createDomain).toHaveBeenCalledWith('DEFAULT', 'env-1', {
            name: 'Customer Identity',
            description: undefined,
            dataPlaneId: 'default',
        });
        expect(await screen.findByText('Domain page')).toBeInTheDocument();
        expect(toast.success).toHaveBeenCalledWith('Domain Customer Identity created');
        expect(listDataPlanes).toHaveBeenCalledWith('DEFAULT', 'env-1');
    });

    it('sends the description once typed', async () => {
        vi.mocked(listDataPlanes).mockResolvedValue([{ id: 'default', name: 'Default' }]);
        const harness = setupCreateDomainPageHarness();

        await harness.typeName('Acme');
        await harness.typeDescription('Customer realm');
        await harness.submitWhenEnabled();

        expect(createDomain).toHaveBeenCalledWith('DEFAULT', 'env-1', {
            name: 'Acme',
            description: 'Customer realm',
            dataPlaneId: 'default',
        });
    });

    it('requires a data plane when there are several', async () => {
        vi.mocked(listDataPlanes).mockResolvedValue([
            { id: 'eu', name: 'Europe' },
            { id: 'us', name: 'America' },
        ]);
        const harness = setupCreateDomainPageHarness();

        await harness.typeName('Acme');

        expect(await screen.findByRole('combobox', { name: 'Data plane' })).toBeInTheDocument();
        expect(harness.submitButton()).toBeDisabled();
    });

    it('keeps the form when the creation fails', async () => {
        vi.mocked(listDataPlanes).mockResolvedValue([{ id: 'default', name: 'Default' }]);
        vi.mocked(createDomain).mockRejectedValueOnce(new HttpError(400, 'Domain with name [Acme] already exists'));
        const harness = setupCreateDomainPageHarness();

        await harness.typeName('Acme');
        await harness.submitWhenEnabled();

        await waitFor(() =>
            expect(toast.error).toHaveBeenCalledWith('Could not create the domain. Domain with name [Acme] already exists'),
        );
        expect(await screen.findByRole('button', { name: 'Create domain' })).toBeEnabled();
        expect(screen.queryByText('Domain page')).not.toBeInTheDocument();
    });

    it('says why a domain cannot be created when the environment has no data plane', async () => {
        vi.mocked(listDataPlanes).mockResolvedValue([]);
        const harness = setupCreateDomainPageHarness();

        await harness.typeName('Acme');

        expect(
            await screen.findByText('No data plane is available in this environment, so a domain cannot be created.'),
        ).toBeInTheDocument();
        expect(harness.dataPlaneSelect()).not.toBeInTheDocument();
        expect(harness.submitButton()).toBeDisabled();
    });

    it('shows the error panel when the data planes do not load', async () => {
        vi.mocked(listDataPlanes).mockRejectedValue(new HttpError(500, 'Service unavailable'));
        setupCreateDomainPageHarness();

        expect(await screen.findByText('Could not load the data planes, so a domain cannot be created.')).toBeInTheDocument();
    });
});
