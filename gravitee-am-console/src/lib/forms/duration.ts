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

export const DURATION_UNITS = [
    { value: 'seconds', label: 'Seconds' },
    { value: 'minutes', label: 'Minutes' },
    { value: 'hours', label: 'Hours' },
    { value: 'days', label: 'Days' },
    { value: 'weeks', label: 'Weeks' },
    { value: 'months', label: 'Months' },
    { value: 'years', label: 'Years' },
] as const;

export type DurationUnit = (typeof DURATION_UNITS)[number]['value'];

const SECONDS_PER_DAY = 86_400;
// moment: a month is 146097 / 4800 days and a year 12 months, rounded to whole days when converted to seconds.
const DAYS_PER_MONTH = 146_097 / 4_800;

/** The Angular `moment.duration(amount, unit).asSeconds()`. */
export function toSeconds(amount: number, unit: DurationUnit): number {
    switch (unit) {
        case 'seconds':
            return amount;
        case 'minutes':
            return amount * 60;
        case 'hours':
            return amount * 3_600;
        case 'days':
            return amount * SECONDS_PER_DAY;
        case 'weeks':
            return amount * 7 * SECONDS_PER_DAY;
        case 'months':
            return Math.round(amount * DAYS_PER_MONTH) * SECONDS_PER_DAY;
        case 'years':
            return Math.round(amount * 12 * DAYS_PER_MONTH) * SECONDS_PER_DAY;
    }
}

/** A duration as a form edits it: the typed amount and its unit. */
export type DurationDraft = {
    readonly time: string;
    readonly unit: DurationUnit | '';
};

/** The largest unit that gives the seconds exactly, with the Angular conversion. */
export function exactDuration(seconds: number): DurationDraft {
    for (const { value: unit } of [...DURATION_UNITS].reverse()) {
        const time = Math.round(seconds / toSeconds(1, unit));
        if (time >= 1 && toSeconds(time, unit) === seconds) {
            return { time: String(time), unit };
        }
    }
    return { time: String(seconds), unit: 'seconds' };
}

/** The Angular `moment.duration(time, unit).asSeconds()`, which is 0 for an empty time. */
export function draftToSeconds(draft: DurationDraft): number {
    return draft.unit ? toSeconds(Number(draft.time), draft.unit) : 0;
}
