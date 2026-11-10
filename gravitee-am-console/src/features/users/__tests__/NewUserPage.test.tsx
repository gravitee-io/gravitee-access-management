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
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route } from 'react-router-dom';
import { searchApplications } from '../../../lib/api/applications';
import { HttpError } from '../../../lib/api/http';
import { listUserIdentityProviders } from '../../../lib/api/identity-providers';
import { evaluatePassword, getActivePasswordPolicy } from '../../../lib/api/password-policies';
import { createUser, isEmailRequired } from '../../../lib/api/users';
import { NewUserPage } from '../NewUserPage';
import { currentDomain, ref, renderRoutes, usersPath } from './render';

vi.mock('../../../lib/api/users', async importOriginal => ({
    ...(await importOriginal<object>()),
    createUser: vi.fn(),
    isEmailRequired: vi.fn(),
}));
vi.mock('../../../lib/api/applications', () => ({ searchApplications: vi.fn() }));
vi.mock('../../../lib/api/identity-providers', () => ({ listUserIdentityProviders: vi.fn() }));
vi.mock('../../../lib/api/password-policies', () => ({ getActivePasswordPolicy: vi.fn(), evaluatePassword: vi.fn() }));
vi.mock('@gravitee/graphene-core', async importOriginal => ({
    ...(await importOriginal<Record<string, unknown>>()),
    toast: { success: vi.fn(), error: vi.fn() },
}));
vi.mock('../../../lib/session/domain', () => ({ useCurrentDomain: () => currentDomain }));

const newPath = `${usersPath}/new`;

function setup() {
    renderRoutes(
        newPath,
        <>
            <Route path={newPath} element={<NewUserPage />} />
            <Route path={`${usersPath}/:userId`} element={<p>User page</p>} />
        </>,
        ['domain_user_create'],
    );
    const create = () => screen.getByRole('button', { name: 'Create user' });
    return {
        create,
        fillProfile: async () => {
            await userEvent.type(screen.getByLabelText('First name'), 'Alice');
            await userEvent.type(screen.getByLabelText('Last name'), 'Liddell');
            await userEvent.type(screen.getByLabelText('Email'), 'alice@acme.com');
            await userEvent.type(screen.getByLabelText('Username'), 'alice');
        },
        typePassword: (password: string) => userEvent.type(screen.getByLabelText('Password'), password),
    };
}

beforeEach(() => {
    vi.clearAllMocks();
    vi.mocked(isEmailRequired).mockResolvedValue(true);
    vi.mocked(listUserIdentityProviders).mockResolvedValue([]);
    vi.mocked(searchApplications).mockResolvedValue({ data: [], currentPage: 0, totalCount: 0 });
    vi.mocked(getActivePasswordPolicy).mockResolvedValue({ id: 'pp-1', minLength: 8, includeNumbers: true });
    vi.mocked(createUser).mockResolvedValue({ id: 'u-9', username: 'alice', enabled: true });
});

describe('NewUserPage', () => {
    it('keeps Create disabled until the password passes the policy of the identity provider', async () => {
        const harness = setup();
        await harness.fillProfile();

        await harness.typePassword('short');

        expect(await screen.findByText('Has at least 8 characters')).toBeInTheDocument();
        expect(screen.getByText('Contains a number')).toBeInTheDocument();
        expect(screen.getAllByRole('img', { name: 'Failed' })).toHaveLength(2);
        expect(harness.create()).toBeDisabled();
        expect(getActivePasswordPolicy).toHaveBeenCalledWith(ref, undefined);

        await harness.typePassword('word42');

        await waitFor(() => expect(harness.create()).toBeEnabled());
    });

    it('creates the user, then opens it', async () => {
        const harness = setup();
        await harness.fillProfile();
        await harness.typePassword('password42');
        await userEvent.click(screen.getByRole('switch', { name: 'Force password reset' }));

        await userEvent.click(harness.create());

        await waitFor(() =>
            expect(createUser).toHaveBeenCalledWith(ref, {
                firstName: 'Alice',
                lastName: 'Liddell',
                email: 'alice@acme.com',
                username: 'alice',
                password: 'password42',
                client: undefined,
                source: undefined,
                forceResetPassword: true,
                preRegistration: false,
            }),
        );
        expect(toast.success).toHaveBeenCalledWith('User alice created');
        expect(await screen.findByText('User page')).toBeInTheDocument();
    });

    it('creates a pre-registered user without a password, with the added attributes', async () => {
        const harness = setup();
        await harness.fillProfile();
        await userEvent.click(screen.getByRole('switch', { name: 'Pre-registration' }));
        await userEvent.click(screen.getByRole('button', { name: 'Add attribute' }));
        await userEvent.type(screen.getByLabelText('Attribute name'), 'team');
        await userEvent.type(screen.getByLabelText('Value of team'), 'blue');

        expect(screen.queryByLabelText('Password')).not.toBeInTheDocument();
        await userEvent.click(harness.create());

        await waitFor(() =>
            expect(createUser).toHaveBeenCalledWith(
                ref,
                expect.objectContaining({ preRegistration: true, additionalInformation: { team: 'blue' } }),
            ),
        );
        expect(vi.mocked(createUser).mock.calls[0][1].password).toBeUndefined();
    });

    it('takes the email as the username', async () => {
        setup();
        await userEvent.type(screen.getByLabelText('Email'), 'alice@acme.com');

        await userEvent.click(screen.getByRole('checkbox', { name: 'Use email as username' }));

        expect(screen.getByLabelText('Username')).toHaveValue('alice@acme.com');
        expect(screen.getByLabelText('Username')).toBeDisabled();
    });

    it('describes an invalid email with its error once the field loses focus', async () => {
        setup();
        const email = screen.getByLabelText('Email');

        await userEvent.type(email, 'alice');
        expect(email).toHaveAttribute('aria-invalid', 'false');
        expect(screen.queryByText('Not a valid email')).not.toBeInTheDocument();
        await userEvent.tab();

        expect(email).toHaveAttribute('aria-invalid', 'true');
        expect(email).toHaveAccessibleDescription('Not a valid email');
        await userEvent.click(email);
        await userEvent.type(email, '@');
        expect(email).toHaveAttribute('aria-invalid', 'false');
    });

    it('lets the email be empty when the platform does not require it', async () => {
        vi.mocked(isEmailRequired).mockResolvedValue(false);
        const harness = setup();
        await waitFor(() => expect(isEmailRequired).toHaveBeenCalled());
        await userEvent.type(screen.getByLabelText('First name'), 'Alice');
        await userEvent.type(screen.getByLabelText('Last name'), 'Liddell');
        await userEvent.type(screen.getByLabelText('Username'), 'alice');
        await userEvent.click(screen.getByRole('switch', { name: 'Pre-registration' }));

        await waitFor(() => expect(harness.create()).toBeEnabled());
    });

    it('checks the dictionary and the history with the server', async () => {
        vi.mocked(getActivePasswordPolicy).mockResolvedValue({ id: 'pp-1', excludePasswordsInDictionary: true });
        vi.mocked(evaluatePassword).mockResolvedValue({ excludePasswordsInDictionary: false });
        const harness = setup();
        await harness.fillProfile();

        await harness.typePassword('password');

        expect(await screen.findByText('Should not be a common password')).toBeInTheDocument();
        await waitFor(() => expect(evaluatePassword).toHaveBeenCalledWith(ref, 'pp-1', undefined, 'password'));
        expect(await screen.findByRole('img', { name: 'Failed' })).toBeInTheDocument();
        expect(harness.create()).toBeDisabled();
    });

    it('leaves the password to the server when the policy cannot be read', async () => {
        vi.mocked(getActivePasswordPolicy).mockRejectedValue(new HttpError(403, 'Forbidden'));
        const harness = setup();
        await harness.fillProfile();

        await harness.typePassword('short');

        await waitFor(() => expect(harness.create()).toBeEnabled());
        expect(screen.queryByRole('list', { name: 'Password policy' })).not.toBeInTheDocument();
    });

    it('keeps the local rules and drops the server rules when the evaluation fails', async () => {
        vi.mocked(getActivePasswordPolicy).mockResolvedValue({ id: 'pp-1', minLength: 8, excludePasswordsInDictionary: true });
        vi.mocked(evaluatePassword).mockRejectedValue(new HttpError(403, 'Forbidden'));
        const harness = setup();
        await harness.fillProfile();

        await harness.typePassword('password');

        await waitFor(() => expect(harness.create()).toBeEnabled());
        expect(screen.getByText('Has at least 8 characters')).toBeInTheDocument();
        expect(screen.queryByText('Should not be a common password')).not.toBeInTheDocument();
    });
});
