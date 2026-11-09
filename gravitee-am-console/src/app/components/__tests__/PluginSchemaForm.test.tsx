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
import { Button } from '@gravitee/graphene-core';
import { renderWithGraphene, selectHarness } from '@gravitee/graphene-core/testing';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useMemo } from 'react';
import { useForm } from 'react-hook-form';
import { http } from '../../../lib/api/http';
import { type AdaptedSchema, adaptAmSchema, type SchemaResource, withSchemaDefaults } from '../../../lib/forms/am-schema';
import { fileValue, PluginSchemaForm, pluginSchemaResolver, useAdaptedSchema } from '../PluginSchemaForm';

vi.mock('../../../lib/api/http', () => ({ http: vi.fn() }));

const SCHEMA = {
    type: 'object',
    required: ['content'],
    properties: {
        content: { type: 'string', title: 'Keystore file', widget: 'file' },
        factorType: { title: 'Factor type', default: 'EMAIL' },
        returnDigits: { type: 'integer', title: 'Number of digits', default: 6 },
        events: {
            type: 'array',
            title: 'Events',
            widget: 'graviteeMultiselect',
            itemsDictionaryEndpoint: '/platform/audits/events',
            items: { type: 'string' },
        },
        graviteeResource: { type: 'string', title: 'Resource' },
    },
};

const RESOURCES: SchemaResource[] = [{ id: 'resource-1', name: 'Mail server' }];

type Values = { config: Record<string, unknown> };

function ConfigurationForm({ adapted, onSubmit }: { readonly adapted: AdaptedSchema; readonly onSubmit: (values: Values) => void }) {
    const resolver = useMemo(() => pluginSchemaResolver<Values>(adapted, 'config'), [adapted]);
    const form = useForm<Values>({ resolver, defaultValues: { config: withSchemaDefaults(adapted.schema, {}) } });
    return (
        <form noValidate onSubmit={form.handleSubmit(onSubmit)}>
            <PluginSchemaForm adapted={adapted} control={form.control} name="config" />
            <Button type="submit">Create</Button>
        </form>
    );
}

function Host({ onSubmit }: { readonly onSubmit: (values: Values) => void }) {
    const { adapted } = useAdaptedSchema(SCHEMA, RESOURCES);
    return adapted ? <ConfigurationForm adapted={adapted} onSubmit={onSubmit} /> : <p>Loading</p>;
}

function renderForm() {
    const onSubmit = vi.fn();
    renderWithGraphene(
        <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
            <Host onSubmit={onSubmit} />
        </QueryClientProvider>,
    );
    return onSubmit;
}

describe('PluginSchemaForm', () => {
    beforeEach(() => {
        vi.mocked(http).mockResolvedValue(['USER_CREATED', 'USER_DELETED']);
    });

    it('submits the defaults, the file, the multiselect items and the resource id', async () => {
        const onSubmit = renderForm();
        const keystore = new File(['keystore'], 'keystore.p12', { type: 'application/x-pkcs12' });

        await userEvent.upload(await screen.findByLabelText(/Keystore file/), keystore);
        await userEvent.click(screen.getByRole('combobox', { name: 'Events' }));
        await userEvent.click(await screen.findByRole('option', { name: 'USER_CREATED' }));
        await userEvent.keyboard('{Escape}');
        await userEvent.click(screen.getByRole('combobox', { name: 'Resource' }));
        await userEvent.click(await screen.findByRole('option', { name: 'Mail server' }));
        await userEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() => expect(onSubmit).toHaveBeenCalled());
        expect(http).toHaveBeenCalledWith('/platform/audits/events');
        expect(onSubmit.mock.calls[0][0]).toEqual({
            config: {
                content: await fileValue(keystore),
                returnDigits: 6,
                events: ['USER_CREATED'],
                graviteeResource: 'resource-1',
            },
        });
    });

    it('edits the scopes as tags, adding each one on a space, and submits them as a list', async () => {
        const onSubmit = vi.fn();
        const adapted = adaptAmSchema({
            type: 'object',
            properties: { scopes: { type: 'array', title: 'Scopes', items: { type: 'string' } } },
        });
        renderWithGraphene(<ConfigurationForm adapted={adapted} onSubmit={onSubmit} />);

        await userEvent.type(screen.getByRole('combobox', { name: 'Scopes' }), 'openid profile ');
        await userEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() => expect(onSubmit).toHaveBeenCalled());
        expect(onSubmit.mock.calls[0][0]).toEqual({ config: { scopes: ['openid', 'profile'] } });
        expect(screen.queryByText(/Item 1/)).not.toBeInTheDocument();
    });

    it('blocks the submit while a required string list is empty', async () => {
        const onSubmit = vi.fn();
        const adapted = adaptAmSchema({
            type: 'object',
            required: ['scopes'],
            properties: { scopes: { type: 'array', title: 'Scopes', items: { type: 'string' } } },
        });
        renderWithGraphene(<ConfigurationForm adapted={adapted} onSubmit={onSubmit} />);

        await userEvent.click(screen.getByRole('button', { name: 'Create' }));

        expect(await screen.findByText('This field is required')).toBeInTheDocument();
        expect(onSubmit).not.toHaveBeenCalled();
    });

    it('blocks the submit while a required file is missing', async () => {
        const onSubmit = renderForm();

        await userEvent.click(await screen.findByRole('button', { name: 'Create' }));

        expect(await screen.findByText('This field is required')).toBeInTheDocument();
        expect(onSubmit).not.toHaveBeenCalled();
    });

    it('shows the form with a read-only multiselect when its dictionary fails to load', async () => {
        vi.mocked(http).mockRejectedValue(new Error('403'));
        renderForm();

        expect(await screen.findByLabelText(/Keystore file/)).toBeInTheDocument();
        expect(within(screen.getByRole('group', { name: 'Events' })).getByRole('button', { name: 'Add' })).toBeDisabled();
    });

    it('warns the developer about the fields it shows without their Angular widget', () => {
        const adapted = adaptAmSchema({
            type: 'object',
            properties: { script: { type: 'string', 'x-schema-form': { type: 'codemirror' } } },
        });
        function Unsupported() {
            const form = useForm<Values>({ defaultValues: { config: {} } });
            return <PluginSchemaForm adapted={adapted} control={form.control} name="config" />;
        }
        renderWithGraphene(<Unsupported />);

        expect(screen.getByText('script (widget "codemirror")')).toBeInTheDocument();
    });
});

describe('pluginSchemaResolver', () => {
    const adapted = adaptAmSchema({
        type: 'object',
        properties: {
            authentication: { type: 'boolean' },
            username: { type: 'string' },
            truststore: { type: 'string', widget: 'file' },
        },
        allOf: [{ if: { properties: { authentication: { const: true } } }, then: { required: ['username'] } }],
    });
    const resolve = (config: Record<string, unknown>) =>
        pluginSchemaResolver<Values>(adapted, 'config')({ config }, undefined, {
            criteriaMode: 'firstError',
            fields: {},
            shouldUseNativeValidation: false,
        });

    it('applies the if/then blocks of allOf, which Graphene drops', async () => {
        expect((await resolve({ authentication: true })).errors).toEqual({
            config: { username: expect.objectContaining({ type: 'required', message: 'This field is required' }) },
        });
    });

    it('passes when the if does not hold and the optional file is empty', async () => {
        const config = { authentication: false };

        expect(await resolve(config)).toEqual({ values: { config }, errors: {} });
    });

    it('passes a stored null, which the Management API drops, and keeps it in the values', async () => {
        const config = { authentication: false, username: null };

        expect(await resolve(config)).toEqual({ values: { config }, errors: {} });
    });
});

describe('PluginSchemaForm certificate select', () => {
    it('clears a picked certificate with None, which submits as unset', async () => {
        const adapted = adaptAmSchema({
            type: 'object',
            properties: {
                certificate: {
                    type: 'string',
                    title: 'Signing certificate',
                    widget: 'select',
                    oneOf: [
                        { const: null, title: 'None' },
                        { const: 'c1', title: 'Signing cert' },
                    ],
                },
            },
        });
        const onSubmit = vi.fn();
        function Edit() {
            const resolver = useMemo(() => pluginSchemaResolver<Values>(adapted, 'config'), []);
            const form = useForm<Values>({ resolver, defaultValues: { config: { certificate: 'c1' } } });
            return (
                <form noValidate onSubmit={form.handleSubmit(onSubmit)}>
                    <PluginSchemaForm adapted={adapted} control={form.control} name="config" />
                    <Button type="submit">Save</Button>
                </form>
            );
        }
        renderWithGraphene(
            <QueryClientProvider client={new QueryClient()}>
                <Edit />
            </QueryClientProvider>,
        );

        expect(screen.getByRole('combobox', { name: /Signing certificate/ })).toHaveTextContent('Signing cert');
        await selectHarness({ name: /Signing certificate/ }).selectOption('None');
        await userEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({ config: { certificate: null } }, expect.anything()));
    });
});

describe('PluginSchemaForm conditional fields', () => {
    const adapted = adaptAmSchema({
        type: 'object',
        properties: {
            responseType: { type: 'string', title: 'Response type', enum: ['code', 'id_token'], default: 'code' },
            clientSecret: { type: 'string', title: 'Client secret' },
        },
        allOf: [{ if: { properties: { responseType: { const: 'code' } } }, then: { required: ['clientSecret'] } }],
    });

    it('marks a field as required while the condition that requires it holds', () => {
        renderWithGraphene(
            <QueryClientProvider client={new QueryClient()}>
                <ConfigurationForm adapted={adapted} onSubmit={vi.fn()} />
            </QueryClientProvider>,
        );

        expect(document.querySelector('[data-slot=field-label][for="config.clientSecret"]')?.textContent).toContain('*');
    });

    it('moves the marker when the condition changes, and keeps the typed values', async () => {
        const toggled = adaptAmSchema({
            type: 'object',
            properties: {
                authentication: { type: 'boolean', title: 'Authentication' },
                username: { type: 'string', title: 'Username' },
            },
            allOf: [{ if: { properties: { authentication: { const: true } } }, then: { required: ['username'] } }],
        });
        renderWithGraphene(
            <QueryClientProvider client={new QueryClient()}>
                <ConfigurationForm adapted={toggled} onSubmit={vi.fn()} />
            </QueryClientProvider>,
        );
        const label = () => document.querySelector('[data-slot=field-label][for="config.username"]')?.textContent;
        await userEvent.type(screen.getByRole('textbox', { name: /Username/ }), 'admin');
        expect(label()).not.toContain('*');

        await userEvent.click(screen.getByRole('switch', { name: /Authentication/ }));

        await waitFor(() => expect(label()).toContain('*'));
        expect(screen.getByRole('textbox', { name: /Username/ })).toHaveValue('admin');
    });
});

describe('PluginSchemaForm in edit mode', () => {
    it('shows the name of the saved file until the user picks another', () => {
        const adapted = adaptAmSchema({
            type: 'object',
            properties: { content: { type: 'string', title: 'Keystore file', widget: 'file' } },
        });
        function Edit() {
            const form = useForm<Values>({ defaultValues: { config: { content: '/opt/graviteeio/certs/keystore.p12' } } });
            return <PluginSchemaForm adapted={adapted} control={form.control} name="config" />;
        }
        renderWithGraphene(<Edit />);

        expect(screen.getByText('File currently saved: keystore.p12')).toBeInTheDocument();
    });

    it('goes back to the saved file when the user removes the picked one', async () => {
        const adapted = adaptAmSchema({
            type: 'object',
            properties: { content: { type: 'string', title: 'Keystore file', widget: 'file' } },
        });
        const onSubmit = vi.fn();
        function Edit() {
            const form = useForm<Values>({ defaultValues: { config: { content: '/opt/graviteeio/certs/keystore.p12' } } });
            return (
                <form noValidate onSubmit={form.handleSubmit(onSubmit)}>
                    <PluginSchemaForm adapted={adapted} control={form.control} name="config" />
                    <Button type="submit">Save</Button>
                </form>
            );
        }
        renderWithGraphene(<Edit />);

        await userEvent.upload(screen.getByLabelText(/Keystore file/), new File(['new'], 'new.p12'));
        await userEvent.click(await screen.findByRole('button', { name: 'Remove new.p12' }));
        await userEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(screen.getByText('File currently saved: keystore.p12')).toBeInTheDocument();
        await waitFor(() => expect(onSubmit).toHaveBeenCalled());
        expect(onSubmit.mock.calls[0][0]).toEqual({ config: { content: '/opt/graviteeio/certs/keystore.p12' } });
    });

    it('drops the picked file when the form resets', async () => {
        const adapted = adaptAmSchema({
            type: 'object',
            properties: { content: { type: 'string', title: 'Keystore file', widget: 'file' } },
        });
        function Edit() {
            const form = useForm<Values>({ defaultValues: { config: { content: '/opt/graviteeio/certs/keystore.p12' } } });
            return (
                <>
                    <PluginSchemaForm adapted={adapted} control={form.control} name="config" />
                    <Button onClick={() => form.reset()}>Discard</Button>
                </>
            );
        }
        renderWithGraphene(<Edit />);

        await userEvent.upload(screen.getByLabelText(/Keystore file/), new File(['new'], 'new.p12'));
        await waitFor(() => expect(screen.queryByText(/File currently saved/)).not.toBeInTheDocument());
        await userEvent.click(screen.getByRole('button', { name: 'Discard' }));

        expect(await screen.findByText('File currently saved: keystore.p12')).toBeInTheDocument();
        expect(screen.queryByText('new.p12')).not.toBeInTheDocument();
    });
});

describe('fileValue', () => {
    it('writes the file as the Angular file widget does, with the content in base64', async () => {
        const value = await fileValue(new File(['hello'], 'hello.txt', { type: 'text/plain' }));

        expect(JSON.parse(value)).toEqual({ name: 'hello.txt', type: 'text/plain', size: 5, content: btoa('hello') });
    });
});
