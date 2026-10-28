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
import { inputHarness, renderWithGraphene, selectHarness } from '@gravitee/graphene-core/testing';
import { fireEvent, screen } from '@testing-library/react';
import { DurationInput } from '../DurationInput';

function setupDurationInputHarness() {
    const onChange = vi.fn();
    renderWithGraphene(
        <>
            <label htmlFor="expiry">Expiry</label>
            <DurationInput id="expiry" label="Expiry" value={{ time: '2', unit: 'hours' }} onChange={onChange} />
        </>,
    );
    return { onChange };
}

describe('DurationInput', () => {
    it('shows the amount and the unit', () => {
        setupDurationInputHarness();

        expect(inputHarness({ name: 'Expiry' }).getValue()).toBe('2');
        expect(screen.getByRole('combobox', { name: 'Expiry unit' })).toHaveTextContent('Hours');
    });

    it('changes the amount and keeps the unit', () => {
        const harness = setupDurationInputHarness();

        fireEvent.change(screen.getByRole('spinbutton', { name: 'Expiry' }), { target: { value: '5' } });

        expect(harness.onChange).toHaveBeenCalledWith({ time: '5', unit: 'hours' });
    });

    it('changes the unit and keeps the amount', async () => {
        const harness = setupDurationInputHarness();

        await selectHarness({ name: 'Expiry unit' }).selectOption('Days');

        expect(harness.onChange).toHaveBeenCalledWith({ time: '2', unit: 'days' });
    });
});
