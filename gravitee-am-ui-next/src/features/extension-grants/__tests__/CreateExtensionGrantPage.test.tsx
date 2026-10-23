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
import { toast } from '@gravitee/graphene-core';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { createExtensionGrant, getExtensionGrantSchema, listExtensionGrantPlugins } from '../../../lib/api/extension-grants';
import { CreateExtensionGrantPage } from '../CreateExtensionGrantPage';
import { currentDomain, extensionGrantsPath, jwtBearerSchema, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/extension-grants', () => ({
    createExtensionGrant: vi.fn(),
    getExtensionGrantSchema: vi.fn(),
    listExtensionGrantPlugins: vi.fn(),
}));
vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof import('@gravitee/graphene-core')>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

function renderPage() {
    return renderRoutes(
        `${extensionGrantsPath}/new`,
        <>
            <Route path={`${extensionGrantsPath}/new`} element={<CreateExtensionGrantPage />} />
            <Route path={`${extensionGrantsPath}/:extensionGrantId`} element={<p>Detail page</p>} />
        </>,
        [],
    );
}

async function openSettingsStep() {
    fireEvent.click(await screen.findByRole('radio', { name: 'JWT Bearer' }));
    fireEvent.click(screen.getByRole('button', { name: 'Next' }));
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listExtensionGrantPlugins).mockResolvedValue([
        { id: 'jwtbearer-am-extension-grant', name: 'JWT Bearer Extension Grant' },
        { id: 'xaa-am-extension-grant', name: 'XAA Extension Grant', feature: 'am-extgrant-xaa' },
    ]);
    vi.mocked(getExtensionGrantSchema).mockResolvedValue(jwtBearerSchema);
    vi.mocked(createExtensionGrant).mockResolvedValue({
        id: 'g-9',
        name: 'Partner JWT',
        type: 'jwtbearer-am-extension-grant',
        configuration: '{}',
    });
});

describe('CreateExtensionGrantPage', () => {
    it('names the plugins as Angular does and locks the ones the license lacks', async () => {
        renderPage();

        expect(await screen.findByRole('radio', { name: 'JWT Bearer' })).toBeInTheDocument();
        expect(screen.getByRole('radio', { name: 'Cross App Access, Enterprise' })).toBeInTheDocument();
    });

    it('shows the grant type from the schema default on the settings step', async () => {
        renderPage();

        await openSettingsStep();

        await waitFor(() =>
            expect(screen.getByRole('textbox', { name: 'Grant type' })).toHaveValue('urn:ietf:params:oauth:grant-type:jwt-bearer'),
        );
        expect(screen.getByRole('textbox', { name: 'Grant type' })).toBeDisabled();
    });

    it('does not create the extension grant when the plugin schema has no grant type, as Angular does', async () => {
        const { grantType: _grantType, ...properties } = jwtBearerSchema.properties;
        vi.mocked(getExtensionGrantSchema).mockResolvedValue({ ...jwtBearerSchema, properties });
        renderPage();

        await openSettingsStep();
        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Partner JWT' } });
        fireEvent.change(screen.getByRole('textbox', { name: /Resolver parameter/ }), { target: { value: 'ssh-rsa AAAA' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() =>
            expect(toast.error).toHaveBeenCalledWith('Could not create the extension grant. The plugin defines no grant type.'),
        );
        expect(createExtensionGrant).not.toHaveBeenCalled();
    });

    it('creates the extension grant with the schema grant type and defaults, then opens it', async () => {
        renderPage();

        await openSettingsStep();
        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Partner JWT' } });
        fireEvent.change(screen.getByRole('textbox', { name: /Resolver parameter/ }), { target: { value: 'ssh-rsa AAAA' } });
        fireEvent.click(screen.getByRole('button', { name: 'Create' }));

        await waitFor(() =>
            expect(createExtensionGrant).toHaveBeenCalledWith(ref, {
                name: 'Partner JWT',
                type: 'jwtbearer-am-extension-grant',
                grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                configuration: expect.any(String),
            }),
        );
        expect(JSON.parse(vi.mocked(createExtensionGrant).mock.calls[0][1].configuration)).toEqual({
            publicKeyResolver: 'GIVEN_KEY',
            publicKey: 'ssh-rsa AAAA',
        });
        expect(getExtensionGrantSchema).toHaveBeenCalledWith('jwtbearer-am-extension-grant');
        expect(await screen.findByText('Detail page')).toBeInTheDocument();
    });
});
