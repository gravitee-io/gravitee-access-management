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
import {
    deleteExtensionGrant,
    getExtensionGrant,
    getExtensionGrantSchema,
    listExtensionGrantPlugins,
    updateExtensionGrant,
} from '../../../lib/api/extension-grants';
import { HttpError } from '../../../lib/api/http';
import { listIdentityProviders } from '../../../lib/api/identity-providers';
import { ExtensionGrantPage } from '../ExtensionGrantPage';
import { crossAppAccessSchema, currentDomain, extensionGrantsPath, jwtBearerSchema, ref, renderRoutes } from './render';

vi.mock('../../../lib/api/extension-grants', () => ({
    deleteExtensionGrant: vi.fn(),
    getExtensionGrant: vi.fn(),
    getExtensionGrantSchema: vi.fn(),
    listExtensionGrantPlugins: vi.fn(),
    updateExtensionGrant: vi.fn(),
}));
vi.mock('../../../lib/api/identity-providers', () => ({ listIdentityProviders: vi.fn() }));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain() }));

const ALL = ['domain_extension_grant_update', 'domain_extension_grant_delete'];
const extensionGrant = {
    id: 'g-1',
    name: 'Partner JWT',
    type: 'jwtbearer-am-extension-grant',
    grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
    identityProvider: 'idp-1',
    createUser: false,
    userExists: false,
    configuration: JSON.stringify({ publicKeyResolver: 'GIVEN_KEY', publicKey: 'ssh-rsa AAAA' }),
};

function renderPage(permissions = ALL, features: string[] = []) {
    return renderRoutes(
        `${extensionGrantsPath}/g-1`,
        <>
            <Route path={`${extensionGrantsPath}/:extensionGrantId`} element={<ExtensionGrantPage />} />
            <Route path={extensionGrantsPath} element={<p>Extension grant list</p>} />
        </>,
        permissions,
        features,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getExtensionGrant).mockResolvedValue(extensionGrant);
    vi.mocked(listExtensionGrantPlugins).mockResolvedValue([
        { id: 'jwtbearer-am-extension-grant', name: 'JWT Bearer Extension Grant' },
        { id: 'xaa-am-extension-grant', name: 'XAA Extension Grant', feature: 'am-extgrant-xaa' },
    ]);
    vi.mocked(getExtensionGrantSchema).mockResolvedValue(jwtBearerSchema);
    vi.mocked(listIdentityProviders).mockResolvedValue([
        { id: 'idp-1', name: 'Default', type: 'mongo-am-idp', system: true, external: false },
        { id: 'idp-2', name: 'Partners', type: 'ldap-am-idp', system: false, external: false },
    ]);
});

describe('ExtensionGrantPage', () => {
    it('saves the seven Angular fields with the serialized configuration', async () => {
        vi.mocked(updateExtensionGrant).mockImplementation(async (_ref, _id, body) => ({ ...extensionGrant, ...body }));
        renderPage();

        expect(await screen.findByRole('textbox', { name: 'Grant type' })).toHaveValue('urn:ietf:params:oauth:grant-type:jwt-bearer');
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Partner assertion' } });
        fireEvent.click(screen.getByRole('switch', { name: 'Check user existence' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateExtensionGrant).toHaveBeenCalledWith(ref, 'g-1', {
                name: 'Partner assertion',
                type: 'jwtbearer-am-extension-grant',
                configuration: expect.any(String),
                grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                identityProvider: 'idp-1',
                createUser: false,
                userExists: true,
            }),
        );
        expect(JSON.parse(vi.mocked(updateExtensionGrant).mock.calls[0][2].configuration)).toEqual({
            publicKeyResolver: 'GIVEN_KEY',
            publicKey: 'ssh-rsa AAAA',
        });
        expect(getExtensionGrant).toHaveBeenCalledWith(ref, 'g-1');
        expect(listIdentityProviders).toHaveBeenCalledWith(ref);
        await waitFor(() => expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled());
    });

    it('sends the schema grant type when the grant has none, as Angular does', async () => {
        vi.mocked(getExtensionGrant).mockResolvedValue({ ...extensionGrant, grantType: undefined });
        vi.mocked(updateExtensionGrant).mockImplementation(async (_ref, _id, body) => ({ ...extensionGrant, ...body }));
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Partner assertion' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateExtensionGrant).toHaveBeenCalledWith(
                ref,
                'g-1',
                expect.objectContaining({ grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer' }),
            ),
        );
    });

    it('leaves the grant type out of the PUT when neither the grant nor the schema has one', async () => {
        const { grantType: _grantType, ...properties } = jwtBearerSchema.properties;
        vi.mocked(getExtensionGrantSchema).mockResolvedValue({ ...jwtBearerSchema, properties });
        vi.mocked(getExtensionGrant).mockResolvedValue({ ...extensionGrant, grantType: undefined });
        vi.mocked(updateExtensionGrant).mockImplementation(async (_ref, _id, body) => ({ ...extensionGrant, ...body }));
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Partner assertion' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(updateExtensionGrant).toHaveBeenCalled());
        expect(vi.mocked(updateExtensionGrant).mock.calls[0][2].grantType).toBeUndefined();
    });

    it('saves a Cross App Access grant with the stored user settings and no empty arrays', async () => {
        const xaa = {
            ...extensionGrant,
            type: 'xaa-am-extension-grant',
            identityProvider: undefined,
            userExists: true,
            configuration: '{}',
        };
        vi.mocked(getExtensionGrant).mockResolvedValue(xaa);
        vi.mocked(getExtensionGrantSchema).mockResolvedValue(crossAppAccessSchema);
        vi.mocked(updateExtensionGrant).mockImplementation(async (_ref, _id, body) => ({ ...xaa, ...body }));
        renderPage(['domain_extension_grant_update'], ['am-extgrant-xaa']);

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Partner XAA' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updateExtensionGrant).toHaveBeenCalledWith(ref, 'g-1', {
                name: 'Partner XAA',
                type: 'xaa-am-extension-grant',
                configuration: '{}',
                grantType: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                identityProvider: undefined,
                createUser: false,
                userExists: true,
            }),
        );
    });

    it('requires a name', async () => {
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: ' ' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByText('Name is required')).toBeInTheDocument();
        expect(updateExtensionGrant).not.toHaveBeenCalled();
    });

    it('replaces the user settings with the issuer hint for a Cross App Access grant', async () => {
        vi.mocked(getExtensionGrant).mockResolvedValue({ ...extensionGrant, type: 'xaa-am-extension-grant', configuration: '{}' });
        vi.mocked(getExtensionGrantSchema).mockResolvedValue(crossAppAccessSchema);
        renderPage();

        expect(await screen.findByText(/resolved from the assertion issuer/)).toBeInTheDocument();
        expect(screen.queryByRole('switch')).not.toBeInTheDocument();
        expect(screen.queryByRole('combobox', { name: 'Identity provider' })).not.toBeInTheDocument();
        expect(listIdentityProviders).not.toHaveBeenCalled();
    });

    it('deletes the extension grant after the confirm dialog, then opens the list', async () => {
        vi.mocked(deleteExtensionGrant).mockResolvedValueOnce();
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Delete' }));
        fireEvent.click(
            within(await screen.findByRole('dialog', { name: 'Delete extension grant' })).getByRole('button', {
                name: 'Delete extension grant',
            }),
        );

        await waitFor(() => expect(deleteExtensionGrant).toHaveBeenCalledWith(ref, 'g-1'));
        expect(await screen.findByText('Extension grant list')).toBeInTheDocument();
    });

    it('hides save and delete without the permissions', async () => {
        renderPage([]);

        expect(await screen.findByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(screen.getByRole('switch', { name: 'Create user account' })).toBeDisabled();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument();
    });

    it('opens the upgrade dialog, and does not save, when the license lacks the plugin feature', async () => {
        vi.mocked(getExtensionGrant).mockResolvedValue({ ...extensionGrant, type: 'xaa-am-extension-grant' });
        renderPage();

        fireEvent.change(await screen.findByRole('textbox', { name: 'Name' }), { target: { value: 'Partner XAA' } });
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        expect(await screen.findByRole('dialog', { name: 'Enterprise feature' })).toBeInTheDocument();
        expect(updateExtensionGrant).not.toHaveBeenCalled();
    });

    it('shows the not-found panel for a deleted extension grant', async () => {
        vi.mocked(getExtensionGrant).mockRejectedValueOnce(new HttpError(404, 'Extension grant not found'));
        renderPage();

        expect(await screen.findByText('Extension grant not found')).toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Back to extension grants' })).toHaveAttribute('href', extensionGrantsPath);
    });
});
