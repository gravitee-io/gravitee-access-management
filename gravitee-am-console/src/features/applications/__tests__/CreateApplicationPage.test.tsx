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
import { toast } from '@gravitee/graphene-core';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route } from 'react-router-dom';
import { createApplication, createCimdApplication, validateCimd } from '../../../lib/api/applications';
import type { Domain } from '../../../lib/api/domains';
import { HttpError } from '../../../lib/api/http';
import { CreateApplicationPage } from '../CreateApplicationPage';
import { applicationsPath, currentDomain, ref, renderRoutes } from './render';

const session = vi.hoisted(() => ({ domain: {} as Domain }));

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/applications', () => ({
    createApplication: vi.fn(),
    createCimdApplication: vi.fn(),
    validateCimd: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => ({ ...currentDomain, domain: session.domain }) }));

function renderPage(domain: Partial<Domain> = {}) {
    session.domain = { ...currentDomain.domain, ...domain };
    return renderRoutes(
        `${applicationsPath}/new`,
        <>
            <Route path={`${applicationsPath}/new`} element={<CreateApplicationPage />} />
            <Route path={`${applicationsPath}/:appId`} element={<p>Application detail</p>} />
        </>,
        ['application_create'],
    );
}

function pickType(name: string) {
    fireEvent.click(screen.getByRole('radio', { name: new RegExp(`^${name}`) }));
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
}

async function openCimdReview(cimdUrl: string) {
    pickType('Web');
    fireEvent.click(screen.getByRole('radio', { name: /CIMD/ }));
    fireEvent.change(screen.getByLabelText('Document URL'), { target: { value: cimdUrl } });
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
    await waitFor(() => expect(validateCimd).toHaveBeenCalledWith(ref, cimdUrl));
}

beforeEach(() => {
    vi.clearAllMocks();
    Object.defineProperty(navigator, 'clipboard', { value: { writeText: vi.fn().mockResolvedValue(undefined) }, configurable: true });
});

describe('CreateApplicationPage', () => {
    it('offers the resource server type only when UMA is on, and never the agent type', () => {
        const { unmount } = renderPage();
        expect(screen.getAllByRole('radio')).toHaveLength(4);
        expect(screen.queryByRole('radio', { name: /^Resource Server/ })).not.toBeInTheDocument();
        expect(screen.queryByRole('radio', { name: /^Agent/ })).not.toBeInTheDocument();
        unmount();

        renderPage({ uma: { enabled: true } });
        expect(screen.getByRole('radio', { name: /^Resource Server/ })).toBeInTheDocument();
    });

    it('moves the type choice with the arrow keys, from one tab stop', async () => {
        renderPage();

        await userEvent.tab();
        await userEvent.tab();
        expect(screen.getByRole('radio', { name: /^Web/ })).toHaveFocus();
        await userEvent.keyboard('{ArrowDown}');
        expect(screen.getByRole('radio', { name: /^Single-Page App/ })).toHaveFocus();
        await userEvent.keyboard(' ');

        expect(screen.getByRole('radio', { name: /^Single-Page App/ })).toBeChecked();
        await userEvent.tab();
        expect(document.activeElement).not.toHaveAttribute('role', 'radio');
    });

    it('shows the required field errors once a field loses focus', async () => {
        renderPage();
        pickType('Web');

        expect(screen.queryByText('Name is required')).not.toBeInTheDocument();
        await userEvent.click(screen.getByRole('textbox', { name: 'Name' }));
        await userEvent.click(screen.getByRole('textbox', { name: 'Redirect URI' }));
        expect(screen.getByText('Name is required')).toBeInTheDocument();
        expect(screen.getByRole('textbox', { name: 'Name' })).toHaveAttribute('aria-invalid', 'true');
        expect(screen.queryByText('Redirect URI is required')).not.toBeInTheDocument();

        await userEvent.tab();
        expect(screen.getByText('Redirect URI is required')).toBeInTheDocument();
    });

    it('creates a web application with the fields as typed, then shows the secret until it is copied', async () => {
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
        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Shop ' } });
        expect(create).toBeDisabled();
        fireEvent.change(screen.getByRole('textbox', { name: 'Redirect URI' }), { target: { value: 'https://shop.example.com/cb' } });
        fireEvent.click(create);

        await waitFor(() =>
            expect(createApplication).toHaveBeenCalledWith(ref, {
                name: 'Shop ',
                type: 'WEB',
                description: undefined,
                clientId: undefined,
                clientSecret: undefined,
                redirectUris: ['https://shop.example.com/cb'],
            }),
        );
        const dialog = await screen.findByRole('dialog', { name: 'Copy your client secret' });
        expect(within(dialog).getByRole('textbox', { name: 'Client secret' })).toHaveValue('s3cret');
        expect(within(dialog).queryByRole('button', { name: 'Close' })).not.toBeInTheDocument();
        const done = within(dialog).getByRole('button', { name: 'I have copied the secret' });
        expect(done).toBeDisabled();

        fireEvent.click(within(dialog).getByRole('button', { name: 'Copy to clipboard' }));
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

        await waitFor(() => expect(createApplication).toHaveBeenCalledOnce());
        const payload = vi.mocked(createApplication).mock.calls[0]?.[1];
        expect(payload).toEqual({ name: 'Batch', type: 'SERVICE', clientId: 'batch-client' });
        expect(payload).not.toHaveProperty('redirectUris');
    });

    it('reports a failed create with the server message', async () => {
        vi.mocked(createApplication).mockRejectedValueOnce(new HttpError(400, 'Client ID already exists'));
        renderPage();

        pickType('Backend to Backend');
        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Batch' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create application' }));

        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Client ID already exists'));
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
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
            metadata: {
                redirect_uris: ['https://agent.example.com/cb'],
                grant_types: ['authorization_code'],
                contacts: ['ops@agent.example.com'],
            },
            missing: { clientName: true },
        });
        vi.mocked(createCimdApplication).mockResolvedValueOnce({ id: 'a3', name: 'Agent', type: 'web', enabled: true });
        renderPage({ oidc: { cimdSettings: { enabled: true } } });

        await openCimdReview(cimdUrl);
        expect(await screen.findByText('https://agent.example.com/cb')).toBeInTheDocument();
        expect(screen.getByText('authorization_code')).toBeInTheDocument();
        expect(screen.getByText('ops@agent.example.com')).toBeInTheDocument();
        expect(screen.getByText('none')).toBeInTheDocument();
        const confirm = screen.getByRole('button', { name: 'Confirm and create' });
        expect(confirm).toBeDisabled();

        fireEvent.change(screen.getByRole('textbox', { name: 'Application name' }), { target: { value: 'Agent' } });
        fireEvent.click(confirm);

        await waitFor(() =>
            expect(createCimdApplication).toHaveBeenCalledWith(ref, {
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

    it('takes the name from the document and sends no client name when the document has one', async () => {
        const cimdUrl = 'https://shop.example.com/client.json';
        vi.mocked(validateCimd).mockResolvedValueOnce({ url: cimdUrl, metadata: { client_name: 'Shop' } });
        vi.mocked(createCimdApplication).mockResolvedValueOnce({ id: 'a4', name: 'Shop', type: 'web', enabled: true });
        renderPage({ oidc: { cimdSettings: { enabled: true } } });

        await openCimdReview(cimdUrl);
        fireEvent.click(await screen.findByRole('button', { name: 'Confirm and create' }));

        await waitFor(() =>
            expect(createCimdApplication).toHaveBeenCalledWith(ref, {
                name: 'Shop',
                type: 'WEB',
                description: undefined,
                cimdUrl,
                clientName: undefined,
            }),
        );
    });

    it('shows the metadata document and copies it as JSON', async () => {
        const cimdUrl = 'https://shop.example.com/client.json';
        const metadata = { client_name: 'Shop', scope: 'openid profile' };
        vi.mocked(validateCimd).mockResolvedValueOnce({ url: cimdUrl, metadata });
        renderPage({ oidc: { cimdSettings: { enabled: true } } });

        await openCimdReview(cimdUrl);
        fireEvent.click(await screen.findByRole('button', { name: 'View metadata JSON' }));
        const dialog = screen.getByRole('dialog', { name: 'Metadata document' });
        expect(within(dialog).getByRole('link', { name: cimdUrl })).toHaveAttribute('href', cimdUrl);
        fireEvent.click(within(dialog).getByRole('button', { name: 'Copy JSON' }));

        await waitFor(() => expect(navigator.clipboard.writeText).toHaveBeenCalledWith(JSON.stringify(metadata, null, 2)));
        expect(await within(dialog).findByRole('button', { name: 'Copied!' })).toBeInTheDocument();
    });

    it('shows the server message when the CIMD URL does not validate', async () => {
        vi.mocked(validateCimd).mockRejectedValueOnce(new HttpError(400, 'Document is not trusted'));
        renderPage({ oidc: { cimdSettings: { enabled: true } } });

        await openCimdReview('https://bad.example.com');

        await waitFor(() => expect(toast.error).toHaveBeenCalledWith('Document is not trusted'));
        expect(screen.queryByRole('button', { name: 'Confirm and create' })).not.toBeInTheDocument();
    });
});
