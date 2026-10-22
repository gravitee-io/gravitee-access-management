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
import { HttpError } from '../../../lib/api/http';
import { assignPasswordPolicy, listIdentityProviderPlugins, listUserIdentityProviders } from '../../../lib/api/identity-providers';
import { createPasswordPolicy, getPasswordPolicy, type PasswordPolicy, updatePasswordPolicy } from '../../../lib/api/password-policies';
import { PasswordPolicyPage } from '../PasswordPolicyPage';
import { editor, policiesPath, ref, renderRoutes } from './render';

vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<typeof Graphene>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/api/password-policies', () => ({
    createPasswordPolicy: vi.fn(),
    getPasswordPolicy: vi.fn(),
    updatePasswordPolicy: vi.fn(),
}));
vi.mock('../../../lib/api/identity-providers', () => ({
    assignPasswordPolicy: vi.fn(),
    listIdentityProviderPlugins: vi.fn(),
    listUserIdentityProviders: vi.fn(),
}));
vi.mock('../../../lib/session/domain', async () => {
    const { currentDomain } = await import('./render');
    return { useCurrentDomain: () => currentDomain };
});

const stored: PasswordPolicy = {
    id: 'pp-1',
    name: 'Strong',
    referenceId: 'd1',
    referenceType: 'DOMAIN',
    createdAt: 1,
    updatedAt: 2,
    minLength: 8,
    includeNumbers: true,
    maxConsecutiveLetters: 3,
    defaultPolicy: false,
};

function renderPage(path: string, permissions = editor) {
    return renderRoutes(
        path,
        <>
            <Route path={`${policiesPath}/new`} element={<PasswordPolicyPage />} />
            <Route path={`${policiesPath}/:policyId`} element={<PasswordPolicyPage />} />
        </>,
        permissions,
    );
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(listIdentityProviderPlugins).mockResolvedValue([
        { id: 'mongo-am-idp', name: 'MongoDB', displayName: 'MongoDB', external: false },
    ]);
    vi.mocked(listUserIdentityProviders).mockResolvedValue([
        { id: 'idp-1', name: 'Default IdP', type: 'mongo-am-idp', system: true, external: false, passwordPolicy: 'pp-1' },
        { id: 'idp-2', name: 'Customers', type: 'mongo-am-idp', system: false, external: false },
        { id: 'idp-3', name: 'Partners', type: 'mongo-am-idp', system: false, external: false, passwordPolicy: 'pp-9' },
    ]);
    vi.mocked(getPasswordPolicy).mockResolvedValue(stored);
    vi.mocked(assignPasswordPolicy).mockResolvedValue(undefined);
});

describe('PasswordPolicyPage', () => {
    it('creates a policy, links the picked provider to it and opens it', async () => {
        vi.mocked(createPasswordPolicy).mockResolvedValueOnce({ id: 'pp-new', name: 'Partners only' });
        renderPage(`${policiesPath}/new`);

        fireEvent.change(screen.getByRole('textbox', { name: 'Name' }), { target: { value: 'Partners only' } });
        fireEvent.click(screen.getByRole('switch', { name: 'Password must include numbers' }));
        const pick = screen.getByRole('button', { name: 'Select identity providers' });
        await waitFor(() => expect(pick).toBeEnabled());
        fireEvent.click(pick);
        const dialog = await screen.findByRole('dialog', { name: 'Select identity providers' });
        expect(within(dialog).queryByText('Partners')).not.toBeInTheDocument();
        fireEvent.click(within(dialog).getByRole('checkbox', { name: 'Customers' }));
        fireEvent.click(within(dialog).getByRole('button', { name: 'Apply' }));
        fireEvent.click(screen.getByRole('button', { name: 'Create policy' }));

        await waitFor(() =>
            expect(createPasswordPolicy).toHaveBeenCalledWith(ref, {
                name: 'Partners only',
                includeNumbers: true,
                maxConsecutiveLetters: undefined,
            }),
        );
        await waitFor(() => expect(assignPasswordPolicy).toHaveBeenCalledWith(ref, 'idp-2', 'pp-new'));
        expect(toast.success).toHaveBeenCalledWith('Password settings configuration updated');
        expect(await screen.findByRole('heading', { level: 1, name: 'Partners only' })).toBeInTheDocument();
    });

    it('saves the stored policy without its id, reference and dates, and unlinks a provider', async () => {
        vi.mocked(updatePasswordPolicy).mockResolvedValueOnce({ ...stored, minLength: 10 });
        renderPage(`${policiesPath}/pp-1`);

        fireEvent.change(await screen.findByRole('spinbutton', { name: 'Minimum length' }), { target: { value: '10' } });
        fireEvent.click(await screen.findByRole('button', { name: 'Unlink Default IdP' }));
        fireEvent.click(screen.getByRole('button', { name: 'Save' }));

        await waitFor(() =>
            expect(updatePasswordPolicy).toHaveBeenCalledWith(ref, 'pp-1', {
                name: 'Strong',
                minLength: 10,
                includeNumbers: true,
                maxConsecutiveLetters: 3,
                defaultPolicy: false,
            }),
        );
        await waitFor(() => expect(assignPasswordPolicy).toHaveBeenCalledWith(ref, 'idp-1', undefined));
    });

    it('blocks the save while the password history size is out of range', async () => {
        renderPage(`${policiesPath}/pp-1`);

        fireEvent.click(await screen.findByRole('switch', { name: 'Maintain password history' }));
        fireEvent.change(screen.getByRole('spinbutton', { name: 'Password history size' }), { target: { value: '30' } });

        expect(screen.getByText('Number of old passwords must be within the range [1, 24]')).toBeInTheDocument();
        expect(screen.getByRole('button', { name: 'Save' })).toBeDisabled();
    });

    it('shows a read-only policy without domain_settings_update', async () => {
        renderPage(`${policiesPath}/pp-1`, ['domain_settings_read']);

        expect(await screen.findByRole('textbox', { name: 'Name' })).toBeDisabled();
        expect(await screen.findByText('Default IdP')).toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Select identity providers' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Unlink Default IdP' })).not.toBeInTheDocument();
        expect(screen.queryByRole('button', { name: 'Save' })).not.toBeInTheDocument();
    });

    it('shows a not-found panel for an unknown policy', async () => {
        vi.mocked(getPasswordPolicy).mockRejectedValueOnce(new HttpError(404, 'Not found'));
        renderPage(`${policiesPath}/missing`);

        expect(await screen.findByText('Password policy not found')).toBeInTheDocument();
    });
});
