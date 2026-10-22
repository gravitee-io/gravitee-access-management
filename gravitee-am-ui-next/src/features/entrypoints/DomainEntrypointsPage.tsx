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
import { useState } from 'react';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { type Domain, listDomainEntrypoints, patchDomainEntrypoints } from '../../lib/api/domains';
import { getInstallationConfiguration } from '../../lib/api/management-api';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { useCurrentEnvironment } from '../../lib/session/environment';
import { usePermissions } from '../../lib/session/permissions';
import { type VirtualHostRow, accessUrl, domainEntrypoint, hostOptions, hostPattern } from './entrypoint-hosts';

interface FormState {
    readonly vhostMode: boolean;
    readonly path: string;
    readonly vhosts: readonly VirtualHostRow[];
}

function fromDomain(domain: Domain): FormState {
    return {
        vhostMode: domain.vhostMode ?? false,
        path: domain.path ?? '',
        vhosts: (domain.vhosts ?? []).map(vhost => ({ ...vhost })),
    };
}

/**
 * The Angular `DomainSettingsEntrypointsComponent` (Settings › Entrypoints): context-path mode (the organization's
 * entrypoint plus the domain's path) or virtual hosts mode (host and path pairs, one of which overrides the entrypoint
 * shown to applications). Saved with the fields of `DomainService.patchEntrypoints`. Cloud mode makes it read-only.
 */
export function DomainEntrypointsPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { domainRestrictions = [] } = useCurrentEnvironment();
    const queryClient = useQueryClient();
    const { has } = usePermissions();
    const entrypoints = useQuery({ queryKey: ['domain-entrypoints', ref], queryFn: () => listDomainEntrypoints(ref) });
    const installation = useQuery({ queryKey: ['installation'], queryFn: getInstallationConfiguration, staleTime: Infinity });
    // Read-only until the installation type is known, so a managed installation is never editable.
    const cloudMode = installation.isPending || installation.data?.type === 'managed';
    const canEdit = has('domain_settings_update') && !cloudMode;
    const [form, setForm] = useState<FormState>(() => fromDomain(domain));
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Entrypoints' },
    ]);

    // A save or a refetch gives a new domain object. The form then starts again from it.
    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        setForm(fromDomain(domain));
    }

    const save = useMutation({
        mutationFn: () => patchDomainEntrypoints(ref, { path: form.path || domain.path, vhostMode: form.vhostMode, vhosts: form.vhosts }),
        onSuccess: updated => {
            queryClient.setQueryData(['domain', ref.organizationId, ref.environmentId, updated.hrid], updated);
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success(`Domain ${updated.name} updated`);
        },
        onError: error => toast.error(error.message || 'Unable to update the entrypoints'),
    });

    if (entrypoints.error) {
        return <ErrorPanel description="Could not load the domain's entrypoints." error={entrypoints.error} />;
    }
    if (!entrypoints.data) {
        return <Skeleton className="h-32 w-full" />;
    }

    const entrypoint = domainEntrypoint(entrypoints.data);
    const dirty = JSON.stringify(form) !== JSON.stringify(fromDomain(domain));
    const set = (patch: Partial<FormState>) => setForm(current => ({ ...current, ...patch }));
    const setVhost = (index: number, patch: Partial<VirtualHostRow>) =>
        set({ vhosts: form.vhosts.map((vhost, i) => (i === index ? { ...vhost, ...patch } : vhost)) });

    const pattern = hostPattern(domainRestrictions);
    const hostErrors = form.vhosts.map(vhost => {
        if (!vhost.host) {
            return 'A host is required.';
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
    const invalid = form.vhostMode ? hostErrors.some(Boolean) || vhostPathErrors.some(Boolean) : !!pathError;

    const addVhost = () =>
        set({
            vhosts:
                form.vhosts.length === 0
                    ? [{ host: '', path: form.path, overrideEntrypoint: true }]
                    : [...form.vhosts, { host: '', path: '/' }],
        });

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
                    cloudMode ? null : (
                        <Button variant="outline" size="sm" onClick={() => set({ vhostMode: !form.vhostMode })}>
                            {form.vhostMode ? 'Switch to context-path mode' : 'Switch to virtual hosts mode'}
                        </Button>
                    )
                }
            />

            {url ? (
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
            ) : null}

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
                                    onChange={event => set({ path: event.target.value })}
                                />
                                {pathError ? (
                                    <FieldError>{pathError}</FieldError>
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
                                    set({ vhosts: form.vhosts.map((vhost, i) => ({ ...vhost, overrideEntrypoint: i === Number(value) })) })
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
                                                        // A free-text field: the selected value follows the text, and Escape keeps it.
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
                                                            showTrigger={false}
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
                                                        onChange={event => setVhost(index, { host: event.target.value })}
                                                    />
                                                )}
                                                {hostErrors[index] ? (
                                                    <FieldError>{hostErrors[index]}</FieldError>
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
                                                    onChange={event => setVhost(index, { path: event.target.value })}
                                                />
                                                {vhostPathErrors[index] ? <FieldError>{vhostPathErrors[index]}</FieldError> : null}
                                            </Field>
                                        </div>
                                        <div className="flex items-center justify-between gap-4">
                                            <Field orientation="horizontal" className="w-auto">
                                                <RadioGroupItem id={`vhost-override-${index}`} value={String(index)} />
                                                <FieldLabel htmlFor={`vhost-override-${index}`} className="font-normal">
                                                    Override entrypoint on the application overview page
                                                </FieldLabel>
                                            </Field>
                                            {canEdit ? (
                                                <Button
                                                    variant="ghost"
                                                    size="icon-sm"
                                                    aria-label={`Remove virtual host ${vhost.host || index + 1}`}
                                                    onClick={() => set({ vhosts: form.vhosts.filter((_, i) => i !== index) })}
                                                >
                                                    <Trash2Icon aria-hidden />
                                                </Button>
                                            ) : null}
                                        </div>
                                    </div>
                                ))}
                            </RadioGroup>
                        )}
                        {canEdit ? (
                            <div>
                                <Button variant="outline" size="sm" onClick={addVhost}>
                                    <PlusIcon aria-hidden />
                                    Add virtual host
                                </Button>
                            </div>
                        ) : null}
                    </CardContent>
                </Card>
            )}

            <SaveBar
                canEdit={canEdit}
                dirty={dirty}
                saving={save.isPending}
                invalid={invalid}
                onDiscard={() => setForm(fromDomain(domain))}
                onSave={() => save.mutate()}
            />
        </div>
    );
}
