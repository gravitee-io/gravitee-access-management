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
import { createDeviceIdentifier, getDeviceIdentifierSchema, listDeviceIdentifierPlugins } from '../../lib/api/device-identifier';
import type { Plugin } from '../../lib/api/plugins';
import { adaptAmSchema, withSchemaDefaults } from '../../lib/forms/am-schema';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { deviceIdentifierTypeName, hasNoSettings } from './device-identifier-types';

const schemaQuery = (type: string) => ({
    queryKey: ['device-identifier-schema', type],
    queryFn: () => getDeviceIdentifierSchema(type),
    staleTime: Infinity,
});

/** The Angular step 2 read-only Type field, and the prototype note for a plugin without settings. */
function TypeField({ type }: { readonly type: string }) {
    const { data } = useQuery(schemaQuery(type));
    return (
        <>
            <Field>
                <FieldLabel htmlFor="device-identifier-type">Type</FieldLabel>
                <Input id="device-identifier-type" value={deviceIdentifierTypeName(type)} disabled />
                <FieldDescription>Device identifier type</FieldDescription>
            </Field>
            {data && hasNoSettings(data) && <p className="text-sm text-muted-foreground">This plugin has no settings to configure.</p>}
        </>
    );
}

/** The Angular `DeviceIdentifierCreationComponent`: pick the device identifier plugin, then name it and fill its configuration. */
export function CreateDeviceIdentifierPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const listPath = `${basePath}/settings/device-identifier`;
    const { data: plugins, error } = useQuery({
        queryKey: ['device-identifier-plugins'],
        queryFn: listDeviceIdentifierPlugins,
        staleTime: Infinity,
        select: list => list.map(plugin => ({ ...plugin, displayName: deviceIdentifierTypeName(plugin.id, plugin.name) })),
    });
    const create = useMutation({
        mutationFn: ({ plugin, name, config }: { plugin: Plugin; name: string; config: Record<string, unknown> }) => {
            const schema: Record<string, unknown> = queryClient.getQueryData(schemaQuery(plugin.id).queryKey) ?? {};
            return createDeviceIdentifier(ref, {
                name,
                type: plugin.id,
                configuration: JSON.stringify(withSchemaDefaults(adaptAmSchema(schema).schema, config)),
            });
        },
        onSuccess: created => {
            queryClient.setQueryData(['device-identifier', ref, created.id], created);
            void queryClient.invalidateQueries({ queryKey: ['device-identifiers', ref] });
            toast.success(`Device identifier ${created.name} created`);
            navigate(`${listPath}/${created.id}`);
        },
        onError: createError => toast.error(`Could not create the device identifier. ${createError.message}`),
    });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Device Identifier', to: listPath },
        { label: 'New device identifier' },
    ]);

    return (
        <PluginWizard<Plugin>
            title="Create a device identifier"
            back={{ to: listPath, label: 'Device identifiers' }}
            pickDescription="Choose a device identifier mechanism."
            configureDescription="Configure your device identifier plugin."
            noun="device identifiers"
            plugins={plugins}
            pluginsError={error}
            schemaKey="device-identifier-creation-schema"
            getSchema={plugin => queryClient.fetchQuery(schemaQuery(plugin.id))}
            extraFields={plugin => <TypeField type={plugin.id} />}
            creating={create.isPending}
            onCreate={(plugin, values) => create.mutate({ plugin, ...values })}
        />
    );
}
