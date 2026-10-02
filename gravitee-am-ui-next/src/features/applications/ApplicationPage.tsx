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
    Textarea,
    toast,
} from '@gravitee/graphene-core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ReactNode } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Outlet, useOutletContext, useParams } from 'react-router-dom';
import { PageHeader } from '../../app/components/PageHeader';
import { APPLICATION_NAV_GROUPS } from '../../app/navigation';
import { type ApplicationDetail, getApplication, patchApplication } from '../../lib/api/management-api';
import { useSectionSidebar } from '../../lib/layout/useSectionSidebar';
import { useCurrentDomain } from '../../lib/session/domain';
import { FlowsPage } from '../flows/FlowsPage';
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

/** Loads the application and shows its sections in the context sidebar. */
export function ApplicationLayout() {
    const { applicationId = '' } = useParams();
    const { ref } = useCurrentDomain();
    const { data: application, error } = useQuery({
        queryKey: ['application', ref, applicationId],
        queryFn: () => getApplication(ref, applicationId),
    });

    if (error) {
        return <p className="text-sm text-destructive">Could not load the application.</p>;
    }
    if (!application) {
        return <Spinner />;
    }
    return <ApplicationSections application={application} />;
}

function ApplicationSections({ application }: { readonly application: ApplicationDetail }) {
    const { domain, basePath, domainsPath } = useCurrentDomain();
    useSectionSidebar({
        title: application.name,
        badges: [APPLICATION_TYPE_LABELS[application.type] ?? application.type, application.enabled ? 'Enabled' : 'Disabled'],
        groups: APPLICATION_NAV_GROUPS,
        base: `${basePath}/applications/${application.id}`,
        crumbs: [
            { label: 'Domains', to: domainsPath },
            { label: domain.name, to: `${basePath}/dashboard` },
            { label: 'Applications', to: `${basePath}/applications` },
            { label: application.name, to: `${basePath}/applications/${application.id}/overview` },
        ],
    });
    return <Outlet context={application} />;
}

export function ApplicationOverviewPage() {
    const application = useOutletContext<ApplicationDetail>();
    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Overview" description={application.description} />
            <Overview application={application} />
        </div>
    );
}

export function ApplicationGeneralPage() {
    const application = useOutletContext<ApplicationDetail>();
    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="General" description="The application's name, description and status." />
            <Settings application={application} />
        </div>
    );
}

const TOKEN_ONLY = ['token'];

/** Service applications never show a login page, so AM only lets them edit the token flow. */
export function ApplicationFlowsPage() {
    const application = useOutletContext<ApplicationDetail>();
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const inherit = useMutation({
        mutationFn: (flowsInherited: boolean) => patchApplication(ref, application.id, { settings: { advanced: { flowsInherited } } }),
        onSuccess: updated => {
            queryClient.setQueryData(['application', ref, application.id], updated);
            toast.success('Application saved.');
        },
        onError: error => toast.error(`Could not save the application. ${error.message}`),
    });
    const tokenOnly = application.type === 'service';

    return (
        <FlowsPage
            applicationId={application.id}
            types={tokenOnly ? TOKEN_ONLY : undefined}
            description={
                tokenOnly
                    ? 'Policies that run around token issuance for this application.'
                    : 'Policies that run before and after each authentication step for this application.'
            }
            actions={
                <Field orientation="horizontal" className="w-auto">
                    <Switch
                        id="flows-inherited"
                        checked={application.settings?.advanced?.flowsInherited ?? false}
                        disabled={inherit.isPending}
                        onCheckedChange={checked => inherit.mutate(checked)}
                    />
                    <FieldLabel htmlFor="flows-inherited" className="flex-col items-start gap-0.5">
                        Inherit configuration
                        <span className="text-xs font-normal text-muted-foreground">Also run the flows of the security domain.</span>
                    </FieldLabel>
                </Field>
            }
        />
    );
}
