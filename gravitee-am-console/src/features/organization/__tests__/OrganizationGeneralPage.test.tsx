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
import { getOrganizationSettings, listOrganizationIdentityProviders, patchOrganizationSettings } from '../../../lib/api/organization';
import { OrganizationGeneralPage } from '../OrganizationGeneralPage';
import { currentEnvironment, ORG, organizationPath, renderRoutes, session } from './render';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/organization', () => ({
    getOrganizationSettings: vi.fn(),
    listOrganizationIdentityProviders: vi.fn(),
    patchOrganizationSettings: vi.fn(),
}));
vi.mock('../../../lib/session/session', () => ({ useSession: () => session }));
vi.mock('../../../lib/session/environment', () => ({ useCurrentEnvironment: () => currentEnvironment }));

const editor = ['organization_settings_read', 'organization_settings_update'];

function renderPage(permissions = editor) {
    return renderRoutes(
        `${organizationPath}/general`,
        <Route path={`${organizationPath}/general`} element={<OrganizationGeneralPage />} />,
        permissions,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(getOrganizationSettings).mockResolvedValue({ identities: ['inline', 'google'] });
    vi.mocked(listOrganizationIdentityProviders).mockResolvedValue([
        { id: 'inline', name: 'Inline users', type: 'inline-am-idp', external: false },
        { id: 'ldap', name: 'Corporate LDAP', type: 'ldap-am-idp', external: false },
        { id: 'google', name: 'Google', type: 'google-am-idp', external: true },
        { id: 'github', name: 'GitHub', type: 'github-am-idp', external: true },
    ]);
});

describe('OrganizationGeneralPage', () => {
    it('shows the selected providers in their own group', async () => {
        renderPage();

        expect(await screen.findByRole('button', { name: 'Remove Inline users' })).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Remove Google' })).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Remove Corporate LDAP' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Remove GitHub' })).not.toBeInTheDocument();
    });

    it('saves the identity providers followed by the social ones', async () => {
        vi.mocked(patchOrganizationSettings).mockResolvedValueOnce({ identities: ['google'] });
        renderPage();

        fireEvent.click(await screen.findByRole('button', { name: 'Remove Inline users' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() => expect(patchOrganizationSettings).toHaveBeenCalledWith(ORG, { identities: ['google'] }));
        expect(toast.success).toHaveBeenCalledWith('Settings updated');
    });

    it('is read-only without organization_settings_update', async () => {
        renderPage(['organization_settings_read']);

        expect(await screen.findByText('Inline users')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
        expect(screen.getByRole('combobox', { name: 'Identity providers' })).toBeDisabled();
    });
});
