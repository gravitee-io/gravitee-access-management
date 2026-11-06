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
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { LicenseScope } from '../../../lib/session/license';
import { PluginWizard, type WizardPlugin } from '../PluginWizard';

const onCreate = vi.fn();
const getSchema = vi.fn();

const SMTP: WizardPlugin = { id: 'smtp', name: 'SMTP', labels: ['mail'] };
const HTTP: WizardPlugin = { id: 'http', name: 'HTTP' };
const KAFKA: WizardPlugin = { id: 'kafka', name: 'Kafka', feature: 'am-reporter-kafka' };

function BrowserBack() {
    const navigate = useNavigate();
    return <button onClick={() => navigate(-1)}>Browser back</button>;
}

function renderWizard(plugins: WizardPlugin[] = [SMTP]) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    return render(
        <QueryClientProvider client={client}>
            <LicenseScope license={{ features: [] }}>
                <MemoryRouter>
                    <LayoutSlotsProvider>
                        <TooltipProvider>
                            <PluginWizard
                                title="Create a service"
                                back={{ to: '/list', label: 'Services' }}
                                pickDescription="Pick"
                                configureDescription="Configure"
                                noun="services"
                                plugins={plugins}
                                pluginsError={null}
                                schemaKey="wizard-test-schema"
                                getSchema={getSchema}
                                creating={false}
                                onCreate={onCreate}
                            />
                            <BrowserBack />
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
        await waitFor(() => expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled());
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() => expect(onCreate).toHaveBeenCalledWith(SMTP, { name: 'Mail', config: { host: 'smtp.acme.io', port: 587 } }));
        expect(getSchema).toHaveBeenCalledWith(SMTP);
    });

    it('disables Create until the name and the required fields are filled', async () => {
        renderWizard();

        fireEvent.click(await screen.findByRole('radio', { name: 'SMTP' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        await waitFor(() => expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled());
        fireEvent.change(screen.getByRole('textbox', { name: /Hostname/ }), { target: { value: 'smtp.acme.io' } });
        await waitFor(() => expect(screen.getByRole('button', { name: 'Create' })).toBeDisabled());
        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Mail' } });

        await waitFor(() => expect(screen.getByRole('button', { name: 'Create' })).toBeEnabled());
    });

    it('filters the plugins on the name and the labels, without case', async () => {
        renderWizard([SMTP, HTTP]);

        fireEvent.change(await screen.findByRole('searchbox', { name: 'Filter services' }), { target: { value: 'MAIL' } });

        expect(screen.getByRole('radio', { name: 'SMTP' })).toBeInTheDocument();
        expect(screen.queryByRole('radio', { name: 'HTTP' })).not.toBeInTheDocument();
        fireEvent.change(screen.getByRole('searchbox', { name: 'Filter services' }), { target: { value: 'ldap' } });
        expect(screen.getByText('No services match the filter.')).toBeInTheDocument();
    });

    it('locks a plugin that the licence lacks, and a click opens the upgrade dialog', async () => {
        renderWizard([KAFKA]);

        fireEvent.click(await screen.findByRole('radio', { name: 'Kafka, Enterprise' }));

        expect(await screen.findByRole('dialog', { name: 'Enterprise feature' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Next', hidden: true })).toBeDisabled();
    });

    it('moves the selection with the arrow keys, and skips the selection of a locked plugin', async () => {
        renderWizard([SMTP, HTTP, KAFKA]);

        const smtp = await screen.findByRole('radio', { name: 'SMTP' });
        expect(screen.getAllByRole('radio').map(radio => radio.tabIndex)).toEqual([0, -1, -1]);
        smtp.focus();
        fireEvent.keyDown(smtp, { key: 'ArrowDown' });

        const http = screen.getByRole('radio', { name: 'HTTP' });
        expect(http).toHaveFocus();
        expect(http).toHaveAttribute('aria-checked', 'true');
        expect(screen.getAllByRole('radio').map(radio => radio.tabIndex)).toEqual([-1, 0, -1]);
        fireEvent.keyDown(http, { key: 'ArrowRight' });

        expect(screen.getByRole('radio', { name: 'Kafka, Enterprise' })).toHaveFocus();
        expect(http).toHaveAttribute('aria-checked', 'true');
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    });

    it('goes back to the plugin list when the schema does not load', async () => {
        getSchema.mockRejectedValue(new Error('Plugin not found'));
        renderWizard();

        fireEvent.click(await screen.findByRole('radio', { name: 'SMTP' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        expect(await screen.findByText('Plugin not found')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Back' }));

        expect(screen.getByRole('radio', { name: 'SMTP' })).toHaveAttribute('aria-checked', 'true');
    });

    it('goes back to the plugin list with the browser back, and a new pick resets the settings', async () => {
        renderWizard([SMTP, HTTP]);

        fireEvent.click(await screen.findByRole('radio', { name: 'SMTP' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));
        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Mail' } });
        fireEvent.click(screen.getByRole('button', { name: 'Browser back' }));
        expect(screen.getByRole('radio', { name: 'SMTP' })).toHaveAttribute('aria-checked', 'true');
        fireEvent.click(screen.getByRole('radio', { name: 'HTTP' }));
        fireEvent.click(screen.getByRole('button', { name: 'Next' }));

        expect(await screen.findByRole('textbox', { name: 'Name' })).toHaveValue('');
        expect(getSchema).toHaveBeenLastCalledWith(HTTP);
    });

    it('opens a step from its label once a plugin is picked', async () => {
        renderWizard();

        expect(await screen.findByRole('button', { name: 'Settings' })).toBeDisabled();
        fireEvent.click(screen.getByRole('radio', { name: 'SMTP' }));
        fireEvent.click(screen.getByRole('button', { name: 'Settings' }));
        expect(await screen.findByRole('textbox', { name: 'Name' })).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Platform' }));

        expect(screen.getByRole('radio', { name: 'SMTP' })).toHaveAttribute('aria-checked', 'true');
        expect(screen.queryByRole('textbox', { name: 'Name' })).not.toBeInTheDocument();
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
