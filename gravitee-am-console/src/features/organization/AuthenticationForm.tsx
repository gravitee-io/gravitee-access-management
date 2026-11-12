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
import { Card, CardContent, CardDescription, CardHeader, CardTitle, toast } from '@gravitee/graphene-core';
import { useMutation } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { SaveBar } from '../../app/components/SaveBar';
import { type OrganizationIdentityProvider, type OrganizationSettings, patchOrganizationSettings } from '../../lib/api/organization';
import { usePermissions } from '../../lib/session/permissions';
import { ProviderSelect } from './ProviderSelect';

const FORM_ID = 'organization-general-form';

type Selection = {
    readonly identityProviders: readonly string[];
    readonly socialIdentities: readonly string[];
};

function selectionOf(settings: OrganizationSettings, providers: readonly OrganizationIdentityProvider[] = []): Selection {
    const identities = settings.identities ?? [];
    const selected = (external: boolean) =>
        providers.filter(provider => provider.external === external && identities.includes(provider.id)).map(provider => provider.id);
    return { identityProviders: selected(false), socialIdentities: selected(true) };
}

export const sameIds = (a: readonly string[], b: readonly string[]) => a.length === b.length && a.every(id => b.includes(id));

interface AuthenticationFormProps {
    readonly organizationId: string;
    readonly settings: OrganizationSettings;
    /** Undefined when the user may not list the identity providers: the form is then read-only, as a save would drop the selection. */
    readonly providers?: readonly OrganizationIdentityProvider[];
    readonly onSaved: (settings: OrganizationSettings) => void;
}

export function AuthenticationForm({ organizationId, settings, providers, onSaved }: AuthenticationFormProps) {
    const { has } = usePermissions();
    const canEdit = has('organization_settings_update') && providers !== undefined;
    const saved = selectionOf(settings, providers);
    const [selection, setSelection] = useState<Selection>(saved);
    const save = useMutation({
        mutationFn: () =>
            patchOrganizationSettings(organizationId, { identities: [...selection.identityProviders, ...selection.socialIdentities] }),
        onSuccess: onSaved,
        onError: error => toast.error(`Could not update the settings. ${error.message}`),
    });
    const dirty =
        !sameIds(selection.identityProviders, saved.identityProviders) || !sameIds(selection.socialIdentities, saved.socialIdentities);
    const submit = (event: FormEvent) => {
        event.preventDefault();
        save.mutate();
    };

    return (
        <>
            {providers === undefined && (
                <p className="text-sm text-muted-foreground">
                    You are not allowed to list the identity providers, so the selection is not shown.
                </p>
            )}
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
                            options={(providers ?? []).filter(provider => !provider.external)}
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
                            options={(providers ?? []).filter(provider => provider.external)}
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
