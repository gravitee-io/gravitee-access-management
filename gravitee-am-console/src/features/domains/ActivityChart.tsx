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
    LineChart,
} from '@gravitee/graphene-charts';
import { Card, CardContent, CardHeader, CardTitle } from '@gravitee/graphene-core';
import { WidgetFailure } from './WidgetFailure';
import { histogram, sentenceCase, seriesColor, type TimeRange, type WidgetResult } from './dashboard-data';

interface ActivityChartProps {
    readonly title: string;
    readonly result: WidgetResult;
    readonly range: TimeRange;
}

export function ActivityChart({ title, result, range }: ActivityChartProps) {
    const { series, data } = histogram(result.status === 'ready' ? result.data : undefined, range);
    const config: ChartConfig = Object.fromEntries(
        series.map((name, index) => [name, { label: sentenceCase(name), color: seriesColor(name, index) }]),
    );
    const isEmpty = data.every(point => series.every(name => point[name] === 0));
    return (
        <Card>
            <CardHeader>
                <CardTitle>{title}</CardTitle>
            </CardHeader>
            <CardContent>
                {result.status === 'error' ? (
                    <WidgetFailure />
                ) : (
                    <ChartContainer config={config} className="h-72 w-full">
                        <LineChart
                            data={[...data]}
                            dataKeys={[...series]}
                            state={result.status === 'loading' ? 'loading' : isEmpty ? 'empty' : 'ready'}
                            showGrid
                            ariaLabel={title}
                            margin={{ top: 12, right: 24 }}
                        >
                            <ChartTooltip content={<ChartTooltipContent />} />
                            <ChartLegend content={<ChartLegendContent />} />
                        </LineChart>
                    </ChartContainer>
                )}
            </CardContent>
        </Card>
    );
}
