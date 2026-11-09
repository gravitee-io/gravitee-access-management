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
import {
    Badge,
    Button,
    DataTable,
    DataTableEmptyState,
    DateCell,
    FacetedFilter,
    Popover,
    PopoverContent,
    PopoverTrigger,
} from '@gravitee/graphene-core';
import { CircleHelpIcon, PlusIcon, SearchIcon, UsersIcon } from '@gravitee/graphene-core/icons';
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

    // The API reads `q` before `filter`, so a text search and the advanced search replace each other.
    const filter = advancedSearchFilter(advanced);
    const search: UserSearch = filter ? { filter } : { text };
    const searchText = useCallback((value: string) => {
        setText(value);
        setAdvanced(NO_ADVANCED_SEARCH);
        setPage(1);
    }, []);
    const searchAdvanced = (patch: Partial<AdvancedSearch>) => {
        setAdvanced(current => ({ ...current, ...patch }));
        setText('');
        setSearchKey(key => key + 1);
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
    // A search the API rejects, such as a SCIM filter still being typed, keeps the search field on screen.
    const loadFailed = users.error !== null && !searching;

    const columns: ColumnDef<User, unknown>[] = [
        {
            accessorKey: 'displayName',
            header: 'Name',
            cell: ({ row }) => (
                <div className="flex items-center gap-3">
                    <UserAvatar user={row.original} size="sm" brand />
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
        ...(canRead
            ? [
                  {
                      accessorKey: 'source',
                      header: 'Identity provider',
                      cell: ({ row }: Cell) =>
                          row.original.serviceAccount
                              ? 'N/A'
                              : row.original.source && <Badge variant="outline">{row.original.source}</Badge>,
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
                actions={!loadFailed && !isFirstUse && <Can all={['domain_user_create']}>{createButton}</Can>}
            />
            {loadFailed ? (
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
                    data={users.error ? [] : (users.data?.data ?? [])}
                    loading={users.isFetching}
                    skeletonCount={pageSize}
                    serverSide
                    toolbar={
                        <div className="flex w-full flex-wrap items-center gap-2">
                            <SearchField
                                key={searchKey}
                                label="users"
                                defaultValue={filter}
                                onSearch={searchText}
                                className="min-w-64 flex-1"
                            />
                            <SearchSyntaxHelp />
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
                        users.error ? (
                            <ErrorPanel description="Could not run this search. Check the syntax of the SCIM filter." error={users.error} />
                        ) : (
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
                        )
                    }
                    pagination={{
                        page,
                        pageSize,
                        totalCount: users.error ? 0 : (users.data?.totalCount ?? 0),
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

const OPERATORS = [
    ['eq', 'equal'],
    ['ne', 'not equal'],
    ['co', 'contains'],
    ['sw', 'starts with'],
    ['ew', 'ends with'],
    ['pr', 'present (has a value)'],
    ['gt', 'greater than'],
    ['ge', 'greater than or equal to'],
    ['lt', 'less than'],
    ['le', 'less than or equal to'],
];

const EXAMPLES = [
    ['Disabled users', 'enabled eq false'],
    ['Users updated in the second half of 2019', 'updatedAt gt "2019-06-01T00:00:00.000Z" and updatedAt lt "2020-01-01T00:00:00.000Z"'],
    ['Users by first name', 'firstName co "john"'],
];

function SearchSyntaxHelp() {
    return (
        <Popover>
            <PopoverTrigger asChild>
                <Button variant="ghost" size="icon-sm" aria-label="Search syntax">
                    <CircleHelpIcon aria-hidden />
                </Button>
            </PopoverTrigger>
            <PopoverContent align="start" className="w-[28rem] max-w-[calc(100vw-2rem)] text-sm">
                <div className="flex flex-col gap-3">
                    <p>
                        Search by name, username or email, or enter a SCIM 2.0 filter: an attribute of the user, an operator and a value.
                        Combine expressions with <code>and</code> and <code>or</code>. Brackets and <code>not</code> are not supported.
                    </p>
                    <dl className="grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
                        {OPERATORS.map(([operator, meaning]) => (
                            <div key={operator} className="contents">
                                <dt className="font-mono">{operator}</dt>
                                <dd className="text-muted-foreground">{meaning}</dd>
                            </div>
                        ))}
                    </dl>
                    <dl className="flex flex-col gap-2">
                        {EXAMPLES.map(([title, example]) => (
                            <div key={title}>
                                <dt className="text-muted-foreground">{title}</dt>
                                <dd className="font-mono text-xs break-all">{example}</dd>
                            </div>
                        ))}
                    </dl>
                </div>
            </PopoverContent>
        </Popover>
    );
}
