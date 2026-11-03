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
import { renderWithGraphene } from '@gravitee/graphene-core/testing';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { screen, waitFor } from '@testing-library/react';
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

    it('blocks the submit while a required file is missing', async () => {
        const onSubmit = renderForm();

        await userEvent.click(await screen.findByRole('button', { name: 'Create' }));

        expect(await screen.findByText('This field is required')).toBeInTheDocument();
        expect(onSubmit).not.toHaveBeenCalled();
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
        properties: { authentication: { type: 'boolean' }, username: { type: 'string' } },
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

    it('passes when the if does not hold', async () => {
        const config = { authentication: false };

        expect(await resolve(config)).toEqual({ values: { config }, errors: {} });
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
