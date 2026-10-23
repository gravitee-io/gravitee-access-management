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
import { Badge, Button, DataTable, DataTableEmptyState, MonoCell, toast } from '@gravitee/graphene-core';
import { PlusIcon, PuzzleIcon, Trash2Icon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { deleteExtensionGrant, type ExtensionGrantSummary, listExtensionGrants } from '../../lib/api/extension-grants';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { Can, usePermissions } from '../../lib/session/permissions';
import { extensionGrantTypeName } from './extension-grant-types';

/** The Angular `DomainSettingsExtensionGrantsComponent`: the domain's extension grants, deletable after a confirm. */
export function ExtensionGrantsPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { has } = usePermissions();
    const queryClient = useQueryClient();
    const listPath = `${basePath}/settings/extensionGrants`;
    const [pendingDelete, setPendingDelete] = useState<ExtensionGrantSummary | null>(null);
    const { data, error, isFetching } = useQuery({ queryKey: ['extension-grants', ref], queryFn: () => listExtensionGrants(ref) });
    const remove = useMutation({
        mutationFn: (extensionGrantId: string) => deleteExtensionGrant(ref, extensionGrantId),
        onSuccess: () => {
            setPendingDelete(null);
            void queryClient.invalidateQueries({ queryKey: ['extension-grants', ref] });
            toast.success('Extension grant deleted');
        },
        onError: deleteError => toast.error(`Could not delete the extension grant. ${deleteError.message}`),
    });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Extension Grants' },
    ]);

    const columns = useMemo<ColumnDef<ExtensionGrantSummary, unknown>[]>(
        () => [
            {
                accessorKey: 'name',
                header: 'Name',
                cell: ({ row }) =>
                    has('domain_extension_grant_read') ? (
                        <Link to={`${listPath}/${row.original.id}`} className="font-medium hover:underline">
                            {row.original.name}
                        </Link>
                    ) : (
                        <span className="font-medium">{row.original.name}</span>
                    ),
            },
            {
                accessorKey: 'type',
                header: 'Type',
                enableSorting: false,
                cell: ({ row }) => <Badge variant="outline">{extensionGrantTypeName(row.original.type)}</Badge>,
            },
            {
                accessorKey: 'grantType',
                header: 'Grant type',
                enableSorting: false,
                cell: ({ row }) => <MonoCell value={row.original.grantType ?? ''} maxLength={48} />,
            },
            {
                id: 'actions',
                enableSorting: false,
                enableHiding: false,
                header: () => <span className="sr-only">Actions</span>,
                cell: ({ row }) => (
                    <Can all={['domain_extension_grant_delete']}>
                        <Button
                            variant="ghost"
                            size="icon-sm"
                            aria-label={`Delete ${row.original.name}`}
                            onClick={() => setPendingDelete(row.original)}
                        >
                            <Trash2Icon aria-hidden />
                        </Button>
                    </Can>
                ),
            },
        ],
        [has, listPath],
    );

    const createButton = (
        <Button asChild>
            <Link to={`${listPath}/new`}>
                <PlusIcon aria-hidden />
                Create extension grant
            </Link>
        </Button>
    );
    const isFirstUse = data?.length === 0;

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Extension grants"
                description="Extension grants add grant types to the four OAuth 2.0 defines, so applications can request tokens with assertions from other issuers."
                actions={
                    !error &&
                    data &&
                    !isFirstUse && (
                        <Can all={['domain_extension_grant_create']}>
                            <div className="flex shrink-0 items-center gap-2">{createButton}</div>
                        </Can>
                    )
                }
            />
            {error ? (
                <ErrorPanel description="Extension grants could not be retrieved from the backend." error={error} />
            ) : isFirstUse ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<PuzzleIcon />}
                    title="No extension grants yet"
                    description="Accept a JWT bearer assertion or a Cross App Access grant at this domain's token endpoint."
                    primaryAction={has('domain_extension_grant_create') ? createButton : undefined}
                />
            ) : (
                <DataTable aria-label="Extension grants" columns={columns} data={data ?? []} loading={isFetching} skeletonCount={3} />
            )}

            <ConfirmDialog
                open={pendingDelete !== null}
                title="Delete extension grant"
                description={`Delete ${pendingDelete?.name ?? 'this extension grant'}? Applications will no longer be able to use it. This cannot be undone.`}
                confirmLabel="Delete extension grant"
                destructive
                pending={remove.isPending}
                onOpenChange={open => !open && setPendingDelete(null)}
                onConfirm={() => pendingDelete && remove.mutate(pendingDelete.id)}
            />
        </div>
    );
}
