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
import { RefreshCwIcon, UserCheckIcon, ZapIcon } from '@gravitee/graphene-core/icons';
import { useQueries, useQueryClient } from '@tanstack/react-query';
import { useState } from 'react';
import { ActivityChart } from '../../app/components/ActivityChart';
import { ErrorPanel } from '../../app/components/ErrorPanel';
import { MetricTile } from '../../app/components/MetricTile';
import { PageHeader } from '../../app/components/PageHeader';
import { DEFAULT_TIME_RANGE, TIME_RANGES, toWidgetResult } from '../../app/components/analytics-widgets';
import { searchApplicationAnalytics } from '../../lib/api/analytics';
import { useCurrentDomain } from '../../lib/session/domain';
import { useApplication } from './ApplicationContext';
import { APPLICATION_WIDGET_KEYS, type ApplicationWidgetKey, applicationWidgetQuery } from './analytics-queries';

export function ApplicationAnalyticsPage() {
    const { ref } = useCurrentDomain();
    const { application } = useApplication();
    const queryClient = useQueryClient();
    const [range, setRange] = useState(DEFAULT_TIME_RANGE);

    const queries = useQueries({
        queries: APPLICATION_WIDGET_KEYS.map(key => ({
            queryKey: ['application-analytics', ref, application.id, range.id, key],
            queryFn: () => searchApplicationAnalytics(ref, application.id, applicationWidgetQuery(key, range, Date.now())),
        })),
    });
    const result = (key: ApplicationWidgetKey) => toWidgetResult(queries[APPLICATION_WIDGET_KEYS.indexOf(key)]);
    const allFailed = queries.every(query => query.isError && !query.isFetching);

    return (
        <div className="flex flex-col gap-6">
            <PageHeader
                title="Analytics"
                description={`Activity and usage statistics for ${application.name}.`}
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
                            onClick={() => void queryClient.invalidateQueries({ queryKey: ['application-analytics', ref, application.id] })}
                        >
                            <RefreshCwIcon aria-hidden />
                        </Button>
                    </div>
                }
            />

            {allFailed ? (
                <ErrorPanel description="Analytics for this application could not be retrieved." error={queries[0].error} />
            ) : (
                <>
                    <div className="grid gap-4 sm:grid-cols-2">
                        <MetricTile label="Logins" icon={ZapIcon} prefix="+" result={result('logins')} />
                        <MetricTile label="Sign ups" icon={UserCheckIcon} prefix="+" result={result('signUps')} />
                    </div>
                    <ActivityChart title="Login activity" result={result('loginActivity')} range={range} />
                    <ActivityChart title="Sign-up activity" result={result('signUpActivity')} range={range} />
                </>
            )}
        </div>
    );
}
