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
import { BotIcon, PlusIcon } from '@gravitee/graphene-core/icons';
import { useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useMemo } from 'react';
import { Link } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { type BotDetectionSummary, listBotDetections } from '../../lib/api/bot-detection';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { Can, usePermissions } from '../../lib/session/permissions';
import { botDetectionTypeName } from './bot-detection-types';

/** The Angular `DomainSettingsBotDetectionsComponent`: the domain's bot detections, with a link to each. */
export function BotDetectionsPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const { has } = usePermissions();
    const listPath = `${basePath}/settings/bot-detection`;
    const { data, error, isFetching } = useQuery({ queryKey: ['bot-detections', ref], queryFn: () => listBotDetections(ref) });
    useBreadcrumbs([
        { label: 'Domains', to: domainsPath },
        { label: domain.name, to: `${basePath}/dashboard` },
        { label: 'Settings', to: `${basePath}/settings/general` },
        { label: 'Bot Detection' },
    ]);

    const columns = useMemo<ColumnDef<BotDetectionSummary, unknown>[]>(
        () => [
            {
                accessorKey: 'name',
                header: 'Name',
                cell: ({ row }) =>
                    has('domain_bot_detection_read') ? (
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
                cell: ({ row }) => <Badge variant="outline">{botDetectionTypeName(row.original.type)}</Badge>,
            },
            {
                accessorKey: 'detectionType',
                header: 'Detection type',
                enableSorting: false,
                cell: ({ row }) => <Badge variant="secondary">{row.original.detectionType}</Badge>,
            },
        ],
        [has, listPath],
    );

    const createButton = (
        <Button asChild>
            <Link to={`${listPath}/new`}>
                <PlusIcon aria-hidden />
                Add bot detection
            </Link>
        </Button>
    );
    const isFirstUse = data?.length === 0;

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Bot detection"
                description="Bot detection protects the sign-in, sign-up and forgot password pages against bots."
                actions={
                    !error &&
                    data &&
                    !isFirstUse && (
                        <Can all={['domain_bot_detection_create']}>
                            <div className="flex shrink-0 items-center gap-2">{createButton}</div>
                        </Can>
                    )
                }
            />
            {error ? (
                <ErrorPanel description="Could not load the bot detections." error={error} />
            ) : isFirstUse ? (
                <DataTableEmptyState
                    variant="first-use"
                    icon={<BotIcon />}
                    title="No bot detection yet"
                    description="Bot detection plugins protect your applications against bots."
                    primaryAction={has('domain_bot_detection_create') ? createButton : undefined}
                />
            ) : (
                <DataTable aria-label="Bot detections" columns={columns} data={data ?? []} loading={isFetching} />
            )}
        </div>
    );
}
