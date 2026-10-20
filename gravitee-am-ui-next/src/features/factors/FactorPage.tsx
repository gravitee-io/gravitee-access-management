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
import { deleteFactor, type Factor, getFactor, getFactorSchema, listFactorPlugins, updateFactor } from '../../lib/api/factors';
import { isNotFound } from '../../lib/api/http';
import { listResourcePlugins, listResourcesWhenPermitted } from '../../lib/api/resources';
import { type AdaptedSchema, adaptAmSchema } from '../../lib/forms/am-schema';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { LicenseGuard } from '../../lib/session/license';
import { Can, usePermissions } from '../../lib/session/permissions';
import { isFido2, schemaFactorType, withResourceSelect, withSchemaDefaults } from './factor-schema';

// A type alias, not an interface: Graphene's resolvers constrain the values to `Record<string, unknown>`.
type FactorValues = {
    name: string;
    config: Record<string, unknown>;
};

const nameResolver: Resolver<FactorValues> = async (values): Promise<ResolverResult<FactorValues>> =>
    values.name.trim() ? { values, errors: {} } : { values: {}, errors: { name: { type: 'required', message: 'Name is required' } } };

function formConfiguration(factor: Factor, adapted: AdaptedSchema): Record<string, unknown> {
    return withSchemaDefaults(adapted.schema, JSON.parse(factor.configuration || '{}') as Record<string, unknown>);
}

interface FactorFormProps {
    readonly factor: Factor;
    readonly factorType: string;
    readonly adapted: AdaptedSchema;
    readonly feature?: string;
    readonly listPath: string;
}

function FactorForm({ factor, factorType, adapted, feature, listPath }: FactorFormProps) {
    const { ref } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_factor_update');
    const [confirmDelete, setConfirmDelete] = useState(false);
    const resolver = useMemo(
        () => composeResolvers<FactorValues>(nameResolver, jsonSchemaResolver<FactorValues>(adapted.schema, { basePath: 'config' })),
        [adapted],
    );
    const defaultValues = useMemo(() => ({ name: factor.name, config: formConfiguration(factor, adapted) }), [factor, adapted]);
    const form = useForm<FactorValues>({
        resolver,
        mode: 'onTouched',
        criteriaMode: 'all',
        defaultValues,
    });
    const save = useMutation({
        mutationFn: (values: FactorValues) =>
            updateFactor(ref, factor.id, { name: values.name, type: factor.type, configuration: JSON.stringify(values.config) }),
        onSuccess: updated => {
            queryClient.setQueryData(['factor', ref, factor.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['factors', ref] });
            form.reset({ name: updated.name, config: formConfiguration(updated, adapted) });
            toast.success('Factor updated');
        },
        onError: error => toast.error(`Could not update the factor. ${error.message}`),
    });
    const remove = useMutation({
        mutationFn: () => deleteFactor(ref, factor.id),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['factors', ref] });
            toast.success('Factor deleted');
            navigate(listPath);
        },
        onError: error => toast.error(`Could not delete the factor. ${error.message}`),
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
                        <FieldLabel htmlFor="factor-name">Name</FieldLabel>
                        <Input id="factor-name" aria-invalid={!!nameError} disabled={!canUpdate} {...form.register('name')} />
                        {nameError && <FieldError errors={[nameError]} />}
                    </Field>
                    <Field>
                        <FieldLabel htmlFor="factor-type">Factor type</FieldLabel>
                        <Input id="factor-type" value={factorType} disabled />
                        <FieldDescription>Type of the factor</FieldDescription>
                    </Field>
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Configuration</CardTitle>
                    {isFido2(factor.type) && (
                        <CardDescription>This FIDO2 factor plugin is using existing WebAuthn configuration.</CardDescription>
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

            <Can all={['domain_factor_delete']}>
                <Card className="border-destructive/30">
                    <CardHeader>
                        <CardTitle className="text-base">Danger zone</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="flex flex-wrap items-center justify-between gap-4">
                            <div className="flex flex-col gap-0.5">
                                <p className="text-sm font-medium">Delete factor</p>
                                <p className="text-sm text-muted-foreground">Applications will no longer be able to use this factor.</p>
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
                title="Delete factor"
                description="Are you sure you want to delete this factor?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={setConfirmDelete}
                onConfirm={() => remove.mutate()}
            />
        </form>
    );
}

/** The Angular `FactorComponent`: name, read-only factor type, plugin configuration, save and delete. */
export function FactorPage() {
    const { factorId = '' } = useParams();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const listPath = `${basePath}/settings/factors`;
    const { data: factor, error } = useQuery({ queryKey: ['factor', ref, factorId], queryFn: () => getFactor(ref, factorId) });
    const plugins = useQuery({ queryKey: ['factor-plugins'], queryFn: listFactorPlugins, staleTime: Infinity });
    const resources = useQuery({ queryKey: ['factor-resources', ref], queryFn: () => listResourcesWhenPermitted(ref) });
    const resourcePlugins = useQuery({ queryKey: ['resource-plugins'], queryFn: listResourcePlugins, staleTime: Infinity });
    const schema = useQuery({
        queryKey: ['factor-schema', factor?.type],
        queryFn: () => getFactorSchema(factor!.type),
        enabled: factor !== undefined,
        staleTime: Infinity,
    });
    const plugin = plugins.data?.find(candidate => candidate.id === factor?.type);
    const adapted = useMemo(
        () =>
            schema.data && plugins.data && resources.data && resourcePlugins.data
                ? adaptAmSchema(withResourceSelect(schema.data, plugin?.category, resources.data, resourcePlugins.data))
                : undefined,
        [schema.data, plugins.data, plugin?.category, resources.data, resourcePlugins.data],
    );
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Multifactor Auth', to: listPath },
        { label: factor?.name ?? '…' },
    ]);

    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="Factor not found"
                description={`No factor with this id exists in ${domain.name}. It may have been deleted.`}
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to factors</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the factor." error={error} />
        );
    }
    const loadError = schema.error ?? plugins.error ?? resourcePlugins.error;
    if (loadError) {
        return <ErrorPanel description="Could not load the factor configuration." error={loadError} />;
    }
    if (!factor || !adapted) {
        return <Skeleton className="h-64 w-full" />;
    }

    return (
        <PageFocused>
            <div className="flex flex-col gap-6">
                <div className="space-y-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Multi-factor authentication
                        </Link>
                    </Button>
                    <div className="flex flex-wrap items-center gap-2">
                        <h1>{factor.name}</h1>
                        <Badge variant="outline">{plugin?.name ?? factor.type}</Badge>
                    </div>
                    <p className="text-sm text-muted-foreground">
                        Multi-factor authentication is an additional step during the login flow in order to enforce access control.
                    </p>
                </div>
                <FactorForm
                    key={factor.id}
                    factor={factor}
                    factorType={(schema.data && schemaFactorType(schema.data)) ?? factor.factorType ?? ''}
                    adapted={adapted}
                    feature={plugin?.feature}
                    listPath={listPath}
                />
            </div>
        </PageFocused>
    );
}
