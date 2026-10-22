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
import { consentDraftInvalid, draftSeconds } from '../consent';

describe('consentDraftInvalid', () => {
    it('blocks an amount without a unit and a unit with a non-positive amount', () => {
        expect(consentDraftInvalid({ amount: '', unit: '' })).toBe(false);
        expect(consentDraftInvalid({ amount: '5', unit: '' })).toBe(true);
        expect(consentDraftInvalid({ amount: '', unit: 'days' })).toBe(false);
        expect(consentDraftInvalid({ amount: '0', unit: 'days' })).toBe(true);
        expect(consentDraftInvalid({ amount: '5', unit: 'days' })).toBe(false);
    });
});

describe('draftSeconds', () => {
    it('sets a period only when both the amount and the unit are given', () => {
        expect(draftSeconds({ amount: '2', unit: 'hours' })).toBe(7_200);
        expect(draftSeconds({ amount: '2', unit: '' })).toBeUndefined();
        expect(draftSeconds({ amount: '', unit: 'hours' })).toBeUndefined();
    });
});
