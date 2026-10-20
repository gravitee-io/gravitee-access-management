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
import { ValueListInput } from '../ValueListInput';

interface HarnessProps {
    readonly editable?: boolean;
    readonly onDuplicate?: (value: string) => void;
    readonly onRemoveRequest?: (value: string) => void;
}

function Harness({ editable = true, onDuplicate = vi.fn(), onRemoveRequest }: HarnessProps) {
    const [values, setValues] = useState(['https://a.example.com']);
    return (
        <ValueListInput
            label="request URI"
            values={values}
            editable={editable}
            emptyText="There are no request URIs."
            onChange={setValues}
            onDuplicate={onDuplicate}
            onRemoveRequest={onRemoveRequest}
        />
    );
}

describe('ValueListInput', () => {
    it('adds the trimmed value with the Add button and on Enter, then removes a value', () => {
        render(<Harness />);
        const input = screen.getByRole('textbox', { name: 'Add request URI' });

        fireEvent.change(input, { target: { value: ' https://b.example.com ' } });
        fireEvent.click(screen.getByRole('button', { name: 'Add' }));
        fireEvent.change(input, { target: { value: 'https://c.example.com' } });
        fireEvent.keyDown(input, { key: 'Enter' });

        expect(screen.getByText('https://b.example.com')).toBeInTheDocument();
        expect(screen.getByText('https://c.example.com')).toBeInTheDocument();
        expect(input).toHaveValue('');

        fireEvent.click(screen.getByRole('button', { name: 'Remove https://a.example.com' }));

        expect(screen.queryByText('https://a.example.com')).not.toBeInTheDocument();
    });

    it('reports a duplicate and keeps the list', () => {
        const onDuplicate = vi.fn();
        render(<Harness onDuplicate={onDuplicate} />);

        fireEvent.change(screen.getByRole('textbox', { name: 'Add request URI' }), { target: { value: 'https://a.example.com' } });
        fireEvent.click(screen.getByRole('button', { name: 'Add' }));

        expect(onDuplicate).toHaveBeenCalledWith('https://a.example.com');
        expect(screen.getAllByText('https://a.example.com')).toHaveLength(1);
    });

    it('asks the page before a remove when onRemoveRequest is set', () => {
        const onRemoveRequest = vi.fn();
        render(<Harness onRemoveRequest={onRemoveRequest} />);

        fireEvent.click(screen.getByRole('button', { name: 'Remove https://a.example.com' }));

        expect(onRemoveRequest).toHaveBeenCalledWith('https://a.example.com');
        expect(screen.getByText('https://a.example.com')).toBeInTheDocument();
    });

    it('shows the values without the input or remove actions when not editable', () => {
        render(<Harness editable={false} />);

        expect(screen.getByText('https://a.example.com')).toBeInTheDocument();
        expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
        expect(screen.queryByRole('button')).not.toBeInTheDocument();
    });
});
