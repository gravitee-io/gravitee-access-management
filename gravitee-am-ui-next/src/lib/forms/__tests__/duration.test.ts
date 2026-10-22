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
import { humanizeSeconds, toSeconds } from '../duration';

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

describe('humanizeSeconds', () => {
    it.each([
        [30, 'a few seconds'],
        [60, 'a minute'],
        [600, '10 minutes'],
        [3_600, 'an hour'],
        [7_200, '2 hours'],
        [86_400, 'a day'],
        [172_800, '2 days'],
        [2_592_000, 'a month'],
        [7_776_000, '3 months'],
        [31_536_000, 'a year'],
        [94_608_000, '3 years'],
    ])('prints %d seconds as %s', (seconds, text) => {
        expect(humanizeSeconds(seconds)).toBe(text);
    });
});
