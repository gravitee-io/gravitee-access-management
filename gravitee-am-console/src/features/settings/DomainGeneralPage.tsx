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
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Textarea,
    toast,
} from '@gravitee/graphene-core';
import { XIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import { ValueListInput } from '../../app/components/ValueListInput';
import { listDataPlanes } from '../../lib/api/data-planes';
import { type Domain, deleteDomain, patchDomainGeneralSettings } from '../../lib/api/domains';
import { listTags } from '../../lib/api/tags';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';

const FORM_ID = 'domain-general-form';

type FormState = {
    readonly name: string;
    readonly description: string;
    readonly enabled: boolean;
    readonly master: boolean;
    readonly tags: readonly string[];
    readonly postLogoutRedirectUris: readonly string[];
    readonly requestUris: readonly string[];
};

function fromDomain(domain: Domain): FormState {
    return {
        name: domain.name,
        description: domain.description ?? '',
        enabled: domain.enabled,
        master: domain.master ?? false,
        tags: domain.tags ?? [],
        postLogoutRedirectUris: domain.oidc?.postLogoutRedirectUris ?? [],
        requestUris: domain.oidc?.requestUris ?? [],
    };
}

type Pending = { readonly kind: 'remove-logout-uri' | 'remove-request-uri'; readonly uri: string } | { readonly kind: 'delete' };

const CONFIRM_TEXTS = {
    'remove-logout-uri': {
        title: 'Remove logout redirect URI',
        description: 'Are you sure you want to remove this redirect URI ?',
        confirmLabel: 'Remove',
    },
    'remove-request-uri': {
        title: 'Remove request URI',
        description: 'Are you sure you want to remove this request URI ?',
        confirmLabel: 'Remove',
    },
    delete: { title: 'Delete Domain', description: 'Are you sure you want to delete this domain ?', confirmLabel: 'Delete domain' },
};

export function DomainGeneralPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const { has } = usePermissions();
    const canEdit = has('domain_settings_update');
    const canDelete = has('domain_settings_delete');
    const dataPlanes = useQuery({
        queryKey: ['data-planes', ref.organizationId, ref.environmentId],
        queryFn: () => listDataPlanes(ref.organizationId, ref.environmentId),
    });
    const tags = useQuery({ queryKey: ['tags', ref.organizationId], queryFn: () => listTags(ref.organizationId) });
    const [form, setForm] = useState<FormState>(() => fromDomain(domain));
    // The dialog reads the last action, so its text stays the same while it closes.
    const [pending, setPending] = useState<Pending>({ kind: 'delete' });
    const [confirmOpen, setConfirmOpen] = useState(false);
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'General' },
    ]);

    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        setForm(fromDomain(domain));
    }

    const save = useMutation({
        mutationFn: () =>
            patchDomainGeneralSettings(ref, {
                name: form.name,
                // An untouched empty field keeps the stored value.
                description: form.description === (domain.description ?? '') ? domain.description : form.description,
                path: domain.path,
                enabled: form.enabled,
                tags: form.tags,
                oidc: { postLogoutRedirectUris: form.postLogoutRedirectUris, requestUris: form.requestUris },
                master: form.master,
            }),
        onSuccess: updated => {
            queryClient.setQueryData(['domain', ref.organizationId, ref.environmentId, updated.hrid], updated);
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success(`Domain ${updated.name} updated`);
            // A new name gives a new hrid, so the URL of this page changes.
            if (updated.hrid !== domain.hrid) {
                navigate(`${domainsPath}/${updated.hrid}/settings/general`, { replace: true });
                queryClient.removeQueries({ queryKey: ['domain', ref.organizationId, ref.environmentId, domain.hrid], exact: true });
            }
        },
        onError: error => toast.error(`Could not update the domain. ${error.message}`),
    });

    const remove = useMutation({
        mutationFn: () => deleteDomain(ref),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success(`Domain ${domain.name} deleted`);
            navigate(domainsPath);
        },
        onError: error => toast.error(`Could not delete the domain. ${error.message}`),
    });

    const dirty = JSON.stringify(form) !== JSON.stringify(fromDomain(domain));
    const set = (patch: Partial<FormState>) => setForm(current => ({ ...current, ...patch }));
    const ask = (action: Pending) => {
        setPending(action);
        setConfirmOpen(true);
    };
    const confirm = () => {
        setConfirmOpen(false);
        if (pending.kind === 'remove-logout-uri') {
            set({ postLogoutRedirectUris: form.postLogoutRedirectUris.filter(uri => uri !== pending.uri) });
        } else if (pending.kind === 'remove-request-uri') {
            set({ requestUris: form.requestUris.filter(uri => uri !== pending.uri) });
        } else {
            remove.mutate();
        }
    };
    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (form.name !== '') {
            save.mutate();
        }
    };

    const dataPlane = dataPlanes.data?.find(plane => plane.id === domain.dataPlaneId);
    const dataPlaneName = dataPlane ? `${dataPlane.name} (${dataPlane.id})` : (domain.dataPlaneId ?? '');
    const availableTags = (tags.data ?? []).filter(tag => !form.tags.includes(tag.id));
    const tagName = (id: string) => tags.data?.find(tag => tag.id === id)?.name;
    const confirmTexts = CONFIRM_TEXTS[pending.kind];

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="General"
                description="The security domain's name, description and deployment. Changing the sharding tags makes the domain unreachable from some AM gateways."
            />

            <form id={FORM_ID} className="flex flex-col gap-6" onSubmit={submit}>
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Details</CardTitle>
                    </CardHeader>
                    <CardContent className="grid gap-5">
                        <Field data-invalid={form.name === ''}>
                            <FieldLabel htmlFor="domain-name">Name</FieldLabel>
                            <Input
                                id="domain-name"
                                value={form.name}
                                disabled={!canEdit}
                                aria-invalid={form.name === ''}
                                aria-describedby={form.name === '' ? 'domain-name-error' : undefined}
                                onChange={event => set({ name: event.target.value })}
                            />
                            {form.name === '' ? (
                                <FieldError id="domain-name-error">A name is required.</FieldError>
                            ) : (
                                <FieldDescription>Your domain&apos;s name.</FieldDescription>
                            )}
                        </Field>
                        {(dataPlanes.data?.length ?? 0) > 1 && (
                            <Field>
                                <FieldLabel htmlFor="domain-data-plane">Data plane</FieldLabel>
                                <Input id="domain-data-plane" value={dataPlaneName} disabled readOnly />
                                <FieldDescription>Data plane connected to this domain.</FieldDescription>
                            </Field>
                        )}
                        <Field>
                            <FieldLabel htmlFor="domain-description">Description</FieldLabel>
                            <Textarea
                                id="domain-description"
                                rows={3}
                                value={form.description}
                                disabled={!canEdit}
                                onChange={event => set({ description: event.target.value })}
                            />
                            <FieldDescription>Your domain&apos;s description.</FieldDescription>
                        </Field>
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Status</CardTitle>
                    </CardHeader>
                    <CardContent className="grid gap-5">
                        <SettingToggle
                            id="domain-enabled"
                            label="Enable domain"
                            description="Enable domain to handle incoming authentication and authorization requests."
                            checked={form.enabled}
                            disabled={!canEdit}
                            onChange={enabled => set({ enabled })}
                        />
                        <SettingToggle
                            id="domain-master"
                            label="Master domain"
                            description="A master security domain can introspect tokens from other domains. Useful if you want to dedicate a domain only to verify tokens."
                            checked={form.master}
                            disabled={!canEdit}
                            onChange={master => set({ master })}
                        />
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Post-logout redirect URIs</CardTitle>
                        <CardDescription>
                            Where users may be sent after signing out. Restricting them prevents redirects to unsafe websites.
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        <ValueListInput
                            label="post-logout redirect URI"
                            values={form.postLogoutRedirectUris}
                            editable={canEdit}
                            placeholder="https://myapp.example.com/"
                            emptyText="There are no redirect URIs."
                            onChange={postLogoutRedirectUris => set({ postLogoutRedirectUris })}
                            onDuplicate={uri => toast.error(`Error : redirect URI "${uri}" already exists`)}
                            onRemoveRequest={uri => ask({ kind: 'remove-logout-uri', uri })}
                        />
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Request URIs</CardTitle>
                        <CardDescription>
                            URLs an application may pass as request_uri. Restricting them prevents server-side request forgery.
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        <ValueListInput
                            label="request URI"
                            values={form.requestUris}
                            editable={canEdit}
                            placeholder="https://myapp.example.com/request.jwt"
                            emptyText="There are no request URIs."
                            onChange={requestUris => set({ requestUris })}
                            onDuplicate={uri => toast.error(`Error : request URI "${uri}" already exists`)}
                            onRemoveRequest={uri => ask({ kind: 'remove-request-uri', uri })}
                        />
                    </CardContent>
                </Card>

                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Deployment</CardTitle>
                        <CardDescription>
                            Sharding tags decide which AM gateways serve this domain. With no tag, every gateway serves it.
                        </CardDescription>
                    </CardHeader>
                    <CardContent className="flex flex-col gap-3">
                        {canEdit && (
                            <Select
                                value=""
                                disabled={tags.isPending || !!tags.error || availableTags.length === 0}
                                onValueChange={id => id && set({ tags: [...form.tags, id] })}
                            >
                                <SelectTrigger className="w-full" aria-label="Add a sharding tag">
                                    <SelectValue
                                        placeholder={
                                            tags.isPending
                                                ? 'Loading sharding tags…'
                                                : availableTags.length === 0
                                                  ? 'No other sharding tags available'
                                                  : 'Add a sharding tag'
                                        }
                                    />
                                </SelectTrigger>
                                <SelectContent>
                                    {availableTags.map(tag => (
                                        <SelectItem key={tag.id} value={tag.id}>
                                            {tag.id} | {tag.name}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                        )}
                        {tags.error && <p className="text-sm text-muted-foreground">Could not load the sharding tags.</p>}
                        {form.tags.length === 0 ? (
                            <p className="text-sm text-muted-foreground">No sharding tags. Every gateway serves this domain.</p>
                        ) : (
                            <div className="flex flex-wrap gap-2">
                                {form.tags.map(id => (
                                    <Badge key={id} variant="secondary">
                                        {tagName(id) ? `${id} | ${tagName(id)}` : id}
                                        {canEdit && (
                                            <Button
                                                type="button"
                                                variant="ghost"
                                                size="icon-xs"
                                                aria-label={`Remove sharding tag ${id}`}
                                                onClick={() => set({ tags: form.tags.filter(tag => tag !== id) })}
                                            >
                                                <XIcon aria-hidden />
                                            </Button>
                                        )}
                                    </Badge>
                                ))}
                            </div>
                        )}
                    </CardContent>
                </Card>
            </form>

            {canEdit && (
                <SaveBar
                    formId={FORM_ID}
                    dirty={dirty}
                    saving={save.isPending}
                    invalid={form.name === ''}
                    onDiscard={() => setForm(fromDomain(domain))}
                />
            )}

            {canDelete && (
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Danger zone</CardTitle>
                    </CardHeader>
                    <CardContent>
                        <div className="flex flex-wrap items-center justify-between gap-4">
                            <div className="flex flex-1 flex-col gap-0.5">
                                <p className="text-sm font-medium">Delete domain</p>
                                <p className="text-sm text-muted-foreground">
                                    Deletes the domain and all related data: applications, identity providers, certificates and roles. Its
                                    users will no longer be able to log in. This cannot be undone.
                                </p>
                            </div>
                            <Button variant="destructive" disabled={remove.isPending} onClick={() => ask({ kind: 'delete' })}>
                                Delete
                            </Button>
                        </div>
                    </CardContent>
                </Card>
            )}

            <ConfirmDialog
                open={confirmOpen}
                title={confirmTexts.title}
                description={confirmTexts.description}
                confirmLabel={confirmTexts.confirmLabel}
                destructive={pending.kind === 'delete'}
                onOpenChange={setConfirmOpen}
                onConfirm={confirm}
            />
        </div>
    );
}
