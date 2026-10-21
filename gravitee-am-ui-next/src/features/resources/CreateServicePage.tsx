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
import { toast } from '@gravitee/graphene-core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { PluginWizard } from '../../app/components/PluginWizard';
import { createResource, getResourceSchema, listResourcePlugins, type ResourcePlugin } from '../../lib/api/resources';
import { adaptAmSchema, withSchemaDefaults } from '../../lib/forms/am-schema';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { HttpFactorResourceNotes } from './HttpFactorResourceNotes';
import { isHttpFactorResource, resourceTypeName } from './resource-types';

const schemaQuery = (type: string) => ({
    queryKey: ['resource-schema', type],
    queryFn: () => getResourceSchema(type),
    staleTime: Infinity,
});

/** The Angular `ResourceCreationComponent`: pick the resource plugin, then name it and fill its configuration. */
export function CreateServicePage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const listPath = `${basePath}/settings/resources`;
    const { data: plugins, error } = useQuery({
        queryKey: ['resource-plugins'],
        queryFn: listResourcePlugins,
        staleTime: Infinity,
        select: list => list.map(plugin => ({ ...plugin, displayName: resourceTypeName(plugin.id, plugin.name) })),
    });
    const create = useMutation({
        mutationFn: async ({ plugin, name, config }: { plugin: ResourcePlugin; name: string; config: Record<string, unknown> }) => {
            const schema = await queryClient.fetchQuery(schemaQuery(plugin.id));
            return createResource(ref, {
                name,
                type: plugin.id,
                configuration: JSON.stringify(withSchemaDefaults(adaptAmSchema(schema).schema, config)),
            });
        },
        onSuccess: created => {
            queryClient.setQueryData(['resource', ref, created.id], created);
            void queryClient.invalidateQueries({ queryKey: ['resources', ref] });
            toast.success(`Service ${created.name} created`);
            navigate(`${listPath}/${created.id}`);
        },
        onError: createError => toast.error(`Could not create the service. ${createError.message}`),
    });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Services', to: listPath },
        { label: 'New service' },
    ]);

    return (
        <PluginWizard<ResourcePlugin>
            title="Create a service"
            back={{ to: listPath, label: 'Services' }}
            pickDescription="Choose the service to create."
            configureDescription="Configure your service."
            noun="services"
            plugins={plugins}
            pluginsError={error}
            schemaKey="resource-creation-schema"
            getSchema={plugin => queryClient.fetchQuery(schemaQuery(plugin.id))}
            extraFields={plugin => isHttpFactorResource(plugin.id) && <HttpFactorResourceNotes />}
            creating={create.isPending}
            onCreate={(plugin, values) => create.mutate({ plugin, ...values })}
        />
    );
}
