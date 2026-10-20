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
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { createApplication, createApplicationFromCimd, validateCimd } from '../../../lib/api/applications';
import type { Domain } from '../../../lib/api/domains';
import { HttpError } from '../../../lib/api/http';
import { CreateApplicationPage } from '../CreateApplicationPage';

const session = vi.hoisted(() => ({ domain: {} as Domain }));
const ref = { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' };

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/applications', () => ({
    createApplication: vi.fn(),
    createApplicationFromCimd: vi.fn(),
    validateCimd: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({
    useCurrentDomain: () => ({
        domain: session.domain,
        ref: { organizationId: 'DEFAULT', environmentId: 'env-1', domainId: 'd1' },
        basePath: '/environments/dev/domains/acme',
        domainsPath: '/environments/dev/domains',
    }),
}));

const basePath = '/environments/dev/domains/acme';

function renderPage(domain: Partial<Domain> = {}) {
    session.domain = { id: 'd1', hrid: 'acme', name: 'Acme', enabled: true, ...domain };
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <MemoryRouter initialEntries={[`${basePath}/applications/new`]}>
                <LayoutSlotsProvider>
                    <TooltipProvider>
                        <Routes>
                            <Route path={`${basePath}/applications/new`} element={<CreateApplicationPage />} />
                            <Route path={`${basePath}/applications/:applicationId`} element={<p>Application detail</p>} />
                        </Routes>
                    </TooltipProvider>
                </LayoutSlotsProvider>
            </MemoryRouter>
        </QueryClientProvider>,
    );
}

function pickType(name: string) {
    fireEvent.click(screen.getByRole('radio', { name: new RegExp(`^${name}`) }));
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
}

beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: vi.fn().mockResolvedValue(undefined) }, configurable: true });
});

describe('CreateApplicationPage', () => {
    it('offers the resource server type only when UMA is enabled on the domain', () => {
        const { unmount } = renderPage();
        expect(screen.queryByRole('radio', { name: /^Resource Server/ })).not.toBeInTheDocument();
        expect(screen.getByRole('radio', { name: /^Web/ })).toBeInTheDocument();
        unmount();

        renderPage({ uma: { enabled: true } });
        expect(screen.getByRole('radio', { name: /^Resource Server/ })).toBeInTheDocument();
    });

    it('creates a web application with the Angular payload, then shows the secret until it is copied', async () => {
        vi.mocked(createApplication).mockResolvedValueOnce({
            id: 'a1',
            name: 'Shop',
            type: 'web',
            enabled: true,
            settings: { oauth: { clientSecret: 's3cret' } },
        });
        renderPage();

        pickType('Web');
        const create = screen.getByRole('button', { name: 'Create application' });
        expect(create).toBeDisabled();
        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Shop' } });
        expect(create).toBeDisabled();
        fireEvent.change(screen.getByRole('textbox', { name: 'Redirect URI' }), { target: { value: 'https://shop.example.com/cb' } });
        fireEvent.click(create);

        await waitFor(() =>
            expect(createApplication).toHaveBeenCalledWith(ref, {
                name: 'Shop',
                type: 'WEB',
                description: undefined,
                clientId: undefined,
                clientSecret: undefined,
                redirectUris: ['https://shop.example.com/cb'],
            }),
        );
        expect(await screen.findByRole('textbox', { name: 'Client secret' })).toHaveValue('s3cret');
        const done = screen.getByRole('button', { name: 'I have copied the secret' });
        expect(done).toBeDisabled();

        fireEvent.click(screen.getByRole('button', { name: 'Copy to clipboard' }));
        await waitFor(() => expect(done).toBeEnabled());
        expect(navigator.clipboard.writeText).toHaveBeenCalledWith('s3cret');
        expect(toast.success).toHaveBeenCalledWith('Client Secret copied to the clipboard');

        fireEvent.click(done);
        expect(toast.success).toHaveBeenCalledWith('Application Shop created');
        expect(await screen.findByText('Application detail')).toBeInTheDocument();
    });

    it('creates a backend application without a redirect URI, even after one was typed for another type', async () => {
        vi.mocked(createApplication).mockResolvedValueOnce({ id: 'a2', name: 'Batch', type: 'service', enabled: true });
        renderPage();

        pickType('Web');
        fireEvent.change(screen.getByRole('textbox', { name: 'Redirect URI' }), { target: { value: 'https://web.example.com/cb' } });
        fireEvent.click(screen.getByRole('button', { name: 'Back' }));
        pickType('Backend to Backend');
        expect(screen.queryByRole('textbox', { name: 'Redirect URI' })).not.toBeInTheDocument();
        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Batch' } });
        fireEvent.change(screen.getByRole('textbox', { name: 'Client ID' }), { target: { value: 'batch-client' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create application' }));

        await waitFor(() =>
            expect(createApplication).toHaveBeenCalledWith(ref, {
                name: 'Batch',
                type: 'SERVICE',
                description: undefined,
                clientId: 'batch-client',
                clientSecret: undefined,
                redirectUris: null,
            }),
        );
    });

    it('hides the import choice when CIMD is off for the domain', () => {
        renderPage();

        pickType('Web');
        expect(screen.queryByRole('radio', { name: /CIMD/ })).not.toBeInTheDocument();
    });

    it('validates a CIMD URL, asks for the missing client name, and creates from the document', async () => {
        const cimdUrl = 'https://agent.example.com/client.json';
        vi.mocked(validateCimd).mockResolvedValueOnce({
            url: cimdUrl,
            metadata: { redirect_uris: ['https://agent.example.com/cb'], grant_types: ['authorization_code'] },
            missing: { clientName: true },
        });
        vi.mocked(createApplicationFromCimd).mockResolvedValueOnce({ id: 'a3', name: 'Agent', type: 'web', enabled: true });
        renderPage({ oidc: { cimdSettings: { enabled: true } } });

        pickType('Web');
        fireEvent.click(screen.getByRole('radio', { name: /CIMD/ }));
        fireEvent.change(screen.getByLabelText('Document URL'), { target: { value: cimdUrl } });
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));

        await waitFor(() => expect(validateCimd).toHaveBeenCalledWith(ref, cimdUrl));
        expect(await screen.findByText('https://agent.example.com/cb')).toBeInTheDocument();
        expect(screen.getByText('none')).toBeInTheDocument();
        const confirm = screen.getByRole('button', { name: 'Confirm and create' });
        expect(confirm).toBeDisabled();

        fireEvent.change(screen.getByRole('textbox', { name: 'Application name' }), { target: { value: 'Agent' } });
        fireEvent.click(confirm);

        await waitFor(() =>
            expect(createApplicationFromCimd).toHaveBeenCalledWith(ref, {
                name: 'Agent',
                type: 'WEB',
                description: undefined,
                cimdUrl,
                clientName: 'Agent',
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('Application Agent created');
        expect(await screen.findByText('Application detail')).toBeInTheDocument();
    });

    it('shows the server message when the CIMD URL does not validate', async () => {
        vi.mocked(validateCimd).mockRejectedValueOnce(new HttpError(400, 'Document is not trusted'));
        renderPage({ oidc: { cimdSettings: { enabled: true } } });

        pickType('Web');
        fireEvent.click(screen.getByRole('radio', { name: /CIMD/ }));
        fireEvent.change(screen.getByLabelText('Document URL'), { target: { value: 'https://bad.example.com' } });
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));

        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Document is not trusted'));
        expect(screen.queryByRole('button', { name: 'Confirm and create' })).not.toBeInTheDocument();
    });
});
