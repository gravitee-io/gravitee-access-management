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
import { Badge, Button, DataTable, DataTableEmptyState, toast } from '@gravitee/graphene-core';
import { NetworkIcon, PlusIcon, SearchIcon, Trash2Icon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { SearchField } from '../../app/components/SearchField';
import {
    deleteIdentityProvider,
    type IdentityProviderSummary,
    listIdentityProviderPlugins,
    listIdentityProviders,
} from '../../lib/api/identity-providers';
import { useCurrentDomain } from '../../lib/session/domain';
import { Can, usePermissions } from '../../lib/session/permissions';

export function IdentityProvidersPage() {
    const { ref, basePath } = useCurrentDomain();
    const queryClient = useQueryClient();
    const { has } = usePermissions();
    const listPath = `${basePath}/settings/providers`;
    // The Management API returns every identity provider in one list, so the table filters and pages it client-side.
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    const [query, setQuery] = useState('');
    const [pendingDelete, setPendingDelete] = useState<IdentityProviderSummary | null>(null);
    const search = useCallback((text: string) => {
        setQuery(text.toLowerCase());
        setPage(1);
    }, []);
    const { data, error, isFetching } = useQuery({ queryKey: ['identity-providers', ref], queryFn: () => listIdentityProviders(ref) });
    const plugins = useQuery({ queryKey: ['identity-provider-plugins'], queryFn: listIdentityProviderPlugins, staleTime: Infinity });
    const remove = useMutation({
        mutationFn: (id: string) => deleteIdentityProvider(ref, id),
        onSuccess: () => {
            setPendingDelete(null);
            void queryClient.invalidateQueries({ queryKey: ['identity-providers', ref] });
            toast.success('Provider deleted');
        },
        onError: deleteError => toast.error(`Could not delete the provider. ${deleteError.message}`),
    });

    // The Angular `displayType`: the plugin display name, else "Custom".
    const typeName = useCallback(
        (type: string) => {
            const plugin = plugins.data?.find(candidate => candidate.id === type);
            return plugin ? (plugin.displayName ?? plugin.name) : 'Custom';
        },
        [plugins.data],
    );
    const columns = useMemo<ColumnDef<IdentityProviderSummary, unknown>[]>(
        () => [
            {
                accessorKey: 'name',
                header: 'Name',
                cell: ({ row }) =>
                    has('domain_identity_provider_read') ? (
                        <Link to={`${listPath}/${row.original.id}`} className="text-left font-medium hover:underline">
                            {row.original.name}
                        </Link>
                    ) : (
                        <span className="font-medium">{row.original.name}</span>
                    ),
            },
            {
                id: 'type',
                accessorFn: row => typeName(row.type),
                header: 'Type',
                enableSorting: false,
                cell: ({ row }) => <Badge variant="outline">{typeName(row.original.type)}</Badge>,
            },
            {
                accessorKey: 'system',
                header: 'Origin',
                enableSorting: false,
                cell: ({ row }) => <Badge variant="secondary">{row.original.system ? 'System' : 'Custom'}</Badge>,
            },
            {
                id: 'actions',
                header: () => <span className="sr-only">Actions</span>,
                enableSorting: false,
                enableHiding: false,
                cell: ({ row }) => (
                    <Can all={['domain_identity_provider_delete']}>
                        <Button
                            variant="ghost"
                            size="icon"
                            aria-label={`Delete ${row.original.name}`}
                            title="Delete"
                            onClick={() => setPendingDelete(row.original)}
                        >
                            <Trash2Icon />
                        </Button>
                    </Can>
                ),
            },
        ],
        [has, listPath, typeName],
    );
    const rows = (data ?? []).filter(idp => idp.name.toLowerCase().includes(query) || typeName(idp.type).toLowerCase().includes(query));
    const isFirstUse = data?.length === 0;
    const createButton = (
        <Can all={['domain_identity_provider_create']}>
            <Button size="sm" asChild>
                <Link to={`${listPath}/new`}>
                    <PlusIcon />
                    Add identity provider
                </Link>
            </Button>
        </Can>
    );

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Identity providers"
                description="Identity providers authenticate the users of this domain’s applications."
                actions={!error && !isFirstUse ? createButton : undefined}
            />
            {error ? (
                <ErrorPanel description="Could not load the identity providers." error={error} />
            ) : isFirstUse ? (
                <div className="rounded-lg border border-border">
                    <DataTableEmptyState
                        variant="first-use"
                        icon={<NetworkIcon />}
                        title="No identity providers yet"
                        description="Identity providers offer user authentication as a service: a user store, a corporate directory or a social login."
                        primaryAction={createButton}
                    />
                </div>
            ) : (
                <DataTable
                    aria-label="Identity providers"
                    columns={columns}
                    data={rows}
                    loading={isFetching || plugins.isPending}
                    skeletonCount={5}
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
            <ConfirmDialog
                open={pendingDelete !== null}
                title="Delete provider"
                description="Are you sure you want to delete this provider?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={open => !open && setPendingDelete(null)}
                onConfirm={() => pendingDelete && remove.mutate(pendingDelete.id)}
            />
        </div>
    );
}
