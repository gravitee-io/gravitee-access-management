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
    Combobox,
    ComboboxContent,
    ComboboxInput,
    ComboboxItem,
    ComboboxList,
    CopyableText,
    Field,
    FieldDescription,
    FieldError,
    FieldLabel,
    Input,
    RadioGroup,
    RadioGroupItem,
    Skeleton,
    toast,
} from '@gravitee/graphene-core';
import { PlusIcon, Trash2Icon, TriangleAlertIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import {
    type Domain,
    type VirtualHost,
    accessUrl,
    domainEntrypoint,
    listDomainEntrypoints,
    patchDomainEntrypoints,
} from '../../lib/api/domains';
import { getInstallationConfiguration } from '../../lib/api/management-api';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { useCurrentEnvironment } from '../../lib/session/environment';
import { usePermissions } from '../../lib/session/permissions';
import { hostOptions, hostPattern } from './entrypoint-hosts';

const FORM_ID = 'domain-entrypoints-form';

type FormState = {
    readonly vhostMode: boolean;
    readonly path: string;
    readonly vhosts: readonly VirtualHost[];
};

function fromDomain(domain: Domain): FormState {
    return {
        vhostMode: domain.vhostMode ?? false,
        path: domain.path ?? '',
        vhosts: domain.vhosts ?? [],
    };
}

export function DomainEntrypointsPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { domainRestrictions = [] } = useCurrentEnvironment();
    const queryClient = useQueryClient();
    const { has } = usePermissions();
    const entrypoints = useQuery({ queryKey: ['domain-entrypoints', ref], queryFn: () => listDomainEntrypoints(ref) });
    const installation = useQuery({ queryKey: ['installation'], queryFn: getInstallationConfiguration, staleTime: Infinity });
    // Read-only until the installation type loads.
    const cloudMode = installation.isPending || installation.data?.type === 'managed';
    const canEdit = has('domain_settings_update') && !cloudMode;
    const [form, setForm] = useState<FormState>(() => fromDomain(domain));
    // The indexes of the vhosts whose host field has lost focus.
    const [touchedHosts, setTouchedHosts] = useState<ReadonlySet<number>>(new Set());
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Entrypoints' },
    ]);

    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        setForm(fromDomain(domain));
        setTouchedHosts(new Set());
    }

    const save = useMutation({
        mutationFn: () => patchDomainEntrypoints(ref, { path: form.path, vhostMode: form.vhostMode, vhosts: form.vhosts }),
        onSuccess: updated => {
            queryClient.setQueryData(['domain', ref.organizationId, ref.environmentId, updated.hrid], updated);
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success(`Domain ${updated.name} updated`);
        },
        onError: error => toast.error(`Could not update the entrypoints. ${error.message}`),
    });

    if (entrypoints.error) {
        return <ErrorPanel description="Could not load the domain entrypoints." error={entrypoints.error} />;
    }
    if (!entrypoints.data) {
        return <Skeleton className="h-32 w-full" />;
    }

    const entrypoint = domainEntrypoint(entrypoints.data);
    const dirty = JSON.stringify(form) !== JSON.stringify(fromDomain(domain));
    const set = (patch: Partial<FormState>) => setForm(current => ({ ...current, ...patch }));
    const setVhost = (index: number, patch: Partial<VirtualHost>) =>
        set({ vhosts: form.vhosts.map((vhost, i) => (i === index ? { ...vhost, ...patch } : vhost)) });

    const pattern = hostPattern(domainRestrictions);
    const hostErrors = form.vhosts.map((vhost, index) => {
        if (!vhost.host) {
            return touchedHosts.has(index) ? 'A host is required.' : undefined;
        }
        if (pattern.test(vhost.host)) {
            return undefined;
        }
        return domainRestrictions.length > 0
            ? `Enter a host under ${domainRestrictions.join(', ')}, with an optional port.`
            : 'Enter a host, with an optional port.';
    });
    const vhostPathErrors = form.vhosts.map(vhost => (vhost.path ? undefined : 'A path is required.'));
    const pathError = entrypoint && !form.path ? 'A path is required.' : undefined;
    const invalid = form.vhostMode
        ? form.vhosts.some(vhost => !vhost.host) || hostErrors.some(Boolean) || vhostPathErrors.some(Boolean)
        : !!pathError;

    const touchHost = (index: number) => setTouchedHosts(current => new Set(current).add(index));
    const removeVhost = (index: number) => {
        set({ vhosts: form.vhosts.filter((_, i) => i !== index) });
        setTouchedHosts(current => new Set([...current].filter(i => i !== index).map(i => (i > index ? i - 1 : i))));
    };
    const discard = () => {
        setForm(fromDomain(domain));
        setTouchedHosts(new Set());
    };
    const addVhost = () =>
        set({
            vhosts:
                form.vhosts.length === 0
                    ? [{ host: '', path: form.path, overrideEntrypoint: true }]
                    : [...form.vhosts, { host: '', path: '/' }],
        });
    const submit = (event: FormEvent) => {
        event.preventDefault();
        if (!invalid) {
            save.mutate();
        }
    };

    const url = entrypoint ? accessUrl(entrypoint.url, form.path, form.vhostMode, form.vhosts) : undefined;

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Entrypoints"
                description={
                    form.vhostMode
                        ? 'In virtual hosts mode, the domain is reached through the gateway on the hosts and paths of your choice. The protocol comes from the entrypoint defined in the organization settings.'
                        : "In context-path mode, the domain is reached through the gateway's entrypoint, defined in the organization settings, followed by the domain's path."
                }
                actions={
                    canEdit && (
                        <Button type="button" variant="outline" size="sm" onClick={() => set({ vhostMode: !form.vhostMode })}>
                            {form.vhostMode ? 'Switch to context-path mode' : 'Switch to virtual hosts mode'}
                        </Button>
                    )
                }
            />

            {url && (
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Access URL</CardTitle>
                        <CardDescription>
                            Where applications reach this domain. Changing it means changing the endpoint URLs in your applications.
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        <CopyableText className="max-w-full" value={url} />
                    </CardContent>
                </Card>
            )}

            <form id={FORM_ID} onSubmit={submit}>
                {!form.vhostMode ? (
                    entrypoint ? (
                        <Card>
                            <CardHeader>
                                <CardTitle className="text-base">Context path</CardTitle>
                            </CardHeader>
                            <CardContent className="grid gap-5">
                                <Field>
                                    <FieldLabel htmlFor="entrypoint-url">Domain entrypoint URL</FieldLabel>
                                    <Input id="entrypoint-url" value={entrypoint.url} disabled readOnly />
                                    <FieldDescription>
                                        The current base URL to access the gateway, defined for the organization.
                                    </FieldDescription>
                                </Field>
                                <Field data-invalid={!!pathError}>
                                    <FieldLabel htmlFor="entrypoint-path">Domain context path</FieldLabel>
                                    <Input
                                        id="entrypoint-path"
                                        value={form.path}
                                        disabled={!canEdit}
                                        aria-invalid={!!pathError}
                                        aria-describedby={pathError ? 'entrypoint-path-error' : undefined}
                                        onChange={event => set({ path: event.target.value })}
                                    />
                                    {pathError ? (
                                        <FieldError id="entrypoint-path-error">{pathError}</FieldError>
                                    ) : (
                                        <FieldDescription>
                                            Must start with &apos;/&apos; and contain only letters, numbers, dashes or underscores.
                                        </FieldDescription>
                                    )}
                                </Field>
                            </CardContent>
                        </Card>
                    ) : (
                        <Alert variant="destructive">
                            <TriangleAlertIcon aria-hidden />
                            <AlertTitle>Default entrypoint is missing</AlertTitle>
                            <AlertDescription>
                                Make sure there is at least one default entrypoint configured in the organization settings.
                            </AlertDescription>
                        </Alert>
                    )
                ) : (
                    <Card>
                        <CardHeader>
                            <CardTitle className="text-base">Virtual hosts</CardTitle>
                            <CardDescription>
                                Paths must not overlap another security domain&apos;s. Only the host and an optional port can be set.
                            </CardDescription>
                        </CardHeader>
                        <CardContent className="flex flex-col gap-4">
                            {form.vhosts.length === 0 ? (
                                <p className="text-sm text-muted-foreground">No virtual hosts yet.</p>
                            ) : (
                                <RadioGroup
                                    value={String(form.vhosts.findIndex(vhost => vhost.overrideEntrypoint))}
                                    disabled={!canEdit}
                                    onValueChange={value =>
                                        set({
                                            vhosts: form.vhosts.map((vhost, i) => ({ ...vhost, overrideEntrypoint: i === Number(value) })),
                                        })
                                    }
                                    className="flex flex-col gap-4"
                                >
                                    {form.vhosts.map((vhost, index) => (
                                        <div key={index} className="flex flex-col gap-3 rounded-lg border border-border p-4">
                                            <div className="grid gap-4 md:grid-cols-2">
                                                <Field data-invalid={!!hostErrors[index]}>
                                                    <FieldLabel htmlFor={`vhost-host-${index}`}>Host</FieldLabel>
                                                    {domainRestrictions.length > 0 ? (
                                                        <Combobox
                                                            items={hostOptions(vhost.host, domainRestrictions)}
                                                            // Escape keeps the typed host.
                                                            value={vhost.host}
                                                            onValueChange={(host: string | null, details: { reason: string }) =>
                                                                details.reason !== 'escape-key' && setVhost(index, { host: host ?? '' })
                                                            }
                                                            inputValue={vhost.host}
                                                            onInputValueChange={(host, details) =>
                                                                details.reason !== 'escape-key' && setVhost(index, { host })
                                                            }
                                                            filter={null}
                                                            disabled={!canEdit}
                                                        >
                                                            <ComboboxInput
                                                                id={`vhost-host-${index}`}
                                                                placeholder="auth.acme.example"
                                                                aria-invalid={!!hostErrors[index]}
                                                                aria-describedby={
                                                                    hostErrors[index] ? `vhost-host-${index}-error` : undefined
                                                                }
                                                                showTrigger={false}
                                                                onBlur={() => touchHost(index)}
                                                            />
                                                            <ComboboxContent>
                                                                <ComboboxList>
                                                                    {(option: string) => (
                                                                        <ComboboxItem key={option} value={option}>
                                                                            {option}
                                                                        </ComboboxItem>
                                                                    )}
                                                                </ComboboxList>
                                                            </ComboboxContent>
                                                        </Combobox>
                                                    ) : (
                                                        <Input
                                                            id={`vhost-host-${index}`}
                                                            placeholder="auth.acme.example"
                                                            value={vhost.host}
                                                            disabled={!canEdit}
                                                            aria-invalid={!!hostErrors[index]}
                                                            aria-describedby={hostErrors[index] ? `vhost-host-${index}-error` : undefined}
                                                            onBlur={() => touchHost(index)}
                                                            onChange={event => setVhost(index, { host: event.target.value })}
                                                        />
                                                    )}
                                                    {hostErrors[index] ? (
                                                        <FieldError id={`vhost-host-${index}-error`}>{hostErrors[index]}</FieldError>
                                                    ) : (
                                                        <FieldDescription>
                                                            Host the HTTP request must carry to reach this domain.
                                                        </FieldDescription>
                                                    )}
                                                </Field>
                                                <Field data-invalid={!!vhostPathErrors[index]}>
                                                    <FieldLabel htmlFor={`vhost-path-${index}`}>Path</FieldLabel>
                                                    <Input
                                                        id={`vhost-path-${index}`}
                                                        value={vhost.path}
                                                        disabled={!canEdit}
                                                        aria-invalid={!!vhostPathErrors[index]}
                                                        aria-describedby={vhostPathErrors[index] ? `vhost-path-${index}-error` : undefined}
                                                        onChange={event => setVhost(index, { path: event.target.value })}
                                                    />
                                                    {vhostPathErrors[index] && (
                                                        <FieldError id={`vhost-path-${index}-error`}>{vhostPathErrors[index]}</FieldError>
                                                    )}
                                                </Field>
                                            </div>
                                            <div className="flex items-center justify-between gap-4">
                                                <Field orientation="horizontal" className="w-auto">
                                                    <RadioGroupItem id={`vhost-override-${index}`} value={String(index)} />
                                                    <FieldLabel htmlFor={`vhost-override-${index}`} className="font-normal">
                                                        Override entrypoint on the application overview page
                                                    </FieldLabel>
                                                </Field>
                                                {canEdit && (
                                                    <Button
                                                        type="button"
                                                        variant="ghost"
                                                        size="icon-sm"
                                                        aria-label={`Remove virtual host ${vhost.host || index + 1}`}
                                                        onClick={() => removeVhost(index)}
                                                    >
                                                        <Trash2Icon aria-hidden />
                                                    </Button>
                                                )}
                                            </div>
                                        </div>
                                    ))}
                                </RadioGroup>
                            )}
                            {canEdit && (
                                <div>
                                    <Button type="button" variant="outline" size="sm" onClick={addVhost}>
                                        <PlusIcon aria-hidden />
                                        Add virtual host
                                    </Button>
                                </div>
                            )}
                        </CardContent>
                    </Card>
                )}
            </form>

            {canEdit && <SaveBar formId={FORM_ID} dirty={dirty} saving={save.isPending} invalid={invalid} onDiscard={discard} />}
        </div>
    );
}
