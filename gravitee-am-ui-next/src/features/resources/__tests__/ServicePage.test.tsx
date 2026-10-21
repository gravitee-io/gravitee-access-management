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
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { HttpError } from '../../../lib/api/http';
import { deleteResource, getResource, getResourceSchema, listResourcePlugins, updateResource } from '../../../lib/api/resources';
import { ServicePage } from '../ServicePage';
import { currentDomain, ref, renderRoutes, resourcesPath } from './render';

vi.mock('../../../lib/api/resources', () => ({
    deleteResource: vi.fn(),
    getResource: vi.fn(),
    getResourceSchema: vi.fn(),
    listResourcePlugins: vi.fn(),
    updateResource: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_resource_update', 'domain_resource_delete'];
const resource = {
    id: 'r-1',
    name: 'Mail server',
    type: 'smtp-am-resource',
    configuration: JSON.stringify({ host: 'smtp.acme.io' }),
};

function renderPage(permissions = ALL, features: string[] = []) {
    return renderRoutes(
        `${resourcesPath}/r-1`,
        <>
            <Route path={`${resourcesPath}/:resourceId`} element={<ServicePage />} />
            <Route path={resourcesPath} element={<p>Service list</p>} />
        </>,
        permissions,
        features,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getResource).mockResolvedValue(resource);
    vi.mocked(listResourcePlugins).mockResolvedValue([
        { id: 'smtp-am-resource', name: 'SMTP Resource' },
        { id: 'twilio-verify-am-resource', name: 'Twilio Resource', feature: 'am-resource-twilio' },
        { id: 'http-factor-am-resource', name: 'HTTP Factor Resource' },
    ]);
    vi.mocked(getResourceSchema).mockResolvedValue({
        type: 'object',
        properties: {
            host: { type: 'string', title: 'Hostname' },
            authentication: { type: 'boolean', title: 'Authentication', default: false },
        },
    });
});

describe('ServicePage', () => {
    it('saves the name, type and serialized configuration with the schema defaults, as Angular does', async () => {
        vi.mocked(updateResource).mockImplementation(async (_ref, _id, body) => ({ ...resource, ...body }));
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Mailer' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateResource).toHaveBeenCalledWith(ref, 'r-1', {
                name: 'Mailer',
                type: 'smtp-am-resource',
                configuration: expect.any(String),
            }),
        );
        expect(JSON.parse(vi.mocked(updateResource).mock.calls[0][2].configuration)).toEqual({
            host: 'smtp.acme.io',
            authentication: false,
        });
        expect(getResource).toHaveBeenCalledWith(ref, 'r-1');
        await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled());
    });

    it('hides the credentials until authentication is on, and sends a hidden stored value back', async () => {
        vi.mocked(getResource).mockResolvedValue({ ...resource, configuration: JSON.stringify({ host: 'smtp', password: '********' }) });
        vi.mocked(getResourceSchema).mockResolvedValue({
            type: 'object',
            properties: {
                host: { type: 'string', title: 'Hostname' },
                authentication: { type: 'boolean', title: 'Authentication', default: false },
                password: { type: 'string', title: 'Password', widget: 'password', condition: 'model.authentication' },
            },
        });
        vi.mocked(updateResource).mockImplementation(async (_ref, _id, body) => ({ ...resource, ...body }));
        renderPage();

        expect(await screen.findByRole('textbox', { name: /Hostname/ })).toHaveValue('smtp');
        expect(screen.queryByLabelText(/Password/)).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();

        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Mailer' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(updateResource).toHaveBeenCalled());
        expect(JSON.parse(vi.mocked(updateResource).mock.calls[0][2].configuration)).toEqual({
            host: 'smtp',
            authentication: false,
            password: '********',
        });
    });

    it('is clean again after an edit goes back to the stored value, when an optional field has no stored value', async () => {
        vi.mocked(getResourceSchema).mockResolvedValue({
            type: 'object',
            properties: {
                host: { type: 'string', title: 'Hostname' },
                sslTrust: { type: 'string', title: 'SSL Trust' },
            },
        });
        renderPage();

        const host = await screen.findByRole('textbox', { name: /Hostname/ });
        fireEvent.change(host, { target: { value: 'smtp.acme.com' } });
        await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled());
        fireEvent.change(host, { target: { value: 'smtp.acme.io' } });

        await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled());
    });

    it('requires a name', async () => {
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: ' ' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByText('Name is required')).toBeInTheDocument();
        expect(updateResource).not.toHaveBeenCalled();
    });

    it('deletes the resource after the confirm dialog, then opens the list', async () => {
        vi.mocked(deleteResource).mockResolvedValueOnce();
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
        fireEvent.click(within(await screen.findByRole('dialog', { name: 'Delete service' })).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(deleteResource).toHaveBeenCalledWith(ref, 'r-1'));
        expect(await screen.findByText('Service list')).toBeInTheDocument();
    });

    it('hides save and delete without the permissions', async () => {
        renderPage([]);

        expect(await screen.findByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('opens the upgrade dialog, and does not save, when the licence lacks the plugin feature', async () => {
        vi.mocked(getResource).mockResolvedValue({ ...resource, type: 'twilio-verify-am-resource' });
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Twilio' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByRole('dialog', { name: 'Enterprise feature' })).toBeInTheDocument();
        expect(updateResource).not.toHaveBeenCalled();
    });

    it('shows the HTTP Factor notes for an HTTP Factor resource', async () => {
        vi.mocked(getResource).mockResolvedValue({ ...resource, type: 'http-factor-am-resource' });
        renderPage();

        expect(await screen.findByText('HTTP Factor Resource')).toBeInTheDocument();
    });

    it('shows the not-found panel for a deleted resource', async () => {
        vi.mocked(getResource).mockRejectedValueOnce(new HttpError(404, 'Resource not found'));
        renderPage();

        expect(await screen.findByText('Service not found')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Back to services' })).toHaveAttribute('href', resourcesPath);
    });

    it('keeps a stored credential that is hidden and shown again', async () => {
        vi.mocked(getResource).mockResolvedValue({
            ...resource,
            configuration: JSON.stringify({ host: 'smtp', authentication: true, username: 'bob' }),
        });
        vi.mocked(getResourceSchema).mockResolvedValue({
            type: 'object',
            properties: {
                host: { type: 'string', title: 'Hostname' },
                authentication: { type: 'boolean', title: 'Authentication', default: false },
                username: { type: 'string', title: 'Username', condition: 'model.authentication' },
            },
        });
        vi.mocked(updateResource).mockImplementation(async (_ref, _id, body) => ({ ...resource, ...body }));
        renderPage();

        expect(await screen.findByRole('textbox', { name: 'Username' })).toHaveValue('bob');
        fireEvent.click(screen.getByRole('switch', { name: /Authentication/ }));
        await waitFor(() => expect(screen.queryByRole('textbox', { name: 'Username' })).not.toBeInTheDocument());
        fireEvent.click(screen.getByRole('switch', { name: /Authentication/ }));
        await screen.findByRole('textbox', { name: 'Username' });
        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Mailer' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(updateResource).toHaveBeenCalled());
        expect(JSON.parse(vi.mocked(updateResource).mock.calls[0][2].configuration)).toEqual({
            host: 'smtp',
            authentication: true,
            username: 'bob',
        });
    });

    it('brings back a value hidden and shown again on Discard', async () => {
        vi.mocked(getResource).mockResolvedValue({
            ...resource,
            configuration: JSON.stringify({ host: 'smtp', authentication: true, username: 'bob' }),
        });
        vi.mocked(getResourceSchema).mockResolvedValue({
            type: 'object',
            properties: {
                host: { type: 'string', title: 'Hostname' },
                authentication: { type: 'boolean', title: 'Authentication', default: false },
                username: { type: 'string', title: 'Username', condition: 'model.authentication' },
            },
        });
        renderPage();

        await screen.findByRole('textbox', { name: 'Username' });
        fireEvent.click(screen.getByRole('switch', { name: /Authentication/ }));
        await waitFor(() => expect(screen.queryByRole('textbox', { name: 'Username' })).not.toBeInTheDocument());
        fireEvent.click(screen.getByRole('button', { name: 'Discard changes' }));

        expect(await screen.findByRole('textbox', { name: 'Username' })).toHaveValue('bob');
    });

    it('shows the name, Save and Delete for a resource whose plugin has no schema', async () => {
        vi.mocked(getResourceSchema).mockResolvedValue({});
        renderPage();

        expect(await screen.findByRole('textbox', { name: 'Name' })).toHaveValue('Mail server');
        expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Delete' })).toBeInTheDocument();
    });
});
