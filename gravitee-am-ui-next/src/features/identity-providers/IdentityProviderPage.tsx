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
import {
    Alert,
    AlertDescription,
    AlertTitle,
    Button,
    composeResolvers,
    Field,
    FieldError,
    FieldLabel,
    Input,
    JsonSchemaForm,
    jsonSchemaResolver,
    Spinner,
    toast,
} from '@gravitee/graphene-core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo } from 'react';
import { type Control, type Resolver, type ResolverResult, useForm } from 'react-hook-form';
import { useParams } from 'react-router-dom';
import { PageHeader } from '../../app/components/PageHeader';
import {
    getIdentityProvider,
    getIdentityProviderSchema,
    type IdentityProvider,
    updateIdentityProvider,
} from '../../lib/api/management-api';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { type AdaptedSchema, adaptAmSchema } from './am-schema';

// A type alias, not an interface: Graphene's resolvers constrain the values to `Record<string, unknown>`.
type ProviderValues = {
    name: string;
    config: Record<string, unknown>;
};

const nameResolver: Resolver<ProviderValues> = async (values): Promise<ResolverResult<ProviderValues>> =>
    values.name.trim() ? { values, errors: {} } : { values: {}, errors: { name: { type: 'required', message: 'Name is required' } } };

function ProviderForm({ provider, adapted }: { readonly provider: IdentityProvider; readonly adapted: AdaptedSchema }) {
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const resolver = useMemo(
        () => composeResolvers<ProviderValues>(nameResolver, jsonSchemaResolver<ProviderValues>(adapted.schema, { basePath: 'config' })),
        [adapted.schema],
    );
    const form = useForm<ProviderValues>({
        resolver,
        mode: 'onTouched',
        criteriaMode: 'all',
        defaultValues: { name: provider.name, config: JSON.parse(provider.configuration ?? '{}') },
    });
    const save = useMutation({
        mutationFn: (values: ProviderValues) =>
            updateIdentityProvider(ref, { ...provider, name: values.name, configuration: JSON.stringify(values.config) }),
        onSuccess: updated => {
            queryClient.setQueryData(['identity-provider', ref, provider.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['identity-providers', ref] });
            form.reset({ name: updated.name, config: JSON.parse(updated.configuration ?? '{}') });
            toast.success('Identity provider saved.');
        },
        onError: error => toast.error(`Could not save the identity provider. ${error.message}`),
    });
    const nameError = form.formState.errors.name;

    return (
        <form noValidate onSubmit={form.handleSubmit(values => save.mutate(values))} className="flex max-w-3xl flex-col gap-4">
            {adapted.unsupported.length > 0 && (
                <Alert>
                    <AlertTitle>Some fields use a plain input</AlertTitle>
                    <AlertDescription>{adapted.unsupported.join(', ')}</AlertDescription>
                </Alert>
            )}
            <Field data-invalid={!!nameError}>
                <FieldLabel htmlFor="name">Name</FieldLabel>
                <Input id="name" aria-invalid={!!nameError} {...form.register('name')} />
                {nameError && <FieldError errors={[nameError]} />}
            </Field>
            <JsonSchemaForm schema={adapted.schema} control={form.control as unknown as Control} name="config" />
            <div>
                <Button type="submit" disabled={!form.formState.isDirty || save.isPending}>
                    Save
                </Button>
            </div>
        </form>
    );
}

export function IdentityProviderPage() {
    const { identityProviderId = '' } = useParams();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { data: provider, error } = useQuery({
        queryKey: ['identity-provider', ref, identityProviderId],
        queryFn: () => getIdentityProvider(ref, identityProviderId),
    });
    const { data: adapted } = useQuery({
        queryKey: ['identity-provider-schema', provider?.type],
        queryFn: async () => adaptAmSchema(await getIdentityProviderSchema(provider!.type)),
        enabled: !!provider && !provider.system,
        staleTime: Infinity,
    });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: basePath },
        { label: 'Identity providers', to: `${basePath}/identity-providers` },
        { label: provider?.name ?? '…' },
    ]);

    if (error) {
        return <p className="text-sm text-destructive">Could not load the identity provider.</p>;
    }
    if (!provider) {
        return <Spinner />;
    }
    return (
        <div className="flex flex-col gap-6">
            <PageHeader title={provider.name} description={<span className="font-mono text-xs">{provider.type}</span>} />
            {provider.system ? (
                <p className="text-sm text-muted-foreground">Access Management manages the configuration of system identity providers.</p>
            ) : adapted ? (
                <ProviderForm provider={provider} adapted={adapted} />
            ) : (
                <Spinner />
            )}
        </div>
    );
}
