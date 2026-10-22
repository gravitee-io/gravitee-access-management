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
import { deleteScope, listScopes, patchScope } from '../../../lib/api/scopes';
import { ScopesPage } from '../ScopesPage';
import { currentDomain, customScope, ref, renderRoutes, scopesPath, systemScope } from './render';

vi.mock('../../../lib/api/scopes', () => ({ deleteScope: vi.fn(), listScopes: vi.fn(), patchScope: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_scope_read', 'domain_scope_create', 'domain_scope_update', 'domain_scope_delete'];

function renderPage(permissions = ALL) {
    return renderRoutes(scopesPath, <Route path={scopesPath} element={<ScopesPage />} />, permissions);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listScopes).mockResolvedValue({ data: [systemScope, customScope], currentPage: 0, totalCount: 2 });
});

describe('ScopesPage', () => {
    it('lists the scopes of the first page with a link to each', async () => {
        renderPage();

        expect(await screen.findByRole('link', { name: 'orders:read' })).toHaveAttribute('href', `${scopesPath}/s-1`);
        expect(screen.getByText('Orders read')).toBeInTheDocument();
        expect(listScopes).toHaveBeenCalledWith(ref, 0, 25, '');
    });

    it('searches with the text', async () => {
        renderPage();
        await screen.findByRole('link', { name: 'orders:read' });

        fireEvent.change(screen.getByRole('textbox', { name: 'Search scopes' }), { target: { value: 'ord' } });

        await waitFor(() => expect(listScopes).toHaveBeenCalledWith(ref, 0, 25, 'ord'));
    });

    it('shows the no-results state for a search with no match', async () => {
        renderPage();
        await screen.findByRole('link', { name: 'orders:read' });
        vi.mocked(listScopes).mockResolvedValue({ data: [], currentPage: 0, totalCount: 0 });

        fireEvent.change(screen.getByRole('textbox', { name: 'Search scopes' }), { target: { value: 'zzz' } });

        expect(await screen.findByText('No scopes match your search')).toBeInTheDocument();
    });

    it('shows the first-use state when the domain has no scope', async () => {
        vi.mocked(listScopes).mockResolvedValue({ data: [], currentPage: 0, totalCount: 0 });
        renderPage();

        expect(await screen.findByText('No scopes yet')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Create scope' })).toHaveAttribute('href', `${scopesPath}/new`);
    });

    it('shows an error, not the first-use state, when the list fails', async () => {
        vi.mocked(listScopes).mockRejectedValue(new HttpError(500, 'boom'));
        renderPage();

        expect(await screen.findByText('Could not load the scopes.')).toBeInTheDocument();
        expect(screen.queryByText('No scopes yet')).not.toBeInTheDocument();
    });

    it('patches one field when a switch is toggled, as Angular does', async () => {
        vi.mocked(patchScope).mockResolvedValue(customScope);
        renderPage();

        fireEvent.click(await screen.findByRole('switch', { name: 'List orders:read in the discovery endpoint' }));
        await waitFor(() => expect(patchScope).toHaveBeenCalledWith(ref, 's-1', { discovery: true }));
        await waitFor(() => expect(screen.getByRole('switch', { name: 'Allow a parameter on orders:read' })).toBeEnabled());

        fireEvent.click(screen.getByRole('switch', { name: 'Allow a parameter on orders:read' }));
        await waitFor(() => expect(patchScope).toHaveBeenCalledWith(ref, 's-1', { parameterized: true }));
    });

    it('locks the switches and the delete action of a system scope', async () => {
        renderPage();

        expect(await screen.findByRole('switch', { name: 'List openid in the discovery endpoint' })).toBeDisabled();
        expect(screen.getByRole('switch', { name: 'Allow a parameter on openid' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Delete openid' })).not.toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Delete orders:read' })).toBeInTheDocument();
    });

    it('hides the create, delete and toggle actions without the permissions', async () => {
        renderPage(['domain_scope_read']);

        expect(await screen.findByRole('link', { name: 'orders:read' })).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Create scope' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete orders:read' })).not.toBeInTheDocument();
        expect(screen.getByRole('switch', { name: 'List orders:read in the discovery endpoint' })).toBeDisabled();
    });

    it('shows the key without a link when the user cannot read a scope', async () => {
        renderPage([]);

        expect(await screen.findByText('orders:read')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'orders:read' })).not.toBeInTheDocument();
    });

    it('deletes through the confirm dialog and reloads the list', async () => {
        vi.mocked(deleteScope).mockResolvedValue(undefined);
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Delete orders:read' }));
        const dialog = await screen.findByRole('dialog');
        expect(deleteScope).not.toHaveBeenCalled();
        fireEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(deleteScope).toHaveBeenCalledWith(ref, 's-1'));
        await waitFor(() => expect(listScopes).toHaveBeenCalledTimes(2));
    });
});
