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
import { Card, CardContent, CardDescription, CardHeader, CardTitle, composeResolvers, toast } from '@gravitee/graphene-core';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { PluginSchemaForm, pluginSchemaResolver } from '../../app/components/PluginSchemaForm';
import { nameResolver } from '../../app/components/PluginWizard';
import { SaveBar } from '../../app/components/SaveBar';
import { UpgradeDialog } from '../../app/components/UpgradeDialog';
import {
    INLINE_TYPE,
    type IdentityProvider,
    parseConfiguration,
    serializeConfiguration,
    updateIdentityProvider,
} from '../../lib/api/identity-providers';
import type { AdaptedSchema } from '../../lib/forms/am-schema';
import { richText } from '../../lib/forms/rich-text';
import { isRecord } from '../../lib/guards';
import { useCurrentDomain } from '../../lib/session/domain';
import { featureDescription, useLicense } from '../../lib/session/license';
import { Can, usePermissions } from '../../lib/session/permissions';
import { DeleteProviderCard } from './DeleteProviderCard';
import { IdentityProviderIntegration } from './IdentityProviderIntegration';
import { MongoStorageGuidance } from './MongoStorageGuidance';
import { ProviderGeneralCard, type ProviderValues } from './ProviderGeneralCard';
import { MONGO_IDP_TYPE } from './provider-schema';

const FORM_ID = 'identity-provider-settings';

function usernames(users: unknown): string[] {
    return Array.isArray(users)
        ? users.flatMap((user: unknown) => (isRecord(user) && typeof user.username === 'string' ? [user.username] : []))
        : [];
}

/** True when the update drops or renames a user of an inline provider, whose password must then be typed again. */
function dropsInlineUser(provider: IdentityProvider, config: Record<string, unknown>): boolean {
    const updated = usernames(config.users);
    return !usernames(parseConfiguration(provider).users).every(username => updated.includes(username));
}

interface IdentityProviderSettingsFormProps {
    readonly provider: IdentityProvider;
    readonly adapted: AdaptedSchema;
    readonly configuration: Record<string, unknown>;
    readonly pinned: readonly string[];
    readonly feature?: string;
}

/** The editable settings of a custom provider. Save sits in the layout banner. */
export function IdentityProviderSettingsForm({ provider, adapted, configuration, pinned, feature }: IdentityProviderSettingsFormProps) {
    const { ref } = useCurrentDomain();
    const queryClient = useQueryClient();
    const canUpdate = usePermissions().has('domain_identity_provider_update');
    const licenseMissing = useLicense().isMissing(feature);
    const [upgradeOpen, setUpgradeOpen] = useState(false);
    // The values that wait for the confirm dialog of a modified inline user.
    const [pendingValues, setPendingValues] = useState<ProviderValues | null>(null);
    const resolver = useMemo(
        () => composeResolvers<ProviderValues>(nameResolver, pluginSchemaResolver<ProviderValues>(adapted, 'config')),
        [adapted],
    );
    const form = useForm<ProviderValues>({
        resolver,
        mode: 'onTouched',
        criteriaMode: 'all',
        defaultValues: { name: provider.name, domainWhitelist: provider.domainWhitelist ?? [], config: configuration },
    });
    // The schema fields write empty values and select defaults as they register, so their writes become the baseline.
    useEffect(() => {
        form.reset(form.getValues());
    }, [form]);
    const save = useMutation({
        mutationFn: async (values: ProviderValues) => {
            // A read-only field goes back as stored.
            const stored = parseConfiguration(provider);
            const config = { ...values.config, ...Object.fromEntries(pinned.map(field => [field, stored[field]])) };
            return updateIdentityProvider(ref, {
                ...provider,
                name: values.name,
                domainWhitelist: values.domainWhitelist,
                configuration: await serializeConfiguration(provider.type, config),
            });
        },
        onSuccess: updated => {
            queryClient.setQueryData(['identity-provider', ref, provider.id], updated);
            void queryClient.invalidateQueries({ queryKey: ['identity-providers', ref] });
            form.reset({
                name: updated.name,
                domainWhitelist: updated.domainWhitelist ?? [],
                config: parseConfiguration(updated),
            });
            toast.success('Provider updated');
        },
        onError: error => toast.error(`Could not update the provider. ${error.message}`),
    });
    const submit = (values: ProviderValues) => {
        if (provider.type === INLINE_TYPE && dropsInlineUser(provider, values.config)) {
            setPendingValues(values);
            return;
        }
        save.mutate(values);
    };
    // Save sits in the layout banner, outside a LicenseGuard, so the submit opens the upgrade dialog.
    const onSubmit = (event: FormEvent<HTMLFormElement>) => {
        if (licenseMissing) {
            event.preventDefault();
            setUpgradeOpen(true);
            return;
        }
        void form.handleSubmit(submit)(event);
    };

    return (
        <div className="flex flex-col gap-6">
            <form id={FORM_ID} noValidate onSubmit={onSubmit} className="flex flex-col gap-6">
                {/* A save resets the form to the saved provider, so an edit typed during the save would be lost. */}
                <fieldset disabled={save.isPending} className="contents">
                    <ProviderGeneralCard form={form} external={provider.external === true} disabled={!canUpdate} />

                    <Card>
                        <CardHeader>
                            <CardTitle>Configuration</CardTitle>
                            {adapted.schema.description && (
                                <CardDescription className="whitespace-pre-line">{richText(adapted.schema.description)}</CardDescription>
                            )}
                        </CardHeader>
                        <CardContent className="grid gap-5">
                            {provider.type === MONGO_IDP_TYPE && <MongoStorageGuidance />}
                            <PluginSchemaForm adapted={adapted} control={form.control} name="config" disabled={!canUpdate} />
                        </CardContent>
                    </Card>
                </fieldset>
            </form>

            {provider.external && <IdentityProviderIntegration provider={provider} />}

            {canUpdate && (
                <SaveBar formId={FORM_ID} dirty={form.formState.isDirty} saving={save.isPending} onDiscard={() => form.reset()} />
            )}

            <Can all={['domain_identity_provider_delete']}>
                <DeleteProviderCard provider={provider} />
            </Can>

            <ConfirmDialog
                open={pendingValues !== null}
                title="Update provider: a user has been modified or deleted"
                description="If you modified an existing user with another username, make sure the password has been modified manually too. Do you want to save your configuration?"
                confirmLabel="Save"
                onOpenChange={open => !open && setPendingValues(null)}
                onConfirm={() => {
                    if (pendingValues) {
                        save.mutate(pendingValues);
                    }
                    setPendingValues(null);
                }}
            />
            {feature && (
                <UpgradeDialog
                    open={upgradeOpen}
                    feature={feature}
                    description={featureDescription(feature)}
                    onOpenChange={setUpgradeOpen}
                />
            )}
        </div>
    );
}
