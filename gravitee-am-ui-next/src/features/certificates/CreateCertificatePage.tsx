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
import { createCertificate, getCertificateSchema, listCertificatePlugins } from '../../lib/api/certificates';
import type { Plugin } from '../../lib/api/plugins';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { certificateTypeName } from './certificate-types';

/** The Angular `CertificateCreationComponent`: pick the certificate plugin, then name it and fill its configuration. */
export function CreateCertificatePage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const listPath = `${basePath}/settings/certificates`;
    const { data: plugins, error } = useQuery({
        queryKey: ['certificate-plugins'],
        queryFn: listCertificatePlugins,
        staleTime: Infinity,
        select: list => list.map(plugin => ({ ...plugin, displayName: certificateTypeName(plugin.id, plugin.name) })),
    });
    const create = useMutation({
        mutationFn: ({ plugin, name, config }: { plugin: Plugin; name: string; config: Record<string, unknown> }) =>
            createCertificate(ref, { name, type: plugin.id, configuration: JSON.stringify(config) }),
        onSuccess: created => {
            void queryClient.invalidateQueries({ queryKey: ['certificates', ref] });
            toast.success(`The certificate ${created.name} has been successfully created.`);
            navigate(`${listPath}/${created.id}`);
        },
        onError: createError => toast.error(`Could not create the certificate. ${createError.message}`),
    });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Certificates', to: listPath },
        { label: 'New certificate' },
    ]);

    return (
        <PluginWizard
            title="New certificate"
            back={{ to: listPath, label: 'Certificates' }}
            pickDescription="Choose your cryptographic algorithms to sign the data (JWT tokens)."
            configureDescription="Create a certificate that can be used for signature, encryption and mTLS."
            noun="certificates"
            plugins={plugins}
            pluginsError={error}
            schemaKey="certificate-schema"
            getSchema={plugin => getCertificateSchema(plugin.id)}
            creating={create.isPending}
            onCreate={(plugin, values) => create.mutate({ plugin, ...values })}
        />
    );
}
