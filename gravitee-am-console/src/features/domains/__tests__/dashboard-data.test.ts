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
import { groups, histogram, rangeStart, registration, sentenceCase, TIME_RANGES, widgetQuery } from '../dashboard-data';

const range = (id: string) => TIME_RANGES.find(r => r.id === id)!;

describe('dashboard-data', () => {
    it('starts each time range as moment().subtract does', () => {
        const to = new Date(2026, 9, 21, 12, 0).getTime();

        expect(rangeStart(range('1h'), to)).toBe(new Date(2026, 9, 21, 11, 0).getTime());
        expect(rangeStart(range('30d'), to)).toBe(new Date(2026, 8, 23, 12, 0).getTime());
        expect(rangeStart(range('90d'), to)).toBe(new Date(2026, 6, 21, 12, 0).getTime());
        expect(rangeStart(range('90d'), new Date(2026, 4, 31, 12, 0).getTime())).toBe(new Date(2026, 1, 28, 12, 0).getTime());
        expect(rangeStart(range('12h'), to)).toBe(to - 12 * 3_600_000);
    });

    it('builds the Angular query of a widget', () => {
        const to = new Date(2026, 9, 21, 12, 0).getTime();

        expect(widgetQuery('topApplications', range('7d'), to)).toEqual({
            type: 'group_by',
            field: 'application',
            from: new Date(2026, 9, 14, 12, 0).getTime(),
            to,
            interval: 86_400_000,
            size: 20,
        });
    });

    it('names the groups from the metadata and marks a key without metadata as deleted', () => {
        expect(
            groups({
                values: { a1: 5, gone: 2 },
                metadata: { a1: { name: 'Portal' }, gone: { name: 'Deleted application', deleted: true } },
            }),
        ).toEqual([
            { key: 'a1', label: 'Portal', value: 5, deleted: false },
            { key: 'gone', label: 'Deleted application', value: 2, deleted: true },
        ]);
        expect(groups({ values: { active: 7 }, metadata: null })).toEqual([{ key: 'active', label: 'active', value: 7, deleted: true }]);
    });

    it('reads the registration as completed out of total', () => {
        expect(registration({ values: { total: 4, completed: 3 } })).toEqual({ completed: 3, total: 4 });
        expect(registration(undefined)).toEqual({ completed: 0, total: 0 });
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

    it('writes a key as a sentence', () => {
        expect(sentenceCase('user_login_failure')).toBe('User login failure');
    });
});
