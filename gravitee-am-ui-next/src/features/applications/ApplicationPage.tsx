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
    Badge,
    Button,
    CopyableCell,
    Field,
    FieldError,
    FieldLabel,
    Input,
    Spinner,
    Switch,
    Tabs,
    TabsContent,
    TabsList,
    TabsTrigger,
    Textarea,
    toast,
} from '@gravitee/graphene-core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { useParams } from 'react-router-dom';
import { PageHeader } from '../../app/components/PageHeader';
import { type ApplicationDetail, getApplication, patchApplication } from '../../lib/api/management-api';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { APPLICATION_TYPE_LABELS } from './application-types';

function Property({ label, children }: { readonly label: string; readonly children: ReactNode }) {
    return (
        <div className="grid grid-cols-[12rem_1fr] items-start gap-4 py-3">
            <dt className="text-sm text-muted-foreground">{label}</dt>
            <dd className="text-sm">{children}</dd>
        </div>
    );
}

function Overview({ application }: { readonly application: ApplicationDetail }) {
    const oauth = application.settings?.oauth;
    return (
        <dl className="divide-y divide-border rounded-lg px-4 ring-1 ring-foreground/10">
            <Property label="Client ID">
                <CopyableCell value={oauth?.clientId} maxLength={48} />
            </Property>
            <Property label="Grant types">
                <div className="flex flex-wrap gap-1">
                    {(oauth?.grantTypes ?? []).map(grant => (
                        <Badge key={grant} variant="outline">
                            {grant}
                        </Badge>
                    ))}
                </div>
            </Property>
            <Property label="Redirect URIs">
                {oauth?.redirectUris?.length ? (
                    <ul className="flex flex-col gap-1 font-mono text-xs">
                        {oauth.redirectUris.map(uri => (
                            <li key={uri}>{uri}</li>
                        ))}
                    </ul>
                ) : (
                    <span className="text-muted-foreground">—</span>
                )}
            </Property>
        </dl>
    );
}

interface SettingsValues {
    readonly name: string;
    readonly description: string;
    readonly enabled: boolean;
}

function Settings({ application }: { readonly application: ApplicationDetail }) {
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const form = useForm<SettingsValues>({
        defaultValues: { name: application.name, description: application.description ?? '', enabled: application.enabled },
    });
    const save = useMutation({
        mutationFn: (values: SettingsValues) => patchApplication(ref, application.id, values),
        onSuccess: updated => {
            queryClient.setQueryData(['application', ref, application.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['applications', ref] });
            form.reset({ name: updated.name, description: updated.description ?? '', enabled: updated.enabled });
            toast.success('Application saved.');
        },
        onError: error => toast.error(`Could not save the application. ${error.message}`),
    });
    const nameError = form.formState.errors.name;

    return (
        <form noValidate onSubmit={form.handleSubmit(values => save.mutate(values))} className="flex max-w-2xl flex-col gap-4">
            <Field data-invalid={!!nameError}>
                <FieldLabel htmlFor="name">Name</FieldLabel>
                <Input id="name" aria-invalid={!!nameError} {...form.register('name', { required: 'Name is required' })} />
                {nameError && <FieldError errors={[nameError]} />}
            </Field>
            <Field>
                <FieldLabel htmlFor="description">Description</FieldLabel>
                <Textarea id="description" rows={3} {...form.register('description')} />
            </Field>
            <Field orientation="horizontal">
                <Controller
                    control={form.control}
                    name="enabled"
                    render={({ field }) => <Switch id="enabled" checked={field.value} onCheckedChange={field.onChange} />}
                />
                <FieldLabel htmlFor="enabled">Enabled</FieldLabel>
            </Field>
            <div>
                <Button type="submit" disabled={!form.formState.isDirty || save.isPending}>
                    Save
                </Button>
            </div>
        </form>
    );
}

export function ApplicationPage() {
    const { applicationId = '' } = useParams();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { data: application, error } = useQuery({
        queryKey: ['application', ref, applicationId],
        queryFn: () => getApplication(ref, applicationId),
    });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: basePath },
        { label: 'Applications', to: `${basePath}/applications` },
        { label: application?.name ?? '…' },
    ]);

    if (error) {
        return <p className="text-sm text-destructive">Could not load the application.</p>;
    }
    if (!application) {
        return <Spinner />;
    }
    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title={
                    <span className="flex items-center gap-2">
                        {application.name}
                        <Badge variant="outline">{APPLICATION_TYPE_LABELS[application.type] ?? application.type}</Badge>
                    </span>
                }
                description={application.description}
            />
            <Tabs defaultValue="overview">
                <TabsList>
                    <TabsTrigger value="overview">Overview</TabsTrigger>
                    <TabsTrigger value="settings">Settings</TabsTrigger>
                </TabsList>
                <TabsContent value="overview" className="pt-4">
                    <Overview application={application} />
                </TabsContent>
                <TabsContent value="settings" className="pt-4">
                    <Settings application={application} />
                </TabsContent>
            </Tabs>
        </div>
    );
}
