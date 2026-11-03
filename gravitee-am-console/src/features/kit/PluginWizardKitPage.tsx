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
    Button,
    Dialog,
    DialogClose,
    DialogContent,
    DialogDescription,
    DialogFooter,
    DialogHeader,
    DialogTitle,
    PageFocused,
    Tabs,
    TabsList,
    TabsTrigger,
} from '@gravitee/graphene-core';
import { useQuery } from '@tanstack/react-query';
import { useMemo, useState } from 'react';
import { useLocation, useSearchParams } from 'react-router-dom';
import { PluginWizard, type WizardPlugin } from '../../app/components/PluginWizard';
import { http } from '../../lib/api/http';
import { compatibleResources } from '../../lib/forms/am-schema';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';

const KINDS = {
    identities: { tab: 'Identity providers', title: 'Create an identity provider', noun: 'identity providers' },
    certificates: { tab: 'Certificates', title: 'Create a certificate', noun: 'certificates' },
    factors: { tab: 'Factors', title: 'Create a factor', noun: 'factors' },
    reporters: { tab: 'Reporters', title: 'Create a reporter', noun: 'reporters' },
    resources: { tab: 'Resources', title: 'Create a resource', noun: 'resources' },
    sample: { tab: 'Sample', title: 'Create a sample plugin', noun: 'plugins' },
} as const;

type Kind = keyof typeof KINDS;

function isKind(value: string | null): value is Kind {
    return !!value && value in KINDS;
}

type KitPlugin = WizardPlugin & { readonly category?: string; readonly categories?: string[] };

const SAMPLE_PLUGIN: KitPlugin = { id: 'kit-sample', name: 'Sample plugin' };
const SAMPLE_RESOURCES = [
    { id: 'smtp-1', name: 'Mail server' },
    { id: 'smtp-2', name: 'Backup mail server' },
];
const SAMPLE_SCHEMA = {
    type: 'object',
    required: ['keystore', 'graviteeResource', 'events'],
    properties: {
        keystore: { type: 'string', title: 'Keystore file', description: 'Any file. The value is the file as JSON.', widget: 'file' },
        hidden: { title: 'No type', default: 'dropped' },
        retries: { type: 'integer', title: 'Retries', default: 3 },
        graviteeResource: { type: 'string', title: 'Resource' },
        events: {
            type: 'array',
            title: 'Events',
            description: 'The events to report.',
            widget: 'graviteeMultiselect',
            itemsDictionaryEndpoint: '/platform/audits/events',
            items: { type: 'string' },
        },
        levels: {
            type: 'array',
            title: 'Levels',
            widget: 'graviteeMultiselect',
            onEmptySelectionMessage: 'Empty means all levels.',
            enum: ['INFO', 'WARN', 'ERROR'],
            items: { type: 'string' },
        },
        authentication: { type: 'boolean', title: 'Authentication', default: false },
        password: { type: 'string', title: 'Password', widget: 'password', condition: 'model.authentication' },
    },
};

export function PluginWizardKitPage() {
    useBreadcrumbs([{ label: 'Component kit' }, { label: 'Plugin wizard' }]);
    const [params, setParams] = useSearchParams();
    const kitPath = useLocation().pathname.replace(/\/plugin-wizard$/, '');
    const requested = params.get('kind');
    const kind: Kind = isKind(requested) ? requested : 'certificates';
    const [category, setCategory] = useState<string>();
    const [created, setCreated] = useState<unknown>();
    const plugins = useQuery({
        queryKey: ['kit-plugins', kind],
        enabled: kind !== 'sample',
        queryFn: () => http<KitPlugin[]>(`/platform/plugins/${kind}${kind === 'identities' ? '?expand=icon' : ''}`),
    });
    const resourcePlugins = useQuery({
        queryKey: ['kit-plugins', 'resources'],
        queryFn: () => http<KitPlugin[]>('/platform/plugins/resources'),
        enabled: kind === 'factors',
    });
    const resources = useMemo(
        () =>
            category && resourcePlugins.data
                ? compatibleResources(
                      category,
                      resourcePlugins.data.map(plugin => ({ id: `${plugin.id}-sample`, name: `${plugin.name} sample`, type: plugin.id })),
                      resourcePlugins.data,
                  )
                : undefined,
        [category, resourcePlugins.data],
    );

    return (
        <div className="flex flex-col gap-6">
            <PageFocused>
                <Tabs
                    value={kind}
                    onValueChange={value => {
                        setCreated(undefined);
                        setParams({ kind: value });
                    }}
                >
                    <TabsList aria-label="Plugin kind">
                        {Object.entries(KINDS).map(([value, { tab }]) => (
                            <TabsTrigger key={value} value={value}>
                                {tab}
                            </TabsTrigger>
                        ))}
                    </TabsList>
                </Tabs>
            </PageFocused>
            <PluginWizard<KitPlugin>
                key={kind}
                title={KINDS[kind].title}
                back={{ to: kitPath, label: 'Component kit' }}
                pickDescription="Pick a plugin."
                configureDescription="Fill its configuration."
                noun={KINDS[kind].noun}
                plugins={kind === 'sample' ? [SAMPLE_PLUGIN] : plugins.data}
                pluginsError={plugins.error}
                schemaKey={`kit-schema-${kind}`}
                getSchema={plugin =>
                    kind === 'sample'
                        ? Promise.resolve(SAMPLE_SCHEMA)
                        : http<Record<string, unknown>>(`/platform/plugins/${kind}/${plugin.id}/schema`)
                }
                resources={kind === 'sample' ? SAMPLE_RESOURCES : resources}
                onSelect={plugin => setCategory(plugin.category)}
                creating={false}
                onCreate={(plugin, values) => setCreated({ type: plugin.id, ...values })}
            />
            <Dialog open={created !== undefined} onOpenChange={open => !open && setCreated(undefined)}>
                <DialogContent className="max-w-[calc(100%-2rem)] sm:max-w-3xl">
                    <DialogHeader>
                        <DialogTitle>Created values</DialogTitle>
                        <DialogDescription>
                            The plugin type, the name and the configuration that the wizard gives to its host page. A plugin page sends them
                            in its create request. This page sends nothing.
                        </DialogDescription>
                    </DialogHeader>
                    <pre aria-label="Created values" className="max-h-96 min-w-0 overflow-auto rounded-md bg-muted p-2 text-xs">
                        {JSON.stringify(created, null, 2)}
                    </pre>
                    <DialogFooter>
                        <DialogClose asChild>
                            <Button type="button">Close</Button>
                        </DialogClose>
                    </DialogFooter>
                </DialogContent>
            </Dialog>
        </div>
    );
}
