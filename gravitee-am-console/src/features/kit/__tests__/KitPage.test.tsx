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
import { LayoutSlotsProvider } from '@gravitee/graphene-core';
import { renderWithGraphene } from '@gravitee/graphene-core/testing';
import { screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { KitPage } from '../KitPage';

describe('KitPage', () => {
    it('shows one section per shared part', () => {
        renderWithGraphene(
            <MemoryRouter>
                <LayoutSlotsProvider>
                    <KitPage />
                </LayoutSlotsProvider>
            </MemoryRouter>,
        );

        expect(screen.getAllByRole('heading', { level: 2 }).map(heading => heading.textContent)).toEqual([
            'SaveBar',
            'ConfirmDialog',
            'SettingToggle',
            'TagInput',
            'ValueListInput',
            'SearchField',
            'DurationInput',
            'UserAvatar',
            'Empty state (Graphene DataTableEmptyState)',
        ]);
    });
});
