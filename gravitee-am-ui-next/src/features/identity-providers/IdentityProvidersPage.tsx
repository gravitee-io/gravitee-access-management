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
import { BadgeCell, DataTable, DataTableEmptyState } from '@gravitee/graphene-core';
import { SearchIcon, UsersIcon } from '@gravitee/graphene-core/icons';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../app/components/PageHeader';
import { SearchField } from '../../app/components/SearchField';
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
    // The Management API returns every identity provider in one list, so the table pages it client-side.
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    const [query, setQuery] = useState('');
    const search = useCallback((text: string) => {
        setQuery(text.toLowerCase());
        setPage(1);
    }, []);
    const { data, isFetching } = useQuery({ queryKey: ['identity-providers', ref], queryFn: () => listIdentityProviders(ref) });
    const rows = (data ?? []).filter(idp => idp.name.toLowerCase().includes(query) || idp.type.toLowerCase().includes(query));

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Identity providers" description="Where the users of this domain are stored and authenticated." />
            {data?.length === 0 ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<UsersIcon />}
                    title="No identity providers"
                    description="An identity provider stores the users of this domain and checks their credentials."
                />
            ) : (
                <DataTable
                    aria-label="Identity providers"
                    columns={columns}
                    data={rows}
                    loading={isFetching}
                    enableColumnVisibility
                    toolbar={<SearchField label="identity providers" onSearch={search} />}
                    emptyMessage={
                        <DataTableEmptyState
                            variant="no-results"
                            icon={<SearchIcon />}
                            title="No identity providers match your search"
                            description="Change the search text."
                        />
                    }
                    pagination={{
                        page,
                        pageSize,
                        totalCount: rows.length,
                        onPageChange: setPage,
                        onPageSizeChange: size => {
                            setPageSize(size);
                            setPage(1);
                        },
                    }}
                />
            )}
        </div>
    );
}
