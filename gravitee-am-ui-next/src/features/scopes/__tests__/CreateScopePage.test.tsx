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
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { createScope } from '../../../lib/api/scopes';
import { CreateScopePage } from '../CreateScopePage';
import { currentDomain, customScope, ref, renderRoutes, scopesPath } from './render';

vi.mock('../../../lib/api/scopes', () => ({ createScope: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

function renderPage() {
    return renderRoutes(
        `${scopesPath}/new`,
        <>
            <Route path={`${scopesPath}/new`} element={<CreateScopePage />} />
            <Route path={`${scopesPath}/:scopeId`} element={<p>Scope detail</p>} />
        </>,
        ['domain_scope_create'],
    );
}

function fill(label: string, value: string) {
    fireEvent.change(screen.getByRole(label === 'Duration' ? 'spinbutton' : 'textbox', { name: label }), { target: { value } });
}

describe('CreateScopePage', () => {
    it('keeps Create disabled until key, name and description are filled', async () => {
        renderPage();
        const create = await screen.findByRole('button', { name: 'Create scope' });
        expect(create).toBeDisabled();

        fill('Key', 'orders:read');
        fill('Name', 'Orders read');
        await waitFor(() => expect(create).toBeDisabled());
        fill('Description', 'Read the orders');

        await waitFor(() => expect(create).toBeEnabled());
    });

    it('posts the Angular fields, without a consent period when none is set, and opens the new scope', async () => {
        vi.mocked(createScope).mockResolvedValue(customScope);
        renderPage();

        fill('Key', 'orders:read');
        fill('Name', 'Orders read');
        fill('Description', 'Read the orders');
        fireEvent.click(screen.getByRole('switch', { name: 'Show in discovery' }));
        const create = screen.getByRole('button', { name: 'Create scope' });
        await waitFor(() => expect(create).toBeEnabled());
        fireEvent.click(create);

        await waitFor(() =>
            expect(createScope).toHaveBeenCalledWith(ref, {
                key: 'orders:read',
                name: 'Orders read',
                description: 'Read the orders',
                discovery: true,
                // parameterized is left out, as Angular does until its switch is used.
                expiresIn: undefined,
            }),
        );
        expect(await screen.findByText('Scope detail')).toBeInTheDocument();
    });

    it('blocks Create while the consent duration has no unit', async () => {
        renderPage();
        fill('Key', 'a');
        fill('Name', 'A');
        fill('Description', 'A scope');
        fill('Duration', '5');

        await waitFor(() => expect(screen.getByText('Enter both a positive duration and a unit.')).toBeInTheDocument());
        expect(screen.getByRole('button', { name: 'Create scope' })).toBeDisabled();
    });
});
