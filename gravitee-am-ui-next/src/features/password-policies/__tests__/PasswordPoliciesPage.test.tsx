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
import type * as Graphene from '@gravitee/graphene-core';
import { toast } from '@gravitee/graphene-core';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { Route } from 'react-router-dom';
import { deletePasswordPolicy, listPasswordPolicies, setDefaultPasswordPolicy } from '../../../lib/api/password-policies';
import { PasswordPoliciesPage } from '../PasswordPoliciesPage';
import { editor, policiesPath, ref, renderRoutes } from './render';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/password-policies', () => ({
    deletePasswordPolicy: vi.fn(),
    listPasswordPolicies: vi.fn(),
    setDefaultPasswordPolicy: vi.fn(),
}));
vi.mock('../../../lib/session/domain', async () => {
    const { currentDomain } = await import('./render');
    return { useCurrentDomain: () => currentDomain };
});

function renderPage(permissions = editor) {
    return renderRoutes(
        policiesPath,
        <>
            <Route path={policiesPath} element={<PasswordPoliciesPage />} />
            <Route path={`${policiesPath}/:policyId`} element={<h1>Policy page</h1>} />
        </>,
        permissions,
    );
}

function openActions(name: string) {
    fireEvent.pointerDown(screen.getByRole('button', { name: `Actions for ${name}` }), { button: 0, ctrlKey: false, pointerType: 'mouse' });
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listPasswordPolicies).mockResolvedValue([
        { id: 'pp-1', name: 'Strong', isDefault: true, idpsNames: ['Default IdP', 'LDAP'] },
        { id: 'pp-2', name: 'Legacy', isDefault: false, idpsNames: [] },
    ]);
});

describe('PasswordPoliciesPage', () => {
    it('lists the policies with their identity provider count and the default one', async () => {
        renderPage();

        const strong = await screen.findByRole('row', { name: /Strong/ });
        expect(listPasswordPolicies).toHaveBeenCalledWith(ref);
        expect(within(strong).getByText('2')).toBeInTheDocument();
        expect(within(strong).getByText('Default')).toBeInTheDocument();
        expect(within(screen.getByRole('row', { name: /Legacy/ })).queryByText('Default')).not.toBeInTheDocument();
        expect(screen.getByRole('link', { name: 'Create password policy' })).toHaveAttribute('href', `${policiesPath}/new`);
    });

    it('shows the first-use state when the domain has no policy', async () => {
        vi.mocked(listPasswordPolicies).mockResolvedValue([]);
        renderPage();

        expect(await screen.findByText('No password policies yet')).toBeInTheDocument();
        expect(screen.queryByRole('table')).not.toBeInTheDocument();
    });

    it('sets a policy as the default one', async () => {
        vi.mocked(setDefaultPasswordPolicy).mockResolvedValueOnce(undefined);
        renderPage();

        await screen.findByRole('row', { name: /Legacy/ });
        openActions('Legacy');
        fireEvent.click(screen.getByRole('menuitem', { name: 'Set as default' }));

        await waitFor(() => expect(setDefaultPasswordPolicy).toHaveBeenCalledWith(ref, 'pp-2'));
        expect(toast.success).toHaveBeenCalledWith('Updated default Password policy');
    });

    it('deletes a policy after the confirmation', async () => {
        vi.mocked(deletePasswordPolicy).mockResolvedValueOnce(undefined);
        renderPage();

        await screen.findByRole('row', { name: /Legacy/ });
        openActions('Legacy');
        fireEvent.click(screen.getByRole('menuitem', { name: 'Delete' }));
        const dialog = await screen.findByRole('dialog', { name: 'Delete Password Policy' });
        fireEvent.click(within(dialog).getByRole('button', { name: 'Delete' }));

        await waitFor(() => expect(deletePasswordPolicy).toHaveBeenCalledWith(ref, 'pp-2'));
        expect(toast.success).toHaveBeenCalledWith('Policy pp-2 deleted');
    });

    it('offers only View without domain_settings_update', async () => {
        renderPage(['domain_settings_read']);

        await screen.findByRole('row', { name: /Legacy/ });
        expect(screen.queryByRole('link', { name: 'Create password policy' })).not.toBeInTheDocument();
        openActions('Legacy');
        expect(screen.getByRole('menuitem', { name: 'View' })).toBeInTheDocument();
        expect(screen.queryByRole('menuitem', { name: 'Set as default' })).not.toBeInTheDocument();
        expect(screen.queryByRole('menuitem', { name: 'Delete' })).not.toBeInTheDocument();
    });
});
