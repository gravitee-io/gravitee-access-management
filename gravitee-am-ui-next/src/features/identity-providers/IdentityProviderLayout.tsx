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
import { Badge, Button, Skeleton, Tabs, TabsList, TabsTrigger } from '@gravitee/graphene-core';
import { ArrowLeftIcon } from '@gravitee/graphene-core/icons';
import { useQuery } from '@tanstack/react-query';
import { Fragment } from 'react';
import { Link, Outlet, useLocation, useNavigate, useOutletContext, useParams } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { NotFoundPanel } from '../../app/components/NotFoundPanel';
import { isNotFound } from '../../lib/api/http';
import { getIdentityProvider, type IdentityProvider, listIdentityProviderPlugins } from '../../lib/api/identity-providers';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';

const TABS = [
    { key: 'settings', label: 'Settings' },
    { key: 'mappers', label: 'User mappers' },
    { key: 'roles', label: 'Role mappers' },
    { key: 'groups', label: 'Group mappers' },
];

export interface IdentityProviderContext {
    readonly provider: IdentityProvider;
    /** The absolute path of the provider list. */
    readonly listPath: string;
    /** The absolute path of this provider. */
    readonly providerPath: string;
}

export function useIdentityProvider(): IdentityProviderContext {
    return useOutletContext<IdentityProviderContext>();
}

/** The Angular `ProviderComponent`: loads the provider once for its tabs. A system provider has no Settings tab. */
export function IdentityProviderLayout() {
    const { providerId = '' } = useParams();
    const { pathname } = useLocation();
    const navigate = useNavigate();
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const listPath = `${basePath}/settings/providers`;
    const providerPath = `${listPath}/${providerId}`;
    const { data: provider, error } = useQuery({
        queryKey: ['identity-provider', ref, providerId],
        queryFn: () => getIdentityProvider(ref, providerId),
    });
    const plugins = useQuery({ queryKey: ['identity-provider-plugins'], queryFn: listIdentityProviderPlugins, staleTime: Infinity });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Providers', to: listPath },
        { label: provider?.name ?? '…' },
    ]);

    if (error) {
        return isNotFound(error) ? (
            <NotFoundPanel
                title="Identity provider not found"
                description={`No identity provider with this id exists in ${domain.name}. It may have been deleted.`}
                action={
                    <Button asChild variant="outline">
                        <Link to={listPath}>Back to providers</Link>
                    </Button>
                }
            />
        ) : (
            <ErrorPanel description="Could not load the identity provider." error={error} />
        );
    }
    if (!provider) {
        return <Skeleton className="h-64 w-full" />;
    }

    const tabs = provider.system ? TABS.filter(tab => tab.key !== 'settings') : TABS;
    const active = pathname.slice(providerPath.length + 1).split('/')[0] || tabs[0].key;
    const plugin = plugins.data?.find(candidate => candidate.id === provider.type);
    const context: IdentityProviderContext = { provider, listPath, providerPath };

    return (
        <div className="flex flex-col gap-6">
            <div className="space-y-1">
                <Button asChild variant="ghost" size="sm" className="-ml-2">
                    <Link to={listPath}>
                        <ArrowLeftIcon aria-hidden />
                        Identity providers
                    </Link>
                </Button>
                <div className="flex flex-wrap items-center gap-2">
                    <h1>{provider.name}</h1>
                    {plugin && <Badge variant="outline">{plugin.displayName ?? plugin.name}</Badge>}
                    {provider.system && <Badge variant="secondary">System</Badge>}
                </div>
            </div>
            <Tabs value={active} onValueChange={value => navigate(`${providerPath}/${value}`)}>
                <TabsList variant="line">
                    {tabs.map(tab => (
                        <TabsTrigger key={tab.key} value={tab.key}>
                            {tab.label}
                        </TabsTrigger>
                    ))}
                </TabsList>
            </Tabs>
            {/* A new provider remounts the tab, so no edit state carries over from the previous one. */}
            <Fragment key={provider.id}>
                <Outlet context={context} />
            </Fragment>
        </div>
    );
}
