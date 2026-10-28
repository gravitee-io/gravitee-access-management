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
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ConfirmDialog } from '../ConfirmDialog';

function setupConfirmDialogHarness({ pending = false } = {}) {
    const onOpenChange = vi.fn();
    const onConfirm = vi.fn();
    renderWithGraphene(
        <ConfirmDialog
            open
            title="Delete scope"
            description="The scope is deleted. This cannot be undone."
            confirmLabel="Delete"
            destructive
            pending={pending}
            onOpenChange={onOpenChange}
            onConfirm={onConfirm}
        />,
    );
    return { onOpenChange, onConfirm };
}

describe('ConfirmDialog', () => {
    it('shows the title and the description', () => {
        setupConfirmDialogHarness();

        const dialog = screen.getByRole('dialog', { name: 'Delete scope' });
        expect(dialog).toHaveAccessibleDescription('The scope is deleted. This cannot be undone.');
    });

    it('confirms', async () => {
        const harness = setupConfirmDialogHarness();

        await buttonHarness({ name: 'Delete' }).click();

        expect(harness.onConfirm).toHaveBeenCalledOnce();
        expect(harness.onOpenChange).not.toHaveBeenCalled();
    });

    it('closes on Cancel without a confirm', async () => {
        const harness = setupConfirmDialogHarness();

        await buttonHarness({ name: 'Cancel' }).click();

        expect(harness.onOpenChange).toHaveBeenCalledWith(false);
        expect(harness.onConfirm).not.toHaveBeenCalled();
    });

    it('disables both buttons while the action runs', () => {
        setupConfirmDialogHarness({ pending: true });

        expect(buttonHarness({ name: 'Delete' }).isDisabled()).toBe(true);
        expect(buttonHarness({ name: 'Cancel' }).isDisabled()).toBe(true);
    });

    it('stays open on Escape while the action runs', async () => {
        const harness = setupConfirmDialogHarness({ pending: true });

        await userEvent.keyboard('{Escape}');

        expect(harness.onOpenChange).not.toHaveBeenCalled();
        expect(screen.queryByRole('button', { name: 'Close' })).not.toBeInTheDocument();
    });
});
