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
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { HttpError } from '../../../lib/api/http';
import { deleteScope, getScope, updateScope } from '../../../lib/api/scopes';
import { ScopePage } from '../ScopePage';
import { currentDomain, customScope, ref, renderRoutes, scopesPath, systemScope } from './render';

vi.mock('../../../lib/api/scopes', () => ({ deleteScope: vi.fn(), getScope: vi.fn(), updateScope: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_scope_read', 'domain_scope_update', 'domain_scope_delete'];

function renderPage(permissions = ALL) {
    return renderRoutes(
        `${scopesPath}/s-1`,
        <>
            <Route path={`${scopesPath}/:scopeId`} element={<ScopePage />} />
            <Route path={scopesPath} element={<p>Scope list</p>} />
        </>,
        permissions,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getScope).mockResolvedValue(customScope);
});

describe('ScopePage', () => {
    it('shows the scope with its key fixed and the current consent period', async () => {
        renderPage();

        expect(await screen.findByRole('heading', { name: 'Orders read' })).toBeInTheDocument();
        expect(screen.getByRole('textbox', { name: 'Key' })).toBeDisabled();
        expect(screen.getByText('a day')).toBeInTheDocument();
        expect(getScope).toHaveBeenCalledWith(ref, 's-1');
    });

    it('saves the five Angular fields', async () => {
        vi.mocked(updateScope).mockImplementation(async (_ref, _id, body) => ({
            ...customScope,
            ...body,
            expiresIn: body.expiresIn ?? undefined,
        }));
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Orders' } });
        fireEvent.click(screen.getByRole('switch', { name: 'Allow a parameter' }));
        const save = screen.getByRole('button', { name: 'Save' });
        await waitFor(() => expect(save).toBeEnabled());
        fireEvent.click(save);

        await waitFor(() =>
            expect(updateScope).toHaveBeenCalledWith(ref, 's-1', {
                name: 'Orders',
                description: 'Read the orders',
                expiresIn: 86_400,
                discovery: false,
                parameterized: true,
            }),
        );
    });

    it('replaces the consent period with the duration and unit, in seconds', async () => {
        vi.mocked(updateScope).mockResolvedValue(customScope);
        renderPage();

        fireEvent.change(await screen.findByRole('spinbutton', { name: 'Duration' }), { target: { value: '2' } });
        fireEvent.click(screen.getByRole('combobox', { name: 'Unit' }));
        fireEvent.click(await screen.findByRole('option', { name: 'Hours' }));
        const save = screen.getByRole('button', { name: 'Save' });
        await waitFor(() => expect(save).toBeEnabled());
        fireEvent.click(save);

        await waitFor(() => expect(updateScope).toHaveBeenCalledWith(ref, 's-1', expect.objectContaining({ expiresIn: 7_200 })));
    });

    it('sends a null period once it is cleared', async () => {
        vi.mocked(updateScope).mockResolvedValue({ ...customScope, expiresIn: undefined });
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Clear the consent period' }));
        expect(screen.getByText('No time set')).toBeInTheDocument();
        const save = screen.getByRole('button', { name: 'Save' });
        await waitFor(() => expect(save).toBeEnabled());
        fireEvent.click(save);

        await waitFor(() => expect(updateScope).toHaveBeenCalledWith(ref, 's-1', expect.objectContaining({ expiresIn: null })));
    });

    it('locks the switches of a system scope and hides its delete action', async () => {
        vi.mocked(getScope).mockResolvedValue(systemScope);
        renderPage();

        expect(await screen.findByRole('switch', { name: 'Show in discovery' })).toBeDisabled();
        expect(screen.getByRole('switch', { name: 'Allow a parameter' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('is read-only without the update permission, and has no delete action without the delete permission', async () => {
        renderPage(['domain_scope_read']);

        expect(await screen.findByRole('textbox', { name: 'Name' })).toHaveAttribute('readonly');
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
        expect(screen.queryByRole('spinbutton', { name: 'Duration' })).not.toBeInTheDocument();
    });

    it('deletes through the confirm dialog and returns to the list', async () => {
        vi.mocked(deleteScope).mockResolvedValue(undefined);
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
        const dialog = await screen.findByRole('dialog');
        expect(deleteScope).not.toHaveBeenCalled();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(deleteScope).toHaveBeenCalledWith(ref, 's-1'));
        expect(await screen.findByText('Scope list')).toBeInTheDocument();
    });

    it('shows a not-found panel for a missing scope', async () => {
        vi.mocked(getScope).mockRejectedValue(new HttpError(404, 'Not found'));
        renderPage();

        expect(await screen.findByText('Scope not found')).toBeInTheDocument();
    });
});
