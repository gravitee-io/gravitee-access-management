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
import { Skeleton } from '@gravitee/graphene-core';
import { useQuery } from '@tanstack/react-query';
import { useMemo } from 'react';
import { Navigate } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { useAdaptedSchema } from '../../app/components/PluginSchemaForm';
import { listCertificates } from '../../lib/api/certificates';
import { listDataSources } from '../../lib/api/data-sources';
import { getIdentityProviderSchema, listIdentityProviderPlugins, parseConfiguration } from '../../lib/api/identity-providers';
import { getInstallationConfiguration } from '../../lib/api/management-api';
import { useCurrentDomain } from '../../lib/session/domain';
import { useIdentityProvider } from './IdentityProviderLayout';
import { IdentityProviderSettingsForm } from './IdentityProviderSettingsForm';
import { enrichSettingsSchema, pinnedFields, withBooleanDefaults } from './provider-schema';

/** Name, domain whitelist, plugin configuration, integration help and delete. */
export function IdentityProviderSettingsPage() {
    const { provider, providerPath } = useIdentityProvider();
    const { ref } = useCurrentDomain();
    const enabled = !provider.system;
    const schema = useQuery({
        queryKey: ['identity-provider-schema', provider.type],
        queryFn: () => getIdentityProviderSchema(provider.type),
        enabled,
        staleTime: Infinity,
    });
    const certificates = useQuery({ queryKey: ['certificates', ref], queryFn: () => listCertificates(ref), enabled });
    const dataSources = useQuery({
        queryKey: ['data-sources', ref.organizationId, ref.environmentId],
        queryFn: () => listDataSources(ref.organizationId, ref.environmentId),
        enabled,
    });
    const installation = useQuery({ queryKey: ['installation'], queryFn: getInstallationConfiguration, staleTime: Infinity, enabled });
    const plugins = useQuery({
        queryKey: ['identity-provider-plugins'],
        queryFn: listIdentityProviderPlugins,
        staleTime: Infinity,
        enabled,
    });
    const installationRestricted = installation.data?.systemClusterRestricted === true;
    const providerRestricted = provider.systemClusterRestricted === true;
    const pinned = pinnedFields(provider.type, providerRestricted, installationRestricted);
    const enriched = useMemo(
        () =>
            schema.data && certificates.data && dataSources.data && installation.data
                ? enrichSettingsSchema(schema.data, {
                      type: provider.type,
                      certificates: certificates.data,
                      dataSources: dataSources.data,
                      systemClusterRestricted: installationRestricted,
                      providerRestricted,
                  })
                : undefined,
        [schema.data, certificates.data, dataSources.data, installation.data, installationRestricted, provider.type, providerRestricted],
    );
    const { adapted } = useAdaptedSchema(enriched);

    // A system provider has no editable settings, so it opens its mappers.
    if (provider.system) {
        return <Navigate to={`${providerPath}/mappers`} replace />;
    }
    // The plugin list only gives the licence feature, so the page shows without it, as the layout does.
    const error = schema.error ?? certificates.error ?? dataSources.error ?? installation.error;
    if (error) {
        return <ErrorPanel description="Could not load the provider configuration." error={error} />;
    }
    if (!adapted || !schema.data || plugins.isPending) {
        return <Skeleton className="h-64 w-full" />;
    }
    return (
        <IdentityProviderSettingsForm
            provider={provider}
            adapted={adapted}
            configuration={withBooleanDefaults(schema.data, parseConfiguration(provider))}
            pinned={pinned}
            feature={plugins.data?.find(plugin => plugin.id === provider.type)?.feature}
        />
    );
}
