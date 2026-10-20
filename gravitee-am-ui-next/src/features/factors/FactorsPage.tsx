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
import { Badge, Button, DataTable, DataTableEmptyState } from '@gravitee/graphene-core';
import { PlusIcon, ShieldCheckIcon } from '@gravitee/graphene-core/icons';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { type FactorSummary, listFactors } from '../../lib/api/factors';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { Can, usePermissions } from '../../lib/session/permissions';

/** The Angular `DomainSettingsFactorsComponent`: the domain's factors, with a link to each. */
export function FactorsPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { has } = usePermissions();
    const listPath = `${basePath}/settings/factors`;
    const { data, error, isFetching } = useQuery({ queryKey: ['factors', ref], queryFn: () => listFactors(ref) });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Multifactor Auth' },
    ]);

    const columns = useMemo<ColumnDef<FactorSummary, unknown>[]>(
        () => [
            {
                accessorKey: 'name',
                header: 'Name',
                cell: ({ row }) =>
                    has('domain_factor_read') ? (
                        <Link to={`${listPath}/${row.original.id}`} className="font-medium hover:underline">
                            {row.original.name}
                        </Link>
                    ) : (
                        <span className="font-medium">{row.original.name}</span>
                    ),
            },
            {
                accessorKey: 'factorType',
                header: 'Factor type',
                enableSorting: false,
                cell: ({ row }) => <Badge variant="secondary">{row.original.factorType}</Badge>,
            },
        ],
        [has, listPath],
    );

    const createButton = (
        <Button asChild>
            <Link to={`${listPath}/new`}>
                <PlusIcon aria-hidden />
                Add factor
            </Link>
        </Button>
    );
    const isFirstUse = data?.length === 0;

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Multi-factor authentication"
                description="Factors add an extra check during the login flow to enforce access control."
                actions={
                    !error &&
                    data &&
                    !isFirstUse && (
                        <Can all={['domain_factor_create']}>
                            <div className="flex shrink-0 items-center gap-2">{createButton}</div>
                        </Can>
                    )
                }
            />
            {error ? (
                <ErrorPanel description="Could not load the factors." error={error} />
            ) : isFirstUse ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<ShieldCheckIcon />}
                    title="No factors yet"
                    description="Choose a multi-factor method to add an extra check during the authentication process."
                    primaryAction={has('domain_factor_create') ? createButton : undefined}
                />
            ) : (
                <DataTable aria-label="Factors" columns={columns} data={data ?? []} loading={isFetching} />
            )}
        </div>
    );
}
