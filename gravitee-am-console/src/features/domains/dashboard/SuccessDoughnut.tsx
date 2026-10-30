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
    type ChartConfig,
    ChartLegend,
    ChartLegendContent,
    ChartTooltip,
    ChartTooltipContent,
    DoughnutChart,
} from '@gravitee/graphene-charts';
import { Card, CardContent, CardHeader, CardTitle } from '@gravitee/graphene-core';
import { WidgetFailure } from './WidgetFailure';
import { groups, sentenceCase, seriesColor, type WidgetResult } from './dashboard-data';

interface SuccessDoughnutProps {
    readonly title: string;
    readonly result: WidgetResult;
}

export function SuccessDoughnut({ title, result }: SuccessDoughnutProps) {
    const slices = groups(result.status === 'ready' ? result.data : undefined).map((group, index) => ({
        category: sentenceCase(group.label),
        value: group.value,
        color: seriesColor(group.key, index),
    }));
    const total = slices.reduce((sum, slice) => sum + slice.value, 0);
    const config: ChartConfig = Object.fromEntries(slices.map(slice => [slice.category, { label: slice.category, color: slice.color }]));
    return (
        <Card>
            <CardHeader>
                <CardTitle>{title}</CardTitle>
            </CardHeader>
            <CardContent>
                {result.status === 'error' ? (
                    <WidgetFailure />
                ) : (
                    <ChartContainer config={config} className="mx-auto h-40 w-40">
                        <DoughnutChart
                            data={slices.map(({ category, value }) => ({ category, value }))}
                            state={result.status === 'loading' ? 'loading' : total === 0 ? 'empty' : 'ready'}
                            innerRadius={0.68}
                            ariaLabel={title}
                            centerContent={<span className="text-lg font-semibold tabular-nums">{total.toLocaleString()}</span>}
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
