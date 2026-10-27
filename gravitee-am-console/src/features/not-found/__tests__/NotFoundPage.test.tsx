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
import { screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { NotFoundPage } from '../NotFoundPage';

describe('NotFoundPage', () => {
    it('says the page does not exist and links home', () => {
        renderWithGraphene(
            <MemoryRouter initialEntries={['/404']}>
                <NotFoundPage />
            </MemoryRouter>,
        );

        expect(screen.getByText('Page not found')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Go home' })).toHaveAttribute('href', '/');
    });
});
