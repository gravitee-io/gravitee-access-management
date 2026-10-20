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
import { ConfirmDialog } from '../ConfirmDialog';

function renderDialog(props: Partial<Parameters<typeof ConfirmDialog>[0]> = {}) {
    const onOpenChange = vi.fn();
    const onConfirm = vi.fn();
    render(
        <ConfirmDialog
            open
            title="Delete scope"
            description="Delete read? This cannot be undone."
            confirmLabel="Delete scope"
            destructive
            onOpenChange={onOpenChange}
            onConfirm={onConfirm}
            {...props}
        />,
    );
    return { onOpenChange, onConfirm };
}

describe('ConfirmDialog', () => {
    it('shows the question and confirms on the labelled button', () => {
        const { onConfirm } = renderDialog();

        expect(screen.getByRole('dialog', { name: 'Delete scope' })).toHaveTextContent('Delete read? This cannot be undone.');
        fireEvent.click(screen.getByRole('button', { name: 'Delete scope' }));

        expect(onConfirm).toHaveBeenCalledOnce();
    });

    it('closes without confirming on Cancel', () => {
        const { onOpenChange, onConfirm } = renderDialog();

        fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

        expect(onOpenChange).toHaveBeenCalledWith(false);
        expect(onConfirm).not.toHaveBeenCalled();
    });

    it('disables both buttons while the action runs', () => {
        renderDialog({ pending: true });

        expect(screen.getByRole('button', { name: 'Delete scope' })).toBeDisabled();
        expect(screen.getByRole('button', { name: 'Cancel' })).toBeDisabled();
    });
});
