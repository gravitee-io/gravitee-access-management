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
import { Field, FieldDescription, FieldLabel, toast } from '@gravitee/graphene-core';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { PluginWizard } from '../../app/components/PluginWizard';
import { TagInput } from '../../app/components/TagInput';
import { listCertificates } from '../../lib/api/certificates';
import { listDataSources } from '../../lib/api/data-sources';
import {
    createIdentityProvider,
    getIdentityProviderSchema,
    listIdentityProviderPlugins,
    serializeConfiguration,
} from '../../lib/api/identity-providers';
import { getInstallationConfiguration } from '../../lib/api/management-api';
import type { IdentityProviderPlugin } from '../../lib/api/plugins';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { enrichCreationSchema } from './provider-schema';

/** The Angular step 1 pre-fills the whitelist of these two plugins. */
const DEFAULT_WHITELIST: Record<string, string[]> = {
    'google-am-idp': ['gmail.com'],
    'azure-ad-am-idp': ['microsoft.com'],
};

export function NewIdentityProviderPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [whitelist, setWhitelist] = useState<string[]>([]);
    const providersPath = `${basePath}/settings/providers`;
    const { data: plugins, error } = useQuery({
        queryKey: ['identity-provider-plugins'],
        queryFn: listIdentityProviderPlugins,
        staleTime: Infinity,
    });
    // The Angular route resolves these before the wizard opens. They enrich the plugin schema.
    const certificates = useQuery({ queryKey: ['certificates', ref], queryFn: () => listCertificates(ref) });
    const dataSources = useQuery({
        queryKey: ['data-sources', ref.organizationId, ref.environmentId],
        queryFn: () => listDataSources(ref.organizationId, ref.environmentId),
    });
    const installation = useQuery({ queryKey: ['installation'], queryFn: getInstallationConfiguration, staleTime: Infinity });
    const ready = certificates.data && dataSources.data && installation.data;
    const create = useMutation({
        mutationFn: async ({ plugin, name, config }: { plugin: IdentityProviderPlugin; name: string; config: Record<string, unknown> }) =>
            createIdentityProvider(ref, {
                name,
                type: plugin.id,
                configuration: await serializeConfiguration(plugin.id, config),
                external: plugin.external,
                domainWhitelist: plugin.external ? whitelist : [],
            }),
        onSuccess: created => {
            void queryClient.invalidateQueries({ queryKey: ['identity-providers', ref] });
            toast.success(`Provider ${created.name} created`);
            navigate(`${providersPath}/${created.id}`);
        },
        onError: error => toast.error(`Could not create the identity provider. ${error.message}`),
    });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Providers', to: providersPath },
        { label: 'New provider' },
    ]);

    return (
        <PluginWizard<IdentityProviderPlugin>
            title="New identity provider"
            back={{ to: providersPath, label: 'Identity providers' }}
            pickDescription="Choose how your users will connect to the authorization server. That could be an Active Directory or a corporate authentication service."
            configureDescription="Configure your identity provider."
            noun="providers"
            plugins={ready ? plugins : undefined}
            pluginsError={error ?? certificates.error ?? dataSources.error ?? installation.error}
            // The enriched schema holds the domain's certificates and the environment's data sources.
            schemaKey={`identity-provider-creation-schema:${ref.environmentId}:${ref.domainId}`}
            getSchema={async plugin =>
                enrichCreationSchema(await getIdentityProviderSchema(plugin.id), {
                    type: plugin.id,
                    certificates: certificates.data ?? [],
                    dataSources: dataSources.data ?? [],
                    systemClusterRestricted: installation.data?.systemClusterRestricted === true,
                })
            }
            onSelect={plugin => setWhitelist(DEFAULT_WHITELIST[plugin.id] ?? [])}
            extraFields={plugin =>
                plugin.external && (
                    <Field>
                        <FieldLabel htmlFor="domain-whitelist">Domain whitelist</FieldLabel>
                        <TagInput
                            id="domain-whitelist"
                            values={whitelist}
                            onChange={setWhitelist}
                            onDuplicate={value => toast.error(`Error : domain whitelist pattern "${value}" already exists`)}
                            placeholder="Enter a domain whitelist pattern"
                        />
                        <FieldDescription>
                            Filters the user based on their email domain matching the list, post authentication of the provider. The user
                            will be logged out otherwise.
                        </FieldDescription>
                    </Field>
                )
            }
            creating={create.isPending}
            onCreate={(plugin, values) => create.mutate({ plugin, ...values })}
        />
    );
}
