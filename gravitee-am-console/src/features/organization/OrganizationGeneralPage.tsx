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
    Combobox,
    ComboboxChip,
    ComboboxChips,
    ComboboxChipsInput,
    ComboboxContent,
    ComboboxEmpty,
    ComboboxItem,
    ComboboxList,
    Field,
    FieldLabel,
    Skeleton,
    toast,
} from '@gravitee/graphene-core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { SaveBar } from '../../app/components/SaveBar';
import {
    getOrganizationSettings,
    listOrganizationIdentityProviders,
    type OrganizationIdentityProvider,
    type OrganizationSettings,
    patchOrganizationSettings,
} from '../../lib/api/organization';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { usePermissions } from '../../lib/session/permissions';
import { useSession } from '../../lib/session/session';
import { useOrganizationPath } from './useOrganizationPath';

const FORM_ID = 'organization-general-form';

type Selection = {
    readonly identityProviders: readonly string[];
    readonly socialIdentities: readonly string[];
};

function selectionOf(settings: OrganizationSettings, providers: readonly OrganizationIdentityProvider[]): Selection {
    const identities = settings.identities ?? [];
    const selected = (external: boolean) =>
        providers.filter(provider => provider.external === external && identities.includes(provider.id)).map(provider => provider.id);
    return { identityProviders: selected(false), socialIdentities: selected(true) };
}

export function OrganizationGeneralPage() {
    const { user } = useSession();
    const queryClient = useQueryClient();
    const base = useOrganizationPath();
    useBreadcrumbs([{ label: 'Organization', to: `${base}/general` }, { label: 'Authentication' }]);
    const settings = useQuery({ queryKey: ['organization-settings', user.org], queryFn: () => getOrganizationSettings(user.org) });
    const providers = useQuery({
        queryKey: ['organization-identity-providers', user.org],
        queryFn: () => listOrganizationIdentityProviders(user.org),
    });

    const error = settings.error ?? providers.error;
    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Authentication"
                description="The platform preferences let you configure how your users will connect to the Access Management Admin Portal."
            />
            {error ? (
                <ErrorPanel description="Could not load the authentication settings." error={error} />
            ) : !settings.data || !providers.data ? (
                <Skeleton className="h-72 w-full" />
            ) : (
                <AuthenticationForm
                    key={String(settings.dataUpdatedAt)}
                    organizationId={user.org}
                    settings={settings.data}
                    providers={providers.data}
                    onSaved={updated => {
                        queryClient.setQueryData(['organization-settings', user.org], updated);
                        toast.success('Settings updated');
                    }}
                />
            )}
        </div>
    );
}

interface AuthenticationFormProps {
    readonly organizationId: string;
    readonly settings: OrganizationSettings;
    readonly providers: readonly OrganizationIdentityProvider[];
    readonly onSaved: (settings: OrganizationSettings) => void;
}

function AuthenticationForm({ organizationId, settings, providers, onSaved }: AuthenticationFormProps) {
    const { has } = usePermissions();
    const canEdit = has('organization_settings_update');
    const saved = selectionOf(settings, providers);
    const [selection, setSelection] = useState<Selection>(saved);
    const save = useMutation({
        mutationFn: () =>
            patchOrganizationSettings(organizationId, { identities: [...selection.identityProviders, ...selection.socialIdentities] }),
        onSuccess: onSaved,
        onError: error => toast.error(`Could not update the settings. ${error.message}`),
    });
    const dirty = JSON.stringify(selection) !== JSON.stringify(saved);
    const submit = (event: FormEvent) => {
        event.preventDefault();
        save.mutate();
    };

    return (
        <>
            <form id={FORM_ID} className="flex flex-col gap-6" onSubmit={submit}>
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Identity Providers</CardTitle>
                        <CardDescription>Identity sources for authenticating and retrieving information about your users.</CardDescription>
                    </CardHeader>
                    <CardContent>
                        <ProviderSelect
                            id="organization-identity-providers"
                            label="Identity providers"
                            placeholder="Identity providers"
                            options={providers.filter(provider => !provider.external)}
                            value={selection.identityProviders}
                            disabled={!canEdit}
                            onChange={identityProviders => setSelection(current => ({ ...current, identityProviders }))}
                        />
                    </CardContent>
                </Card>
                <Card>
                    <CardHeader>
                        <CardTitle className="text-base">Social / Enterprise</CardTitle>
                        <CardDescription>
                            Social or external sources for authenticating and retrieving information about your users.
                        </CardDescription>
                    </CardHeader>
                    <CardContent>
                        <ProviderSelect
                            id="organization-social-identities"
                            label="Social providers"
                            placeholder="Social providers"
                            options={providers.filter(provider => provider.external)}
                            value={selection.socialIdentities}
                            disabled={!canEdit}
                            onChange={socialIdentities => setSelection(current => ({ ...current, socialIdentities }))}
                        />
                    </CardContent>
                </Card>
            </form>
            {canEdit && <SaveBar formId={FORM_ID} dirty={dirty} saving={save.isPending} onDiscard={() => setSelection(saved)} />}
        </>
    );
}

interface ProviderSelectProps {
    readonly id: string;
    readonly label: string;
    readonly placeholder: string;
    readonly options: readonly OrganizationIdentityProvider[];
    readonly value: readonly string[];
    readonly disabled: boolean;
    readonly onChange: (ids: string[]) => void;
}

function ProviderSelect({ id, label, placeholder, options, value, disabled, onChange }: ProviderSelectProps) {
    const items = options.map(option => ({ value: option.id, label: option.name }));
    const selected = items.filter(item => value.includes(item.value));
    return (
        <Field>
            <FieldLabel htmlFor={id} className="sr-only">
                {label}
            </FieldLabel>
            <Combobox
                multiple
                items={items}
                value={selected}
                onValueChange={next => onChange((next as typeof items).map(item => item.value))}
                isItemEqualToValue={(item, current) => item.value === current.value}
                disabled={disabled}
            >
                <ComboboxChips>
                    {selected.map(item => (
                        <ComboboxChip key={item.value} removeAriaLabel={`Remove ${item.label}`}>
                            {item.label}
                        </ComboboxChip>
                    ))}
                    <ComboboxChipsInput id={id} placeholder={selected.length === 0 ? placeholder : undefined} />
                </ComboboxChips>
                <ComboboxContent>
                    <ComboboxEmpty>No provider found.</ComboboxEmpty>
                    <ComboboxList>
                        {(item: (typeof items)[number]) => (
                            <ComboboxItem key={item.value} value={item}>
                                {item.label}
                            </ComboboxItem>
                        )}
                    </ComboboxList>
                </ComboboxContent>
            </Combobox>
        </Field>
    );
}
