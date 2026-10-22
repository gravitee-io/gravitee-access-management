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
    toast,
} from '@gravitee/graphene-core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import { type Domain, type KeyRetrievalSettings, patchDomainKeyRetrievalSettings } from '../../lib/api/domains';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { TrustDomainsTabs } from './TrustDomainsTabs';

type NumberKey = 'fetchTimeoutMs' | 'maxResponseSizeKb' | 'cacheTtlSeconds' | 'cacheMaxEntries';

type FormState = Omit<KeyRetrievalSettings, NumberKey> & { [K in NumberKey]?: number | null };

/** `DEFAULT_KEY_RETRIEVAL_SETTINGS` of the Angular component, for the settings the domain does not store. */
const DEFAULTS: KeyRetrievalSettings = {
    allowUnsecuredHttpUri: false,
    allowPrivateIpAddress: false,
    fetchTimeoutMs: 5000,
    maxResponseSizeKb: 32,
    cacheTtlSeconds: 300,
    cacheMaxEntries: 50,
};

const fromDomain = (domain: Domain): FormState => ({ ...DEFAULTS, ...domain.keyRetrievalSettings });

const NUMBER_FIELDS: readonly { key: NumberKey; label: string; hint: string; min: number; section: 'ssrf' | 'cache' }[] = [
    {
        key: 'fetchTimeoutMs',
        label: 'Fetch timeout (ms)',
        hint: 'Maximum time to wait when fetching a JWKS endpoint. Default: 5000.',
        min: 100,
        section: 'ssrf',
    },
    {
        key: 'maxResponseSizeKb',
        label: 'Max JWKS response size (KB)',
        hint: 'Reject JWKS responses larger than this. Default: 32.',
        min: 1,
        section: 'ssrf',
    },
    {
        key: 'cacheTtlSeconds',
        label: 'Cache TTL (seconds)',
        hint: 'Default cache lifetime when a trusted domain does not override the refresh interval. Default: 300.',
        min: 1,
        section: 'cache',
    },
    {
        key: 'cacheMaxEntries',
        label: 'Cache max entries',
        hint: 'Maximum number of cached key sets. Default: 50.',
        min: 1,
        section: 'cache',
    },
];

/**
 * The Angular `TrustDomainKeyRetrievalComponent` (Trusted Domains › Key retrieval): the SSRF limits and the key cache of
 * every trusted domain of the domain. Saved as `keyRetrievalSettings` on the domain.
 */
export function TrustDomainKeyRetrievalPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canEdit = usePermissions().has('domain_settings_update');
    const [form, setForm] = useState<FormState>(() => fromDomain(domain));
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Trusted Domains', to: `${basePath}/settings/trust-domains/domains` },
        { label: 'Key retrieval' },
    ]);

    // A save or a refetch gives a new domain object. The form then starts again from it.
    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        setForm(fromDomain(domain));
    }

    const save = useMutation({
        mutationFn: () => patchDomainKeyRetrievalSettings(ref, form as KeyRetrievalSettings),
        onSuccess: updated => {
            queryClient.setQueryData(['domain', ref.organizationId, ref.environmentId, updated.hrid], updated);
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success('Key retrieval settings updated');
        },
        onError: error => toast.error(error.message || 'Failed to update key retrieval settings'),
    });

    const dirty = JSON.stringify(form) !== JSON.stringify(fromDomain(domain));
    const errors = Object.fromEntries(
        NUMBER_FIELDS.map(field => {
            const value = form[field.key];
            return [field.key, value === null || value === undefined || value < field.min ? `Enter ${field.min} or more.` : undefined];
        }),
    );
    const set = (patch: Partial<FormState>) => setForm(current => ({ ...current, ...patch }));

    const numberField = (field: (typeof NUMBER_FIELDS)[number]) => (
        <Field key={field.key} data-invalid={!!errors[field.key]}>
            <FieldLabel htmlFor={`key-retrieval-${field.key}`}>{field.label}</FieldLabel>
            <Input
                id={`key-retrieval-${field.key}`}
                type="number"
                min={field.min}
                className="w-48"
                value={form[field.key] ?? ''}
                disabled={!canEdit}
                aria-invalid={!!errors[field.key]}
                onChange={event => set({ [field.key]: event.target.value === '' ? null : Number(event.target.value) })}
            />
            {errors[field.key] ? <FieldError>{errors[field.key]}</FieldError> : <FieldDescription>{field.hint}</FieldDescription>}
        </Field>
    );

    return (
        <div className="flex flex-col gap-6">
            <TrustDomainsTabs active="key-retrieval" />

            <p className="text-sm text-muted-foreground">
                These limits apply to every trusted domain in this security domain, whichever usage it serves. Key sources and refresh
                intervals are set on each trusted domain.
            </p>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">SSRF protection</CardTitle>
                    <CardDescription>
                        With both options off, AM never fetches keys over plain HTTP or from addresses on the internal network.
                    </CardDescription>
                </CardHeader>
                <CardContent className="flex flex-col gap-5">
                    <div>
                        <SettingToggle
                            id="key-retrieval-http"
                            label="Allow unsecured HTTP JWKS URLs"
                            description="When off, only https:// JWKS URLs are accepted (recommended)."
                            checked={!!form.allowUnsecuredHttpUri}
                            disabled={!canEdit}
                            onChange={allowUnsecuredHttpUri => set({ allowUnsecuredHttpUri })}
                        />
                        <SettingToggle
                            id="key-retrieval-private"
                            label="Allow JWKS URLs resolving to private IP addresses"
                            description="Turn on only when trusted domain key endpoints live on the internal network."
                            checked={!!form.allowPrivateIpAddress}
                            disabled={!canEdit}
                            onChange={allowPrivateIpAddress => set({ allowPrivateIpAddress })}
                        />
                    </div>
                    {NUMBER_FIELDS.filter(field => field.section === 'ssrf').map(numberField)}
                </CardContent>
            </Card>

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Key cache</CardTitle>
                </CardHeader>
                <CardContent className="grid gap-5">
                    {NUMBER_FIELDS.filter(field => field.section === 'cache').map(numberField)}
                </CardContent>
            </Card>

            <SaveBar
                canEdit={canEdit}
                dirty={dirty}
                saving={save.isPending}
                invalid={Object.values(errors).some(Boolean)}
                onDiscard={() => setForm(fromDomain(domain))}
                onSave={() => save.mutate()}
            />
        </div>
    );
}
