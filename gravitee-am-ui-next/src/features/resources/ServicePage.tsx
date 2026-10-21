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
import { isNotFound } from '../../lib/api/http';
import {
    deleteResource,
    getResource,
    getResourceSchema,
    listResourcePlugins,
    type ResourceDetail,
    updateResource,
} from '../../lib/api/resources';
import { type AdaptedSchema, adaptAmSchema, withSchemaDefaults } from '../../lib/forms/am-schema';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { LicenseGuard } from '../../lib/session/license';
import { Can, usePermissions } from '../../lib/session/permissions';
import { HttpFactorResourceNotes } from './HttpFactorResourceNotes';
import { isHttpFactorResource, resourceTypeName } from './resource-types';

// A type alias, not an interface: Graphene's resolvers constrain the values to `Record<string, unknown>`.
type ServiceValues = {
    name: string;
    config: Record<string, unknown>;
};

const nameResolver: Resolver<ServiceValues> = async (values): Promise<ResolverResult<ServiceValues>> =>
    values.name.trim() ? { values, errors: {} } : { values: {}, errors: { name: { type: 'required', message: 'Name is required' } } };

function formConfiguration(resource: ResourceDetail, adapted: AdaptedSchema): Record<string, unknown> {
    return withSchemaDefaults(adapted.schema, JSON.parse(resource.configuration || '{}') as Record<string, unknown>);
}

interface ServiceFormProps {
    readonly resource: ResourceDetail;
    readonly adapted: AdaptedSchema;
    readonly feature?: string;
    readonly listPath: string;
}

function ServiceForm({ resource, adapted, feature, listPath }: ServiceFormProps) {
    const { ref } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_resource_update');
    const [confirmDelete, setConfirmDelete] = useState(false);
    const resolver = useMemo(
        () => composeResolvers<ServiceValues>(nameResolver, jsonSchemaResolver<ServiceValues>(adapted.schema, { basePath: 'config' })),
        [adapted],
    );
    const defaultValues = useMemo(() => ({ name: resource.name, config: formConfiguration(resource, adapted) }), [resource, adapted]);
    const form = useForm<ServiceValues>({
        resolver,
        mode: 'onTouched',
        criteriaMode: 'all',
        defaultValues,
    });
    const save = useMutation({
        // A field hidden by its condition, or shown again after that, has no form value, where Angular keeps it, so the stored value goes back.
        mutationFn: (values: ServiceValues) =>
            updateResource(ref, resource.id, {
                name: values.name,
                type: resource.type,
                configuration: JSON.stringify({
                    ...JSON.parse(resource.configuration || '{}'),
                    ...Object.fromEntries(Object.entries(values.config).filter(([, value]) => value !== undefined)),
                }),
            }),
        onSuccess: updated => {
            queryClient.setQueryData(['resource', ref, resource.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['resources', ref] });
            form.reset({ name: updated.name, config: formConfiguration(updated, adapted) });
            toast.success('Service updated');
        },
        onError: error => toast.error(`Could not update the service. ${error.message}`),
    });
    const remove = useMutation({
        mutationFn: () => deleteResource(ref, resource.id),
        onSuccess: () => {
            queryClient.removeQueries({ queryKey: ['resource', ref, resource.id] });
            void queryClient.invalidateQueries({ queryKey: ['resources', ref] });
            toast.success('Service deleted');
            navigate(listPath);
        },
        onError: error => toast.error(`Could not delete the service. ${error.message}`),
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
                        <FieldLabel htmlFor="service-name">Name</FieldLabel>
                        <Input id="service-name" aria-invalid={!!nameError} disabled={!canUpdate} {...form.register('name')} />
                        {nameError && <FieldError errors={[nameError]} />}
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
                    <JsonSchemaForm
                        schema={adapted.schema}
                        control={form.control as unknown as Control}
                        name="config"
                        disabled={!canUpdate}
                    />
                </CardContent>
            </Card>

            {isHttpFactorResource(resource.type) && <HttpFactorResourceNotes />}

            <LicenseGuard feature={feature}>
                {/* An optional field registers as undefined, and isDirty then counts it against the stored configuration. */}
                <SaveBar
                    canEdit={canUpdate}
                    dirty={Object.keys(form.formState.dirtyFields).length > 0}
                    saving={save.isPending}
                    // A hidden field also loses its default value, so Discard resets to the stored configuration.
                    onDiscard={() => form.reset(defaultValues)}
                />
            </LicenseGuard>

            <Can all={['domain_resource_delete']}>
                <Card className="border-destructive/30">
                    <CardHeader>
                        <CardTitle className="text-base">Danger zone</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="flex flex-wrap items-center justify-between gap-4">
                            <div className="flex flex-col gap-0.5">
                                <p className="text-sm font-medium">Delete service</p>
                                <p className="text-sm text-muted-foreground">
                                    The service will be deleted and will not be available anymore.
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
                title="Delete service"
                description="Are you sure you want to delete this service?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={setConfirmDelete}
                onConfirm={() => remove.mutate()}
            />
        </form>
    );
}

/** The Angular `ResourceComponent`: name, plugin configuration, save and delete. */
export function ServicePage() {
    const { resourceId = '' } = useParams();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const listPath = `${basePath}/settings/resources`;
    const { data: resource, error } = useQuery({ queryKey: ['resource', ref, resourceId], queryFn: () => getResource(ref, resourceId) });
    const plugins = useQuery({ queryKey: ['resource-plugins'], queryFn: listResourcePlugins, staleTime: Infinity });
    const schema = useQuery({
        queryKey: ['resource-schema', resource?.type],
        queryFn: () => getResourceSchema(resource!.type),
        enabled: resource !== undefined,
        staleTime: Infinity,
    });
    const plugin = plugins.data?.find(candidate => candidate.id === resource?.type);
    const adapted = useMemo(() => (schema.data ? adaptAmSchema(schema.data) : undefined), [schema.data]);
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Services', to: listPath },
        { label: resource?.name ?? '…' },
    ]);

    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="Service not found"
                description={`No service with this id exists in ${domain.name}. It may have been deleted.`}
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to services</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the service." error={error} />
        );
    }
    const loadError = schema.error ?? plugins.error;
    if (loadError) {
        return <ErrorPanel description="Could not load the service configuration." error={loadError} />;
    }
    if (!resource || !adapted || !plugins.data) {
        return <Skeleton className="h-64 w-full" />;
    }

    return (
        <PageFocused>
            <div className="flex flex-col gap-6">
                <div className="space-y-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Services
                        </Link>
                    </Button>
                    <div className="flex flex-wrap items-center gap-2">
                        <h1>{resource.name}</h1>
                        <Badge variant="outline">{resourceTypeName(resource.type, plugin?.name)}</Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">A service is a configuration facility to reuse some settings.</p>
                </div>
                <ServiceForm key={resource.id} resource={resource} adapted={adapted} feature={plugin?.feature} listPath={listPath} />
            </div>
        </PageFocused>
    );
}
