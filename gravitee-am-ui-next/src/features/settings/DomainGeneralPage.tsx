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
    FieldLabel,
    Input,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Switch,
    Textarea,
    toast,
} from '@gravitee/graphene-core';
import { XIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { ValueListInput } from '../../app/components/ValueListInput';
import { listDataPlanes } from '../../lib/api/data-planes';
import { type Domain, deleteDomain, patchDomainGeneralSettings } from '../../lib/api/domains';
import { listTags } from '../../lib/api/tags';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';

interface FormState {
    readonly name: string;
    readonly description: string;
    readonly enabled: boolean;
    readonly master: boolean;
    readonly tags: readonly string[];
    readonly postLogoutRedirectUris: readonly string[];
    readonly requestUris: readonly string[];
}

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

/**
 * The Angular `DomainSettingsGeneralComponent` (Settings › General): name, description, enable and master flags, the
 * domain-wide post-logout and request URIs, sharding tags, and deleting the domain. Saved in one PATCH with the fields
 * of `DomainService.patchGeneralSettings`.
 */
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
    // The dialog text reads the last action, so it stays the same while the dialog closes.
    const [pending, setPending] = useState<Pending | null>(null);
    const [confirmOpen, setConfirmOpen] = useState(false);
    const ask = (action: Pending) => {
        setPending(action);
        setConfirmOpen(true);
    };
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'General' },
    ]);

    // A save or a refetch gives a new domain object. The form then starts again from it.
    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        setForm(fromDomain(domain));
    }

    const dirty = JSON.stringify(form) !== JSON.stringify(fromDomain(domain));
    const set = (patch: Partial<FormState>) => setForm(current => ({ ...current, ...patch }));
    const dataPlane = dataPlanes.data?.find(plane => plane.id === domain.dataPlaneId);
    const dataPlaneName = dataPlane ? `${dataPlane.name} (${dataPlane.id})` : (domain.dataPlaneId ?? '');
    const availableTags = (tags.data ?? []).filter(tag => !form.tags.includes(tag.id));
    const tagName = (id: string) => tags.data?.find(tag => tag.id === id)?.name;

    const save = useMutation({
        mutationFn: () =>
            patchDomainGeneralSettings(ref, {
                name: form.name,
                // An untouched empty field keeps the stored value, as the Angular form sends domain.description.
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
        onError: error => toast.error(error.message || 'Unable to update the domain'),
    });

    const remove = useMutation({
        mutationFn: () => deleteDomain(ref),
        onSuccess: () => {
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success(`Domain ${domain.name} deleted`);
            navigate(domainsPath);
        },
        onError: error => toast.error(error.message || 'Unable to delete the domain'),
    });

    const confirm = () => {
        if (!pending) {
            return;
        }
        setConfirmOpen(false);
        if (pending.kind === 'remove-logout-uri') {
            set({ postLogoutRedirectUris: form.postLogoutRedirectUris.filter(uri => uri !== pending.uri) });
        } else if (pending.kind === 'remove-request-uri') {
            set({ requestUris: form.requestUris.filter(uri => uri !== pending.uri) });
        } else {
            remove.mutate();
        }
    };

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="General"
                description="The security domain's name, description and deployment. Changing the sharding tags makes the domain unreachable from some AM gateways."
            />

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Details</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    <Field>
                        <FieldLabel htmlFor="domain-name">Name</FieldLabel>
                        <Input
                            id="domain-name"
                            value={form.name}
                            disabled={!canEdit}
                            aria-invalid={form.name === ''}
                            onChange={event => set({ name: event.target.value })}
                        />
                        <FieldDescription>Your domain&apos;s name.</FieldDescription>
                    </Field>
                    {(dataPlanes.data?.length ?? 0) > 1 ? (
                        <Field>
                            <FieldLabel htmlFor="domain-data-plane">Data plane</FieldLabel>
                            <Input id="domain-data-plane" value={dataPlaneName} disabled readOnly />
                            <FieldDescription>Data plane connected to this domain.</FieldDescription>
                        </Field>
                    ) : null}
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
                    <Field orientation="horizontal">
                        <Switch
                            id="domain-enabled"
                            checked={form.enabled}
                            disabled={!canEdit}
                            onCheckedChange={enabled => set({ enabled })}
                        />
                        <FieldLabel htmlFor="domain-enabled" className="flex-col items-start gap-0.5">
                            Enable domain
                            <span className="text-xs font-normal text-muted-foreground">
                                Enable domain to handle incoming authentication and authorization requests.
                            </span>
                        </FieldLabel>
                    </Field>
                    <Field orientation="horizontal">
                        <Switch id="domain-master" checked={form.master} disabled={!canEdit} onCheckedChange={master => set({ master })} />
                        <FieldLabel htmlFor="domain-master" className="flex-col items-start gap-0.5">
                            Master domain
                            <span className="text-xs font-normal text-muted-foreground">
                                A master security domain can introspect tokens from other domains. Useful if you want to dedicate a domain
                                only to verify tokens.
                            </span>
                        </FieldLabel>
                    </Field>
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
                    {canEdit ? (
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
                    ) : null}
                    {tags.error ? (
                        <p className="text-sm text-muted-foreground">Sharding tags could not be retrieved from the backend.</p>
                    ) : null}
                    {form.tags.length === 0 ? (
                        <p className="text-sm text-muted-foreground">No sharding tags. Every gateway serves this domain.</p>
                    ) : (
                        <div className="flex flex-wrap gap-2">
                            {form.tags.map(id => (
                                <Badge key={id} variant="secondary">
                                    {tagName(id) ? `${id} | ${tagName(id)}` : id}
                                    {canEdit ? (
                                        <button
                                            type="button"
                                            className="ml-1"
                                            aria-label={`Remove sharding tag ${id}`}
                                            onClick={() => set({ tags: form.tags.filter(tag => tag !== id) })}
                                        >
                                            <XIcon className="size-3" aria-hidden />
                                        </button>
                                    ) : null}
                                </Badge>
                            ))}
                        </div>
                    )}
                </CardContent>
            </Card>

            <SaveBar
                canEdit={canEdit}
                dirty={dirty}
                saving={save.isPending}
                invalid={form.name === ''}
                onDiscard={() => setForm(fromDomain(domain))}
                onSave={() => save.mutate()}
            />

            {canDelete ? (
                <Card className="border-destructive/30">
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
            ) : null}

            <ConfirmDialog
                open={confirmOpen}
                title={
                    pending?.kind === 'delete'
                        ? 'Delete Domain'
                        : pending?.kind === 'remove-request-uri'
                          ? 'Remove request URI'
                          : 'Remove logout redirect URI'
                }
                description={
                    pending?.kind === 'delete'
                        ? 'Are you sure you want to delete this domain ?'
                        : pending?.kind === 'remove-request-uri'
                          ? 'Are you sure you want to remove this request URI ?'
                          : 'Are you sure you want to remove this redirect URI ?'
                }
                confirmLabel={pending?.kind === 'delete' ? 'Delete domain' : 'Remove'}
                destructive={pending?.kind === 'delete'}
                onOpenChange={setConfirmOpen}
                onConfirm={confirm}
            />
        </div>
    );
}
