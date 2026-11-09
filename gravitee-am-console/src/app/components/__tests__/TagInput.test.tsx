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
import { buttonHarness, renderWithGraphene } from '@gravitee/graphene-core/testing';
import { fireEvent, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { TagInput } from '../TagInput';

function setupTagInputHarness(values: string[] = ['openid'], addOnBlur = false, addOnSpace = false) {
    const onChange = vi.fn();
    const onDuplicate = vi.fn();
    renderWithGraphene(
        <>
            <label htmlFor="scopes">Scopes</label>
            <TagInput
                id="scopes"
                values={values}
                onChange={onChange}
                onDuplicate={onDuplicate}
                addOnBlur={addOnBlur}
                addOnSpace={addOnSpace}
            />
        </>,
    );
    const input = screen.getByRole('combobox', { name: 'Scopes' });
    return { onChange, onDuplicate, input, type: (text: string) => userEvent.type(input, text) };
}

describe('TagInput', () => {
    it('shows each tag', () => {
        setupTagInputHarness(['openid', 'profile']);

        expect(screen.getByText('openid')).toBeInTheDocument();
        expect(screen.getByText('profile')).toBeInTheDocument();
    });

    it('adds the typed tag on Enter and clears the input', async () => {
        const harness = setupTagInputHarness();

        await harness.type(' email {Enter}');

        expect(harness.onChange).toHaveBeenCalledWith(['openid', 'email']);
        expect(harness.input).toHaveValue('');
    });

    it('reports a duplicate and keeps the text', async () => {
        const harness = setupTagInputHarness();

        await harness.type('openid{Enter}');

        expect(harness.onDuplicate).toHaveBeenCalledWith('openid');
        expect(harness.onChange).not.toHaveBeenCalled();
        expect(harness.input).toHaveValue('openid');
    });

    it('adds the typed tag on blur when addOnBlur is set', async () => {
        const harness = setupTagInputHarness(['openid'], true);

        await harness.type('email');
        await userEvent.tab();

        expect(harness.onChange).toHaveBeenCalledWith(['openid', 'email']);
    });

    it('adds the typed tag on a space when addOnSpace is set', async () => {
        const harness = setupTagInputHarness(['openid'], false, true);

        await harness.type('email ');

        expect(harness.onChange).toHaveBeenCalledWith(['openid', 'email']);
        expect(harness.input).toHaveValue('');
    });

    it('splits a pasted text on its spaces when addOnSpace is set, and reports the duplicates', async () => {
        const harness = setupTagInputHarness(['openid'], false, true);

        await userEvent.click(harness.input);
        await userEvent.paste('openid profile  email');

        expect(harness.onChange).toHaveBeenCalledWith(['openid', 'profile', 'email']);
        expect(harness.onDuplicate).toHaveBeenCalledWith('openid');
        expect(harness.input).toHaveValue('');
    });

    it('splits a pasted text at the caret and replaces the selection', async () => {
        const harness = setupTagInputHarness([], false, true);

        await harness.type('opnid');
        (harness.input as HTMLInputElement).setSelectionRange(0, 5);
        await userEvent.paste('openid profile');

        expect(harness.onChange).toHaveBeenLastCalledWith(['openid', 'profile']);
    });

    it('keeps a space in the typed text by default', async () => {
        const harness = setupTagInputHarness();

        await harness.type('my value');

        expect(harness.onChange).not.toHaveBeenCalled();
        expect(harness.input).toHaveValue('my value');
    });

    it('does not add the typed tag on blur by default', async () => {
        const harness = setupTagInputHarness();

        await harness.type('email');
        await userEvent.tab();

        expect(harness.onChange).not.toHaveBeenCalled();
    });

    it('does not add while an IME composes the text', async () => {
        const harness = setupTagInputHarness();

        await harness.type('email');
        fireEvent.keyDown(harness.input, { key: 'Enter', isComposing: true });

        expect(harness.onChange).not.toHaveBeenCalled();
        expect(harness.input).toHaveValue('email');
    });

    it('ignores an empty value', async () => {
        const harness = setupTagInputHarness();

        await harness.type('   {Enter}');

        expect(harness.onChange).not.toHaveBeenCalled();
    });

    it('removes a tag', async () => {
        const harness = setupTagInputHarness(['openid', 'profile']);

        await buttonHarness({ name: 'Remove openid' }).click();

        expect(harness.onChange).toHaveBeenCalledWith(['profile']);
    });
});
