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
import { Badge, Button, DataTable, DataTableEmptyState, Switch, toast } from '@gravitee/graphene-core';
import { KeyRoundIcon, PlusIcon, SearchIcon, Trash2Icon } from '@gravitee/graphene-core/icons';
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useCallback, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { SearchField } from '../../app/components/SearchField';
import { deleteScope, listScopes, patchScope, type Scope } from '../../lib/api/scopes';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { Can, usePermissions } from '../../lib/session/permissions';

/**
 * The Angular `DomainSettingsScopesComponent`: the domain's OAuth 2.0 scopes. Discovery and parameterized toggle
 * straight from the list (PATCH), except on a system scope. A system scope cannot be deleted.
 */
export function ScopesPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { has } = usePermissions();
    const queryClient = useQueryClient();
    const listPath = `${basePath}/settings/scopes`;
    // Graphene pages start at 1, the Management API pages start at 0.
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(25);
    const [query, setQuery] = useState('');
    const [pendingDelete, setPendingDelete] = useState<Scope | null>(null);
    const search = useCallback((text: string) => {
        setQuery(text);
        setPage(1);
    }, []);
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Scopes' },
    ]);

    const { data, error, isFetching } = useQuery({
        queryKey: ['scopes', ref, page, pageSize, query],
        queryFn: () => listScopes(ref, page - 1, pageSize, query),
        placeholderData: keepPreviousData,
    });
    const toggle = useMutation({
        mutationFn: ({ scope, patch }: { scope: Scope; patch: { discovery: boolean } | { parameterized: boolean } }) =>
            patchScope(ref, scope.id, patch),
        // The switches stay disabled until the list is fresh, so a second click reads the stored value.
        onSuccess: (_updated, { scope }) => {
            toast.success('Scope updated');
            void queryClient.invalidateQueries({ queryKey: ['scope', ref, scope.id] });
            return queryClient.invalidateQueries({ queryKey: ['scopes', ref] });
        },
        onError: error => toast.error(`Could not update the scope. ${error.message}`),
    });
    const { mutate: toggleScope, isPending: toggling } = toggle;
    const remove = useMutation({
        mutationFn: (scope: Scope) => deleteScope(ref, scope.id),
        onSuccess: (_result, scope) => {
            queryClient.removeQueries({ queryKey: ['scope', ref, scope.id] });
            // Deleting the last row of the last page steps back to the page before.
            if (page > 1 && data?.data.length === 1) {
                setPage(page - 1);
            }
            void queryClient.invalidateQueries({ queryKey: ['scopes', ref] });
            setPendingDelete(null);
            toast.success('Scope deleted');
        },
        onError: error => {
            setPendingDelete(null);
            toast.error(`Could not delete the scope. ${error.message}`);
        },
    });

    // The Angular table sorts each page with the system scopes first, then by key.
    const rows = useMemo(
        () => [...(data?.data ?? [])].sort((a, b) => Number(b.system) - Number(a.system) || a.key.localeCompare(b.key)),
        [data],
    );
    const canEdit = has('domain_scope_update');
    const canRead = has('domain_scope_read');
    const canDelete = has('domain_scope_delete');
    // No sortable headers: the Management API list has no sort parameter.
    const columns = useMemo<ColumnDef<Scope, unknown>[]>(
        () => [
            {
                accessorKey: 'key',
                header: 'Key',
                cell: ({ row }) =>
                    canRead ? (
                        <Link to={`${listPath}/${row.original.id}`} className="font-mono text-sm font-medium hover:underline">
                            {row.original.key}
                        </Link>
                    ) : (
                        <span className="font-mono text-sm font-medium">{row.original.key}</span>
                    ),
            },
            { accessorKey: 'name', header: 'Name' },
            {
                id: 'origin',
                header: 'Origin',
                cell: ({ row }) =>
                    row.original.system ? <Badge variant="secondary">System</Badge> : <Badge variant="outline">Custom</Badge>,
            },
            {
                accessorKey: 'discovery',
                header: 'Discovery',
                cell: ({ row }) => (
                    <Switch
                        aria-label={`List ${row.original.key} in the discovery endpoint`}
                        checked={row.original.discovery}
                        disabled={row.original.system || !canEdit || toggling}
                        onCheckedChange={discovery => toggleScope({ scope: row.original, patch: { discovery } })}
                    />
                ),
            },
            {
                accessorKey: 'parameterized',
                header: 'Parameterized',
                cell: ({ row }) => (
                    <Switch
                        aria-label={`Allow a parameter on ${row.original.key}`}
                        checked={row.original.parameterized}
                        disabled={row.original.system || !canEdit || toggling}
                        onCheckedChange={parameterized => toggleScope({ scope: row.original, patch: { parameterized } })}
                    />
                ),
            },
            {
                id: 'actions',
                enableSorting: false,
                enableHiding: false,
                header: () => <span className="sr-only">Actions</span>,
                cell: ({ row }) =>
                    canDelete && !row.original.system ? (
                        <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={`Delete ${row.original.key}`}
                            onClick={() => setPendingDelete(row.original)}
                        >
                            <Trash2Icon aria-hidden />
                        </Button>
                    ) : null,
            },
        ],
        [canRead, canEdit, canDelete, listPath, toggling, toggleScope],
    );

    const isFirstUse = data?.totalCount === 0 && !query;
    const createButton = (
        <Button asChild>
            <Link to={`${listPath}/new`}>
                <PlusIcon aria-hidden />
                Create scope
            </Link>
        </Button>
    );

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Scopes"
                description="Scopes limit what an application may do on a user's behalf: the access token it receives only carries the scopes the user granted on the consent screen."
                actions={!error && data && !isFirstUse && <Can all={['domain_scope_create']}>{createButton}</Can>}
            />
            {error ? (
                <ErrorPanel description="Could not load the scopes." error={error} />
            ) : isFirstUse ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<KeyRoundIcon />}
                    title="No scopes yet"
                    description="Define scopes such as orders:read to let applications request a limited slice of a user's access."
                    primaryAction={has('domain_scope_create') ? createButton : undefined}
                />
            ) : (
                <DataTable
                    aria-label="Scopes"
                    columns={columns}
                    data={rows}
                    loading={isFetching}
                    skeletonCount={pageSize}
                    serverSide
                    toolbar={<SearchField label="scopes" onSearch={search} />}
                    emptyMessage={
                        <DataTableEmptyState
                            variant="no-results"
                            icon={<SearchIcon />}
                            title="No scopes match your search"
                            description="Try another key or name."
                        />
                    }
                    pagination={{
                        page,
                        pageSize,
                        // The Management API caps a scopes page at 50.
                        pageSizeOptions: [10, 25, 50],
                        totalCount: data?.totalCount ?? 0,
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
                title="Delete Scope"
                description="Are you sure you want to delete this scope ?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={open => !open && setPendingDelete(null)}
                onConfirm={() => pendingDelete && remove.mutate(pendingDelete)}
            />
        </div>
    );
}
