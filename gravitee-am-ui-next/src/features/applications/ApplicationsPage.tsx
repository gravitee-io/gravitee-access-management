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
import { AppWindowIcon } from '@gravitee/graphene-core/icons';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { PageHeader } from '../../app/components/PageHeader';
import { type Application, listApplications } from '../../lib/api/management-api';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { APPLICATION_TYPE_LABELS } from './application-types';

const columns: ColumnDef<Application, unknown>[] = [
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
        header: 'Type',
        cell: ({ row }) => <BadgeCell value={APPLICATION_TYPE_LABELS[row.original.type] ?? row.original.type} variant="outline" />,
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

export function ApplicationsPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    // Graphene pages start at 1, the Management API pages start at 0.
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    useBreadcrumbs([{ label: 'Domains', to: domainsPath }, { label: domain.name, to: basePath }, { label: 'Applications' }]);

    const { data, isFetching } = useQuery({
        queryKey: ['applications', ref, page, pageSize],
        queryFn: () => listApplications(ref, page - 1, pageSize),
        placeholderData: keepPreviousData,
    });

    return (
        <div className="flex flex-col gap-6">
            <PageHeader title="Applications" description="Clients that sign users in through this domain." />
            {data?.totalCount === 0 ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<AppWindowIcon />}
                    title="No applications"
                    description="An application is a client that signs users in with this domain."
                />
            ) : (
                <DataTable
                    aria-label="Applications"
                    columns={columns}
                    data={data?.data ?? []}
                    loading={isFetching}
                    skeletonCount={pageSize}
                    serverSide
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
