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
    DropdownMenu,
    DropdownMenuContent,
    DropdownMenuItem,
    DropdownMenuSeparator,
    DropdownMenuTrigger,
    toast,
} from '@gravitee/graphene-core';
import { MoreVerticalIcon, PlusIcon, ShieldCheckIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { deleteTrustedDomain, listTrustedDomains } from '../../lib/api/trust-domains';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { TrustDomainsTabs } from './TrustDomainsTabs';
import { fromTrustedDomain, keyMaterialSourceLabel, type TrustDomain, trustDomainUsages, trustDomainUsagesLabel } from './trust-domain';

/** The Angular `DomainSettingsTrustDomainsComponent`: the external authorities the domain trusts, their usages and key sources. */
export function TrustDomainsPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const { has } = usePermissions();
    const canCreate = has('domain_trust_domain_create');
    const canRead = has('domain_trust_domain_read');
    const canUpdate = has('domain_trust_domain_update');
    const canDelete = has('domain_trust_domain_delete');
    const listPath = `${basePath}/settings/trust-domains/domains`;
    const [pendingDelete, setPendingDelete] = useState<TrustDomain | null>(null);
    const { data, error, isFetching } = useQuery({ queryKey: ['trusted-domains', ref], queryFn: () => listTrustedDomains(ref) });
    const rows = useMemo(() => (data ?? []).map(fromTrustedDomain), [data]);
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Trusted Domains' },
    ]);

    const remove = useMutation({
        mutationFn: (id: string) => deleteTrustedDomain(ref, id),
        onSuccess: () => {
            setPendingDelete(null);
            void queryClient.invalidateQueries({ queryKey: ['trusted-domains', ref] });
            toast.success('Trusted domain deleted');
        },
        onError: () => toast.error('Failed to delete trusted domain'),
    });

    const columns = useMemo<ColumnDef<TrustDomain, unknown>[]>(
        () => [
            {
                accessorKey: 'name',
                header: 'Name',
                cell: ({ row }) => {
                    const { id, name, description, issuer } = row.original;
                    return canRead ? (
                        <Link to={`${listPath}/${id}`} title={description ?? issuer} className="font-medium hover:underline">
                            {name}
                        </Link>
                    ) : (
                        <span title={description ?? issuer} className="font-medium">
                            {name}
                        </span>
                    );
                },
            },
            {
                id: 'usage',
                header: 'Usage',
                enableSorting: false,
                cell: ({ row }) => {
                    const usages = trustDomainUsages(row.original);
                    const label = trustDomainUsagesLabel(row.original);
                    return (
                        <Badge variant="outline" title={label}>
                            {usages.length > 1 ? `${usages.length} usages` : label}
                        </Badge>
                    );
                },
            },
            {
                id: 'keySource',
                header: 'Key source',
                enableSorting: false,
                cell: ({ row }) =>
                    row.original.keyMaterial ? (
                        <span className="text-sm">{keyMaterialSourceLabel(row.original.keyMaterial.source)}</span>
                    ) : (
                        <span className="text-sm text-muted-foreground">None</span>
                    ),
            },
            {
                id: 'actions',
                header: () => <span className="sr-only">Actions</span>,
                enableSorting: false,
                enableHiding: false,
                cell: ({ row }) =>
                    canRead || canDelete ? (
                        <div className="flex justify-end">
                            <DropdownMenu>
                                <DropdownMenuTrigger asChild>
                                    <Button variant="ghost" size="icon-xs" aria-label={`Actions for ${row.original.name}`}>
                                        <MoreVerticalIcon aria-hidden />
                                    </Button>
                                </DropdownMenuTrigger>
                                <DropdownMenuContent align="end">
                                    {canRead ? (
                                        <DropdownMenuItem onSelect={() => navigate(`${listPath}/${row.original.id}`)}>
                                            {canUpdate ? 'Settings' : 'View'}
                                        </DropdownMenuItem>
                                    ) : null}
                                    {canRead && canDelete ? <DropdownMenuSeparator /> : null}
                                    {canDelete ? (
                                        <DropdownMenuItem variant="destructive" onSelect={() => setPendingDelete(row.original)}>
                                            Delete
                                        </DropdownMenuItem>
                                    ) : null}
                                </DropdownMenuContent>
                            </DropdownMenu>
                        </div>
                    ) : null,
            },
        ],
        [canRead, canUpdate, canDelete, listPath, navigate],
    );

    const isFirstUse = data?.length === 0;
    const createButton = (
        <Button asChild size="sm">
            <Link to={`${listPath}/new`}>
                <PlusIcon aria-hidden />
                Add trusted domain
            </Link>
        </Button>
    );

    return (
        <div className="flex flex-col gap-6">
            <TrustDomainsTabs active="domains" actions={!error && data && !isFirstUse && canCreate ? createButton : null} />
            {error ? (
                <ErrorPanel description="Could not load the trusted domains." error={error} />
            ) : isFirstUse ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<ShieldCheckIcon />}
                    title="No trusted domains yet"
                    description="Trust an external issuer to accept its tokens in a token exchange, a SPIFFE trust domain to accept workload identities, or an authority to issue Cross App Access tokens towards."
                    primaryAction={canCreate ? createButton : undefined}
                />
            ) : (
                <DataTable aria-label="Trusted domains" columns={columns} data={rows} loading={isFetching} />
            )}
            <ConfirmDialog
                open={pendingDelete !== null}
                title="Delete Trusted Domain"
                description={`Are you sure you want to delete "${pendingDelete?.name ?? ''}"?`}
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={open => !open && setPendingDelete(null)}
                onConfirm={() => pendingDelete?.id && remove.mutate(pendingDelete.id)}
            />
        </div>
    );
}
