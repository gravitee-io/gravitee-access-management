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
import { type DurationUnit, toSeconds } from '../../lib/forms/duration';

export type ConsentDraft = {
    amount: string;
    unit: DurationUnit | '';
};

export const EMPTY_CONSENT: ConsentDraft = { amount: '', unit: '' };

/** Angular `formIsInvalid()`: an amount without a unit, or a unit without a positive amount, blocks the save. */
export function consentDraftInvalid(draft: ConsentDraft): boolean {
    const amount = Number(draft.amount);
    return (amount > 0 && !draft.unit) || (!!draft.unit && draft.amount !== '' && !(amount > 0));
}

/** Angular sets `expiresIn` only when both an amount and a unit are given. */
export function draftSeconds(draft: ConsentDraft): number | undefined {
    return draft.unit && Number(draft.amount) > 0 ? toSeconds(Number(draft.amount), draft.unit) : undefined;
}
