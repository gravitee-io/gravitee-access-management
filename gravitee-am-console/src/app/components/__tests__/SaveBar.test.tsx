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
import { LayoutSlotsProvider, useLayoutSlots } from '@gravitee/graphene-core';
import { buttonHarness, renderWithGraphene } from '@gravitee/graphene-core/testing';
import { screen } from '@testing-library/react';
import { SaveBar } from '../SaveBar';

interface HarnessProps {
    readonly dirty?: boolean;
    readonly saving?: boolean;
    readonly invalid?: boolean;
}

function Banner() {
    const { slots } = useLayoutSlots();
    return <>{slots.banner}</>;
}

function setupSaveBarHarness({ dirty = true, saving = false, invalid = false }: HarnessProps = {}) {
    const onSubmit = vi.fn((event: React.FormEvent) => event.preventDefault());
    const onDiscard = vi.fn();
    const ui = (discard: () => void) => (
        <LayoutSlotsProvider>
            <Banner />
            <form id="settings" onSubmit={onSubmit} />
            <SaveBar formId="settings" dirty={dirty} saving={saving} invalid={invalid} onDiscard={discard} />
        </LayoutSlotsProvider>
    );
    const { rerender } = renderWithGraphene(ui(onDiscard));
    return { onSubmit, onDiscard, rerenderWith: (discard: () => void) => rerender(ui(discard)) };
}

describe('SaveBar', () => {
    it('is hidden while the form is clean', () => {
        setupSaveBarHarness({ dirty: false });

        expect(screen.queryByText('You have unsaved changes.')).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });

    it('submits the form named by formId', async () => {
        const harness = setupSaveBarHarness();

        await buttonHarness({ name: 'Save' }).click();

        expect(screen.getByText('You have unsaved changes.')).toBeInTheDocument();
        expect(harness.onSubmit).toHaveBeenCalledOnce();
    });

    it('discards the changes', async () => {
        const harness = setupSaveBarHarness();

        await buttonHarness({ name: 'Discard' }).click();

        expect(harness.onDiscard).toHaveBeenCalledOnce();
        expect(harness.onSubmit).not.toHaveBeenCalled();
    });

    it('discards with the onDiscard of the last render', async () => {
        const harness = setupSaveBarHarness();
        const latest = vi.fn();
        harness.rerenderWith(latest);

        await buttonHarness({ name: 'Discard' }).click();

        expect(latest).toHaveBeenCalledOnce();
        expect(harness.onDiscard).not.toHaveBeenCalled();
    });

    it('blocks Save while the form is invalid', () => {
        setupSaveBarHarness({ invalid: true });

        expect(buttonHarness({ name: 'Save' }).isDisabled()).toBe(true);
        expect(buttonHarness({ name: 'Discard' }).isDisabled()).toBe(false);
    });

    it('disables both buttons while the save runs', () => {
        setupSaveBarHarness({ saving: true });

        expect(buttonHarness({ name: 'Saving…' }).isDisabled()).toBe(true);
        expect(buttonHarness({ name: 'Discard' }).isDisabled()).toBe(true);
    });
});
