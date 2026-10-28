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
import { http } from './http';
import { type DomainRef, domainPath } from './paths';

export type AnalyticsType = 'count' | 'date_histo' | 'group_by';

export interface AnalyticsQuery {
    readonly type: AnalyticsType;
    readonly field: string;
    readonly from: number;
    readonly to: number;
    readonly interval: number;
    readonly size?: number;
}

// The spec has no schema for the responses. The shapes are io.gravitee.am.model.analytics.
export interface AnalyticsCount {
    readonly value: number;
}

export interface AnalyticsBucket {
    readonly field: string;
    readonly name: string;
    readonly data: readonly number[];
}

export interface AnalyticsHistogram {
    readonly timestamp: { readonly from: number; readonly to: number; readonly interval: number };
    readonly values: readonly AnalyticsBucket[];
}

export interface AnalyticsGroupBy {
    readonly values: Readonly<Record<string, number>>;
    /** Set for the application field only: the name of each key, and `deleted` for an application that is gone. */
    readonly metadata?: Readonly<Record<string, { readonly name?: string; readonly deleted?: boolean }>> | null;
}

export type AnalyticsResponse = AnalyticsCount | AnalyticsHistogram | AnalyticsGroupBy;

export function searchAnalytics(ref: DomainRef, query: AnalyticsQuery): Promise<AnalyticsResponse> {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(query)) {
        if (value !== undefined) params.set(key, String(value));
    }
    return http<AnalyticsResponse>(`${domainPath(ref)}/analytics?${params}`);
}
