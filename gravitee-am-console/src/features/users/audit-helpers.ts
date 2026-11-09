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
import type { Audit } from '../../lib/api/users';

export type TimeRangeId = '1h' | '12h' | '1d' | '7d' | '30d' | '90d';

/** The Angular `availableTimeRanges`. */
export const TIME_RANGES: readonly { readonly id: TimeRangeId; readonly name: string }[] = [
    { id: '1h', name: 'Last hour' },
    { id: '12h', name: 'Last 12 hours' },
    { id: '1d', name: 'Today' },
    { id: '7d', name: 'This week' },
    { id: '30d', name: 'This month' },
    { id: '90d', name: 'Last 90 days' },
];

const HOUR = 60 * 60 * 1000;
const DAY = 24 * HOUR;

/** The start of a quick range ending at `now`, as Angular subtracts it with moment. */
export function rangeStart(id: TimeRangeId, now: number): number {
    switch (id) {
        case '1h':
            return now - HOUR;
        case '12h':
            return now - 12 * HOUR;
        case '1d':
            return now - DAY;
        case '7d':
            return now - 7 * DAY;
        case '30d':
            return now - 28 * DAY;
        case '90d': {
            const start = new Date(now);
            start.setMonth(start.getMonth() - 3);
            return start.getTime();
        }
    }
}

/** The Angular `auditDetails`: a success message as indented JSON, a failure message as is, else the event type. */
export function auditDetails(audit: Audit): string {
    const message = audit.outcome?.message;
    if (!message) {
        return `${audit.type} success`;
    }
    if (audit.outcome?.status !== 'success') {
        return message;
    }
    try {
        return JSON.stringify(JSON.parse(message), null, 2);
    } catch {
        return message;
    }
}

export function formatAuditDate(timestamp: number): string {
    return new Date(timestamp).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'medium' });
}
