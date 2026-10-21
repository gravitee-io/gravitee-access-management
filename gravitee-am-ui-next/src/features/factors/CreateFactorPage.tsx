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
import { Alert, AlertDescription, Field, FieldDescription, FieldLabel, Input, toast } from '@gravitee/graphene-core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { PluginWizard } from '../../app/components/PluginWizard';
import { createFactor, type FactorPlugin, getFactorSchema, listFactorPlugins } from '../../lib/api/factors';
import { listResourcePlugins, listResourcesWhenPermitted } from '../../lib/api/resources';
import { adaptAmSchema, withSchemaDefaults } from '../../lib/forms/am-schema';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { factorPluginName, isFido2, schemaFactorType, withResourceSelect } from './factor-schema';

/** The raw plugin schema. The wizard reads it for the form, the factor type field and the POST for its `factorType` default. */
const schemaQuery = (type: string) => ({ queryKey: ['factor-schema', type], queryFn: () => getFactorSchema(type), staleTime: Infinity });

function FactorTypeField({ type }: { readonly type: string }) {
    const { data } = useQuery(schemaQuery(type));
    return (
        <Field>
            <FieldLabel htmlFor="factor-type">Factor type</FieldLabel>
            <Input id="factor-type" value={(data && schemaFactorType(data)) ?? ''} disabled />
            <FieldDescription>Type of the factor</FieldDescription>
        </Field>
    );
}

/** The Angular `FactorCreationComponent`: pick the factor plugin, then name it and fill its configuration. */
export function CreateFactorPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const listPath = `${basePath}/settings/factors`;
    const { data: plugins, error } = useQuery({
        queryKey: ['factor-plugins'],
        queryFn: listFactorPlugins,
        staleTime: Infinity,
        select: list => list.map(plugin => ({ ...plugin, displayName: factorPluginName(plugin.id, plugin.name) })),
    });
    // The Angular route resolves these before the wizard opens.
    const resources = useQuery({ queryKey: ['factor-resources', ref], queryFn: () => listResourcesWhenPermitted(ref) });
    const resourcePlugins = useQuery({ queryKey: ['resource-plugins'], queryFn: listResourcePlugins, staleTime: Infinity });
    const ready = resources.data && resourcePlugins.data;
    const create = useMutation({
        mutationFn: ({ plugin, name, config }: { plugin: FactorPlugin; name: string; config: Record<string, unknown> }) => {
            const schema: Record<string, unknown> = queryClient.getQueryData(schemaQuery(plugin.id).queryKey) ?? {};
            return createFactor(ref, {
                name,
                type: plugin.id,
                factorType: schemaFactorType(schema) ?? '',
                configuration: JSON.stringify(withSchemaDefaults(adaptAmSchema(schema).schema, config)),
            });
        },
        onSuccess: created => {
            void queryClient.invalidateQueries({ queryKey: ['factors', ref] });
            toast.success(`Factor ${created.name} created`);
            navigate(`${listPath}/${created.id}`);
        },
        onError: createError => toast.error(`Could not create the factor. ${createError.message}`),
    });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Multifactor Auth', to: listPath },
        { label: 'New factor' },
    ]);

    return (
        <PluginWizard<FactorPlugin>
            title="Create a factor"
            back={{ to: listPath, label: 'Multi-factor authentication' }}
            pickDescription="Choose multi-factor auth to add an extra check during the authentication process."
            configureDescription="Configure your factor."
            noun="factors"
            plugins={ready ? plugins : undefined}
            pluginsError={error ?? resourcePlugins.error}
            // The enriched schema holds the domain's resources, so a new resource list builds it again.
            schemaKey={`factor-creation-schema:${ref.environmentId}:${ref.domainId}:${resources.dataUpdatedAt}`}
            getSchema={async plugin =>
                withResourceSelect(
                    await queryClient.fetchQuery(schemaQuery(plugin.id)),
                    plugin.category,
                    resources.data ?? [],
                    resourcePlugins.data ?? [],
                )
            }
            extraFields={plugin => (
                <>
                    <FactorTypeField type={plugin.id} />
                    {isFido2(plugin.id) && (
                        <Alert>
                            <AlertDescription>
                                FIDO2 factor plugin uses the existing WebAuthn configuration. Please configure WebAuthn under Security
                                section if it is not configured yet.
                            </AlertDescription>
                        </Alert>
                    )}
                </>
            )}
            creating={create.isPending}
            onCreate={(plugin, values) => create.mutate({ plugin, ...values })}
        />
    );
}
