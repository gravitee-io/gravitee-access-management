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
import { BreakdownChart, ChartContainer, type ChartConfig, type ChartState, DoughnutChart, LineChart } from '@gravitee/graphene-charts';
import {
    Button,
    Card,
    CardContent,
    CardDescription,
    CardHeader,
    CardTitle,
    Select,
    SelectContent,
    SelectItem,
    SelectTrigger,
    SelectValue,
    Skeleton,
} from '@gravitee/graphene-core';
import { AppWindowIcon, PlusIcon, RefreshCwIcon, UserCheckIcon, UsersIcon, ZapIcon } from '@gravitee/graphene-core/icons';
import { useQueries, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { PageHeader } from '../../app/components/PageHeader';
import { type AnalyticsResponse, searchAnalytics } from '../../lib/api/analytics';
import { useBreadcrumbs } from '../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../lib/session/domain';
import { Can } from '../../lib/session/permissions';
import {
    count,
    DEFAULT_TIME_RANGE,
    type Group,
    groups,
    histogram,
    registration,
    sentenceCase,
    TIME_RANGES,
    type TimeRangeId,
    WIDGETS,
    type WidgetKey,
    widgetQuery,
} from './dashboard-data';

interface WidgetResult {
    readonly data: AnalyticsResponse | undefined;
    readonly failed: boolean;
    readonly loading: boolean;
}

const WIDGET_KEYS = Object.keys(WIDGETS) as WidgetKey[];

function WidgetFailure() {
    return <p className="py-6 text-center text-sm text-muted-foreground">Failed to fetch data</p>;
}

function Metric({
    label,
    icon: Icon,
    prefix = '',
    result,
}: Readonly<{ label: string; icon: typeof UsersIcon; prefix?: string; result: WidgetResult }>) {
    const value = count(result.data);
    return (
        <Card>
            <CardContent className="flex flex-col gap-1 pt-6">
                <span className="flex items-center justify-between text-sm text-muted-foreground">
                    {label}
                    <Icon className="size-4" aria-hidden />
                </span>
                {result.loading ? (
                    <Skeleton className="h-8 w-24" />
                ) : result.failed ? (
                    <span className="text-sm text-muted-foreground">Failed to fetch data</span>
                ) : (
                    <span className="text-2xl font-semibold tabular-nums">
                        {value === undefined ? '—' : `${value > 0 ? prefix : ''}${value.toLocaleString()}`}
                    </span>
                )}
            </CardContent>
        </Card>
    );
}

function ActivityChart({ title, result, rangeId }: Readonly<{ title: string; result: WidgetResult; rangeId: TimeRangeId }>) {
    const range = TIME_RANGES.find(r => r.id === rangeId) ?? TIME_RANGES[2];
    const { series, data } = histogram(result.data, range);
    let palette = 0;
    const config: ChartConfig = Object.fromEntries(
        series.map(name => [
            name,
            { label: sentenceCase(name), color: name.includes('failure') ? 'var(--destructive)' : `var(--chart-${(palette++ % 5) + 1})` },
        ]),
    );
    const isEmpty = data.every(point => series.every(name => point[name] === 0));
    const state: ChartState = result.loading ? 'loading' : isEmpty ? 'empty' : 'ready';
    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-base">{title}</CardTitle>
            </CardHeader>
            <CardContent>
                {result.failed && !result.loading ? (
                    <WidgetFailure />
                ) : (
                    <ChartContainer config={config} className="h-72 w-full">
                        <LineChart
                            data={[...data]}
                            dataKeys={[...series]}
                            state={state}
                            showGrid
                            ariaLabel={title}
                            margin={{ top: 12, right: 24 }}
                        />
                    </ChartContainer>
                )}
            </CardContent>
        </Card>
    );
}

function BreakdownCard({
    title,
    description,
    items,
    result,
    sort,
    maxItems,
    onItemClick,
}: Readonly<{
    title: string;
    description: string;
    items: Group[];
    result: WidgetResult;
    sort?: boolean;
    maxItems?: number;
    onItemClick?: (key: string) => void;
}>) {
    const isEmpty = !result.loading && items.every(item => item.value === 0);
    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-base">{title}</CardTitle>
                <CardDescription>{description}</CardDescription>
            </CardHeader>
            <CardContent>
                {result.failed && !result.loading ? (
                    <WidgetFailure />
                ) : isEmpty ? (
                    <p className="py-6 text-center text-sm text-muted-foreground">No activity in this period.</p>
                ) : (
                    <BreakdownChart
                        items={items.map(item => ({ key: item.key, label: sentenceCase(item.label), value: item.value }))}
                        state={result.loading ? 'loading' : 'ready'}
                        sortByValue={sort ? 'desc' : undefined}
                        ratioMode={sort ? 'max' : 'total'}
                        maxItems={maxItems}
                        formatValue={value => value.toLocaleString()}
                        onItemClick={onItemClick && (item => onItemClick(item.key))}
                    />
                )}
            </CardContent>
        </Card>
    );
}

function SuccessDoughnut({ title, result }: Readonly<{ title: string; result: WidgetResult }>) {
    const data = groups(result.data).map(group => ({ category: sentenceCase(group.label), value: group.value }));
    const total = data.reduce((sum, point) => sum + point.value, 0);
    const state: ChartState = result.loading ? 'loading' : total === 0 ? 'empty' : 'ready';
    const config: ChartConfig = Object.fromEntries(data.map(point => [point.category, { label: point.category }]));
    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-base">{title}</CardTitle>
            </CardHeader>
            <CardContent>
                {result.failed && !result.loading ? (
                    <WidgetFailure />
                ) : (
                    <ChartContainer config={config} className="mx-auto" style={{ height: '10rem', width: '10rem' }}>
                        <DoughnutChart
                            data={data}
                            state={state}
                            innerRadius={0.68}
                            ariaLabel={title}
                            centerContent={<span className="text-lg font-semibold tabular-nums">{total.toLocaleString()}</span>}
                        />
                    </ChartContainer>
                )}
            </CardContent>
        </Card>
    );
}

function RegistrationCard({ result }: Readonly<{ result: WidgetResult }>) {
    const { completed, total } = registration(result.data);
    return (
        <Card>
            <CardHeader>
                <CardTitle className="text-base">User registration</CardTitle>
                <CardDescription>Share of self-registrations that were completed.</CardDescription>
            </CardHeader>
            <CardContent>
                {result.failed && !result.loading ? (
                    <WidgetFailure />
                ) : (
                    <ChartContainer
                        config={{ Completed: { label: 'Completed' }, Pending: { label: 'Pending' } }}
                        className="mx-auto"
                        style={{ height: '11rem', width: '11rem' }}
                    >
                        <DoughnutChart
                            data={[
                                { category: 'Completed', value: completed },
                                { category: 'Pending', value: total - completed },
                            ]}
                            state={result.loading ? 'loading' : total === 0 ? 'empty' : 'ready'}
                            innerRadius={0.7}
                            ariaLabel="User registration completion"
                            centerContent={
                                <span className="text-2xl font-semibold tabular-nums">
                                    {total === 0 ? '—' : `${Math.round((completed / total) * 100)}%`}
                                </span>
                            }
                        />
                    </ChartContainer>
                )}
            </CardContent>
        </Card>
    );
}

/** The Angular `DomainDashboardComponent`: the domain's audit analytics, with a quick time range and a refresh. */
export function DomainDashboardPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [rangeId, setRangeId] = useState<TimeRangeId>(DEFAULT_TIME_RANGE);
    const range = TIME_RANGES.find(r => r.id === rangeId) ?? TIME_RANGES[2];
    useBreadcrumbs([{ label: 'Domains', to: domainsPath }, { label: domain.name, to: `${basePath}/dashboard` }, { label: 'Dashboard' }]);

    // One call per widget, each with its own window end, as the Angular `gv-dashboard` does.
    const queries = useQueries({
        queries: WIDGET_KEYS.map(key => ({
            queryKey: ['analytics', ref, rangeId, key],
            queryFn: () => searchAnalytics(ref, widgetQuery(key, range, Date.now())),
        })),
    });
    const results = Object.fromEntries(
        WIDGET_KEYS.map((key, index) => [
            key,
            { data: queries[index].data, failed: queries[index].isError, loading: queries[index].isFetching },
        ]),
    ) as Record<WidgetKey, WidgetResult>;
    const allFailed = queries.every(query => query.isError && !query.isFetching);

    const topApplications = groups(results.topApplications.data);
    const deletedApplications = new Set(topApplications.filter(group => group.deleted).map(group => group.key));

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Dashboard"
                description={`Activity and usage statistics for ${domain.name}.`}
                actions={
                    <div className="flex items-center gap-2">
                        <Select value={rangeId} onValueChange={value => setRangeId(value as TimeRangeId)}>
                            <SelectTrigger className="h-8 w-48" aria-label="Time range">
                                <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                                {TIME_RANGES.map(r => (
                                    <SelectItem key={r.id} value={r.id}>
                                        {r.name}
                                    </SelectItem>
                                ))}
                            </SelectContent>
                        </Select>
                        <Button
                            variant="outline"
                            size="icon-sm"
                            aria-label="Refresh data"
                            onClick={() => void queryClient.invalidateQueries({ queryKey: ['analytics', ref] })}
                        >
                            <RefreshCwIcon aria-hidden />
                        </Button>
                        <Can all={['application_create']}>
                            <Button size="sm" asChild>
                                <Link to={`${basePath}/applications/new`}>
                                    <PlusIcon aria-hidden />
                                    Create application
                                </Link>
                            </Button>
                        </Can>
                    </div>
                }
            />

            {allFailed ? (
                <ErrorPanel description="Analytics for this domain could not be retrieved." error={queries[0].error} />
            ) : (
                <>
                    <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
                        <Metric label="Logins" icon={ZapIcon} prefix="+" result={results.logins} />
                        <Metric label="Sign ups" icon={UserCheckIcon} prefix="+" result={results.signUps} />
                        <Metric label="Users" icon={UsersIcon} result={results.users} />
                        <Metric label="Applications" icon={AppWindowIcon} result={results.applications} />
                    </div>

                    <div className="grid gap-4 lg:grid-cols-2">
                        <ActivityChart title="Login activity" result={results.loginActivity} rangeId={rangeId} />
                        <ActivityChart title="Sign-up activity" result={results.signUpActivity} rangeId={rangeId} />
                    </div>

                    <div className="grid gap-4 lg:grid-cols-2">
                        <BreakdownCard
                            title="User status"
                            description="How the domain's users are distributed across account states."
                            items={groups(results.userStatus.data)}
                            result={results.userStatus}
                        />
                        <RegistrationCard result={results.registration} />
                    </div>

                    <div className="grid gap-4 lg:grid-cols-2">
                        <BreakdownCard
                            title="Top applications"
                            description="Ordered by login calls."
                            items={topApplications}
                            result={results.topApplications}
                            sort
                            maxItems={5}
                            onItemClick={key => {
                                if (!deletedApplications.has(key)) navigate(`${basePath}/applications/${key}`);
                            }}
                        />
                        <BreakdownCard
                            title="Login type"
                            description="How users signed in."
                            items={groups(results.loginType.data)}
                            result={results.loginType}
                        />
                    </div>

                    <div className="grid gap-4 md:grid-cols-3">
                        <SuccessDoughnut title="WebAuthn logins" result={results.webauthn} />
                        <SuccessDoughnut title="Certificate-based logins" result={results.cba} />
                        <SuccessDoughnut title="Magic link logins" result={results.magicLink} />
                    </div>
                </>
            )}
        </div>
    );
}
