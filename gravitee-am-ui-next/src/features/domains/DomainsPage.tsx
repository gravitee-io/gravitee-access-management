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
import { BadgeCell, DataTable, DataTableEmptyState, DateCell } from '@gravitee/graphene-core';
import { ShieldIcon, SearchIcon } from '@gravitee/graphene-core/icons';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../app/components/PageHeader';
import { SearchField } from '../../app/components/SearchField';
import { type Domain, listDomains } from '../../lib/api/management-api';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentEnvironment } from '../../lib/session/environment';
import { useSession } from '../../lib/session/session';

// No sortable headers: the Management API list has no sort parameter.
const columns: ColumnDef<Domain, unknown>[] = [
    {
        accessorKey: 'name',
        header: 'Name',
        cell: ({ row }) => (
            <Link to={row.original.hrid} className="font-medium hover:underline">
                {row.original.name}
            </Link>
        ),
    },
    {
        accessorKey: 'description',
        header: 'Description',
        cell: ({ row }) => <span className="text-muted-foreground">{row.original.description || '—'}</span>,
    },
    {
        accessorKey: 'enabled',
        header: 'Status',
        cell: ({ row }) =>
            row.original.enabled ? <BadgeCell value="Enabled" variant="success" /> : <BadgeCell value="Disabled" variant="secondary" />,
    },
    {
        accessorKey: 'updatedAt',
        header: 'Updated',
        cell: ({ row }) => <DateCell value={row.original.updatedAt ? new Date(row.original.updatedAt) : null} />,
    },
];

export function DomainsPage() {
    const { user } = useSession();
    const environment = useCurrentEnvironment();
    // Graphene pages start at 1, the Management API pages start at 0.
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    const [query, setQuery] = useState('');
    const search = useCallback((text: string) => {
        setQuery(text);
        setPage(1);
    }, []);
    useBreadcrumbs([{ label: 'Domains' }]);

    const { data, isFetching } = useQuery({
        queryKey: ['domains', user.org, environment.id, page, pageSize, query],
        queryFn: () => listDomains(user.org, environment.id, page - 1, pageSize, query),
        placeholderData: keepPreviousData,
    });

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Domains" description={`Security domains in ${environment.name}.`} />
            {data?.totalCount === 0 && !query ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<ShieldIcon />}
                    title="No security domains"
                    description="A security domain holds the applications, identity providers and policies of one realm."
                />
            ) : (
                <DataTable
                    aria-label="Security domains"
                    columns={columns}
                    data={data?.data ?? []}
                    loading={isFetching}
                    skeletonCount={pageSize}
                    serverSide
                    enableColumnVisibility
                    toolbar={<SearchField label="domains" onSearch={search} />}
                    emptyMessage={
                        <DataTableEmptyState
                            variant="no-results"
                            icon={<SearchIcon />}
                            title="No domains match your search"
                            description="Change the search text."
                        />
                    }
                    pagination={{
                        page,
                        pageSize,
                        totalCount: data?.totalCount ?? 0,
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
