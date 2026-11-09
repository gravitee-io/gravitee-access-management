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
    DateCell,
    DatePicker,
    Dialog,
    DialogContent,
    DialogDescription,
    DialogHeader,
    DialogTitle,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
} from '@gravitee/graphene-core';
import { CodeEditor } from '@gravitee/graphene-core/code-editor';
import { CircleCheckIcon, CircleXIcon, EyeIcon, RefreshCwIcon, ScrollTextIcon } from '@gravitee/graphene-core/icons';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import type { ColumnDef } from '@tanstack/react-table';
import { useState } from 'react';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { type Audit, type AuditQuery, listAuditEventTypes, listUserAudits } from '../../lib/api/users';
import { useCurrentDomain } from '../../lib/session/domain';
import { usePermissions } from '../../lib/session/permissions';
import { useUser } from './UserLayout';
import { auditDetails, formatAuditDate, rangeStart, TIME_RANGES, type TimeRangeId } from './audit-helpers';

const ANY = '__any__';
const PAGE_SIZE = 10;

type Filters = {
    readonly type: string;
    readonly status: string;
    readonly range: TimeRangeId;
    readonly start?: Date;
    readonly end?: Date;
};

const NO_FILTERS: Filters = { type: ANY, status: ANY, range: '1d' };

function AuditStatusBadge({ status }: { readonly status?: 'success' | 'failure' }) {
    return status === 'success' ? (
        <Badge variant="success">
            <CircleCheckIcon className="mr-1 size-3" aria-hidden />
            Success
        </Badge>
    ) : (
        <Badge variant="destructive">
            <CircleXIcon className="mr-1 size-3" aria-hidden />
            Failure
        </Badge>
    );
}

/** The Angular `UserHistoryComponent`: the user's audit events, filtered by type, status and period. */
export function UserHistoryPage() {
    const { user } = useUser();
    const { ref } = useCurrentDomain();
    const canReadDetails = usePermissions().has('domain_audit_read');
    const [filters, setFilters] = useState<Filters>(NO_FILTERS);
    const [page, setPage] = useState(1);
    const [selected, setSelected] = useState<Audit | null>(null);
    const filter = (patch: Partial<Filters>) => {
        setFilters(current => ({ ...current, ...patch }));
        setPage(1);
    };

    const audits = useQuery({
        queryKey: ['user-audits', ref, user.id, filters, page],
        // The period ends now at each fetch, so Refresh moves it forward.
        queryFn: () => {
            const now = Date.now();
            const query: AuditQuery = {
                page: page - 1,
                size: PAGE_SIZE,
                from: filters.start ? filters.start.getTime() : rangeStart(filters.range, now),
                to: filters.end ? filters.end.getTime() : now,
                type: filters.type === ANY ? undefined : filters.type,
                status: filters.status === ANY ? undefined : (filters.status as AuditQuery['status']),
            };
            return listUserAudits(ref, user.id, query);
        },
        placeholderData: keepPreviousData,
    });
    const eventTypes = useQuery({ queryKey: ['audit-event-types'], queryFn: listAuditEventTypes, staleTime: Infinity });

    const filtered = filters.type !== ANY || filters.status !== ANY || Boolean(filters.start) || Boolean(filters.end);
    const columns: ColumnDef<Audit, unknown>[] = [
        { accessorKey: 'timestamp', header: 'Date', cell: ({ row }) => <DateCell value={new Date(row.original.timestamp)} /> },
        {
            accessorKey: 'type',
            header: 'Event',
            cell: ({ row }) => (
                <Badge variant="outline" className="font-mono">
                    {row.original.type}
                </Badge>
            ),
        },
        { id: 'status', header: 'Status', cell: ({ row }) => <AuditStatusBadge status={row.original.outcome?.status} /> },
        {
            id: 'details',
            header: () => <span className="sr-only">Details</span>,
            enableSorting: false,
            cell: ({ row }) =>
                canReadDetails && (
                    <Button
                        variant="ghost"
                        size="icon-sm"
                        aria-label={`View ${row.original.type} details`}
                        onClick={() => setSelected(row.original)}
                    >
                        <EyeIcon aria-hidden />
                    </Button>
                ),
        },
    ];

    if (audits.error) {
        return <ErrorPanel description="Could not load the user history." error={audits.error} />;
    }

    return (
        <div className="flex flex-col gap-4">
            <p className="text-sm text-muted-foreground">History of the user&apos;s account activity.</p>
            <DataTable
                aria-label="User history"
                columns={columns}
                data={audits.data?.data ?? []}
                loading={audits.isFetching}
                skeletonCount={PAGE_SIZE}
                serverSide
                pagination={{ page, pageSize: PAGE_SIZE, totalCount: audits.data?.totalCount ?? 0, onPageChange: setPage }}
                emptyMessage={
                    <DataTableEmptyState
                        variant="no-results"
                        icon={<ScrollTextIcon />}
                        title={filtered ? 'No events match these filters' : 'No events in this period'}
                        description="Try a longer time range."
                    />
                }
                toolbar={
                    <div className="flex w-full flex-wrap items-center gap-2">
                        <Select value={filters.type} onValueChange={type => filter({ type })}>
                            <SelectTrigger className="h-8 w-64" aria-label="Event">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value={ANY}>All events</SelectItem>
                                {(eventTypes.data ?? []).map(type => (
                                    <SelectItem key={type} value={type}>
                                        {type}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                        <Select value={filters.status} onValueChange={status => filter({ status })}>
                            <SelectTrigger className="h-8 w-40" aria-label="Status">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                <SelectItem value={ANY}>All statuses</SelectItem>
                                <SelectItem value="SUCCESS">Success</SelectItem>
                                <SelectItem value="FAILURE">Failure</SelectItem>
                            </SelectContent>
                        </Select>
                        <DatePicker
                            className="h-8 w-44"
                            aria-label="Start date"
                            placeholder="Start date"
                            value={filters.start}
                            onChange={start => filter({ start })}
                        />
                        <DatePicker
                            className="h-8 w-44"
                            aria-label="End date"
                            placeholder="End date"
                            value={filters.end}
                            onChange={end => filter({ end })}
                        />
                        {filtered && (
                            <Button
                                variant="ghost"
                                size="sm"
                                onClick={() => filter({ type: ANY, status: ANY, start: undefined, end: undefined })}
                            >
                                Reset
                            </Button>
                        )}
                        <div className="ml-auto flex items-center gap-2">
                            <Select value={filters.range} onValueChange={range => filter({ range: range as TimeRangeId })}>
                                <SelectTrigger className="h-8 w-44" aria-label="Quick time range">
                                    <SelectValue />
                                </SelectTrigger>
                                <SelectContent>
                                    {TIME_RANGES.map(range => (
                                        <SelectItem key={range.id} value={range.id}>
                                            {range.name}
                                        </SelectItem>
                                    ))}
                                </SelectContent>
                            </Select>
                            <Button variant="outline" size="icon-sm" aria-label="Refresh" onClick={() => void audits.refetch()}>
                                <RefreshCwIcon aria-hidden />
                            </Button>
                        </div>
                    </div>
                }
            />

            <Dialog open={selected !== null} onOpenChange={open => !open && setSelected(null)}>
                <DialogContent className="max-w-[calc(100%-2rem)] sm:max-w-2xl">
                    <DialogHeader>
                        <DialogTitle>{selected?.type}</DialogTitle>
                        <DialogDescription>{selected ? formatAuditDate(selected.timestamp) : ''}</DialogDescription>
                    </DialogHeader>
                    {selected && (
                        <div className="flex flex-col gap-4">
                            <dl className="grid gap-4 sm:grid-cols-2">
                                <div>
                                    <dt className="text-xs text-muted-foreground">IP</dt>
                                    <dd className="font-mono text-sm">{selected.accessPoint?.ipAddress ?? '—'}</dd>
                                </div>
                                <div className="min-w-0">
                                    <dt className="text-xs text-muted-foreground">User agent</dt>
                                    <dd className="truncate text-sm">{selected.accessPoint?.userAgent ?? '—'}</dd>
                                </div>
                            </dl>
                            <CodeEditor value={auditDetails(selected)} language="json" readOnly height={240} />
                        </div>
                    )}
                </DialogContent>
            </Dialog>
        </div>
    );
}
