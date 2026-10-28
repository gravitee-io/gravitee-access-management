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
import type { AnalyticsQuery, AnalyticsResponse, AnalyticsType } from '../../lib/api/analytics';

/** `utils/time-range-utils.ts` in the Angular console. */
export const TIME_RANGES = [
    { id: '1h', name: 'Last hour', value: 60, unit: 'minutes', interval: 1000 * 60 },
    { id: '12h', name: 'Last 12 hours', value: 12, unit: 'hours', interval: 1000 * 60 * 60 },
    { id: '1d', name: 'Today', value: 24, unit: 'hours', interval: 1000 * 60 * 60 },
    { id: '7d', name: 'This week', value: 7, unit: 'days', interval: 1000 * 60 * 60 * 24 },
    { id: '30d', name: 'This month', value: 4, unit: 'weeks', interval: 1000 * 60 * 60 * 24 },
    { id: '90d', name: 'Last 90 days', value: 3, unit: 'months', interval: 1000 * 60 * 60 * 24 },
] as const;

export type TimeRange = (typeof TIME_RANGES)[number];
export type TimeRangeId = TimeRange['id'];

export const DEFAULT_TIME_RANGE: TimeRangeId = '1d';

/**
 * The start of the range, as Angular's `moment().subtract(value, unit)`: minutes and hours are absolute time, days and
 * weeks keep the local time of day, and a month is a calendar month clamped to its last day.
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

/** The widgets of the Angular `DomainDashboardComponent`, one analytics call each. */
export const WIDGETS = {
    logins: { type: 'count', field: 'user_login' },
    signUps: { type: 'count', field: 'user_registered' },
    users: { type: 'count', field: 'user' },
    applications: { type: 'count', field: 'application' },
    loginActivity: { type: 'date_histo', field: 'user_login' },
    signUpActivity: { type: 'date_histo', field: 'user_registered' },
    userStatus: { type: 'group_by', field: 'user_status' },
    registration: { type: 'group_by', field: 'user_registration' },
    topApplications: { type: 'group_by', field: 'application', size: 20 },
    loginType: { type: 'group_by', field: 'user_login' },
    webauthn: { type: 'group_by', field: 'webauthn' },
    cba: { type: 'group_by', field: 'cba' },
    magicLink: { type: 'group_by', field: 'magic_link' },
} satisfies Record<string, { type: AnalyticsType; field: string; size?: number }>;

export type WidgetKey = keyof typeof WIDGETS;

export function widgetQuery(key: WidgetKey, range: TimeRange, to: number): AnalyticsQuery {
    const widget: { type: AnalyticsType; field: string; size?: number } = WIDGETS[key];
    return { type: widget.type, field: widget.field, from: rangeStart(range, to), to, interval: range.interval, size: widget.size };
}

export function count(result: AnalyticsResponse | undefined): number | undefined {
    return result && 'value' in result && typeof result.value === 'number' ? result.value : undefined;
}

export interface Group {
    readonly key: string;
    readonly label: string;
    readonly value: number;
    /** As the Angular top applications table: a key without metadata counts as deleted. */
    readonly deleted: boolean;
}

export function groups(result: AnalyticsResponse | undefined): Group[] {
    if (!result || 'timestamp' in result || !('values' in result)) return [];
    const { values, metadata } = result;
    return Object.entries(values).map(([key, value]) => ({
        key,
        label: metadata?.[key]?.name ?? key,
        value,
        deleted: !metadata?.[key] || metadata[key].deleted === true,
    }));
}

/** The `user_registration` group: `total` users and the `completed` registrations among them. */
export function registration(result: AnalyticsResponse | undefined): { completed: number; total: number } {
    const values = Object.fromEntries(groups(result).map(group => [group.key, group.value]));
    return { completed: values.completed ?? 0, total: values.total ?? 0 };
}

export interface Histogram {
    readonly series: readonly string[];
    readonly data: readonly ({ category: string } & Record<string, number | string>)[];
}

/** Every bucket of a `date_histo` response as one series, as the Angular line widget draws them. */
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
