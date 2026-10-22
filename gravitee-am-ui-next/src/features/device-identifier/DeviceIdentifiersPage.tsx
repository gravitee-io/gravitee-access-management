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
import { FingerprintIcon, PlusIcon } from '@gravitee/graphene-core/icons';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { type DeviceIdentifierSummary, listDeviceIdentifiers } from '../../lib/api/device-identifier';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { Can, usePermissions } from '../../lib/session/permissions';
import { deviceIdentifierTypeName } from './device-identifier-types';

/** The Angular `DomainSettingsDeviceIdentifiersComponent`: the domain's device identifiers, with a link to each. */
export function DeviceIdentifiersPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { has } = usePermissions();
    const listPath = `${basePath}/settings/device-identifier`;
    const { data, error, isFetching } = useQuery({ queryKey: ['device-identifiers', ref], queryFn: () => listDeviceIdentifiers(ref) });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Device Identifier' },
    ]);

    const columns = useMemo<ColumnDef<DeviceIdentifierSummary, unknown>[]>(
        () => [
            {
                accessorKey: 'name',
                header: 'Name',
                cell: ({ row }) =>
                    has('domain_device_identifier_read') ? (
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
                cell: ({ row }) => <Badge variant="outline">{deviceIdentifierTypeName(row.original.type)}</Badge>,
            },
        ],
        [has, listPath],
    );

    const createButton = (
        <Button asChild>
            <Link to={`${listPath}/new`}>
                <PlusIcon aria-hidden />
                Add device identifier
            </Link>
        </Button>
    );
    const isFirstUse = data?.length === 0;

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Device identifiers"
                description="Device identifiers remember your users’ devices, so multi-factor authentication can skip trusted ones."
                actions={
                    !error &&
                    data &&
                    !isFirstUse && (
                        <Can all={['domain_device_identifier_create']}>
                            <div className="flex shrink-0 items-center gap-2">{createButton}</div>
                        </Can>
                    )
                }
            />
            {error ? (
                <ErrorPanel description="Could not load the device identifiers." error={error} />
            ) : isFirstUse ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<FingerprintIcon />}
                    title="No device identifiers yet"
                    description="Configure a device identifier plugin to remember the devices of your users."
                    primaryAction={has('domain_device_identifier_create') ? createButton : undefined}
                />
            ) : (
                <DataTable aria-label="Device identifiers" columns={columns} data={data ?? []} loading={isFetching} />
            )}
        </div>
    );
}
