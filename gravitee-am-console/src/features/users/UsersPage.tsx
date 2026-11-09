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
import { Button, DataTable, DataTableEmptyState, DateCell, FacetedFilter } from '@gravitee/graphene-core';
import { PlusIcon, SearchIcon, UsersIcon } from '@gravitee/graphene-core/icons';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { CellContext, ColumnDef } from '@tanstack/react-table';
import { useCallback, useState } from 'react';
import { Link } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { SearchField } from '../../app/components/SearchField';
import { UserAvatar } from '../../app/components/UserAvatar';
import { listApplications } from '../../lib/api/applications';
import { listIdentityProviders } from '../../lib/api/identity-providers';
import { listUsers, type User, type UserSearch } from '../../lib/api/users';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { Can, usePermissions } from '../../lib/session/permissions';
import { UserStatusBadge } from './UserStatusBadge';
import { type AdvancedSearch, NO_ADVANCED_SEARCH, advancedSearchFilter } from './user-helpers';

type Cell = CellContext<User, unknown>;
const DISABLED = 'disabled';
// The Angular advanced search loads the first 50 applications.
const APPLICATION_OPTIONS = 50;

export function UsersPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { has } = usePermissions();
    const canRead = has('domain_user_read');
    const listPath = `${basePath}/settings/users`;
    // Graphene pages start at 1, the Management API pages start at 0.
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    const [text, setText] = useState('');
    const [advanced, setAdvanced] = useState<AdvancedSearch>(NO_ADVANCED_SEARCH);
    // A new key remounts the search field, which empties it.
    const [searchKey, setSearchKey] = useState(0);
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Users' },
    ]);

    // The API reads `q` before `filter`, so a text search and the advanced search replace each other, as in Angular.
    const filter = advancedSearchFilter(advanced);
    const search: UserSearch = filter ? { filter } : { text };
    const searchText = useCallback((value: string) => {
        setText(value);
        setAdvanced(NO_ADVANCED_SEARCH);
        setPage(1);
    }, []);
    const searchAdvanced = (patch: Partial<AdvancedSearch>) => {
        setAdvanced(current => ({ ...current, ...patch }));
        if (text) {
            setText('');
            setSearchKey(key => key + 1);
        }
        setPage(1);
    };
    const clearSearch = () => {
        setText('');
        setAdvanced(NO_ADVANCED_SEARCH);
        setSearchKey(key => key + 1);
        setPage(1);
    };

    const users = useQuery({
        queryKey: ['users', ref, search, page, pageSize],
        queryFn: () => listUsers(ref, search, page - 1, pageSize),
        placeholderData: keepPreviousData,
    });
    // Filter options only: a failure leaves the filter empty.
    const providers = useQuery({ queryKey: ['identity-providers', ref], queryFn: () => listIdentityProviders(ref) });
    const applications = useQuery({
        queryKey: ['applications', ref, 0, APPLICATION_OPTIONS],
        queryFn: () => listApplications(ref, 0, APPLICATION_OPTIONS),
    });

    const searching = text !== '' || filter !== '';
    const isFirstUse = users.data?.totalCount === 0 && !searching;

    const columns: ColumnDef<User, unknown>[] = [
        {
            accessorKey: 'displayName',
            header: 'Name',
            cell: ({ row }) => (
                <div className="flex items-center gap-3">
                    <UserAvatar user={row.original} size="sm" />
                    {canRead ? (
                        <Link to={`${listPath}/${row.original.id}`} className="font-medium hover:underline">
                            {row.original.displayName}
                        </Link>
                    ) : (
                        <span className="font-medium">{row.original.displayName}</span>
                    )}
                </div>
            ),
        },
        { accessorKey: 'username', header: 'Username' },
        { id: 'status', header: 'Status', cell: ({ row }) => <UserStatusBadge user={row.original} /> },
        // Angular shows these columns to a reader only.
        ...(canRead
            ? [
                  {
                      accessorKey: 'source',
                      header: 'Identity provider',
                      cell: ({ row }: Cell) => (row.original.serviceAccount ? 'N/A' : row.original.source),
                  },
                  {
                      accessorKey: 'loggedAt',
                      header: 'Last login',
                      cell: ({ row }: Cell) =>
                          row.original.serviceAccount ? (
                              'N/A'
                          ) : row.original.loggedAt ? (
                              <DateCell value={new Date(row.original.loggedAt)} />
                          ) : (
                              <span className="text-muted-foreground">Never logged in</span>
                          ),
                  },
              ]
            : []),
    ];

    const createButton = (
        <Button asChild>
            <Link to={`${listPath}/new`}>
                <PlusIcon aria-hidden />
                Create user
            </Link>
        </Button>
    );

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Users"
                description="People who can sign in to the applications registered in this domain."
                actions={!users.error && !isFirstUse && <Can all={['domain_user_create']}>{createButton}</Can>}
            />
            {users.error ? (
                <ErrorPanel description="Could not load the users." error={users.error} />
            ) : isFirstUse ? (
                <div className="rounded-lg border border-border">
                    <DataTableEmptyState
                        variant="first-use"
                        icon={<UsersIcon />}
                        title="No users yet"
                        description="Users appear here when they register through an application, or when you create them."
                        primaryAction={has('domain_user_create') ? createButton : undefined}
                    />
                </div>
            ) : (
                <DataTable
                    aria-label="Users"
                    columns={columns}
                    data={users.data?.data ?? []}
                    loading={users.isFetching}
                    skeletonCount={pageSize}
                    serverSide
                    toolbar={
                        <div
                            className="flex flex-wrap items-center gap-2"
                            title='Search by name, username or email, or enter a SCIM filter such as userName sw "alice"'
                        >
                            <SearchField key={searchKey} label="users" onSearch={searchText} />
                            <FacetedFilter
                                title="Identity provider"
                                options={(providers.data ?? []).map(provider => ({ value: provider.id, label: provider.name }))}
                                selected={advanced.providers}
                                onChange={values => searchAdvanced({ providers: values })}
                            />
                            <FacetedFilter
                                title="Application"
                                options={(applications.data?.data ?? []).map(application => ({
                                    value: application.id,
                                    label: application.name,
                                }))}
                                selected={advanced.applications}
                                onChange={values => searchAdvanced({ applications: values })}
                            />
                            <FacetedFilter
                                title="Status"
                                options={[{ value: DISABLED, label: 'Disabled' }]}
                                selected={advanced.disabledOnly ? [DISABLED] : []}
                                onChange={values => searchAdvanced({ disabledOnly: values.includes(DISABLED) })}
                            />
                            {filter && (
                                <Button variant="ghost" size="sm" onClick={() => searchAdvanced(NO_ADVANCED_SEARCH)}>
                                    Reset
                                </Button>
                            )}
                        </div>
                    }
                    emptyMessage={
                        <DataTableEmptyState
                            variant="no-results"
                            icon={<SearchIcon />}
                            title="No users match your search"
                            description='Try another name, username or email, or a SCIM filter such as userName sw "alice".'
                            action={
                                <Button variant="outline" size="sm" onClick={clearSearch}>
                                    Clear filters
                                </Button>
                            }
                        />
                    }
                    pagination={{
                        page,
                        pageSize,
                        totalCount: users.data?.totalCount ?? 0,
                        onPageChange: setPage,
                        onPageSizeChange: size => {
                            setPageSize(size);
                            setPage(1);
                        },
                        pageSizeOptions: [10, 25, 50, 100],
                    }}
                />
            )}
        </div>
    );
}
