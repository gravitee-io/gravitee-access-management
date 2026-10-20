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
import { SaveBar } from '../SaveBar';

describe('SaveBar', () => {
    it('keeps both buttons disabled until the form is dirty', () => {
        render(<SaveBar dirty={false} saving={false} onDiscard={vi.fn()} />);

        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Discard changes' })).toBeDisabled();
    });

    it('submits the surrounding form and discards through the callback', () => {
        const onSubmit = vi.fn((event: React.FormEvent) => event.preventDefault());
        const onDiscard = vi.fn();
        render(
            <form onSubmit={onSubmit}>
                <SaveBar dirty saving={false} onDiscard={onDiscard} />
            </form>,
        );

        fireEvent.click(screen.getByRole('button', { name: 'Save' }));
        fireEvent.click(screen.getByRole('button', { name: 'Discard changes' }));

        expect(onSubmit).toHaveBeenCalledOnce();
        expect(onDiscard).toHaveBeenCalledOnce();
    });

    it('shows the saving state and blocks an invalid form', () => {
        const { rerender } = render(<SaveBar dirty saving onDiscard={vi.fn()} />);
        expect(screen.getByRole('button', { name: 'Saving…' })).toBeDisabled();

        rerender(<SaveBar dirty saving={false} invalid onDiscard={vi.fn()} />);
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('renders nothing for a user who cannot edit', () => {
        const { container } = render(<SaveBar canEdit={false} dirty saving={false} onDiscard={vi.fn()} />);

        expect(container).toBeEmptyDOMElement();
    });
});
