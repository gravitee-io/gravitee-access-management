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
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    composeResolvers,
    CopyableText,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    JsonSchemaForm,
    jsonSchemaResolver,
    Skeleton,
    toast,
} from '@gravitee/graphene-core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { type Control, Controller, type Resolver, type ResolverResult, useForm } from 'react-hook-form';
import { Navigate, useNavigate } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { SaveBar } from '../../app/components/SaveBar';
import { TagInput } from '../../app/components/TagInput';
import { listCertificates } from '../../lib/api/certificates';
import { listDataSources } from '../../lib/api/data-sources';
import { listDomainEntrypoints } from '../../lib/api/domains';
import {
    deleteIdentityProvider,
    getIdentityProviderSchema,
    type IdentityProvider,
    listIdentityProviderPlugins,
    serializeConfiguration,
    updateIdentityProvider,
} from '../../lib/api/identity-providers';
import { getInstallationConfiguration } from '../../lib/api/management-api';
import { type AdaptedSchema, adaptAmSchema } from '../../lib/forms/am-schema';
import { useCurrentDomain } from '../../lib/session/domain';
import { LicenseGuard } from '../../lib/session/license';
import { Can, usePermissions } from '../../lib/session/permissions';
import { useIdentityProvider } from './IdentityProviderLayout';
import { enrichSettingsSchema, MONGO_IDP_TYPE, pinnedFields, withBooleanDefaults } from './provider-schema';
import { redirectUri } from './redirect-uri';

const INLINE_TYPE = 'inline-am-idp';

// A type alias, not an interface: Graphene's resolvers constrain the values to `Record<string, unknown>`.
type ProviderValues = {
    name: string;
    domainWhitelist: string[];
    config: Record<string, unknown>;
};

type Pending = 'delete' | 'inline-users' | null;

const nameResolver: Resolver<ProviderValues> = async (values): Promise<ResolverResult<ProviderValues>> =>
    values.name.trim() ? { values, errors: {} } : { values: {}, errors: { name: { type: 'required', message: 'Name is required' } } };

function parseConfiguration(provider: IdentityProvider): Record<string, unknown> {
    return JSON.parse(provider.configuration ?? '{}') as Record<string, unknown>;
}

/** True when the update drops or renames a user of an inline provider, whose password must then be typed again. */
function dropsInlineUser(provider: IdentityProvider, config: Record<string, unknown>): boolean {
    const original = (parseConfiguration(provider).users ?? []) as { username: string }[];
    const updated = ((config.users ?? []) as { username: string }[]).map(user => user.username);
    return !original.every(user => updated.includes(user.username));
}

function Integration({ provider }: { readonly provider: IdentityProvider }) {
    const { domain, ref } = useCurrentDomain();
    const { data: entrypoints } = useQuery({ queryKey: ['domain-entrypoints', ref], queryFn: () => listDomainEntrypoints(ref) });
    const uri = entrypoints && redirectUri(entrypoints, domain);
    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-base">Integration</CardTitle>
                <CardDescription>
                    The redirect URI (callback URL, or Assertion Consumer Service URL) must be known by your identity server. Register it in
                    your identity server’s app settings.
                </CardDescription>
            </CardHeader>
            <CardContent className="grid gap-5">
                <Field>
                    <FieldLabel>Redirect URI</FieldLabel>
                    {uri && <CopyableText className="max-w-full" value={uri} />}
                </Field>
                <Field>
                    <FieldLabel>Custom login page</FieldLabel>
                    <CopyableText
                        className="max-w-full"
                        value={`<a th:href="\${authorizeUrls.get('${provider.id}')}">SIGN IN WITH OAUTH2 PROVIDER</a>`}
                    />
                    <FieldDescription>
                        If you enabled a custom login form, add this link to its content to offer this provider.
                    </FieldDescription>
                </Field>
            </CardContent>
        </Card>
    );
}

interface SettingsFormProps {
    readonly provider: IdentityProvider;
    readonly adapted: AdaptedSchema;
    readonly configuration: Record<string, unknown>;
    readonly pinned: readonly string[];
    readonly feature?: string;
}

function SettingsForm({ provider, adapted, configuration, pinned, feature }: SettingsFormProps) {
    const { ref } = useCurrentDomain();
    const { listPath } = useIdentityProvider();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_identity_provider_update');
    const [pending, setPending] = useState<Pending>(null);
    const [pendingValues, setPendingValues] = useState<ProviderValues | null>(null);
    const resolver = useMemo(
        () => composeResolvers<ProviderValues>(nameResolver, jsonSchemaResolver<ProviderValues>(adapted.schema, { basePath: 'config' })),
        [adapted.schema],
    );
    const form = useForm<ProviderValues>({
        resolver,
        mode: 'onTouched',
        criteriaMode: 'all',
        defaultValues: { name: provider.name, domainWhitelist: provider.domainWhitelist ?? [], config: configuration },
    });
    const save = useMutation({
        mutationFn: async (values: ProviderValues) => {
            // A read-only field reaches the server as stored, as the Angular `withPinnedStorage` does.
            const stored = parseConfiguration(provider);
            const config = { ...values.config, ...Object.fromEntries(pinned.map(field => [field, stored[field]])) };
            return updateIdentityProvider(ref, {
                ...provider,
                name: values.name,
                domainWhitelist: values.domainWhitelist,
                configuration: await serializeConfiguration(provider.type, config),
            });
        },
        onSuccess: updated => {
            queryClient.setQueryData(['identity-provider', ref, provider.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['identity-providers', ref] });
            form.reset({ name: updated.name, domainWhitelist: updated.domainWhitelist ?? [], config: parseConfiguration(updated) });
            toast.success('Provider updated');
        },
        onError: error => toast.error(`Could not update the provider. ${error.message}`),
    });
    const remove = useMutation({
        mutationFn: () => deleteIdentityProvider(ref, provider.id),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['identity-providers', ref] });
            toast.success('Identity provider deleted');
            navigate(listPath);
        },
        onError: error => toast.error(`Could not delete the provider. ${error.message}`),
    });
    const submit = (values: ProviderValues) => {
        if (provider.type === INLINE_TYPE && dropsInlineUser(provider, values.config)) {
            setPendingValues(values);
            setPending('inline-users');
            return;
        }
        save.mutate(values);
    };
    const confirmCopy = {
        delete: { title: 'Delete provider', description: 'Are you sure you want to delete this provider?', label: 'Delete' },
        'inline-users': {
            title: 'Update provider: a user has been modified or deleted',
            description:
                'If you modified an existing user with another username, make sure the password has been modified manually too. Do you want to save your configuration?',
            label: 'Save',
        },
    };
    const nameError = form.formState.errors.name;

    return (
        <form noValidate onSubmit={form.handleSubmit(submit)} className="flex flex-col gap-6">
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">General</CardTitle>
                    <CardDescription>Register your users to interact with the Access Management server.</CardDescription>
                </CardHeader>
                <CardContent className="grid gap-5">
                    <Field data-invalid={!!nameError}>
                        <FieldLabel htmlFor="name">Name</FieldLabel>
                        <Input id="name" aria-invalid={!!nameError} disabled={!canUpdate} {...form.register('name')} />
                        {nameError && <FieldError errors={[nameError]} />}
                    </Field>
                    {provider.external && (
                        <Field>
                            <FieldLabel htmlFor="domain-whitelist">Domain whitelist</FieldLabel>
                            <Controller
                                control={form.control}
                                name="domainWhitelist"
                                render={({ field }) => (
                                    <TagInput
                                        id="domain-whitelist"
                                        values={field.value}
                                        onChange={field.onChange}
                                        onDuplicate={value => toast.error(`Error : domain whitelist pattern "${value}" already exists`)}
                                        placeholder="Enter a domain whitelist pattern"
                                        disabled={!canUpdate}
                                    />
                                )}
                            />
                            <FieldDescription>
                                Filters users on their email domain after they authenticate with the provider. Users outside the list are
                                logged out.
                            </FieldDescription>
                        </Field>
                    )}
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Configuration</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    {provider.type === MONGO_IDP_TYPE && (
                        <Alert>
                            <AlertTitle>User storage</AlertTitle>
                            <AlertDescription>
                                Prefer a data source. It is the connection the provider actually uses (ahead of the system cluster and any
                                URI on this form) and credentials stay in the platform configuration.
                            </AlertDescription>
                        </Alert>
                    )}
                    {adapted.unsupported.length > 0 && (
                        <Alert>
                            <AlertTitle>Some fields use a plain input</AlertTitle>
                            <AlertDescription>{adapted.unsupported.join(', ')}</AlertDescription>
                        </Alert>
                    )}
                    <JsonSchemaForm
                        schema={adapted.schema}
                        control={form.control as unknown as Control}
                        name="config"
                        disabled={!canUpdate}
                    />
                </CardContent>
            </Card>

            {provider.external && <Integration provider={provider} />}

            <LicenseGuard feature={feature}>
                <SaveBar canEdit={canUpdate} dirty={form.formState.isDirty} saving={save.isPending} onDiscard={() => form.reset()} />
            </LicenseGuard>

            <Can all={['domain_identity_provider_delete']}>
                <Card className="border-destructive/30">
                    <CardHeader>
                        <CardTitle className="text-base">Danger zone</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="flex flex-wrap items-center justify-between gap-4">
                            <div className="flex flex-col gap-0.5">
                                <p className="text-sm font-medium">Delete identity provider</p>
                                <p className="text-sm text-muted-foreground">
                                    Applications will no longer be able to use this identity provider to authenticate users.
                                </p>
                            </div>
                            <Button type="button" variant="destructive" onClick={() => setPending('delete')}>
                                Delete
                            </Button>
                        </div>
                    </CardContent>
                </Card>
            </Can>

            <ConfirmDialog
                open={pending !== null}
                title={pending ? confirmCopy[pending].title : ''}
                description={pending ? confirmCopy[pending].description : ''}
                confirmLabel={pending ? confirmCopy[pending].label : ''}
                destructive={pending === 'delete'}
                pending={remove.isPending}
                onOpenChange={open => !open && setPending(null)}
                onConfirm={() => {
                    if (pending === 'delete') {
                        remove.mutate();
                        return;
                    }
                    setPending(null);
                    if (pendingValues) {
                        save.mutate(pendingValues);
                    }
                }}
            />
        </form>
    );
}

/** The Angular `ProviderSettingsComponent`: name, domain whitelist, plugin configuration, integration help and delete. */
export function IdentityProviderSettingsPage() {
    const { provider, providerPath } = useIdentityProvider();
    const { ref } = useCurrentDomain();
    const enabled = !provider.system;
    const schema = useQuery({
        queryKey: ['identity-provider-schema', provider.type],
        queryFn: () => getIdentityProviderSchema(provider.type),
        enabled,
        staleTime: Infinity,
    });
    const certificates = useQuery({ queryKey: ['certificates', ref], queryFn: () => listCertificates(ref), enabled });
    const dataSources = useQuery({
        queryKey: ['data-sources', ref.organizationId, ref.environmentId],
        queryFn: () => listDataSources(ref.organizationId, ref.environmentId),
        enabled,
    });
    const installation = useQuery({ queryKey: ['installation'], queryFn: getInstallationConfiguration, staleTime: Infinity, enabled });
    const plugins = useQuery({
        queryKey: ['identity-provider-plugins'],
        queryFn: listIdentityProviderPlugins,
        staleTime: Infinity,
        enabled,
    });
    const installationRestricted = installation.data?.systemClusterRestricted === true;
    const pinned = pinnedFields(provider.type, provider.systemClusterRestricted === true, installationRestricted);
    const adapted = useMemo(
        () =>
            schema.data && certificates.data && dataSources.data && installation.data
                ? adaptAmSchema(
                      enrichSettingsSchema(schema.data, {
                          type: provider.type,
                          certificates: certificates.data,
                          dataSources: dataSources.data,
                          systemClusterRestricted: installationRestricted,
                          providerRestricted: provider.systemClusterRestricted === true,
                      }),
                  )
                : undefined,
        [schema.data, certificates.data, dataSources.data, installation.data, installationRestricted, provider],
    );

    // The settings of a system provider are not editable: Angular lands on its mappers instead.
    if (provider.system) {
        return <Navigate to={`${providerPath}/mappers`} replace />;
    }
    const error = schema.error ?? certificates.error ?? dataSources.error ?? installation.error ?? plugins.error;
    if (error) {
        return <ErrorPanel description="Could not load the provider configuration." error={error} />;
    }
    if (!adapted || !schema.data || !plugins.data) {
        return <Skeleton className="h-64 w-full" />;
    }
    return (
        <SettingsForm
            key={provider.id}
            provider={provider}
            adapted={adapted}
            configuration={withBooleanDefaults(schema.data, parseConfiguration(provider))}
            pinned={pinned}
            feature={plugins.data.find(plugin => plugin.id === provider.type)?.feature}
        />
    );
}
