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
import { rangeStart, type TimeRange } from '../../../app/components/analytics-widgets';
import type { AnalyticsQuery, AnalyticsResponse, AnalyticsType } from '../../../lib/api/analytics';

export const WIDGET_KEYS = [
    'logins',
    'signUps',
    'users',
    'applications',
    'loginActivity',
    'signUpActivity',
    'userStatus',
    'registration',
    'topApplications',
    'loginType',
    'webauthn',
    'cba',
    'magicLink',
] as const;

export type WidgetKey = (typeof WIDGET_KEYS)[number];

interface Widget {
    readonly type: AnalyticsType;
    readonly field: string;
    readonly size?: number;
}

export const WIDGETS: Readonly<Record<WidgetKey, Widget>> = {
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
};

export function widgetQuery(key: WidgetKey, range: TimeRange, to: number): AnalyticsQuery {
    const widget = WIDGETS[key];
    return { type: widget.type, field: widget.field, from: rangeStart(range, to), to, interval: range.interval, size: widget.size };
}

export interface Group {
    readonly key: string;
    readonly label: string;
    readonly value: number;
    /** A key without metadata counts as deleted. */
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
