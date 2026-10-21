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
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { createResource, getResourceSchema, listResourcePlugins } from '../../../lib/api/resources';
import { CreateServicePage } from '../CreateServicePage';
import { currentDomain, ref, renderRoutes, resourcesPath } from './render';

vi.mock('../../../lib/api/resources', () => ({
    createResource: vi.fn(),
    getResourceSchema: vi.fn(),
    listResourcePlugins: vi.fn(),
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

function renderPage() {
    return renderRoutes(
        `${resourcesPath}/new`,
        <>
            <Route path={`${resourcesPath}/new`} element={<CreateServicePage />} />
            <Route path={`${resourcesPath}/:resourceId`} element={<p>Detail page</p>} />
        </>,
        [],
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listResourcePlugins).mockResolvedValue([
        { id: 'smtp-am-resource', name: 'SMTP Resource' },
        { id: 'http-factor-am-resource', name: 'HTTP Factor Resource' },
        { id: 'twilio-verify-am-resource', name: 'Twilio Resource', feature: 'am-resource-twilio' },
    ]);
    vi.mocked(getResourceSchema).mockResolvedValue({
        type: 'object',
        properties: {
            host: { type: 'string', title: 'Hostname' },
            authentication: { type: 'boolean', title: 'Authentication', default: false },
        },
    });
    vi.mocked(createResource).mockResolvedValue({ id: 'r-9', name: 'Mail server', type: 'smtp-am-resource', configuration: '{}' });
});

describe('CreateServicePage', () => {
    it('names the plugins as Angular does and locks the ones the licence lacks', async () => {
        renderPage();

        expect(await screen.findByRole('radio', { name: 'SMTP' })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'Twilio Verify, Enterprise' })).toBeInTheDocument();
    });

    it('creates the resource with the schema defaults in the configuration, then opens it', async () => {
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'SMTP' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Mail server' } });
        fireEvent.change(screen.getByRole('textbox', { name: /Hostname/ }), { target: { value: 'smtp.acme.io' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() =>
            expect(createResource).toHaveBeenCalledWith(ref, {
                name: 'Mail server',
                type: 'smtp-am-resource',
                configuration: expect.any(String),
            }),
        );
        expect(JSON.parse(vi.mocked(createResource).mock.calls[0][1].configuration)).toEqual({
            host: 'smtp.acme.io',
            authentication: false,
        });
        expect(getResourceSchema).toHaveBeenCalledWith('smtp-am-resource');
        expect(await screen.findByText('Detail page')).toBeInTheDocument();
    });

    it('shows the HTTP Factor notes for an HTTP Factor resource', async () => {
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'HTTP Factor' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));

        expect(await screen.findByText('HTTP Factor Resource')).toBeInTheDocument();
    });

    it('sends the default of a field its condition hides', async () => {
        vi.mocked(getResourceSchema).mockResolvedValue({
            type: 'object',
            properties: {
                host: { type: 'string', title: 'Hostname' },
                authentication: { type: 'boolean', title: 'Authentication', default: false },
                authenticationType: {
                    type: 'string',
                    title: 'Type',
                    default: 'basic',
                    enum: ['basic', 'oauth2'],
                    condition: 'model.authentication',
                },
            },
        });
        renderPage();

        fireEvent.click(await screen.findByRole('radio', { name: 'SMTP' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Mail server' } });
        fireEvent.change(screen.getByRole('textbox', { name: /Hostname/ }), { target: { value: 'smtp.acme.io' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() => expect(createResource).toHaveBeenCalled());
        expect(JSON.parse(vi.mocked(createResource).mock.calls[0][1].configuration)).toEqual({
            host: 'smtp.acme.io',
            authentication: false,
            authenticationType: 'basic',
        });
    });
});
