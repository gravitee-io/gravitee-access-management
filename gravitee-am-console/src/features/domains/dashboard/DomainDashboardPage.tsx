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
import { Button, Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@gravitee/graphene-core';
import { AppWindowIcon, PlusIcon, RefreshCwIcon, UserCheckIcon, UsersIcon, ZapIcon } from '@gravitee/graphene-core/icons';
import { useQueries, useQueryClient, type UseQueryResult } from '@tanstack/react-query';
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ErrorPanel } from '../../../app/components/ErrorPanel';
import { PageHeader } from '../../../app/components/PageHeader';
import { type AnalyticsResponse, searchAnalytics } from '../../../lib/api/analytics';
import { useBreadcrumbs } from '../../../lib/layout/useBreadcrumbs';
import { useCurrentDomain } from '../../../lib/session/domain';
import { Can } from '../../../lib/session/permissions';
import { ActivityChart } from './ActivityChart';
import { BreakdownCard } from './BreakdownCard';
import { MetricTile } from './MetricTile';
import { RegistrationCard } from './RegistrationCard';
import { SuccessDoughnut } from './SuccessDoughnut';
import { DEFAULT_TIME_RANGE, groups, TIME_RANGES, WIDGET_KEYS, type WidgetKey, widgetQuery, type WidgetResult } from './dashboard-data';

function toWidgetResult(query: UseQueryResult<AnalyticsResponse>): WidgetResult {
    if (query.isFetching) return { status: 'loading' };
    if (query.isError) return { status: 'error' };
    return { status: 'ready', data: query.data };
}

export function DomainDashboardPage() {
    const { domain, ref, basePath, domainsPath } = useCurrentDomain();
    const navigate = useNavigate();
    const queryClient = useQueryClient();
    const [range, setRange] = useState(DEFAULT_TIME_RANGE);
    useBreadcrumbs([{ label: 'Domains', to: domainsPath }, { label: domain.name }, { label: 'Dashboard' }]);

    // Each widget call takes its own window end.
    const queries = useQueries({
        queries: WIDGET_KEYS.map(key => ({
            queryKey: ['analytics', ref, range.id, key],
            queryFn: () => searchAnalytics(ref, widgetQuery(key, range, Date.now())),
        })),
    });
    const result = (key: WidgetKey) => toWidgetResult(queries[WIDGET_KEYS.indexOf(key)]);
    const dataOf = (key: WidgetKey) => queries[WIDGET_KEYS.indexOf(key)].data;
    const allFailed = queries.every(query => query.isError && !query.isFetching);

    const topApplications = groups(dataOf('topApplications'));
    const deletedApplications = new Set(topApplications.filter(group => group.deleted).map(group => group.key));

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Dashboard"
                description={`Activity and usage statistics for ${domain.name}.`}
                actions={
                    <div className="flex items-center gap-2">
                        <Select
                            value={range.id}
                            onValueChange={value => setRange(TIME_RANGES.find(candidate => candidate.id === value) ?? DEFAULT_TIME_RANGE)}
                        >
                            <SelectTrigger className="w-48" aria-label="Time range">
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
                        <MetricTile label="Logins" icon={ZapIcon} prefix="+" result={result('logins')} />
                        <MetricTile label="Sign ups" icon={UserCheckIcon} prefix="+" result={result('signUps')} />
                        <MetricTile label="Users" icon={UsersIcon} result={result('users')} />
                        <MetricTile label="Applications" icon={AppWindowIcon} result={result('applications')} />
                    </div>

                    <div className="grid gap-4 lg:grid-cols-2">
                        <ActivityChart title="Login activity" result={result('loginActivity')} range={range} />
                        <ActivityChart title="Sign-up activity" result={result('signUpActivity')} range={range} />
                    </div>

                    <div className="grid gap-4 lg:grid-cols-2">
                        <BreakdownCard
                            title="User status"
                            description="How the domain's users are distributed across account states."
                            items={groups(dataOf('userStatus'))}
                            result={result('userStatus')}
                        />
                        <RegistrationCard result={result('registration')} />
                    </div>

                    <div className="grid gap-4 lg:grid-cols-2">
                        <BreakdownCard
                            title="Top applications"
                            description="Ordered by login calls."
                            items={topApplications}
                            result={result('topApplications')}
                            sort
                            maxItems={5}
                            onItemClick={key => {
                                if (!deletedApplications.has(key)) navigate(`${basePath}/applications/${key}`);
                            }}
                        />
                        <BreakdownCard
                            title="Login type"
                            description="How users signed in."
                            items={groups(dataOf('loginType'))}
                            result={result('loginType')}
                        />
                    </div>

                    <div className="grid gap-4 md:grid-cols-3">
                        <SuccessDoughnut title="WebAuthn logins" result={result('webauthn')} />
                        <SuccessDoughnut title="Certificate-based logins" result={result('cba')} />
                        <SuccessDoughnut title="Magic link logins" result={result('magicLink')} />
                    </div>
                </>
            )}
        </div>
    );
}
