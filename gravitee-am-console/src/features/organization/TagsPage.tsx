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
import { useMemo, useState } from 'react';
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
    const [pendingDelete, setPendingDelete] = useState<Tag | null>(null);
    const { data, error, isPending } = useQuery({ queryKey: ['tags', user.org], queryFn: () => listTags(user.org) });
    const remove = useMutation({
        mutationFn: (id: string) => deleteTag(user.org, id),
        onSuccess: () => {
            setPendingDelete(null);
            void queryClient.invalidateQueries({ queryKey: ['tags', user.org] });
            toast.success('Sharding tag deleted');
        },
        onError: deleteError => toast.error(`Could not delete the sharding tag. ${deleteError.message}`),
    });

    const columns = useMemo<ColumnDef<Tag, unknown>[]>(
        () => [
            { accessorKey: 'id', header: 'ID', cell: ({ row }) => <span className="font-mono text-sm">{row.original.id}</span> },
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
                actions={!error && !isFirstUse ? createButton : undefined}
            />
            {error ? (
                <ErrorPanel description="Could not load the sharding tags." error={error} />
            ) : isFirstUse ? (
                <div className="rounded-lg border border-border">
                    <DataTableEmptyState
                        variant="first-use"
                        icon={<ServerIcon />}
                        title="Sharding tags will appear here"
                        description="Start creating a sharding tag."
                        primaryAction={createButton}
                    />
                </div>
            ) : (
                <DataTable aria-label="Sharding tags" columns={columns} data={rows} loading={isPending} skeletonCount={4} />
            )}
            <ConfirmDialog
                open={pendingDelete !== null}
                title="Delete Sharding Tag"
                description="Are you sure you want to delete this sharding tag ?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={open => !open && setPendingDelete(null)}
                onConfirm={() => pendingDelete && remove.mutate(pendingDelete.id)}
            />
        </div>
    );
}
