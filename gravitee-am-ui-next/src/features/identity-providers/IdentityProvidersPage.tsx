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
import { BadgeCell, DataTable } from '@gravitee/graphene-core';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../app/components/PageHeader';
import { type IdentityProviderSummary, listIdentityProviders } from '../../lib/api/management-api';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';

const columns: ColumnDef<IdentityProviderSummary, unknown>[] = [
    {
        accessorKey: 'name',
        header: 'Name',
        cell: ({ row }) => (
            <Link to={row.original.id} className="font-medium hover:underline">
                {row.original.name}
            </Link>
        ),
    },
    {
        accessorKey: 'type',
        header: 'Plugin',
        cell: ({ row }) => <span className="font-mono text-xs">{row.original.type}</span>,
    },
    {
        accessorKey: 'system',
        header: 'Origin',
        cell: ({ row }) =>
            row.original.system ? <BadgeCell value="System" variant="secondary" /> : <BadgeCell value="Custom" variant="outline" />,
    },
];

export function IdentityProvidersPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    useBreadcrumbs([{ label: 'Domains', to: domainsPath }, { label: domain.name, to: basePath }, { label: 'Identity providers' }]);
    const { data, isFetching } = useQuery({ queryKey: ['identity-providers', ref], queryFn: () => listIdentityProviders(ref) });

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Identity providers" description="Where the users of this domain are stored and authenticated." />
            <DataTable aria-label="Identity providers" columns={columns} data={data ?? []} loading={isFetching} />
        </div>
    );
}
