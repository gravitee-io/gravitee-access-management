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
import { Field, FieldDescription, FieldLabel, Input, toast } from '@gravitee/graphene-core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { PluginWizard } from '../../app/components/PluginWizard';
import { createExtensionGrant, getExtensionGrantSchema, listExtensionGrantPlugins } from '../../lib/api/extension-grants';
import type { Plugin } from '../../lib/api/plugins';
import { adaptAmSchema, withSchemaDefaults } from '../../lib/forms/am-schema';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import {
    configurationSchema,
    extensionGrantTypeName,
    GRANT_TYPE_DESCRIPTION,
    schemaGrantType,
    toConfiguration,
} from './extension-grant-types';

/** The raw plugin schema. The wizard reads it for the form, the grant type field and the POST for its `grantType` default. */
const schemaQuery = (type: string) => ({
    queryKey: ['extension-grant-schema', type],
    queryFn: () => getExtensionGrantSchema(type),
    staleTime: Infinity,
});

function GrantTypeField({ type }: { readonly type: string }) {
    const { data } = useQuery(schemaQuery(type));
    return (
        <Field>
            <FieldLabel htmlFor="extension-grant-type">Grant type</FieldLabel>
            <Input id="extension-grant-type" value={(data && schemaGrantType(data)) ?? ''} disabled />
            <FieldDescription>{GRANT_TYPE_DESCRIPTION}</FieldDescription>
        </Field>
    );
}

/** The Angular `ExtensionGrantCreationComponent`: pick the extension grant plugin, then name it and fill its configuration. */
export function CreateExtensionGrantPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const listPath = `${basePath}/settings/extensionGrants`;
    const { data: plugins, error } = useQuery({
        queryKey: ['extension-grant-plugins'],
        queryFn: listExtensionGrantPlugins,
        staleTime: Infinity,
        select: list => list.map(plugin => ({ ...plugin, displayName: extensionGrantTypeName(plugin.id, plugin.name) })),
    });
    const create = useMutation({
        mutationFn: async ({ plugin, name, config }: { plugin: Plugin; name: string; config: Record<string, unknown> }) => {
            const schema = await queryClient.fetchQuery(schemaQuery(plugin.id));
            const grantType = schemaGrantType(schema);
            // Angular disables Create without a grant type. The Management API requires one.
            if (!grantType) throw new Error('The plugin defines no grant type.');
            const configSchema = adaptAmSchema(configurationSchema(schema)).schema;
            return createExtensionGrant(ref, {
                name,
                type: plugin.id,
                grantType,
                configuration: toConfiguration(configSchema, withSchemaDefaults(configSchema, config)),
            });
        },
        onSuccess: created => {
            queryClient.setQueryData(['extension-grant', ref, created.id], created);
            void queryClient.invalidateQueries({ queryKey: ['extension-grants', ref] });
            toast.success(`Extension grant ${created.name} created`);
            navigate(`${listPath}/${created.id}`);
        },
        onError: createError => toast.error(`Could not create the extension grant. ${createError.message}`),
    });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Extension Grants', to: listPath },
        { label: 'New extension grant' },
    ]);

    return (
        <PluginWizard<Plugin>
            title="Create an extension grant"
            back={{ to: listPath, label: 'Extension grants' }}
            pickDescription="Choose grant type flow to request OAuth 2.0 tokens."
            configureDescription="Configure your extension grant."
            noun="extension grants"
            plugins={plugins}
            pluginsError={error}
            schemaKey="extension-grant-creation-schema"
            getSchema={async plugin => configurationSchema(await queryClient.fetchQuery(schemaQuery(plugin.id)))}
            extraFields={plugin => <GrantTypeField type={plugin.id} />}
            creating={create.isPending}
            onCreate={(plugin, values) => create.mutate({ plugin, ...values })}
        />
    );
}
