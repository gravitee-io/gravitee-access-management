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
    Tooltip,
    TooltipContent,
    TooltipTrigger,
    toast,
} from '@gravitee/graphene-core';
import { KeyRoundIcon, MoreVerticalIcon, PlusIcon, StarIcon } from '@gravitee/graphene-core/icons';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ConfirmDialog } from '../../app/components/ConfirmDialog';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import {
    deletePasswordPolicy,
    listPasswordPolicies,
    type PasswordPolicySummary,
    setDefaultPasswordPolicy,
} from '../../lib/api/password-policies';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';

/**
 * The Angular `PasswordPoliciesComponent` (Settings › Password Policies): the domain's password policies, the identity
 * providers that use each one, and the default policy for the providers without one of their own.
 */
export function PasswordPoliciesPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const queryClient = useQueryClient();
    const navigate = useNavigate();
    const { has } = usePermissions();
    const canEdit = has('domain_settings_update');
    const listPath = `${basePath}/settings/password-policies`;
    const [pendingDelete, setPendingDelete] = useState<PasswordPolicySummary | null>(null);
    const { data, error, isFetching } = useQuery({ queryKey: ['password-policies', ref], queryFn: () => listPasswordPolicies(ref) });
    const reload = () => queryClient.invalidateQueries({ queryKey: ['password-policies', ref] });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Password Policies' },
    ]);

    const { mutate: makeDefault } = useMutation({
        mutationFn: (id: string) => setDefaultPasswordPolicy(ref, id),
        onSuccess: () => {
            void reload();
            toast.success('Updated default Password policy');
        },
        onError: () => toast.error("Couldn't set default Password policy"),
    });
    const remove = useMutation({
        mutationFn: (id: string) => deletePasswordPolicy(ref, id),
        onSuccess: (_, id) => {
            setPendingDelete(null);
            void reload();
            toast.success(`Policy ${id} deleted`);
        },
        onError: deleteError => toast.error(deleteError.message || 'Unable to delete the password policy'),
    });

    const columns = useMemo<ColumnDef<PasswordPolicySummary, unknown>[]>(
        () => [
            {
                accessorKey: 'name',
                header: 'Name',
                cell: ({ row }) => (
                    <Link to={`${listPath}/${row.original.id}`} className="font-medium hover:underline">
                        {row.original.name}
                    </Link>
                ),
            },
            {
                id: 'idps',
                header: 'Identity providers',
                enableSorting: false,
                cell: ({ row }) => {
                    const names = row.original.idpsNames ?? [];
                    const badge = <Badge variant="outline">{names.length}</Badge>;
                    return names.length === 0 ? (
                        badge
                    ) : (
                        <Tooltip>
                            <TooltipTrigger asChild>
                                <span>{badge}</span>
                            </TooltipTrigger>
                            <TooltipContent>{names.join(', ')}</TooltipContent>
                        </Tooltip>
                    );
                },
            },
            {
                accessorKey: 'isDefault',
                header: 'Default',
                enableSorting: false,
                cell: ({ row }) =>
                    row.original.isDefault ? (
                        <Badge variant="secondary" title="Applies to identity providers that have no policy of their own">
                            <StarIcon className="mr-1 size-3" aria-hidden />
                            Default
                        </Badge>
                    ) : null,
            },
            {
                id: 'actions',
                header: () => <span className="sr-only">Actions</span>,
                enableSorting: false,
                enableHiding: false,
                cell: ({ row }) => (
                    <div className="flex justify-end">
                        <DropdownMenu>
                            <DropdownMenuTrigger asChild>
                                <Button variant="ghost" size="icon-xs" aria-label={`Actions for ${row.original.name}`}>
                                    <MoreVerticalIcon aria-hidden />
                                </Button>
                            </DropdownMenuTrigger>
                            <DropdownMenuContent align="end">
                                <DropdownMenuItem onSelect={() => navigate(`${listPath}/${row.original.id}`)}>
                                    {canEdit ? 'Edit' : 'View'}
                                </DropdownMenuItem>
                                {canEdit && !row.original.isDefault ? (
                                    <DropdownMenuItem onSelect={() => makeDefault(row.original.id)}>Set as default</DropdownMenuItem>
                                ) : null}
                                {canEdit ? (
                                    <>
                                        <DropdownMenuSeparator />
                                        <DropdownMenuItem variant="destructive" onSelect={() => setPendingDelete(row.original)}>
                                            Delete
                                        </DropdownMenuItem>
                                    </>
                                ) : null}
                            </DropdownMenuContent>
                        </DropdownMenu>
                    </div>
                ),
            },
        ],
        [canEdit, listPath, navigate, makeDefault],
    );

    const isFirstUse = data?.length === 0;
    const createButton = (
        <Button asChild size="sm">
            <Link to={`${listPath}/new`}>
                <PlusIcon aria-hidden />
                Create password policy
            </Link>
        </Button>
    );

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Password Policies"
                description="Rules that passwords must follow, per identity provider. The default policy applies to providers without one of their own."
                actions={!error && data && !isFirstUse && canEdit ? createButton : null}
            />
            {error ? (
                <ErrorPanel description="Could not load the password policies." error={error} />
            ) : isFirstUse ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<KeyRoundIcon />}
                    title="No password policies yet"
                    description="Start configuring your password policies: length, character rules, expiry and history, linked to your identity providers."
                    primaryAction={canEdit ? createButton : undefined}
                />
            ) : (
                <DataTable aria-label="Password policies" columns={columns} data={data ?? []} loading={isFetching} />
            )}
            <ConfirmDialog
                open={pendingDelete !== null}
                title="Delete Password Policy"
                description="Are you sure you want to delete this password policy?"
                confirmLabel="Delete"
                destructive
                pending={remove.isPending}
                onOpenChange={open => !open && setPendingDelete(null)}
                onConfirm={() => pendingDelete && remove.mutate(pendingDelete.id)}
            />
        </div>
    );
}
