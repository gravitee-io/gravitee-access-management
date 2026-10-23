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
    Badge,
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    composeResolvers,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    JsonSchemaForm,
    jsonSchemaResolver,
    PageFocused,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Skeleton,
    toast,
} from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { type Control, Controller, type Resolver, type ResolverResult, useForm } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import {
    deleteExtensionGrant,
    type ExtensionGrant,
    getExtensionGrant,
    getExtensionGrantSchema,
    listExtensionGrantPlugins,
    updateExtensionGrant,
} from '../../lib/api/extension-grants';
import { isNotFound } from '../../lib/api/http';
import { listIdentityProviders } from '../../lib/api/identity-providers';
import { type AdaptedSchema, adaptAmSchema, withSchemaDefaults } from '../../lib/forms/am-schema';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { LicenseGuard } from '../../lib/session/license';
import { Can, usePermissions } from '../../lib/session/permissions';
import {
    configurationSchema,
    CROSS_APP_ACCESS,
    extensionGrantTypeName,
    GRANT_TYPE_DESCRIPTION,
    schemaGrantType,
    toConfiguration,
    toFormConfiguration,
} from './extension-grant-types';

// A type alias, not an interface: Graphene's resolvers constrain the values to `Record<string, unknown>`.
type ExtensionGrantValues = {
    name: string;
    identityProvider: string | undefined;
    createUser: boolean;
    userExists: boolean;
    config: Record<string, unknown>;
};

const nameResolver: Resolver<ExtensionGrantValues> = async (values): Promise<ResolverResult<ExtensionGrantValues>> =>
    values.name.trim() ? { values, errors: {} } : { values: {}, errors: { name: { type: 'required', message: 'Name is required' } } };

function toValues(extensionGrant: ExtensionGrant, adapted: AdaptedSchema): ExtensionGrantValues {
    return {
        name: extensionGrant.name,
        identityProvider: extensionGrant.identityProvider,
        createUser: extensionGrant.createUser ?? false,
        userExists: extensionGrant.userExists ?? false,
        config: toFormConfiguration(
            adapted.schema,
            withSchemaDefaults(adapted.schema, JSON.parse(extensionGrant.configuration || '{}') as Record<string, unknown>),
        ),
    };
}

/** The Angular identity provider select. A failed list disables only this field. */
function IdentityProviderSelect({
    value,
    disabled,
    onChange,
}: {
    readonly value: string | undefined;
    readonly disabled: boolean;
    readonly onChange: (value: string) => void;
}) {
    const { ref } = useCurrentDomain();
    const { data, error } = useQuery({ queryKey: ['identity-providers', ref], queryFn: () => listIdentityProviders(ref) });
    if (!data && !error) return <Skeleton className="h-9 w-full" />;
    return (
        <Field>
            <FieldLabel htmlFor="extension-grant-identity-provider">Identity provider</FieldLabel>
            <Select value={value} disabled={disabled || !!error} onValueChange={onChange}>
                <SelectTrigger id="extension-grant-identity-provider" className="w-full">
                    <SelectValue placeholder="Identity provider" />
                </SelectTrigger>
                <SelectContent>
                    {(data ?? []).map(provider => (
                        <SelectItem key={provider.id} value={provider.id}>
                            {provider.name}
                        </SelectItem>
                    ))}
                </SelectContent>
            </Select>
            <FieldDescription>
                {error
                    ? 'Identity providers could not be retrieved, so this setting cannot be changed right now.'
                    : 'Identity provider used to retrieve user information from incoming request assertion.'}
            </FieldDescription>
        </Field>
    );
}

interface ExtensionGrantFormProps {
    readonly extensionGrant: ExtensionGrant;
    readonly adapted: AdaptedSchema;
    /** Undefined when neither the grant nor the schema has one. The PUT then leaves the key out, as Angular does. */
    readonly grantType: string | undefined;
    readonly feature?: string;
    readonly listPath: string;
}

function ExtensionGrantForm({ extensionGrant, adapted, grantType, feature, listPath }: ExtensionGrantFormProps) {
    const { ref } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_extension_grant_update');
    const [confirmDelete, setConfirmDelete] = useState(false);
    const crossAppAccess = extensionGrant.type === CROSS_APP_ACCESS;
    const resolver = useMemo(
        () =>
            composeResolvers<ExtensionGrantValues>(
                nameResolver,
                jsonSchemaResolver<ExtensionGrantValues>(adapted.schema, { basePath: 'config' }),
            ),
        [adapted],
    );
    const defaultValues = useMemo(() => toValues(extensionGrant, adapted), [extensionGrant, adapted]);
    const form = useForm<ExtensionGrantValues>({ resolver, mode: 'onTouched', criteriaMode: 'all', defaultValues });
    const save = useMutation({
        mutationFn: (values: ExtensionGrantValues) =>
            updateExtensionGrant(ref, extensionGrant.id, {
                name: values.name,
                type: extensionGrant.type,
                configuration: toConfiguration(adapted.schema, values.config),
                grantType,
                identityProvider: values.identityProvider,
                createUser: values.createUser,
                userExists: values.userExists,
            }),
        onSuccess: updated => {
            queryClient.setQueryData(['extension-grant', ref, extensionGrant.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['extension-grants', ref] });
            form.reset(toValues(updated, adapted));
            toast.success('Extension grant updated');
        },
        onError: error => toast.error(`Could not update the extension grant. ${error.message}`),
    });
    const remove = useMutation({
        mutationFn: () => deleteExtensionGrant(ref, extensionGrant.id),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['extension-grants', ref] });
            toast.success('Extension grant deleted');
            navigate(listPath);
        },
        onError: error => toast.error(`Could not delete the extension grant. ${error.message}`),
    });
    const nameError = form.formState.errors.name;

    return (
        <form noValidate onSubmit={form.handleSubmit(values => save.mutate(values))} className="flex flex-col gap-6">
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Details</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    <Field data-invalid={!!nameError}>
                        <FieldLabel htmlFor="extension-grant-name">Name</FieldLabel>
                        <Input id="extension-grant-name" aria-invalid={!!nameError} disabled={!canUpdate} {...form.register('name')} />
                        {nameError && <FieldError errors={[nameError]} />}
                    </Field>
                    <Field>
                        <FieldLabel htmlFor="extension-grant-type">Grant type</FieldLabel>
                        <Input id="extension-grant-type" value={grantType ?? ''} disabled />
                        <FieldDescription>{GRANT_TYPE_DESCRIPTION}</FieldDescription>
                    </Field>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">{crossAppAccess ? 'Identity provider' : 'Users'}</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    {crossAppAccess ? (
                        <p className="text-sm text-muted-foreground">
                            The identity provider is resolved from the assertion issuer: the provider whose discovered issuer matches the
                            assertion&apos;s iss claim verifies it.
                        </p>
                    ) : (
                        <>
                            <div>
                                <Controller
                                    control={form.control}
                                    name="createUser"
                                    render={({ field }) => (
                                        <SettingToggle
                                            id="extension-grant-create-user"
                                            label="Create user account"
                                            description="Used for user account activity and the OAuth 2.0 refresh token flow."
                                            checked={field.value}
                                            disabled={!canUpdate}
                                            onChange={field.onChange}
                                        />
                                    )}
                                />
                                <Controller
                                    control={form.control}
                                    name="userExists"
                                    render={({ field }) => (
                                        <SettingToggle
                                            id="extension-grant-user-exists"
                                            label="Check user existence"
                                            description="Check that the user exists in the identity provider."
                                            checked={field.value}
                                            disabled={!canUpdate}
                                            onChange={field.onChange}
                                        />
                                    )}
                                />
                            </div>
                            <Controller
                                control={form.control}
                                name="identityProvider"
                                render={({ field }) => (
                                    <IdentityProviderSelect value={field.value} disabled={!canUpdate} onChange={field.onChange} />
                                )}
                            />
                        </>
                    )}
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Configuration</CardTitle>
                    {adapted.schema.description && (
                        <CardDescription className="whitespace-pre-wrap">{adapted.schema.description}</CardDescription>
                    )}
                </CardHeader>
                <CardContent className="grid gap-5">
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

            <LicenseGuard feature={feature}>
                <SaveBar canEdit={canUpdate} dirty={form.formState.isDirty} saving={save.isPending} onDiscard={() => form.reset()} />
            </LicenseGuard>

            <Can all={['domain_extension_grant_delete']}>
                <Card className="border-destructive/30">
                    <CardHeader>
                        <CardTitle className="text-base">Danger zone</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="flex flex-wrap items-center justify-between gap-4">
                            <div className="flex flex-col gap-0.5">
                                <p className="text-sm font-medium">Delete extension grant</p>
                                <p className="text-sm text-muted-foreground">
                                    Applications will no longer be able to use this extension grant.
                                </p>
                            </div>
                            <Button type="button" variant="destructive" onClick={() => setConfirmDelete(true)}>
                                Delete
                            </Button>
                        </div>
                    </CardContent>
                </Card>
            </Can>

            <ConfirmDialog
                open={confirmDelete}
                title="Delete extension grant"
                description={`Delete ${extensionGrant.name}? Applications will no longer be able to use it. This cannot be undone.`}
                confirmLabel="Delete extension grant"
                destructive
                pending={remove.isPending}
                onOpenChange={setConfirmDelete}
                onConfirm={() => remove.mutate()}
            />
        </form>
    );
}

/**
 * The Angular `ExtensionGrantComponent`: name, read-only grant type, user creation, user existence check, identity
 * provider and the plugin configuration, saved together. A Cross App Access grant resolves its identity provider from
 * the assertion issuer, so the user settings do not apply to it.
 */
export function ExtensionGrantPage() {
    const { extensionGrantId = '' } = useParams();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const listPath = `${basePath}/settings/extensionGrants`;
    const { data: extensionGrant, error } = useQuery({
        queryKey: ['extension-grant', ref, extensionGrantId],
        queryFn: () => getExtensionGrant(ref, extensionGrantId),
    });
    const plugins = useQuery({ queryKey: ['extension-grant-plugins'], queryFn: listExtensionGrantPlugins, staleTime: Infinity });
    const schema = useQuery({
        queryKey: ['extension-grant-schema', extensionGrant?.type],
        queryFn: () => getExtensionGrantSchema(extensionGrant!.type),
        enabled: extensionGrant !== undefined,
        staleTime: Infinity,
    });
    const plugin = plugins.data?.find(candidate => candidate.id === extensionGrant?.type);
    const adapted = useMemo(() => (schema.data ? adaptAmSchema(configurationSchema(schema.data)) : undefined), [schema.data]);
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Extension Grants', to: listPath },
        { label: extensionGrant?.name ?? '…' },
    ]);

    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="Extension grant not found"
                description={`No extension grant with this id exists in ${domain.name}. It may have been deleted.`}
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to extension grants</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the extension grant." error={error} />
        );
    }
    const loadError = schema.error ?? plugins.error;
    if (loadError) {
        return <ErrorPanel description="Could not load the extension grant configuration." error={loadError} />;
    }
    if (!extensionGrant || !adapted || !schema.data || !plugins.data) {
        return <Skeleton className="h-64 w-full" />;
    }

    return (
        <PageFocused>
            <div className="flex flex-col gap-6">
                <div className="space-y-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Extension grants
                        </Link>
                    </Button>
                    <div className="flex flex-wrap items-center gap-2">
                        <h1>{extensionGrant.name}</h1>
                        <Badge variant="outline">{extensionGrantTypeName(extensionGrant.type, plugin?.name ?? extensionGrant.type)}</Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">
                        Extension grants let applications request OAuth 2.0 access tokens with grant types beyond authorization code,
                        implicit, password and client credentials.
                    </p>
                </div>
                <ExtensionGrantForm
                    key={extensionGrant.id}
                    extensionGrant={extensionGrant}
                    adapted={adapted}
                    grantType={extensionGrant.grantType ?? schemaGrantType(schema.data)}
                    feature={plugin?.feature}
                    listPath={listPath}
                />
            </div>
        </PageFocused>
    );
}
