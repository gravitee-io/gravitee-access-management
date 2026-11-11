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
import { Button, DataTable, DataTableEmptyState, toast } from '@gravitee/graphene-core';
import { PlusIcon, ServerIcon, Trash2Icon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo, useState, useRef } from 'react';
import { Link } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { deleteTag, listTags, type Tag } from '../../lib/api/tags';
import { Can, usePermissions } from '../../lib/session/permissions';
import { useSession } from '../../lib/session/session';
import { useOrganizationPath } from './useOrganizationPath';

export function TagsPage() {
    const { user } = useSession();
    const queryClient = useQueryClient();
    const { has } = usePermissions();
    const listPath = `${useOrganizationPath()}/tags`;
    const listRef = useRef<HTMLDivElement>(null);
    // The Management API returns every row in one list, so the table pages it client-side.
    const [page, setPage] = useState(1);
    const [pageSize, setPageSize] = useState(10);
    const [pendingDelete, setPendingDelete] = useState<Tag | null>(null);
    const { data, error, isPending, refetch } = useQuery({ queryKey: ['tags', user.org], queryFn: () => listTags(user.org) });
    const remove = useMutation({
        mutationFn: (id: string) => deleteTag(user.org, id),
        onSuccess: () => {
            setPendingDelete(null);
            listRef.current?.focus();
            void queryClient.invalidateQueries({ queryKey: ['tags', user.org] });
            toast.success('Sharding tag deleted');
        },
        onError: deleteError => toast.error(`Could not delete the sharding tag. ${deleteError.message}`),
    });

    const columns = useMemo<ColumnDef<Tag, unknown>[]>(
        () => [
            {
                accessorKey: 'name',
                header: 'Name',
                cell: ({ row }) =>
                    has('organization_tag_read') ? (
                        <Link to={`${listPath}/${row.original.id}`} className="text-left font-medium hover:underline">
                            {row.original.name}
                        </Link>
                    ) : (
                        <span className="font-medium">{row.original.name}</span>
                    ),
            },
            { accessorKey: 'id', header: 'ID', cell: ({ row }) => <span className="font-mono text-sm">{row.original.id}</span> },
            {
                accessorKey: 'description',
                header: 'Description',
                enableSorting: false,
                cell: ({ row }) => <span className="block max-w-md truncate text-muted-foreground">{row.original.description ?? '—'}</span>,
            },
            {
                id: 'actions',
                header: () => <span className="sr-only">Actions</span>,
                enableSorting: false,
                enableHiding: false,
                cell: ({ row }) => (
                    <Can all={['organization_tag_delete']}>
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
        [has, listPath],
    );

    const rows = useMemo(() => [...(data ?? [])].sort((a, b) => a.name.localeCompare(b.name)), [data]);
    // A delete can shrink the list below the current page.
    const currentPage = Math.min(page, Math.max(1, Math.ceil(rows.length / pageSize)));
    const isFirstUse = data?.length === 0;
    const createButton = (
        <Can all={['organization_tag_create']}>
            <Button size="sm" asChild>
                <Link to={`${listPath}/new`}>
                    <PlusIcon />
                    Create sharding tag
                </Link>
            </Button>
        </Can>
    );

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Sharding tags"
                description="Sharding tags determine which AM gateways each security domain is deployed to."
                actions={createButton}
            />
            {error ? (
                <ErrorPanel description="Could not load the sharding tags." error={error} onRetry={() => void refetch()} />
            ) : isFirstUse ? (
                <div className="rounded-lg border border-border">
                    <DataTableEmptyState
                        variant="first-use"
                        icon={<ServerIcon />}
                        title="Sharding tags will appear here"
                        description="Create a sharding tag."
                    />
                </div>
            ) : (
                <div ref={listRef} tabIndex={-1} className="outline-none">
                    <DataTable
                        aria-label="Sharding tags"
                        columns={columns}
                        data={rows.slice((currentPage - 1) * pageSize, currentPage * pageSize)}
                        loading={isPending}
                        skeletonCount={pageSize}
                        pagination={{
                            page: currentPage,
                            pageSize,
                            totalCount: rows.length,
                            onPageChange: setPage,
                            onPageSizeChange: size => {
                                setPageSize(size);
                                setPage(1);
                            },
                        }}
                        tableOptions={{ getRowId: row => row.id }}
                    />
                </div>
            )}
            <ConfirmDialog
                open={pendingDelete !== null}
                title="Delete Sharding Tag"
                description="Are you sure you want to delete this sharding tag?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={open => !open && setPendingDelete(null)}
                onConfirm={() => pendingDelete && remove.mutate(pendingDelete.id)}
            />
        </div>
    );
}
