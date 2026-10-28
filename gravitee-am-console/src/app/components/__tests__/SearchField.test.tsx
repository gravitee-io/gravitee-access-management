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
import { renderWithGraphene } from '@gravitee/graphene-core/testing';
import { act, fireEvent, screen } from '@testing-library/react';
import { SearchField } from '../SearchField';

function setupSearchFieldHarness() {
    const onSearch = vi.fn();
    renderWithGraphene(<SearchField label="scopes" onSearch={onSearch} />);
    const input = screen.getByRole('searchbox', { name: 'Search scopes' });
    return { onSearch, type: (value: string) => fireEvent.change(input, { target: { value } }) };
}

describe('SearchField', () => {
    beforeEach(() => vi.useFakeTimers());
    afterEach(() => vi.useRealTimers());

    it('searches 300 ms after the last change, with the trimmed text', () => {
        const harness = setupSearchFieldHarness();

        harness.type('ope');
        act(() => vi.advanceTimersByTime(200));
        harness.type(' openid ');
        act(() => vi.advanceTimersByTime(299));
        expect(harness.onSearch).not.toHaveBeenCalled();

        act(() => vi.advanceTimersByTime(1));
        expect(harness.onSearch).toHaveBeenCalledOnce();
        expect(harness.onSearch).toHaveBeenCalledWith('openid');
    });

    it('does not search on the first render', () => {
        const harness = setupSearchFieldHarness();

        act(() => vi.advanceTimersByTime(1_000));

        expect(harness.onSearch).not.toHaveBeenCalled();
    });
});
