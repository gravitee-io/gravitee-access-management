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
    Skeleton,
    toast,
} from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { type Control, type Resolver, type ResolverResult, useForm } from 'react-hook-form';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { SaveBar } from '../../app/components/SaveBar';
import {
    type DeviceIdentifier,
    deleteDeviceIdentifier,
    getDeviceIdentifier,
    getDeviceIdentifierSchema,
    listDeviceIdentifierPlugins,
    updateDeviceIdentifier,
} from '../../lib/api/device-identifier';
import { isNotFound } from '../../lib/api/http';
import { type AdaptedSchema, adaptAmSchema, withSchemaDefaults } from '../../lib/forms/am-schema';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { LicenseGuard } from '../../lib/session/license';
import { Can, usePermissions } from '../../lib/session/permissions';
import { deviceIdentifierTypeName, hasNoSettings } from './device-identifier-types';

// A type alias, not an interface: Graphene's resolvers constrain the values to `Record<string, unknown>`.
type DeviceIdentifierValues = {
    name: string;
    config: Record<string, unknown>;
};

const nameResolver: Resolver<DeviceIdentifierValues> = async (values): Promise<ResolverResult<DeviceIdentifierValues>> =>
    values.name.trim() ? { values, errors: {} } : { values: {}, errors: { name: { type: 'required', message: 'Name is required' } } };

function parseConfiguration(deviceIdentifier: DeviceIdentifier): Record<string, unknown> {
    return JSON.parse(deviceIdentifier.configuration || '{}') as Record<string, unknown>;
}

interface DeviceIdentifierFormProps {
    readonly deviceIdentifier: DeviceIdentifier;
    readonly adapted: AdaptedSchema;
    readonly noSettings: boolean;
    readonly feature?: string;
    readonly listPath: string;
}

function DeviceIdentifierForm({ deviceIdentifier, adapted, noSettings, feature, listPath }: DeviceIdentifierFormProps) {
    const { ref } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_device_identifier_update');
    const [confirmDelete, setConfirmDelete] = useState(false);
    const resolver = useMemo(
        () =>
            composeResolvers<DeviceIdentifierValues>(
                nameResolver,
                jsonSchemaResolver<DeviceIdentifierValues>(adapted.schema, { basePath: 'config' }),
            ),
        [adapted],
    );
    const defaultValues = useMemo(
        () => ({ name: deviceIdentifier.name, config: withSchemaDefaults(adapted.schema, parseConfiguration(deviceIdentifier)) }),
        [deviceIdentifier, adapted],
    );
    const form = useForm<DeviceIdentifierValues>({ resolver, mode: 'onTouched', criteriaMode: 'all', defaultValues });
    const save = useMutation({
        mutationFn: (values: DeviceIdentifierValues) =>
            updateDeviceIdentifier(ref, deviceIdentifier.id, {
                name: values.name,
                type: deviceIdentifier.type,
                configuration: JSON.stringify(values.config),
            }),
        onSuccess: updated => {
            queryClient.setQueryData(['device-identifier', ref, deviceIdentifier.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['device-identifiers', ref] });
            form.reset({ name: updated.name, config: withSchemaDefaults(adapted.schema, parseConfiguration(updated)) });
            toast.success('Device identifier updated');
        },
        onError: error => toast.error(`Could not update the device identifier. ${error.message}`),
    });
    const remove = useMutation({
        mutationFn: () => deleteDeviceIdentifier(ref, deviceIdentifier.id),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['device-identifiers', ref] });
            toast.success('Device identifier deleted');
            navigate(listPath);
        },
        onError: error => toast.error(`Could not delete the device identifier. ${error.message}`),
    });
    const nameError = form.formState.errors.name;

    return (
        <form noValidate onSubmit={form.handleSubmit(values => save.mutate(values))} className="flex flex-col gap-6">
            <Card>
                <CardHeader>
                    <CardTitle className="text-base">General</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    <Field data-invalid={!!nameError}>
                        <FieldLabel htmlFor="device-identifier-name">Name</FieldLabel>
                        <Input id="device-identifier-name" aria-invalid={!!nameError} disabled={!canUpdate} {...form.register('name')} />
                        {nameError && <FieldError errors={[nameError]} />}
                    </Field>
                    <Field>
                        <FieldLabel htmlFor="device-identifier-type">Type</FieldLabel>
                        <Input id="device-identifier-type" value={deviceIdentifierTypeName(deviceIdentifier.type)} disabled />
                        <FieldDescription>Device identifier type</FieldDescription>
                    </Field>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Configuration</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    {adapted.unsupported.length > 0 && (
                        <Alert>
                            <AlertTitle>Some fields use a plain input</AlertTitle>
                            <AlertDescription>{adapted.unsupported.join(', ')}</AlertDescription>
                        </Alert>
                    )}
                    {noSettings ? (
                        <p className="text-sm text-muted-foreground">This plugin has no settings to configure.</p>
                    ) : (
                        <JsonSchemaForm
                            schema={adapted.schema}
                            control={form.control as unknown as Control}
                            name="config"
                            disabled={!canUpdate}
                        />
                    )}
                </CardContent>
            </Card>

            <LicenseGuard feature={feature}>
                <SaveBar canEdit={canUpdate} dirty={form.formState.isDirty} saving={save.isPending} onDiscard={() => form.reset()} />
            </LicenseGuard>

            <Can all={['domain_device_identifier_delete']}>
                <Card className="border-destructive/30">
                    <CardHeader>
                        <CardTitle className="text-base">Danger zone</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="flex flex-wrap items-center justify-between gap-4">
                            <div className="flex flex-col gap-0.5">
                                <p className="text-sm font-medium">Delete device identifier</p>
                                <p className="text-sm text-muted-foreground">
                                    Applications will no longer be able to use this device identifier plugin.
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
                title="Delete device identifier"
                description="Are you sure you want to delete this device identifier?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={setConfirmDelete}
                onConfirm={() => remove.mutate()}
            />
        </form>
    );
}

/** The Angular `DeviceIdentifierComponent`: name, read-only type, plugin configuration, save and delete. */
export function DeviceIdentifierPage() {
    const { deviceIdentifierId = '' } = useParams();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const listPath = `${basePath}/settings/device-identifier`;
    const { data: deviceIdentifier, error } = useQuery({
        queryKey: ['device-identifier', ref, deviceIdentifierId],
        queryFn: () => getDeviceIdentifier(ref, deviceIdentifierId),
    });
    const plugins = useQuery({ queryKey: ['device-identifier-plugins'], queryFn: listDeviceIdentifierPlugins, staleTime: Infinity });
    const schema = useQuery({
        queryKey: ['device-identifier-schema', deviceIdentifier?.type],
        queryFn: () => getDeviceIdentifierSchema(deviceIdentifier!.type),
        enabled: deviceIdentifier !== undefined,
        staleTime: Infinity,
    });
    const plugin = plugins.data?.find(candidate => candidate.id === deviceIdentifier?.type);
    const adapted = useMemo(() => (schema.data ? adaptAmSchema(schema.data) : undefined), [schema.data]);
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Device Identifier', to: listPath },
        { label: deviceIdentifier?.name ?? '…' },
    ]);

    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="Device identifier not found"
                description="This device identifier does not exist or has been deleted."
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to device identifiers</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the device identifier." error={error} />
        );
    }
    const loadError = schema.error ?? plugins.error;
    if (loadError) {
        return <ErrorPanel description="Could not load the device identifier configuration." error={loadError} />;
    }
    if (!deviceIdentifier || !adapted || !schema.data || !plugins.data) {
        return <Skeleton className="h-64 w-full" />;
    }

    return (
        <PageFocused>
            <div className="flex flex-col gap-6">
                <div className="space-y-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Device identifiers
                        </Link>
                    </Button>
                    <div className="flex flex-wrap items-center gap-2">
                        <h1>{deviceIdentifier.name}</h1>
                        <Badge variant="outline">{deviceIdentifierTypeName(deviceIdentifier.type)}</Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">
                        The device identifier plugin lets you remember the devices of your users for multi-factor authentication.
                    </p>
                </div>
                <DeviceIdentifierForm
                    key={deviceIdentifier.id}
                    deviceIdentifier={deviceIdentifier}
                    adapted={adapted}
                    noSettings={hasNoSettings(schema.data)}
                    feature={plugin?.feature}
                    listPath={listPath}
                />
            </div>
        </PageFocused>
    );
}
