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
import { draftToSeconds, exactDuration, toSeconds } from '../duration';

describe('toSeconds', () => {
    it('converts as moment.duration(amount, unit).asSeconds() does', () => {
        expect(toSeconds(90, 'seconds')).toBe(90);
        expect(toSeconds(2, 'minutes')).toBe(120);
        expect(toSeconds(3, 'hours')).toBe(10_800);
        expect(toSeconds(2, 'days')).toBe(172_800);
        expect(toSeconds(1, 'weeks')).toBe(604_800);
        expect(toSeconds(1, 'months')).toBe(2_592_000);
        expect(toSeconds(1, 'years')).toBe(31_536_000);
    });
});

describe('exactDuration', () => {
    it('takes the largest unit that gives the seconds exactly', () => {
        expect(exactDuration(7_776_000)).toEqual({ time: '90', unit: 'days' });
        expect(exactDuration(1_209_600)).toEqual({ time: '2', unit: 'weeks' });
        expect(exactDuration(5_270_400)).toEqual({ time: '2', unit: 'months' });
        expect(exactDuration(31_536_000)).toEqual({ time: '1', unit: 'years' });
        expect(exactDuration(90)).toEqual({ time: '90', unit: 'seconds' });
    });
});

describe('draftToSeconds', () => {
    it('converts as moment does, and gives 0 without a unit', () => {
        expect(draftToSeconds({ time: '2', unit: 'hours' })).toBe(7_200);
        expect(draftToSeconds({ time: '', unit: 'hours' })).toBe(0);
        expect(draftToSeconds({ time: '', unit: '' })).toBe(0);
    });
});
