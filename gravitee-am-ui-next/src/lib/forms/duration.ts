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

/** The Angular `moment.duration(seconds, 'seconds').humanize()`, with moment's default thresholds. */
export function humanizeSeconds(totalSeconds: number): string {
    const seconds = Math.round(totalSeconds);
    const minutes = Math.round(totalSeconds / 60);
    const hours = Math.round(totalSeconds / 3_600);
    const days = Math.round(totalSeconds / SECONDS_PER_DAY);
    const months = Math.round(totalSeconds / SECONDS_PER_DAY / DAYS_PER_MONTH);
    const years = Math.round(totalSeconds / SECONDS_PER_DAY / DAYS_PER_MONTH / 12);
    if (seconds <= 44) return 'a few seconds';
    if (minutes <= 1) return 'a minute';
    if (minutes < 45) return `${minutes} minutes`;
    if (hours <= 1) return 'an hour';
    if (hours < 22) return `${hours} hours`;
    if (days <= 1) return 'a day';
    if (days < 26) return `${days} days`;
    if (months <= 1) return 'a month';
    if (months < 11) return `${months} months`;
    if (years <= 1) return 'a year';
    return `${years} years`;
}
