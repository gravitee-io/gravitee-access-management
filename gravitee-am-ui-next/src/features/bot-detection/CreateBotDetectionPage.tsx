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
import { createBotDetection, getBotDetectionSchema, listBotDetectionPlugins } from '../../lib/api/bot-detection';
import type { Plugin } from '../../lib/api/plugins';
import { adaptAmSchema, withSchemaDefaults } from '../../lib/forms/am-schema';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { botDetectionTypeName, configurationSchema, schemaDetectionType } from './bot-detection-types';

/** The raw plugin schema. The wizard reads it for the form, the detection type field and the POST for its `detectionType` default. */
const schemaQuery = (type: string) => ({
    queryKey: ['bot-detection-schema', type],
    queryFn: () => getBotDetectionSchema(type),
    staleTime: Infinity,
});

function DetectionTypeField({ type }: { readonly type: string }) {
    const { data } = useQuery(schemaQuery(type));
    return (
        <Field>
            <FieldLabel htmlFor="bot-detection-type">Detection type</FieldLabel>
            <Input id="bot-detection-type" value={(data && schemaDetectionType(data)) ?? ''} disabled />
            <FieldDescription>Type of bot detection</FieldDescription>
        </Field>
    );
}

/** The Angular `BotDetectionCreationComponent`: pick the bot detection plugin, then name it and fill its configuration. */
export function CreateBotDetectionPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const listPath = `${basePath}/settings/bot-detection`;
    const { data: plugins, error } = useQuery({
        queryKey: ['bot-detection-plugins'],
        queryFn: listBotDetectionPlugins,
        staleTime: Infinity,
        select: list => list.map(plugin => ({ ...plugin, displayName: botDetectionTypeName(plugin.id, plugin.name) })),
    });
    const create = useMutation({
        mutationFn: ({ plugin, name, config }: { plugin: Plugin; name: string; config: Record<string, unknown> }) => {
            const schema: Record<string, unknown> = queryClient.getQueryData(schemaQuery(plugin.id).queryKey) ?? {};
            return createBotDetection(ref, {
                name,
                type: plugin.id,
                // Without a schema default the key is left out, as Angular does, and the Management API rejects the POST.
                detectionType: schemaDetectionType(schema) as string,
                configuration: JSON.stringify(withSchemaDefaults(adaptAmSchema(configurationSchema(schema)).schema, config)),
            });
        },
        onSuccess: created => {
            void queryClient.invalidateQueries({ queryKey: ['bot-detections', ref] });
            toast.success(`Bot detection ${created.name} created`);
            navigate(`${listPath}/${created.id}`);
        },
        onError: createError => toast.error(`Could not create the bot detection. ${createError.message}`),
    });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Bot Detection', to: listPath },
        { label: 'New bot detection' },
    ]);

    return (
        <PluginWizard<Plugin>
            title="Create a bot detection"
            back={{ to: listPath, label: 'Bot detection' }}
            pickDescription="Choose a bot detection mechanism."
            configureDescription="Configure your bot detection plugin."
            noun="bot detections"
            plugins={plugins}
            pluginsError={error}
            schemaKey="bot-detection-creation-schema"
            getSchema={async plugin => configurationSchema(await queryClient.fetchQuery(schemaQuery(plugin.id)))}
            extraFields={plugin => <DetectionTypeField type={plugin.id} />}
            creating={create.isPending}
            onCreate={(plugin, values) => create.mutate({ plugin, ...values })}
        />
    );
}
