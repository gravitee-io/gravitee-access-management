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
import { histogram, rangeStart, sentenceCase, seriesColor, TIME_RANGES } from '../analytics-widgets';

const range = (id: string) => TIME_RANGES.find(r => r.id === id)!;

describe('analytics-widgets', () => {
    it('starts each time range as moment().subtract does', () => {
        const to = new Date(2026, 9, 21, 12, 0).getTime();

        expect(rangeStart(range('1h'), to)).toBe(new Date(2026, 9, 21, 11, 0).getTime());
        expect(rangeStart(range('30d'), to)).toBe(new Date(2026, 8, 23, 12, 0).getTime());
        expect(rangeStart(range('90d'), to)).toBe(new Date(2026, 6, 21, 12, 0).getTime());
        expect(rangeStart(range('90d'), new Date(2026, 4, 31, 12, 0).getTime())).toBe(new Date(2026, 1, 28, 12, 0).getTime());
        expect(rangeStart(range('12h'), to)).toBe(to - 12 * 3_600_000);
    });

    it('keeps every histogram bucket as a series', () => {
        const result = histogram(
            {
                timestamp: { from: 0, to: 7_200_000, interval: 3_600_000 },
                values: [
                    { field: 'user_login', name: 'user_login_success', data: [3, 4] },
                    { field: 'user_login', name: 'user_login_failure', data: [1, 0] },
                ],
            },
            range('1d'),
        );

        expect(result.series).toEqual(['user_login_success', 'user_login_failure']);
        expect(result.data.map(({ category: _, ...values }) => values)).toEqual([
            { user_login_success: 3, user_login_failure: 1 },
            { user_login_success: 4, user_login_failure: 0 },
        ]);
    });

    it('colors the login outcomes with the semantic tokens and the other series with the chart palette', () => {
        expect(seriesColor('user_login_success', 0)).toBe('var(--success)');
        expect(seriesColor('failure', 1)).toBe('var(--destructive)');
        expect(seriesColor('active', 0)).toBe('var(--chart-1)');
        expect(seriesColor('locked', 6)).toBe('var(--chart-2)');
    });

    it('writes a key as a sentence', () => {
        expect(sentenceCase('user_login_failure')).toBe('User login failure');
    });
});
