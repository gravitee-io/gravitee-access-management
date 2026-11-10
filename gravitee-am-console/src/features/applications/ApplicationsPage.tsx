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
import { Badge, BadgeCell, Button, DataTable, DataTableEmptyState, DateCell } from '@gravitee/graphene-core';
import { AppWindowIcon, PlusIcon, SearchIcon } from '@gravitee/graphene-core/icons';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { SearchField } from '../../app/components/SearchField';
import { type ApplicationSummaryRow, listDomainApplications } from '../../lib/api/applications';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { Can, usePermissions } from '../../lib/session/permissions';
import { APPLICATION_TYPE_LABELS } from './application-types';

function ApplicationStatus({ application }: { readonly application: ApplicationSummaryRow }) {
    if (!application.enabled) {
        return <Badge variant="secondary">Disabled</Badge>;
    }
    return application.template ? <Badge variant="outline">Template</Badge> : <Badge variant="success">Enabled</Badge>;
}

export function ApplicationsPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { has } = usePermissions();
    const listPath = `${basePath}/applications`;
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [query, setQuery] = useState('');
    // A new key remounts the search field, which empties it.
    const [searchKey, setSearchKey] = useState(0);
    const search = useCallback((text: string) => {
        setQuery(text);
        setPage(1);
    }, []);
    const clearSearch = () => {
        setQuery('');
        setSearchKey(key => key + 1);
        setPage(1);
    };
    useBreadcrumbs([{ label: 'Domains', to: domainsPath }, { label: domain.name, to: `${basePath}/dashboard` }, { label: 'Applications' }]);

    const { data, error, isFetching } = useQuery({
        queryKey: ['applications', ref, 'list', page, pageSize, query],
        queryFn: () => listDomainApplications(ref, page - 1, pageSize, query),
        placeholderData: keepPreviousData,
    });
    const isFirstUse = data?.totalCount === 0 && !query;

    const columns: ColumnDef<ApplicationSummaryRow, unknown>[] = [
        {
            accessorKey: 'name',
            header: 'Name',
            cell: ({ row }) => (
                <Link to={`${listPath}/${row.original.id}`} className="font-medium hover:underline">
                    {row.original.name}
                </Link>
            ),
        },
        { id: 'status', header: 'Status', cell: ({ row }) => <ApplicationStatus application={row.original} /> },
        {
            accessorKey: 'type',
            header: 'Type',
            cell: ({ row }) => <BadgeCell value={APPLICATION_TYPE_LABELS[row.original.type] ?? row.original.type} variant="outline" />,
        },
        {
            accessorKey: 'updatedAt',
            header: 'Last updated',
            cell: ({ row }) => <DateCell value={row.original.updatedAt ? new Date(row.original.updatedAt) : null} />,
        },
    ];

    const createButton = (
        <Button asChild>
            <Link to={`${listPath}/new`}>
                <PlusIcon aria-hidden />
                Create application
            </Link>
        </Button>
    );

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Applications"
                description="Applications let users log in and access protected resources through this domain."
                actions={!error && !isFirstUse && <Can all={['application_create']}>{createButton}</Can>}
            />
            {error ? (
                <ErrorPanel description="Could not load the applications." error={error} />
            ) : isFirstUse ? (
                <div className="rounded-lg border border-border">
                    <DataTableEmptyState
                        variant="first-use"
                        icon={<AppWindowIcon />}
                        title="No applications yet"
                        description="Applications can be web apps, single-page apps, native mobile or desktop apps, or backend services."
                        primaryAction={has('application_create') ? createButton : undefined}
                    />
                </div>
            ) : (
                <DataTable
                    aria-label="Applications"
                    columns={columns}
                    data={data?.data ?? []}
                    loading={isFetching}
                    skeletonCount={pageSize}
                    serverSide
                    toolbar={<SearchField key={searchKey} label="applications" onSearch={search} />}
                    emptyMessage={
                        <DataTableEmptyState
                            variant="no-results"
                            icon={<SearchIcon />}
                            title="No applications match your search"
                            description="Try another name or client ID."
                            action={
                                <Button size="sm" variant="outline" onClick={clearSearch}>
                                    Clear search
                                </Button>
                            }
                        />
                    }
                    pagination={{
                        page,
                        pageSize,
                        totalCount: data?.totalCount ?? 0,
                        pageSizeOptions: [10, 25, 50],
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
