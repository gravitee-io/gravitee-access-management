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
import { MemoryRouter } from 'react-router-dom';
import { LicenseScope } from '../../../lib/session/license';
import { PluginWizard } from '../PluginWizard';

const onCreate = vi.fn();
const getSchema = vi.fn();

function renderWizard() {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <LicenseScope license={{ tier: 'oss', features: [] }}>
                <MemoryRouter>
                    <LayoutSlotsProvider>
                        <TooltipProvider>
                            <PluginWizard
                                title="Create a service"
                                back={{ to: '/list', label: 'Services' }}
                                pickDescription="Pick"
                                configureDescription="Configure"
                                noun="services"
                                plugins={[{ id: 'smtp', name: 'SMTP' }]}
                                pluginsError={null}
                                schemaKey="wizard-test-schema"
                                getSchema={getSchema}
                                creating={false}
                                onCreate={onCreate}
                            />
                        </TooltipProvider>
                    </LayoutSlotsProvider>
                </MemoryRouter>
            </LicenseScope>
        </QueryClientProvider>,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    getSchema.mockResolvedValue({
        type: 'object',
        properties: {
            host: { type: 'string', title: 'Hostname' },
            port: { type: 'integer', title: 'Port', default: 587 },
        },
        required: ['host', 'port'],
    });
});

describe('PluginWizard', () => {
    it('fills the schema defaults, so a required field with a default needs no input', async () => {
        renderWizard();

        fireEvent.click(await screen.findByRole('radio', { name: 'SMTP' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Mail' } });
        expect(screen.getByRole('spinbutton', { name: /Port/ })).toHaveValue(587);
        fireEvent.change(screen.getByRole('textbox', { name: /Hostname/ }), { target: { value: 'smtp.acme.io' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() =>
            expect(onCreate).toHaveBeenCalledWith(
                { id: 'smtp', name: 'SMTP' },
                { name: 'Mail', config: { host: 'smtp.acme.io', port: 587 } },
            ),
        );
    });

    it('shows a field again when its unset-or-equals condition holds after a toggle', async () => {
        getSchema.mockResolvedValue({
            type: 'object',
            properties: {
                authentication: { type: 'boolean', title: 'Authentication', default: false },
                authenticationType: {
                    type: 'string',
                    title: 'Type',
                    default: 'basic',
                    enum: ['basic', 'oauth2'],
                    condition: 'model.authentication',
                },
                username: {
                    type: 'string',
                    title: 'Username',
                    condition: {
                        functionBody: "return model.authentication && (!model.authenticationType || model.authenticationType === 'basic');",
                    },
                },
            },
        });
        renderWizard();

        fireEvent.click(await screen.findByRole('radio', { name: 'SMTP' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        fireEvent.click(await screen.findByRole('switch', { name: /Authentication/ }));
        expect(await screen.findByRole('textbox', { name: 'Username' })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('switch', { name: /Authentication/ }));
        await waitFor(() => expect(screen.queryByRole('textbox', { name: 'Username' })).not.toBeInTheDocument());
        fireEvent.click(screen.getByRole('switch', { name: /Authentication/ }));

        expect(await screen.findByRole('textbox', { name: 'Username' })).toBeInTheDocument();
    });
});
