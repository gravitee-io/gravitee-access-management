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
import type { UseQueryResult } from '@tanstack/react-query';
import type { AnalyticsResponse } from '../../lib/api/analytics';

export const TIME_RANGES = [
    { id: '1h', name: 'Last hour', value: 60, unit: 'minutes', interval: 1000 * 60 },
    { id: '12h', name: 'Last 12 hours', value: 12, unit: 'hours', interval: 1000 * 60 * 60 },
    { id: '1d', name: 'Today', value: 24, unit: 'hours', interval: 1000 * 60 * 60 },
    { id: '7d', name: 'This week', value: 7, unit: 'days', interval: 1000 * 60 * 60 * 24 },
    { id: '30d', name: 'This month', value: 4, unit: 'weeks', interval: 1000 * 60 * 60 * 24 },
    { id: '90d', name: 'Last 90 days', value: 3, unit: 'months', interval: 1000 * 60 * 60 * 24 },
] as const;

export type TimeRange = (typeof TIME_RANGES)[number];

export const DEFAULT_TIME_RANGE: TimeRange = TIME_RANGES[2];

/**
 * Minutes and hours subtract absolute time, days and weeks keep the local time of day, and a month is a calendar month
 * clamped to its last day.
 */
export function rangeStart(range: TimeRange, to: number): number {
    if (range.unit === 'minutes') return to - range.value * 60_000;
    if (range.unit === 'hours') return to - range.value * 3_600_000;
    const start = new Date(to);
    if (range.unit === 'months') {
        const day = start.getDate();
        start.setDate(1);
        start.setMonth(start.getMonth() - range.value);
        start.setDate(Math.min(day, new Date(start.getFullYear(), start.getMonth() + 1, 0).getDate()));
    } else {
        start.setDate(start.getDate() - range.value * (range.unit === 'weeks' ? 7 : 1));
    }
    return start.getTime();
}

export type WidgetResult =
    | { readonly status: 'loading' }
    | { readonly status: 'error' }
    | { readonly status: 'ready'; readonly data: AnalyticsResponse | undefined };

export function toWidgetResult(query: UseQueryResult<AnalyticsResponse>): WidgetResult {
    if (query.isFetching) return { status: 'loading' };
    if (query.isError) return { status: 'error' };
    return { status: 'ready', data: query.data };
}

/** Login outcomes take the semantic colors; other series take the chart palette in order. */
export function seriesColor(key: string, paletteIndex: number): string {
    if (key.includes('failure')) return 'var(--destructive)';
    if (key.includes('success')) return 'var(--success)';
    return `var(--chart-${(paletteIndex % 5) + 1})`;
}

export function count(result: AnalyticsResponse | undefined): number | undefined {
    return result && 'value' in result && typeof result.value === 'number' ? result.value : undefined;
}

export interface Histogram {
    readonly series: readonly string[];
    readonly data: readonly ({ category: string } & Record<string, number | string>)[];
}

export function histogram(result: AnalyticsResponse | undefined, range: TimeRange): Histogram {
    if (!result || !('timestamp' in result)) return { series: [], data: [] };
    const intraday = range.unit === 'minutes' || range.unit === 'hours';
    const format = new Intl.DateTimeFormat(
        undefined,
        intraday ? { hour: '2-digit', minute: '2-digit' } : { month: 'short', day: 'numeric' },
    );
    const length = Math.max(0, ...result.values.map(bucket => bucket.data.length));
    const data = Array.from({ length }, (_, index) => ({
        category: format.format(result.timestamp.from + index * result.timestamp.interval),
        ...Object.fromEntries(result.values.map(bucket => [bucket.name, bucket.data[index] ?? 0])),
    }));
    return { series: result.values.map(bucket => bucket.name), data };
}

export function sentenceCase(label: string): string {
    const words = label.replace(/_/g, ' ');
    return words.charAt(0).toUpperCase() + words.slice(1);
}
