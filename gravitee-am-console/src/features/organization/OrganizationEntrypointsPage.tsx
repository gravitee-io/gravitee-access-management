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
import { GlobeIcon, PlusIcon, Trash2Icon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { deleteOrganizationEntrypoint, listOrganizationEntrypoints, type OrganizationEntrypoint } from '../../lib/api/entrypoints';
import { Can, usePermissions } from '../../lib/session/permissions';
import { useSession } from '../../lib/session/session';
import { useCloudMode } from './useCloudMode';
import { useOrganizationPath } from './useOrganizationPath';

export function OrganizationEntrypointsPage() {
    const { user, environments } = useSession();
    const queryClient = useQueryClient();
    const { has } = usePermissions();
    const cloudMode = useCloudMode();
    const listPath = `${useOrganizationPath()}/entrypoints`;
    const [pendingDelete, setPendingDelete] = useState<OrganizationEntrypoint | null>(null);
    const { data, error, isPending, refetch } = useQuery({
        queryKey: ['organization-entrypoints', user.org],
        queryFn: () => listOrganizationEntrypoints(user.org),
    });
    const remove = useMutation({
        mutationFn: (id: string) => deleteOrganizationEntrypoint(user.org, id),
        onSuccess: () => {
            setPendingDelete(null);
            void queryClient.invalidateQueries({ queryKey: ['organization-entrypoints', user.org] });
            toast.success('Entrypoint deleted');
        },
        onError: deleteError => toast.error(`Could not delete the entrypoint. ${deleteError.message}`),
    });

    const columns = useMemo<ColumnDef<OrganizationEntrypoint, unknown>[]>(
        () => [
            {
                accessorKey: 'name',
                header: 'Name',
                cell: ({ row }) => (
                    <div className="flex flex-col">
                        <div className="flex items-center gap-2">
                            {has('organization_entrypoint_read') && !cloudMode ? (
                                <Link to={`${listPath}/${row.original.id}`} className="font-medium hover:underline">
                                    {row.original.name}
                                </Link>
                            ) : (
                                <span className="font-medium">{row.original.name}</span>
                            )}
                            {row.original.defaultEntrypoint && <Badge variant="secondary">Default</Badge>}
                        </div>
                        {row.original.description && <small className="text-muted-foreground">{row.original.description}</small>}
                    </div>
                ),
            },
            {
                accessorKey: 'url',
                header: 'Endpoint',
                cell: ({ row }) => <span className="font-mono text-sm">{row.original.url}</span>,
            },
            {
                id: 'environment',
                header: 'Environment',
                enableSorting: false,
                cell: ({ row }) =>
                    environments.find(environment => environment.id === row.original.environmentId)?.name ??
                    row.original.environmentId ??
                    '',
            },
            {
                id: 'actions',
                header: () => <span className="sr-only">Actions</span>,
                enableSorting: false,
                enableHiding: false,
                cell: ({ row }) =>
                    !cloudMode &&
                    !row.original.defaultEntrypoint && (
                        <Can all={['organization_entrypoint_delete']}>
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
        [has, listPath, cloudMode, environments],
    );

    const rows = useMemo(() => [...(data ?? [])].sort((a, b) => a.name.localeCompare(b.name)), [data]);
    const isFirstUse = data?.length === 0;
    const createButton = cloudMode ? null : (
        <Can all={['organization_entrypoint_create']}>
            <Button size="sm" asChild>
                <Link to={`${listPath}/new`}>
                    <PlusIcon />
                    Create entrypoint
                </Link>
            </Button>
        </Can>
    );

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Entrypoints"
                description="The URLs end-user applications use to contact the AM Gateway. A domain shows the entrypoint that shares one of its sharding tags."
                actions={createButton}
            />
            {error ? (
                <ErrorPanel description="Could not load the entrypoints." error={error} onRetry={() => void refetch()} />
            ) : isFirstUse ? (
                <div className="rounded-lg border border-border">
                    <DataTableEmptyState
                        variant="first-use"
                        icon={<GlobeIcon />}
                        title="Entrypoints will appear here"
                        description="Start creating a entrypoint."
                    />
                </div>
            ) : (
                <DataTable aria-label="Entrypoints" columns={columns} data={rows} loading={isPending} skeletonCount={4} />
            )}
            <ConfirmDialog
                open={pendingDelete !== null}
                title="Delete Entrypoint"
                description="Are you sure you want to delete this entrypoint ?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={open => !open && setPendingDelete(null)}
                onConfirm={() => pendingDelete && remove.mutate(pendingDelete.id)}
            />
        </div>
    );
}
