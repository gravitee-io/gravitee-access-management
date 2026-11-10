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
import { rangeStart, type TimeRange } from '../../app/components/analytics-widgets';
import type { AnalyticsQuery, AnalyticsType } from '../../lib/api/analytics';

export const APPLICATION_WIDGETS = {
    logins: { type: 'count', field: 'user_login' },
    signUps: { type: 'count', field: 'user_registered' },
    loginActivity: { type: 'date_histo', field: 'user_login' },
    signUpActivity: { type: 'date_histo', field: 'user_registered' },
} as const satisfies Record<string, { type: AnalyticsType; field: string }>;

export type ApplicationWidgetKey = keyof typeof APPLICATION_WIDGETS;

export const APPLICATION_WIDGET_KEYS = Object.keys(APPLICATION_WIDGETS) as ApplicationWidgetKey[];

export function applicationWidgetQuery(key: ApplicationWidgetKey, range: TimeRange, to: number): AnalyticsQuery {
    const { type, field } = APPLICATION_WIDGETS[key];
    return { type, field, from: rangeStart(range, to), to, interval: range.interval };
}
