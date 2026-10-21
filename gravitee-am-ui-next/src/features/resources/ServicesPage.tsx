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
import { Badge, Button, DataTable, DataTableEmptyState } from '@gravitee/graphene-core';
import { MailIcon, PlugIcon, PlusIcon } from '@gravitee/graphene-core/icons';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { listResourcePlugins, listResources, type Resource } from '../../lib/api/resources';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { Can, usePermissions } from '../../lib/session/permissions';
import { resourceTypeName } from './resource-types';

/** The Angular `DomainSettingsResourcesComponent`: the domain's resources, with a link to each. */
export function ServicesPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { has } = usePermissions();
    const listPath = `${basePath}/settings/resources`;
    const { data, error, isFetching } = useQuery({ queryKey: ['resources', ref], queryFn: () => listResources(ref) });
    const plugins = useQuery({ queryKey: ['resource-plugins'], queryFn: listResourcePlugins, staleTime: Infinity });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Services' },
    ]);

    const icons = useMemo(() => new Map(plugins.data?.map(plugin => [plugin.id, plugin.icon])), [plugins.data]);
    const columns = useMemo<ColumnDef<Resource, unknown>[]>(
        () => [
            {
                accessorKey: 'name',
                header: 'Name',
                cell: ({ row }) => {
                    const icon = icons.get(row.original.type);
                    return (
                        <span className="flex items-center gap-2">
                            {icon ? (
                                <img src={icon} alt="" className="size-6 shrink-0 object-contain" />
                            ) : (
                                <MailIcon aria-hidden className="size-6 shrink-0 text-muted-foreground" />
                            )}
                            <Link to={`${listPath}/${row.original.id}`} className="font-medium hover:underline">
                                {row.original.name}
                            </Link>
                        </span>
                    );
                },
            },
            {
                accessorKey: 'type',
                header: 'Type',
                enableSorting: false,
                cell: ({ row }) => <Badge variant="secondary">{resourceTypeName(row.original.type)}</Badge>,
            },
        ],
        [icons, listPath],
    );

    const createButton = (
        <Button asChild>
            <Link to={`${listPath}/new`}>
                <PlusIcon aria-hidden />
                Add service
            </Link>
        </Button>
    );
    const isFirstUse = data?.length === 0;

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Services"
                description="Services are shared connection settings — an SMTP server, an SMS gateway — that factors and flows reuse."
                actions={
                    !error &&
                    data &&
                    !isFirstUse && (
                        <Can all={['domain_resource_create']}>
                            <div className="flex shrink-0 items-center gap-2">{createButton}</div>
                        </Can>
                    )
                }
            />
            {error ? (
                <ErrorPanel description="Could not load the services." error={error} />
            ) : isFirstUse ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<PlugIcon />}
                    title="No services yet"
                    description="A service is a configuration facility to reuse some settings, such as how to send an email or SMS."
                    primaryAction={has('domain_resource_create') ? createButton : undefined}
                />
            ) : (
                <DataTable aria-label="Services" columns={columns} data={data ?? []} loading={isFetching} />
            )}
        </div>
    );
}
