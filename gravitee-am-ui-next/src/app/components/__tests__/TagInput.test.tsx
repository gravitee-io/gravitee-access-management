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
import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { TagInput } from '../TagInput';

function Harness({ onDuplicate }: { readonly onDuplicate?: (value: string) => void }) {
    const [values, setValues] = useState(['gmail.com']);
    return <TagInput id="tags" values={values} onChange={setValues} onDuplicate={onDuplicate} placeholder="Add a pattern" />;
}

describe('TagInput', () => {
    it('adds the typed value on Enter and removes a badge', () => {
        render(<Harness />);
        const input = screen.getByPlaceholderText('Add a pattern');

        fireEvent.change(input, { target: { value: ' acme.com ' } });
        fireEvent.keyDown(input, { key: 'Enter' });

        expect(screen.getByText('acme.com')).toBeInTheDocument();
        expect(input).toHaveValue('');

        fireEvent.click(screen.getByRole('button', { name: 'Remove gmail.com' }));

        expect(screen.queryByText('gmail.com')).not.toBeInTheDocument();
    });

    it('reports a duplicate and keeps the text', () => {
        const onDuplicate = vi.fn();
        render(<Harness onDuplicate={onDuplicate} />);
        const input = screen.getByPlaceholderText('Add a pattern');

        fireEvent.change(input, { target: { value: 'gmail.com' } });
        fireEvent.keyDown(input, { key: 'Enter' });

        expect(onDuplicate).toHaveBeenCalledWith('gmail.com');
        expect(screen.getAllByText('gmail.com')).toHaveLength(1);
        expect(input).toHaveValue('gmail.com');
    });
});
