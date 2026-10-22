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
    toast,
} from '@gravitee/graphene-core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { DurationInput } from '../../app/components/DurationInput';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import { SettingToggle } from '../../app/components/SettingToggle';
import { type Domain, patchDomainSecretSettings } from '../../lib/api/domains';
import { draftToSeconds, type DurationDraft, exactDuration } from '../../lib/forms/duration';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';

interface FormState {
    readonly enabled: boolean;
    readonly expiry: DurationDraft;
}

/** 90 days, the Angular default when the domain has no expiry. */
const DEFAULT_EXPIRY_SECONDS = 7_776_000;

function fromDomain(domain: Domain): FormState {
    const settings = domain.secretExpirationSettings;
    return { enabled: settings?.enabled ?? false, expiry: exactDuration(settings?.expiryTimeSeconds || DEFAULT_EXPIRY_SECONDS) };
}

/**
 * The Angular `DomainSettingsSecretsComponent` (Settings › Client Secrets): the domain-wide client secret expiry, which
 * an application can override. Saved as `secretSettings: { enabled, expiryTimeSeconds }`.
 */
export function DomainSecretsPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canEdit = usePermissions().has('domain_settings_update');
    const [form, setForm] = useState<FormState>(() => fromDomain(domain));
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Client Secrets' },
    ]);

    // A save or a refetch gives a new domain object. The form then starts again from it.
    const [syncedDomain, setSyncedDomain] = useState(domain);
    if (syncedDomain !== domain) {
        setSyncedDomain(domain);
        setForm(fromDomain(domain));
    }

    const save = useMutation({
        // Angular sends moment's duration of the form values, which is 0 for an empty time.
        mutationFn: () => patchDomainSecretSettings(ref, { enabled: form.enabled, expiryTimeSeconds: draftToSeconds(form.expiry) }),
        onSuccess: updated => {
            queryClient.setQueryData(['domain', ref.organizationId, ref.environmentId, updated.hrid], updated);
            void queryClient.invalidateQueries({ queryKey: ['domains'] });
            toast.success('Secrets configuration updated');
        },
        onError: error => toast.error(error.message || 'Unable to update the secrets configuration'),
    });

    const dirty = JSON.stringify(form) !== JSON.stringify(fromDomain(domain));
    const set = (patch: Partial<FormState>) => setForm(current => ({ ...current, ...patch }));
    let timeError: string | undefined;
    if (form.enabled && form.expiry.time === '') {
        timeError = 'Duration is required.';
    } else if (form.enabled && !(Number(form.expiry.time) >= 1)) {
        timeError = 'Duration must be greater than 0.';
    }

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Client Secrets"
                description="Rules applied to the client secrets of every application in this domain. An application can override them."
            />

            <Card>
                <CardHeader>
                    <CardTitle className="text-base">Expiry policy</CardTitle>
                    <CardDescription>New secrets expire after this duration, and must be renewed.</CardDescription>
                </CardHeader>
                <CardContent>
                    <SettingToggle
                        id="secrets-expiry"
                        label="Enable client secret expiry"
                        description="Apply an expiry to the client secrets of all applications."
                        checked={form.enabled}
                        disabled={!canEdit}
                        onChange={enabled => set({ enabled })}
                    >
                        {form.enabled ? (
                            <Field data-invalid={!!timeError}>
                                <FieldLabel htmlFor="secrets-duration">Expiry time</FieldLabel>
                                <DurationInput
                                    id="secrets-duration"
                                    label="Expiry time"
                                    value={form.expiry}
                                    disabled={!canEdit}
                                    invalid={!!timeError}
                                    onChange={expiry => set({ expiry })}
                                />
                                {timeError ? (
                                    <FieldError>{timeError}</FieldError>
                                ) : (
                                    <FieldDescription>How long a client secret stays valid.</FieldDescription>
                                )}
                            </Field>
                        ) : null}
                    </SettingToggle>
                </CardContent>
            </Card>

            <SaveBar
                canEdit={canEdit}
                dirty={dirty}
                saving={save.isPending}
                invalid={!!timeError}
                onDiscard={() => setForm(fromDomain(domain))}
                onSave={() => save.mutate()}
            />
        </div>
    );
}
