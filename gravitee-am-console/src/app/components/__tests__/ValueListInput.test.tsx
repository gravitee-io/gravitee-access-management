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
import { buttonHarness, inputHarness, renderWithGraphene } from '@gravitee/graphene-core/testing';
import { screen, within } from '@testing-library/react';
import { ValueListInput } from '../ValueListInput';

interface HarnessProps {
    readonly values?: string[];
    readonly editable?: boolean;
    readonly confirmRemove?: boolean;
}

function setupValueListInputHarness({ values = ['https://a.test/cb'], editable = true, confirmRemove = false }: HarnessProps = {}) {
    const onChange = vi.fn();
    const onDuplicate = vi.fn();
    const onRemoveRequest = vi.fn();
    renderWithGraphene(
        <ValueListInput
            label="redirect URI"
            values={values}
            editable={editable}
            emptyText="No redirect URI."
            onChange={onChange}
            onDuplicate={onDuplicate}
            onRemoveRequest={confirmRemove ? onRemoveRequest : undefined}
        />,
    );
    return { onChange, onDuplicate, onRemoveRequest };
}

describe('ValueListInput', () => {
    it('lists the values', () => {
        setupValueListInputHarness({ values: ['https://a.test/cb', 'https://b.test/cb'] });

        const items = within(screen.getByRole('list', { name: 'redirect URI' })).getAllByRole('listitem');
        expect(items.map(item => item.textContent)).toEqual(['https://a.test/cb', 'https://b.test/cb']);
    });

    it('shows the empty text', () => {
        setupValueListInputHarness({ values: [] });

        expect(screen.getByText('No redirect URI.')).toBeInTheDocument();
        expect(screen.queryByRole('list')).not.toBeInTheDocument();
    });

    it('adds the typed value with the Add button', async () => {
        const harness = setupValueListInputHarness();

        await inputHarness({ name: 'Add redirect URI' }).type(' https://b.test/cb ');
        await buttonHarness({ name: 'Add' }).click();

        expect(harness.onChange).toHaveBeenCalledWith(['https://a.test/cb', 'https://b.test/cb']);
        expect(inputHarness({ name: 'Add redirect URI' }).getValue()).toBe('');
    });

    it('adds the typed value on Enter', async () => {
        const harness = setupValueListInputHarness();

        await inputHarness({ name: 'Add redirect URI' }).type('https://b.test/cb{Enter}');

        expect(harness.onChange).toHaveBeenCalledWith(['https://a.test/cb', 'https://b.test/cb']);
    });

    it('reports a duplicate', async () => {
        const harness = setupValueListInputHarness();

        await inputHarness({ name: 'Add redirect URI' }).type('https://a.test/cb{Enter}');

        expect(harness.onDuplicate).toHaveBeenCalledWith('https://a.test/cb');
        expect(harness.onChange).not.toHaveBeenCalled();
    });

    it('removes a value', async () => {
        const harness = setupValueListInputHarness();

        await buttonHarness({ name: 'Remove https://a.test/cb' }).click();

        expect(harness.onChange).toHaveBeenCalledWith([]);
    });

    it('asks the page before a remove when the page confirms', async () => {
        const harness = setupValueListInputHarness({ confirmRemove: true });

        await buttonHarness({ name: 'Remove https://a.test/cb' }).click();

        expect(harness.onRemoveRequest).toHaveBeenCalledWith('https://a.test/cb');
        expect(harness.onChange).not.toHaveBeenCalled();
    });

    it('hides the input and the remove actions when read-only', () => {
        setupValueListInputHarness({ editable: false });

        expect(screen.queryByRole('textbox', { name: 'Add redirect URI' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Remove https://a.test/cb' })).not.toBeInTheDocument();
    });
});
