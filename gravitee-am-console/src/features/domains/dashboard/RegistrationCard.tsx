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
    ChartContainer,
    ChartLegend,
    ChartLegendContent,
    ChartTooltip,
    ChartTooltipContent,
    DoughnutChart,
} from '@gravitee/graphene-charts';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@gravitee/graphene-core';
import { WidgetFailure } from './WidgetFailure';
import { registration, type WidgetResult } from './dashboard-data';

interface RegistrationCardProps {
    readonly result: WidgetResult;
}

export function RegistrationCard({ result }: RegistrationCardProps) {
    const { completed, total } = registration(result.status === 'ready' ? result.data : undefined);
    return (
        <Card>
            <CardHeader>
                <CardTitle>User registration</CardTitle>
                <CardDescription>Share of self-registrations that were completed.</CardDescription>
            </CardHeader>
            <CardContent>
                {result.status === 'error' ? (
                    <WidgetFailure />
                ) : (
                    <ChartContainer
                        config={{ Completed: { label: 'Completed' }, Pending: { label: 'Pending' } }}
                        className="mx-auto h-44 w-44"
                    >
                        <DoughnutChart
                            data={[
                                { category: 'Completed', value: completed },
                                { category: 'Pending', value: total - completed },
                            ]}
                            state={result.status === 'loading' ? 'loading' : total === 0 ? 'empty' : 'ready'}
                            innerRadius={0.7}
                            ariaLabel="User registration completion"
                            centerContent={
                                <span className="text-2xl font-semibold tabular-nums">
                                    {total === 0 ? '—' : `${Math.round((completed / total) * 100)}%`}
                                </span>
                            }
                        >
                            <ChartTooltip content={<ChartTooltipContent />} />
                            <ChartLegend content={<ChartLegendContent />} />
                        </DoughnutChart>
                    </ChartContainer>
                )}
            </CardContent>
        </Card>
    );
}
