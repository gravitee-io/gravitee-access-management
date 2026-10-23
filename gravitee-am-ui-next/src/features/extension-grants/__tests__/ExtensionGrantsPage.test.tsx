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
import { deleteExtensionGrant, listExtensionGrants } from '../../../lib/api/extension-grants';
import { ExtensionGrantsPage } from '../ExtensionGrantsPage';
import { currentDomain, extensionGrantsPath, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/extension-grants', () => ({ deleteExtensionGrant: vi.fn(), listExtensionGrants: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_extension_grant_create', 'domain_extension_grant_read', 'domain_extension_grant_delete'];

function renderPage(permissions = ALL) {
    return renderRoutes(extensionGrantsPath, <Route path={extensionGrantsPath} element={<ExtensionGrantsPage />} />, permissions);
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listExtensionGrants).mockResolvedValue([
        { id: 'g-1', name: 'Partner JWT', type: 'jwtbearer-am-extension-grant', grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer' },
        { id: 'g-2', name: 'Custom', type: 'custom-am-extension-grant', grantType: 'urn:custom' },
    ]);
});

describe('ExtensionGrantsPage', () => {
    it('lists the extension grants of the domain with the Angular type names and a link to each', async () => {
        renderPage();

        expect(await screen.findByRole('link', { name: 'Partner JWT' })).toHaveAttribute('href', `${extensionGrantsPath}/g-1`);
        expect(screen.getByText('JWT Bearer')).toBeInTheDocument();
        expect(screen.getByText('custom-am-extension-grant')).toBeInTheDocument();
        expect(screen.getByText('urn:ietf:params:oauth:grant-type:jwt-bearer')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Create extension grant' })).toHaveAttribute('href', `${extensionGrantsPath}/new`);
        expect(listExtensionGrants).toHaveBeenCalledWith(ref);
    });

    it('shows the first-use state when the domain has no extension grant', async () => {
        vi.mocked(listExtensionGrants).mockResolvedValueOnce([]);
        renderPage();

        expect(await screen.findByText('No extension grants yet')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Create extension grant' })).toBeInTheDocument();
    });

    it('shows the error panel, not the first-use state, when the list fails', async () => {
        vi.mocked(listExtensionGrants).mockRejectedValueOnce(new Error('Service unavailable'));
        renderPage();

        expect(await screen.findByText('Extension grants could not be retrieved from the backend.')).toBeInTheDocument();
        expect(screen.queryByText('No extension grants yet')).not.toBeInTheDocument();
    });

    it('deletes an extension grant after the confirm dialog, then reloads the list', async () => {
        vi.mocked(deleteExtensionGrant).mockResolvedValueOnce();
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Delete Partner JWT' }));
        fireEvent.click(
            within(await screen.findByRole('dialog', { name: 'Delete extension grant' })).getByRole('button', {
                name: 'Delete extension grant',
            }),
        );

        await waitFor(() => expect(deleteExtensionGrant).toHaveBeenCalledWith(ref, 'g-1'));
        await waitFor(() => expect(listExtensionGrants).toHaveBeenCalledTimes(2));
    });

    it('hides the create button, the links and the delete buttons without the permissions', async () => {
        renderPage([]);

        expect(await screen.findByText('Partner JWT')).toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Partner JWT' })).not.toBeInTheDocument();
        expect(screen.queryByRole('link', { name: 'Create extension grant' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete Partner JWT' })).not.toBeInTheDocument();
    });
});
