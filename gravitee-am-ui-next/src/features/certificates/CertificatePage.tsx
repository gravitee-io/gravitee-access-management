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
import {
    type CertificateDetail,
    deleteCertificate,
    getCertificate,
    getCertificateSchema,
    listCertificatePlugins,
    updateCertificate,
} from '../../lib/api/certificates';
import { isNotFound } from '../../lib/api/http';
import { type AdaptedSchema, adaptAmSchema } from '../../lib/forms/am-schema';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { LicenseGuard } from '../../lib/session/license';
import { Can, usePermissions } from '../../lib/session/permissions';
import { certificateTypeName } from './certificate-types';

// A type alias, not an interface: Graphene's resolvers constrain the values to `Record<string, unknown>`.
type CertificateValues = {
    name: string;
    config: Record<string, unknown>;
};

const nameResolver: Resolver<CertificateValues> = async (values): Promise<ResolverResult<CertificateValues>> =>
    values.name.trim() ? { values, errors: {} } : { values: {}, errors: { name: { type: 'required', message: 'Name is required' } } };

function parseConfiguration(certificate: CertificateDetail): Record<string, unknown> {
    return JSON.parse(certificate.configuration || '{}') as Record<string, unknown>;
}

interface CertificateFormProps {
    readonly certificate: CertificateDetail;
    /** Absent for a system certificate, whose configuration Angular hides. */
    readonly adapted?: AdaptedSchema;
    readonly feature?: string;
    readonly listPath: string;
}

function CertificateForm({ certificate, adapted, feature, listPath }: CertificateFormProps) {
    const { ref } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_certificate_update');
    const [confirmDelete, setConfirmDelete] = useState(false);
    const resolver = useMemo(
        () =>
            adapted
                ? composeResolvers<CertificateValues>(
                      nameResolver,
                      jsonSchemaResolver<CertificateValues>(adapted.schema, { basePath: 'config' }),
                  )
                : nameResolver,
        [adapted],
    );
    const form = useForm<CertificateValues>({
        resolver,
        mode: 'onTouched',
        criteriaMode: 'all',
        defaultValues: { name: certificate.name, config: parseConfiguration(certificate) },
    });
    const save = useMutation({
        mutationFn: (values: CertificateValues) =>
            updateCertificate(ref, certificate.id, {
                name: values.name,
                type: certificate.type,
                configuration: JSON.stringify(values.config),
            }),
        onSuccess: updated => {
            queryClient.setQueryData(['certificate', ref, certificate.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['certificates', ref] });
            form.reset({ name: updated.name, config: parseConfiguration(updated) });
            toast.success('Certificate successfully updated');
        },
        onError: error => toast.error(`Could not update the certificate. ${error.message}`),
    });
    const remove = useMutation({
        mutationFn: () => deleteCertificate(ref, certificate.id),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['certificates', ref] });
            toast.success('Certificate deleted');
            navigate(listPath);
        },
        onError: error => toast.error(`Could not delete the certificate. ${error.message}`),
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
                        <FieldLabel htmlFor="certificate-name">Name</FieldLabel>
                        <Input id="certificate-name" aria-invalid={!!nameError} disabled={!canUpdate} {...form.register('name')} />
                        {nameError && <FieldError errors={[nameError]} />}
                    </Field>
                </CardContent>
            </Card>

            {adapted && (
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
            )}

            <LicenseGuard feature={feature}>
                <SaveBar canEdit={canUpdate} dirty={form.formState.isDirty} saving={save.isPending} onDiscard={() => form.reset()} />
            </LicenseGuard>

            <Can all={['domain_certificate_delete']}>
                <Card className="border-destructive/30">
                    <CardHeader>
                        <CardTitle className="text-base">Danger zone</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="flex flex-wrap items-center justify-between gap-4">
                            <div className="flex flex-col gap-0.5">
                                <p className="text-sm font-medium">Delete certificate</p>
                                <p className="text-sm text-muted-foreground">
                                    Applications will no longer be able to use this certificate to sign tokens.
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
                title="Delete certificate"
                description="Are you sure you want to delete this certificate?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={setConfirmDelete}
                onConfirm={() => remove.mutate()}
            />
        </form>
    );
}

/** The Angular `CertificateComponent`: name, plugin configuration, save and delete. A system certificate shows its name only. */
export function CertificatePage() {
    const { certificateId = '' } = useParams();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const listPath = `${basePath}/settings/certificates`;
    const { data: certificate, error } = useQuery({
        queryKey: ['certificate', ref, certificateId],
        queryFn: () => getCertificate(ref, certificateId),
    });
    const plugins = useQuery({ queryKey: ['certificate-plugins'], queryFn: listCertificatePlugins, staleTime: Infinity });
    const editable = certificate !== undefined && !certificate.system;
    const schema = useQuery({
        queryKey: ['certificate-schema', certificate?.type],
        queryFn: () => getCertificateSchema(certificate!.type),
        enabled: editable,
        staleTime: Infinity,
    });
    const adapted = useMemo(() => (schema.data ? adaptAmSchema(schema.data) : undefined), [schema.data]);
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Certificates', to: listPath },
        { label: certificate?.name ?? '…' },
    ]);

    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="Certificate not found"
                description={`No certificate with this id exists in ${domain.name}. It may have been deleted.`}
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to certificates</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the certificate." error={error} />
        );
    }
    const loadError = schema.error ?? plugins.error;
    if (loadError) {
        return <ErrorPanel description="Could not load the certificate configuration." error={loadError} />;
    }
    if (!certificate || !plugins.data || (editable && !adapted)) {
        return <Skeleton className="h-64 w-full" />;
    }

    return (
        <PageFocused>
            <div className="flex flex-col gap-6">
                <div className="space-y-1">
                    <Button asChild variant="ghost" size="sm" className="-ml-2">
                        <Link to={listPath}>
                            <ArrowLeftIcon aria-hidden />
                            Certificates
                        </Link>
                    </Button>
                    <div className="flex flex-wrap items-center gap-2">
                        <h1>{certificate.name}</h1>
                        <Badge variant="outline">{certificateTypeName(certificate.type)}</Badge>
                        {certificate.system && <Badge variant="secondary">System</Badge>}
                    </div>
                    <p className="text-sm text-muted-foreground">Configure your certificate to ensure data integrity.</p>
                </div>
                <CertificateForm
                    key={certificate.id}
                    certificate={certificate}
                    adapted={editable ? adapted : undefined}
                    feature={plugins.data.find(plugin => plugin.id === certificate.type)?.feature}
                    listPath={listPath}
                />
            </div>
        </PageFocused>
    );
}
