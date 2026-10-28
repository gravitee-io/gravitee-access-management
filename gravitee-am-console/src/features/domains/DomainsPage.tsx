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
    BadgeCell,
    Button,
    DataTable,
    DataTableEmptyState,
    DateCell,
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuTrigger,
    Label,
    Switch,
    toast,
} from '@gravitee/graphene-core';
import { GlobeIcon, MoreVerticalIcon, PlusIcon, SearchIcon, StarIcon } from '@gravitee/graphene-core/icons';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { SearchField } from '../../app/components/SearchField';
import { type Domain, listDomainsByIds, searchDomains } from '../../lib/api/domains';
import { type UserPreferences, updateUserPreferences } from '../../lib/api/management-api';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentEnvironment } from '../../lib/session/environment';
import { Can, usePermissions } from '../../lib/session/permissions';
import { useUserPreferences } from '../../lib/session/preferences';
import { useSession } from '../../lib/session/session';

const NO_PINS: readonly string[] = [];

/** The Angular `DomainsComponent`: search, "Pinned only", and the user's default and pinned domains. */
export function DomainsPage() {
    const { user } = useSession();
    const environment = useCurrentEnvironment();
    const { has } = usePermissions();
    const queryClient = useQueryClient();
    const envPath = `/environments/${environment.hrids[0]}`;
    const listPath = `${envPath}/domains`;
    // Graphene pages start at 1, the Management API pages start at 0.
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    const [query, setQuery] = useState('');
    const [pinnedOnly, setPinnedOnly] = useState(false);
    const search = useCallback((text: string) => {
        setQuery(text);
        setPage(1);
    }, []);
    useBreadcrumbs([{ label: 'Domains' }]);

    const preferences = useUserPreferences();
    const pinnedIds = preferences.data?.pinnedDomainIds ?? NO_PINS;
    const defaultDomainId = preferences.data?.defaultDomainId;

    const paged = useQuery({
        queryKey: ['domains', user.org, environment.id, page, pageSize, query],
        queryFn: () => searchDomains(user.org, environment.id, page - 1, pageSize, query),
        placeholderData: keepPreviousData,
        enabled: !pinnedOnly,
    });
    const pinned = useQuery({
        queryKey: ['domains', user.org, environment.id, 'pinned', pinnedIds],
        queryFn: () => listDomainsByIds(user.org, environment.id, pinnedIds),
        enabled: pinnedOnly && pinnedIds.length > 0,
    });

    // Pinned only reads the pinned domains, then filters by name and pages in memory, as Angular does.
    const pinnedRows = useMemo(() => {
        const needle = query.toLowerCase();
        return pinnedIds.length === 0 ? [] : (pinned.data?.data ?? []).filter(domain => domain.name.toLowerCase().includes(needle));
    }, [pinned.data, pinnedIds.length, query]);
    const rows = pinnedOnly ? pinnedRows.slice((page - 1) * pageSize, page * pageSize) : (paged.data?.data ?? []);
    const totalCount = pinnedOnly ? pinnedRows.length : (paged.data?.totalCount ?? 0);
    const error = (pinnedOnly ? pinned.error : paged.error) ?? preferences.error;
    const isFirstUse = !pinnedOnly && paged.data?.totalCount === 0 && !query;

    const savePreferences = useMutation({
        mutationFn: ({ next }: { next: UserPreferences; message: string }) => updateUserPreferences(next),
        onSuccess: (updated, { message }) => {
            queryClient.setQueryData(['user-preferences'], updated);
            toast.success(message);
        },
        onError: failure => toast.error(`Could not update your preferences. ${failure.message}`),
    });

    const togglePin = (domain: Domain) => {
        const isPinned = pinnedIds.includes(domain.id);
        savePreferences.mutate({
            next: {
                ...preferences.data,
                pinnedDomainIds: isPinned ? pinnedIds.filter(id => id !== domain.id) : [...pinnedIds, domain.id],
            },
            message: isPinned ? `${domain.name} unpinned` : `${domain.name} pinned`,
        });
    };

    const toggleDefault = (domain: Domain) => {
        const unset = defaultDomainId === domain.id;
        savePreferences.mutate({
            next: {
                ...preferences.data,
                defaultDomainId: unset ? null : domain.id,
                defaultEnvironmentId: unset ? null : environment.id,
            },
            message: unset ? 'Default domain unset' : `${domain.name} is now your default domain`,
        });
    };

    const columns: ColumnDef<Domain, unknown>[] = [
        {
            accessorKey: 'name',
            header: 'Name',
            cell: ({ row }) => (
                <div className="flex flex-col">
                    <span className="flex items-center gap-1.5">
                        <Link to={`${listPath}/${row.original.hrid}`} className="font-medium hover:underline">
                            {row.original.name}
                        </Link>
                        {defaultDomainId === row.original.id && (
                            <StarIcon className="size-3.5 fill-current text-warning" aria-label="Default domain" />
                        )}
                    </span>
                    {row.original.description && <span className="text-sm text-muted-foreground">{row.original.description}</span>}
                </div>
            ),
        },
        {
            accessorKey: 'enabled',
            header: 'Status',
            cell: ({ row }) =>
                row.original.enabled ? <BadgeCell value="Enabled" variant="success" /> : <BadgeCell value="Disabled" variant="secondary" />,
        },
        {
            accessorKey: 'updatedAt',
            header: 'Last updated',
            cell: ({ row }) => <DateCell value={row.original.updatedAt ? new Date(row.original.updatedAt) : null} />,
        },
        {
            id: 'actions',
            header: () => <span className="sr-only">Actions</span>,
            enableHiding: false,
            cell: ({ row }) => (
                <div className="flex justify-end">
                    <DropdownMenu>
                        <DropdownMenuTrigger asChild>
                            <Button variant="ghost" size="icon-xs" aria-label={`Actions for ${row.original.name}`}>
                                <MoreVerticalIcon className="size-4" aria-hidden />
                            </Button>
                        </DropdownMenuTrigger>
                        <DropdownMenuContent align="end">
                            <DropdownMenuItem onSelect={() => toggleDefault(row.original)}>
                                {defaultDomainId === row.original.id ? 'Unset default domain' : 'Set as default domain'}
                            </DropdownMenuItem>
                            <DropdownMenuItem onSelect={() => togglePin(row.original)}>
                                {pinnedIds.includes(row.original.id) ? 'Unpin domain' : 'Pin domain'}
                            </DropdownMenuItem>
                        </DropdownMenuContent>
                    </DropdownMenu>
                </div>
            ),
        },
    ];

    const createButton = (
        <Button asChild>
            <Link to={`${envPath}/new-domain`}>
                <PlusIcon aria-hidden />
                Create domain
            </Link>
        </Button>
    );

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Domains"
                description="Security domains group applications, users and identity providers behind one authorization server."
                actions={!error && !isFirstUse && <Can all={['domain_create']}>{createButton}</Can>}
            />
            {error ? (
                <ErrorPanel description="Could not load the security domains." error={error} />
            ) : isFirstUse ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<GlobeIcon />}
                    title="No security domains yet"
                    description="A security domain lets you register applications and users and manage identity providers, for centralized authentication, authorization and user management."
                    primaryAction={has('domain_create') ? createButton : undefined}
                />
            ) : (
                <DataTable
                    aria-label="Security domains"
                    columns={columns}
                    data={rows}
                    loading={pinnedOnly ? pinned.isFetching : paged.isFetching}
                    skeletonCount={pageSize}
                    serverSide
                    toolbar={
                        <>
                            <SearchField label="domains" onSearch={search} />
                            <div className="flex items-center gap-2">
                                <Switch
                                    id="domains-pinned-only"
                                    checked={pinnedOnly}
                                    onCheckedChange={checked => {
                                        setPinnedOnly(checked);
                                        setPage(1);
                                    }}
                                />
                                <Label htmlFor="domains-pinned-only" className="text-sm font-normal">
                                    Pinned only
                                </Label>
                            </div>
                        </>
                    }
                    emptyMessage={
                        <DataTableEmptyState
                            variant="no-results"
                            icon={<SearchIcon />}
                            title={pinnedOnly && pinnedIds.length === 0 ? 'No pinned domains' : 'No domains match your search'}
                            description={
                                pinnedOnly && pinnedIds.length === 0
                                    ? 'Pin a domain from its actions menu to find it here.'
                                    : 'Try another name, or clear the filters.'
                            }
                        />
                    }
                    pagination={{
                        page,
                        pageSize,
                        totalCount,
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
